package com.example.contactapp.ui.features.autoreply

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.contactapp.R
import com.example.contactapp.ui.components.CommonHeader
import com.example.contactapp.ui.components.CustomSwitch
import com.example.contactapp.ui.components.SettingsCard
import com.example.contactapp.ui.components.SettingsItem
import com.example.contactapp.ui.components.SettingsSectionHeader
import com.example.contactapp.ui.theme.PrimaryGreen

@Composable
fun AutoReplyScreen(
    onBack: () -> Unit,
    viewModel: AutoReplyViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasSmsPermission = granted
        // Only actually turn the feature on once permission is confirmed — otherwise a
        // silently-denied toggle would look "on" while doing nothing.
        viewModel.setEnabled(granted)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(scrollState)
    ) {
        CommonHeader(
            title = stringResource(R.string.tool_auto_reply),
            onBackClick = onBack
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionHeader(title = stringResource(R.string.general_header))
        SettingsCard {
            SettingsItem(
                title = stringResource(R.string.enable_auto_reply),
                icon = Icons.AutoMirrored.Outlined.Chat,
                onClick = {
                    val next = !uiState.isEnabled
                    if (next && !hasSmsPermission) {
                        permissionLauncher.launch(Manifest.permission.SEND_SMS)
                    } else {
                        viewModel.setEnabled(next)
                    }
                },
                trailing = {
                    CustomSwitch(
                        checked = uiState.isEnabled,
                        onCheckedChange = { checked ->
                            if (checked && !hasSmsPermission) {
                                permissionLauncher.launch(Manifest.permission.SEND_SMS)
                            } else {
                                viewModel.setEnabled(checked)
                            }
                        }
                    )
                }
            )
        }

        if (!hasSmsPermission) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.auto_reply_permission_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionHeader(title = stringResource(R.string.auto_reply_message_header))
        SettingsCard {
            OutlinedTextField(
                value = uiState.message,
                onValueChange = { viewModel.setMessage(it) },
                label = { Text(stringResource(R.string.auto_reply_message_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                minLines = 3,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryGreen)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
