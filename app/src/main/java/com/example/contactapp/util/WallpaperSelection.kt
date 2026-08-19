package com.example.contactapp.util

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

sealed class WallpaperSelection {
    object None : WallpaperSelection()
    data class SolidColor(val colorArgb: Int, val isDark: Boolean) : WallpaperSelection()
    data class BuiltIn(val id: String, val isDark: Boolean) : WallpaperSelection()
    data class Device(val uri: String, val isDark: Boolean) : WallpaperSelection()
}

/**
 * Both call screens (InCallActivity, FakeCallActivity) fall back to a hardcoded dark
 * background when no wallpaper is selected, so status bar icons should stay light in that case.
 */
fun WallpaperSelection.isDarkOnCallScreen(): Boolean = when (this) {
    is WallpaperSelection.None -> true
    is WallpaperSelection.SolidColor -> isDark
    is WallpaperSelection.BuiltIn -> isDark
    is WallpaperSelection.Device -> isDark
}

private data class WallpaperDto(
    val type: String, // "color" | "builtin" | "device"
    val colorArgb: Int? = null,
    val builtInId: String? = null,
    val deviceUri: String? = null,
    val isDark: Boolean = true
)

object WallpaperCodec {
    private val gson = Gson()

    fun encode(selection: WallpaperSelection): String? = when (selection) {
        is WallpaperSelection.None -> null
        is WallpaperSelection.SolidColor ->
            gson.toJson(WallpaperDto(type = "color", colorArgb = selection.colorArgb, isDark = selection.isDark))
        is WallpaperSelection.BuiltIn ->
            gson.toJson(WallpaperDto(type = "builtin", builtInId = selection.id, isDark = selection.isDark))
        is WallpaperSelection.Device ->
            gson.toJson(WallpaperDto(type = "device", deviceUri = selection.uri, isDark = selection.isDark))
    }

    /**
     * Returns the decoded selection plus whether [raw] was in the legacy (pre-typed) format,
     * so the caller can rewrite storage to the new format exactly once.
     */
    fun decode(raw: String?): Pair<WallpaperSelection, Boolean> {
        if (raw.isNullOrBlank()) return WallpaperSelection.None to false

        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            return try {
                val dto = gson.fromJson(trimmed, WallpaperDto::class.java)
                val selection = when (dto.type) {
                    "color" -> WallpaperSelection.SolidColor(dto.colorArgb ?: 0, dto.isDark)
                    "builtin" -> WallpaperSelection.BuiltIn(dto.builtInId ?: "", dto.isDark)
                    "device" -> WallpaperSelection.Device(dto.deviceUri ?: "", dto.isDark)
                    else -> WallpaperSelection.None
                }
                selection to false
            } catch (e: JsonSyntaxException) {
                WallpaperSelection.None to false
            }
        }

        // Legacy format: either a color-int string, or a raw content:// URI string.
        val legacyColor = trimmed.toLongOrNull()
        return if (legacyColor != null) {
            val argb = legacyColor.toInt()
            WallpaperSelection.SolidColor(argb, isColorDark(argb)) to true
        } else {
            // Brightness unknown without a bitmap decode, which must not happen on this
            // path (it can run on the call-time hot path for pre-existing installs).
            WallpaperSelection.Device(trimmed, isDark = true) to true
        }
    }
}
