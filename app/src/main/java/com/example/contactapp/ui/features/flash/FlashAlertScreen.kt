package com.example.contactapp.ui.features.flash

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.contactapp.R
import com.example.contactapp.ads.BannerAdView
import com.example.contactapp.ui.components.CommonHeader
import com.example.contactapp.ui.components.CustomSwitch
import com.example.contactapp.ui.components.SettingsCard
import com.example.contactapp.ui.components.SettingsDivider
import com.example.contactapp.ui.components.SettingsItem
import com.example.contactapp.ui.components.SettingsSectionHeader
import com.example.contactapp.viewmodel.AppConfigViewModel

@Composable
fun FlashAlertScreen(
    onBack: () -> Unit,
    viewModel: FlashAlertViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val activity = context as? Activity

    val appConfigViewModel: AppConfigViewModel = androidx.lifecycle.viewmodel.compose.viewModel(context as ComponentActivity)
    val adConfig by appConfigViewModel.appResponse.collectAsState()
    val bannerAdUnitId = adConfig?.result?.let { result ->
        if (result.google_ads_on_off == "on" && result.banner_4_on_off == "on") {
            result.banner_4?.takeIf { it.isNotBlank() }
        } else null
    }

    var showPermissionRationale by remember { mutableStateOf(false) }
    // Distinguishes "denied once" (system will re-ask) from "denied permanently" (Don't ask
    // again — only the app's own Settings page can help); checked only after a fresh denial.
    var isPermanentlyDenied by remember { mutableStateOf(false) }

    // FlashAlertManager.startBlinking() calls CameraManager.setTorchMode(), which throws/silently
    // no-ops without the CAMERA permission — without this request, the toggle would happily flip
    // "on" and the feature would just never actually blink, with no error shown anywhere.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleEnabled(true)
        } else {
            isPermanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            showPermissionRationale = true
        }
    }

    fun enableFlashAlert() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.toggleEnabled(true)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (bannerAdUnitId != null) {
                BannerAdView(adUnitId = bannerAdUnitId)
            }
        }
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding())
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(scrollState)
    ) {
        CommonHeader(
            title = stringResource(R.string.flash_alert),
            onBackClick = onBack
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionHeader(title = stringResource(R.string.general_header))
        SettingsCard {
            SettingsItem(
                title = stringResource(R.string.enable_flash_alert),
                icon = Icons.Outlined.FlashlightOn,
                onClick = { if (uiState.isEnabled) viewModel.toggleEnabled(false) else enableFlashAlert() },
                trailing = {
                    CustomSwitch(
                        checked = uiState.isEnabled,
                        onCheckedChange = { checked -> if (checked) enableFlashAlert() else viewModel.toggleEnabled(false) }
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionHeader(title = stringResource(R.string.blink_speed_header))
        SettingsCard {
            val speedOptions = listOf(
                stringResource(R.string.slow) to 800L,
                stringResource(R.string.medium) to 400L,
                stringResource(R.string.fast) to 150L
            )

            speedOptions.forEachIndexed { index, (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setBlinkSpeed(value) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = uiState.blinkSpeed == value,
                        onClick = { viewModel.setBlinkSpeed(value) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = label,
                        modifier = Modifier.padding(start = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (index < speedOptions.size - 1) {
                    SettingsDivider()
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text(stringResource(R.string.camera_permission_needed_title)) },
            text = {
                Text(
                    stringResource(R.string.camera_permission_needed_desc) +
                        if (isPermanentlyDenied) "\n\n" + stringResource(R.string.camera_permission_denied_settings_hint) else ""
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    if (isPermanentlyDenied) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        )
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }) {
                    Text(if (isPermanentlyDenied) stringResource(R.string.open_settings) else stringResource(R.string.try_again))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}
