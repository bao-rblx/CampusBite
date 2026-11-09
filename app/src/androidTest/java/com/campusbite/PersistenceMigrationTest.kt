package com.campusbite

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.campusbite.data.SeedData
import com.campusbite.data.local.AppDatabase
import com.campusbite.data.local.entity.CartEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun schemaUpgradeKeepsExistingStalls() = runBlocking {
        val name = "campusbite-migration-test"
        context.deleteDatabase(name)
        val json = InstrumentationRegistry.getInstrumentation().context.assets.open("com.campusbite.data.local.AppDatabase/1.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            val entities = json.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = json.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO stalls VALUES ('legacy', 'Original stall', 'Campus', '', '07:00–19:00', 1, 1)")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_1_2).build()
        try {
            assertEquals("Original stall", db.stallDao().getAll().single().name)
            assertTrue(db.slotDao().observeAll().first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun cartAndFavouriteSurviveDatabaseReopen() = runBlocking {
        val name = "campusbite-persistence-test"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            db.stallDao().upsertAll(SeedData.stalls); db.dishDao().upsertAll(SeedData.dishes)
            db.dishDao().setFavourite("demo-chicken", true)
            db.cartDao().upsert(CartEntity(userId = "local-demo", dishId = "demo-chicken", stallId = "demo-rice", quantity = 3, unitPrice = 35000, note = "No chilli", addedAt = 1))
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            assertEquals(3, db.cartDao().getAll("local-demo").single().quantity)
            assertEquals("No chilli", db.cartDao().getAll("local-demo").single().note)
            assertTrue(db.dishDao().get("demo-chicken")!!.isFavourite)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
