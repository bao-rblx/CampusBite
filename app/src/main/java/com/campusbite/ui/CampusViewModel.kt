package com.campusbite.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusbite.data.local.entity.*
import com.campusbite.di.ServiceLocator
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

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
    val repository = ServiceLocator.repository
    val auth = ServiceLocator.auth
    private val db = ServiceLocator.database
    val busy = MutableStateFlow(false)
    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()
    private val catalog = combine(db.stallDao().observeAll(), db.dishDao().observe(), db.slotDao().observeAll()) { s, d, p -> Triple(s, d, p) }
    val state = auth.uid.flatMapLatest { uid ->
        if (uid == null) flowOf(CampusState()) else combine(catalog, db.userDao().observe(uid), db.cartDao().observe(uid), db.orderDao().observeAll(uid)) { data, user, cart, orders ->
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
    fun message(text: String) { viewModelScope.launch { events.send(text) } }
    override fun onCleared() { ServiceLocator.cloud.stop() }
}
