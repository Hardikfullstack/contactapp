package com.example.contactapp.ui.features.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
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

@Composable
fun SettingsScreen(
    onBlockedNumbersClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onRecycleBinClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showSortDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    val systemSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshState() }

    fun runSync() {
        viewModel.syncContacts { synced ->
            val message = if (synced) {
                "Contacts synced successfully"
            } else {
                "No account to sync with — contacts are stored on this device only"
            }
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
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
        SettingsSectionHeader(title = "Preferences")
        SettingsCard {
            SettingsItem(
                title = "App Language",
                icon = Icons.Outlined.Language,
                value = uiState.currentLanguage,
                onClick = onLanguageClick
            )
            SettingsDivider()
            SettingsItem(
                title = "Sync Contact",
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
                title = "Sort Contact",
                icon = Icons.Outlined.SortByAlpha,
                value = uiState.contactSortOrder,
                onClick = { showSortDialog = true }
            )
            SettingsDivider()
            SettingsItem(
                title = "Dark Mode",
                icon = if (uiState.appTheme == "Dark") Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                onClick = { showThemeDialog = true },
                value = uiState.appTheme,
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
                title = "Sound & Vibration",
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                onClick = { viewModel.openSoundSettings() }
            )
        }

        // Call Reliability Section — helps on OEMs (MIUI, ColorOS, FuntouchOS, EMUI, etc.)
        // that kill background apps and can silently stop incoming calls from reaching
        // this app even after it's set as the default dialer.
        SettingsSectionHeader(title = "Call Reliability")
        SettingsCard {
            SettingsItem(
                title = "Battery Optimization",
                icon = Icons.Outlined.BatteryChargingFull,
                value = if (uiState.isBatteryOptimizationIgnored) "Allowed" else "Restricted – tap to fix",
                showChevron = false,
                onClick = {
                    if (!uiState.isBatteryOptimizationIgnored) {
                        try {
                            systemSettingsLauncher.launch(viewModel.getBatteryOptimizationIntent())
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Couldn't open battery settings", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
            if (uiState.hasAutoStartSettings) {
                SettingsDivider()
                SettingsItem(
                    title = "Autostart Permission",
                    icon = Icons.Outlined.PlayCircleOutline,
                    value = "Required on this device for incoming calls",
                    onClick = {
                        viewModel.getAutoStartIntent()?.let {
                            try {
                                systemSettingsLauncher.launch(it)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Couldn't open autostart settings", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }

        // Privacy & Data Section
        SettingsSectionHeader(title = "Privacy & Data")
        SettingsCard {
            SettingsItem(
                title = "Caller ID & Spam",
                icon = Icons.Outlined.Report,
                onClick = { 
                    android.widget.Toast.makeText(context, "Caller ID protection is active", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
            SettingsDivider()
            SettingsItem(
                title = "Blocking",
                icon = Icons.Outlined.Block,
                onClick = onBlockedNumbersClick
            )
            SettingsDivider()
            SettingsItem(
                title = "Recycle Bin",
                icon = Icons.Outlined.Delete,
                onClick = onRecycleBinClick
            )
        }

        // Other Section
        SettingsSectionHeader(title = "Other")
        SettingsCard {
            SettingsItem(
                title = "Rate Us",
                icon = Icons.Outlined.ThumbUp,
                onClick = { 
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=${context.packageName}"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // fallback to browser
                    }
                }
            )
            SettingsDivider()
            SettingsItem(
                title = "Share App",
                icon = Icons.Outlined.Share,
                onClick = { 
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Check out this Contact App!")
                    }
                    context.startActivity(Intent.createChooser(intent, "Share via"))
                }
            )
            SettingsDivider()
            SettingsItem(
                title = "Feedback",
                icon = Icons.AutoMirrored.Outlined.Chat,
                onClick = { 
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:feedback@example.com")
                        putExtra(Intent.EXTRA_SUBJECT, "App Feedback")
                    }
                    try { context.startActivity(intent) } catch (e: Exception) {}
                }
            )
            SettingsDivider()
            SettingsItem(
                title = "About Us",
                icon = Icons.Outlined.Info,
                onClick = { 
                    android.widget.Toast.makeText(context, "Connect App v${uiState.appVersion}", android.widget.Toast.LENGTH_LONG).show()
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
                            Text(text = option, modifier = Modifier.padding(start = 8.dp))
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
                            Text(text = option, modifier = Modifier.padding(start = 8.dp))
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
}
