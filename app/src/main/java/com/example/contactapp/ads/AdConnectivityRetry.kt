package com.example.contactapp.ads

import android.content.Context
import com.example.contactapp.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Bumps [tick] once each time connectivity comes back after being lost — NativeAdView/BannerAdView
 * observe it and retry a previously-failed load, without needing the user to navigate away from
 * and back to the screen (their own DisposableEffect/factory only fires once per composable
 * lifetime, so a load that failed while offline would otherwise just sit failed forever).
 *
 * A single shared observer (started once from ContactApplication.onCreate) instead of every
 * individual ad composable registering its own ConnectivityManager callback — a list screen can
 * have many simultaneous native ad slots, and each one doing that would be wasteful.
 */
object AdConnectivityRetry {
    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick

    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Main.immediate).launch {
            var wasOnline = true
            NetworkMonitor(appContext).isOnline.collect { online ->
                if (online && !wasOnline) {
                    _tick.value++
                }
                wasOnline = online
            }
        }
    }
}
