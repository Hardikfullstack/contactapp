package com.example.contactapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.contactapp.ads.AppOpenBackgroundReturnTrigger
import com.example.contactapp.ui.features.splash.SplashScreen
import com.example.contactapp.ui.navigation.MainNavigation
import com.example.contactapp.ui.navigation.OnboardingNavHost
import com.example.contactapp.ui.theme.ContactAppTheme
import com.example.contactapp.util.LocaleChangeState
import com.example.contactapp.util.PreferenceManager
import com.example.contactapp.viewmodel.AppConfigViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() — hands off from the system splash (Theme.App.Starting,
        // see themes.xml) to postSplashScreenTheme as soon as this Activity's first frame is
        // drawn, so our own SplashScreen.kt composable takes over instead of the system splash
        // lingering (its default dismiss condition is "first frame drawn", which is what we want).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keeps the screen from auto-sleeping for as long as the app is in the foreground,
        // on any screen — not just calls (InCallActivity already sets this separately).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            // Instantiated here so its init{} fires and fetches the remote ad/app config on
            // startup, and shared (Activity-scoped) with any nested screen — e.g.
            // LanguageSelectionScreen — that reads the same config via viewModel(activity).
            val appConfigViewModel: AppConfigViewModel = viewModel()
            val adConfig by appConfigViewModel.appResponse.collectAsState()

            LaunchedEffect(adConfig) {
                val result = adConfig?.result ?: return@LaunchedEffect
                if (result.google_ads_on_off != "on") return@LaunchedEffect
                if (result.app_open_1_on_off == "on") {
                    result.app_open_1?.takeIf { it.isNotBlank() }?.let {
                        AppOpenBackgroundReturnTrigger.init(application, it)
                    }
                }
            }

            // High-performance theme observation
            val appTheme by preferenceManager.themeFlow.collectAsState(initial = preferenceManager.getAppTheme())
            val systemInDark = isSystemInDarkTheme()

            val isDarkTheme = remember(appTheme, systemInDark) {
                when (appTheme) {
                    "Dark" -> true
                    "Light" -> false
                    else -> systemInDark
                }
            }

            var isOnboardingCompleted by remember {
                mutableStateOf(preferenceManager.isOnboardingCompleted() && hasRequiredPermissions())
            }
            // The check above only runs once, at cold start — if the user backgrounds the app
            // (e.g. to revoke a permission from system Settings, or Android auto-revokes an
            // unused one) and comes back, that stale state would otherwise never notice and the
            // app would stay on MainNavigation with a core permission actually missing. Re-verify
            // on every resume and drop back to onboarding the moment one of these required
            // permissions is no longer granted.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME && isOnboardingCompleted && !hasRequiredPermissions()) {
                        isOnboardingCompleted = false
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            // Set by AfterCallActivity when the user taps Contact/Recent Call there — jumping
            // straight to that tab should feel instant, not re-trigger this app's own cold-start
            // splash (and the App Open ad it can show) on top of a screen they already just left.
            val skipSplash = remember {
                val fromIntent = intent?.getBooleanExtra("skip_splash", false) == true
                val fromLocaleChange = LocaleChangeState.skipNextSplash
                if (fromLocaleChange) LocaleChangeState.skipNextSplash = false
                fromIntent || fromLocaleChange
            }
            var showSplash by remember { mutableStateOf(!skipSplash) }
            val startTab = remember { intent?.getStringExtra("open_tab") }

            val insetsController = remember { WindowCompat.getInsetsController(window, window.decorView) }
            SideEffect {
                insetsController.isAppearanceLightStatusBars = !isDarkTheme
                insetsController.isAppearanceLightNavigationBars = !isDarkTheme
            }

            // Bottom (gesture/nav) bar stays hidden for the duration of the splash branding
            // animation — it reappears as soon as we hand off to onboarding/main navigation.
            LaunchedEffect(showSplash) {
                if (showSplash) {
                    insetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    insetsController.hide(WindowInsetsCompat.Type.navigationBars())
                } else {
                    insetsController.show(WindowInsetsCompat.Type.navigationBars())
                }
            }

            ContactAppTheme(darkTheme = isDarkTheme) {
                if (showSplash) {
                    SplashScreen(
                        isFullySetUp = isOnboardingCompleted,
                        onTimeout = { showSplash = false }
                    )
                } else if (isOnboardingCompleted) {
                    MainNavigation(preferenceManager = preferenceManager, startTab = startTab)
                } else {
                    OnboardingNavHost(
                        onOnboardingComplete = {
                            preferenceManager.setOnboardingCompleted(true)
                            isOnboardingCompleted = true
                        }
                    )
                }
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG
        )
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}

