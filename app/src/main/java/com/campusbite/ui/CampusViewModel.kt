package com.campusbite.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusbite.data.local.entity.*
import com.campusbite.di.ServiceLocator
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import com.campusbite.data.local.entity.OrderItemSnapshot

data class CampusState(
    val user: UserEntity? = null,
    val stalls: List<StallEntity> = emptyList(),
    val dishes: List<DishEntity> = emptyList(),
    val cart: List<CartEntity> = emptyList(),
    val orders: List<OrderEntity> = emptyList(),
    val slots: List<SlotEntity> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class CampusViewModel : ViewModel() {
    // The View layer never sees a Repository; it talks to this ViewModel only.
    private val repository = ServiceLocator.repository
    private val auth = ServiceLocator.auth
    val busy = MutableStateFlow(false)
    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()
    private val catalog = combine(repository.observeStalls(), repository.observeDishes(), repository.observeSlots()) { s, d, p -> Triple(s, d, p) }
    val state = auth.uid.flatMapLatest { uid ->
        if (uid == null) flowOf(CampusState()) else combine(catalog, repository.observeUser(uid), repository.observeCart(uid), repository.observeOrders(uid)) { data, user, cart, orders ->
            val demo = uid == com.campusbite.data.AuthRepository.DEMO_UID
            CampusState(user, data.first.filter { it.stallId.startsWith("demo-") == demo },
                data.second.filter { it.dishId.startsWith("demo-") == demo }, cart, orders,
                data.third.filter { it.slotId.startsWith("demo-") == demo })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CampusState())

    init {
        action { repository.initialize() }
        viewModelScope.launch { auth.uid.collectLatest {
            ServiceLocator.cloud.start()
            if (it != null) action { repository.refresh(); ServiceLocator.cloud.start() }
        } }
        viewModelScope.launch { ServiceLocator.cloud.connectionMessage.filterNotNull().distinctUntilChanged().collect { events.send(it) } }
    }

    fun action(success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block(); success?.let { events.send(it) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { events.send(e.localizedMessage ?: "Something went wrong. Please try again.") }
        }
    }
    fun submit(success: String? = null, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { block(); success?.let { events.send(it) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { events.send(e.localizedMessage ?: "Please try again.") }
            finally { busy.value = false }
        }
    }

    // ---- Session (AuthRepository) ----
    val uid: StateFlow<String?> get() = auth.uid
    val isDemo: Boolean get() = auth.isDemo
    val cloudConfigured: Boolean get() = auth.cloudConfigured
    suspend fun demo() = auth.demo()
    suspend fun authenticate(email: String, password: String, name: String?, confirmation: String) = auth.authenticate(email, password, name, confirmation)
    suspend fun resetPassword(email: String) = auth.resetPassword(email)
    suspend fun saveProfile(name: String, favouriteStall: String?, notifications: Boolean) = auth.saveProfile(name, favouriteStall, notifications)
    suspend fun logout() = auth.logout()
    suspend fun deleteAccount() = auth.deleteAccount()

    // ---- Catalogue, cart, orders, reviews (CampusRepository -> Room) ----
    suspend fun initializeDemo() = repository.initializeDemo()
    suspend fun refresh() = repository.refresh()
    suspend fun favourite(dish: DishEntity) = repository.favourite(dish)
    suspend fun add(dishId: String, quantity: Int = 1, note: String? = null) = repository.add(dishId, quantity, note)
    suspend fun quantity(item: CartEntity, value: Int) = repository.quantity(item, value)
    suspend fun restore(item: CartEntity) = repository.restore(item)
    suspend fun checkout(selections: Map<String, String>): List<String> = repository.checkout(selections)
    fun items(order: OrderEntity): List<OrderItemSnapshot> = repository.items(order)
    suspend fun reorder(order: OrderEntity) = repository.reorder(order)
    suspend fun cancel(id: String) = repository.cancel(id)
    suspend fun advanceDemo(id: String) = repository.advanceDemo(id)
    suspend fun deleteCancelled(order: OrderEntity) = repository.deleteCancelled(order)
    suspend fun review(dishId: String, rating: Int, comment: String, deleted: Boolean = false) = repository.review(dishId, rating, comment, deleted)
    fun watchReviews(dishId: String) = ServiceLocator.cloud.watchReviews(dishId)
    fun reviewsFor(dishId: String): Flow<List<ReviewEntity>> = repository.observeReviews(dishId)
    fun message(text: String) { viewModelScope.launch { events.send(text) } }
    override fun onCleared() { ServiceLocator.cloud.stop() }
}
