package com.example.contactapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.contactapp.R
import com.example.contactapp.ui.features.recents.RecentsScreen
import com.example.contactapp.ui.features.recents.SearchScreen
import com.example.contactapp.ui.features.history.HistoryScreen
import com.example.contactapp.ui.features.favorites.FavoritesScreen
import com.example.contactapp.ui.features.contacts.ContactsScreen
import com.example.contactapp.ui.features.keypad.KeypadScreen
import com.example.contactapp.ui.features.settings.SettingsScreen
import com.example.contactapp.ui.features.settings.AfterCallSettingsScreen
import com.example.contactapp.ui.features.settings.BlockedNumbersScreen
import com.example.contactapp.ui.features.settings.RecycleBinScreen
import com.example.contactapp.ui.features.tools.ToolsScreen
import com.example.contactapp.ui.features.analytics.AnalyticsScreen
import com.example.contactapp.ui.features.announcer.CallAnnouncerScreen
import com.example.contactapp.ui.features.flash.FlashAlertScreen
import com.example.contactapp.ui.features.wallpaper.CallWallpaperScreen
import com.example.contactapp.ui.features.callthemes.CallThemeScreen
import com.example.contactapp.ui.features.ringtone.RingtoneScreen
import com.example.contactapp.ui.features.autoreply.AutoReplyScreen
import com.example.contactapp.ui.features.fakecall.FakeCallSetupScreen
import com.example.contactapp.ui.features.callreminder.CallReminderScreen
import com.example.contactapp.ui.features.callreminder.CallReminderSetupScreen
import com.example.contactapp.ui.features.onboarding.LanguageSelectionScreen
import com.example.contactapp.ads.BannerAdView
import com.example.contactapp.ui.components.CommonBottomBar
import com.example.contactapp.ui.components.BottomBarActionItem
import com.example.contactapp.util.AnalyticsManager
import com.example.contactapp.util.PreferenceManager
import com.example.contactapp.viewmodel.AppConfigViewModel

sealed class MainScreen(
    val route: String,
    val labelRes: Int? = null,
    val icon: ImageVector? = null,
    val selectedIcon: ImageVector? = null
) {
    object Recents : MainScreen("recents", R.string.recents, Icons.Outlined.AccessTime, Icons.Filled.AccessTimeFilled)
    object Contacts : MainScreen("contacts", R.string.contacts, Icons.Outlined.PersonOutline, Icons.Filled.Person)
    object Tools : MainScreen("tools", R.string.tools, Icons.Outlined.Window, Icons.Filled.Window)
    object Favorites : MainScreen("favorites", R.string.favorites, Icons.Outlined.StarOutline, Icons.Filled.Star)
    object Settings : MainScreen("settings", R.string.settings, Icons.Outlined.Settings, Icons.Filled.Settings)
    object Keypad : MainScreen("keypad")
    object Search : MainScreen("search")
    object History : MainScreen("history/{name}/{number}") {
        fun createRoute(name: String, number: String) = "history/$name/$number"
    }
    object BlockedNumbers : MainScreen("blocked_numbers")
    object RecycleBin : MainScreen("recycle_bin")
    object AfterCall : MainScreen("after_call_settings")
    object Language : MainScreen("language_settings")
    object Analytics : MainScreen("analytics")
    object CallAnnouncer : MainScreen("call_announcer")
    object FlashAlert : MainScreen("flash_alert")
    object CallWallpaper : MainScreen("call_wallpaper")
    object CallThemes : MainScreen("call_themes")
    object Ringtone : MainScreen("ringtone")
    object ContactRingtone : MainScreen("contact_ringtone/{name}/{number}") {
        fun createRoute(name: String, number: String) = "contact_ringtone/$name/$number"
    }
    object AutoReply : MainScreen("auto_reply")
    object FakeCallSetup : MainScreen("fake_call_setup")
    object CallReminder : MainScreen("call_reminder")
    object CallReminderSetup : MainScreen("call_reminder_setup")
}

@Composable
fun MainNavigation(preferenceManager: PreferenceManager, startTab: String? = null) {
    val navController = rememberNavController()
    // "contacts"/"recents" as passed via MainActivity's open_tab intent extra (e.g. from the
    // After Call screen) — any other/missing value falls back to the normal default tab.
    val startDestination = when (startTab) {
        "contacts" -> MainScreen.Contacts.route
        "recents" -> MainScreen.Recents.route
        else -> MainScreen.Recents.route
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Matches the Messages app's pattern: a plain OnDestinationChangedListener, with the route
    // trimmed to its base segment (drops "/{name}/{number}"-style args) for a stable screen name.
    DisposableEffect(navController) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
            val screenName = destination.route?.substringBefore("/")?.substringBefore("?") ?: "unknown"
            AnalyticsManager.logScreenView(screenName)
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    // Memoize navigation items to prevent redundant re-calculations
    val navItems = remember {
        listOf(
            MainScreen.Recents,
            MainScreen.Contacts,
            MainScreen.Tools,
            MainScreen.Favorites,
            MainScreen.Settings
        )
    }

    val showBottomBar = remember(currentDestination) {
        navItems.any { it.route == currentDestination?.route }
    }

    // Shares the same AppConfigViewModel instance created in MainActivity (Activity-scoped),
    // so the remote ad config isn't refetched per screen.
    val context = LocalContext.current
    val appConfigViewModel: AppConfigViewModel = viewModel(context as ComponentActivity)
    val adConfig by appConfigViewModel.appResponse.collectAsState()
    val bannerAdUnitId = adConfig?.result?.let { result ->
        if (result.google_ads_on_off == "on" && result.banner_1_on_off == "on") {
            result.banner_1?.takeIf { it.isNotBlank() }
        } else null
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                val currentRoute = currentDestination?.route
                val items = navItems.map { screen ->
                    val label = stringResource(screen.labelRes!!)
                    remember(currentRoute, label) {
                        BottomBarActionItem(
                            icon = screen.icon!!,
                            selectedIcon = screen.selectedIcon,
                            label = label,
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
                Column(modifier = Modifier.navigationBarsPadding()) {
                    CommonBottomBar(items = items, windowInsets = WindowInsets(0.dp))
                    if (bannerAdUnitId != null) {
                        BannerAdView(adUnitId = bannerAdUnitId)
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp)
        ) {
            composable(MainScreen.Recents.route) {
                RecentsScreen(
                    onSearchClick = { navController.navigate(MainScreen.Search.route) },
                    onHistoryClick = { name, number ->
                        navController.navigate(MainScreen.History.createRoute(name, number))
                    },
                    onKeypadClick = { navController.navigate(MainScreen.Keypad.route) }
                )
            }
            composable(MainScreen.Search.route) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onContactClick = { name, number ->
                        navController.navigate(MainScreen.History.createRoute(name, number))
                    }
                )
            }
            composable(MainScreen.History.route) {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onRingtoneClick = { name, number ->
                        navController.navigate(MainScreen.ContactRingtone.createRoute(name, number))
                    }
                )
            }
            composable(MainScreen.Favorites.route) {
                FavoritesScreen(
                    onContactClick = { name, number ->
                        navController.navigate(MainScreen.History.createRoute(name, number))
                    }
                )
            }
            composable(MainScreen.Tools.route) {
                ToolsScreen(
                    onRecycleBinClick = { navController.navigate(MainScreen.RecycleBin.route) },
                    onAnalyticsClick = { navController.navigate(MainScreen.Analytics.route) },
                    onFakeCallClick = { navController.navigate(MainScreen.FakeCallSetup.route) },
                    onAnnouncerClick = { navController.navigate(MainScreen.CallAnnouncer.route) },
                    onFlashAlertClick = { navController.navigate(MainScreen.FlashAlert.route) },
                    onWallpaperClick = { navController.navigate(MainScreen.CallWallpaper.route) },
                    onCallThemesClick = { navController.navigate(MainScreen.CallThemes.route) },
                    onSetRingtoneClick = { navController.navigate(MainScreen.Ringtone.route) },
                    onAutoReplyClick = { navController.navigate(MainScreen.AutoReply.route) },
                    onKeypadClick = { navController.navigate(MainScreen.Keypad.route) },
                    onCallReminderClick = { navController.navigate(MainScreen.CallReminder.route) }
                )
            }
            composable(MainScreen.Keypad.route) { 
                KeypadScreen(
                    onSearchClick = { navController.navigate(MainScreen.Search.route) },
                    onBackClick = { navController.popBackStack() },
                    preferenceManager = preferenceManager
                )
            }
            composable(MainScreen.Contacts.route) { 
                ContactsScreen(
                    onContactClick = { name, number ->
                        navController.navigate(MainScreen.History.createRoute(name, number))
                    },
                    onSearchClick = { navController.navigate(MainScreen.Search.route) }
                )
            }
            composable(MainScreen.Settings.route) { 
                SettingsScreen(
                    onBlockedNumbersClick = { navController.navigate(MainScreen.BlockedNumbers.route) },
                    onLanguageClick = { navController.navigate(MainScreen.Language.route) },
                    onRecycleBinClick = { navController.navigate(MainScreen.RecycleBin.route) },
                    onAfterCallClick = { navController.navigate(MainScreen.AfterCall.route) }
                )
            }
            composable(MainScreen.BlockedNumbers.route) {
                BlockedNumbersScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(MainScreen.AfterCall.route) {
                AfterCallSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(MainScreen.RecycleBin.route) {
                RecycleBinScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(MainScreen.Analytics.route) {
                AnalyticsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(MainScreen.CallAnnouncer.route) {
                CallAnnouncerScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(MainScreen.FlashAlert.route) {
                FlashAlertScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(MainScreen.CallWallpaper.route) {
                CallWallpaperScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(MainScreen.CallThemes.route) {
                CallThemeScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(MainScreen.Ringtone.route) {
                RingtoneScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(MainScreen.ContactRingtone.route) {
                RingtoneScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(MainScreen.AutoReply.route) {
                AutoReplyScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(MainScreen.FakeCallSetup.route) {
                FakeCallSetupScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(MainScreen.CallReminder.route) {
                CallReminderScreen(
                    onBack = { navController.popBackStack() },
                    onAddReminder = { navController.navigate(MainScreen.CallReminderSetup.route) }
                )
            }
            composable(MainScreen.CallReminderSetup.route) {
                CallReminderSetupScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(MainScreen.Language.route) {
                LanguageSelectionScreen(
                    onDone = { navController.popBackStack() },
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}

