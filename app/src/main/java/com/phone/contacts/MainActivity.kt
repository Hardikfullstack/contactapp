package com.phone.contacts

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.phone.contacts.ui.features.onboarding.LanguageSelectionScreen
import com.phone.contacts.ui.features.onboarding.MiuiPermissionDialog
import com.phone.contacts.ui.features.splash.SplashScreen
import com.phone.contacts.ui.navigation.MainNavigation
import com.phone.contacts.ui.theme.ContactsTheme
import com.phone.contacts.util.AnalyticsEvents
import com.phone.contacts.util.AnalyticsManager
import com.phone.contacts.util.AppThemePreferences
import com.phone.contacts.util.DefaultDialerState
import com.phone.contacts.util.DeviceUtils
import com.phone.contacts.util.OnboardingPreferences
import com.phone.contacts.util.ThemeMode

class MainActivity : AppCompatActivity() {
    companion object {
        /** Set by the After Call screen's "View Contact" - MainNavigation opens that contact's detail. */
        const val EXTRA_OPEN_NUMBER = "open_contact_number"
    }

    // Play Core's IMMEDIATE in-app-update flow is launched the classic startActivityForResult way
    // (see AppUpdateHelper.startUpdate), so its outcome arrives here and is forwarded through
    // InAppUpdateResult for whichever screen wants to react to it.
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == com.phone.contacts.util.InAppUpdateResult.REQUEST_CODE) {
            com.phone.contacts.util.InAppUpdateResult.pendingResultCode = resultCode
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.phone.contacts.util.AppConfigStore.config.value =
            com.phone.contacts.util.AppConfigStore.readCached(applicationContext)
        // Keeps the screen on while the app itself is open (Contacts/Recents/Keypad/Settings
        // etc.) - separate from CallActivity's own proximity-sensor-driven screen control during
        // an actual call; both are meant to coexist, not replace one another.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Remote config is fetched on each app open, like the sibling contactapp does.
        lifecycleScope.launch { com.phone.contacts.util.AppConfigStore.refresh(applicationContext) }
        enableEdgeToEdge()
        
        com.phone.contacts.util.AppThemePreferences.initialize(applicationContext)
        val resolvedDarkAtStart = when (com.phone.contacts.util.AppThemePreferences.themeMode.value) {
            com.phone.contacts.util.ThemeMode.DARK -> true
            com.phone.contacts.util.ThemeMode.LIGHT -> false
            else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !resolvedDarkAtStart
            isAppearanceLightNavigationBars = !resolvedDarkAtStart
        }

        setContent {
            val isDarkTheme = when (com.phone.contacts.util.AppThemePreferences.themeMode.value) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
            }
            ContactsTheme(darkTheme = isDarkTheme) {
                val context = LocalContext.current
                // rememberSaveable: a language change recreates this activity, and the splash must
                // not play again - the user should land back where they were, in the new language.
                var showSplash by rememberSaveable { mutableStateOf(true) }
                var isLanguageSelected by remember { mutableStateOf(OnboardingPreferences.isLanguageSelected(context)) }
                var showAppOpenLoader by remember { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()

                // Matches contactapp: shows an App Open ad whenever the app itself returns to the
                // foreground from being backgrounded (not on every screen change). Cold start has
                // its own separate every-3rd-launch cadence - see AppOpenCounter, wired into
                // SplashScreen's onTimeout below.
                val adConfig by com.phone.contacts.util.AppConfigStore.config.collectAsState()
                LaunchedEffect(adConfig) {
                    com.phone.contacts.ads.AdPlacements.adUnitId(
                        adConfig?.result,
                        com.phone.contacts.ads.AdType.APP_OPEN,
                        slot = 1
                    )?.let { com.phone.contacts.ads.AppOpenBackgroundReturnTrigger.init(application, it) }
                }

                // Only relevant once the app is already the default dialer - not part of the
                // upfront onboarding flow. Re-derived from DefaultDialerState (refreshed wherever
                // SetDefaultScreen's onSetAsDefault fires, e.g. ContactsScreen/RecentsScreen/
                // FavoritesScreen) every time that flips, so it reliably catches the moment
                // default-dialer is granted regardless of which tab the user was on.
                val isDefaultDialer by DefaultDialerState.isDefault
                var isMiuiPermissionGranted by remember {
                    mutableStateOf(
                        Settings.canDrawOverlays(context) && DeviceUtils.isMiuiBackgroundPermissionGranted(context)
                    )
                }
                val showMiuiPermissionDialog = isDefaultDialer && DeviceUtils.isMiui() && !isMiuiPermissionGranted

                // System bottom nav bar stays hidden throughout the app on 2/3-button navigation
                // devices (a swipe from the edge still reveals it briefly — standard immersive
                // behavior). On GESTURE navigation devices (OnePlus/Oppo/etc.), actually hiding
                // the bar via insetsController.hide() has been observed to also disable the OS's
                // own edge-swipe back gesture on some OEM skins — the bar being "hidden" and the
                // gesture-recognition zone are apparently the same thing to their implementation.
                // enableEdgeToEdge() alone already draws content behind that thin gesture pill
                // without needing to hide it, so gesture-nav devices skip the explicit hide and
                // keep working back-swipes — matches contactapp's own MainActivity.
                val insetsController = remember { WindowCompat.getInsetsController(window, window.decorView) }
                LaunchedEffect(Unit) {
                    if (!DeviceUtils.isGestureNavigationEnabled(context)) {
                        insetsController.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        insetsController.hide(WindowInsetsCompat.Type.navigationBars())
                    }
                }

                // Without this (API 33+), the call notification's checkNotificationPermission()
                // would silently stay false forever - declaring POST_NOTIFICATIONS in the manifest
                // alone doesn't grant it, it still needs this one-time runtime request. Deferred
                // until isDefaultDialer is actually true - there's no call notification to show
                // before that, so asking upfront on every fresh install would just be an unexplained
                // permission prompt before the user has any reason to say yes to it.
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }
                LaunchedEffect(isDefaultDialer) {
                    if (isDefaultDialer &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                when {
                    showSplash -> SplashScreen(onTimeout = {
                        // isLanguageSelected here reflects whatever it was when this cold start
                        // began (onboarding's own onDone can't have run yet - Splash is still the
                        // only thing on screen) - true means this is a returning user's cold
                        // start, which is the only case AppOpenCounter should ever count.
                        val activity = context as? android.app.Activity
                        val adUnitId = if (isLanguageSelected) {
                            com.phone.contacts.ads.AdPlacements.adUnitId(adConfig?.result, com.phone.contacts.ads.AdType.APP_OPEN, slot = 1)
                        } else null
                        val openCount = if (isLanguageSelected) com.phone.contacts.ads.AppOpenCounter.incrementAndGet(context) else 0
                        val isDue = isLanguageSelected && com.phone.contacts.ads.AppOpenCounter.shouldShowAppOpenAd(context, openCount)
                        if (adUnitId != null && activity != null && isDue) {
                            // The ad is still loading more often than not at this point - a cold
                            // start is also when Mobile Ads SDK itself is still warming up, so
                            // checking isReady() right away (no wait) almost always missed it.
                            // Give it a real, bounded chance instead of silently skipping.
                            coroutineScope.launch {
                                if (!com.phone.contacts.ads.AppOpenAdManager.isReady()) {
                                    // No point showing a loading state for the full bounded-wait
                                    // timeout when there's clearly no connectivity to load an ad
                                    // with — skip straight to the "didn't work" outcome instead.
                                    if (com.phone.contacts.ads.AdConnectivityRetry.isOnline(context)) {
                                        showAppOpenLoader = true
                                        com.phone.contacts.ads.waitUntilAdReady { com.phone.contacts.ads.AppOpenAdManager.isReady() }
                                        showAppOpenLoader = false
                                    }
                                }
                                if (com.phone.contacts.ads.AppOpenAdManager.isReady()) {
                                    com.phone.contacts.ads.AppOpenCounter.clearRetryPending(context)
                                    com.phone.contacts.ads.AppOpenAdManager.show(activity, adUnitId) { showSplash = false }
                                } else {
                                    // Still not ready after the bounded wait (e.g. no connectivity) -
                                    // don't let this due cycle go completely unattempted; retry on
                                    // the very next cold start regardless of the %3 cadence.
                                    com.phone.contacts.ads.AppOpenCounter.markRetryPending(context)
                                    showSplash = false
                                }
                            }
                        } else {
                            showSplash = false
                        }
                    })
                    !isLanguageSelected -> LanguageSelectionScreen(
                        onDone = {
                            OnboardingPreferences.setLanguageSelected(context)
                            isLanguageSelected = true
                            AnalyticsManager.logEventWithAction(AnalyticsEvents.ONBOARDING_COMPLETED, AnalyticsEvents.SCREEN_LANGUAGE, AnalyticsEvents.ACTION_FINISHED)
                        }
                    )
                    else -> {
                        MainNavigation()
                        if (showMiuiPermissionDialog) {
                            MiuiPermissionDialog(onGranted = { isMiuiPermissionGranted = true })
                        }
                    }
                }

                val isWaitingForBackgroundReturnAd by com.phone.contacts.ads.AppOpenBackgroundReturnTrigger.isWaitingForAd.collectAsState()
                if (showAppOpenLoader || isWaitingForBackgroundReturnAd) {
                    // A Dialog, not inline content - it needs to paint over SplashScreen's own
                    // Window-level content regardless of composition order (same reasoning as
                    // BackInterstitial's loading dialog).
                    Dialog(
                        onDismissRequest = {},
                        properties = DialogProperties(
                            dismissOnBackPress = false,
                            dismissOnClickOutside = false,
                            usePlatformDefaultWidth = false
                        )
                    ) {
                        com.phone.contacts.ads.AdLoadingScreen()
                    }
                }
            }
        }
    }
}
