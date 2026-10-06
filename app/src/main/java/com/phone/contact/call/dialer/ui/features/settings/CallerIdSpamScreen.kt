package com.phone.contact.call.dialer.ui.features.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.phone.contact.call.dialer.R
import com.phone.contact.call.dialer.ads.NativeOrBannerAdView
import com.phone.contact.call.dialer.ui.components.CommonHeader
import com.phone.contact.call.dialer.ui.components.CustomSwitch
import com.phone.contact.call.dialer.ui.components.SettingsCard
import com.phone.contact.call.dialer.ui.components.SettingsItem
import com.phone.contact.call.dialer.ui.components.SettingsSectionHeader
import com.phone.contact.call.dialer.viewmodel.AppConfigViewModel

@Composable
fun CallerIdSpamScreen(
    onBack: () -> Unit,
    viewModel: CallerIdSpamViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val appConfigViewModel: AppConfigViewModel = androidx.lifecycle.viewmodel.compose.viewModel(context as ComponentActivity)
    val adConfig by appConfigViewModel.appResponse.collectAsState()
    val bannerAdUnitId = adConfig?.result?.let { result ->
        if (result.google_ads_on_off == "on" && result.banner_9_on_off == "on") {
            result.banner_9?.takeIf { it.isNotBlank() }
        } else null
    }
    // native_14 is unused elsewhere — tried first here (see NativeOrBannerAdView), falling back to
    // banner_9 above only if it fails to load.
    val nativeAdUnitId = adConfig?.result?.let { result ->
        if (result.google_ads_on_off == "on" && result.native_14_on_off == "on") {
            result.native_14?.takeIf { it.isNotBlank() }
        } else null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (nativeAdUnitId != null || bannerAdUnitId != null) {
                NativeOrBannerAdView(nativeAdUnitId = nativeAdUnitId, bannerAdUnitId = bannerAdUnitId, cacheKey = "caller_id_spam_ad")
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
                title = stringResource(R.string.settings_caller_id_spam),
                onBackClick = onBack
            )

            SettingsSectionHeader(title = stringResource(R.string.general_header))
            SettingsCard {
                SettingsItem(
                    title = stringResource(R.string.enable_caller_id_spam_protection),
                    icon = Icons.Outlined.Report,
                    onClick = { viewModel.setEnabled(!uiState.isEnabled) },
                    trailing = {
                        CustomSwitch(
                            checked = uiState.isEnabled,
                            onCheckedChange = { checked -> viewModel.setEnabled(checked) }
                        )
                    }
                )
            }

            androidx.compose.material3.Text(
                text = stringResource(R.string.caller_id_spam_protection_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            SettingsSectionHeader(title = stringResource(R.string.spam_numbers_section))
            NumberListCard(
                numbers = uiState.spamNumbers,
                emptyText = stringResource(R.string.no_spam_numbers),
                onRemove = { viewModel.removeSpamNumber(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NumberListCard(numbers: List<String>, emptyText: String, onRemove: (String) -> Unit) {
    SettingsCard {
        if (numbers.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        numbers.forEach { number ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onRemove(number) }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
