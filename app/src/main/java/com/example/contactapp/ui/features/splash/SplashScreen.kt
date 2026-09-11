package com.example.contactapp.ui.features.splash

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.contactapp.R
import com.example.contactapp.ads.AdLoadingLottie
import com.example.contactapp.ads.AppOpenAdManager
import com.example.contactapp.ads.AppOpenCounter
import com.example.contactapp.ads.BannerAdCache
import com.example.contactapp.ads.ColdStartAdType
import com.example.contactapp.ads.InterstitialAdManager
import com.example.contactapp.ads.NativeAdCache
import com.example.contactapp.ads.waitUntilAdReady
import com.example.contactapp.ui.theme.LocalIsDarkTheme
import com.example.contactapp.ui.theme.PrimaryGreen
import com.example.contactapp.viewmodel.AppConfigViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Each letter of the app name slides in from the right, staggered one after another below the
 * logo — long enough to read comfortably, short enough that returning users (the common case)
 * aren't kept waiting. */
private const val LetterStaggerMs = 90L
private const val LetterStartDelayMs = 650L

/** Minimum time the branding animation gets to play before this screen hands off to the next
 * one, regardless of how quickly config/ads resolve — otherwise on a fast/cached run the icon
 * and typing text would barely flash on screen before being replaced. */
private const val SplashMinDurationMs = 2000L

/**
 * Cold-start gate: plays a brief logo + letter-slide-in branding animation, and — once setup is
 * fully done ([isFullySetUp]) — gives the App Open/Interstitial ad a real chance to finish
 * loading before the user lands on Recents, since without a short bounded wait the ad is almost
 * never ready yet on a fresh process start. The ad wait never applies mid-onboarding — showing
 * an ad there would be jarring, and Language/etc. have their own placements.
 */
@Composable
fun SplashScreen(
    isFullySetUp: Boolean,
    onTimeout: () -> Unit
) {
    val context = LocalContext.current
    var showAdLoader by remember { mutableStateOf(false) }

    // The branding-animation state below forces a white background regardless of theme
    // (deliberate — see that Box's own background comment), so status bar icons must stay dark
    // there to stay visible against it — MainActivity's own theme-based rule (light icons
    // whenever dark mode is on) would otherwise make them just as light as that white background
    // and functionally invisible. Once showAdLoader switches to the theme's own background color,
    // follow the theme normally like MainActivity does everywhere else in the app. MainActivity
    // skips setting this itself while this screen is showing, so this is the only thing driving
    // it for as long as Splash is on screen.
    val isDarkTheme = LocalIsDarkTheme.current
    val view = LocalView.current
    SideEffect {
        val window = (context as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            val lightIcons = if (showAdLoader) !isDarkTheme else true
            insetsController.isAppearanceLightStatusBars = lightIcons
            insetsController.isAppearanceLightNavigationBars = lightIcons
        }
    }

    // Shares the same AppConfigViewModel instance created in MainActivity (Activity-scoped).
    val appConfigViewModel: AppConfigViewModel = viewModel(context as ComponentActivity)
    val adConfig by appConfigViewModel.appResponse.collectAsState()

    // Preload as soon as config is available — runs in parallel with the brief wait below, so
    // both the App Open ad and whichever screen we're about to land on are as close to ready as
    // possible by the time navigation actually happens.
    LaunchedEffect(adConfig, isFullySetUp) {
        val result = adConfig?.result ?: return@LaunchedEffect
        if (result.google_ads_on_off != "on") return@LaunchedEffect
        if (result.app_open_1_on_off == "on") {
            result.app_open_1?.takeIf { it.isNotBlank() }?.let {
                AppOpenAdManager.preload(context, it)
            }
        }
        // Cold-start rotation's Interstitial slot (see AppOpenCounter) — preloaded unconditionally
        // so it's ready by the time the 3rd/6th/... cold open needs it, same as the App Open ad.
        if (result.interstitial_2_on_off == "on") {
            result.interstitial_2?.takeIf { it.isNotBlank() }?.let {
                InterstitialAdManager.preload(context, it)
            }
        }
        if (!isFullySetUp) {
            // First-time user — onboarding routes through the Language screen next, which shows
            // an interstitial right after "Done" is tapped — preload it now so it's ready by then.
            if (result.native_1_on_off == "on") {
                result.native_1?.takeIf { it.isNotBlank() }?.let {
                    NativeAdCache.preload(context, it)
                }
            }
            if (result.interstitial_1_on_off == "on") {
                result.interstitial_1?.takeIf { it.isNotBlank() }?.let {
                    InterstitialAdManager.preload(context, it)
                }
            }
        }
        // Recents (Home) is reached either way in this same cold start — right after onboarding
        // for a first-time user, or immediately for a returning one — so its ads are preloaded
        // unconditionally too. Without this, a fresh install's very first Home screen had to load
        // its banner from scratch with no head start, unlike every app open after the first.
        if (result.native_2_on_off == "on") {
            result.native_2?.takeIf { it.isNotBlank() }?.let {
                NativeAdCache.preload(context, it)
            }
        }
        if (result.banner_1_on_off == "on") {
            result.banner_1?.takeIf { it.isNotBlank() }?.let {
                BannerAdCache.preload(context, it)
            }
        }
        // Bottom nav tries native_6 before falling back to banner_1 (see NativeOrBannerAdView) —
        // preload it too, otherwise the native-first attempt always starts from scratch on Home.
        if (result.native_6_on_off == "on") {
            result.native_6?.takeIf { it.isNotBlank() }?.let {
                NativeAdCache.preload(context, it)
            }
        }
    }

    LaunchedEffect(Unit) {
        // Let the branding animation actually finish playing before deciding where to go.
        delay(SplashMinDurationMs)

        if (isFullySetUp) {
            // Give the remote config fetch a moment to resolve — right after composition,
            // appResponse.value is still whatever loadCachedResponse() returned (often null on
            // a fresh install), so the ad check below would otherwise almost always find nothing.
            var waited = 0L
            while (appConfigViewModel.appResponse.value == null && waited < 2000L) {
                delay(150)
                waited += 150
            }

            val activity = context as? Activity
            val result = appConfigViewModel.appResponse.value?.result
            // google_ads_on_off reflects the last successful config fetch (cached or live), so it
            // can still read "on" even while currently offline — checking isOnline too avoids
            // showing "Ad is loading" for the full timeout only to fail, when there's clearly no
            // network to load an ad with in the first place.
            val adsEnabled = result?.google_ads_on_off == "on" && appConfigViewModel.isOnline.value

            // 1st/2nd cold open after setup → App Open; 3rd (and every 3rd after) → Interstitial.
            val openCount = AppOpenCounter.incrementAndGet(context)
            val adType = AppOpenCounter.adTypeFor(openCount)

            if (adsEnabled && activity != null && adType == ColdStartAdType.APP_OPEN) {
                val adUnitId = result?.app_open_1?.takeIf { result.app_open_1_on_off == "on" && it.isNotBlank() }
                if (adUnitId != null) {
                    if (!AppOpenAdManager.isReady()) {
                        showAdLoader = true
                        waitUntilAdReady { AppOpenAdManager.isReady() }
                    }
                    if (AppOpenAdManager.isReady()) {
                        AppOpenAdManager.show(activity, adUnitId) { onTimeout() }
                        return@LaunchedEffect
                    }
                }
            } else if (adsEnabled && activity != null && adType == ColdStartAdType.INTERSTITIAL) {
                val interstitialAdUnitId = result?.interstitial_2?.takeIf { result.interstitial_2_on_off == "on" && it.isNotBlank() }
                if (interstitialAdUnitId != null) {
                    if (!InterstitialAdManager.isReady(interstitialAdUnitId)) {
                        showAdLoader = true
                        waitUntilAdReady { InterstitialAdManager.isReady(interstitialAdUnitId) }
                    }
                    if (InterstitialAdManager.isReady(interstitialAdUnitId)) {
                        InterstitialAdManager.show(activity, interstitialAdUnitId) { onTimeout() }
                        return@LaunchedEffect
                    }
                }
            }
        }
        onTimeout()
    }

    // White background during the branding animation, with the logo/app-name in brand green
    // instead of white-on-green. The ad-loading state isn't part of that branded handoff moment,
    // so it keeps using the app's normal light/dark background instead of forcing white.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (showAdLoader) MaterialTheme.colorScheme.background else Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (showAdLoader) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AdLoadingLottie(modifier = Modifier.size(200.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.ad_is_loading),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp
                )
            }
        } else {
            BrandingAnimation()
        }
    }
}

/** Logo pop-in (bouncy scale + fade) followed by the app name below it — each letter slides in
 * from the right and settles into place, staggered one after another (no cursor, tight spacing). */
@Composable
private fun BrandingAnimation() {
    val fullText = stringResource(R.string.app_name)
    val letters = remember(fullText) { fullText.toList() }
    val offsets = remember(fullText) { letters.map { Animatable(28f) } }
    val alphas = remember(fullText) { letters.map { Animatable(0f) } }

    LaunchedEffect(fullText) {
        letters.indices.forEach { index ->
            launch {
                delay(LetterStartDelayMs + index * LetterStaggerMs)
                launch { alphas[index].animateTo(1f, animationSpec = tween(durationMillis = 350)) }
                offsets[index].animateTo(
                    0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
        }
    }

    Column(
        modifier = Modifier.padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SplashLogo()
        Spacer(modifier = Modifier.height(14.dp))
        Row {
            letters.forEachIndexed { index, letter ->
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryGreen,
                    modifier = Modifier
                        .offset(x = offsets[index].value.dp)
                        .alpha(alphas[index].value)
                )
            }
        }
    }
}

/** Two small dots slide in from either side and meet at the center — once "connected", they
 * crossfade into the app logo mark underneath. */
@Composable
private fun SplashLogo() {
    val dotOffset = remember { Animatable(1f) } // 1 = apart at the sides, 0 = merged at center
    val dotsAlpha = remember { Animatable(1f) }
    val circleAlpha = remember { Animatable(0f) }
    val circleScale = remember { Animatable(0.7f) }

    LaunchedEffect(Unit) {
        dotOffset.animateTo(
            0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
        // Dots have met — swap them out for the real logo circle underneath.
        launch { dotsAlpha.animateTo(0f, animationSpec = tween(durationMillis = 150)) }
        launch { circleAlpha.animateTo(1f, animationSpec = tween(durationMillis = 200)) }
        circleScale.animateTo(
            1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        )
    }

    Box(
        modifier = Modifier.size(104.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .offset(x = (-41).dp * dotOffset.value)
                .alpha(dotsAlpha.value)
                .clip(CircleShape)
                .background(PrimaryGreen.copy(alpha = 0.5f))
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .offset(x = 41.dp * dotOffset.value)
                .alpha(dotsAlpha.value)
                .clip(CircleShape)
                .background(PrimaryGreen.copy(alpha = 0.5f))
        )

        Image(
            painter = painterResource(R.drawable.logo_cotntacts),
            contentDescription = null,
            modifier = Modifier
                .size(104.dp)
                .scale(circleScale.value)
                .alpha(circleAlpha.value)
        )
    }
}
