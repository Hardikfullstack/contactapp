package com.phone.contacts.util

import android.content.Context
import androidx.compose.runtime.mutableStateOf

private const val PREFS_NAME = "app_theme_prefs"
private const val KEY_THEME_MODE = "theme_mode"

enum class ThemeMode(val label: String) {
    LIGHT("Light theme"),
    DARK("Dark theme"),
    SYSTEM_DEFAULT("System default")
}

/** Matches the reference app's "Application theme" setting — a Light/Dark/System default
 * chooser, not just an on/off switch. */
object AppThemePreferences {
    val themeMode = mutableStateOf(ThemeMode.SYSTEM_DEFAULT)

    fun initialize(context: Context) {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, ThemeMode.SYSTEM_DEFAULT.name)
        themeMode.value = runCatching { ThemeMode.valueOf(raw ?: ThemeMode.SYSTEM_DEFAULT.name) }
            .getOrDefault(ThemeMode.SYSTEM_DEFAULT)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.name)
            .apply()
        themeMode.value = mode
    }
}
