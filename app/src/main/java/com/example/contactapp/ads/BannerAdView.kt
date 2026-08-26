package com.example.contactapp.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.example.contactapp.util.AnalyticsManager

/**
 * Reusable banner ad, not placed on any screen yet.
 *
 * [adaptive] = true sizes the banner to the full screen width using an anchored adaptive size
 * (taller than a fixed banner, generally better fill rate/eCPM). false uses the fixed
 * [AdSize.BANNER] (320x50) — smaller and more predictable for tight layouts.
 *
 * Shows a shimmer skeleton (matching the ad's own reserved height) until the ad actually loads.
 * [onSizeKnown] fires as soon as the ad's height is determined — before load completes, since
 * that height is reserved immediately — so callers that need to reserve space elsewhere (e.g. a
 * FAB sitting above this banner) don't have to wait for the network round trip.
 */
@Composable
fun BannerAdView(
    adUnitId: String,
    modifier: Modifier = Modifier,
    adaptive: Boolean = true,
    onSizeKnown: (Dp) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val cachedAdView = remember(adUnitId) { BannerAdCache.take(adUnitId) }
    var isLoaded by remember(adUnitId) { mutableStateOf(cachedAdView?.responseInfo != null) }
    var hasFailed by remember(adUnitId) { mutableStateOf(BannerAdCache.hasFailed(adUnitId)) }

    val adSize = remember(adaptive, configuration.screenWidthDp) {
        if (adaptive) {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, configuration.screenWidthDp)
        } else {
            AdSize.BANNER
        }
    }

    LaunchedEffect(adSize, hasFailed) {
        // Collapses back to 0 on failure so callers reserving space above this banner (e.g. a
        // FAB) don't leave a permanent gap for an ad that never showed.
        onSizeKnown(if (hasFailed) 0.dp else adSize.height.dp)
    }

    // No fill / network error / misconfigured unit id — collapse rather than shimmering forever.
    if (hasFailed) return

    Box(modifier = modifier.fillMaxWidth()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                // Reuses the preloaded AdView as-is (same ad unit id/size it was created with) if
                // one's cached; otherwise creates+loads a fresh one exactly as before.
                (cachedAdView ?: AdView(ctx).apply {
                    this.adUnitId = adUnitId
                    setAdSize(adSize)
                }).apply {
                    // Overwrites BannerAdCache's own bookkeeping listener (if this came from
                    // there) — this composable's isLoaded/hasFailed need the callback from here on.
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            isLoaded = true
                            AnalyticsManager.logAdEvent("banner", adUnitId, "loaded")
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            hasFailed = true
                            AnalyticsManager.logAdEvent("banner", adUnitId, "failed_to_load")
                        }

                        override fun onAdClicked() {
                            AnalyticsManager.logAdEvent("banner", adUnitId, "clicked")
                        }
                    }
                    if (cachedAdView == null) {
                        AnalyticsManager.logAdEvent("banner", adUnitId, "request")
                        loadAd(AdRequest.Builder().build())
                    }
                }
            },
            onRelease = { it.destroy() }
        )

        if (!isLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(adSize.height.dp)
                    .adShimmerEffect()
            )
        }
    }
}
