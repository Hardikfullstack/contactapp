package com.example.contactapp.ads

import kotlinx.coroutines.delay

/**
 * Polls [isReady] every 150ms until it returns true or [timeoutMillis] elapses — App Open ads
 * take a couple of seconds to load over the network, so a brief bounded wait (with a loading
 * indicator shown by the caller) gives them a real chance to be ready instead of silently
 * skipping the ad on every cold start.
 */
suspend fun waitUntilAdReady(timeoutMillis: Long = 3500L, isReady: () -> Boolean) {
    var waited = 0L
    val step = 150L
    while (!isReady() && waited < timeoutMillis) {
        delay(step)
        waited += step
    }
}
