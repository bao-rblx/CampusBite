package com.campusbite.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.campusbite.data.local.entity.CartEntity
import com.campusbite.data.local.entity.DishEntity
import com.campusbite.data.local.entity.OrderEntity
import com.campusbite.data.local.entity.ReviewEntity
import com.campusbite.data.local.entity.StallEntity
import com.campusbite.data.local.entity.SyncState
import com.campusbite.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

/*
 * Base DAOs shared by every slice. Each owner may add queries to their own DAO.
 * Rule: refreshes use @Upsert, never OnConflictStrategy.REPLACE
 * (REPLACE deletes the parent row and cascade-wipes cart items and reviews).
 */

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE uid = :uid")
    suspend fun get(uid: String): UserEntity?
    @Query("SELECT * FROM users WHERE uid = :uid")
    fun observe(uid: String): Flow<UserEntity?>

    @Upsert suspend fun upsert(user: UserEntity)

    @Query("DELETE FROM users WHERE uid = :uid")
    suspend fun delete(uid: String)
}

@Dao
interface StallDao {
    @Query("SELECT * FROM stalls")
    suspend fun getAll(): List<StallEntity>
    @Query("SELECT * FROM stalls ORDER BY isOpen DESC, name")
    fun observeAll(): Flow<List<StallEntity>>

    @Query("SELECT * FROM stalls WHERE stallId = :id")
    fun observe(id: String): Flow<StallEntity?>

    @Upsert suspend fun upsertAll(stalls: List<StallEntity>)
}

@Dao
interface DishDao {
    @Query("SELECT * FROM dishes WHERE dishId = :id")
    suspend fun get(id: String): DishEntity?
    @Query(
        """SELECT * FROM dishes
           WHERE (:query = '' OR name LIKE '%' || :query || '%')
             AND (:category IS NULL OR category = :category)
           ORDER BY name"""
    )
    fun observe(query: String = "", category: String? = null): Flow<List<DishEntity>>

    @Query("SELECT * FROM dishes WHERE dishId = :id")
    fun observeById(id: String): Flow<DishEntity?>

    @Query("SELECT * FROM dishes WHERE isFavourite = 1 ORDER BY name")
    fun observeFavourites(): Flow<List<DishEntity>>

    @Query("SELECT DISTINCT category FROM dishes WHERE category != '' ORDER BY category")
    fun observeCategories(): Flow<List<String>>

    @Query("SELECT dishId FROM dishes WHERE isFavourite = 1")
    suspend fun favouriteIds(): List<String>

    @Upsert suspend fun upsertAll(dishes: List<DishEntity>)

    @Query("UPDATE dishes SET isFavourite = :fav WHERE dishId = :id")
    suspend fun setFavourite(id: String, fav: Boolean)

    /** Refresh from the network while keeping the local-only isFavourite flag. */
    @Transaction
    suspend fun upsertKeepFavourite(remote: List<DishEntity>) {
        val favs = favouriteIds().toSet()
        upsertAll(remote.map { it.copy(isFavourite = it.dishId in favs) })
    }
}

@Dao
interface CartDao {
    @Query("SELECT * FROM cart_items WHERE userId = :uid ORDER BY stallId, addedAt")
    fun observe(uid: String): Flow<List<CartEntity>>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM cart_items WHERE userId = :uid")
    fun observeCount(uid: String): Flow<Int>

    @Query("SELECT * FROM cart_items WHERE userId = :uid")
    suspend fun getAll(uid: String): List<CartEntity>

    @Query("SELECT * FROM cart_items WHERE userId = :uid AND dishId = :dishId")
    suspend fun find(uid: String, dishId: String): CartEntity?

    @Upsert suspend fun upsert(item: CartEntity)

    @Query("UPDATE cart_items SET quantity = :qty WHERE cartItemId = :id AND :qty >= 1")
    suspend fun setQuantity(id: Long, qty: Int)

    @Query("DELETE FROM cart_items WHERE cartItemId = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM cart_items WHERE userId = :uid")
    suspend fun clear(uid: String)
}

@Dao
interface OrderDao {
    @Query("DELETE FROM orders WHERE orderId = :id AND userId = :uid AND status = 'CANCELLED' AND syncState = 'SYNCED'")
    suspend fun deleteCancelled(id: String, uid: String)

    @Query("DELETE FROM orders WHERE userId = :uid")
    suspend fun deleteForUser(uid: String)
    @Query("SELECT * FROM orders WHERE userId = :uid ORDER BY createdAt DESC")
    fun observeAll(uid: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE orderId = :id")
    fun observe(id: String): Flow<OrderEntity?>

    @Query("SELECT * FROM orders WHERE orderId = :id")
    suspend fun get(id: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE syncState IN ('PENDING', 'FAILED')")
    suspend fun pending(): List<OrderEntity>

    @Upsert suspend fun upsert(order: OrderEntity)

    @Upsert suspend fun upsertAll(orders: List<OrderEntity>)

    @Query("UPDATE orders SET syncState = :state, retryCount = retryCount + :inc WHERE orderId = :id")
    suspend fun setSyncState(id: String, state: SyncState, inc: Int = 0)
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM reviews WHERE userId = :uid AND dishId = :dishId")
    suspend fun find(uid: String, dishId: String): ReviewEntity?

    @Query("DELETE FROM reviews WHERE userId = :uid")
    suspend fun deleteForUser(uid: String)
    @Query("SELECT * FROM reviews WHERE dishId = :dishId AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun observeForDish(dishId: String): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews WHERE syncState != 'SYNCED'")
    suspend fun pending(): List<ReviewEntity>

    @Upsert suspend fun upsert(review: ReviewEntity)

    @Upsert suspend fun upsertAll(reviews: List<ReviewEntity>)

    @Query("DELETE FROM reviews WHERE reviewId = :id")
    suspend fun hardDelete(id: String)
}

@Dao
interface SlotDao {
    @Query("SELECT * FROM slots ORDER BY startsAt")
    fun observeAll(): Flow<List<com.campusbite.data.local.entity.SlotEntity>>
    @Query("SELECT * FROM slots WHERE slotId = :id")
    suspend fun get(id: String): com.campusbite.data.local.entity.SlotEntity?
    @Upsert suspend fun upsertAll(slots: List<com.campusbite.data.local.entity.SlotEntity>)
}
