package com.phone.contacts.ui.features.call

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A double up-chevron hint shown above an accept/decline button, colored to match it: the
 * chevron closer to the button moves up and fades out first, then — after a short delay, not at
 * the same time — the one further above it follows the same path, and the cycle repeats (the
 * motion "flows" up and away from the button, not the other way around). Purely decorative (no
 * swipe gesture behind it, only the existing tap target). [iconSize] scales both the icons and
 * how far they travel, so the same animation reads correctly at the real call screen's size and
 * at the tiny grid-preview size. */
@Composable
fun SwipeUpChevrons(color: Color, iconSize: Dp = 16.dp, modifier: Modifier = Modifier) {
    val duration = 900
    val stagger = 250
    val transition = rememberInfiniteTransition(label = "swipe_up_chevrons")
    // Leads — this is the icon closer to the button (listed second, further down in the Column).
    val leadProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(duration, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "chevron_lead"
    )
    // Follows, delayed — this is the icon further from the button (listed first, higher up).
    val followProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(stagger, StartOffsetType.Delay)
        ),
        label = "chevron_follow"
    )
    val travel = iconSize / 2
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = color.copy(alpha = (1f - followProgress)),
            modifier = Modifier.size(iconSize).offset(y = -travel * followProgress)
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = color.copy(alpha = (1f - leadProgress)),
            modifier = Modifier.size(iconSize).offset(y = -travel * leadProgress)
        )
    }
}
