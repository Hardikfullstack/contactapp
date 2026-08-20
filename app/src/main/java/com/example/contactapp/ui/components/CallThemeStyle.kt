package com.example.contactapp.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.example.contactapp.util.CallButtonShape
import kotlin.math.cos
import kotlin.math.sin

/**
 * A rounded flower/scallop shape — [lobes] rounded bumps alternating with notches whose depth
 * is set by [innerRatio]. A shallow, many-lobed version reads as a "cookie"; a deep, 4-lobed
 * version reads as a "clover" — the same family of expressive shapes Android's own dialer uses
 * for its call button.
 */
private fun scallopShape(lobes: Int, innerRatio: Float): Shape =
    GenericShape { size, _ ->
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerRadius = minOf(size.width, size.height) / 2f
        val innerRadius = outerRadius * innerRatio
        val totalPoints = lobes * 2
        val angleStep = 2 * Math.PI / totalPoints
        for (i in 0 until totalPoints) {
            val r = if (i % 2 == 0) outerRadius else innerRadius
            val angle = i * angleStep - Math.PI / 2
            val x = cx + r * cos(angle).toFloat()
            val y = cy + r * sin(angle).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

fun CallButtonShape.toComposeShape(): Shape = when (this) {
    CallButtonShape.CIRCLE -> CircleShape
    CallButtonShape.ROUNDED_SQUARE -> RoundedCornerShape(20.dp)
    CallButtonShape.SQUARE -> RoundedCornerShape(4.dp)
    CallButtonShape.LEAF -> RoundedCornerShape(topStartPercent = 0, topEndPercent = 50, bottomStartPercent = 50, bottomEndPercent = 50)
    CallButtonShape.CLOVER -> scallopShape(lobes = 4, innerRatio = 0.55f)
    CallButtonShape.COOKIE -> scallopShape(lobes = 12, innerRatio = 0.86f)
    CallButtonShape.FLOWER -> scallopShape(lobes = 6, innerRatio = 0.72f)
    CallButtonShape.BADGE -> scallopShape(lobes = 8, innerRatio = 0.78f)
    CallButtonShape.SUN -> scallopShape(lobes = 10, innerRatio = 0.85f)
    CallButtonShape.STAMP -> scallopShape(lobes = 16, innerRatio = 0.92f)
}

/** Blends toward white for a soft glossy highlight on filled call buttons. */
fun Color.lightened(amount: Float = 0.2f): Color = lerp(this, Color.White, amount)
