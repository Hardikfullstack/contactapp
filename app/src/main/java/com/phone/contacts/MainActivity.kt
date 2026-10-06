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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.phone.contacts.ui.features.onboarding.LanguageSelectionScreen
import com.phone.contacts.ui.features.onboarding.MiuiPermissionDialog
import com.phone.contacts.ui.features.splash.SplashScreen
import com.phone.contacts.ui.navigation.MainNavigation
import com.phone.contacts.ui.theme.ContactsTheme
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keeps the screen on while the app itself is open (Contacts/Recents/Keypad/Settings
        // etc.) - separate from CallActivity's own proximity-sensor-driven screen control during
        // an actual call; both are meant to coexist, not replace one another.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Remote config is fetched on each app open, like the sibling contactapp does.
        lifecycleScope.launch { com.phone.contacts.util.AppConfigStore.refresh(applicationContext) }
        enableEdgeToEdge()
        setContent {
            val themeContext = LocalContext.current
            remember { AppThemePreferences.initialize(themeContext) }
            val isDarkTheme = when (AppThemePreferences.themeMode.value) {
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
                    showSplash -> SplashScreen(onTimeout = { showSplash = false })
                    !isLanguageSelected -> LanguageSelectionScreen(
                        onDone = {
                            OnboardingPreferences.setLanguageSelected(context)
                            isLanguageSelected = true
                        }
                    )
                    else -> {
                        MainNavigation()
                        if (showMiuiPermissionDialog) {
                            MiuiPermissionDialog(onGranted = { isMiuiPermissionGranted = true })
                        }
                    }
                }
            }
        }
    }
}
