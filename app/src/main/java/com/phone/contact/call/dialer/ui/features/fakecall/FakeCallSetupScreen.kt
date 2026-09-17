package com.phone.contact.call.dialer.ui.features.fakecall

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.ComponentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.phone.contact.call.dialer.R
import com.phone.contact.call.dialer.ads.NativeOrBannerAdView
import com.phone.contact.call.dialer.service.FakeCallReceiver
import com.phone.contact.call.dialer.ui.components.CommonHeader
import com.phone.contact.call.dialer.ui.components.ContactItem
import com.phone.contact.call.dialer.ui.components.CustomSwitch
import com.phone.contact.call.dialer.ui.components.SettingsCard
import com.phone.contact.call.dialer.ui.features.contacts.ContactsViewModel
import com.phone.contact.call.dialer.ui.theme.PrimaryGreen
import com.phone.contact.call.dialer.util.AnalyticsManager
import com.phone.contact.call.dialer.util.CallReliabilityUtils
import com.phone.contact.call.dialer.viewmodel.AppConfigViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FakeCallSetupScreen(
    onBack: () -> Unit,
    viewModel: FakeCallSetupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var selectedDelaySec by remember { mutableIntStateOf(10) }

    // Lets the user pick a real contact instead of typing a fake caller's name/number by hand —
    // shown as this app's own search-then-list picker (ContactPickerDialog below) rather than the
    // system's contact-chooser UI, matching the app's own Contacts screen look.
    var showContactPicker by remember { mutableStateOf(false) }
    // Same picker, reused for the "Quick Trigger Profile" (shake-to-fake-call) fields below —
    // a separate flag since either field's picker can be open independently of the other's.
    var showShakeContactPicker by remember { mutableStateOf(false) }

    var shakeCallerName by remember { mutableStateOf(viewModel.getShakeCallerName()) }
    var shakeCallerNumber by remember { mutableStateOf(viewModel.getShakeCallerNumber()) }
    var shakeEnabled by remember { mutableStateOf(viewModel.isShakeTriggerEnabled()) }

    // Re-checked on resume, not just once — the "Fix" button below sends the user to system
    // Settings and back, and a plain function-call check in the composable body wouldn't
    // otherwise trigger a recomposition, leaving the warning shown even after it's fixed.
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(CallReliabilityUtils.isIgnoringBatteryOptimizations(context))
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isIgnoringBatteryOptimizations = CallReliabilityUtils.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val now = remember { LocalTime.now() }
    var useCustomTime by remember { mutableStateOf(false) }
    var customHour by remember { mutableIntStateOf(now.hour) }
    var customMinute by remember { mutableIntStateOf(now.minute) }
    var showTimePicker by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    val appConfigViewModel: AppConfigViewModel = androidx.lifecycle.viewmodel.compose.viewModel(context as ComponentActivity)
    val adConfig by appConfigViewModel.appResponse.collectAsState()
    val bannerAdUnitId = adConfig?.result?.let { result ->
        if (result.google_ads_on_off == "on" && result.banner_6_on_off == "on") {
            result.banner_6?.takeIf { it.isNotBlank() }
        } else null
    }
    // native_11 is unused elsewhere — tried first here (see NativeOrBannerAdView), falling back to
    // banner_6 above only if it fails to load.
    val nativeAdUnitId = adConfig?.result?.let { result ->
        if (result.google_ads_on_off == "on" && result.native_11_on_off == "on") {
            result.native_11?.takeIf { it.isNotBlank() }
        } else null
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .statusBarsPadding()
        ) {
            CommonHeader(
                title = stringResource(R.string.fake_call_setup),
                onBackClick = onBack
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Caller Info Section
                Text(
                    text = stringResource(R.string.caller_info),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsCard {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.caller_name)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        trailingIcon = { PickContactIconButton(onClick = { showContactPicker = true }) }
                    )

                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = { Text(stringResource(R.string.caller_number)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Timer Section
                Text(
                    text = stringResource(R.string.schedule_timer),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsCard {
                    val options = listOf(
                        stringResource(R.string.sec_10) to 10,
                        stringResource(R.string.sec_30) to 30,
                        stringResource(R.string.min_1) to 60,
                        stringResource(R.string.min_5) to 300
                    )

                    options.forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedDelaySec = value
                                    useCustomTime = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !useCustomTime && selectedDelaySec == value,
                                onClick = {
                                    selectedDelaySec = value
                                    useCustomTime = false
                                }
                            )
                            Text(text = label, modifier = Modifier.padding(start = 12.dp))
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    // Custom time-of-day option — schedules for that clock time today, or tomorrow
                    // if that time has already passed.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                useCustomTime = true
                                showTimePicker = true
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = useCustomTime,
                            onClick = {
                                useCustomTime = true
                                showTimePicker = true
                            }
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(text = stringResource(R.string.custom_time))
                            if (useCustomTime) {
                                Text(
                                    text = formatClockTime(customHour, customMinute),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = {
                            useCustomTime = true
                            showTimePicker = true
                        }) {
                            Icon(Icons.Default.Schedule, contentDescription = stringResource(R.string.pick_time))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Quick Trigger Profile — the "Set it and Forget it" shake-to-fake-call setup.
                // Saved once here; after that the shake listener runs in the background without
                // the app ever needing to be opened again.
                Text(
                    text = stringResource(R.string.quick_trigger_profile),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsCard {
                    Text(
                        text = stringResource(R.string.quick_trigger_profile_desc),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val nameIsInvalid = shakeEnabled && shakeCallerName.isBlank()
                    val numberIsInvalid = shakeEnabled && shakeCallerNumber.isBlank()
                    val fillProfileFirstMessage = stringResource(R.string.fill_shake_profile_first)

                    OutlinedTextField(
                        value = shakeCallerName,
                        onValueChange = { shakeCallerName = it },
                        label = { Text(stringResource(R.string.default_caller_name)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        trailingIcon = { PickContactIconButton(onClick = { showShakeContactPicker = true }) },
                        isError = nameIsInvalid,
                        supportingText = if (nameIsInvalid) {
                            { Text(fillProfileFirstMessage) }
                        } else null,
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = shakeCallerNumber,
                        onValueChange = { shakeCallerNumber = it },
                        label = { Text(stringResource(R.string.default_caller_number)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                        ),
                        isError = numberIsInvalid,
                        supportingText = if (numberIsInvalid) {
                            { Text(fillProfileFirstMessage) }
                        } else null,
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.enable_shake_trigger),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.enable_shake_trigger_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        CustomSwitch(
                            checked = shakeEnabled,
                            onCheckedChange = { checked -> shakeEnabled = checked }
                        )
                    }

                    if (shakeEnabled && !isIgnoringBatteryOptimizations) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.shake_battery_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = {
                                context.startActivity(CallReliabilityUtils.batteryOptimizationIntent(context))
                            }) {
                                Text(stringResource(R.string.fix))
                            }
                        }
                    }

                    val isShakeProfileInvalid = nameIsInvalid || numberIsInvalid
                    // Same "primary light" tint AfterCallCard uses for its bottom bar.
                    val isDarkTheme = isSystemInDarkTheme()
                    val primaryLightBg = if (isDarkTheme) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    }

                    Button(
                        onClick = {
                            viewModel.saveShakeProfile(shakeCallerName, shakeCallerNumber, shakeEnabled)
                            Toast.makeText(context, context.getString(R.string.shake_profile_saved), Toast.LENGTH_SHORT).show()
                        },
                        enabled = !isShakeProfileInvalid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, PrimaryGreen),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryLightBg,
                            contentColor = PrimaryGreen,
                            disabledContainerColor = primaryLightBg,
                            disabledContentColor = PrimaryGreen.copy(alpha = 0.4f)
                        )
                    ) {
                        Text(text = stringResource(R.string.save_quick_trigger_profile))
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Button(
                    onClick = {
                        val delaySec = if (useCustomTime) secondsUntilClockTime(customHour, customMinute) else selectedDelaySec
                        val scheduledMsg = if (useCustomTime) {
                            context.getString(R.string.call_scheduled_at, formatClockTime(customHour, customMinute))
                        } else {
                            context.getString(R.string.call_scheduled, "$selectedDelaySec seconds")
                        }
                        val testCallName = context.getString(R.string.test_call)
                        scheduleFakeCall(context, name.ifBlank { testCallName }, number.ifBlank { "1234567890" }, delaySec, scheduledMsg)
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text(text = stringResource(R.string.schedule_call), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (!WindowInsets.isImeVisible && (nativeAdUnitId != null || bannerAdUnitId != null)) {
                NativeOrBannerAdView(nativeAdUnitId = nativeAdUnitId, bannerAdUnitId = bannerAdUnitId)
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = customHour,
            initialMinute = customMinute,
            is24Hour = false
        )
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.pick_time),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TimePicker(state = timePickerState)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(onClick = {
                            customHour = timePickerState.hour
                            customMinute = timePickerState.minute
                            useCustomTime = true
                            showTimePicker = false
                        }) {
                            Text(stringResource(R.string.select))
                        }
                    }
                }
            }
        }
    }

    if (showContactPicker) {
        ContactPickerDialog(
            onDismiss = { showContactPicker = false },
            onContactSelected = { pickedName, pickedNumber ->
                name = pickedName
                number = pickedNumber
                showContactPicker = false
            }
        )
    }

    if (showShakeContactPicker) {
        ContactPickerDialog(
            onDismiss = { showShakeContactPicker = false },
            onContactSelected = { pickedName, pickedNumber ->
                shakeCallerName = pickedName
                shakeCallerNumber = pickedNumber
                showShakeContactPicker = false
            }
        )
    }
}

/** The round "pick a contact" trailing icon shared by every caller-name field on this screen. */
@Composable
private fun PickContactIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = stringResource(R.string.pick_contact),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** A search bar on top and the app's own contacts list below, styled like the main Contacts
 * screen — used instead of the system's contact-chooser UI so picking a fake caller feels
 * consistent with the rest of the app. */
@Composable
private fun ContactPickerDialog(
    onDismiss: () -> Unit,
    onContactSelected: (name: String, number: String) -> Unit,
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    val allContacts = remember(uiState.groupedContacts) { uiState.groupedContacts.values.flatten() }
    val filteredContacts = remember(allContacts, query) {
        if (query.isBlank()) {
            allContacts
        } else {
            allContacts.filter {
                it.name.contains(query, ignoreCase = true) || it.number.contains(query)
            }
        }
    }

    val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        searchFocusRequester.requestFocus()
        keyboardController?.show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                CommonHeader(
                    title = stringResource(R.string.pick_contact),
                    onBackClick = onDismiss
                )

                // Same search-pill look/behavior as SearchScreen.kt (Recents' own search) — a
                // rounded surface, live-filtering text field, and a clear ("X") icon that only
                // appears once there's something to clear.
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.search_contacts),
                                        style = androidx.compose.ui.text.TextStyle(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 16.sp
                                        )
                                    )
                                }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 16.sp
                                    ),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester)
                                )
                            }
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (query.isNotBlank() && filteredContacts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = R.drawable.no_search_new),
                                contentDescription = null,
                                modifier = Modifier.size(160.dp)
                            )
                            Text(
                                text = stringResource(R.string.no_result_found),
                                modifier = Modifier.offset(y = (-35).dp),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(items = filteredContacts, key = { it.id }) { contact ->
                            ContactItem(
                                contact = contact,
                                onClick = { onContactSelected(contact.name, contact.number) },
                                showCallButton = false
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatClockTime(hour: Int, minute: Int): String {
    return LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("h:mm a"))
}

/** Seconds from now until the next occurrence of [hour]:[minute] — today if still ahead, else tomorrow. */
private fun secondsUntilClockTime(hour: Int, minute: Int): Int {
    val now = Calendar.getInstance()
    val target = (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (target.timeInMillis <= now.timeInMillis) {
        target.add(Calendar.DATE, 1)
    }
    return ((target.timeInMillis - now.timeInMillis) / 1000).toInt()
}

/**
 * A fresh, persisted request code per call. PendingIntent identity is keyed on this (the
 * Intent's component/action/data are identical across every scheduled call), so reusing a fixed
 * code would make each new schedule silently replace whichever one was scheduled before it
 * instead of queuing independently.
 */
private fun nextRequestCode(context: Context): Int {
    val prefs = context.getSharedPreferences("fake_call_scheduler", Context.MODE_PRIVATE)
    val next = prefs.getInt("next_request_code", 0) + 1
    prefs.edit().putInt("next_request_code", next).apply()
    return next
}

private fun scheduleFakeCall(context: Context, name: String, number: String, delaySec: Int, scheduledMsg: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, FakeCallReceiver::class.java).apply {
        putExtra("caller_name", name)
        putExtra("caller_number", number)
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        nextRequestCode(context),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val triggerTime = SystemClock.elapsedRealtime() + (delaySec * 1000L)

    AnalyticsManager.logEventWithAction(
        "fake_call_scheduled",
        "FakeCallSetupScreen",
        "scheduled",
        mapOf("delay_sec" to delaySec)
    )

    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)
        }

        Toast.makeText(context, scheduledMsg, Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.toast_error_scheduling_call), Toast.LENGTH_SHORT).show()
    }
}
