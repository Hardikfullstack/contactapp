package com.phone.contact.call.dialer.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.phone.contact.call.dialer.util.AnalyticsManager

/**
 * Preloads an App Open ad and shows it on demand — meant to be shown from a splash/launch point,
 * gated by whatever cold-start cadence logic the caller wants (not on every app foreground).
 */
object AppOpenAdManager {
    // Google's own App Open ad guidance: a loaded ad is only valid for ~4 hours — calling show()
    // past that is undefined (sometimes neither onAdDismissedFullScreenContent nor
    // onAdFailedToShowFullScreenContent fires at all), which would leave a caller (e.g. Splash)
    // waiting on that completion callback forever.
    private const val EXPIRY_MS = 4 * 60 * 60 * 1000L

    private var appOpenAd: AppOpenAd? = null
    private var loadTimeMs: Long = 0L
    private var isLoading = false

    fun preload(context: Context, adUnitId: String) {
        if (isLoading || isReady()) return
        isLoading = true
        AnalyticsManager.logAdEvent("app_open", adUnitId, "request")
        AppOpenAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    loadTimeMs = System.currentTimeMillis()
                    isLoading = false
                    AnalyticsManager.logAdEvent("app_open", adUnitId, "loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    AnalyticsManager.logAdEvent("app_open", adUnitId, "failed_to_load")
                }
            }
        )
    }

    /** Self-clears an expired ad so the next [preload] call (isLoading/isReady both now false)
     * actually fires instead of being blocked by a stale reference forever. */
    fun isReady(): Boolean {
        if (appOpenAd != null && System.currentTimeMillis() - loadTimeMs > EXPIRY_MS) {
            appOpenAd = null
        }
        return appOpenAd != null
    }

    /**
     * Shows the preloaded ad if ready; [onDismissed] always fires exactly once either way.
     * Pass [adUnitId] to have the next one auto-preload right after this one is dismissed.
     */
    fun show(activity: Activity, adUnitId: String? = null, onDismissed: () -> Unit) {
        val ad = appOpenAd
        if (ad == null) {
            onDismissed()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                adUnitId?.let {
                    AnalyticsManager.logAdEvent("app_open", it, "dismissed")
                    preload(activity.applicationContext, it)
                }
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                appOpenAd = null
                adUnitId?.let {
                    AnalyticsManager.logAdEvent("app_open", it, "failed_to_show")
                    preload(activity.applicationContext, it)
                }
                onDismissed()
            }

            override fun onAdClicked() {
                adUnitId?.let { AnalyticsManager.logAdEvent("app_open", it, "clicked") }
            }
        }
        adUnitId?.let { AnalyticsManager.logAdEvent("app_open", it, "shown") }
        ad.show(activity)
    }
}
