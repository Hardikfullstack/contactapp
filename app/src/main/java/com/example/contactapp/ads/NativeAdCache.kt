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
    private val ads = mutableMapOf<String, NativeAd>()
    private val loadingIds = mutableSetOf<String>()

    fun preload(context: Context, adUnitId: String) {
        if (adUnitId in loadingIds || ads.containsKey(adUnitId)) return
        loadingIds += adUnitId
        AnalyticsManager.logAdEvent("native", adUnitId, "request")
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                ads[adUnitId] = ad
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

    /** Hands over the cached ad for [adUnitId] if one finished loading — consumes it (won't be
     * returned again), since a [NativeAd] can only ever be bound to one view. */
    fun take(adUnitId: String): NativeAd? = ads.remove(adUnitId)
}
