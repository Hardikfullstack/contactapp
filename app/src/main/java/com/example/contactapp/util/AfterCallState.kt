package com.example.contactapp.util

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/** On/off toggle for the After Call screen (Settings), backed by SharedPreferences so
 * [AfterCallReceiver][com.example.contactapp.service.AfterCallReceiver] can read it without
 * needing composable state. */
object AfterCallState {
    private const val PREFS = "contact_app_prefs"
    private const val KEY_ENABLED = "after_call_enabled"

    var enabled = mutableStateOf(true)

    fun applyPersistedMode(context: Context) {
        enabled.value = readEnabled(context)
    }

    fun setEnabled(context: Context, value: Boolean) {
        enabled.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, value)
            .apply()
    }

    fun readEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    }
}
