package com.campusbite.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.campusbite.data.local.dao.SlotDao
import com.campusbite.data.local.entity.SlotEntity
import com.campusbite.data.local.dao.CartDao
import com.campusbite.data.local.dao.DishDao
import com.campusbite.data.local.dao.OrderDao
import com.campusbite.data.local.dao.ReviewDao
import com.campusbite.data.local.dao.StallDao
import com.campusbite.data.local.dao.UserDao
import com.campusbite.data.local.entity.CartEntity
import com.campusbite.data.local.entity.DishEntity
import com.campusbite.data.local.entity.OrderEntity
import com.campusbite.data.local.entity.ReviewEntity
import com.campusbite.data.local.entity.StallEntity
import com.campusbite.data.local.entity.UserEntity

/**
 * CampusBite_DB — single source of truth.
 * Migration policy (owned by the Team Leader): any schema change bumps `version`
 * and adds a Migration; during development fallbackToDestructiveMigration is allowed.
 */
@Database(
    entities = [
        StallEntity::class, DishEntity::class, CartEntity::class,
        OrderEntity::class, ReviewEntity::class, UserEntity::class, SlotEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun stallDao(): StallDao
    abstract fun dishDao(): DishDao
    abstract fun cartDao(): CartDao
    abstract fun orderDao(): OrderDao
    abstract fun reviewDao(): ReviewDao
    abstract fun slotDao(): SlotDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `slots` (`slotId` TEXT NOT NULL, `stallId` TEXT NOT NULL, `label` TEXT NOT NULL, `startsAt` INTEGER NOT NULL, `capacity` INTEGER NOT NULL, `bookedCount` INTEGER NOT NULL, PRIMARY KEY(`slotId`))")
            }
        }
        const val NAME = "CampusBite_DB"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Thread-safe singleton (double-checked locking). @Volatile makes the
         * write to INSTANCE visible to every thread at once; synchronized makes
         * sure only one thread can build the database.
         */
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
