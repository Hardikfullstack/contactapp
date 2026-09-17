package com.phone.contact.call.dialer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.phone.contact.call.dialer.ui.features.onboarding.AdvancedPermissionScreen
import com.phone.contact.call.dialer.ui.features.onboarding.LanguageSelectionScreen
import com.phone.contact.call.dialer.ui.features.onboarding.PermissionScreen
import com.phone.contact.call.dialer.ui.features.settings.LegalWebViewScreen
import com.phone.contact.call.dialer.util.AnalyticsManager

sealed class OnboardingScreen(val route: String) {
    object Permission : OnboardingScreen("permission")
    object AdvancedPermission : OnboardingScreen("advanced_permission")
    object Language : OnboardingScreen("language")
    object LegalWebView : OnboardingScreen("onboarding_legal_webview/{type}") {
        fun createRoute(type: String) = "onboarding_legal_webview/$type"
    }
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
                },
                onPrivacyPolicyClick = {
                    navController.navigate(OnboardingScreen.LegalWebView.createRoute("privacy"))
                }
            )
        }
        composable(OnboardingScreen.LegalWebView.route) { backStackEntry ->
            LegalWebViewScreen(
                onBack = { navController.popBackStack() },
                type = backStackEntry.arguments?.getString("type") ?: "privacy"
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
