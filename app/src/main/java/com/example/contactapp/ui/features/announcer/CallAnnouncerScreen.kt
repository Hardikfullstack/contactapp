package com.example.contactapp.ui.features.announcer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
fun CallAnnouncerScreen(
    onBack: () -> Unit,
    viewModel: CallAnnouncerViewModel = hiltViewModel()
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
            title = stringResource(R.string.call_announcer),
            onBackClick = onBack
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionHeader(title = stringResource(R.string.general_header))
        SettingsCard {
            SettingsItem(
                title = stringResource(R.string.enable_announcer),
                icon = Icons.Outlined.RecordVoiceOver,
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

        SettingsSectionHeader(title = stringResource(R.string.announcement_settings_header))
        SettingsCard {
            val repeatOptions = listOf(
                stringResource(R.string.times_1) to 1,
                stringResource(R.string.times_2) to 2,
                stringResource(R.string.times_3) to 3,
                stringResource(R.string.continuous) to 0
            )

            repeatOptions.forEachIndexed { index, (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setRepeatCount(value) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = uiState.repeatCount == value,
                        onClick = { viewModel.setRepeatCount(value) },
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
                if (index < repeatOptions.size - 1) {
                    SettingsDivider()
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
