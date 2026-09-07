package com.example.contactapp.ui.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** An expanding, fading pill-shaped glow drawn behind a pill/rounded button — the classic
 * "radar ping" CTA-attention effect, matching the Messages app's animatedPulse. */
fun Modifier.animatedPulse(
    pulseColor: Color,
    targetExpansionDp: Float = 12f,
    animationDuration: Int = 1500,
    maxAlpha: Float = 0.5f
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")

    val expansion by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = targetExpansionDp,
        animationSpec = infiniteRepeatable(
            animation = tween(animationDuration, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_expansion"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = maxAlpha,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(animationDuration, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    this.drawBehind {
        val expansionPx = expansion.dp.toPx()
        drawRoundRect(
            color = pulseColor,
            alpha = alpha,
            topLeft = Offset(-expansionPx, -expansionPx),
            size = Size(size.width + 2 * expansionPx, size.height + 2 * expansionPx),
            cornerRadius = CornerRadius(size.height / 2 + expansionPx, size.height / 2 + expansionPx)
        )
    }
}
