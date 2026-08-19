package com.example.contactapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.contactapp.ui.navigation.MainNavigation
import com.example.contactapp.ui.navigation.OnboardingNavHost
import com.example.contactapp.ui.theme.ContactAppTheme
import com.example.contactapp.util.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
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

            val insetsController = remember { WindowCompat.getInsetsController(window, window.decorView) }
            SideEffect {
                insetsController.isAppearanceLightStatusBars = !isDarkTheme
                insetsController.isAppearanceLightNavigationBars = !isDarkTheme
            }

            ContactAppTheme(darkTheme = isDarkTheme) {
                if (isOnboardingCompleted) {
                    MainNavigation(preferenceManager = preferenceManager)
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

