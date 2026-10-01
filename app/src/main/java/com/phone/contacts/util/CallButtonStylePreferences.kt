package com.phone.contacts.util

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.phone.contacts.ui.features.call.CallButtonStyle

private const val PREFS_NAME = "call_button_style_prefs"
private const val KEY_STYLE = "call_button_style"
private const val KEY_SWAP = "swap_call_buttons"

/** Persists the chosen incoming-call accept/decline style — same Light/Dark/System pattern as
 * [AppThemePreferences] — plus whether Accept/Decline's left-right order is swapped from the
 * default (Decline left, Accept right). Doesn't apply to [CallButtonStyle.SLIDER], which has no
 * separate left/right buttons to swap. */
object CallButtonStylePreferences {
    val style = mutableStateOf(CallButtonStyle.SLIDER)
    val swapButtons = mutableStateOf(false)

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_STYLE, CallButtonStyle.SLIDER.name)
        style.value = runCatching { CallButtonStyle.valueOf(raw ?: CallButtonStyle.SLIDER.name) }
            .getOrDefault(CallButtonStyle.SLIDER)
        swapButtons.value = prefs.getBoolean(KEY_SWAP, false)
    }

    fun setStyle(context: Context, newStyle: CallButtonStyle) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STYLE, newStyle.name)
            .apply()
        style.value = newStyle
    }

    fun setSwapButtons(context: Context, swap: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SWAP, swap)
            .apply()
        swapButtons.value = swap
    }
}
