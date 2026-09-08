package com.example.contactapp.ui.features.recents

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.contactapp.R
import com.example.contactapp.ads.AppOpenBackgroundReturnTrigger
import com.example.contactapp.ads.AppOpenCounter
import com.example.contactapp.ads.NativeAdTemplate
import com.example.contactapp.ads.NativeAdView
import com.example.contactapp.ui.components.*
import com.example.contactapp.ui.components.dialogs.RateUsDialog
import com.example.contactapp.ui.components.dialogs.UpdateAppDialog
import com.example.contactapp.ui.theme.LocalIsDarkTheme
import com.example.contactapp.ui.theme.PrimaryGreen
import com.example.contactapp.ui.theme.TextSecondary
import com.example.contactapp.util.AppUpdateHelper
import com.example.contactapp.util.CallUtils
import com.example.contactapp.util.MessageUtils
import com.example.contactapp.util.RateUsHelper
import com.example.contactapp.util.isRemoteVersionNewer
import com.example.contactapp.viewmodel.AppConfigViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    onSearchClick: () -> Unit,
    onHistoryClick: (String, String) -> Unit,
    onKeypadClick: () -> Unit,
    viewModel: RecentsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val expandedCallId by viewModel.expandedCallId.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.checkPermissionAndFetch()
    }

    // Shares the same AppConfigViewModel instance created in MainActivity (Activity-scoped).
    val appConfigViewModel: AppConfigViewModel = viewModel(context as ComponentActivity)
    val adConfig by appConfigViewModel.appResponse.collectAsState()
    val homeNativeAdUnitId = adConfig?.result?.let { result ->
        if (result.google_ads_on_off == "on" && result.native_3_on_off == "on") {
            result.native_3?.takeIf { it.isNotBlank() }
        } else null
    }

    // In-app update: extra_data_2_message carries the latest version to prompt for; extra_data_5_on_off
    // picks soft vs hard update, extra_data_2_on_off picks Play's in-app API vs plain Play Store.
    var showUpdateDialog by remember { mutableStateOf(false) }
    val appUpdateHelper = remember { AppUpdateHelper(context) }
    LaunchedEffect(adConfig) {
        val remoteVersion = adConfig?.result?.extra_data_2_message
        if (!remoteVersion.isNullOrBlank() && isRemoteVersionNewer(remoteVersion, com.example.contactapp.BuildConfig.VERSION_NAME)) {
            showUpdateDialog = true
        }
    }

    // Auto Rate Us — the first-ever open never touches AppOpenCounter (see its doc comment),
    // so count == 1 here is exactly the user's second open, shown once ever.
    var showRateUsDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!RateUsHelper.hasAutoShown(context) &&
            !RateUsHelper.hasInteracted(context) &&
            AppOpenCounter.currentCount(context) == 1
        ) {
            showRateUsDialog = true
            RateUsHelper.markAutoShown(context)
        }
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

    if (showUpdateDialog) {
        val isSoftUpdate = adConfig?.result?.extra_data_5_on_off == "on"
        UpdateAppDialog(
            title = stringResource(R.string.update_title),
            description = stringResource(R.string.update_desc),
            onOkClick = {
                val openPlayStore = {
                    val playStoreLink = adConfig?.result?.app_link
                        ?.takeIf { it.isNotBlank() }
                        ?: "https://play.google.com/store/apps/details?id=${context.packageName}"
                    val uri = runCatching { android.net.Uri.parse(playStoreLink) }.getOrNull()
                    if (uri != null) {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                        try {
                            // Opening Play Store backgrounds/re-foregrounds this Activity — without
                            // this, that return would look like a normal app-switch-back and could
                            // trigger an App Open ad right as the user is trying to update.
                            AppOpenBackgroundReturnTrigger.isAdPaused = true
                            context.startActivity(intent)
                        } catch (e: android.content.ActivityNotFoundException) {
                            // No Play Store app or browser available — nothing more we can do.
                        }
                    }
                }

                if (adConfig?.result?.extra_data_2_on_off == "on") {
                    appUpdateHelper.checkForUpdate(object : AppUpdateHelper.UpdateStatusListener {
                        override fun onUpdateAvailable(appUpdateInfo: com.google.android.play.core.appupdate.AppUpdateInfo) {
                            val activity = context as? Activity
                            if (activity != null) {
                                appUpdateHelper.startUpdate(
                                    activity,
                                    appUpdateInfo,
                                    com.google.android.play.core.install.model.AppUpdateType.IMMEDIATE,
                                    999,
                                    onFailure = openPlayStore
                                )
                            } else {
                                openPlayStore()
                            }
                        }

                        override fun onUpdateNotAvailable() {
                            openPlayStore()
                        }

                        override fun onUpdateFailed(e: Exception) {
                            openPlayStore()
                        }

                        override fun onFlexibleUpdateDownloaded() {}
                    })
                } else {
                    openPlayStore()
                }
            },
            onCancelClick = if (isSoftUpdate) {
                { showUpdateDialog = false }
            } else null
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onKeypadClick,
                containerColor = PrimaryGreen,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Outlined.Dialpad,
                    contentDescription = "Keypad"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            CommonHeader(
                title = stringResource(R.string.recents),
                actions = {
                    HeaderActionButton(
                        icon = Icons.Outlined.Search,
                        onClick = onSearchClick
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    var showFilterMenu by remember { mutableStateOf(false) }
                    
                    Box {
                        HeaderActionButton(
                            icon = Icons.Outlined.FilterList,
                            onClick = { showFilterMenu = true }
                        )
                        
                        FilterDropdown(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                            selectedFilter = uiState.selectedFilter,
                            onFilterSelected = { viewModel.setFilter(it) }
                        )
                    }
                }
            )

            if (homeNativeAdUnitId != null) {
                NativeAdView(
                    adUnitId = homeNativeAdUnitId,
                    template = NativeAdTemplate.LARGE,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
                )
            }

            if (uiState.spamNumbers.isNotEmpty()) {
                SpamBanner(
                    count = uiState.spamNumbers.size,
                    onClearClick = { viewModel.showClearSpamConfirmation(true) }
                )
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (!uiState.hasPermission || uiState.groupedCalls.isEmpty()) {
                // Empty State
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (!uiState.hasPermission) 
                            stringResource(R.string.call_log_permission_required) 
                        else 
                            stringResource(R.string.no_result_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                val sections = remember {
                    listOf(
                        "today" to R.string.today,
                        "yesterday" to R.string.yesterday,
                        "older" to R.string.older
                    )
                }

                val listState = rememberLazyListState()
                LaunchedEffect(uiState.selectedFilter) {
                    listState.scrollToItem(0)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    sections.forEach { (key, titleRes) ->
                        val calls = uiState.groupedCalls[key]
                        if (!calls.isNullOrEmpty()) {
                            item(key = "header_$key") {
                                Text(
                                    text = stringResource(titleRes),
                                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 16.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.onSurfaceVariant else TextSecondary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }

                            itemsIndexed(
                                items = calls,
                                key = { _, call -> call.id }
                            ) { index, call ->
                                val isExpanded = expandedCallId == call.id
                                val shape = when {
                                    calls.size == 1 -> RoundedCornerShape(16.dp)
                                    index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                    index == calls.size - 1 -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                                    else -> RoundedCornerShape(0.dp)
                                }

                                Surface(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .fillMaxWidth(),
                                    shape = shape,
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 0.dp
                                ) {
                                    Column {
                                        ExpandableCallItem(
                                            call = call,
                                            expanded = isExpanded,
                                            onClick = { viewModel.onCallClicked(call.id) },
                                            onLongClick = { viewModel.onCallLongClick(call) },
                                            onCallClick = { CallUtils.makeCall(context, call.number) },
                                            onMessageClick = { MessageUtils.sendMessage(context, call.number) },
                                            onHistoryClick = { onHistoryClick(call.name ?: "", call.number) }
                                        )
                                        
                                        if (index < calls.size - 1) {
                                            // Starts under the name/desc text (16dp row padding +
                                            // 46dp avatar + 16dp spacer from CallItem), not under
                                            // the avatar — and runs flush to the card's right edge.
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 78.dp),
                                                thickness = 0.5.dp,
                                                color = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.outlineVariant else Color(0xFFCDCDCD)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showClearSpamDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showClearSpamConfirmation(false) },
            title = { Text(stringResource(R.string.clear_spam_title)) },
            text = { Text(stringResource(R.string.clear_spam_text, uiState.spamNumbers.size)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteAllSpamCalls() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showClearSpamConfirmation(false) }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    uiState.selectedCallItem?.let { item ->
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        val annotatedString = androidx.compose.ui.text.AnnotatedString(item.number)
        
        ContactActionBottomSheet(
            title = item.name ?: item.number,
            subtitle = item.number,
            isBlocked = item.isBlocked,
            onCopyClick = {
                clipboardManager.setText(annotatedString)
            },
            onBlockClick = {
                viewModel.toggleBlock(item.number, item.isBlocked)
            },
            onDeleteClick = {
                viewModel.deleteCall(item.id)
            },
            onDismiss = { viewModel.dismissActionSheet() }
        )
    }

}

@Composable
private fun SpamBanner(count: Int, onClearClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Report,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.spam_detected_banner, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClearClick) {
                Text(
                    text = stringResource(R.string.clear_spam_calls),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
