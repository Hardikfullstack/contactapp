package com.phone.contacts.ui.features.aftercall

import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import androidx.compose.foundation.layout.offset
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phone.contacts.MainActivity
import com.phone.contacts.R
import com.phone.contacts.data.CallLogItem
import com.phone.contacts.data.CallLogRepository
import com.phone.contacts.data.CallType
import com.phone.contacts.data.ContactRepository
import com.phone.contacts.data.local.AppDatabase
import com.phone.contacts.data.local.ReminderEntity
import com.phone.contacts.data.BlockRepository
import com.phone.contacts.util.CallReminderScheduler
import com.phone.contacts.util.CallUtils
import com.phone.contacts.util.MessageUtils
import com.phone.contacts.util.WhatsAppUtils
import com.phone.contacts.ui.theme.BrandPrimary
import com.phone.contacts.ui.theme.primaryAccentColor
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Palette taken from the sibling contactapp project so the two apps' after-call screens match.
private val AcRed = Color(0xFFE0413B)
private val CallGreen = Color(0xFF1DA463)
private val AcWhatsApp = Color(0xFF25D366)
// More-tab icon backgrounds: View Contact and Send message each get their own colour.
private val AcViewContact = Color(0xFF7E57C2)
private val AcSendMessage = Color(0xFF00897B)

// Theme-aware, so the after-call screen follows the system/app light-dark setting like the rest of
// the app. The accent is the light-mode brand blue in both modes (no lighter dark-mode variant here).
@Composable private fun acBackground(): Color = MaterialTheme.colorScheme.background
@Composable private fun acSurface(): Color = MaterialTheme.colorScheme.surfaceVariant
@Composable private fun acOnSurface(): Color = MaterialTheme.colorScheme.onBackground
@Composable private fun acSecondary(): Color = MaterialTheme.colorScheme.onSurfaceVariant
@Composable private fun acOutline(): Color = MaterialTheme.colorScheme.outline
@Composable private fun acStrip(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
@Composable private fun acAccent(): Color = BrandPrimary

private val ReminderColors = listOf(
    Color(0xFF109D6F), Color(0xFF3D6BF5), Color(0xFFE0413B), Color(0xFFFF9800),
    Color(0xFF9C27B0), Color(0xFF1CBFD5), Color(0xFFE85C88)
)

@Composable
fun AfterCallScreen(
    number: String,
    displayName: String?,
    durationSeconds: Long,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    // Swiping between the four tabs and tapping a tab both move this pager; tabs are its pages.
    val pagerState = rememberPagerState(pageCount = { 4 })
    val selectedTab = pagerState.currentPage
    val scope = rememberCoroutineScope()
    var resolvedName by remember { mutableStateOf(displayName) }
    var resolvedPhoto by remember { mutableStateOf<String?>(null) }
    // False for a number that isn't in the phone's contacts - More tab then offers "Add contact".
    var isSaved by remember { mutableStateOf(false) }
    LaunchedEffect(number) {
        ContactRepository.findContactByNumber(context, number)?.let {
            isSaved = true
            it.name?.let { n -> resolvedName = n }
            resolvedPhoto = it.photoUri
        }
    }
    val title = resolvedName?.takeIf { it.isNotBlank() } ?: number

    Scaffold(containerColor = acBackground()) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 84.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            title,
                            color = acOnSurface(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.size(12.dp))
                        Box(
                            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(BrandPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Contacts, contentDescription = null, tint = acAccent(), modifier = Modifier.size(22.dp))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().background(BrandPrimary.copy(alpha = 0.14f)).padding(start = 84.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "$number - ${stringResource(R.string.call_ended)}",
                                color = acSecondary(),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                stringResource(R.string.after_call_duration, formatDuration(durationSeconds)),
                                color = acSecondary(),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Box(
                            modifier = Modifier.size(30.dp).clickable {
                                CallUtils.placeCall(context, number)
                                onFinish()
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = null, tint = CallGreen, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 14.dp)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(acAccent()),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = resolvedName?.trim()?.take(1)?.uppercase()
                    if (!initial.isNullOrBlank()) {
                        Text(initial, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }

            val tabIcons = listOf<ImageVector>(
                Icons.AutoMirrored.Filled.List,
                Icons.AutoMirrored.Filled.Chat,
                Icons.Filled.Notifications,
                Icons.Filled.MoreHoriz
            )
            TabRow(selectedTabIndex = selectedTab, containerColor = acSurface(), contentColor = acAccent()) {
                tabIcons.forEachIndexed { index, icon ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        icon = {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (selectedTab == index) acAccent() else acSecondary()
                            )
                        }
                    )
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
                when (page) {
                    0 -> HistoryTab(number, resolvedName ?: number, resolvedPhoto)
                    1 -> QuickMessageTab(number)
                    2 -> ReminderTab(number, resolvedName ?: number)
                    else -> MoreTab(number, resolvedName ?: number, isSaved)
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(number: String, name: String, photoUri: String?) {
    val context = LocalContext.current
    var calls by remember { mutableStateOf<List<CallLogItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(number) {
        // Whole call log (like the Recents screen), not just this number - grouped by the repository.
        calls = CallLogRepository.fetchCallLogs(context)
        loaded = true
    }
    if (loaded && calls.isEmpty()) {
        CenteredMessage(stringResource(R.string.after_call_no_history))
        return
    }
    val rows = calls
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(rows, key = { _, item -> item.id }) { index, call ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hasName = name.isNotBlank() && name != number
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(acAccent()),
                            contentAlignment = Alignment.Center
                        ) {
                            val initial = name.trim().take(1).uppercase()
                            if (hasName && initial.isNotBlank()) {
                                Text(initial, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(Modifier.size(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                buildString {
                                    append(if (hasName) name else number)
                                    if (call.callCount > 1) append(" (${call.callCount})")
                                },
                                color = if (call.type == CallType.MISSED) AcRed else acOnSurface(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(formatWhen(call.timestamp), color = acSecondary(), fontSize = 13.sp)
                        }
                        Spacer(Modifier.size(8.dp))
                        // Same trailing phone + direction-arrow badge as the Recents list.
                        Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Call, contentDescription = null, tint = acSecondary(), modifier = Modifier.size(22.dp))
                            Icon(
                                callTypeIcon(call.type),
                                contentDescription = null,
                                tint = callTypeColor(call.type),
                                modifier = Modifier.offset(x = 7.dp, y = (-7.5).dp).size(14.dp)
                            )
                        }
                    }
                    if (index < rows.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 74.dp),
                            color = acOutline().copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

private fun callTypeIcon(type: CallType): ImageVector = when (type) {
    CallType.INCOMING -> Icons.Filled.CallReceived
    CallType.OUTGOING -> Icons.Filled.CallMade
    else -> Icons.Filled.CallMissed
}

@Composable
private fun QuickMessageTab(number: String) {
    val context = LocalContext.current
    val presets = listOf(
        stringResource(R.string.after_call_quick_reply_1),
        stringResource(R.string.after_call_quick_reply_2),
        stringResource(R.string.after_call_quick_reply_3),
        stringResource(R.string.after_call_quick_reply_4)
    )
    var selected by remember { mutableIntStateOf(-1) }
    var personal by remember { mutableStateOf("") }
    val text = if (selected >= 0) presets[selected] else personal

    fun send() {
        if (text.isBlank()) return
        if (MessageUtils.hasSendSmsPermission(context) && MessageUtils.sendSmsDirectly(context, number, text)) {
            Toast.makeText(context, context.getString(R.string.toast_sent), Toast.LENGTH_SHORT).show()
        } else {
            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply { putExtra("sms_body", text) })
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        presets.forEachIndexed { index, preset ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { selected = index }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, if (selected == index) acAccent() else acOutline(), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected == index) Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(acAccent()))
                }
                Spacer(Modifier.size(14.dp))
                Text(preset, color = acOnSurface(), fontSize = 16.sp, modifier = Modifier.weight(1f))
                // Once a preset is picked, its send icon appears at the end of that same row.
                if (selected == index) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = acAccent(),
                        modifier = Modifier.size(24.dp).clickable { send() }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(acSurface()).padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = personal,
                onValueChange = {
                    personal = it
                    selected = -1
                },
                textStyle = TextStyle(color = acOnSurface(), fontSize = 16.sp),
                cursorBrush = SolidColor(primaryAccentColor()),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (personal.isEmpty()) {
                        Text(stringResource(R.string.after_call_write_personal), color = acSecondary(), fontSize = 16.sp)
                    }
                    inner()
                }
            )
            // Compact send button: a filled accent circle once there is text to send.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (text.isNotBlank()) acAccent() else Color.Transparent)
                    .clickable(enabled = text.isNotBlank()) { send() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = if (text.isNotBlank()) Color.White else acSecondary(),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

    }
}


@Composable
private fun ReminderTab(number: String, name: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.getInstance(context).reminderDao() }
    val reminders by dao.getForNumber(number).collectAsState(initial = emptyList())
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        ReminderForm(
            onCancel = { creating = false },
            onSave = { note, time, colorIndex ->
                creating = false
                scope.launch {
                    val draft = ReminderEntity(
                        number = number, name = name, note = note, timeMillis = time, colorIndex = colorIndex
                    )
                    val id = dao.insert(draft)
                    CallReminderScheduler.schedule(context, draft.copy(id = id))
                }
            }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(acAccent())
                .clickable { creating = true }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.after_call_create_reminder), color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
        if (reminders.isEmpty()) {
            Text(stringResource(R.string.after_call_no_reminders), color = acSecondary(), modifier = Modifier.padding(8.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(reminders, key = { it.id }) { reminder ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(acSurface()).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(ReminderColors[reminder.colorIndex.coerceIn(0, ReminderColors.lastIndex)])
                        )
                        Spacer(Modifier.size(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(reminder.note.ifBlank { name }, color = acOnSurface(), fontWeight = FontWeight.Medium)
                            Text(reminderTimeLabel(reminder.timeMillis), color = acSecondary(), fontSize = 13.sp)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                CallReminderScheduler.cancel(context, reminder.id)
                                dao.deleteById(reminder.id)
                            }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = acSecondary())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderForm(onCancel: () -> Unit, onSave: (note: String, timeMillis: Long, colorIndex: Int) -> Unit) {
    val context = LocalContext.current
    var note by remember { mutableStateOf("") }
    var colorIndex by remember { mutableIntStateOf(0) }
    var timeMillis by remember { mutableLongStateOf(Calendar.getInstance().apply { add(Calendar.MINUTE, 1) }.timeInMillis) }
    var error by remember { mutableStateOf<String?>(null) }
    val pickFutureMsg = stringResource(R.string.after_call_pick_future_time)

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        BasicTextField(
            value = note,
            onValueChange = { note = it },
            textStyle = TextStyle(color = acOnSurface(), fontSize = 16.sp),
            cursorBrush = SolidColor(primaryAccentColor()),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(acSurface()).padding(horizontal = 18.dp, vertical = 14.dp),
            decorationBox = { inner ->
                if (note.isEmpty()) {
                    Text(stringResource(R.string.after_call_remind_me_about), color = acSecondary(), fontSize = 16.sp)
                }
                inner()
            }
        )
        Spacer(Modifier.height(16.dp))
        WheelTimePicker(initialMillis = timeMillis) {
            timeMillis = it
            error = null
        }
        error?.let { Text(it, color = AcRed, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReminderColors.forEachIndexed { index, color ->
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(color).clickable { colorIndex = index },
                    contentAlignment = Alignment.Center
                ) {
                    if (index == colorIndex) Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.White))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cancel), color = acAccent())
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(acAccent().copy(alpha = if (note.isNotBlank()) 1f else 0.4f))
                    // Same as contactapp: a reminder needs a message, so Save stays disabled until one is typed.
                    .clickable(enabled = note.isNotBlank()) {
                        if (timeMillis <= System.currentTimeMillis()) {
                            error = pickFutureMsg
                        } else {
                            onSave(note.trim(), timeMillis, colorIndex)
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.save), color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Inline hour : minute AM/PM wheels (the reference app's reminder time picker), reporting the
 * chosen time for today as epoch millis. */
@Composable
private fun WheelTimePicker(initialMillis: Long, onChanged: (Long) -> Unit) {
    val initial = Calendar.getInstance().apply { timeInMillis = initialMillis }
    val initialHour = initial.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    var hour12 by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initial.get(Calendar.MINUTE)) }
    var pm by remember { mutableStateOf(initial.get(Calendar.AM_PM) == Calendar.PM) }
    // Days from today (0 = today) - the reference's date wheel covers the next month.
    val todayStart = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val initialDay = Calendar.getInstance().apply { timeInMillis = initialMillis; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val initialOffset = Math.round((initialDay.timeInMillis - todayStart.timeInMillis) / 86_400_000.0).toInt().coerceIn(0, DAY_OPTIONS - 1)
    var dayOffset by remember { mutableIntStateOf(initialOffset) }
    val dayLabels = remember {
        val format = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        (0 until DAY_OPTIONS).map { offset ->
            format.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }.time)
        }
    }

    fun emit(offset: Int, h12: Int, min: Int, isPm: Boolean) {
        val c = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, offset)
            set(Calendar.HOUR_OF_DAY, (h12 % 12) + if (isPm) 12 else 0)
            set(Calendar.MINUTE, min)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        onChanged(c.timeInMillis)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        WheelColumn(items = dayLabels, selected = dayOffset, width = 128.dp) {
            dayOffset = it
            emit(dayOffset, hour12, minute, pm)
        }
        Spacer(Modifier.size(6.dp))
        WheelColumn(
            items = (1..12).map { "%02d".format(it) },
            selected = hour12 - 1,
            width = 56.dp
        ) {
            hour12 = it + 1
            emit(dayOffset, hour12, minute, pm)
        }
        Text(":", color = acOnSurface(), fontSize = 24.sp, modifier = Modifier.padding(horizontal = 4.dp))
        WheelColumn(
            items = (0..59).map { "%02d".format(it) },
            selected = minute,
            width = 56.dp
        ) {
            minute = it
            emit(dayOffset, hour12, minute, pm)
        }
        Spacer(Modifier.size(6.dp))
        WheelColumn(items = listOf("AM", "PM"), selected = if (pm) 1 else 0, width = 52.dp) {
            pm = it == 1
            emit(dayOffset, hour12, minute, pm)
        }
    }
}

private const val DAY_OPTIONS = 30

/** One scrolling column that snaps its nearest item to the centre and reports that index. */
@Composable
private fun WheelColumn(items: List<String>, selected: Int, width: Dp, onSelected: (Int) -> Unit) {
    val itemHeight = 40.dp
    // One blank row above and below, so the first and last value can also sit in the centre row.
    // Row i of the padded list holds items[i - 1]; the centre row is the middle of the 3-row window.
    val padded = remember(items) { listOf("") + items + listOf("") }
    // With one blank row on top, the selected value is centred when it is the first visible row.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, items.lastIndex))
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val info = listState.layoutInfo
                val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2
                val centredRow = info.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2) - centre) }?.index
                val real = centredRow?.minus(1)
                if (real != null && real in items.indices && real != selected) onSelected(real)
            }
        }
    }

    Box(modifier = Modifier.width(width).height(itemHeight * 3), contentAlignment = Alignment.Center) {
        // Highlight band behind the centred item.
        Box(modifier = Modifier.fillMaxWidth().height(itemHeight).clip(RoundedCornerShape(12.dp)).background(acSurface()))
        LazyColumn(
            state = listState,
            flingBehavior = snapBehavior,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(padded) { row, label ->
                val isSelected = row - 1 == selected
                Box(modifier = Modifier.fillMaxWidth().height(itemHeight), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        color = if (isSelected) acOnSurface() else acSecondary(),
                        fontSize = if (isSelected) 20.sp else 16.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreTab(number: String, name: String, isSaved: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Reactive, like the contact detail screen: flips between Block and Unblock as soon as the
    // block list changes, and the screen stays open - blocking no longer closes the After Call screen.
    var isBlocked by remember { mutableStateOf(false) }
    LaunchedEffect(number) {
        BlockRepository.blockedNumbersFlow(context).collect { entries ->
            isBlocked = entries.any { it.number.filter(Char::isDigit).takeLast(10) == number.filter(Char::isDigit).takeLast(10) }
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isSaved) {
            MoreRow(Icons.Filled.Person, AcViewContact, stringResource(R.string.after_call_view_contact)) {
                // Opens this app's own contact detail (MainActivity -> MainNavigation), not the system app.
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(MainActivity.EXTRA_OPEN_NUMBER, number)
                    }
                )
            }
        } else {
            // Unknown number: there is no contact to view, so offer to save it instead.
            MoreRow(Icons.Filled.Contacts, acAccent(), stringResource(R.string.add_contact_title)) {
                context.startActivity(
                    Intent(ContactsContract.Intents.Insert.ACTION).apply {
                        type = ContactsContract.RawContacts.CONTENT_TYPE
                        putExtra(ContactsContract.Intents.Insert.PHONE, number)
                    }
                )
            }
        }
        MoreRow(Icons.AutoMirrored.Filled.Chat, AcSendMessage, stringResource(R.string.after_call_send_message)) {
            MessageUtils.sendMessage(context, number)
        }
        MoreRow(
            Icons.Filled.Block,
            if (isBlocked) acAccent() else AcRed,
            stringResource(if (isBlocked) R.string.action_unblock else R.string.after_call_block)
        ) {
            scope.launch {
                val ok = if (isBlocked) BlockRepository.unblockByNumber(context, number) else BlockRepository.blockNumber(context, number)
                val message = when {
                    !ok -> context.getString(R.string.set_default_to_block_message)
                    isBlocked -> context.getString(R.string.unblocked_toast, name)
                    else -> context.getString(R.string.blocked_toast, name)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
        MoreRow(ImageVector.vectorResource(R.drawable.ic_whatsapp), AcWhatsApp, stringResource(R.string.after_call_whatsapp)) {
            WhatsAppUtils.openChat(context, number)
        }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(acSurface()).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(16.dp))
        Text(label, color = acOnSurface(), fontSize = 16.sp)
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = acSecondary())
    }
}

private fun formatDuration(seconds: Long): String =
    String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)

private fun formatWhen(millis: Long): String =
    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(millis)

/** Reminder and scheduled-message time, as the reference app shows it: "Today, 10:24 am" /
 * "Tomorrow, ..." for the next two days, otherwise the full date. */
@Composable
private fun reminderTimeLabel(millis: Long): String {
    val today = stringResource(R.string.after_call_today)
    val tomorrow = stringResource(R.string.after_call_tomorrow)
    val target = Calendar.getInstance().apply { timeInMillis = millis }
    val now = Calendar.getInstance()
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(millis)
    return when {
        sameDay(target, now) -> "$today, $time"
        sameDay(target, Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }) -> "$tomorrow, $time"
        else -> formatWhen(millis)
    }
}


// Same colours as the Recents list (RecentsScreen's call-log row).
@Composable private fun callTypeColor(type: CallType): Color = when (type) {
    CallType.INCOMING -> primaryAccentColor()
    CallType.OUTGOING -> Color(0xFF1DA463)
    CallType.MISSED, CallType.REJECTED, CallType.BLOCKED -> AcRed
    CallType.OTHER -> acSecondary()
}
