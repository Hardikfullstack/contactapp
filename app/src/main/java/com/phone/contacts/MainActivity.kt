package com.phone.contacts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.phone.contacts.ui.features.onboarding.LanguageSelectionScreen
import com.phone.contacts.ui.features.splash.SplashScreen
import com.phone.contacts.ui.navigation.MainNavigation
import com.phone.contacts.ui.theme.ContactsTheme
import com.phone.contacts.util.AppThemePreferences
import com.phone.contacts.util.DeviceUtils
import com.phone.contacts.util.OnboardingPreferences
import com.phone.contacts.util.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                var showSplash by remember { mutableStateOf(true) }
                var isLanguageSelected by remember { mutableStateOf(OnboardingPreferences.isLanguageSelected(context)) }

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

                when {
                    showSplash -> SplashScreen(onTimeout = { showSplash = false })
                    !isLanguageSelected -> LanguageSelectionScreen(
                        onDone = {
                            OnboardingPreferences.setLanguageSelected(context)
                            isLanguageSelected = true
                        }
                    )
                    else -> MainNavigation()
                }
            }
        }
    }
}
