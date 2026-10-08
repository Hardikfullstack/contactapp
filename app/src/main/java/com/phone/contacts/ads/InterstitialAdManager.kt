package com.phone.contacts.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.phone.contacts.util.AnalyticsManager

/**
 * Loads interstitials ahead of time (preload) so they're ready to show instantly when needed,
 * instead of the UI blocking/waiting on a load at the moment it's wanted.
 *
 * Keyed by ad unit id — supports several distinct interstitial placements at once (including the
 * "on back / exit" one), each with its own config slot; a single shared ad field would let one
 * placement's preload silently block another's.
 */
object InterstitialAdManager {
    // Google's own recommended window for an interstitial sitting loaded-but-unshown: calling
    // show() past this risks neither onAdDismissedFullScreenContent nor
    // onAdFailedToShowFullScreenContent ever firing, which would hang a caller waiting on the
    // onDismissed callback forever.
    private const val EXPIRY_MS = 60 * 60 * 1000L

    private val ads = mutableMapOf<String, InterstitialAd>()
    private val loadTimesMs = mutableMapOf<String, Long>()
    private val loadingIds = mutableSetOf<String>()

    fun preload(context: Context, adUnitId: String) {
        if (adUnitId in loadingIds || isReady(adUnitId)) return
        loadingIds += adUnitId
        AnalyticsManager.logAdEvent("interstitial", adUnitId, "request")
        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    ads[adUnitId] = ad
                    loadTimesMs[adUnitId] = System.currentTimeMillis()
                    loadingIds -= adUnitId
                    AnalyticsManager.logAdEvent("interstitial", adUnitId, "loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingIds -= adUnitId
                    AnalyticsManager.logAdEvent("interstitial", adUnitId, "failed_to_load")
                }
            }
        )
    }

    /** Self-clears an expired entry so the next [preload] call actually fires instead of being
     * blocked by a stale reference forever. */
    fun isReady(adUnitId: String): Boolean {
        val loadedAt = loadTimesMs[adUnitId]
        if (ads.containsKey(adUnitId) && loadedAt != null && System.currentTimeMillis() - loadedAt > EXPIRY_MS) {
            ads.remove(adUnitId)
            loadTimesMs.remove(adUnitId)
        }
        return ads.containsKey(adUnitId)
    }

    /**
     * Shows the preloaded ad for [adUnitId] if one is ready. [onDismissed] always fires exactly
     * once — either after the shown ad is closed, or immediately if no ad was ready (caller's
     * flow should never block waiting on an ad). A fresh ad is preloaded afterward either way.
     */
    fun show(activity: Activity, adUnitId: String, onDismissed: () -> Unit) {
        val ad = ads[adUnitId]
        if (ad == null) {
            onDismissed()
            preload(activity, adUnitId)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ads.remove(adUnitId)
                loadTimesMs.remove(adUnitId)
                AnalyticsManager.logAdEvent("interstitial", adUnitId, "dismissed")
                preload(activity.applicationContext, adUnitId)
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                ads.remove(adUnitId)
                loadTimesMs.remove(adUnitId)
                AnalyticsManager.logAdEvent("interstitial", adUnitId, "failed_to_show")
                preload(activity.applicationContext, adUnitId)
                onDismissed()
            }

            override fun onAdClicked() {
                AnalyticsManager.logAdEvent("interstitial", adUnitId, "clicked")
            }
        }
        AnalyticsManager.logAdEvent("interstitial", adUnitId, "shown")
        ad.show(activity)
    }
}
