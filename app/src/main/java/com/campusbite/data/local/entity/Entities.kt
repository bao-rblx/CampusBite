package com.campusbite.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** All money values are Long (integer VND). Never Double. */

@Entity(tableName = "stalls")
data class StallEntity(
    @PrimaryKey val stallId: String,
    val name: String,
    val location: String,
    val imageUrl: String,
    val openHours: String,
    val isOpen: Boolean,
    val updatedAt: Long
)

@Entity(
    tableName = "dishes",
    foreignKeys = [ForeignKey(
        entity = StallEntity::class,
        parentColumns = ["stallId"],
        childColumns = ["stallId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("stallId"), Index("category")]
)
data class DishEntity(
    @PrimaryKey val dishId: String,
    val stallId: String,
    val name: String,
    val category: String,
    val price: Long,
    val imageUrl: String,
    val description: String,
    val avgRating: Float,
    val isAvailable: Boolean,
    val isFavourite: Boolean,
    val updatedAt: Long
)

@Entity(
    tableName = "cart_items",
    foreignKeys = [
        ForeignKey(
            entity = DishEntity::class,
            parentColumns = ["dishId"],
            childColumns = ["dishId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StallEntity::class,
            parentColumns = ["stallId"],
            childColumns = ["stallId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId", "dishId"], unique = true), Index("dishId"), Index("stallId")]
)
data class CartEntity(
    @PrimaryKey(autoGenerate = true) val cartItemId: Long = 0,
    val userId: String,
    val dishId: String,
    val stallId: String,
    val quantity: Int,
    val unitPrice: Long,
    val note: String?,
    val addedAt: Long
)

enum class OrderStatus { QUEUED, CONFIRMED, PREPARING, READY, COMPLETED, CANCELLED }
enum class SyncState { PENDING, SYNCING, SYNCED, FAILED }

@Entity(
    tableName = "orders",
    foreignKeys = [ForeignKey(
        entity = StallEntity::class,
        parentColumns = ["stallId"],
        childColumns = ["stallId"],
        onDelete = ForeignKey.NO_ACTION
    )],
    indices = [Index("userId"), Index("stallId")]
)
data class OrderEntity(
    @PrimaryKey val orderId: String,
    val userId: String,
    val stallId: String,
    val slotId: String,
    val pickupWindow: String,
    val itemsJson: String,
    val totalAmount: Long,
    val status: OrderStatus,
    val syncState: SyncState,
    val retryCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)

/** Snapshot stored in OrderEntity.itemsJson so history survives menu changes. */
data class OrderItemSnapshot(
    val dishId: String,
    val name: String,
    val unitPrice: Long,
    val quantity: Int,
    val note: String?
)

@Entity(
    tableName = "reviews",
    foreignKeys = [ForeignKey(
        entity = DishEntity::class,
        parentColumns = ["dishId"],
        childColumns = ["dishId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("dishId"), Index(value = ["userId", "dishId"], unique = true)]
)
data class ReviewEntity(
    @PrimaryKey val reviewId: String,
    val dishId: String,
    val userId: String,
    val authorName: String,
    val rating: Int,
    val comment: String,
    val syncState: SyncState,
    val isDeleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "users",
    foreignKeys = [ForeignKey(
        entity = StallEntity::class,
        parentColumns = ["stallId"],
        childColumns = ["favouriteStallId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("favouriteStallId")]
)
data class UserEntity(
    @PrimaryKey val uid: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String?,
    val favouriteStallId: String?,
    val notificationsOn: Boolean
)

@Entity(tableName = "slots")
data class SlotEntity(
    @PrimaryKey val slotId: String,
    val stallId: String,
    val label: String,
    val startsAt: Long,
    val capacity: Int,
    val bookedCount: Int
)
