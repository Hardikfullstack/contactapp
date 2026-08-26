package com.example.contactapp.ui.features.aftercall

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.contactapp.R
import com.example.contactapp.ads.NativeAdTemplate
import com.example.contactapp.ads.NativeAdView
import com.example.contactapp.data.local.entity.ReminderEntity
import com.example.contactapp.ui.components.AfterCallCard
import com.example.contactapp.ui.components.AfterCallTabBar
import com.example.contactapp.ui.components.InlineDateTimePicker
import com.example.contactapp.util.CallUtils
import com.example.contactapp.util.MessageUtils
import com.example.contactapp.viewmodel.AppConfigViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun AfterCallScreen(
    number: String,
    displayName: String?,
    isKnownContact: Boolean,
    callInfoLine1: String,
    callInfoLine2: String,
    onFinish: () -> Unit,
    onOpenMainApp: (tab: String) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val appConfigViewModel: AppConfigViewModel = viewModel()
    val adConfig by appConfigViewModel.appResponse.collectAsState()
    val nativeAdUnitId = adConfig?.result?.let { result ->
        if (result.google_ads_on_off == "on" && result.native_7_on_off == "on") {
            result.native_7?.takeIf { it.isNotBlank() }
        } else null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                AfterCallCard(
                    displayName = displayName,
                    isKnownContact = isKnownContact,
                    callInfoLine1 = callInfoLine1,
                    callInfoLine2 = callInfoLine2,
                    onMessageClick = { MessageUtils.sendMessage(context, number) },
                    onCallClick = { CallUtils.makeCall(context, number) }
                )
                AfterCallTabBar(
                    selectedTab = pagerState.currentPage,
                    onTabSelected = { tab ->
                        coroutineScope.launch { pagerState.animateScrollToPage(tab) }
                    }
                )
            }
        },
        bottomBar = {
            if (nativeAdUnitId != null) {
                NativeAdView(
                    adUnitId = nativeAdUnitId,
                    template = NativeAdTemplate.MEDIUM,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { tab ->
            when (tab) {
                0 -> AfterCallHistoryTab(
                    number = number,
                    onQuickMessageClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    onOpenMainApp = onOpenMainApp
                )
                1 -> AfterCallQuickMessageTab(number = number, onSent = onFinish)
                2 -> AfterCallReminderTab(number = number, displayName = displayName)
                3 -> AfterCallMoreTab(number = number)
            }
        }
    }
}

@Composable
private fun AfterCallHistoryTab(number: String, onQuickMessageClick: () -> Unit, onOpenMainApp: (String) -> Unit) {
    val strContact = "Contact"
    val strQuickMessage = "Quick Message"
    val strRecentCall = "Recent Call"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Contact Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onOpenMainApp("contacts") },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = strContact, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        // Quick Message Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onQuickMessageClick() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = strQuickMessage, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        // Recent Call Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onOpenMainApp("recents") },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.History, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = strRecentCall, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun AfterCallQuickMessageTab(number: String, onSent: () -> Unit) {
    val context = LocalContext.current
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var personalText by remember { mutableStateOf("") }

    val presets = listOf(
        stringResource(R.string.after_call_quick_reply_1),
        stringResource(R.string.after_call_quick_reply_2),
        stringResource(R.string.after_call_quick_reply_3)
    )
    val strWritePersonal = stringResource(R.string.after_call_write_personal)

    fun send(text: String) {
        MessageUtils.sendMessage(context, number, text)
        onSent()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 10.dp)
    ) {
        presets.forEachIndexed { index, preset ->
            AfterCallQuickReplyPresetRow(
                text = preset,
                isSelected = selectedIndex == index,
                onSelect = { selectedIndex = index },
                onSend = { send(preset) }
            )
        }
        AfterCallWritePersonalRow(
            isSelected = selectedIndex == 3,
            text = personalText,
            placeholder = strWritePersonal,
            onTextChange = { personalText = it },
            onSelect = { selectedIndex = 3 },
            onSend = { send(personalText) }
        )
    }
}

@Composable
private fun AfterCallSendButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = stringResource(R.string.message),
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun AfterCallQuickReplyPresetRow(text: String, isSelected: Boolean, onSelect: () -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 15.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Spacer(modifier = Modifier.width(8.dp))
            AfterCallSendButton(onClick = onSend)
        }
    }
}

@Composable
private fun AfterCallWritePersonalRow(
    isSelected: Boolean,
    text: String,
    placeholder: String,
    onTextChange: (String) -> Unit,
    onSelect: () -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 12.dp, start = 24.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(22.dp)
                .clickable(onClick = onSelect)
        )
        Spacer(modifier = Modifier.width(12.dp))
        if (isSelected) {
            Box(modifier = Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(text = placeholder, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Text(
                text = placeholder,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onSelect)
            )
        }
        if (isSelected && text.isNotBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            AfterCallSendButton(onClick = onSend)
        }
    }
}

private val ReminderColorPalette = listOf(
    Color(0xFF4C6FFF), Color(0xFF5B6ABF), Color(0xFFE0507A), Color(0xFFA855C9),
    Color(0xFFE0654F), Color(0xFF8E5BC9), Color(0xFF4FA8E0)
)

@Composable
private fun AfterCallReminderTab(number: String, displayName: String?) {
    val viewModel: AfterCallViewModel = hiltViewModel()

    var showForm by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<ReminderEntity?>(null) }

    val remindersList by remember(number) { viewModel.remindersFor(number) }.collectAsState(initial = emptyList())
    val reminders = remember(remindersList) {
        remindersList.filter { it.reminderTimeMillis > System.currentTimeMillis() }
    }

    val strNoReminder = stringResource(R.string.after_call_no_reminder)
    val strAddReminder = stringResource(R.string.after_call_add_reminder)

    if (showForm) {
        AfterCallReminderForm(
            initial = editingReminder,
            onCancel = {
                showForm = false
                editingReminder = null
            },
            onSave = { note, targetMillis, colorIndex ->
                viewModel.saveReminder(editingReminder, number, displayName, note, targetMillis, colorIndex)
                showForm = false
                editingReminder = null
            }
        )
        return
    }

    if (reminders.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = strNoReminder, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(93.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        editingReminder = null
                        showForm = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = strAddReminder, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(reminders, key = { it.id }) { reminder ->
                    AfterCallReminderRow(
                        reminder = reminder,
                        onEdit = {
                            editingReminder = reminder
                            showForm = true
                        },
                        onDelete = { viewModel.deleteReminder(reminder) }
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        editingReminder = null
                        showForm = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = strAddReminder, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun AfterCallReminderRow(reminder: ReminderEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    val strToday = stringResource(R.string.today)
    val strTomorrow = stringResource(R.string.tomorrow)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(ReminderColorPalette[reminder.colorIndex % ReminderColorPalette.size])
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reminder.note,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = reminder.formattedTime, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = reminder.formattedDayLabel(strToday, strTomorrow), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(imageVector = Icons.Default.Edit, contentDescription = stringResource(R.string.save), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AfterCallReminderForm(
    initial: ReminderEntity?,
    onCancel: () -> Unit,
    onSave: (note: String, targetMillis: Long, colorIndex: Int) -> Unit
) {
    val strRemindMeAbout = stringResource(R.string.after_call_remind_me_about)
    val strSave = stringResource(R.string.save)
    val strCancel = stringResource(R.string.cancel)
    val strToday = stringResource(R.string.today)
    val strTomorrow = stringResource(R.string.tomorrow)

    var note by remember { mutableStateOf(initial?.note ?: "") }
    var colorIndex by remember { mutableStateOf(initial?.colorIndex ?: 0) }

    val dayLabels = remember {
        val labels = mutableListOf(strToday, strTomorrow)
        val fmt = java.time.format.DateTimeFormatter.ofPattern("MMM dd", Locale.getDefault())
        var date = java.time.LocalDate.now().plusDays(2)
        repeat(28) {
            labels.add(date.format(fmt))
            date = date.plusDays(1)
        }
        labels
    }

    val initialCal = remember {
        java.util.Calendar.getInstance().apply {
            if (initial != null) timeInMillis = initial.reminderTimeMillis
        }
    }
    var selectedDayIndex by remember {
        mutableStateOf(
            if (initial != null) {
                val days = java.time.temporal.ChronoUnit.DAYS.between(
                    java.time.LocalDate.now(),
                    java.time.Instant.ofEpochMilli(initial.reminderTimeMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                ).toInt()
                days.coerceIn(0, dayLabels.size - 1)
            } else 0
        )
    }
    var selectedHour by remember { mutableStateOf(initialCal.get(java.util.Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableStateOf(initialCal.get(java.util.Calendar.MINUTE)) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (note.isEmpty()) {
                    Text(text = strRemindMeAbout, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        InlineDateTimePicker(
            dayLabels = dayLabels,
            selectedDayIndex = selectedDayIndex,
            onDaySelected = { selectedDayIndex = it },
            selectedHour = selectedHour,
            onHourSelected = { selectedHour = it },
            selectedMinute = selectedMinute,
            onMinuteSelected = { selectedMinute = it }
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReminderColorPalette.forEachIndexed { index, color ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(if (index == colorIndex) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .clickable { colorIndex = index }
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(93.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center
            ) {
                Text(text = strCancel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
            val targetMillis = remember(selectedDayIndex, selectedHour, selectedMinute) {
                val cal = java.util.Calendar.getInstance()
                if (selectedDayIndex > 0) cal.add(java.util.Calendar.DAY_OF_YEAR, selectedDayIndex)
                cal.set(java.util.Calendar.HOUR_OF_DAY, selectedHour)
                cal.set(java.util.Calendar.MINUTE, selectedMinute)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            // Only relevant when Today is selected — Tomorrow/later dates can't land in the past.
            val nowFloorToMinute = remember(selectedDayIndex, selectedHour, selectedMinute) {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            val isPastTime = targetMillis < nowFloorToMinute
            val canSave = note.isNotBlank() && !isPastTime
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(93.dp))
                    .background(if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    .clickable(enabled = canSave) { onSave(note.trim(), targetMillis, colorIndex) },
                contentAlignment = Alignment.Center
            ) {
                Text(text = strSave, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

@Composable
private fun AfterCallMoreTab(number: String) {
    val context = LocalContext.current
    val strMessage = stringResource(R.string.after_call_action_message)
    val strSendMail = stringResource(R.string.after_call_action_send_mail)
    val strCalendar = stringResource(R.string.after_call_action_calendar)
    val strWeb = stringResource(R.string.after_call_action_web)

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 10.dp)) {
        AfterCallOptionRow(
            icon = Icons.AutoMirrored.Filled.Chat,
            text = strMessage,
            onClick = { MessageUtils.sendMessage(context, number) }
        )
        AfterCallOptionRow(
            icon = Icons.Default.Email,
            text = strSendMail,
            onClick = {
                val email = lookupContactEmail(context, number)
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    if (email != null) putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                }
                try {
                    AfterCallActivity.suppressNextLeaveFinish = true
                    context.startActivity(Intent.createChooser(intent, strSendMail))
                } catch (e: Exception) { e.printStackTrace() }
            }
        )
        AfterCallOptionRow(
            icon = Icons.Default.CalendarMonth,
            text = strCalendar,
            onClick = {
                val intent = Intent(Intent.ACTION_INSERT).apply { data = CalendarContract.Events.CONTENT_URI }
                try {
                    AfterCallActivity.suppressNextLeaveFinish = true
                    context.startActivity(Intent.createChooser(intent, strCalendar))
                } catch (e: Exception) { e.printStackTrace() }
            }
        )
        AfterCallOptionRow(
            icon = Icons.Default.Language,
            text = strWeb,
            onClick = {
                try {
                    AfterCallActivity.suppressNextLeaveFinish = true
                    context.startActivity(Intent(Intent.ACTION_WEB_SEARCH).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                } catch (e: Exception) {
                    try {
                        AfterCallActivity.suppressNextLeaveFinish = true
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        )
    }
}

@Composable
private fun AfterCallOptionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(20.dp))
        Text(text = text, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Resolves the email address of the contact matching this phone number, if any (via PhoneLookup -> Email join). */
private fun lookupContactEmail(context: Context, number: String): String? {
    return try {
        val phoneUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        var contactId: String? = null
        context.contentResolver.query(phoneUri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) contactId = cursor.getString(0)
        }
        val id = contactId ?: return null
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(id),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }
}
