package com.example.contactapp.ads

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.example.contactapp.R
import com.example.contactapp.ui.theme.LocalIsDarkTheme
import com.example.contactapp.util.AnalyticsManager

enum class NativeAdTemplate { SMALL, MEDIUM }

// Driven by the app's own dark/light state (LocalIsDarkTheme), not system config — these views
// are plain Android widgets, not Compose, so they can't pick colors up from values-night on their
// own the way MaterialTheme.colorScheme does.
private val NativeAdCardBgLight = Color(0xFFEBEBEF)
private val NativeAdCardBgDark = Color(0xFF1F2023)
private val NativeAdTextTitleLight = Color(0xFF1B1B24)
private val NativeAdTextTitleDark = Color(0xFFFFFFFF)
private val NativeAdTextDesLight = Color(0xFF464555)
private val NativeAdTextDesDark = Color(0xFF9E9E9E)

/** Fixed row height for [NativeAdTemplate.SMALL], applied to both the skeleton and the loaded
 * ad's AndroidView, so swapping one for the other never shifts the list around it. */
private val SmallNativeAdHeight = 68.dp

/** Fixed card height for [NativeAdTemplate.MEDIUM] — generous enough to fit icon+headline+body,
 * the media block (even at its tallest clamp), and the button, applied to both the skeleton and
 * the loaded ad's AndroidView so swapping one for the other never shifts surrounding content. */
private val MediumNativeAdHeight = 280.dp

/** Shorter skeleton height for [NativeAdView]'s [compact] MEDIUM variant — for placements sitting
 * somewhere tight (e.g. right above a keyboard/input) that shouldn't eat as much vertical space
 * as the full-size card. */
private val CompactMediumNativeAdHeight = 220.dp

/**
 * Reusable native ad, not placed on any screen yet. [template] picks the layout: SMALL for a
 * compact list-row style, MEDIUM for a bigger card with a media (image/video) view. Shows a
 * shimmer skeleton shaped like the template (icon/headline/body/button blocks) while the ad
 * is loading, so there's no layout jump once it arrives.
 */
@Composable
fun NativeAdView(
    adUnitId: String,
    template: NativeAdTemplate,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    var nativeAd by remember(adUnitId) { mutableStateOf(NativeAdCache.take(adUnitId)) }
    var hasFailed by remember(adUnitId) { mutableStateOf(false) }

    DisposableEffect(adUnitId) {
        // A cached ad (preloaded ahead of time via NativeAdCache) is already in hand — skip
        // loading a fresh one.
        if (nativeAd != null) {
            return@DisposableEffect onDispose { nativeAd?.destroy() }
        }
        hasFailed = false
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                nativeAd = ad
                AnalyticsManager.logAdEvent("native", adUnitId, "loaded")
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    hasFailed = true
                    AnalyticsManager.logAdEvent("native", adUnitId, "failed_to_load")
                }

                override fun onAdClicked() {
                    AnalyticsManager.logAdEvent("native", adUnitId, "clicked")
                }
            })
            .build()
        AnalyticsManager.logAdEvent("native", adUnitId, "request")
        adLoader.loadAd(AdRequest.Builder().build())
        onDispose { nativeAd?.destroy() }
    }

    // No fill / network error / misconfigured unit id — collapse rather than shimmering forever.
    if (hasFailed) return

    val ad = nativeAd
    if (ad == null) {
        when (template) {
            NativeAdTemplate.SMALL -> SmallNativeAdSkeleton(modifier, isDarkTheme)
            NativeAdTemplate.MEDIUM -> MediumNativeAdSkeleton(modifier, compact, isDarkTheme)
        }
        return
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .then(
                when (template) {
                    // Fixed to match the adjacent list row's own height exactly.
                    NativeAdTemplate.SMALL -> Modifier.height(SmallNativeAdHeight)
                    // No fixed height here — at large font sizes the text can wrap past
                    // MediumNativeAdHeight, and forcing it would clip the CTA button.
                    NativeAdTemplate.MEDIUM -> Modifier
                }
            ),
        factory = { ctx ->
            val layoutRes = when (template) {
                NativeAdTemplate.SMALL -> R.layout.native_ad_small
                NativeAdTemplate.MEDIUM -> R.layout.native_ad_medium
            }
            LayoutInflater.from(ctx).inflate(layoutRes, null) as com.google.android.gms.ads.nativead.NativeAdView
        },
        update = { adView ->
            applyCardColors(adView, template, isDarkTheme)
            bindNativeAd(adView, ad, compact)
        }
    )
}

/** Re-applies card background + text colors from [isDarkTheme] every recomposition — needed
 * because these are plain Android widgets (not Compose), so a theme flip after inflate would
 * otherwise leave them showing whatever colors were resolved at inflate time. */
private fun applyCardColors(
    adView: com.google.android.gms.ads.nativead.NativeAdView,
    template: NativeAdTemplate,
    isDarkTheme: Boolean
) {
    val cardBg = if (isDarkTheme) NativeAdCardBgDark else NativeAdCardBgLight
    val titleColor = if (isDarkTheme) NativeAdTextTitleDark else NativeAdTextTitleLight
    val bodyColor = if (isDarkTheme) NativeAdTextDesDark else NativeAdTextDesLight

    when (template) {
        NativeAdTemplate.SMALL -> adView.setBackgroundColor(cardBg.toArgb())
        NativeAdTemplate.MEDIUM -> {
            // native_ad_medium_card_bg.xml is a rounded-rect <shape>, inflated as a
            // GradientDrawable — mutate() first so recoloring this instance doesn't also
            // recolor every other view still sharing the drawable's default constant state.
            (adView.background?.mutate() as? android.graphics.drawable.GradientDrawable)
                ?.setColor(cardBg.toArgb())
        }
    }

    adView.findViewById<TextView>(R.id.ad_headline)?.setTextColor(titleColor.toArgb())
    adView.findViewById<TextView>(R.id.ad_body)?.setTextColor(bodyColor.toArgb())
    adView.findViewById<TextView>(R.id.ad_attribution)?.let { attribution ->
        // Small template's "Ad" badge stays white-on-color regardless of theme (native_ad_small.xml);
        // only the Medium template's plain attribution label follows the body text color.
        if (template == NativeAdTemplate.MEDIUM) attribution.setTextColor(bodyColor.toArgb())
    }
}

@Composable
private fun SmallNativeAdSkeleton(modifier: Modifier = Modifier, isDarkTheme: Boolean = false) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SmallNativeAdHeight)
            .background(if (isDarkTheme) NativeAdCardBgDark else NativeAdCardBgLight)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .adShimmerEffect()
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth(0.6f).height(15.dp).clip(RoundedCornerShape(4.dp)).adShimmerEffect())
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(20.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).adShimmerEffect())
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(4.dp)).adShimmerEffect())
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(18.dp))
                .adShimmerEffect()
        )
    }
}

@Composable
private fun MediumNativeAdSkeleton(modifier: Modifier = Modifier, compact: Boolean = false, isDarkTheme: Boolean = false) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) CompactMediumNativeAdHeight else MediumNativeAdHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDarkTheme) NativeAdCardBgDark else NativeAdCardBgLight)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row {
            Box(modifier = Modifier.size(52.dp).adShimmerEffect())
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(4.dp)).adShimmerEffect())
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.width(20.dp).height(9.dp).clip(RoundedCornerShape(4.dp)).adShimmerEffect())
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(4.dp)).adShimmerEffect())
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(4.dp)).adShimmerEffect())
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().height(if (compact) 80.dp else 130.dp).clip(RoundedCornerShape(12.dp)).adShimmerEffect())
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(18.dp)).adShimmerEffect())
    }
}

private fun bindNativeAd(adView: com.google.android.gms.ads.nativead.NativeAdView, nativeAd: NativeAd, compact: Boolean = false) {
    val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
    val bodyView = adView.findViewById<TextView>(R.id.ad_body)
    val iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)
    val ctaView = adView.findViewById<Button>(R.id.ad_call_to_action)
    val mediaView = adView.findViewById<MediaView?>(R.id.ad_media)

    headlineView.text = nativeAd.headline
    adView.headlineView = headlineView

    if (nativeAd.body == null) {
        bodyView.visibility = View.INVISIBLE
    } else {
        bodyView.text = nativeAd.body
        bodyView.visibility = View.VISIBLE
    }
    adView.bodyView = bodyView

    val icon = nativeAd.icon
    if (icon == null) {
        iconView.visibility = View.GONE
    } else {
        iconView.setImageDrawable(icon.drawable)
        iconView.visibility = View.VISIBLE
    }
    adView.iconView = iconView

    if (nativeAd.callToAction == null) {
        ctaView.visibility = View.INVISIBLE
    } else {
        ctaView.text = nativeAd.callToAction
        ctaView.visibility = View.VISIBLE
    }
    adView.callToActionView = ctaView

    if (mediaView != null) {
        adView.mediaView = mediaView
        val mediaContent = nativeAd.mediaContent
        mediaView.mediaContent = mediaContent

        // MediaView letterboxes its content to fit a fixed height, leaving most of the box
        // empty if the asset's own aspect ratio is very different — size the box to the ad's
        // actual aspect ratio instead so the image/video fills it edge to edge.
        val aspectRatio = mediaContent?.aspectRatio
        if (aspectRatio != null && aspectRatio > 0f) {
            mediaView.post {
                val width = mediaView.width
                if (width > 0) {
                    // Clamp to a landscape-ish band so a very tall (portrait) or very wide
                    // creative can't blow the card out of proportion.
                    val idealHeight = width / aspectRatio
                    val clampedHeight = if (compact) {
                        idealHeight.coerceIn(width * 0.18f, width * 0.28f)
                    } else {
                        idealHeight.coerceIn(width * 0.28f, width * 0.42f)
                    }
                    val params = mediaView.layoutParams
                    params.height = clampedHeight.toInt()
                    mediaView.layoutParams = params
                }
            }
        }
    }

    adView.setNativeAd(nativeAd)
}
