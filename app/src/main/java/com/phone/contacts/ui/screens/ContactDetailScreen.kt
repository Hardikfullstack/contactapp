package com.phone.contacts.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import com.phone.contacts.R
import com.phone.contacts.ads.AdPlacements
import com.phone.contacts.ads.AdType
import com.phone.contacts.data.BlockRepository
import com.phone.contacts.data.CallLogItem
import com.phone.contacts.data.CallLogRepository
import com.phone.contacts.data.CallType
import com.phone.contacts.data.Contact
import com.phone.contacts.data.ContactRepository
import com.phone.contacts.data.FullContactData
import com.phone.contacts.ui.components.contactTypeLabel
import com.phone.contacts.ui.components.verticalScrollIndicator
import com.phone.contacts.ui.theme.primaryAccentColor
import com.phone.contacts.util.AnalyticsEvents
import com.phone.contacts.util.AnalyticsManager
import com.phone.contacts.util.AppConfigStore
import com.phone.contacts.util.CallUtils
import com.phone.contacts.util.GoogleMeetUtils
import com.phone.contacts.util.MessageUtils
import com.phone.contacts.util.RecentlyViewedContacts
import com.phone.contacts.util.WhatsAppUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private val WHATSAPP_GREEN = Color(0xFF25D366)

/** Distinct from Missed/Declined's shared red — a blocked call never even rang, so it gets its
 * own color instead of blending into "you missed something" red. Red in light mode; a lighter
 * pink in dark mode, since a dark red is too close to the dark background to actually read. */
@Composable
private fun blockedCallColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFF6B9D) else Color(0xFFD32F2F)

/** Last-10-digits comparison — CallLog's own NUMBER and BlockedNumberContract's stored number can
 * differ in formatting (spaces, +country code) for what's really the same number. */
private fun normalizeForBlockMatch(number: String): String = number.filter { it.isDigit() }.takeLast(10)

/** Matches the reference app's contact detail screen (back arrow, avatar, name, Call/Text/
 * WhatsApp/Video-call row, number card, WhatsApp quick actions, "Call history", bottom
 * Favorites/Edit/Delete/More bar) — opened from both a Contacts row tap and a Recents "i" tap.
 * [contactId] arrives null when opened from a Recents entry, since call-log rows don't carry a
 * contact id — but the number may still belong to a real saved contact, so it's resolved via
 * [ContactRepository.findContactByNumber] below rather than leaving Favorites/Delete permanently
 * disabled for every Recents-opened contact regardless of whether it's actually saved. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactDetailScreen(
    contactId: String?,
    name: String,
    number: String,
    photoUri: String?,
    isStarred: Boolean,
    contactUpdated: Boolean = false,
    onContactUpdatedConsumed: () -> Unit = {},
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onEditClick: (String) -> Unit = {},
    onSetRingtoneClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val adConfig by AppConfigStore.config.collectAsState()
    var resolvedContactId by remember(contactId) { mutableStateOf(contactId) }
    var starred by remember(contactId) { mutableStateOf(isStarred) }
    // Both start from the nav arguments and show instantly (no lag, matching the Contacts list) —
    // only re-fetched when [contactUpdated] says Edit actually just saved (see getContactSummary's
    // doc), not on every plain resume/first-open, which just re-showed the same value anyway at
    // the cost of a visible delay.
    var currentName by remember(name) { mutableStateOf(name) }
    var currentPhotoUri by remember(photoUri) { mutableStateOf(photoUri) }
    LaunchedEffect(contactId, number) {
        if (contactId == null && number.isNotBlank()) {
            val resolved = ContactRepository.findContactByNumber(context, number)
            if (resolved != null) {
                resolvedContactId = resolved.id
                starred = resolved.isStarred
                // Opened from Recents, which only ever has a name/number, not the contact's photo
                // — this lookup is the only place that ever learns it, so it has to be applied here,
                // not just used to resolve the id.
                currentName = resolved.name
                currentPhotoUri = resolved.photoUri
            }
        }
    }
    LaunchedEffect(contactUpdated) {
        if (contactUpdated) {
            resolvedContactId?.let { id ->
                ContactRepository.getContactSummary(context, id)?.let { summary ->
                    summary.name?.let { currentName = it }
                    currentPhotoUri = summary.photoUri
                }
            }
            onContactUpdatedConsumed()
        }
    }
    var showCallHistory by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    val whatsAppInstalled = remember { WhatsAppUtils.isInstalled(context) }
    val whatsAppIcon = ImageVector.vectorResource(id = R.drawable.ic_whatsapp)
    val meetInstalled = remember { GoogleMeetUtils.isInstalled(context) }
    // Gates the expanded Message/Voice call/Video call via WhatsApp section - unlike
    // whatsAppInstalled (device-level), this is per-contact: whether THIS contact actually has
    // WhatsApp (see WhatsAppUtils.hasWhatsAppContact). Re-checked once resolvedContactId resolves.
    val hasWhatsAppContact = remember(resolvedContactId) {
        whatsAppInstalled && WhatsAppUtils.hasWhatsAppContact(context, resolvedContactId)
    }

    // Reactive — updates immediately if the number is blocked/unblocked from anywhere (this
    // screen's own menu, or the Blocking settings screen), not just on first composition.
    var isNumberBlocked by remember { mutableStateOf(false) }
    LaunchedEffect(number) {
        BlockRepository.blockedNumbersFlow(context).collect { entries ->
            isNumberBlocked = entries.any { normalizeForBlockMatch(it.number) == normalizeForBlockMatch(number) }
        }
    }

    LaunchedEffect(number) {
        RecentlyViewedContacts.recordView(context, number)
    }

    // Email/Address/Website/Important dates/Work info/Relation/Notes - the same extra fields the
    // Add Contact screen can save (FullContactData / ContactRepository.fetchFullContact), just not
    // previously surfaced anywhere on this screen.
    var fullContact by remember(resolvedContactId) { mutableStateOf<FullContactData?>(null) }
    LaunchedEffect(resolvedContactId, contactUpdated) {
        val id = resolvedContactId
        fullContact = if (id != null) ContactRepository.fetchFullContact(context, id) else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Fixed, outside the scrolling content below — matches every other screen's back button
        // (Blocking/ManageBlockList/ImportExport/RecycleBin/Theme all use this same Row+padding),
        // instead of scrolling away with the rest of the page.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        // Everything above the Favorites/Edit/Delete/More bar scrolls in its own weighted
        // Column, so that bar always stays pinned to the bottom of the screen instead of just
        // trailing along after whatever content happens to fit.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val displayName = currentName.ifBlank { if (number.isBlank()) stringResource(R.string.unknown) else number }
            ContactAvatar(name = displayName, photoUri = currentPhotoUri, size = 96.dp)
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = currentName.ifBlank { if (number.isBlank()) stringResource(R.string.unknown) else number },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailActionButton(
                icon = Icons.Outlined.Call,
                label = stringResource(R.string.action_call),
                modifier = Modifier.weight(1f),
                onClick = { CallUtils.placeCall(context, number) }
            )
            DetailActionButton(
                icon = Icons.AutoMirrored.Outlined.Message,
                label = stringResource(R.string.action_text),
                modifier = Modifier.weight(1f),
                onClick = { MessageUtils.sendMessage(context, number) }
            )
            DetailActionButton(
                icon = whatsAppIcon,
                label = stringResource(R.string.action_whatsapp),
                enabled = whatsAppInstalled,
                modifier = Modifier.weight(1f),
                onClick = { WhatsAppUtils.openChat(context, number) }
            )
            DetailActionButton(
                icon = Icons.Outlined.Videocam,
                label = stringResource(R.string.action_video_call),
                enabled = meetInstalled,
                modifier = Modifier.weight(1f),
                onClick = { GoogleMeetUtils.launchMeetCall(context, number) }
            )
        }

        Spacer(modifier = Modifier.size(20.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .combinedClickable(
                    onClick = { CallUtils.placeCall(context, number) },
                    onLongClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.hint_phone_number), number))
                        Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    }
                )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.type_mobile),
                    color = primaryAccentColor(),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = number,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        fullContact?.let { details -> ContactExtraDetailsCard(details) }

        if (hasWhatsAppContact) {
            Spacer(modifier = Modifier.size(16.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            ) {
                Column {
                    WhatsAppActionRow(label = stringResource(R.string.message_number_label, number)) { WhatsAppUtils.openChat(context, number) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    WhatsAppActionRow(label = stringResource(R.string.voice_call_number_label, number)) {
                        if (!WhatsAppUtils.launchVoiceCall(context, resolvedContactId)) {
                            WhatsAppUtils.openChat(context, number)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    WhatsAppActionRow(label = stringResource(R.string.video_call_number_label, number)) {
                        if (!WhatsAppUtils.launchVideoCall(context, resolvedContactId)) {
                            WhatsAppUtils.openChat(context, number)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.size(24.dp))

        Surface(
            onClick = { showCallHistory = true },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.call_history_title),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.size(16.dp))
        } // end scrollable content Column

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomBarAction(
                icon = if (starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                label = stringResource(R.string.favorites),
                enabled = resolvedContactId != null,
                tint = if (starred) Color(0xFFFFC107) else MaterialTheme.colorScheme.onBackground,
                onClick = {
                    val id = resolvedContactId ?: return@BottomBarAction
                    val newStarred = !starred
                    starred = newStarred
                    AnalyticsManager.logEventWithAction(
                        AnalyticsEvents.CONTACT_UPDATED,
                        AnalyticsEvents.SCREEN_CONTACT_DETAIL,
                        AnalyticsEvents.ACTION_FAVORITE_TOGGLED,
                        mapOf(AnalyticsEvents.PARAM_STARRED to newStarred)
                    )
                    coroutineScope.launch { ContactRepository.setStarred(context, id, newStarred) }
                }
            )
            BottomBarAction(
                icon = Icons.Filled.Edit,
                label = stringResource(R.string.action_edit),
                enabled = resolvedContactId != null,
                onClick = { resolvedContactId?.let(onEditClick) }
            )
            BottomBarAction(
                icon = Icons.Filled.Delete,
                label = stringResource(R.string.action_delete),
                enabled = resolvedContactId != null,
                onClick = { showDeleteConfirm = true }
            )
            Box {
                BottomBarAction(
                    icon = Icons.Filled.MoreVert,
                    label = stringResource(R.string.action_more),
                    onClick = { moreMenuExpanded = true }
                )
                val unblockedToastMessage = stringResource(R.string.unblocked_toast, currentName)
                val blockedToastMessage = stringResource(R.string.blocked_toast, currentName)
                val blockDeniedToastMessage = stringResource(R.string.set_default_to_block_message)
                val shareContactChooserTitle = stringResource(R.string.share_contact_chooser_title)
                DropdownMenu(
                    expanded = moreMenuExpanded,
                    onDismissRequest = { moreMenuExpanded = false },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isNumberBlocked) stringResource(R.string.action_unblock) else stringResource(R.string.action_block)) },
                        onClick = {
                            moreMenuExpanded = false
                            coroutineScope.launch {
                                val success = if (isNumberBlocked) {
                                    BlockRepository.unblockByNumber(context, number)
                                } else {
                                    BlockRepository.blockNumber(context, number)
                                }
                                val message = when {
                                    success && isNumberBlocked -> unblockedToastMessage
                                    success -> blockedToastMessage
                                    else -> blockDeniedToastMessage
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_set_ringtone)) },
                        enabled = resolvedContactId != null,
                        onClick = {
                            moreMenuExpanded = false
                            resolvedContactId?.let { onSetRingtoneClick(it) }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_share)) },
                        enabled = resolvedContactId != null,
                        onClick = {
                            moreMenuExpanded = false
                            val id = resolvedContactId ?: return@DropdownMenuItem
                            coroutineScope.launch {
                                val uris = ContactRepository.getVcardUris(context, listOf(id))
                                val uri = uris.firstOrNull() ?: return@launch
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/x-vcard"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, shareContactChooserTitle))
                            }
                        }
                    )
                }
            }
        }

        AdPlacements.adUnitId(adConfig?.result, AdType.NATIVE, slot = 3)?.let {
            com.phone.contacts.ads.NativeAdView(
                adUnitId = it,
                template = com.phone.contacts.ads.NativeAdTemplate.SMALL
            )
        }
    }

    if (showCallHistory) {
        CallHistoryFullScreen(
            name = currentName,
            number = number,
            onBack = { showCallHistory = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text(stringResource(R.string.move_to_bin_title)) },
            text = { Text(stringResource(R.string.move_to_bin_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val id = resolvedContactId
                    showDeleteConfirm = false
                    if (id != null) {
                        coroutineScope.launch {
                            ContactRepository.moveToRecycleBin(context, listOf(Contact(id = id, name = currentName, number = number, photoUri = currentPhotoUri)))
                            onDeleted()
                        }
                    }
                }) { Text(stringResource(R.string.action_move_to_bin)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun CallHistoryFullScreen(name: String, number: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var calls by remember { mutableStateOf<List<CallLogItem>>(emptyList()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var isNumberBlocked by remember { mutableStateOf(false) }

    LaunchedEffect(number) {
        calls = CallLogRepository.fetchCallHistoryForNumber(context, number)
    }
    // Reactive — same reasoning as the main screen's own "Block"/"Unblock" menu item above.
    LaunchedEffect(number) {
        BlockRepository.blockedNumbersFlow(context).collect { entries ->
            isNumberBlocked = entries.any { normalizeForBlockMatch(it.number) == normalizeForBlockMatch(number) }
        }
    }
    val todayLabel = stringResource(R.string.date_today)
    val yesterdayLabel = stringResource(R.string.date_yesterday)
    val grouped = remember(calls, todayLabel, yesterdayLabel) { groupCallsByDate(calls, todayLabel, yesterdayLabel) }
    val listState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (isNumberBlocked) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Block,
                                contentDescription = null,
                                tint = blockedCallColor(),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.blocked_label),
                                color = blockedCallColor(),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
                IconButton(onClick = { showClearDialog = true }, enabled = calls.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Clear call history",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (calls.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.no_calls_with_number),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScrollIndicator(listState, primaryAccentColor())
                ) {
                    grouped.forEach { (dateLabel, callsInGroup) ->
                        item {
                            Text(
                                text = dateLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                        items(callsInGroup, key = { it.id }) { call ->
                            Column {
                                CallHistoryDetailRow(call, isBlocked = isNumberBlocked)
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 68.dp),
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text(stringResource(R.string.clear_call_history_title)) },
            text = { Text(stringResource(R.string.clear_history_confirm_message, number)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    coroutineScope.launch {
                        CallLogRepository.deleteAllForNumber(context, number)
                        AnalyticsManager.logEventWithAction(
                            AnalyticsEvents.CALL_HISTORY_CLEARED,
                            AnalyticsEvents.SCREEN_CONTACT_DETAIL,
                            AnalyticsEvents.ACTION_SUCCESS
                        )
                        calls = emptyList()
                    }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

private fun groupCallsByDate(calls: List<CallLogItem>, todayLabel: String, yesterdayLabel: String): List<Pair<String, List<CallLogItem>>> {
    val today = java.util.Calendar.getInstance()
    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    val cal = java.util.Calendar.getInstance()

    fun isSameDay(a: java.util.Calendar, b: java.util.Calendar) =
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
            a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)

    fun labelFor(timestamp: Long): String {
        cal.timeInMillis = timestamp
        return when {
            isSameDay(cal, today) -> todayLabel
            isSameDay(cal, yesterday) -> yesterdayLabel
            else -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(timestamp)
        }
    }

    val result = LinkedHashMap<String, MutableList<CallLogItem>>()
    calls.forEach { call -> result.getOrPut(labelFor(call.timestamp)) { mutableListOf() }.add(call) }
    return result.map { it.key to it.value }
}

private fun formatCallDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val minutes = seconds / 60
    val secs = seconds % 60
    return when {
        minutes > 0 && secs > 0 -> "$minutes min $secs sec"
        minutes > 0 -> "$minutes min"
        else -> "$secs seconds"
    }
}

@Composable
private fun DetailActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: primaryAccentColor()
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/** Email/Address/Website/Important dates/Work info/Relation/Notes - the same extra fields the Add
 * Contact screen can save, now actually shown here too (previously silently dropped from this
 * screen even when a contact had them). One card, one row per non-blank entry, styled like this
 * app's own Mobile-number card above it rather than copying the reference app's look. */
@Composable
private fun ContactExtraDetailsCard(details: FullContactData) {
    val dateFormatter = remember { SimpleDateFormat("d MMMM, yyyy", Locale.getDefault()) }
    // Plain (non-composable) data gathering - the type/customLabel -> localized label resolution
    // (contactTypeLabel, which reads a string resource) happens below in DetailFieldRow itself,
    // not here, since this builder lambda isn't guaranteed composable-call-safe.
    val rows = remember(details) {
        buildList {
            details.emails.forEach { email ->
                if (email.value.isNotBlank()) add(DetailRowData(Icons.Filled.Email, R.string.type_email, email.value, email.type, email.customLabel))
            }
            details.addresses.forEach { address ->
                if (!address.isBlank()) {
                    val formatted = listOf(address.street, address.city, address.state, address.country, address.postcode)
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                    add(DetailRowData(Icons.Filled.LocationOn, R.string.type_address, formatted, address.type, address.customLabel))
                }
            }
            details.websites.forEach { website ->
                if (website.value.isNotBlank()) add(DetailRowData(Icons.Outlined.Language, R.string.type_website, website.value, website.type, website.customLabel))
            }
            details.importantDates.forEach { date ->
                add(DetailRowData(Icons.Outlined.CalendarToday, R.string.type_important_dates, dateFormatter.format(java.util.Date(date.dateMillis)), date.type, date.customLabel))
            }
            val workInfo = listOf(details.jobTitle, details.department, details.company).filter { it.isNotBlank() }.joinToString(", ")
            if (workInfo.isNotBlank()) add(DetailRowData(Icons.Filled.Work, R.string.work_info_label, workInfo, null, ""))
            details.relations.forEach { relation ->
                if (relation.value.isNotBlank()) add(DetailRowData(Icons.Outlined.FavoriteBorder, R.string.type_relation, relation.value, relation.type, relation.customLabel))
            }
            if (details.notes.isNotBlank()) add(DetailRowData(Icons.Filled.Notes, R.string.hint_notes, details.notes, null, ""))
        }
    }

    if (rows.isEmpty()) return

    Spacer(modifier = Modifier.size(16.dp))
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
    ) {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                DetailFieldRow(row)
            }
        }
    }
}

private data class DetailRowData(
    val icon: ImageVector,
    val categoryLabelRes: Int,
    val value: String,
    val type: String?,
    val customLabel: String
)

@Composable
private fun DetailFieldRow(row: DetailRowData) {
    val subLabel = row.type?.let { type ->
        if (type == "Custom" && row.customLabel.isNotBlank()) row.customLabel else contactTypeLabel(type)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(row.categoryLabelRes),
                style = MaterialTheme.typography.bodySmall,
                color = primaryAccentColor(),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.size(2.dp))
            // Same size/weight/color as the Mobile number's own value text above.
            Text(
                text = row.value,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            subLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Icon(
            imageVector = row.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WhatsAppActionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(WHATSAPP_GREEN),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_whatsapp),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Text(text = label, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun BottomBarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onBackground
) {
    Column(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .width(72.dp)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint)
        Spacer(modifier = Modifier.size(4.dp))
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun CallHistoryDetailRow(call: CallLogItem, isBlocked: Boolean = false) {
    // A number currently on the block list always reads as "Blocked" here, regardless of what
    // CallLog itself recorded for this specific call — see CallLogRow's identical reasoning.
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
    val subtitle = when (displayType) {
        CallType.INCOMING -> listOfNotNull(stringResource(R.string.call_type_incoming), formatCallDuration(call.durationSeconds).ifEmpty { null }).joinToString(" ")
        CallType.OUTGOING -> listOfNotNull(stringResource(R.string.call_type_outgoing), formatCallDuration(call.durationSeconds).ifEmpty { null }).joinToString(" ")
        CallType.MISSED -> stringResource(R.string.call_type_missed)
        CallType.REJECTED -> stringResource(R.string.call_type_declined)
        CallType.BLOCKED -> stringResource(R.string.blocked_label)
        CallType.OTHER -> stringResource(R.string.action_call)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            if (displayType == CallType.OTHER) {
                Icon(imageVector = Icons.Filled.Person, contentDescription = null, tint = badgeColor, modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                if (directionIcon != null) {
                    Icon(
                        imageVector = directionIcon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier
                            .offset(y = (-7).dp, x = 7.dp)
                            .size(13.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.size(14.dp))
        Column {
            Text(
                text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(call.timestamp),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (displayType == CallType.BLOCKED) badgeColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
