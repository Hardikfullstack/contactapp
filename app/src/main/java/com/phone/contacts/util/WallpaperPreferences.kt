package com.phone.contacts.util

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf

private const val PREFS_NAME = "call_wallpaper_prefs"
private const val KEY_SELECTION = "call_wallpaper_selection"
private const val KEY_BLUR = "call_wallpaper_blur"

sealed class WallpaperSelection {
    object None : WallpaperSelection()
    data class SolidColor(val colorArgb: Int, val isDark: Boolean) : WallpaperSelection()
    data class BuiltIn(val id: String, val isDark: Boolean) : WallpaperSelection()
    data class Device(val uri: String, val isDark: Boolean) : WallpaperSelection()
}

/** The real call screen falls back to a hardcoded dark background when no wallpaper is selected,
 * so status bar icons should stay light in that case. */
fun WallpaperSelection.isDarkOnCallScreen(): Boolean = when (this) {
    is WallpaperSelection.None -> true
    is WallpaperSelection.SolidColor -> isDark
    is WallpaperSelection.BuiltIn -> isDark
    is WallpaperSelection.Device -> isDark
}

/** Encodes as "type:isDark:payload" — payload is last and grabbed with a 3-way split limit so a
 * device content:// URI (the only variable-content field) can itself contain ':' safely. */
private fun encode(selection: WallpaperSelection): String? = when (selection) {
    is WallpaperSelection.None -> null
    is WallpaperSelection.SolidColor -> "color:${selection.isDark}:${selection.colorArgb}"
    is WallpaperSelection.BuiltIn -> "builtin:${selection.isDark}:${selection.id}"
    is WallpaperSelection.Device -> "device:${selection.isDark}:${selection.uri}"
}

private fun decode(raw: String?): WallpaperSelection {
    if (raw.isNullOrBlank()) return WallpaperSelection.None
    val parts = raw.split(":", limit = 3)
    if (parts.size != 3) return WallpaperSelection.None
    val (type, isDarkRaw, payload) = parts
    val isDark = isDarkRaw.toBooleanStrictOrNull() ?: true
    return when (type) {
        "color" -> payload.toIntOrNull()?.let { WallpaperSelection.SolidColor(it, isDark) } ?: WallpaperSelection.None
        "builtin" -> WallpaperSelection.BuiltIn(payload, isDark)
        "device" -> WallpaperSelection.Device(payload, isDark)
        else -> WallpaperSelection.None
    }
}

/** Persists the chosen call-screen wallpaper — same SharedPreferences-backed singleton pattern as
 * [CallButtonStylePreferences]. Matches the reference app's own Call Wallpaper setting, scoped to
 * a single app-wide choice (no per-contact override).
 *
 * Every selectable wallpaper here is only *committed* via [commit] — screens build a candidate
 * (with [colorCandidate]/[builtInCandidate]/[deviceCandidate]) and show it in a confirm-first
 * preview, matching the same tap-to-preview-then-"Set"-flow already used for Call button styles. */
object WallpaperPreferences {
    val selection = mutableStateOf<WallpaperSelection>(WallpaperSelection.None)
    val blurEnabled = mutableStateOf(false)

    val colorPresets: List<Long> = listOf(
        0xFF109D6F, 0xFF2196F3, 0xFF9C27B0, 0xFFFF9800,
        0xFFE91E63, 0xFF607D8B, 0xFF3F51B5, 0xFFC62828,
        0xFF009688, 0xFFFFC107, 0xFF4527A0, 0xFF212121
    )

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        selection.value = decode(prefs.getString(KEY_SELECTION, null))
        blurEnabled.value = prefs.getBoolean(KEY_BLUR, false)
    }

    fun colorCandidate(argb: Int): WallpaperSelection = WallpaperSelection.SolidColor(argb, isColorDark(argb))

    fun builtInCandidate(id: String): WallpaperSelection? {
        val wallpaper = BuiltInWallpapers.findById(id) ?: return null
        return WallpaperSelection.BuiltIn(id, wallpaper.isDark)
    }

    suspend fun deviceCandidate(context: Context, uri: Uri): WallpaperSelection =
        WallpaperSelection.Device(uri.toString(), computeIsDarkForImage(context, uri))

    /** Persists [newSelection] plus the current [blur] state together, matching how the preview
     * screen's "Set wallpaper" commits both at once. */
    fun commit(context: Context, newSelection: WallpaperSelection, blur: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTION, encode(newSelection))
            .putBoolean(KEY_BLUR, blur)
            .apply()
        selection.value = newSelection
        blurEnabled.value = blur
    }
}
