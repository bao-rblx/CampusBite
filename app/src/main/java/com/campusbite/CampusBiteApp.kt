package com.campusbite

import android.app.Application
import com.campusbite.di.ServiceLocator

class CampusBiteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val settings = getSharedPreferences("settings", 0)
        if (settings.contains("dark")) androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            if (settings.getBoolean("dark", false)) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        ServiceLocator.init(this)
        // LeakCanary initialises itself automatically via ContentProvider in debug builds.
        // No code is needed here — it watches all Activities, Fragments, and ViewModels
        // out of the box. The leak trace appears in the in-app UI and Logcat.
    }
}
