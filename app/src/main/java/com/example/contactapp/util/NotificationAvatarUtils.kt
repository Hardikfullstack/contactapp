package com.example.contactapp.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri

/**
 * RemoteViews (used for the custom call notification) needs an actual Bitmap for its ImageView —
 * unlike the rest of the app's avatars, which are just a Composable Box + Text, there's no live
 * layout tree here to draw into, so this renders the exact same look (contact photo if present,
 * otherwise getAvatarColor()'s color with the name's first initial) onto a plain Bitmap instead.
 */
object NotificationAvatarUtils {

    fun createAvatarBitmap(context: Context, photoUri: String?, name: String, sizeDp: Int = 48): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)

        val photoBitmap = photoUri?.let { loadContactPhoto(context, it, sizePx) }
        return if (photoBitmap != null) {
            circleCrop(photoBitmap, sizePx)
        } else {
            initialAvatar(name, sizePx)
        }
    }

    private fun loadContactPhoto(context: Context, photoUri: String, sizePx: Int): Bitmap? {
        return try {
            context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun circleCrop(source: Bitmap, sizePx: Int): Bitmap {
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val scale = sizePx.toFloat() / minOf(source.width, source.height)
        val scaledWidth = source.width * scale
        val scaledHeight = source.height * scale
        val dx = (sizePx - scaledWidth) / 2f
        val dy = (sizePx - scaledHeight) / 2f
        val matrix = android.graphics.Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(matrix)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        val radius = sizePx / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        return output
    }

    private fun initialAvatar(name: String, sizePx: Int): Bitmap {
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val backgroundColor = getAvatarColor(name).toArgbInt()

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor }
        val radius = sizePx / 2f
        canvas.drawCircle(radius, radius, radius, circlePaint)

        val initial = name.trim().take(1).uppercase().ifBlank { "?" }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = sizePx * 0.45f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val textY = radius - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(initial, radius, textY, textPaint)
        return output
    }

    private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int {
        return android.graphics.Color.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt()
        )
    }
}
