package com.phone.contacts.util

import android.content.Context
import androidx.compose.runtime.mutableStateOf

private const val PREFS_NAME = "keypad_tone_prefs"
private const val KEY_ENABLED = "enabled"

/** Whether the Keypad plays a DTMF tone on each digit press — a plain on/off toggle shown inline
 * on its own Settings row (no sub-screen), matching "Call back screen"'s own inline-switch style.
 * Scoped to the tone only; the Keypad's haptic vibration on press is independent of this. */
object KeypadTonePreferences {
    val enabled = mutableStateOf(true)

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        enabled.value = prefs.getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, value)
            .apply()
        enabled.value = value
    }
}
