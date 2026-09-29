package com.phone.contacts.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.phone.contacts.data.CallLogItem
import com.phone.contacts.data.CallLogRepository
import com.phone.contacts.data.CallType
import com.phone.contacts.ui.features.onboarding.SetDefaultScreen
import com.phone.contacts.util.CallUtils
import com.phone.contacts.util.DefaultDialerState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun RecentsScreen(onContactClick: (name: String?, number: String) -> Unit) {
    val context = LocalContext.current
    // Runs synchronously during composition (RoleManager.isRoleHeld is a fast local binder call,
    // not async) so isDefaultDialer below reads the real value on the very first frame — deferring
    // this to a LaunchedEffect instead left a one-frame gap where isDefault's stale initial value
    // (false) made SetDefaultScreen flash even when the app was already the default dialer.
    remember { DefaultDialerState.refresh(context) }
    val isDefaultDialer by DefaultDialerState.isDefault

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) DefaultDialerState.refresh(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!isDefaultDialer) {
        SetDefaultScreen(onSetAsDefault = { DefaultDialerState.refresh(context) })
        return
    }

    var hasCallLogPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCallLogPermission = results[Manifest.permission.READ_CALL_LOG] == true
    }
    LaunchedEffect(Unit) {
        if (!hasCallLogPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS))
        }
    }

    if (!hasCallLogPermission) {
        PlaceholderScreen(title = "Recents")
        return
    }

    var allCalls by remember { mutableStateOf<List<CallLogItem>>(emptyList()) }
    var isLoadingCalls by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(hasCallLogPermission, refreshTrigger) {
        allCalls = CallLogRepository.fetchCallLogs(context)
        isLoadingCalls = false
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTrigger++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var query by remember { mutableStateOf("") }
    var showMissedOnly by remember { mutableStateOf(false) }

    val filtered = remember(allCalls, query, showMissedOnly) {
        allCalls
            .filter { !showMissedOnly || it.type == CallType.MISSED }
            .filter {
                query.isBlank() ||
                    (it.name?.contains(query, ignoreCase = true) == true) ||
                    it.number.contains(query)
            }
    }
    val grouped = remember(filtered) { groupByDate(filtered) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Text(
            text = "Recents",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        if (isLoadingCalls) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(label = "All", selected = !showMissedOnly, onClick = { showMissedOnly = false })
                FilterChip(label = "Missed", selected = showMissedOnly, onClick = { showMissedOnly = true })
            }
            CallLogSkeleton()
        } else if (grouped.isEmpty()) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(label = "All", selected = !showMissedOnly, onClick = { showMissedOnly = false })
                FilterChip(label = "Missed", selected = showMissedOnly, onClick = { showMissedOnly = true })
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No recent calls",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            val view = LocalView.current

            // Cumulative item-index each date group's header sits at, so the current group can be
            // looked up from just the first visible item index (no per-frame scan of the list).
            // Starts at 2 — the search field and filter row are now the first two LazyColumn
            // items, ahead of the date groups, so they scroll away with the rest of the content.
            val sectionStarts = remember(grouped) {
                val starts = mutableListOf<Int>()
                var index = 2
                grouped.forEach { (_, calls) ->
                    starts.add(index)
                    index += 1 + calls.size
                }
                starts
            }
            val currentSectionLabel by remember(grouped, sectionStarts) {
                derivedStateOf {
                    val firstVisible = listState.firstVisibleItemIndex
                    val sectionIndex = sectionStarts.indexOfLast { it <= firstVisible }.coerceAtLeast(0)
                    grouped.getOrNull(sectionIndex)?.first.orEmpty()
                }
            }
            val canScrollUp by remember {
                derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
            }
            // Measured height of the search field + filter row so the scroll thumb's track
            // starts right where the actual call list begins, not at the very top of the screen
            // (which sits behind that header before you've scrolled at all).
            var searchFieldHeightPx by remember { mutableStateOf(0) }
            var filterRowHeightPx by remember { mutableStateOf(0) }
            val density = LocalDensity.current
            val headerHeight = with(density) { (searchFieldHeightPx + filterRowHeightPx).toDp() }
            // How far down the list is scrolled, as a 0..1 fraction — drives the date thumb's
            // vertical position on the right edge when the user isn't dragging it.
            val scrollFraction by remember {
                derivedStateOf {
                    val layoutInfo = listState.layoutInfo
                    val visibleItems = layoutInfo.visibleItemsInfo
                    val totalItems = layoutInfo.totalItemsCount
                    if (totalItems == 0 || visibleItems.isEmpty()) return@derivedStateOf 0f
                    val avgItemHeight = visibleItems.sumOf { it.size } / visibleItems.size.toFloat()
                    val totalContentHeight = avgItemHeight * totalItems
                    val viewportHeight = layoutInfo.viewportSize.height.toFloat()
                    if (totalContentHeight <= viewportHeight) return@derivedStateOf 0f
                    val scrollOffset = listState.firstVisibleItemIndex * avgItemHeight - visibleItems.first().offset
                    (scrollOffset / (totalContentHeight - viewportHeight)).coerceIn(0f, 1f)
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        SearchField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .onGloballyPositioned { searchFieldHeightPx = it.size.height }
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                                .onGloballyPositioned { filterRowHeightPx = it.size.height },
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(label = "All", selected = !showMissedOnly, onClick = { showMissedOnly = false })
                            FilterChip(label = "Missed", selected = showMissedOnly, onClick = { showMissedOnly = true })
                        }
                    }
                    grouped.forEach { (dateLabel, calls) ->
                        item {
                            Text(
                                text = dateLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                        items(calls, key = { it.id }) { call ->
                            Column {
                                // Swipe-to-call/message stays disabled — that's what caused the
                                // accidental-call bug (a stray swipe mid-scroll placed real
                                // calls). A tap is a deliberate, discrete gesture and doesn't have
                                // that failure mode, so it's safe to re-enable on its own.
                                // SwipeableCallLogRow(
                                //     call = call,
                                //     onCall = { CallUtils.placeCall(context, call.number) },
                                //     onInfoClick = { onContactClick(call.name, call.number) },
                                //     onMessage = { MessageUtils.sendMessage(context, call.number) }
                                // )
                                CallLogRow(
                                    call = call,
                                    onClick = { CallUtils.placeCall(context, call.number) },
                                    onInfoClick = { onContactClick(call.name, call.number) }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 74.dp),
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
                            }
                        }
                    }
                }

                // Fast-scroll thumb — same as ContactsScreen's, but jumps between date groups
                // instead of letters, and has no popup bubble (dates aren't a single character).
                var isDragging by remember { mutableStateOf(false) }
                var dragFraction by remember { mutableStateOf(0f) }
                // The reference app's fast-scroller (a RecyclerViewFastScroller/BubbleTextGetter
                // widget, confirmed used on its call-log screen too) maps drag position to the
                // underlying adapter's item index directly — not to date-group boundaries, which
                // is why jumping only between the handful of date-group starts felt broken (most
                // of the drag track was dead space). Resolve per-call instead, same as Contacts.
                val totalCalls = remember(grouped) { grouped.sumOf { it.second.size } }

                fun jumpTo(fraction: Float) {
                    dragFraction = fraction
                    if (totalCalls == 0) return
                    val targetCallIndex = (fraction * (totalCalls - 1)).toInt().coerceIn(0, totalCalls - 1)
                    var remaining = targetCallIndex
                    var groupIndex = 0
                    while (groupIndex < grouped.size - 1 && remaining >= grouped[groupIndex].second.size) {
                        remaining -= grouped[groupIndex].second.size
                        groupIndex++
                    }
                    val targetIndex = sectionStarts.getOrElse(groupIndex) { 0 } + 1 + remaining
                    coroutineScope.launch { listState.scrollToItem(targetIndex) }
                }

                val thumbHeight = 72.dp
                val displayFraction = if (isDragging) dragFraction else scrollFraction
                val thumbOffsetY = headerHeight +
                    ((maxHeight - headerHeight - thumbHeight) * displayFraction).coerceAtLeast(0.dp)
                val thumbAlpha by animateFloatAsState(
                    targetValue = if (listState.isScrollInProgress || isDragging) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = if (listState.isScrollInProgress || isDragging) 150 else 400,
                        delayMillis = if (listState.isScrollInProgress || isDragging) 0 else 2000
                    ),
                    label = "thumb_alpha"
                )

                // Invisible drag strip, hugging the very edge (CallLogRow's own "i" button sits
                // 20dp+ in from the edge, so this needs to stay clear of that — it can't be made
                // wider without covering it, the way ContactsScreen's can). Being this close to
                // the edge would normally lose the touch to the OS's edge-swipe back gesture, so
                // this rect is registered as a system-gesture exclusion zone below instead of
                // relying on inset alone — the reference app's own fast-scroller (which sits in
                // this same strip, per its layout resources) has the identical edge-adjacency
                // problem to solve, and exclusion rects are the documented Android fix for it.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(top = headerHeight, end = 2.dp)
                        .width(16.dp)
                        .onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInRoot()
                            ViewCompat.setSystemGestureExclusionRects(
                                view,
                                listOf(
                                    android.graphics.Rect(
                                        bounds.left.toInt(),
                                        bounds.top.toInt(),
                                        bounds.right.toInt(),
                                        bounds.bottom.toInt()
                                    )
                                )
                            )
                        }
                        .pointerInput(sectionStarts, grouped) {
                            // size.height is read fresh on every event rather than cached once —
                            // this Box's real height depends on headerHeight, which only settles
                            // a frame or two after the search field/filter row report their
                            // measured size, and a stale cached height made drags near the top of
                            // the track land on the wrong fraction.
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    jumpTo((offset.y / size.height.toFloat()).coerceIn(0f, 1f))
                                },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false }
                            ) { change, _ ->
                                change.consume()
                                jumpTo((change.position.y / size.height.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                )

                if (thumbAlpha > 0f) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 6.dp,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(y = thumbOffsetY)
                            .padding(end = 4.dp)
                            .size(width = 26.dp, height = thumbHeight)
                            .alpha(thumbAlpha)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Floating date pill — driven by the exact same raw condition/timing as
                // thumbAlpha (not by "thumbAlpha > 0f", which is itself mid-fade and would make
                // the pill start its own fade-out late, stacking an extra ~2.4s on top before it
                // actually disappeared) so the two visually hide together.
                val pillAlpha by animateFloatAsState(
                    targetValue = if ((listState.isScrollInProgress || isDragging) && currentSectionLabel.isNotEmpty()) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = if (listState.isScrollInProgress || isDragging) 150 else 400,
                        delayMillis = if (listState.isScrollInProgress || isDragging) 0 else 2000
                    ),
                    label = "date_pill_alpha"
                )
                if (pillAlpha > 0f) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = headerHeight + 8.dp)
                            .alpha(pillAlpha)
                    ) {
                        Text(
                            text = currentSectionLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }

                // Jump-to-top button — stays visible the whole time the list is scrolled away
                // from the top (not just during active scrolling), positioned just above where
                // the bottom navigation bar sits.
                val topButtonAlpha by animateFloatAsState(
                    targetValue = if (canScrollUp) 1f else 0f,
                    animationSpec = tween(durationMillis = 200),
                    label = "top_button_alpha"
                )
                if (topButtonAlpha > 0f) {
                    Surface(
                        onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp)
                            .size(48.dp)
                            .alpha(topButtonAlpha)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardDoubleArrowUp,
                                contentDescription = "Scroll to top",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = "Search calls",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable { onValueChange("") }
                )
            }
        }
    }
}

@Composable
private fun CallLogSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    val shimmerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)

    Column(modifier = Modifier.fillMaxSize()) {
        repeat(8) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(shimmerColor)
                )
                Spacer(modifier = Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerColor)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableCallLogRow(
    call: CallLogItem,
    onCall: () -> Unit,
    onInfoClick: () -> Unit,
    onMessage: () -> Unit
) {
    // Matches the reference app's own Recents swipe: swipe right to call, swipe left to
    // message — neither direction removes the row, both just snap back after firing the action.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onCall()
                SwipeToDismissBoxValue.EndToStart -> onMessage()
                SwipeToDismissBoxValue.Settled -> {}
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val (bg, icon, alignment) = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(Color(0xFF1DA463), Icons.Filled.Call, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> Triple(Color(0xFF1E88E5), Icons.AutoMirrored.Filled.Message, Alignment.CenterEnd)
                SwipeToDismissBoxValue.Settled -> Triple(Color.Transparent, null, Alignment.Center)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                icon?.let { Icon(it, contentDescription = null, tint = Color.White) }
            }
        }
    ) {
        CallLogRow(call = call, onClick = onCall, onInfoClick = onInfoClick)
    }
}

@Composable
private fun CallLogRow(
    call: CallLogItem,
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val badgeColor = when (call.type) {
        CallType.INCOMING -> MaterialTheme.colorScheme.primary
        CallType.OUTGOING -> Color(0xFF1DA463)
        CallType.MISSED, CallType.REJECTED, CallType.BLOCKED -> Color(0xFFE0413B)
        CallType.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val directionIcon = when (call.type) {
        CallType.INCOMING -> Icons.Filled.CallReceived
        CallType.OUTGOING -> Icons.Filled.CallMade
        CallType.MISSED, CallType.REJECTED, CallType.BLOCKED -> Icons.Filled.CallMissed
        CallType.OTHER -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (call.type == CallType.OTHER) {
                Icon(imageVector = Icons.Filled.Person, contentDescription = null, tint = badgeColor, modifier = Modifier.size(22.dp))
            } else {
                // Phone icon as the base — neutral color, like the reference app — with the
                // direction arrow layered directly on top of it, centered, colored by call type.
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                if (directionIcon != null) {
                    Icon(
                        imageVector = directionIcon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier
                            .offset(y = (-7.5).dp, x = 7.dp)
                            .size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append(call.name ?: call.number)
                    if (call.callCount > 1) append(" (${call.callCount})")
                },
                color = if (call.type == CallType.MISSED) badgeColor else MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatTime(call.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable(onClick = onInfoClick),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "i", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(timestamp)

private fun groupByDate(calls: List<CallLogItem>): List<Pair<String, List<CallLogItem>>> {
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val cal = Calendar.getInstance()

    fun labelFor(timestamp: Long): String {
        cal.timeInMillis = timestamp
        return when {
            isSameDay(cal, today) -> "Today"
            isSameDay(cal, yesterday) -> "Yesterday"
            else -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(timestamp)
        }
    }

    val result = LinkedHashMap<String, MutableList<CallLogItem>>()
    calls.forEach { call ->
        result.getOrPut(labelFor(call.timestamp)) { mutableListOf() }.add(call)
    }
    return result.map { it.key to it.value }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

