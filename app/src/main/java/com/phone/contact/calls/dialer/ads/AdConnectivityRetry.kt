package com.phone.contact.calls.dialer.ads

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Bumps [tick] once each time connectivity comes back after being lost — NativeAdView observes it
 * and retries a previously-failed load, without needing the user to navigate away from
 * and back to the screen (their own DisposableEffect/factory only fires once per composable
 * lifetime, so a load that failed while offline would otherwise just sit failed forever).
 *
 * A single shared observer (started once, e.g. from the Application class) instead of every
 * individual ad composable registering its own ConnectivityManager callback — a list screen can
 * have many simultaneous native ad slots, and each one doing that would be wasteful.
 */
object AdConnectivityRetry {
    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick

    private var started = false
    private var wasOnline = true

    fun start(context: Context) {
        if (started) return
        started = true
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!wasOnline) _tick.value++
                wasOnline = true
            }

            override fun onLost(network: Network) {
                wasOnline = false
            }
        })
    }

    /** One-shot snapshot — true if there's an active network with internet capability right now.
     * Lets a caller (e.g. the App Open ad's cold-start check) skip straight past a doomed attempt
     * instead of showing a loading state for the full bounded-wait timeout when there's clearly
     * no connectivity to load anything with. */
    fun isOnline(context: Context): Boolean {
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        // VALIDATED (not just NET_CAPABILITY_INTERNET) — a Wi-Fi network with no actual internet
        // behind it (captive portal, router with no WAN) reports the capability but fails Android's
        // own connectivity check, which is what we actually care about here.
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
