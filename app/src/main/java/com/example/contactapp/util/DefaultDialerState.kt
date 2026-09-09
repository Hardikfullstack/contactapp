package com.example.contactapp.util

import android.app.role.RoleManager
import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Whether this app currently holds the default-dialer role. Shared (not screen-local) because
 * both [com.example.contactapp.ui.features.recents.RecentsScreen] (which owns the prompt/
 * permission-cascade flow) and the bottom navigation bar in
 * [com.example.contactapp.ui.navigation.MainNavigation] need to react to it — the whole main app
 * (list content, FAB, and bottom nav) locks down to just the "Set Default" action until this is
 * true, matching the reference app's own behavior of disabling its Home screen the same way.
 */
object DefaultDialerState {
    val isDefault = mutableStateOf(true)

    fun refresh(context: Context) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        isDefault.value = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    }
}
