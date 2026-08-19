package com.example.contactapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * A ringing-call control (used for both Answer and Decline) that requires an upward drag past
 * [swipeDistance] to trigger — tapping alone does nothing. While idle it bobs upward a few dp
 * in a slow, continuous, subtle loop as a swipe hint (the loop pauses the instant a real drag
 * starts), matching the slide-to-answer/decline gesture stock dialers use instead of a plain
 * tap target. No caption is shown — the motion itself is the only cue.
 */
@Composable
fun SwipeUpCallButton(
    icon: ImageVector,
    color: Color,
    shape: Shape,
    contentDescription: String,
    onTriggered: () -> Unit,
    modifier: Modifier = Modifier,
    swipeDistance: Dp = 110.dp
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val swipeDistancePx = with(density) { swipeDistance.toPx() }
    val dragOffset = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var triggered by remember { mutableStateOf(false) }

    // Idle hint: a slow, subtle, continuous upward bob that fades out the instant a real drag starts.
    val infiniteTransition = rememberInfiniteTransition(label = "swipeHintTransition")
    val hintBob by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "swipeHintBob"
    )
    val hintAmplitude by animateFloatAsState(
        targetValue = if (isDragging || triggered) 0f else 1f,
        animationSpec = tween(200),
        label = "swipeHintAmplitude"
    )
    val hintOffsetPx = with(density) { (-14).dp.toPx() } * hintBob * hintAmplitude

    val totalOffsetPx = dragOffset.value + hintOffsetPx
    val progress = (-dragOffset.value / swipeDistancePx).coerceIn(0f, 1f)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Icon(
            imageVector = Icons.Default.KeyboardDoubleArrowUp,
            contentDescription = null,
            tint = Color.White.copy(alpha = (0.55f - progress * 0.55f).coerceIn(0f, 0.55f)),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .offset { IntOffset(0, totalOffsetPx.roundToInt()) }
                .shadow(elevation = 10.dp, shape = shape, ambientColor = color, spotColor = color)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(color.lightened(), color)))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            if (!triggered) {
                                scope.launch {
                                    dragOffset.animateTo(0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            if (!triggered) {
                                scope.launch { dragOffset.animateTo(0f) }
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (triggered) return@detectVerticalDragGestures
                            change.consume()
                            val next = (dragOffset.value + dragAmount).coerceIn(-swipeDistancePx * 1.2f, 0f)
                            scope.launch { dragOffset.snapTo(next) }
                            if (next <= -swipeDistancePx) {
                                triggered = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    dragOffset.animateTo(-swipeDistancePx * 1.3f, animationSpec = tween(160))
                                    onTriggered()
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}
