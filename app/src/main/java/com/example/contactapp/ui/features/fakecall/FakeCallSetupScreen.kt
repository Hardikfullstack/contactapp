package com.example.contactapp.ui.features.fakecall

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.activity.ComponentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.contactapp.R
import com.example.contactapp.ads.BannerAdView
import com.example.contactapp.service.FakeCallReceiver
import com.example.contactapp.ui.components.CommonHeader
import com.example.contactapp.ui.components.CustomSwitch
import com.example.contactapp.ui.components.SettingsCard
import com.example.contactapp.ui.theme.PrimaryGreen
import com.example.contactapp.util.AnalyticsManager
import com.example.contactapp.util.CallReliabilityUtils
import com.example.contactapp.viewmodel.AppConfigViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeCallSetupScreen(
    onBack: () -> Unit,
    viewModel: FakeCallSetupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var selectedDelaySec by remember { mutableIntStateOf(10) }

    var shakeCallerName by remember { mutableStateOf(viewModel.getShakeCallerName()) }
    var shakeCallerNumber by remember { mutableStateOf(viewModel.getShakeCallerNumber()) }
    var shakeEnabled by remember { mutableStateOf(viewModel.isShakeTriggerEnabled()) }

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

    Scaffold(
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
                Spacer(modifier = Modifier.height(24.dp))

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
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
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

                    OutlinedTextField(
                        value = shakeCallerName,
                        onValueChange = { shakeCallerName = it },
                        label = { Text(stringResource(R.string.default_caller_name)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
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
                            onCheckedChange = { checked ->
                                if (checked && (shakeCallerName.isBlank() || shakeCallerNumber.isBlank())) {
                                    Toast.makeText(context, context.getString(R.string.fill_shake_profile_first), Toast.LENGTH_SHORT).show()
                                    return@CustomSwitch
                                }
                                shakeEnabled = checked
                            }
                        )
                    }

                    if (shakeEnabled && !CallReliabilityUtils.isIgnoringBatteryOptimizations(context)) {
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

                    Button(
                        onClick = {
                            if (shakeEnabled && (shakeCallerName.isBlank() || shakeCallerNumber.isBlank())) {
                                Toast.makeText(context, context.getString(R.string.fill_shake_profile_first), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.saveShakeProfile(shakeCallerName, shakeCallerNumber, shakeEnabled)
                            Toast.makeText(context, context.getString(R.string.shake_profile_saved), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text(text = stringResource(R.string.save_quick_trigger_profile))
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            if (bannerAdUnitId != null) {
                BannerAdView(adUnitId = bannerAdUnitId)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
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
