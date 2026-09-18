package com.phone.contact.call.dialer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.core.content.ContextCompat
import com.phone.contact.call.dialer.R

/**
 * RemoteViews (used for the custom call notification) needs an actual Bitmap for its ImageView —
 * unlike the rest of the app's avatars, which are just a Composable Box + Text, there's no live
 * layout tree here to draw into, so this renders the exact same look (contact photo if present,
 * name's first initial if a real contact matched, otherwise the same generic "unknown person" icon
 * used by the Recents list) onto a plain Bitmap instead.
 */
object NotificationAvatarUtils {

    fun createAvatarBitmap(
        context: Context,
        photoUri: String?,
        name: String,
        hasContactName: Boolean = true,
        sizeDp: Int = 48
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)

        val photoBitmap = photoUri?.let { loadContactPhoto(context, it, sizePx) }
        return when {
            photoBitmap != null -> circleCrop(photoBitmap, sizePx)
            hasContactName -> initialAvatar(name, sizePx)
            else -> unknownAvatar(context, sizePx)
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

    /** Conference calls have no single caller/photo to show — a group-of-people glyph instead,
     * matching the reference dialer's own conference notification, while still using our own
     * app's accent color (not copying the reference app's own icon/branding). */
    fun createConferenceAvatarBitmap(context: Context, sizeDp: Int = 48): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#4CAF50")
        }
        val radius = sizePx / 2f
        canvas.drawCircle(radius, radius, radius, circlePaint)

        val iconSize = (sizePx * 0.6f).toInt()
        val offset = (sizePx - iconSize) / 2
        ContextCompat.getDrawable(context, R.drawable.ic_group_conference)?.apply {
            setBounds(offset, offset, offset + iconSize, offset + iconSize)
            draw(canvas)
        }
        return output
    }

    /** Matches CallComponents.kt's CallItem fallback for a call with no resolved contact name:
     * a plain gray (#9E9E9E) circle with a generic person silhouette, instead of a misleading
     * initial taken from the raw phone number. */
    private fun unknownAvatar(context: Context, sizePx: Int): Bitmap {
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#9E9E9E")
        }
        val radius = sizePx / 2f
        canvas.drawCircle(radius, radius, radius, circlePaint)

        val iconSize = (sizePx * 0.6f).toInt()
        val offset = (sizePx - iconSize) / 2
        ContextCompat.getDrawable(context, R.drawable.ic_unknown_person)?.apply {
            setBounds(offset, offset, offset + iconSize, offset + iconSize)
            draw(canvas)
        }
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
