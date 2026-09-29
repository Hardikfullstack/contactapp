package com.phone.contacts.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A thin, non-interactive vertical scrollbar drawn on the trailing edge of the list — a simple
 * position indicator (not a draggable thumb), visible while the list is actively scrolling and
 * fading out shortly after it settles, matching the reference app's plain scroll indicator. */
fun Modifier.verticalScrollIndicator(
    listState: LazyListState,
    color: Color,
    width: Dp = 4.dp
): Modifier = composed {
    val targetAlpha = if (listState.isScrollInProgress) 1f else 0f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(
            durationMillis = if (targetAlpha == 0f) 400 else 150,
            delayMillis = if (targetAlpha == 0f) 2000 else 0
        ),
        label = "scroll_indicator_alpha"
    )
    drawWithContent {
        drawContent()
        if (alpha <= 0f) return@drawWithContent

        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        val totalItems = layoutInfo.totalItemsCount
        if (totalItems == 0 || visibleItems.isEmpty()) return@drawWithContent

        val viewportHeight = layoutInfo.viewportSize.height.toFloat()
        if (viewportHeight <= 0f) return@drawWithContent

        val averageItemHeight = visibleItems.sumOf { it.size } / visibleItems.size.toFloat()
        val totalContentHeight = averageItemHeight * totalItems
        if (totalContentHeight <= viewportHeight) return@drawWithContent

        val scrollOffset = listState.firstVisibleItemIndex * averageItemHeight - visibleItems.first().offset
        val thumbHeight = (viewportHeight * viewportHeight / totalContentHeight)
            .coerceIn(32.dp.toPx(), viewportHeight)
        val maxThumbOffset = viewportHeight - thumbHeight
        val scrollableDistance = (totalContentHeight - viewportHeight).coerceAtLeast(1f)
        val thumbOffset = (scrollOffset / scrollableDistance * maxThumbOffset).coerceIn(0f, maxThumbOffset)

        val barWidthPx = width.toPx()
        drawRoundRect(
            color = color.copy(alpha = alpha * 0.85f),
            topLeft = Offset(size.width - barWidthPx - 3.dp.toPx(), thumbOffset),
            size = Size(barWidthPx, thumbHeight),
            cornerRadius = CornerRadius(barWidthPx / 2)
        )
    }
}
