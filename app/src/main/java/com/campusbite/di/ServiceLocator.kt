package com.campusbite.di

import android.content.Context
import com.campusbite.data.local.AppDatabase
import com.campusbite.data.remote.ApiClient
import com.campusbite.data.remote.CampusBiteApi
import com.campusbite.data.AuthRepository
import com.campusbite.data.CampusRepository
import com.campusbite.data.CloudSync

/**
 * Manual DI (no Hilt to keep the build simple).
 * Each slice adds its Repository here as a lazy property, e.g.
 *   val menuRepository by lazy { MenuRepository(database.dishDao(), api) }
 */
object ServiceLocator {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val api: CampusBiteApi by lazy { ApiClient.create() }
    val auth by lazy { AuthRepository(appContext, database) }
    val repository by lazy { CampusRepository(database, api, auth) { CloudSync.enqueue(appContext) } }
    val cloud by lazy { CloudSync(appContext, database, auth) }
}
