package com.example.contactapp.ui.features.flash

import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.contactapp.R
import com.example.contactapp.ui.components.CommonHeader
import com.example.contactapp.ui.components.SettingsCard
import com.example.contactapp.ui.components.SettingsDivider
import com.example.contactapp.ui.components.SettingsItem
import com.example.contactapp.ui.components.SettingsSectionHeader

@Composable
fun FlashAlertScreen(
    onBack: () -> Unit,
    viewModel: FlashAlertViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                onClick = { viewModel.toggleEnabled(!uiState.isEnabled) },
                trailing = {
                    Switch(
                        checked = uiState.isEnabled,
                        onCheckedChange = { viewModel.toggleEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
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
