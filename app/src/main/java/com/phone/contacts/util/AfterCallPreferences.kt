package com.phone.contacts.util

import android.content.Context
import androidx.compose.runtime.mutableStateOf

private const val PREFS_NAME = "after_call_prefs"
private const val KEY_ENABLED = "enabled"

/** Whether the After Call screen opens when a call you answered ends - the Settings "Call back
 * screen" switch. On by default. */
object AfterCallPreferences {
    val enabled = mutableStateOf(true)

    fun initialize(context: Context) {
        enabled.value = isEnabled(context)
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, value).apply()
        enabled.value = value
    }
}
