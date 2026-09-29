package com.phone.contacts.util

import android.app.role.RoleManager
import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Whether this app currently holds the default-dialer role. Shared (not screen-local) so any
 * screen that needs to react to it — Recents, Settings, etc. — reads the same value instead of
 * each re-querying RoleManager independently.
 */
object DefaultDialerState {
    val isDefault = mutableStateOf(false)

    fun refresh(context: Context) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        isDefault.value = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    }
}
