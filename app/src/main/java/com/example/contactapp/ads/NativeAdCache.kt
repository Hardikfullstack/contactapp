package com.example.contactapp.ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.example.contactapp.util.AnalyticsManager

/**
 * Holds a native ad *object* (independent of any View/Composable) loaded ahead of time — for when
 * a specific native placement is known to be needed before its screen even composes. Falls back
 * to loading on-render as usual (see [NativeAdView]) if nothing is cached — this is purely an
 * optional head start, not a requirement.
 */
object NativeAdCache {
    private data class Entry(val ad: NativeAd, val cachedAtMs: Long)

    private const val MAX_AGE_MS = 30 * 60 * 1000L // 30 minutes

    private val ads = mutableMapOf<String, Entry>()
    private val loadingIds = mutableSetOf<String>()

    fun preload(context: Context, adUnitId: String) {
        if (adUnitId in loadingIds || ads.containsKey(adUnitId)) return
        loadingIds += adUnitId
        AnalyticsManager.logAdEvent("native", adUnitId, "request")
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                ads[adUnitId] = Entry(ad, System.currentTimeMillis())
                loadingIds -= adUnitId
                AnalyticsManager.logAdEvent("native", adUnitId, "loaded")
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingIds -= adUnitId
                    AnalyticsManager.logAdEvent("native", adUnitId, "failed_to_load")
                }
            })
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    /** Hands over the cached ad for [adUnitId] if one finished loading and isn't stale — consumes
     * it (won't be returned again), since a [NativeAd] can only ever be bound to one view. A stale
     * entry is destroyed and treated as a miss, so the caller loads a fresh one instead. */
    fun take(adUnitId: String): NativeAd? {
        val entry = ads.remove(adUnitId) ?: return null
        if (System.currentTimeMillis() - entry.cachedAtMs > MAX_AGE_MS) {
            entry.ad.destroy()
            return null
        }
        return entry.ad
    }

    /** Puts an already-loaded [ad] back for reuse — for a caller that owns the ad's whole
     * lifecycle itself (loaded once, shown, then left this cache instead of destroyed) so the
     * *same* ad renders again next time this placement is revisited, instead of loading fresh
     * every time. Matches the reference dialer app's own ad controller, which caches/reuses a
     * loaded native ad across revisits rather than re-requesting one on every screen entry.
     * Overwrites (destroying) whatever was already cached for this id, if anything. Resets the
     * staleness clock — an ad actively being revisited stays eligible; only one left completely
     * untouched for [MAX_AGE_MS] gets discarded on its next [take]. */
    fun put(adUnitId: String, ad: NativeAd) {
        ads.put(adUnitId, Entry(ad, System.currentTimeMillis()))?.ad?.destroy()
    }
}
