package com.example.contactapp.ads

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import com.example.contactapp.R

/**
 * Light shimmer for ad placeholders — subtler than a full list-skeleton shimmer since these sit
 * inside content the user is actively scrolling past.
 */
@Composable
fun Modifier.adShimmerEffect(): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "ad_shimmer_transition")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ad_shimmer_alpha"
    )
    return this.background(colorResource(R.color.light_color_gray).copy(alpha = alpha))
}
