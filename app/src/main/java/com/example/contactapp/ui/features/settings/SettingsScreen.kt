package com.example.contactapp.ui.features.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.contactapp.R
import com.example.contactapp.ui.components.CommonHeader
import com.example.contactapp.ui.components.CustomSwitch
import com.example.contactapp.ui.components.SettingsCard
import com.example.contactapp.ui.theme.LocalIsDarkTheme
import com.example.contactapp.ui.theme.PrimaryGreen
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
    onCallerIdSpamClick: () -> Unit,
    onAboutUsClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showSortDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showRateUsDialog by remember { mutableStateOf(false) }
    var showAfterCallDisableDialog by remember { mutableStateOf(false) }
    val afterCallEnabled by AfterCallState.enabled

    val systemSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshState() }

    val couldntOpenPermissionSettingsMessage = stringResource(R.string.toast_couldnt_open_permission_settings)
    fun enableAfterCall() {
        AfterCallState.setEnabled(context, true)
        if (!Settings.canDrawOverlays(context)) {
            try {
                systemSettingsLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                )
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, couldntOpenPermissionSettingsMessage, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Autostart/MIUI-popup screens have no meaningful ActivityResult callback (see
    // CallReliabilityUtils) — catch the return trip via ON_RESUME instead.
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

    val shareAppMessage = stringResource(R.string.share_app_message)
    val shareViaLabel = stringResource(R.string.share_via)
    val feedbackEmailSubject = stringResource(R.string.email_subject_app_feedback)

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
                    CustomSwitch(
                        checked = uiState.appTheme == "Dark",
                        onCheckedChange = { viewModel.setTheme(if (it) "Dark" else "Light") }
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
            SettingsItem(
                title = stringResource(R.string.settings_after_call_screen_title),
                icon = Icons.Outlined.Call,
                showChevron = false,
                onClick = {
                    if (afterCallEnabled) showAfterCallDisableDialog = true else enableAfterCall()
                },
                trailing = {
                    CustomSwitch(
                        checked = afterCallEnabled,
                        onCheckedChange = { checked ->
                            if (!checked) showAfterCallDisableDialog = true else enableAfterCall()
                        }
                    )
                }
            )
        }

        // Privacy & Data Section
        SettingsSectionHeader(title = stringResource(R.string.settings_section_privacy_data))
        SettingsCard {
            SettingsItem(
                title = stringResource(R.string.settings_caller_id_spam),
                icon = Icons.Outlined.Report,
                onClick = onCallerIdSpamClick
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
                    // Always built from the app's own package name — never a remote-configured
                    // link — so a share can never go out pointing at nothing (or the wrong app).
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            String.format(shareAppMessage, "https://play.google.com/store/apps/details?id=${context.packageName}")
                        )
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
                onClick = onAboutUsClick
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }

    // Dialogs
    if (showSortDialog) {
        SelectionDialog(
            title = stringResource(R.string.sort_by),
            options = listOf("First Name", "Last Name"),
            optionLabel = { sortOrderLabel(it) },
            selectedOption = uiState.contactSortOrder,
            onOptionSelected = { option ->
                viewModel.setSortOrder(option)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false }
        )
    }

    if (showThemeDialog) {
        SelectionDialog(
            title = stringResource(R.string.theme),
            options = listOf("Light", "Dark", "System"),
            optionLabel = { themeLabel(it) },
            selectedOption = uiState.appTheme,
            onOptionSelected = { option ->
                viewModel.setTheme(option)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
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

    if (showAfterCallDisableDialog) {
        AlertDialog(
            onDismissRequest = { showAfterCallDisableDialog = false },
            containerColor = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.surface else Color(0xFFF3F3F3),
            title = { Text(stringResource(R.string.after_call_disable_dialog_title)) },
            text = { Text(stringResource(R.string.after_call_disable_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    AfterCallState.setEnabled(context, false)
                    showAfterCallDisableDialog = false
                }) {
                    Text(stringResource(R.string.turn_off), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAfterCallDisableDialog = false }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

}

/** Matches the Messages app's FilterDialog styling: a plain rounded card (not AlertDialog) with
 * a title + close icon row, and whole-row-clickable options with a trailing radio button. */
@Composable
private fun SelectionDialog(
    title: String,
    options: List<String>,
    optionLabel: (String) -> String,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(23.dp),
            color = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.surface else Color(0xFFF3F3F3),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 300.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Medium,
                        fontSize = 22.sp,
                        color = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.onSurface else Color(0xFF020202)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                options.forEach { option ->
                    val selected = selectedOption == option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOptionSelected(option) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = optionLabel(option),
                            fontSize = 16.sp,
                            color = if (selected) {
                                if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.onSurface else Color(0xFF020202)
                            } else {
                                if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF656565)
                            }
                        )
                        RadioButton(
                            selected = selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = PrimaryGreen,
                                unselectedColor = Color.LightGray
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
