package com.phone.contacts.ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.phone.contacts.util.AnalyticsManager

/**
 * Holds a banner ad *View* loaded ahead of time — for when a specific banner placement is known
 * to be needed before its screen even composes. Unlike [NativeAdCache], this caches the actual
 * [AdView] (banners are inherently view-bound, unlike NativeAd/InterstitialAd), created off-screen
 * here and handed over — still attached to nothing — for the real screen to mount into its
 * layout. Falls back to loading on-render as usual (see [BannerAdView]) if nothing is cached —
 * this is purely an optional head start, not a requirement.
 */
object BannerAdCache {
    // Same staleness guidance InterstitialAdManager/AppOpenAdManager/NativeAdCache already follow
    // — a banner preloaded ahead of its screen but not actually mounted for a long time (e.g. the
    // screen it was meant for was never opened this session) shouldn't be handed out once stale.
    private const val EXPIRY_MS = 60 * 60 * 1000L

    private val adViews = mutableMapOf<String, AdView>()
    private val loadTimesMs = mutableMapOf<String, Long>()
    private val loadingIds = mutableSetOf<String>()
    private val failedIds = mutableSetOf<String>()

    fun preload(context: Context, adUnitId: String) {
        if (adUnitId in loadingIds || isCached(adUnitId)) return
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
        loadTimesMs[adUnitId] = System.currentTimeMillis()
        AnalyticsManager.logAdEvent("banner", adUnitId, "request")
        adView.loadAd(AdRequest.Builder().build())
    }

    /** Self-clears (and destroys) an expired entry so a stale banner is never handed out, and so
     * the next [preload] call actually fires instead of being blocked by it forever. */
    private fun isCached(adUnitId: String): Boolean {
        val loadedAt = loadTimesMs[adUnitId]
        if (adViews.containsKey(adUnitId) && loadedAt != null && System.currentTimeMillis() - loadedAt > EXPIRY_MS) {
            adViews.remove(adUnitId)?.destroy()
            loadTimesMs.remove(adUnitId)
        }
        return adViews.containsKey(adUnitId)
    }

    /** Hands over the cached [AdView] for [adUnitId] if one was preloaded and hasn't expired —
     * consumes it (won't be returned again), since a banner AdView can only ever live in one
     * place. The caller must replace its adListener (the one set above only maintains this
     * cache's own bookkeeping) and should check [AdView.getResponseInfo] to know whether it's
     * already finished loading. */
    fun take(adUnitId: String): AdView? {
        if (!isCached(adUnitId)) return null
        loadTimesMs.remove(adUnitId)
        return adViews.remove(adUnitId)
    }

    /** Returns an already-shown [AdView] back into the cache instead of it being destroyed — used
     * when a screen holding a banner is navigated away from, so returning to that screen re-adopts
     * the same still-loaded banner via [take] instead of always starting a fresh load. A detached-
     * but-not-destroyed AdView keeps working normally (any load still in flight isn't cancelled by
     * this). Overwrites (and destroys) whatever was already cached for this id, if anything — last
     * one back wins. */
    fun put(adUnitId: String, adView: AdView) {
        adViews.put(adUnitId, adView)?.takeIf { it !== adView }?.destroy()
        loadTimesMs[adUnitId] = System.currentTimeMillis()
    }

    /** True if this cached ad had already failed to load by the time it's checked — consumed
     * (checked once), matching [take]'s one-shot semantics. */
    fun hasFailed(adUnitId: String): Boolean = failedIds.remove(adUnitId)
}
