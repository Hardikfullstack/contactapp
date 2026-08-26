package com.example.contactapp.ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.example.contactapp.util.AnalyticsManager

/**
 * Holds a banner ad *View* loaded ahead of time — for when a specific banner placement is known
 * to be needed before its screen even composes. Unlike [NativeAdCache], this caches the actual
 * [AdView] (banners are inherently view-bound, unlike NativeAd/InterstitialAd), created off-screen
 * here and handed over — still attached to nothing — for the real screen to mount into its
 * layout. Falls back to loading on-render as usual (see [BannerAdView]) if nothing is cached —
 * this is purely an optional head start, not a requirement.
 */
object BannerAdCache {
    private val adViews = mutableMapOf<String, AdView>()
    private val loadingIds = mutableSetOf<String>()
    private val failedIds = mutableSetOf<String>()

    fun preload(context: Context, adUnitId: String) {
        if (adUnitId in loadingIds || adViews.containsKey(adUnitId)) return
        loadingIds += adUnitId

        val displayMetrics = context.resources.displayMetrics
        val screenWidthDp = (displayMetrics.widthPixels / displayMetrics.density).toInt()
        val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, screenWidthDp)

        val adView = AdView(context.applicationContext).apply {
            this.adUnitId = adUnitId
            setAdSize(adSize)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    loadingIds -= adUnitId
                    AnalyticsManager.logAdEvent("banner", adUnitId, "loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    adViews.remove(adUnitId)
                    loadingIds -= adUnitId
                    failedIds += adUnitId
                    AnalyticsManager.logAdEvent("banner", adUnitId, "failed_to_load")
                }
            }
        }
        adViews[adUnitId] = adView
        AnalyticsManager.logAdEvent("banner", adUnitId, "request")
        adView.loadAd(AdRequest.Builder().build())
    }

    /** Hands over the cached [AdView] for [adUnitId] if one was preloaded — consumes it (won't
     * be returned again), since a banner AdView can only ever live in one place. The caller must
     * replace its adListener (the one set above only maintains this cache's own bookkeeping) and
     * should check [AdView.getResponseInfo] to know whether it's already finished loading. */
    fun take(adUnitId: String): AdView? = adViews.remove(adUnitId)

    /** True if this cached ad had already failed to load by the time it's checked — consumed
     * (checked once), matching [take]'s one-shot semantics. */
    fun hasFailed(adUnitId: String): Boolean = failedIds.remove(adUnitId)
}
