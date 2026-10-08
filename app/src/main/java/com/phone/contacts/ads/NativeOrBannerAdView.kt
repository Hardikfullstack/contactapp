package com.phone.contacts.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Tries a native ad first, falling back to a banner only if the native one fails to load.
 *
 * Uses [NativeAdTemplate.SMALL] — its fixed, compact row height is the closest visual match to a
 * banner ad's own footprint, so swapping between the two here doesn't visibly jump the layout.
 */
@Composable
fun NativeOrBannerAdView(
    nativeAdUnitId: String?,
    bannerAdUnitId: String?,
    modifier: Modifier = Modifier,
    // Non-null only for a placement that must survive being unmounted and remounted without
    // reloading (e.g. the bottom nav bar's ad, torn down/rebuilt every time the user leaves/
    // returns to a top-level tab) — see NativeAdView's own cacheKey doc for how this works.
    cacheKey: String? = null
) {
    var nativeFailed by remember(nativeAdUnitId) { mutableStateOf(false) }

    if (nativeAdUnitId != null && !nativeFailed) {
        NativeAdView(
            adUnitId = nativeAdUnitId,
            template = NativeAdTemplate.SMALL,
            modifier = modifier,
            cacheKey = cacheKey,
            onFailed = { nativeFailed = true }
        )
    } else if (bannerAdUnitId != null) {
        BannerAdView(adUnitId = bannerAdUnitId, modifier = modifier)
    }
}
