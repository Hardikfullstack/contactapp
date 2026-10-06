package com.phone.contacts.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.phone.contacts.R
import com.phone.contacts.data.Contact
import com.phone.contacts.data.ContactRepository
import com.phone.contacts.ui.components.ListRowSkeleton
import com.phone.contacts.ui.components.ScreenSearchField
import com.phone.contacts.ui.components.ScreenTitleBar
import com.phone.contacts.ui.components.verticalScrollIndicator
import com.phone.contacts.ui.features.onboarding.SetDefaultScreen
import com.phone.contacts.util.DefaultDialerState
import com.phone.contacts.util.DisplayOptionsPreferences
import com.phone.contacts.util.RecentlyAddedContacts
import com.phone.contacts.util.RecentlyViewedContacts
import com.phone.contacts.util.formattedForDisplay
import com.phone.contacts.util.sortKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun ContactsScreen(onAddContactClick: () -> Unit, onContactClick: (Contact) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current

    // Same default-dialer gate as RecentsScreen — until the app holds ROLE_DIALER, show the
    // set-as-default prompt here too instead of the contact list.
    remember { DefaultDialerState.refresh(context) }
    val isDefaultDialer by DefaultDialerState.isDefault
    if (!isDefaultDialer) {
        SetDefaultScreen(onSetAsDefault = { DefaultDialerState.refresh(context) })
        return
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    var allContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            ContactRepository.fetchContacts(context).collect { list ->
                allContacts = list
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    // RecentlyViewedContacts is a SharedPreferences read, not a Flow — this bumps it fresh
    // whenever the screen resumes (e.g. navigating back from the contact detail screen after
    // recording a view), the same pattern RecentsScreen uses to re-fetch its own call log.
    var viewedRefreshTrigger by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewedRefreshTrigger++
                DefaultDialerState.refresh(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    remember { DisplayOptionsPreferences.initialize(context) }
    val sortOrderPref by DisplayOptionsPreferences.sortOrder

    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ContactFilter.ALL) }
    var sortDescending by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val filtered = remember(allContacts, query) {
        if (query.isBlank()) {
            allContacts
        } else {
            allContacts.filter {
                it.name.contains(query, ignoreCase = true) || it.number.contains(query)
            }
        }
    }

    // "All" shows the alphabet index, sorted A-Z/Z-A. "Recent added" and "Recent viewed" are each
    // driven by their own SharedPreferences-backed tracker ([RecentlyAddedContacts] recorded when
    // a contact is created through this app's own Add Contact screen; [RecentlyViewedContacts]
    // recorded whenever the contact detail screen is actually opened) — matching the reference
    // app's own tracked lists (each with a real per-contact timestamp, date-grouped, with its own
    // "Clear" action) instead of Android's system "last contacted" timestamp or contact-id order.
    val addedTimestamps = remember(selectedFilter, viewedRefreshTrigger) {
        if (selectedFilter == ContactFilter.RECENT_ADDED) RecentlyAddedContacts.addedTimestamps(context) else emptyMap()
    }
    val viewedTimestamps = remember(selectedFilter, viewedRefreshTrigger) {
        if (selectedFilter == ContactFilter.RECENT_VIEWED) RecentlyViewedContacts.viewedTimestamps(context) else emptyMap()
    }
    val orderedContacts = remember(filtered, selectedFilter, sortDescending, addedTimestamps, viewedTimestamps, sortOrderPref) {
        when (selectedFilter) {
            ContactFilter.ALL -> if (sortDescending) {
                filtered.sortedByDescending { it.sortKey(sortOrderPref) }
            } else {
                filtered.sortedBy { it.sortKey(sortOrderPref) }
            }
            ContactFilter.RECENT_ADDED -> filtered
                .filter { addedTimestamps.containsKey(it.number) }
                .sortedByDescending { addedTimestamps[it.number] }
            ContactFilter.RECENT_VIEWED -> filtered
                .filter { viewedTimestamps.containsKey(it.number) }
                .sortedByDescending { viewedTimestamps[it.number] }
        }
    }
    val showLetterIndex = selectedFilter == ContactFilter.ALL
    val grouped = remember(orderedContacts, showLetterIndex, sortDescending, sortOrderPref) {
        if (!showLetterIndex) {
            emptyMap()
        } else {
            val map = orderedContacts.groupBy { it.sortKey(sortOrderPref).firstOrNull()?.uppercaseChar()?.takeIf { c -> c.isLetter() } ?: '#' }
            if (sortDescending) map.toSortedMap(compareByDescending { it }) else map.toSortedMap()
        }
    }
    val groupedByAddedDate = remember(orderedContacts, selectedFilter, addedTimestamps) {
        if (selectedFilter != ContactFilter.RECENT_ADDED) {
            emptyList()
        } else {
            groupContactsByDate(orderedContacts, addedTimestamps)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // The title/selection bar stays fixed above everything below — it never scrolls.
        Column(modifier = Modifier.fillMaxSize()) {
            if (selectionMode) {
                SelectionTitleBar(
                    count = selectedIds.size,
                    onClose = {
                        selectionMode = false
                        selectedIds = emptySet()
                    },
                    onShare = {
                        val ids = selectedIds.toList()
                        coroutineScope.launch { shareContactsAsVcf(context, ids) }
                    },
                    onDelete = { showDeleteConfirm = true },
                    allSelected = selectedIds.isNotEmpty() && selectedIds.size == orderedContacts.size,
                    onToggleSelectAll = {
                        selectedIds = if (selectedIds.size == orderedContacts.size) {
                            emptySet()
                        } else {
                            orderedContacts.map { it.id }.toSet()
                        }
                    }
                )
            } else {
                ScreenTitleBar(
                    title = stringResource(R.string.contacts),
                    trailingAction = {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.select)) },
                                    onClick = {
                                        menuExpanded = false
                                        selectionMode = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.select_all_label)) },
                                    onClick = {
                                        menuExpanded = false
                                        selectionMode = true
                                        selectedIds = orderedContacts.map { it.id }.toSet()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (sortDescending) stringResource(R.string.action_ascending) else stringResource(R.string.action_descending)) },
                                    onClick = {
                                        menuExpanded = false
                                        sortDescending = !sortDescending
                                    }
                                )
                                if (selectedFilter == ContactFilter.RECENT_ADDED) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.clear_recently_added_label)) },
                                        onClick = {
                                            menuExpanded = false
                                            RecentlyAddedContacts.clear(context)
                                            viewedRefreshTrigger++
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }

            if (!hasPermission) {
                PlaceholderScreen(title = stringResource(R.string.permission_required_title))
            } else if (isLoading) {
                ScreenSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.search_contacts_placeholder),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )
                ListRowSkeleton()
            } else if (orderedContacts.isEmpty()) {
                ScreenSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.search_contacts_placeholder),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ContactFilter.entries.forEach { filter ->
                        FilterChip(
                            label = filter.label,
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter }
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (selectedFilter) {
                            ContactFilter.RECENT_VIEWED -> stringResource(R.string.empty_recently_contacted)
                            ContactFilter.RECENT_ADDED -> stringResource(R.string.empty_recently_added)
                            ContactFilter.ALL -> stringResource(R.string.empty_no_contacts_found)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val listState = rememberLazyListState()

                // Cumulative item-index each letter group's header sits at, so the current group
                // can be looked up from just the first visible item index. Starts at 2 — the
                // search field and filter row are the first two LazyColumn items, ahead of the
                // letter groups, so they scroll away with the rest of the content.
                val sectionStarts = remember(grouped) {
                    val starts = mutableListOf<Int>()
                    var index = 2
                    grouped.forEach { (_, contactsInGroup) ->
                        starts.add(index)
                        index += 1 + contactsInGroup.size
                    }
                    starts
                }
                val groupedEntries = remember(grouped) { grouped.entries.toList() }
                val currentSectionLabel by remember(groupedEntries, sectionStarts) {
                    derivedStateOf {
                        val firstVisible = listState.firstVisibleItemIndex
                        val sectionIndex = sectionStarts.indexOfLast { it <= firstVisible }.coerceAtLeast(0)
                        groupedEntries.getOrNull(sectionIndex)?.key?.toString().orEmpty()
                    }
                }
                val canScrollUp by remember {
                    derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
                }
                // Measured height of the search field + filter row so the scroll thumb's track
                // starts right where the actual contact list begins, not at the very top of the
                // screen (which sits behind that header before you've scrolled at all).
                var searchFieldHeightPx by remember { mutableStateOf(0) }
                var filterRowHeightPx by remember { mutableStateOf(0) }
                val density = LocalDensity.current
                val headerHeight = with(density) { (searchFieldHeightPx + filterRowHeightPx).toDp() }
                // How much of the search field + filter row has scrolled past, in px — matches the
                // reference app's collapsing-header behavior, where the current-letter pill slides
                // up together with the header as it scrolls away, then sticks right below the fixed
                // title bar once fully scrolled past, instead of staying pinned at the header's
                // unscrolled height (which would float in empty space above the list once scrolled).
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
                // How far down the list is scrolled, as a 0..1 fraction — drives the alphabet
                // thumb's vertical position on the right edge when the user isn't dragging it.
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
                        modifier = if (showLetterIndex) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .fillMaxSize()
                                .verticalScrollIndicator(listState, MaterialTheme.colorScheme.primary)
                        }
                    ) {
                        item {
                            ScreenSearchField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = stringResource(R.string.search_contacts_placeholder),
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
                                ContactFilter.entries.forEach { filter ->
                                    FilterChip(
                                        label = filter.label,
                                        selected = selectedFilter == filter,
                                        onClick = { selectedFilter = filter }
                                    )
                                }
                            }
                        }
                        if (showLetterIndex) {
                            grouped.forEach { (letter, contactsInGroup) ->
                                item(key = "header_$letter") {
                                    Column {
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 20.dp, vertical = 6.dp)
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = letter.toString(),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                        HorizontalDivider(
                                            modifier = Modifier.fillMaxWidth(),
                                            thickness = 1.dp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                        )
                                    }
                                }
                                items(contactsInGroup, key = { it.id }) { contact ->
                                    Column {
                                        ContactRow(
                                            contact = contact,
                                            selectionMode = selectionMode,
                                            isSelected = selectedIds.contains(contact.id),
                                            onClick = { onContactClick(contact) },
                                            onToggleSelect = {
                                                selectedIds = if (selectedIds.contains(contact.id)) {
                                                    selectedIds - contact.id
                                                } else {
                                                    selectedIds + contact.id
                                                }
                                            },
                                            onLongPress = {
                                                selectionMode = true
                                                selectedIds = selectedIds + contact.id
                                            }
                                        )
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 74.dp),
                                            thickness = 1.dp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                        )
                                    }
                                }
                            }
                        } else if (selectedFilter == ContactFilter.RECENT_ADDED) {
                            groupedByAddedDate.forEach { (dateLabel, contactsInGroup) ->
                                item(key = "added_header_$dateLabel") {
                                    Text(
                                        text = dateLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                    )
                                }
                                items(contactsInGroup, key = { it.id }) { contact ->
                                    Column {
                                        ContactRow(
                                            contact = contact,
                                            selectionMode = selectionMode,
                                            isSelected = selectedIds.contains(contact.id),
                                            onClick = { onContactClick(contact) },
                                            onToggleSelect = {
                                                selectedIds = if (selectedIds.contains(contact.id)) {
                                                    selectedIds - contact.id
                                                } else {
                                                    selectedIds + contact.id
                                                }
                                            },
                                            onLongPress = {
                                                selectionMode = true
                                                selectedIds = selectedIds + contact.id
                                            },
                                            trailingTime = addedTimestamps[contact.number]?.let { formatAddedTime(it) }
                                        )
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 74.dp),
                                            thickness = 1.dp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                        )
                                    }
                                }
                            }
                        } else {
                            items(orderedContacts, key = { it.id }) { contact ->
                                Column {
                                    ContactRow(
                                        contact = contact,
                                        selectionMode = selectionMode,
                                        isSelected = selectedIds.contains(contact.id),
                                        onClick = { onContactClick(contact) },
                                        onToggleSelect = {
                                            selectedIds = if (selectedIds.contains(contact.id)) {
                                                selectedIds - contact.id
                                            } else {
                                                selectedIds + contact.id
                                            }
                                        },
                                        onLongPress = {
                                            selectionMode = true
                                            selectedIds = selectedIds + contact.id
                                        }
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

                    if (showLetterIndex) {
                        var isDragging by remember { mutableStateOf(false) }
                        var dragFraction by remember { mutableStateOf(0f) }
                        var dragTargetIndex by remember { mutableStateOf<Int?>(null) }

                        fun indexForFraction(fraction: Float): Int {
                            val lastIndex = (groupedEntries.size - 1).coerceAtLeast(0)
                            val targetSection = (fraction * lastIndex).toInt().coerceIn(0, lastIndex)
                            return sectionStarts.getOrElse(targetSection) { 0 }
                        }

                        // A single reactive effect instead of launching a new coroutine per drag
                        // event — detectDragGestures' onDrag fires dozens of times a second, and each
                        // one racing independently for the list's scroll mutex is what made this feel
                        // janky. LaunchedEffect cancels the in-flight scroll and starts the new one
                        // through Compose's own mechanism instead of piling up competing launches.
                        LaunchedEffect(dragTargetIndex) {
                            dragTargetIndex?.let { listState.scrollToItem(it) }
                        }

                        val thumbHeight = 72.dp
                        val displayFraction = if (isDragging) dragFraction else scrollFraction
                        val thumbOffsetY = headerHeight +
                            ((maxHeight - headerHeight - thumbHeight) * displayFraction).coerceAtLeast(0.dp)

                        // Alphabet fast-scroll thumb — a fixed-size pill pinned to the right edge.
                        // Grabbing and dragging it (or the wider invisible strip behind it, for an
                        // easier target) jumps the list straight to that letter. It's only visible
                        // while actively scrolling/dragging, fading out 2 seconds after settling.
                        val thumbAlpha by animateFloatAsState(
                            targetValue = if (listState.isScrollInProgress || isDragging) 1f else 0f,
                            animationSpec = tween(
                                durationMillis = if (listState.isScrollInProgress || isDragging) 150 else 400,
                                delayMillis = if (listState.isScrollInProgress || isDragging) 0 else 2000
                            ),
                            label = "thumb_alpha"
                        )

                        // Wider invisible drag zone — the visual pill is thin, but ContactsScreen's
                        // rows have nothing else living out at the edge (unlike Recents' "i"
                        // button), so this can stay generously wide. It's still this close to the
                        // OS's edge-swipe back-gesture zone though, so the rect is registered as a
                        // system-gesture exclusion below rather than relying on inset alone — the
                        // documented Android fix for edge-adjacent draggable UI like this.
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(top = headerHeight, end = 10.dp)
                                .width(44.dp)
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
                                .pointerInput(sectionStarts, groupedEntries) {
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
                            // tonalElevation (not a flat surfaceVariant fill) so the thumb reads as
                            // a distinct raised surface in dark mode too, where surfaceVariant and
                            // background are otherwise too close in value to tell apart.
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

                        // Letter bubble — only while actively dragging the thumb/strip, not during
                        // a normal list scroll (that's what the top pill below is for).
                        val bubbleAlpha by animateFloatAsState(
                            targetValue = if (isDragging && currentSectionLabel.isNotEmpty()) 1f else 0f,
                            animationSpec = tween(durationMillis = 150),
                            label = "letter_bubble_alpha"
                        )
                        if (bubbleAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-38).dp, y = thumbOffsetY + (thumbHeight - 44.dp) / 2)
                                    .size(44.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 20.dp,
                                            bottomStart = 20.dp,
                                            topEnd = 6.dp,
                                            bottomEnd = 20.dp
                                        )
                                    )
                                    .background(MaterialTheme.colorScheme.primary)
                                    .alpha(bubbleAlpha),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = currentSectionLabel,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }

                        // Top-center letter pill — driven by the exact same raw condition/timing
                        // as thumbAlpha (not "thumbAlpha > 0f", which is itself mid-fade and would
                        // make the pill start its own fade-out late, stacking an extra ~2.4s on
                        // top before it actually disappeared) so the two visually hide together.
                        val topPillAlpha by animateFloatAsState(
                            targetValue = if ((listState.isScrollInProgress || isDragging) && currentSectionLabel.isNotEmpty()) 1f else 0f,
                            animationSpec = tween(
                                durationMillis = if (listState.isScrollInProgress || isDragging) 150 else 400,
                                delayMillis = if (listState.isScrollInProgress || isDragging) 0 else 2000
                            ),
                            label = "top_pill_alpha"
                        )
                        if (topPillAlpha > 0f) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 2.dp,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = pillTopPadding)
                                    .alpha(topPillAlpha)
                            ) {
                                Text(
                                    text = currentSectionLabel,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // Jump-to-top button — same behavior and placement as RecentsScreen's.
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

        if (hasPermission && !selectionMode) {
            Surface(
                onClick = onAddContactClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add contact",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text(if (count == 1) stringResource(R.string.move_to_bin_title) else stringResource(R.string.move_multiple_to_bin_title, count)) },
            text = { Text(if (count == 1) stringResource(R.string.move_to_bin_message) else stringResource(R.string.move_multiple_to_bin_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = allContacts.filter { it.id in selectedIds }
                    showDeleteConfirm = false
                    coroutineScope.launch {
                        ContactRepository.moveToRecycleBin(context, toDelete)
                        selectionMode = false
                        selectedIds = emptySet()
                    }
                }) { Text(stringResource(R.string.action_move_to_bin)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

}

/** Shares one or more contacts as real .vcf files via the system share sheet — matching the
 * reference app's own selection-mode Share action exactly (it shares a genuine "Name.vcf", not
 * plain text), using Android's built-in `CONTENT_VCARD_URI` instead of hand-building vCard text. */
private suspend fun shareContactsAsVcf(context: android.content.Context, ids: List<String>) {
    val uris = ContactRepository.getVcardUris(context, ids)
    if (uris.isEmpty()) return
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/x-vcard"
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/x-vcard"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris))
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Share contact"))
}

private enum class ContactFilter(val label: String) {
    ALL("All"),
    RECENT_ADDED("Recent added"),
    RECENT_VIEWED("Recent viewed")
}

private fun formatAddedTime(timestamp: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(timestamp)

/** Groups contacts by the date they were added (via [RecentlyAddedContacts]'s timestamps),
 * newest group first — mirrors RecentsScreen's own "Today"/"Yesterday"/date call-log grouping. */
private fun groupContactsByDate(contacts: List<Contact>, timestamps: Map<String, Long>): List<Pair<String, List<Contact>>> {
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val cal = Calendar.getInstance()

    fun isSameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    fun labelFor(timestamp: Long): String {
        cal.timeInMillis = timestamp
        return when {
            isSameDay(cal, today) -> "Today"
            isSameDay(cal, yesterday) -> "Yesterday"
            else -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(timestamp)
        }
    }

    val result = LinkedHashMap<String, MutableList<Contact>>()
    contacts.forEach { contact ->
        val timestamp = timestamps[contact.number] ?: return@forEach
        result.getOrPut(labelFor(timestamp)) { mutableListOf() }.add(contact)
    }
    return result.map { it.key to it.value }
}

@Composable
private fun SelectionTitleBar(
    count: Int,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit
) {
    var moreMenuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Cancel selection",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = "$count",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        IconButton(onClick = onShare, enabled = count > 0) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "Share selected",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        IconButton(onClick = onDelete, enabled = count > 0) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete selected",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Box {
            IconButton(onClick = { moreMenuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            DropdownMenu(
                expanded = moreMenuExpanded,
                onDismissRequest = { moreMenuExpanded = false },
                shape = RoundedCornerShape(16.dp)
            ) {
                DropdownMenuItem(
                    text = { Text(if (allSelected) stringResource(R.string.action_deselect_all) else stringResource(R.string.select_all_label)) },
                    onClick = {
                        moreMenuExpanded = false
                        onToggleSelectAll()
                    }
                )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    contact: Contact,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onLongPress: () -> Unit,
    trailingTime: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
            .combinedClickable(
                onClick = if (selectionMode) onToggleSelect else onClick,
                onLongClick = { if (!selectionMode) onLongPress() }
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = contact.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            ContactAvatar(contact = contact, size = 40.dp)
        }
        Spacer(modifier = Modifier.size(14.dp))
        val nameFormat by DisplayOptionsPreferences.nameFormat
        Text(
            text = contact.name.formattedForDisplay(nameFormat),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (trailingTime != null) {
            Text(
                text = trailingTime,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Shows the contact's real photo when the provider has one, otherwise a colored initial. */
@Composable
fun ContactAvatar(contact: Contact, size: Dp) {
    ContactAvatar(name = contact.name, photoUri = contact.photoUri, size = size)
}

/** Same as the [Contact] overload, but takes the name/photo directly — for call sites (like the
 * contact detail screen) that only have a caller's name/number, not a full [Contact]. */
@Composable
fun ContactAvatar(name: String, photoUri: String?, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColorFor(name)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "#",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            // Scales with the avatar itself — a 40dp row avatar and a 120dp call-screen
            // avatar shouldn't show the same fixed-size letter.
            fontSize = (size.value * 0.4f).sp
        )
        if (photoUri != null) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private val avatarPalette = listOf(
    Color(0xFFE85C88),
    Color(0xFF1DA463),
    Color(0xFFE0413B),
    Color(0xFF9C27B0),
    Color(0xFFFF9800),
    Color(0xFF1CBFD5),
    Color(0xFF5BD902),
    Color(0xFF3F51B5),
    Color(0xFFFFC107),
    Color(0xFF2196F3)
)

private fun avatarColorFor(name: String): Color =
    avatarPalette[abs(name.hashCode()) % avatarPalette.size]
