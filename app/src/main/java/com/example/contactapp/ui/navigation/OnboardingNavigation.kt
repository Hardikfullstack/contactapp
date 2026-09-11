package com.example.contactapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.contactapp.ui.features.onboarding.AdvancedPermissionScreen
import com.example.contactapp.ui.features.onboarding.LanguageSelectionScreen
import com.example.contactapp.ui.features.onboarding.PermissionScreen
import com.example.contactapp.util.AnalyticsManager

sealed class OnboardingScreen(val route: String) {
    object Permission : OnboardingScreen("permission")
    object AdvancedPermission : OnboardingScreen("advanced_permission")
    object Language : OnboardingScreen("language")
}

@Composable
fun OnboardingNavHost(
    onBasicPermissionsGranted: () -> Unit = {},
    onOnboardingComplete: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = OnboardingScreen.Permission.route
    ) {
        composable(OnboardingScreen.Permission.route) {
            PermissionScreen(
                onContinue = {
                    onBasicPermissionsGranted()
                    navController.navigate(OnboardingScreen.AdvancedPermission.route)
                }
            )
        }
        composable(OnboardingScreen.AdvancedPermission.route) {
            AdvancedPermissionScreen(
                onAllPermissionsGranted = {
                    navController.navigate(OnboardingScreen.Language.route)
                }
            )
        }
        composable(OnboardingScreen.Language.route) {
            LanguageSelectionScreen(
                onDone = {
                    AnalyticsManager.logEventWithAction("onboarding_completed", "OnboardingNavHost", "finished")
                    onOnboardingComplete()
                },
                isFirstRun = true
            )
        }
    }
}
