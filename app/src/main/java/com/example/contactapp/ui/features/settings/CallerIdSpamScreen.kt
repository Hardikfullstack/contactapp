package com.example.contactapp.ui.features.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.contactapp.R
import com.example.contactapp.ads.NativeOrBannerAdView
import com.example.contactapp.ui.components.CommonHeader
import com.example.contactapp.ui.components.CustomSwitch
import com.example.contactapp.ui.components.SettingsCard
import com.example.contactapp.ui.components.SettingsItem
import com.example.contactapp.ui.components.SettingsSectionHeader
import com.example.contactapp.viewmodel.AppConfigViewModel

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
                NativeOrBannerAdView(nativeAdUnitId = nativeAdUnitId, bannerAdUnitId = bannerAdUnitId)
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
        }
    }
}
