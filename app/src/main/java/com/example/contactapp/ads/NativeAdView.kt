package com.example.contactapp.ads

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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

enum class NativeAdTemplate { SMALL, MEDIUM, LARGE, EXIT }

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

/** Skeleton height for [NativeAdTemplate.LARGE] — the horizontal (media-left, text-right) card
 * used on the Recents home screen, matching how many real network ads render there. */
private val LargeNativeAdHeight = 130.dp

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
    compact: Boolean = false,
    onFailed: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    var nativeAd by remember(adUnitId) { mutableStateOf(NativeAdCache.take(adUnitId)) }
    var hasFailed by remember(adUnitId) { mutableStateOf(false) }

    // Retries a failed load once connectivity comes back — without this, a load that failed
    // while offline just sits failed forever, since the DisposableEffect below only fires once
    // per composable lifetime on its own.
    var retryGeneration by remember(adUnitId) { mutableStateOf(0) }
    val reconnectTick by AdConnectivityRetry.tick.collectAsState()
    LaunchedEffect(reconnectTick) {
        if (hasFailed) {
            hasFailed = false
            nativeAd = null
            retryGeneration++
        }
    }

    DisposableEffect(adUnitId, retryGeneration) {
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
                    onFailed()
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
            NativeAdTemplate.LARGE -> LargeNativeAdSkeleton(modifier, isDarkTheme)
            NativeAdTemplate.EXIT -> ExitNativeAdSkeleton(modifier, isDarkTheme)
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
                    NativeAdTemplate.LARGE -> Modifier
                    NativeAdTemplate.EXIT -> Modifier
                }
            ),
        factory = { ctx ->
            val layoutRes = when (template) {
                NativeAdTemplate.SMALL -> R.layout.native_ad_small
                NativeAdTemplate.MEDIUM -> R.layout.native_ad_medium
                NativeAdTemplate.LARGE -> R.layout.native_ad_large
                NativeAdTemplate.EXIT -> R.layout.native_ad_exit
            }
            LayoutInflater.from(ctx).inflate(layoutRes, null) as com.google.android.gms.ads.nativead.NativeAdView
        },
        update = { adView ->
            applyCardColors(adView, template, isDarkTheme)
            bindNativeAd(adView, ad, template, compact)
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
        NativeAdTemplate.MEDIUM, NativeAdTemplate.LARGE -> {
            // native_ad_medium_card_bg.xml is a rounded-rect <shape>, inflated as a
            // GradientDrawable — mutate() first so recoloring this instance doesn't also
            // recolor every other view still sharing the drawable's default constant state.
            (adView.background?.mutate() as? android.graphics.drawable.GradientDrawable)
                ?.setColor(cardBg.toArgb())
        }
        // No card background — sits directly on the exit sheet's own surface color.
        NativeAdTemplate.EXIT -> {}
    }

    adView.findViewById<TextView>(R.id.ad_headline)?.setTextColor(titleColor.toArgb())
    adView.findViewById<TextView>(R.id.ad_body)?.setTextColor(bodyColor.toArgb())
    adView.findViewById<TextView>(R.id.ad_attribution)?.let { attribution ->
        // Small template's "Ad" badge stays white-on-color regardless of theme (native_ad_small.xml);
        // Medium and Large's plain attribution label follows the body text color instead.
        if (template == NativeAdTemplate.MEDIUM || template == NativeAdTemplate.LARGE) {
            attribution.setTextColor(bodyColor.toArgb())
        }
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

/** Skeleton for [NativeAdTemplate.LARGE] — a 50/50 media-left, text-right split matching
 * native_ad_large.xml, with the button sitting under the text column (50% width), not full-width. */
@Composable
private fun LargeNativeAdSkeleton(modifier: Modifier = Modifier, isDarkTheme: Boolean = false) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(LargeNativeAdHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDarkTheme) NativeAdCardBgDark else NativeAdCardBgLight)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).adShimmerEffect())
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.fillMaxWidth(0.8f).height(15.dp).clip(RoundedCornerShape(4.dp)).adShimmerEffect())
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.width(24.dp).height(11.dp).clip(RoundedCornerShape(4.dp)).adShimmerEffect())
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(4.dp)).adShimmerEffect())
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(4.dp)).adShimmerEffect())
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(18.dp)).adShimmerEffect())
        }
    }
}

/** Skeleton for [NativeAdTemplate.EXIT] — matches native_ad_exit.xml: a media-left/text-right
 * row (no inline CTA), followed by a full-width outlined pill button below the row. */
@Composable
private fun ExitNativeAdSkeleton(modifier: Modifier = Modifier, isDarkTheme: Boolean = false) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(0.6f).height(130.dp).clip(RoundedCornerShape(12.dp)).adShimmerEffect())
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(0.4f), verticalArrangement = Arrangement.Center) {
                Box(modifier = Modifier.fillMaxWidth(0.8f).height(17.dp).clip(RoundedCornerShape(4.dp)).adShimmerEffect())
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(4.dp)).adShimmerEffect())
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(4.dp)).adShimmerEffect())
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).adShimmerEffect())
    }
}

private fun bindNativeAd(
    adView: com.google.android.gms.ads.nativead.NativeAdView,
    nativeAd: NativeAd,
    template: NativeAdTemplate,
    compact: Boolean = false
) {
    val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
    val bodyView = adView.findViewById<TextView>(R.id.ad_body)
    val iconView = adView.findViewById<ImageView?>(R.id.ad_app_icon)
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

    // null on templates like LARGE that don't declare an ad_app_icon view at all — the big
    // MediaView is the primary visual there, so there's nothing to bind.
    if (iconView != null) {
        val icon = nativeAd.icon
        if (icon == null) {
            iconView.visibility = View.GONE
        } else {
            iconView.setImageDrawable(icon.drawable)
            iconView.visibility = View.VISIBLE
        }
        adView.iconView = iconView
    }

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
        // actual aspect ratio instead so the image/video fills it edge to edge. Skipped for
        // LARGE and EXIT, whose MediaViews are fixed square thumbnails by design, not a
        // full-width variable-height block — letting this run on them shrinks the box down to
        // ~28-42% of its own width instead of leaving it as the fixed square it's declared as.
        val aspectRatio = mediaContent?.aspectRatio
        if (template != NativeAdTemplate.LARGE && template != NativeAdTemplate.EXIT && aspectRatio != null && aspectRatio > 0f) {
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
