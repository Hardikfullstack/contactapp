package com.example.contactapp.ui.features.recents

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.example.contactapp.util.AnalyticsManager
import com.example.contactapp.util.AppUpdateHelper
import com.example.contactapp.util.CallUtils
import com.example.contactapp.util.DefaultDialerState
import com.example.contactapp.util.InAppUpdateResult
import com.example.contactapp.util.MessageUtils
import com.example.contactapp.util.PreferenceManager
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

    // "Set as default dialer" now lives here instead of onboarding, matching the reference app's
    // own flow: Home prompts for the role itself, and declining it (Cancel/"Don't allow" on the
    // role picker) falls straight through to requesting Phone/Call Log/Contacts together — the
    // same permission set the reference app's Home screen requests in that situation. A second
    // "Don't allow" on any of those (no more rationale to show) surfaces a Settings dialog, same
    // as onboarding's own permanently-denied handling. A persistent banner also lets the user
    // retry "Set Default" any time, not just on the one-time auto-prompt.
    val isDefaultDialerState by DefaultDialerState.isDefault
    var showPermanentlyDeniedDialog by remember { mutableStateOf(false) }

    fun refreshDefaultDialerState() {
        DefaultDialerState.refresh(context)
    }

    val fallbackPermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.WRITE_CALL_LOG)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.WRITE_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
    }

    val fallbackPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.checkPermissionAndFetch()
        // A permission the system will no longer show a rationale for means the user picked
        // "Don't allow" a second time — the request dialog won't reappear on its own, so offer
        // Settings instead, same as onboarding's own permanently-denied dialog.
        val activity = context as? Activity
        val anyPermanentlyDenied = activity != null && fallbackPermissions.any { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
        if (anyPermanentlyDenied) {
            showPermanentlyDeniedDialog = true
        }
    }

    val defaultDialerRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        refreshDefaultDialerState()
        if (!isDefaultDialerState) {
            fallbackPermissionLauncher.launch(fallbackPermissions.toTypedArray())
        } else {
            // Being default dialer doesn't itself grant Call Log/Contacts — without this, the
            // list stayed stuck on whatever hasPermission/isLoading was at first mount until the
            // user left this tab and came back (which re-runs the LaunchedEffect(Unit) below).
            viewModel.checkPermissionAndFetch()
        }
    }

    fun launchDefaultDialerRequest() {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager != null) {
            defaultDialerRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        }
    }

    LaunchedEffect(Unit) {
        refreshDefaultDialerState()
        val prefs = PreferenceManager(context.applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isDefaultDialerState && !prefs.isDefaultDialerPrompted()) {
            prefs.setDefaultDialerPrompted()
            launchDefaultDialerRequest()
        }
    }

    // The role/permissions can change from system Settings while this screen is backgrounded.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshDefaultDialerState()
                viewModel.checkPermissionAndFetch()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showPermanentlyDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermanentlyDeniedDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.permission_permanently_denied_title)) },
            text = { Text(stringResource(R.string.permission_permanently_denied_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermanentlyDeniedDialog = false
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.open_settings), color = PrimaryGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermanentlyDeniedDialog = false }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Shares the same AppConfigViewModel instance created in MainActivity (Activity-scoped).
    val appConfigViewModel: AppConfigViewModel = viewModel(context as ComponentActivity)
    val adConfig by appConfigViewModel.appResponse.collectAsState()
    val homeNativeAdUnitId = adConfig?.result?.let { result ->
        if (result.google_ads_on_off == "on" && result.native_2_on_off == "on") {
            result.native_2?.takeIf { it.isNotBlank() }
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

    // Runs on every resume (not just once), per Google's own guidance for the IMMEDIATE update
    // flow: (1) resume a stalled update instead of leaving it silently abandoned if the flow got
    // interrupted mid-way (a call came in, the app backgrounded, etc.), and (2) log the outcome
    // of a just-finished flow (see MainActivity.onActivityResult -> InAppUpdateResult) for
    // visibility — our own dialog above isn't dismissed on tapping "Update", so the user lands
    // back on it naturally either way if the flow was cancelled or failed; this doesn't need to
    // act on the result beyond logging it.
    val updateFlowLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(updateFlowLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver

            InAppUpdateResult.pendingResultCode?.let { resultCode ->
                InAppUpdateResult.pendingResultCode = null
                val outcome = when (resultCode) {
                    Activity.RESULT_OK -> "ok"
                    Activity.RESULT_CANCELED -> "cancelled"
                    com.google.android.play.core.install.model.ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> "failed"
                    else -> "unknown_$resultCode"
                }
                AnalyticsManager.logEventWithAction(
                    eventName = "app_update_dialog",
                    screenName = "RecentsScreen",
                    action = "Immediate Update Flow Result",
                    extraParams = mapOf("result" to outcome)
                )
            }

            (context as? Activity)?.let { activity ->
                appUpdateHelper.resumeStalledUpdateIfAny { appUpdateInfo ->
                    appUpdateHelper.startUpdate(
                        activity,
                        appUpdateInfo,
                        com.google.android.play.core.install.model.AppUpdateType.IMMEDIATE,
                        InAppUpdateResult.REQUEST_CODE
                    )
                }
            }
        }
        updateFlowLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { updateFlowLifecycleOwner.lifecycle.removeObserver(observer) }
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

    // Maintenance (MainNavigation.kt) is a separate overlay Dialog() — without this guard, both
    // could show stacked at once if the panel ever has both flags on simultaneously.
    if (showUpdateDialog && adConfig?.result?.extra_data_1_on_off != "on") {
        val isSoftUpdate = adConfig?.result?.extra_data_5_on_off == "on"
        LaunchedEffect(Unit) {
            AnalyticsManager.logEventWithAction(
                eventName = "app_update_dialog",
                screenName = "RecentsScreen",
                action = "Shown",
                extraParams = mapOf("type" to if (isSoftUpdate) "soft" else "hard")
            )
        }
        UpdateAppDialog(
            title = stringResource(R.string.update_title),
            description = stringResource(R.string.update_desc),
            onOkClick = {
                AnalyticsManager.logEventWithAction(
                    eventName = "app_update_dialog",
                    screenName = "RecentsScreen",
                    action = "Update Accepted"
                )
                val openPlayStore = {
                    // Always built from the app's own package name — never from the panel's
                    // app_link field. There's never a legitimate reason this app's Play Store URL
                    // would be anything other than this, so a remote value can only ever be a
                    // liability (a misconfigured panel value would silently break this). Same
                    // market:// -> https:// fallback pattern as RateUsHelper's
                    // openPlayStoreListing(), for the same reason.
                    AppOpenBackgroundReturnTrigger.isAdPaused = true
                    try {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=${context.packageName}")))
                    } catch (e: android.content.ActivityNotFoundException) {
                        try {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")))
                        } catch (e2: android.content.ActivityNotFoundException) {
                            // No Play Store app or browser available — nothing more we can do.
                        }
                    }
                }

                if (adConfig?.result?.extra_data_2_on_off == "on") {
                    // Play Core's appUpdateInfo Task can, on some devices/Play Store app states,
                    // just never call back at all — neither success nor failure — leaving the
                    // Update button looking like it did nothing. This guard forces the same Play
                    // Store fallback after a few seconds if none of the listener methods have
                    // fired by then, instead of leaving the user stuck forever on a call Google's
                    // own SDK never resolved.
                    var handled = false
                    val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    val timeoutRunnable = Runnable {
                        if (!handled) {
                            handled = true
                            openPlayStore()
                        }
                    }
                    timeoutHandler.postDelayed(timeoutRunnable, 4000L)

                    appUpdateHelper.checkForUpdate(object : AppUpdateHelper.UpdateStatusListener {
                        override fun onUpdateAvailable(appUpdateInfo: com.google.android.play.core.appupdate.AppUpdateInfo) {
                            if (handled) return
                            handled = true
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            val activity = context as? Activity
                            if (activity != null) {
                                appUpdateHelper.startUpdate(
                                    activity,
                                    appUpdateInfo,
                                    com.google.android.play.core.install.model.AppUpdateType.IMMEDIATE,
                                    InAppUpdateResult.REQUEST_CODE,
                                    onFailure = openPlayStore
                                )
                            } else {
                                openPlayStore()
                            }
                        }

                        override fun onUpdateNotAvailable() {
                            if (handled) return
                            handled = true
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            openPlayStore()
                        }

                        override fun onUpdateFailed(e: Exception) {
                            if (handled) return
                            handled = true
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            openPlayStore()
                        }

                        override fun onFlexibleUpdateDownloaded() {}
                    })
                } else {
                    openPlayStore()
                }
            },
            onCancelClick = if (isSoftUpdate) {
                {
                    AnalyticsManager.logEventWithAction(
                        eventName = "app_update_dialog",
                        screenName = "RecentsScreen",
                        action = "Soft Update Cancelled/Dismissed"
                    )
                    showUpdateDialog = false
                }
            } else null
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (isDefaultDialerState) onKeypadClick() },
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
                        onClick = { if (isDefaultDialerState) onSearchClick() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))

                    var showFilterMenu by remember { mutableStateOf(false) }

                    Box {
                        HeaderActionButton(
                            icon = Icons.Outlined.FilterList,
                            onClick = { if (isDefaultDialerState) showFilterMenu = true }
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

            if (isDefaultDialerState && uiState.spamNumbers.isNotEmpty()) {
                SpamBanner(
                    count = uiState.spamNumbers.size,
                    onClearClick = { viewModel.showClearSpamConfirmation(true) }
                )
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (!isDefaultDialerState) {
                // Replaces the whole list area (not just a banner above it) with a centered
                // prompt, matching the reference app's own full-screen "Set Default" placeholder.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SetDefaultDialerBanner(onSetDefaultClick = { launchDefaultDialerRequest() })
                }
            } else if (!uiState.hasPermission || uiState.groupedCalls.isEmpty()) {
                // Empty State — reached only once this app IS the default dialer, so a missing
                // permission here means Call Log specifically still needs to be granted.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.no_result_found),
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
private fun SetDefaultDialerBanner(onSetDefaultClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.surface else Color(0xFFF3F3F3),
        border = BorderStroke(1.dp, PrimaryGreen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.set_default_banner_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onSetDefaultClick,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp)
                    .animatedPulse(PrimaryGreen),
                shape = RoundedCornerShape(77.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Text(
                    text = stringResource(R.string.action_set_default),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
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
