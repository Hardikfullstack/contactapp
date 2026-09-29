package com.phone.contacts.util

import android.content.Context

private const val PREFS_NAME = "onboarding_prefs"
private const val KEY_LANGUAGE_SELECTED = "language_selected"

object OnboardingPreferences {
    fun isLanguageSelected(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LANGUAGE_SELECTED, false)

    fun setLanguageSelected(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LANGUAGE_SELECTED, true)
            .apply()
    }
}
