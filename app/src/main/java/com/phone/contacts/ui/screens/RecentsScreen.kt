package com.phone.contacts.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.phone.contacts.R
import com.phone.contacts.data.BlockRepository
import com.phone.contacts.data.CallLogItem
import com.phone.contacts.data.CallLogRepository
import com.phone.contacts.data.CallType
import com.phone.contacts.data.ContactRepository
import com.phone.contacts.ads.AdPlacements
import com.phone.contacts.ads.AdType
import com.phone.contacts.ads.NativeAdView
import com.phone.contacts.ads.NativeAdTemplate
import com.phone.contacts.util.AppConfigStore
import androidx.compose.runtime.collectAsState
import com.phone.contacts.ui.components.highlightMatches
import com.phone.contacts.ui.features.onboarding.SetDefaultScreen
import com.phone.contacts.ui.theme.primaryAccentColor
import com.phone.contacts.util.CallUtils
import com.phone.contacts.util.DefaultDialerState
import com.phone.contacts.util.MessageUtils
import com.phone.contacts.util.WhatsAppUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun RecentsScreen(onContactClick: (name: String?, number: String) -> Unit, onAddToContact: (String) -> Unit) {
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
        PlaceholderScreen(title = stringResource(R.string.recents))
        return
    }

    var allCalls by remember { mutableStateOf<List<CallLogItem>>(emptyList()) }
    var blockedNumbers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoadingCalls by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }
    // Matches the reference app: a call from a number currently sitting in the Recycle Bin is
    // hidden from Recents (not deleted from the real call log) — restoring the contact brings it
    // straight back, since this is just a filter applied on every refresh, not a real delete.
    LaunchedEffect(hasCallLogPermission, refreshTrigger) {
        val binNumbers = ContactRepository.recycleBinFlow(context).first().map { it.number }.toSet()
        allCalls = CallLogRepository.fetchCallLogs(context).filterNot { it.number in binNumbers }
        isLoadingCalls = false
    }
    // Reactive (not tied to refreshTrigger/resume) — a number blocked or unblocked anywhere in the
    // app (e.g. Contact Detail's "Block" action) updates every matching row here immediately,
    // without needing to leave and come back to this screen. Matched by normalized digits, not
    // CallLog's own per-call BLOCKED_TYPE — many OEMs never set it, and a call logged before the
    // number was blocked never has it either.
    LaunchedEffect(Unit) {
        BlockRepository.blockedNumbersFlow(context).collect { entries ->
            blockedNumbers = entries.map { normalizeForBlockMatch(it.number) }.toSet()
        }
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
    val adConfig by AppConfigStore.config.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Text(
            text = stringResource(R.string.recents),
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
                FilterChip(label = stringResource(R.string.filter_all), selected = !showMissedOnly, onClick = { showMissedOnly = false })
                FilterChip(label = stringResource(R.string.filter_missed), selected = showMissedOnly, onClick = { showMissedOnly = true })
            }
            Box(modifier = Modifier.weight(1f)) {
                CallLogSkeleton()
            }
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
                FilterChip(label = stringResource(R.string.filter_all), selected = !showMissedOnly, onClick = { showMissedOnly = false })
                FilterChip(label = stringResource(R.string.filter_missed), selected = showMissedOnly, onClick = { showMissedOnly = true })
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_recent_calls),
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
            // How much of the search field + filter row has scrolled past, in px — matches the
            // reference app's collapsing-header behavior, where the current-date pill slides up
            // together with the header as it scrolls away, then sticks right below the fixed title
            // bar once fully scrolled past, instead of staying pinned at the header's unscrolled
            // height (which would float in empty space above the list once scrolled).
            val headerScrolledPx by remember {
                derivedStateOf {
                    val idx = listState.firstVisibleItemIndex
                    val offset = listState.firstVisibleItemScrollOffset
                    when {
                        idx <= 0 -> offset
                        idx == 1 -> searchFieldHeightPx + offset
                        else -> searchFieldHeightPx + filterRowHeightPx
                    }.coerceAtMost(searchFieldHeightPx + filterRowHeightPx)
                }
            }
            val pillTopPadding = with(density) {
                (searchFieldHeightPx + filterRowHeightPx - headerScrolledPx).coerceAtLeast(0).toDp()
            } + 8.dp
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

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                            FilterChip(label = stringResource(R.string.filter_all), selected = !showMissedOnly, onClick = { showMissedOnly = false })
                            FilterChip(label = stringResource(R.string.filter_missed), selected = showMissedOnly, onClick = { showMissedOnly = true })
                        }
                    }
                    grouped.forEachIndexed { groupIndex, (dateLabel, calls) ->
                        item {
                            // The very first header (always "Today") has no previous group above it
                            // to separate from, so it only needs the small resting gap - not the
                            // larger one every other header uses to break away from the group before it.
                            Text(
                                text = dateLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = if (groupIndex == 0) 3.dp else 14.dp,
                                    bottom = 0.dp
                                )
                            )
                        }
                        itemsIndexed(calls, key = { _, call -> call.id }) { index, call ->
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
                                    isBlocked = normalizeForBlockMatch(call.number) in blockedNumbers,
                                    onClick = { CallUtils.placeCall(context, call.number) },
                                    onInfoClick = { onContactClick(call.name, call.number) },
                                    onDeleted = { refreshTrigger++ },
                                    onAddToContact = { onAddToContact(call.number) },
                                    highlightQuery = query
                                )
                                // Skipped on each group's last row — that row's own bottom edge is
                                // already the boundary into the next date header's extra top
                                // spacing, so a divider there would just double up with it.
                                if (index != calls.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 74.dp),
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Fast-scroll thumb — same as ContactsScreen's, but jumps between date groups
                // instead of letters, and has no popup bubble (dates aren't a single character).
                var isDragging by remember { mutableStateOf(false) }
                var dragFraction by remember { mutableStateOf(0f) }
                var dragTargetIndex by remember { mutableStateOf<Int?>(null) }
                // The reference app's fast-scroller (a RecyclerViewFastScroller/BubbleTextGetter
                // widget, confirmed used on its call-log screen too) maps drag position to the
                // underlying adapter's item index directly — not to date-group boundaries, which
                // is why jumping only between the handful of date-group starts felt broken (most
                // of the drag track was dead space). Resolve per-call instead, same as Contacts.
                val totalCalls = remember(grouped) { grouped.sumOf { it.second.size } }

                fun indexForFraction(fraction: Float): Int? {
                    if (totalCalls == 0) return null
                    val targetCallIndex = (fraction * (totalCalls - 1)).toInt().coerceIn(0, totalCalls - 1)
                    var remaining = targetCallIndex
                    var groupIndex = 0
                    while (groupIndex < grouped.size - 1 && remaining >= grouped[groupIndex].second.size) {
                        remaining -= grouped[groupIndex].second.size
                        groupIndex++
                    }
                    return sectionStarts.getOrElse(groupIndex) { 0 } + 1 + remaining
                }

                // A single reactive effect instead of launching a new coroutine per drag event —
                // detectDragGestures' onDrag fires dozens of times a second, and each one racing
                // independently for the list's scroll mutex is what made this feel broken on a long
                // call history. LaunchedEffect cancels the in-flight scroll and starts the new one
                // through Compose's own mechanism instead of piling up competing launches.
                LaunchedEffect(dragTargetIndex) {
                    dragTargetIndex?.let { listState.scrollToItem(it) }
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
                // 20dp+ in from the edge — its Row has 20dp horizontal padding — so this uses that
                // whole safe margin, right up to the edge, without covering it). Being this close
                // to the edge would normally lose the touch to the OS's edge-swipe back gesture, so
                // this rect is registered as a system-gesture exclusion zone below instead of
                // relying on inset alone — the reference app's own fast-scroller (which sits in
                // this same strip, per its layout resources) has the identical edge-adjacency
                // problem to solve, and exclusion rects are the documented Android fix for it.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(top = headerHeight)
                        .width(20.dp)
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
                                    val fraction = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                    dragFraction = fraction
                                    dragTargetIndex = indexForFraction(fraction)
                                },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false }
                            ) { change, _ ->
                                change.consume()
                                val fraction = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                dragFraction = fraction
                                dragTargetIndex = indexForFraction(fraction)
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
                            .padding(top = pillTopPadding)
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
                        text = stringResource(R.string.search_calls_placeholder),
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
                Spacer(modifier = Modifier.size(8.dp))
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

/** Distinct from Missed/Declined's shared red — a blocked call never even rang, so it gets its
 * own color instead of blending into "you missed something" red. Red in light mode; a lighter
 * pink in dark mode, since a dark red is too close to the dark background to actually read.
 * Same shades ContactDetailScreen uses for the same call type, in its own call-history rows. */
@Composable
private fun blockedCallColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFF6B9D) else Color(0xFFD32F2F)

/** Last-10-digits comparison — CallLog's own NUMBER and BlockedNumberContract's stored number can
 * differ in formatting (spaces, +country code) for what's really the same number. */
private fun normalizeForBlockMatch(number: String): String = number.filter { it.isDigit() }.takeLast(10)

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallLogRow(
    call: CallLogItem,
    isBlocked: Boolean = false,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
    onDeleted: () -> Unit = {},
    onAddToContact: () -> Unit = {},
    highlightQuery: String = ""
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // A number currently on the block list always reads as "Blocked" here, regardless of what
    // CallLog itself recorded for this specific call — many OEMs never set BLOCKED_TYPE, and a
    // call logged before the number was blocked never has it either.
    val displayType = if (isBlocked) CallType.BLOCKED else call.type
    val badgeColor = when (displayType) {
        CallType.INCOMING -> primaryAccentColor()
        CallType.OUTGOING -> Color(0xFF1DA463)
        CallType.MISSED, CallType.REJECTED -> Color(0xFFE0413B)
        CallType.BLOCKED -> blockedCallColor()
        CallType.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val directionIcon = when (displayType) {
        CallType.INCOMING -> Icons.Filled.CallReceived
        CallType.OUTGOING -> Icons.Filled.CallMade
        CallType.MISSED, CallType.REJECTED -> Icons.Filled.CallMissed
        CallType.BLOCKED -> Icons.Filled.Block
        CallType.OTHER -> null
    }

    Box {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (displayType == CallType.OTHER) {
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

        Spacer(modifier = Modifier.size(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightMatches(
                    buildString {
                        append(call.name ?: call.presentationLabel ?: call.number)
                        if (call.callCount > 1) append(" (${call.callCount})")
                    },
                    highlightQuery,
                    MaterialTheme.colorScheme.primary
                ),
                color = if (displayType == CallType.MISSED) badgeColor else MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (displayType == CallType.BLOCKED) stringResource(R.string.blocked_label) else formatTime(call.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = if (displayType == CallType.BLOCKED) badgeColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable(onClick = onInfoClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Contact info",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }

    // A dedicated zero-size anchor pinned to the row's right edge — DropdownMenu's own `modifier`
    // parameter applies to its popup content, not to where it anchors, so aligning it directly
    // didn't move the menu; wrapping it in a normal Box that itself respects `.align()` does.
    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = call.presentationLabel ?: call.number,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        // Only for numbers that aren't already a saved contact — CACHED_NAME is null/absent for
        // those, matching the reference app's own "Add to contact" being unknown-number-only.
        if (call.name == null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_add_to_contact)) },
                leadingIcon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                onClick = {
                    showMenu = false
                    onAddToContact()
                }
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_copy_number)) },
            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
            onClick = {
                showMenu = false
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.hint_phone_number), call.number))
                Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_call)) },
            leadingIcon = { Icon(Icons.Filled.Call, contentDescription = null) },
            onClick = {
                showMenu = false
                CallUtils.placeCall(context, call.number)
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_message)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null) },
            onClick = {
                showMenu = false
                MessageUtils.sendMessage(context, call.number)
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            onClick = {
                showMenu = false
                showDeleteConfirm = true
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_video_call)) },
            leadingIcon = { Icon(Icons.Filled.Videocam, contentDescription = null) },
            onClick = {
                showMenu = false
                WhatsAppUtils.openChat(context, call.number)
            }
        )
    }
    }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text(stringResource(R.string.clear_call_history_title)) },
            text = { Text(stringResource(R.string.clear_history_confirm_message, call.number)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    coroutineScope.launch {
                        CallLogRepository.deleteAllForNumber(context, call.number)
                        onDeleted()
                    }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
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

