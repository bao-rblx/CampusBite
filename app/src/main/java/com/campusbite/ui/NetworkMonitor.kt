package com.campusbite.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live connectivity monitor used for offline / Airplane-mode handling.
 *
 * The default-network callback keeps [online] up to date as the device loses and
 * regains a validated internet connection, so the UI can show a friendly banner
 * and keep serving cached Room data instead of failing.
 *
 * Memory safety: the callback is always registered against the **application**
 * context. Registering it against an Activity would store that Activity into the
 * static `ConnectivityManager.sCallbacks` map, keeping it alive after
 * `onDestroy` (this is exactly the leak LeakCanary reported for MainActivity).
 * [stop] unregisters the callback so nothing outlives the screen that started it.
 */
object NetworkMonitor {
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private var manager: ConnectivityManager? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    /** Idempotent: starting twice is a no-op. */
    fun start(context: Context) {
        if (callback != null) return
        val app = context.applicationContext ?: context
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager = cm
        _online.value = isOnline(cm)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { _online.value = true }
            override fun onLost(network: Network) { _online.value = isOnline(cm) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
        callback = cb
    }

    /** Unregisters the callback; safe to call when never started. */
    fun stop() {
        val cb = callback ?: return
        runCatching { manager?.unregisterNetworkCallback(cb) }
        callback = null
        manager = null
    }

    private fun isOnline(cm: ConnectivityManager): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
