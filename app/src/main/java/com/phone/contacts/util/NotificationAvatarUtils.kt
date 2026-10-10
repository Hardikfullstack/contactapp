package com.phone.contacts.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.core.content.ContextCompat
import com.phone.contacts.R
import kotlin.math.abs

/** RemoteViews (used for the custom call notification) needs an actual Bitmap for its ImageView -
 * unlike the rest of the app's avatars, which are just a Composable Box + Text, there's no live
 * layout tree here to draw into, so this renders the same look (contact photo if present, the
 * same colored-initial circle `ContactAvatar`/`avatarColorFor` use elsewhere, otherwise a plain
 * gray circle with a generic person silhouette for an unresolved number - matching contactapp's
 * own unknown-caller notification avatar) onto a plain Bitmap instead. */
object NotificationAvatarUtils {

    // Same palette as ContactsScreen's own avatarColorFor - kept in sync manually since that one
    // is private to its file and this is the only other place needing the identical look.
    private val avatarPalette = listOf(
        Color.parseColor("#E85C88"),
        Color.parseColor("#1DA463"),
        Color.parseColor("#E0413B"),
        Color.parseColor("#9C27B0"),
        Color.parseColor("#FF9800"),
        Color.parseColor("#1CBFD5"),
        Color.parseColor("#5BD902"),
        Color.parseColor("#3F51B5"),
        Color.parseColor("#FFC107"),
        Color.parseColor("#2196F3")
    )

    private fun avatarColorFor(name: String): Int = avatarPalette[abs(name.hashCode()) % avatarPalette.size]

    fun createAvatarBitmap(
        context: Context,
        photoUri: String?,
        name: String,
        hasContactName: Boolean = true,
        sizeDp: Int = 48
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)

        val photoBitmap = photoUri?.let { loadContactPhoto(context, it) }
        return when {
            photoBitmap != null -> circleCrop(photoBitmap, sizePx)
            hasContactName -> initialAvatar(name, avatarColorFor(name), sizePx)
            else -> unknownAvatar(context, sizePx)
        }
    }

    /** Conference calls have no single caller/photo to show - a plain "..." on the brand color,
     * matching how CallScreen's own conference display has no individual avatar either. */
    fun createConferenceAvatarBitmap(context: Context, sizeDp: Int = 48): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        return initialAvatar("...", Color.parseColor("#0E51E3"), sizePx)
    }

    private fun loadContactPhoto(context: Context, photoUri: String): Bitmap? {
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
        val matrix = Matrix().apply {
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

    private fun initialAvatar(text: String, backgroundColor: Int, sizePx: Int): Bitmap {
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val radius = sizePx / 2f

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor }
        canvas.drawCircle(radius, radius, radius, circlePaint)

        val label = if (text == "...") text else text.trim().take(1).uppercase().ifBlank { "?" }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = sizePx * (if (label == "...") 0.3f else 0.45f)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val textY = radius - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, radius, textY, textPaint)
        return output
    }

    /** Matches contactapp's own unknown-caller notification avatar: a plain gray (#9E9E9E) circle
     * with a generic person silhouette, instead of a "#"/initial taken from an unverified number
     * or a CNAP-provided name that isn't actually a saved contact. */
    private fun unknownAvatar(context: Context, sizePx: Int): Bitmap {
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9E9E9E") }
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
}
