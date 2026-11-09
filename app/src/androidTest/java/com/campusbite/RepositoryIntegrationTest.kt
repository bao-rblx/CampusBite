package com.campusbite

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.campusbite.data.*
import com.campusbite.data.local.AppDatabase
import com.campusbite.data.local.entity.*
import com.campusbite.data.remote.CampusBiteApi
import com.campusbite.data.remote.dto.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryIntegrationTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: CampusRepository
    private val uid = AuthRepository.DEMO_UID
    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val api = object : CampusBiteApi {
            override suspend fun getStalls(): List<StallDto> = error("Unexpected network request")
            override suspend fun getMenus(): List<DishDto> = error("Unexpected network request")
            override suspend fun getMenusByStall(stallId: String): List<DishDto> = error("Unexpected network request")
            override suspend fun getMenu(id: String): DishDto = error("Unexpected network request")
        }
        val auth = AuthRepository(context, db)
        repo = CampusRepository(db, api, auth) {}
        repo.initialize(); auth.demo()
    }
    @After fun close() { db.close() }
    private suspend fun selections() = db.slotDao().observeAll().first().filter { it.startsAt > System.currentTimeMillis() }.groupBy { it.stallId }.mapValues { it.value.first().slotId }

    @Test fun concurrentAddsNeverLoseQuantities() = runBlocking {
        coroutineScope { (1..20).map { async(Dispatchers.IO) { repo.add("demo-chicken") } }.awaitAll() }
        assertEquals(20, db.cartDao().getAll(uid).single().quantity)
    }
    @Test fun multiStallCheckoutClearsCartAndPreservesSnapshots() = runBlocking {
        repo.add("demo-chicken", 2); repo.add("demo-coffee", 1, "Less ice")
        val ids = repo.checkout(selections())
        assertEquals(2, ids.size); assertTrue(db.cartDao().getAll(uid).isEmpty())
        val orders = db.orderDao().observeAll(uid).first()
        assertEquals(88000L, orders.sumOf { it.totalAmount })
        assertEquals("Less ice", repo.items(orders.single { it.stallId == "demo-cafe" }).single().note)
        orders.forEach { assertEquals(1, db.slotDao().get(it.slotId)!!.bookedCount) }
    }
    @Test fun invalidSecondStallRollsBackEntireCheckout() = runBlocking {
        repo.add("demo-chicken"); repo.add("demo-coffee")
        val slots = selections(); val invalid = slots.toMutableMap().apply { put("demo-cafe", "missing") }
        assertTrue(runCatching { repo.checkout(invalid) }.isFailure)
        assertEquals(2, db.cartDao().getAll(uid).size)
        assertTrue(db.orderDao().observeAll(uid).first().isEmpty())
        slots.values.forEach { assertEquals(0, db.slotDao().get(it)!!.bookedCount) }
    }
    @Test fun fullSlotRetainsCart() = runBlocking {
        repo.add("demo-chicken")
        val selection = selections(); val slot = db.slotDao().get(selection.getValue("demo-rice"))!!
        db.slotDao().upsertAll(listOf(slot.copy(bookedCount = slot.capacity)))
        assertTrue(runCatching { repo.checkout(selection) }.isFailure)
        assertEquals(1, db.cartDao().getAll(uid).size)
    }
    @Test fun cancellationReleasesCapacityExactlyOnce() = runBlocking {
        repo.add("demo-chicken"); val id = repo.checkout(selections()).single()
        repo.cancel(id)
        val order = db.orderDao().get(id)!!
        assertEquals(OrderStatus.CANCELLED, order.status)
        assertEquals(0, db.slotDao().get(order.slotId)!!.bookedCount)
        assertTrue(runCatching { repo.cancel(id) }.isFailure)
    }
    @Test fun reorderUsesCurrentPricesAndHistoryDoesNotChange() = runBlocking {
        repo.add("demo-chicken"); val id = repo.checkout(selections()).single()
        val old = db.orderDao().get(id)!!; val dish = db.dishDao().get("demo-chicken")!!
        db.dishDao().upsertAll(listOf(dish.copy(price = 42000, name = "New menu name")))
        repo.reorder(old)
        assertEquals(42000L, db.cartDao().getAll(uid).single().unitPrice)
        assertEquals(35000L, repo.items(old).single().unitPrice)
        assertEquals("Cơm gà nướng", repo.items(old).single().name)
    }
    @Test fun refreshPreservesFavouritesAndCart() = runBlocking {
        val dish = db.dishDao().get("demo-chicken")!!
        repo.favourite(dish); repo.add(dish.dishId)
        db.dishDao().upsertKeepFavourite(listOf(dish.copy(price = 39000)))
        assertTrue(db.dishDao().get(dish.dishId)!!.isFavourite)
        assertEquals(1, db.cartDao().getAll(uid).size)
    }
    @Test fun reviewEditsAndDeletesKeepOneReviewPerUser() = runBlocking {
        repo.review("demo-chicken", 4, "Good"); repo.review("demo-chicken", 5, "Great")
        assertEquals(1, db.reviewDao().observeForDish("demo-chicken").first().size)
        assertEquals(5, db.reviewDao().find(uid, "demo-chicken")!!.rating)
        repo.review("demo-chicken", 5, "Great", true)
        assertTrue(db.reviewDao().observeForDish("demo-chicken").first().isEmpty())
        repo.review("demo-chicken", 3, "Updated")
        assertEquals(1, db.reviewDao().observeForDish("demo-chicken").first().size)
    }
}
