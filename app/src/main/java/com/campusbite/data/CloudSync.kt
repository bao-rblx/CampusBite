package com.campusbite.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import com.campusbite.data.local.AppDatabase
import com.campusbite.data.local.entity.*
import com.campusbite.di.ServiceLocator
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class CloudSync(private val context: Context, private val db: AppDatabase, private val auth: AuthRepository) {
    private val firestore get() = FirebaseFirestore.getInstance()
    private val registrations = mutableListOf<ListenerRegistration>()
    private var scope: CoroutineScope? = null
    val connectionMessage = MutableStateFlow<String?>(null)

    fun stop() {
        registrations.forEach { it.remove() }; registrations.clear()
        scope?.cancel(); scope = null
    }
    fun start() {
        stop()
        val uid = auth.uid.value ?: return
        if (auth.isDemo || !auth.cloudConfigured) return
        val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = sessionScope
        registrations += firestore.collection("slots").addSnapshotListener { snapshot, error ->
            if (error != null) { connectionMessage.value = "Live pickup times unavailable. Check your connection and Firebase access."; return@addSnapshotListener }
            sessionScope.launch {
                runCatching { snapshot?.let { db.slotDao().upsertAll(it.documents.map { d ->
                    SlotEntity(d.id, d.getString("stallId").orEmpty(), d.getString("label").orEmpty(),
                        d.getLong("startsAt") ?: 0, (d.getLong("capacity") ?: 0).toInt(), (d.getLong("bookedCount") ?: 0).toInt())
                }) } }.onFailure { connectionMessage.value = "Could not cache pickup times." }
            }
        }
        registrations += firestore.collection("orders").whereEqualTo("userId", uid).addSnapshotListener { snapshot, error ->
            if (error != null) { connectionMessage.value = "Order updates paused. Your saved orders are still available."; return@addSnapshotListener }
            sessionScope.launch {
                runCatching {
                    snapshot?.documents?.forEach { document ->
                        val remote = document.order() ?: return@forEach
                        db.withTransaction {
                            val local = db.orderDao().get(remote.orderId)
                            if (db.stallDao().getAll().none { it.stallId == remote.stallId }) return@withTransaction
                            if (local == null || (local.syncState == SyncState.SYNCED && remote.updatedAt >= local.updatedAt)) db.orderDao().upsert(remote)
                        }
                    }
                }.onFailure { connectionMessage.value = "Could not save an order update." }
            }
        }
        enqueue(context)
    }

    fun watchReviews(dishId: String): ListenerRegistration? {
        if (!auth.cloudConfigured || auth.isDemo || auth.uid.value == null) return null
        return firestore.collection("reviews").whereEqualTo("dishId", dishId).addSnapshotListener { snapshot, error ->
            if (error != null) { connectionMessage.value = "Showing cached reviews. Live reviews are unavailable."; return@addSnapshotListener }
            scope?.launch {
                runCatching { snapshot?.documents?.forEach { d ->
                    val owner = d.getString("userId") ?: return@forEach
                    val old = db.reviewDao().find(owner, dishId)
                    val updated = d.getLong("updatedAt") ?: 0
                    if ((old == null || (old.syncState == SyncState.SYNCED && updated >= old.updatedAt)) && db.dishDao().get(dishId) != null) {
                        db.reviewDao().upsert(ReviewEntity(d.id, dishId, owner, d.getString("authorName").orEmpty(),
                            (d.getLong("rating") ?: 1).toInt().coerceIn(1, 5), d.getString("comment").orEmpty(), SyncState.SYNCED,
                            d.getBoolean("isDeleted") ?: false, d.getLong("createdAt") ?: updated, updated))
                    }
                } }.onFailure { connectionMessage.value = "Could not cache reviews." }
            }
        }
    }

    suspend fun sync(): Boolean {
        val uid = auth.uid.value ?: return true
        if (auth.isDemo || !auth.cloudConfigured) return true
        var success = true
        for (order in db.orderDao().pending().filter { it.userId == uid && it.syncState == SyncState.PENDING }) {
            if (auth.uid.value != uid) return true
            try {
                val result = firestore.runTransaction { tx ->
                    val ref = firestore.collection("orders").document(order.orderId)
                    val existing = tx.get(ref)
                    val slotRef = firestore.collection("slots").document(order.slotId)
                    val slot = tx.get(slotRef)
                    if (existing.exists()) {
                        val remote = requireNotNull(existing.order())
                        if (order.status == OrderStatus.CANCELLED && remote.status == OrderStatus.QUEUED) {
                            tx.update(ref, mapOf("status" to "CANCELLED", "updatedAt" to System.currentTimeMillis()))
                            tx.update(slotRef, mapOf("bookedCount" to ((slot.getLong("bookedCount") ?: 1) - 1).coerceAtLeast(0), "lastOrderId" to order.orderId))
                            order.copy(syncState = SyncState.SYNCED)
                        } else remote
                    } else if (order.status == OrderStatus.CANCELLED) {
                        order.copy(syncState = SyncState.SYNCED)
                    } else {
                        require(slot.exists() && slot.getString("stallId") == order.stallId && (slot.getLong("startsAt") ?: 0) > System.currentTimeMillis()) { "SLOT_UNAVAILABLE" }
                        val booked = slot.getLong("bookedCount") ?: 0
                        require(booked < (slot.getLong("capacity") ?: 0)) { "SLOT_UNAVAILABLE" }
                        tx.set(ref, order.remoteMap())
                        tx.update(slotRef, mapOf("bookedCount" to booked + 1, "lastOrderId" to order.orderId))
                        order.copy(syncState = SyncState.SYNCED)
                    }
                }.await()
                db.withTransaction {
                    val current = db.orderDao().get(order.orderId)
                    // A cancellation written during the request remains pending for the next pass.
                    if (current?.updatedAt == order.updatedAt && current.status == order.status) db.orderDao().upsert(result)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (generateSequence<Throwable>(e) { it.cause }.any { it.message?.contains("SLOT_UNAVAILABLE") == true }) {
                    db.withTransaction {
                        val current = db.orderDao().get(order.orderId)
                        if (current?.status == order.status) db.orderDao().setSyncState(order.orderId, SyncState.FAILED, 1)
                    }
                } else {
                    success = false
                    db.orderDao().setSyncState(order.orderId, SyncState.PENDING, 1)
                }
            }
        }
        for (review in db.reviewDao().pending().filter { it.userId == uid }) {
            if (auth.uid.value != uid) return true
            try {
                firestore.collection("reviews").document(review.reviewId).set(mapOf(
                    "dishId" to review.dishId, "userId" to review.userId, "authorName" to review.authorName,
                    "rating" to review.rating, "comment" to review.comment, "isDeleted" to review.isDeleted,
                    "createdAt" to review.createdAt, "updatedAt" to review.updatedAt)).await()
                db.withTransaction {
                    if (db.reviewDao().find(uid, review.dishId)?.updatedAt == review.updatedAt) db.reviewDao().upsert(review.copy(syncState = SyncState.SYNCED))
                }
            } catch (e: Exception) { if (e is CancellationException) throw e; success = false }
        }
        return success && db.orderDao().pending().none { it.userId == uid && it.syncState == SyncState.PENDING } && db.reviewDao().pending().none { it.userId == uid }
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("campusbite-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}

class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        if (ServiceLocator.cloud.sync()) Result.success() else Result.retry()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { Result.retry() }
}

private fun OrderEntity.remoteMap(): Map<String, Any> = mapOf(
    "userId" to userId, "stallId" to stallId, "slotId" to slotId, "pickupWindow" to pickupWindow,
    "itemsJson" to itemsJson, "totalAmount" to totalAmount, "status" to status.name,
    "createdAt" to createdAt, "updatedAt" to updatedAt)

private fun DocumentSnapshot.order(): OrderEntity? = runCatching {
    OrderEntity(id, requireNotNull(getString("userId")), requireNotNull(getString("stallId")),
        requireNotNull(getString("slotId")), getString("pickupWindow").orEmpty(), requireNotNull(getString("itemsJson")),
        requireNotNull(getLong("totalAmount")), OrderStatus.valueOf(requireNotNull(getString("status"))), SyncState.SYNCED, 0,
        requireNotNull(getLong("createdAt")), requireNotNull(getLong("updatedAt")))
}.getOrNull()
