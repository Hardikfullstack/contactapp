package com.example.contactapp.util

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-contact "calling card" background — a custom call-screen wallpaper for one specific
 * phone number, distinct from (and taking priority over) the single global wallpaper chosen
 * in Tools > Call Wallpaper. Reuses [WallpaperSelection]/[WallpaperCodec] so it plugs
 * straight into the same rendering (CallWallpaperBackground) and status-bar contrast
 * (isDarkOnCallScreen) code the global wallpaper already uses.
 */
@Singleton
class ContactCallBackgroundManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("contact_call_backgrounds", Context.MODE_PRIVATE)

    private val changeSignal = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private fun keyFor(number: String): String = number.replace(Regex("[^0-9]"), "").takeLast(10)

    fun getBackground(number: String): WallpaperSelection {
        val key = keyFor(number)
        if (key.isEmpty()) return WallpaperSelection.None
        return WallpaperCodec.decode(prefs.getString(key, null)).first
    }

    fun getBackgroundFlow(number: String): Flow<WallpaperSelection> {
        return changeSignal.onStart { emit(Unit) }.map { getBackground(number) }
    }

    fun setBackground(number: String, selection: WallpaperSelection) {
        val key = keyFor(number)
        if (key.isEmpty()) return
        val encoded = WallpaperCodec.encode(selection)
        if (encoded == null) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, encoded).apply()
        }
        changeSignal.tryEmit(Unit)
    }

    fun clearBackground(number: String) = setBackground(number, WallpaperSelection.None)
}
