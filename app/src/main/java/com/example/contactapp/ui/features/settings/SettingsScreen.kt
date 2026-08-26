package com.example.contactapp.ui.features.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.contactapp.R
import com.example.contactapp.ui.components.CommonHeader
import com.example.contactapp.ui.components.SettingsCard
import com.example.contactapp.ui.components.SettingsDivider
import com.example.contactapp.ui.components.SettingsItem
import com.example.contactapp.ui.components.SettingsSectionHeader
import com.example.contactapp.ui.components.dialogs.RateUsDialog
import com.example.contactapp.util.AfterCallState
import com.example.contactapp.util.RateUsHelper

@Composable
fun SettingsScreen(
    onBlockedNumbersClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onRecycleBinClick: () -> Unit,
    onAfterCallClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showSortDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showRateUsDialog by remember { mutableStateOf(false) }
    val afterCallEnabled by AfterCallState.enabled

    val systemSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshState() }

    // The autostart/MIUI-popup screens are launched as plain startActivity() calls (no
    // meaningful ActivityResult callback — see CallReliabilityUtils), so catch the return trip
    // via ON_RESUME instead, same as systemSettingsLauncher's callback does for the ones that do
    // support a result callback.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val contactsSyncedMessage = stringResource(R.string.toast_contacts_synced)
    val noSyncAccountMessage = stringResource(R.string.toast_no_sync_account)
    fun runSync() {
        viewModel.syncContacts { synced ->
            val message = if (synced) contactsSyncedMessage else noSyncAccountMessage
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val couldntOpenBatterySettingsMessage = stringResource(R.string.toast_couldnt_open_battery_settings)
    val couldntOpenNotificationSettingsMessage = stringResource(R.string.toast_couldnt_open_notification_settings)
    val callerIdProtectionActiveMessage = stringResource(R.string.toast_caller_id_protection_active)
    val shareAppMessage = stringResource(R.string.share_app_message)
    val shareViaLabel = stringResource(R.string.share_via)
    val feedbackEmailSubject = stringResource(R.string.email_subject_app_feedback)
    val appVersionMessage = stringResource(R.string.toast_app_version_template, uiState.appVersion)

    val firstNameLabel = stringResource(R.string.sort_first_name)
    val lastNameLabel = stringResource(R.string.sort_last_name)
    val lightLabel = stringResource(R.string.theme_light)
    val darkLabel = stringResource(R.string.theme_dark)
    val systemLabel = stringResource(R.string.theme_system)
    fun sortOrderLabel(order: String): String = if (order == "Last Name") lastNameLabel else firstNameLabel
    fun themeLabel(theme: String): String = when (theme) {
        "Dark" -> darkLabel
        "Light" -> lightLabel
        else -> systemLabel
    }

    val accountsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { runSync() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scrollState)
    ) {
        CommonHeader(
            title = stringResource(R.string.settings)
        )

        // Preferences Section
        SettingsSectionHeader(title = stringResource(R.string.settings_section_preferences))
        SettingsCard {
            SettingsItem(
                title = stringResource(R.string.settings_app_language),
                icon = Icons.Outlined.Language,
                value = uiState.currentLanguage,
                onClick = onLanguageClick
            )
            SettingsDivider()
            SettingsItem(
                title = stringResource(R.string.settings_sync_contact),
                icon = Icons.Outlined.Sync,
                showChevron = false,
                trailing = {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                onClick = {
                    if (!uiState.isSyncing) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED) {
                            runSync()
                        } else {
                            accountsPermissionLauncher.launch(Manifest.permission.GET_ACCOUNTS)
                        }
                    }
                }
            )
            SettingsDivider()
            SettingsItem(
                title = stringResource(R.string.settings_sort_contact),
                icon = Icons.Outlined.SortByAlpha,
                value = sortOrderLabel(uiState.contactSortOrder),
                onClick = { showSortDialog = true }
            )
            SettingsDivider()
            SettingsItem(
                title = stringResource(R.string.settings_dark_mode),
                icon = if (uiState.appTheme == "Dark") Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                onClick = { showThemeDialog = true },
                value = themeLabel(uiState.appTheme),
                trailing = {
                    Switch(
                        checked = uiState.appTheme == "Dark",
                        onCheckedChange = { viewModel.setTheme(if (it) "Dark" else "Light") },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            )
            SettingsDivider()
            SettingsItem(
                title = stringResource(R.string.settings_sound_vibration),
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                onClick = { viewModel.openSoundSettings() }
            )
        }

        // Call Reliability Section — helps on OEMs (MIUI, ColorOS, FuntouchOS, EMUI, etc.)
        // that kill background apps and can silently stop incoming calls from reaching
        // this app even after it's set as the default dialer.
        SettingsSectionHeader(title = stringResource(R.string.settings_section_call_reliability))
        SettingsCard {
            val allowedLabel = stringResource(R.string.state_allowed)
            SettingsItem(
                title = stringResource(R.string.settings_battery_optimization),
                icon = Icons.Outlined.BatteryChargingFull,
                value = if (uiState.isBatteryOptimizationIgnored) allowedLabel else stringResource(R.string.state_restricted_tap_to_fix),
                showChevron = false,
                onClick = {
                    if (!uiState.isBatteryOptimizationIgnored) {
                        try {
                            systemSettingsLauncher.launch(viewModel.getBatteryOptimizationIntent())
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, couldntOpenBatterySettingsMessage, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
            SettingsDivider()
            SettingsItem(
                title = stringResource(R.string.settings_full_screen_notifications),
                icon = Icons.Outlined.NotificationsActive,
                value = if (uiState.hasFullScreenIntentPermission) allowedLabel else stringResource(R.string.state_required_tap_to_fix),
                showChevron = false,
                onClick = {
                    if (!uiState.hasFullScreenIntentPermission) {
                        try {
                            systemSettingsLauncher.launch(viewModel.fullScreenIntentIntent())
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, couldntOpenNotificationSettingsMessage, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
            if (uiState.hasAutoStartSettings) {
                SettingsDivider()
                SettingsItem(
                    title = stringResource(R.string.settings_autostart_permission),
                    icon = Icons.Outlined.PlayCircleOutline,
                    value = if (uiState.isMiuiAutostartGranted) allowedLabel else stringResource(R.string.autostart_required_desc),
                    onClick = { viewModel.launchAutoStartSettings(context) }
                )
            }
            if (uiState.showBackgroundPopupSettings) {
                SettingsDivider()
                SettingsItem(
                    title = stringResource(R.string.settings_background_popup_permission),
                    icon = Icons.Outlined.PictureInPicture,
                    value = if (uiState.isMiuiBackgroundPopupGranted) allowedLabel else stringResource(R.string.background_popup_required_desc),
                    onClick = { viewModel.openMiuiBackgroundPopupSettings(context) }
                )
            }
            SettingsDivider()
            SettingsItem(
                title = stringResource(R.string.settings_after_call_screen_title),
                icon = Icons.Outlined.NotificationsActive,
                value = if (afterCallEnabled) stringResource(R.string.state_on) else stringResource(R.string.state_off),
                onClick = onAfterCallClick
            )
        }

        // Privacy & Data Section
        SettingsSectionHeader(title = stringResource(R.string.settings_section_privacy_data))
        SettingsCard {
            SettingsItem(
                title = stringResource(R.string.settings_caller_id_spam),
                icon = Icons.Outlined.Report,
                onClick = {
                    android.widget.Toast.makeText(context, callerIdProtectionActiveMessage, android.widget.Toast.LENGTH_SHORT).show()
                }
            )
            SettingsDivider()
            SettingsItem(
                title = stringResource(R.string.settings_blocking),
                icon = Icons.Outlined.Block,
                onClick = onBlockedNumbersClick
            )
            SettingsDivider()
            SettingsItem(
                title = stringResource(R.string.recycle_bin_title),
                icon = Icons.Outlined.Delete,
                onClick = onRecycleBinClick
            )
        }

        // Other Section
        SettingsSectionHeader(title = stringResource(R.string.settings_section_other))
        SettingsCard {
            SettingsItem(
                title = stringResource(R.string.settings_rate_us),
                icon = Icons.Outlined.ThumbUp,
                onClick = { showRateUsDialog = true }
            )
            SettingsDivider()
            SettingsItem(
                title = stringResource(R.string.settings_share_app),
                icon = Icons.Outlined.Share,
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareAppMessage)
                    }
                    context.startActivity(Intent.createChooser(intent, shareViaLabel))
                }
            )
            SettingsDivider()
            SettingsItem(
                title = stringResource(R.string.settings_feedback),
                icon = Icons.AutoMirrored.Outlined.Chat,
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:parth@aavakar.com")
                        putExtra(Intent.EXTRA_SUBJECT, feedbackEmailSubject)
                    }
                    try { context.startActivity(intent) } catch (e: Exception) {}
                }
            )
            SettingsDivider()
            SettingsItem(
                title = stringResource(R.string.settings_about_us),
                icon = Icons.Outlined.Info,
                onClick = {
                    android.widget.Toast.makeText(context, appVersionMessage, android.widget.Toast.LENGTH_LONG).show()
                }
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }

    // Dialogs
    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text(stringResource(R.string.sort_by)) },
            text = {
                Column {
                    listOf("First Name", "Last Name").forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSortOrder(option)
                                    showSortDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.contactSortOrder == option,
                                onClick = {
                                    viewModel.setSortOrder(option)
                                    showSortDialog = false
                                }
                            )
                            Text(text = sortOrderLabel(option), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.theme)) },
            text = {
                Column {
                    listOf("Light", "Dark", "System").forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setTheme(option)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.appTheme == option,
                                onClick = {
                                    viewModel.setTheme(option)
                                    showThemeDialog = false
                                }
                            )
                            Text(text = themeLabel(option), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showRateUsDialog) {
        RateUsDialog(
            onRateClick = { stars ->
                showRateUsDialog = false
                RateUsHelper.handleRating(context, stars)
            },
            onDismiss = { showRateUsDialog = false }
        )
    }

}
