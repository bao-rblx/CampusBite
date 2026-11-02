package com.campusbite.data

import androidx.room.withTransaction
import com.campusbite.BuildConfig
import com.campusbite.data.local.AppDatabase
import com.campusbite.data.local.entity.*
import com.campusbite.data.remote.CampusBiteApi
import com.campusbite.data.remote.dto.toEntity
import com.campusbite.domain.OrderRules
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CampusRepository(private val db: AppDatabase, private val api: CampusBiteApi, val auth: AuthRepository,
    private val scheduleSync: () -> Unit) {
    private val gson = Gson()

    // Reactive reads (Room DAO Flow -> Repository -> ViewModel). The UI never touches a DAO.
    fun observeStalls(): Flow<List<StallEntity>> = db.stallDao().observeAll()
    fun observeDishes(): Flow<List<DishEntity>> = db.dishDao().observe()
    fun observeSlots(): Flow<List<SlotEntity>> = db.slotDao().observeAll()
    fun observeUser(uid: String): Flow<UserEntity?> = db.userDao().observe(uid)
    fun observeCart(uid: String): Flow<List<CartEntity>> = db.cartDao().observe(uid)
    fun observeCartCount(uid: String): Flow<Int> = db.cartDao().observeCount(uid)
    fun observeOrders(uid: String): Flow<List<OrderEntity>> = db.orderDao().observeAll(uid)
    fun observeReviews(dishId: String): Flow<List<ReviewEntity>> = db.reviewDao().observeForDish(dishId)
    val catalogConfigured = !BuildConfig.MOCKAPI_BASE_URL.contains("example.mockapi.io")
    suspend fun initialize() {
        if (!catalogConfigured || auth.isDemo) initializeDemo()
    }
    suspend fun initializeDemo() {
        db.withTransaction {
            if (db.stallDao().getAll().none { it.stallId.startsWith("demo-") }) {
                db.stallDao().upsertAll(SeedData.stalls)
                db.dishDao().upsertAll(SeedData.dishes)
            }
            SeedData.slots().forEach { slot ->
                if (db.slotDao().get(slot.slotId) == null) db.slotDao().upsertAll(listOf(slot))
            }
        }
    }

    suspend fun refresh() {
        if (!catalogConfigured || auth.isDemo) { initialize(); return }
        val stalls = api.getStalls().map { it.toEntity() }
        val dishes = api.getMenus().map { it.toEntity() }
        require(dishes.all { dish -> dish.price >= 0 && stalls.any { it.stallId == dish.stallId } }) { "The menu service returned invalid data." }
        db.withTransaction {
            db.stallDao().upsertAll(stalls)
            db.dishDao().upsertKeepFavourite(dishes)
        }
    }

    private fun uid() = requireNotNull(auth.uid.value) { "Please sign in first." }
    suspend fun favourite(dish: DishEntity) = db.dishDao().setFavourite(dish.dishId, !dish.isFavourite)

    suspend fun add(dishId: String, quantity: Int = 1, note: String? = null) {
        val userId = uid()
        require(quantity in 1..99)
        db.withTransaction {
            val dish = requireNotNull(db.dishDao().get(dishId)) { "This dish is no longer on the menu." }
            require(dish.isAvailable && db.stallDao().getAll().any { it.stallId == dish.stallId && it.isOpen }) { "This dish or stall is unavailable." }
            val old = db.cartDao().find(userId, dishId)
            val next = (old?.quantity ?: 0) + quantity
            require(next <= 99) { "You can add up to 99 of each dish." }
            db.cartDao().upsert(old?.copy(quantity = next, note = note ?: old.note) ?: CartEntity(
                userId = userId, dishId = dishId, stallId = dish.stallId, quantity = quantity,
                unitPrice = dish.price, note = note?.trim()?.take(200), addedAt = System.currentTimeMillis()))
        }
    }

    suspend fun quantity(item: CartEntity, value: Int) {
        require(item.userId == uid())
        require(value in 0..99) { "Choose up to 99 items." }
        if (value == 0) db.cartDao().delete(item.cartItemId) else db.cartDao().setQuantity(item.cartItemId, value)
    }
    suspend fun restore(item: CartEntity) = add(item.dishId, item.quantity, item.note)

    suspend fun checkout(selections: Map<String, String>): List<String> {
        val userId = uid()
        require(auth.isDemo || (catalogConfigured && selections.values.none { it.startsWith("demo-") })) { "Live checkout requires a configured menu service and live pickup slots." }
        val ids = db.withTransaction {
            val cart = db.cartDao().getAll(userId)
            require(cart.isNotEmpty()) { "Your cart is empty." }
            val now = System.currentTimeMillis()
            val orders = cart.groupBy { it.stallId }.map { (stallId, rows) ->
                require(db.stallDao().getAll().any { it.stallId == stallId && it.isOpen }) { "A stall has closed. Please update your cart." }
                val slot = requireNotNull(selections[stallId]?.let { db.slotDao().get(it) }) { "Choose a pickup time for each stall." }
                require(slot.stallId == stallId && slot.startsAt > now) { "That pickup time has passed. Choose another." }
                require(OrderRules.remaining(slot.capacity, slot.bookedCount) > 0) { "That pickup window is full." }
                val items = rows.map { row ->
                    val dish = requireNotNull(db.dishDao().get(row.dishId))
                    require(dish.isAvailable) { "${dish.name} is sold out. Remove it from your cart." }
                    OrderItemSnapshot(row.dishId, dish.name, row.unitPrice, row.quantity, row.note)
                }
                if (auth.isDemo) db.slotDao().upsertAll(listOf(slot.copy(bookedCount = slot.bookedCount + 1)))
                OrderEntity(UUID.randomUUID().toString(), userId, stallId, slot.slotId, slot.label, gson.toJson(items),
                    OrderRules.total(items), OrderStatus.QUEUED, if (auth.isDemo) SyncState.SYNCED else SyncState.PENDING, 0, now, now)
            }
            db.orderDao().upsertAll(orders)
            db.cartDao().clear(userId)
            orders.map { it.orderId }
        }
        scheduleSync()
        return ids
    }

    fun items(order: OrderEntity): List<OrderItemSnapshot> = gson.fromJson(order.itemsJson, Array<OrderItemSnapshot>::class.java).toList()
    suspend fun reorder(order: OrderEntity) {
        require(order.userId == uid())
        db.withTransaction { items(order).forEach { add(it.dishId, it.quantity, it.note) } }
    }
    suspend fun cancel(id: String) {
        db.withTransaction {
            val order = requireNotNull(db.orderDao().get(id))
            require(order.userId == uid() && OrderRules.canCancel(order.status)) { "Only queued orders can be cancelled." }
            db.orderDao().upsert(order.copy(status = OrderStatus.CANCELLED,
                syncState = if (auth.isDemo) SyncState.SYNCED else SyncState.PENDING, updatedAt = System.currentTimeMillis()))
            if (auth.isDemo) db.slotDao().get(order.slotId)?.let { db.slotDao().upsertAll(listOf(it.copy(bookedCount = (it.bookedCount - 1).coerceAtLeast(0)))) }
        }
        scheduleSync()
    }
    suspend fun advanceDemo(id: String) {
        require(auth.isDemo)
        db.withTransaction {
            val order = requireNotNull(db.orderDao().get(id))
            require(order.userId == uid())
            val next = when (order.status) {
                OrderStatus.QUEUED -> OrderStatus.CONFIRMED
                OrderStatus.CONFIRMED -> OrderStatus.PREPARING
                OrderStatus.PREPARING -> OrderStatus.READY
                OrderStatus.READY -> OrderStatus.COMPLETED
                else -> return@withTransaction
            }
            db.orderDao().upsert(order.copy(status = next, updatedAt = System.currentTimeMillis()))
        }
    }
    suspend fun deleteCancelled(order: OrderEntity) {
        require(order.userId == uid() && order.status == OrderStatus.CANCELLED && order.syncState == SyncState.SYNCED)
        if (!auth.isDemo) com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("orders").document(order.orderId).delete().await()
        db.orderDao().deleteCancelled(order.orderId, uid())
    }

    suspend fun review(dishId: String, rating: Int, comment: String, deleted: Boolean = false) {
        require(rating in 1..5)
        require(comment.trim().length <= 1000) { "Keep reviews under 1,000 characters." }
        val userId = uid()
        val now = System.currentTimeMillis()
        val old = db.reviewDao().find(userId, dishId)
        val user = requireNotNull(db.userDao().get(userId))
        val review = ReviewEntity(old?.reviewId ?: "$userId-$dishId", dishId, userId, user.displayName, rating,
            comment.trim(), if (auth.isDemo) SyncState.SYNCED else SyncState.PENDING, deleted, old?.createdAt ?: now, now)
        db.reviewDao().upsert(review)
        scheduleSync()
    }
}
