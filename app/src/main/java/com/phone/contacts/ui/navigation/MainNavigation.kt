package com.phone.contacts.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTimeFilled
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.phone.contacts.R
import com.phone.contacts.data.Contact
import com.phone.contacts.ui.components.BottomBarActionItem
import com.phone.contacts.ui.components.CommonBottomBar
import com.phone.contacts.ui.features.onboarding.LanguageSelectionScreen
import com.phone.contacts.ui.screens.AddContactScreen
import com.phone.contacts.ui.screens.BlockingScreen
import com.phone.contacts.ui.screens.CallButtonStylesScreen
import com.phone.contacts.ui.screens.ContactDetailScreen
import com.phone.contacts.ui.screens.ContactsScreen
import com.phone.contacts.ui.screens.FavoritesScreen
import com.phone.contacts.ui.screens.ImportExportScreen
import com.phone.contacts.ui.screens.KeypadScreen
import com.phone.contacts.ui.screens.ManageBlockListScreen
import com.phone.contacts.ui.screens.RecentsScreen
import com.phone.contacts.ui.screens.RecycleBinScreen
import com.phone.contacts.ui.screens.SettingsScreen
import com.phone.contacts.ui.screens.ThemeScreen
import com.phone.contacts.util.DefaultDialerState

sealed class MainScreen(
    val route: String,
    val labelRes: Int? = null,
    val icon: ImageVector? = null,
    val selectedIcon: ImageVector? = null
) {
    object Recents : MainScreen("recents", R.string.recents, Icons.Outlined.AccessTime, Icons.Filled.AccessTimeFilled)
    object Contacts : MainScreen("contacts", R.string.contacts, Icons.Outlined.PersonOutline, Icons.Filled.Person)
    object Favorites : MainScreen("favorites", R.string.favorites, Icons.Outlined.StarOutline, Icons.Filled.Star)
    object Keypad : MainScreen("keypad", R.string.keypad, Icons.Outlined.Dialpad, Icons.Filled.Dialpad)
    object Settings : MainScreen("settings", R.string.settings, Icons.Outlined.Settings, Icons.Filled.Settings)
    object Language : MainScreen("language_settings")
    object RecycleBin : MainScreen("recycle_bin")
    object ImportExport : MainScreen("import_export")
    object Theme : MainScreen("theme_settings")
    object CallButtonStyles : MainScreen("call_button_styles")
    object Blocking : MainScreen("blocking")
    object ManageBlockList : MainScreen("manage_block_list")
    object AddContact : MainScreen("add_contact?phone={phone}&editId={editId}") {
        fun routeWithPhone(phone: String) = "add_contact?phone=${Uri.encode(phone)}"
        fun routeForEdit(contactId: String) = "add_contact?phone=&editId=${Uri.encode(contactId)}"
    }
    object ContactDetail : MainScreen("contact_detail?id={id}&name={name}&number={number}&photoUri={photoUri}&starred={starred}") {
        fun routeFor(contact: Contact) =
            routeFor(id = contact.id, name = contact.name, number = contact.number, photoUri = contact.photoUri, starred = contact.isStarred)

        /** [id] is null when opened from a Recents entry that isn't a saved contact. */
        fun routeFor(id: String?, name: String?, number: String, photoUri: String? = null, starred: Boolean = false): String {
            val safeName = Uri.encode(name?.ifBlank { number } ?: number)
            return "contact_detail?id=${Uri.encode(id.orEmpty())}&name=$safeName" +
                "&number=${Uri.encode(number)}&photoUri=${Uri.encode(photoUri.orEmpty())}&starred=$starred"
        }
    }
}

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // App-wide default-dialer state — refreshed here (not just inside Recents) so Keypad/
    // Settings' disabled state reacts no matter which tab happens to be visible when the role is
    // actually granted (e.g. via system Settings). No forced navigation on the transition —
    // Recents/Contacts/Favorites each gate on this themselves and simply reveal their own real
    // content once it flips true, so whichever of those three the user was already on is exactly
    // what they land on (no jump to Recents specifically).
    remember { DefaultDialerState.refresh(context) }
    val isDefaultDialer by DefaultDialerState.isDefault
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) DefaultDialerState.refresh(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val navItems = remember {
        listOf(
            MainScreen.Recents,
            MainScreen.Contacts,
            MainScreen.Favorites,
            MainScreen.Keypad,
            MainScreen.Settings
        )
    }
    val showBottomBar = navItems.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                val items = navItems.map { screen ->
                    val label = stringResource(screen.labelRes!!)
                    // Recents/Contacts/Favorites show their own set-as-default prompt when not
                    // yet the default dialer; Keypad/Settings just disable outright instead.
                    val isGatedByDefaultDialer = screen == MainScreen.Keypad || screen == MainScreen.Settings
                    BottomBarActionItem(
                        icon = screen.icon!!,
                        selectedIcon = screen.selectedIcon,
                        label = label,
                        selected = currentRoute == screen.route,
                        enabled = isDefaultDialer || !isGatedByDefaultDialer,
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
                Column(modifier = Modifier.navigationBarsPadding()) {
                    CommonBottomBar(items = items, windowInsets = WindowInsets(0.dp))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainScreen.Recents.route,
            modifier = Modifier.padding(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp)
        ) {
            composable(MainScreen.Recents.route) {
                RecentsScreen(
                    onContactClick = { name, number ->
                        navController.navigate(MainScreen.ContactDetail.routeFor(id = null, name = name, number = number))
                    },
                    onAddToContact = { number ->
                        navController.navigate(MainScreen.AddContact.routeWithPhone(number))
                    }
                )
            }
            composable(MainScreen.Contacts.route) {
                ContactsScreen(
                    onAddContactClick = { navController.navigate(MainScreen.AddContact.routeWithPhone("")) },
                    onContactClick = { contact -> navController.navigate(MainScreen.ContactDetail.routeFor(contact)) }
                )
            }
            composable(
                route = MainScreen.ContactDetail.route,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; defaultValue = "" },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    navArgument("number") { type = NavType.StringType; defaultValue = "" },
                    navArgument("photoUri") { type = NavType.StringType; defaultValue = "" },
                    navArgument("starred") { type = NavType.BoolType; defaultValue = false }
                )
            ) { backStackEntry ->
                val args = backStackEntry.arguments
                // AddContactScreen sets this on our own entry (via previousBackStackEntry) right
                // before popping back after a real save — survives our composable being torn down
                // and recreated while AddContact is on top (a plain `remember` flag doesn't).
                val contactUpdated by backStackEntry.savedStateHandle
                    .getStateFlow("contact_updated", false)
                    .collectAsState()
                ContactDetailScreen(
                    contactId = args?.getString("id").orEmpty().ifBlank { null },
                    name = args?.getString("name").orEmpty(),
                    number = args?.getString("number").orEmpty(),
                    photoUri = args?.getString("photoUri").orEmpty().ifBlank { null },
                    isStarred = args?.getBoolean("starred") ?: false,
                    contactUpdated = contactUpdated,
                    onContactUpdatedConsumed = { backStackEntry.savedStateHandle["contact_updated"] = false },
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                    onEditClick = { id -> navController.navigate(MainScreen.AddContact.routeForEdit(id)) }
                )
            }
            composable(
                route = MainScreen.AddContact.route,
                arguments = listOf(
                    navArgument("phone") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("editId") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val initialPhone = backStackEntry.arguments?.getString("phone").orEmpty()
                val editId = backStackEntry.arguments?.getString("editId").orEmpty().ifBlank { null }
                AddContactScreen(
                    onClose = { saved ->
                        if (saved) {
                            navController.previousBackStackEntry?.savedStateHandle?.set("contact_updated", true)
                        }
                        navController.popBackStack()
                    },
                    initialPhone = initialPhone,
                    editContactId = editId
                )
            }
            composable(MainScreen.Favorites.route) {
                FavoritesScreen(
                    onAddFavoriteClick = {
                        navController.navigate(MainScreen.Contacts.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onFavoriteClick = { contact -> navController.navigate(MainScreen.ContactDetail.routeFor(contact)) }
                )
            }
            composable(MainScreen.Keypad.route) {
                KeypadScreen(
                    onAddNumberClick = { number ->
                        navController.navigate(MainScreen.AddContact.routeWithPhone(number))
                    }
                )
            }
            composable(MainScreen.Settings.route) {
                SettingsScreen(
                    onLanguageClick = { navController.navigate(MainScreen.Language.route) },
                    onRecycleBinClick = { navController.navigate(MainScreen.RecycleBin.route) },
                    onImportExportClick = { navController.navigate(MainScreen.ImportExport.route) },
                    onThemeClick = { navController.navigate(MainScreen.Theme.route) },
                    onBlockingClick = { navController.navigate(MainScreen.Blocking.route) },
                    onCallButtonStylesClick = { navController.navigate(MainScreen.CallButtonStyles.route) }
                )
            }
            composable(MainScreen.Language.route) {
                LanguageSelectionScreen(
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(MainScreen.RecycleBin.route) {
                RecycleBinScreen(onBack = { navController.popBackStack() })
            }
            composable(MainScreen.ImportExport.route) {
                ImportExportScreen(onBack = { navController.popBackStack() })
            }
            composable(MainScreen.Theme.route) {
                ThemeScreen(onBack = { navController.popBackStack() })
            }
            composable(MainScreen.CallButtonStyles.route) {
                CallButtonStylesScreen(onBack = { navController.popBackStack() })
            }
            composable(MainScreen.Blocking.route) {
                BlockingScreen(
                    onBack = { navController.popBackStack() },
                    onManageBlockList = { navController.navigate(MainScreen.ManageBlockList.route) }
                )
            }
            composable(MainScreen.ManageBlockList.route) {
                ManageBlockListScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
