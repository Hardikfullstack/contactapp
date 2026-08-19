package com.example.contactapp.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun isColorDark(argb: Int): Boolean = ColorUtils.calculateLuminance(argb) < 0.5

/**
 * Decodes a downsampled bitmap and averages its luminance. Must only be called at wallpaper
 * *selection* time (off the main thread) — never from the call-time hot path (InCallActivity /
 * FakeCallActivity), which only reads the precomputed [WallpaperSelection.isDark] flag.
 */
suspend fun computeIsDarkForImage(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 128 || bounds.outHeight / sampleSize > 128) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return@withContext true

        var total = 0.0
        var count = 0
        val stepX = maxOf(1, bitmap.width / 32)
        val stepY = maxOf(1, bitmap.height / 32)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                total += ColorUtils.calculateLuminance(bitmap.getPixel(x, y))
                count++
                x += stepX
            }
            y += stepY
        }
        bitmap.recycle()

        if (count == 0) true else (total / count) < 0.5
    } catch (e: Exception) {
        true
    }
}
