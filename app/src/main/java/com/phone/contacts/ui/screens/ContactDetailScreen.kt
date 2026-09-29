package com.phone.contacts.ui.screens

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.media.RingtoneManager
import android.provider.BlockedNumberContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Call
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import com.phone.contacts.R
import com.phone.contacts.data.CallLogItem
import com.phone.contacts.data.CallLogRepository
import com.phone.contacts.data.CallType
import com.phone.contacts.data.Contact
import com.phone.contacts.data.ContactRepository
import com.phone.contacts.ui.components.verticalScrollIndicator
import com.phone.contacts.util.CallUtils
import com.phone.contacts.util.MessageUtils
import com.phone.contacts.util.RecentlyViewedContacts
import com.phone.contacts.util.WhatsAppUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private val WHATSAPP_GREEN = Color(0xFF25D366)

/** Matches the reference app's contact detail screen (back arrow, avatar, name, Call/Text/
 * WhatsApp/Video-call row, number card, WhatsApp quick actions, "Call history", bottom
 * Favorites/Edit/Delete/More bar) — opened from both a Contacts row tap and a Recents "i" tap.
 * [contactId] arrives null when opened from a Recents entry, since call-log rows don't carry a
 * contact id — but the number may still belong to a real saved contact, so it's resolved via
 * [ContactRepository.findContactByNumber] below rather than leaving Favorites/Delete permanently
 * disabled for every Recents-opened contact regardless of whether it's actually saved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contactId: String?,
    name: String,
    number: String,
    photoUri: String?,
    isStarred: Boolean,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var resolvedContactId by remember(contactId) { mutableStateOf(contactId) }
    var starred by remember(contactId) { mutableStateOf(isStarred) }
    LaunchedEffect(contactId, number) {
        if (contactId == null && number.isNotBlank()) {
            val resolved = ContactRepository.findContactByNumber(context, number)
            if (resolved != null) {
                resolvedContactId = resolved.id
                starred = resolved.isStarred
            }
        }
    }
    var showCallHistory by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    val whatsAppInstalled = remember { WhatsAppUtils.isInstalled(context) }
    val whatsAppIcon = ImageVector.vectorResource(id = R.drawable.ic_whatsapp)

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val id = resolvedContactId ?: return@rememberLauncherForActivityResult
            val uri = result.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            coroutineScope.launch {
                ContactRepository.setCustomRingtone(context, id, uri)
            }
        }
    }

    LaunchedEffect(number) {
        RecentlyViewedContacts.recordView(context, number)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Everything above the Favorites/Edit/Delete/More bar scrolls in its own weighted
        // Column, so that bar always stays pinned to the bottom of the screen instead of just
        // trailing along after whatever content happens to fit.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ContactAvatar(name = name, photoUri = photoUri, size = 96.dp)
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = name,
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
                label = "Call",
                modifier = Modifier.weight(1f),
                onClick = { CallUtils.placeCall(context, number) }
            )
            DetailActionButton(
                icon = Icons.AutoMirrored.Outlined.Message,
                label = "Text",
                modifier = Modifier.weight(1f),
                onClick = { MessageUtils.sendMessage(context, number) }
            )
            DetailActionButton(
                icon = whatsAppIcon,
                label = "Whatsapp",
                enabled = whatsAppInstalled,
                modifier = Modifier.weight(1f),
                onClick = { WhatsAppUtils.openChat(context, number) }
            )
            DetailActionButton(
                icon = Icons.Outlined.Videocam,
                label = "Video call",
                enabled = false, // kept disabled for now
                modifier = Modifier.weight(1f),
                onClick = { WhatsAppUtils.openChat(context, number) }
            )
        }

        Spacer(modifier = Modifier.size(20.dp))

        Surface(
            onClick = { CallUtils.placeCall(context, number) },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Mobile",
                    color = MaterialTheme.colorScheme.primary,
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

        if (whatsAppInstalled) {
            Spacer(modifier = Modifier.size(16.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            ) {
                Column {
                    WhatsAppActionRow(label = "Message $number") { WhatsAppUtils.openChat(context, number) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    WhatsAppActionRow(label = "Voice call $number") { WhatsAppUtils.openChat(context, number) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    WhatsAppActionRow(label = "Video call $number") { WhatsAppUtils.openChat(context, number) }
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
                    text = "Call history",
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
                label = "Favorites",
                enabled = resolvedContactId != null,
                tint = if (starred) Color(0xFFFFC107) else MaterialTheme.colorScheme.onBackground,
                onClick = {
                    val id = resolvedContactId ?: return@BottomBarAction
                    val newStarred = !starred
                    starred = newStarred
                    coroutineScope.launch { ContactRepository.setStarred(context, id, newStarred) }
                }
            )
            BottomBarAction(
                icon = Icons.Filled.Edit,
                label = "Edit",
                onClick = { Toast.makeText(context, "Editing coming soon", Toast.LENGTH_SHORT).show() }
            )
            BottomBarAction(
                icon = Icons.Filled.Delete,
                label = "Delete",
                enabled = resolvedContactId != null,
                onClick = { showDeleteConfirm = true }
            )
            Box {
                BottomBarAction(
                    icon = Icons.Filled.MoreVert,
                    label = "More",
                    onClick = { moreMenuExpanded = true }
                )
                DropdownMenu(
                    expanded = moreMenuExpanded,
                    onDismissRequest = { moreMenuExpanded = false },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Block") },
                        onClick = {
                            moreMenuExpanded = false
                            coroutineScope.launch {
                                val blocked = withContext(Dispatchers.IO) {
                                    if (BlockedNumberContract.canCurrentUserBlockNumbers(context)) {
                                        val values = ContentValues().apply {
                                            put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
                                        }
                                        context.contentResolver.insert(
                                            BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                                            values
                                        ) != null
                                    } else {
                                        false
                                    }
                                }
                                Toast.makeText(
                                    context,
                                    if (blocked) "$name blocked" else "Set this app as default phone app to block numbers",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Set Ringtone") },
                        enabled = resolvedContactId != null,
                        onClick = {
                            moreMenuExpanded = false
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select ringtone")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            }
                            ringtonePickerLauncher.launch(intent)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            moreMenuExpanded = false
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "$name\n$number")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share contact"))
                        }
                    )
                }
            }
        }
    }

    if (showCallHistory) {
        CallHistoryFullScreen(
            name = name,
            number = number,
            onBack = { showCallHistory = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Delete contact?") },
            text = { Text("$name will be moved to Recycle bin.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = resolvedContactId
                    showDeleteConfirm = false
                    if (id != null) {
                        coroutineScope.launch {
                            ContactRepository.moveToRecycleBin(context, listOf(Contact(id = id, name = name, number = number, photoUri = photoUri)))
                            onDeleted()
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
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

    LaunchedEffect(number) {
        calls = CallLogRepository.fetchCallHistoryForNumber(context, number)
    }
    val grouped = remember(calls) { groupCallsByDate(calls) }
    val listState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
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
                        text = "No calls with this number",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScrollIndicator(listState, MaterialTheme.colorScheme.primary)
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
                                CallHistoryDetailRow(call)
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
            title = { Text("Clear Call History") },
            text = { Text("Clear all $number's history?") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    coroutineScope.launch {
                        CallLogRepository.deleteAllForNumber(context, number)
                        calls = emptyList()
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun groupCallsByDate(calls: List<CallLogItem>): List<Pair<String, List<CallLogItem>>> {
    val today = java.util.Calendar.getInstance()
    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    val cal = java.util.Calendar.getInstance()

    fun isSameDay(a: java.util.Calendar, b: java.util.Calendar) =
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
            a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)

    fun labelFor(timestamp: Long): String {
        cal.timeInMillis = timestamp
        return when {
            isSameDay(cal, today) -> "Today"
            isSameDay(cal, yesterday) -> "Yesterday"
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
            tint = tint ?: MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground
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
private fun CallHistoryDetailRow(call: CallLogItem) {
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
    val subtitle = when (call.type) {
        CallType.INCOMING -> listOfNotNull("Incoming", formatCallDuration(call.durationSeconds).ifEmpty { null }).joinToString(" ")
        CallType.OUTGOING -> listOfNotNull("Outgoing", formatCallDuration(call.durationSeconds).ifEmpty { null }).joinToString(" ")
        CallType.MISSED -> "Missed call"
        CallType.REJECTED -> "Declined call"
        CallType.BLOCKED -> "Blocked call"
        CallType.OTHER -> "Call"
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            if (call.type == CallType.OTHER) {
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
