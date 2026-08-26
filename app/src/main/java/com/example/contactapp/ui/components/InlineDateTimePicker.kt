package com.example.contactapp.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * A scrolling wheel Day/Hour/Minute picker — plain LazyColumn + snap-fling physics, not a
 * Dialog/DatePickerDialog/TimePickerDialog. Hour/Minute loop endlessly (scrolling past 23/59
 * wraps around, like a real spinner); Day doesn't loop (bounded range) — padded with blank
 * sentinel rows instead of contentPadding so the first/last real day can still reach the visual
 * center using plain index math (contentPadding's pixel math is easy to get subtly wrong; blank
 * rows aren't).
 */
@Composable
fun InlineDateTimePicker(
    dayLabels: List<String>,
    selectedDayIndex: Int,
    onDaySelected: (Int) -> Unit,
    selectedHour: Int,
    onHourSelected: (Int) -> Unit,
    selectedMinute: Int,
    onMinuteSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hourLabels = remember { (0..23).map { String.format("%02d", it) } }
    val minuteLabels = remember { (0..59).map { String.format("%02d", it) } }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            WheelColumn(
                items = dayLabels,
                selectedIndex = selectedDayIndex,
                onSelectedChange = onDaySelected,
                loop = false,
                modifier = Modifier.weight(1.4f)
            )
            WheelColumn(
                items = hourLabels,
                selectedIndex = selectedHour,
                onSelectedChange = onHourSelected,
                loop = true,
                modifier = Modifier.weight(1f)
            )
            WheelColumn(
                items = minuteLabels,
                selectedIndex = selectedMinute,
                onSelectedChange = onMinuteSelected,
                loop = true,
                modifier = Modifier.weight(1f)
            )
        }

        // Selection-window indicator — two horizontal lines bounding the centered/selected row,
        // spanning all three columns (Day/Hour/Minute), matching a classic wheel-picker look.
        val dividerColor = MaterialTheme.colorScheme.outlineVariant
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.TopCenter)
                .offset(y = WheelItemHeight * WheelHalfRows)
                .background(dividerColor)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.TopCenter)
                .offset(y = WheelItemHeight * (WheelHalfRows + 1))
                .background(dividerColor)
        )
    }
}

private const val LOOP_MULTIPLIER = 400
private val WheelItemHeight = 34.dp
private const val WheelVisibleRows = 3
private const val WheelHalfRows = WheelVisibleRows / 2

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    loop: Boolean,
    modifier: Modifier = Modifier
) {
    val itemHeight = WheelItemHeight
    val visibleRows = WheelVisibleRows
    val halfRows = WheelHalfRows
    val coroutineScope = rememberCoroutineScope()

    // Non-looping columns get blank rows padded on both ends so the first/last real item can
    // still scroll all the way to the center row, same purpose contentPadding would serve but
    // without its harder-to-reason-about pixel offset math.
    val virtualCount = if (loop) items.size * LOOP_MULTIPLIER else items.size + halfRows * 2

    fun realIndexFor(virtualIndex: Int): Int {
        return if (loop) {
            ((virtualIndex % items.size) + items.size) % items.size
        } else {
            virtualIndex - halfRows
        }
    }

    val startVirtualIndex = remember(items) {
        if (loop) {
            val mid = (items.size * LOOP_MULTIPLIER) / 2
            mid - (mid % items.size) + selectedIndex
        } else {
            selectedIndex + halfRows
        }
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (startVirtualIndex - halfRows).coerceAtLeast(0)
    )
    val flingBehavior = rememberSnapFlingBehavior(listState)

    val centeredVirtualIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) {
                startVirtualIndex
            } else {
                val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
                info.visibleItemsInfo.minByOrNull { item ->
                    kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
                }?.index ?: startVirtualIndex
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val real = realIndexFor(centeredVirtualIndex)
                if (real in items.indices && real != selectedIndex) onSelectedChange(real)
            }
        }
    }

    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier.height(itemHeight * visibleRows),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxSize()
        ) {
            items(virtualCount) { vIndex ->
                val real = realIndexFor(vIndex)
                val label = if (real in items.indices) items[real] else ""
                val isSelected = vIndex == centeredVirtualIndex
                Text(
                    text = label,
                    fontSize = if (isSelected) 17.sp else 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) selectedColor else unselectedColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .then(
                            if (label.isNotEmpty()) {
                                Modifier.clickable {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem((vIndex - halfRows).coerceAtLeast(0))
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                )
            }
        }
    }
}
