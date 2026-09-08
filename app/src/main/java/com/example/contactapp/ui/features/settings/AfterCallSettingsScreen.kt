package com.example.contactapp.ui.features.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.contactapp.ui.theme.LocalIsDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.contactapp.R
import com.example.contactapp.ads.BannerAdView
import com.example.contactapp.ui.components.CustomSwitch
import com.example.contactapp.ui.components.SettingsCard
import com.example.contactapp.ui.components.SettingsDivider
import com.example.contactapp.ui.components.SettingsItem
import com.example.contactapp.ui.components.SettingsSectionHeader
import com.example.contactapp.util.AfterCallState
import com.example.contactapp.viewmodel.AppConfigViewModel

/**
 * Dedicated screen for the After Call feature — the main on/off switch plus every OEM permission
 * it depends on to actually fire in the background (overlay/autostart/MIUI popup), gathered in
 * one place instead of scattered across the main Settings list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AfterCallSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val afterCallEnabled by AfterCallState.enabled
    var showDisableDialog by remember { mutableStateOf(false) }
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    val systemSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshState()
        canDrawOverlays = Settings.canDrawOverlays(context)
    }

    fun enable() {
        AfterCallState.setEnabled(context, true)
        if (!canDrawOverlays) {
            try {
                systemSettingsLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                )
            } catch (e: Exception) { /* Not available on this OEM — feature just won't fire. */ }
        }
    }

    // Autostart/MIUI-popup screens have no meaningful ActivityResult callback (see
    // CallReliabilityUtils) — catch the return trip via ON_RESUME instead.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshState()
                canDrawOverlays = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("After Call Screen", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 2.dp).size(40.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            val appConfigViewModel: AppConfigViewModel = androidx.lifecycle.viewmodel.compose.viewModel(context as ComponentActivity)
            val adConfig by appConfigViewModel.appResponse.collectAsState()
            val bannerAdUnitId = adConfig?.result?.let { result ->
                if (result.google_ads_on_off == "on" && result.banner_7_on_off == "on") {
                    result.banner_7?.takeIf { it.isNotBlank() }
                } else null
            }
            if (bannerAdUnitId != null) {
                BannerAdView(adUnitId = bannerAdUnitId)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp)
        ) {
            SettingsSectionHeader(title = "Enable")
            SettingsCard {
                SettingsItem(
                    title = "Show After Call Screen",
                    icon = Icons.Outlined.NotificationsActive,
                    showChevron = false,
                    onClick = {
                        if (afterCallEnabled) showDisableDialog = true else enable()
                    },
                    trailing = {
                        CustomSwitch(
                            checked = afterCallEnabled,
                            onCheckedChange = { checked ->
                                if (!checked) showDisableDialog = true else enable()
                            }
                        )
                    }
                )
            }
            Text(
                text = "Shows a quick screen with call history, message, and reminder options right after a call ends.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            if (afterCallEnabled) {
                SettingsSectionHeader(title = "Permissions Needed")
                SettingsCard {
                    SettingsItem(
                        title = "Display Over Other Apps",
                        icon = Icons.Outlined.PictureInPicture,
                        value = if (canDrawOverlays) "Allowed" else "Required – tap to fix",
                        showChevron = false,
                        onClick = {
                            if (!canDrawOverlays) {
                                try {
                                    systemSettingsLauncher.launch(
                                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                    )
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Couldn't open permission settings", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = "Full-Screen Notifications",
                        icon = Icons.Outlined.NotificationsActive,
                        value = if (uiState.hasFullScreenIntentPermission) "Allowed" else "Required – tap to fix",
                        showChevron = false,
                        onClick = {
                            if (!uiState.hasFullScreenIntentPermission) {
                                try {
                                    systemSettingsLauncher.launch(viewModel.fullScreenIntentIntent())
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Couldn't open notification settings", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                    if (uiState.hasAutoStartSettings) {
                        SettingsDivider()
                        SettingsItem(
                            title = "Autostart Permission",
                            icon = Icons.Outlined.PlayCircleOutline,
                            value = "Required on this device",
                            onClick = { viewModel.launchAutoStartSettings(context) }
                        )
                    }
                    if (uiState.showBackgroundPopupSettings) {
                        SettingsDivider()
                        SettingsItem(
                            title = "Background Pop-up Permission",
                            icon = Icons.Outlined.PictureInPicture,
                            value = if (uiState.isMiuiBackgroundPopupGranted) "Allowed" else "Required on MIUI",
                            onClick = { viewModel.openMiuiBackgroundPopupSettings(context) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { showDisableDialog = false },
            containerColor = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.surface else Color(0xFFF3F3F3),
            title = { Text("Turn off After Call screen?") },
            text = { Text("You won't see quick actions after your calls end. You can turn this back on anytime.") },
            confirmButton = {
                TextButton(onClick = {
                    AfterCallState.setEnabled(context, false)
                    showDisableDialog = false
                }) {
                    Text("Turn Off", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableDialog = false }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}
