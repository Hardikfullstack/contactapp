package com.example.contactapp

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
import com.example.contactapp.util.AnalyticsManager
import com.example.contactapp.util.LocaleChangeState
import com.example.contactapp.util.PreferenceManager
import com.example.contactapp.viewmodel.AppConfigViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        config.fontScale = config.fontScale.coerceAtMost(1.2f)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() — hands off from the system splash to our own
        // SplashScreen.kt composable as soon as the first frame draws (see themes.xml).
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
            // Re-verify on every resume — the check above only runs at cold start, so a permission
            // revoked while backgrounded would otherwise go unnoticed and strand the user on MainNavigation.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        if (isOnboardingCompleted && !hasRequiredPermissions()) {
                            isOnboardingCompleted = false
                        }
                        // Can change while backgrounded (role granted/revoked from system Settings).
                        AnalyticsManager.setUserProperty("is_default_dialer", if (isDefaultDialer()) "yes" else "no")
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(Unit) {
                AnalyticsManager.setUserProperty(
                    "app_language",
                    AppCompatDelegate.getApplicationLocales().toLanguageTags().ifEmpty { "system" }
                )
                AnalyticsManager.setUserProperty("is_default_dialer", if (isDefaultDialer()) "yes" else "no")
            }
            LaunchedEffect(appTheme) {
                AnalyticsManager.setUserProperty("app_theme", appTheme)
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

            // Bottom (gesture/nav) bar stays hidden throughout the app, not just during splash —
            // a swipe from the edge still reveals it briefly (standard immersive behavior).
            LaunchedEffect(Unit) {
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.navigationBars())
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

    // Only the permissions PermissionScreen.kt actually requests during onboarding now — Contacts
    // and Call Log are requested lazily on first use instead, so checking for them here would
    // always fail and incorrectly bounce the user back into onboarding right after it just finished.
    private fun hasRequiredPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE
        )
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isDefaultDialer(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = getSystemService(RoleManager::class.java)
        return roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    }
}

