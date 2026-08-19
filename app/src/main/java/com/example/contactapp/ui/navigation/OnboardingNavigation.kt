package com.example.contactapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.contactapp.ui.features.onboarding.LanguageSelectionScreen
import com.example.contactapp.ui.features.onboarding.PermissionScreen

sealed class OnboardingScreen(val route: String) {
    object Permission : OnboardingScreen("permission")
    object Language : OnboardingScreen("language")
}

@Composable
fun OnboardingNavHost(
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
                    navController.navigate(OnboardingScreen.Language.route)
                }
            )
        }
        composable(OnboardingScreen.Language.route) {
            LanguageSelectionScreen(
                onDone = {
                    onOnboardingComplete()
                }
            )
        }
    }
}
