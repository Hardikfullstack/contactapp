package com.example.contactapp.ui.features.history

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.contactapp.R
import com.example.contactapp.ads.NativeOrBannerAdView
import com.example.contactapp.domain.model.Contact
import com.example.contactapp.ui.components.BottomBarActionItem
import com.example.contactapp.ui.components.CommonBottomBar
import com.example.contactapp.ui.components.ContactQrDialog
import com.example.contactapp.ui.components.EditContactSheet
import com.example.contactapp.ui.components.HeaderActionButton
import com.example.contactapp.ui.components.HistoryItem
import com.example.contactapp.ui.theme.PrimaryGreen
import com.example.contactapp.util.CallUtils
import com.example.contactapp.util.MessageUtils
import com.example.contactapp.util.getAvatarColor
import com.example.contactapp.viewmodel.AppConfigViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onRingtoneClick: (name: String, number: String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Re-fetch name/photo whenever this screen resumes — covers coming back from the
    // system contact editor ("More details") after changing something there.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshContactDetails()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onBack()
        }
    }

    // Shares the same AppConfigViewModel instance created in MainActivity (Activity-scoped),
    // so the remote ad config isn't refetched per screen.
    val appConfigViewModel: AppConfigViewModel = viewModel(context as ComponentActivity)
    val adConfig by appConfigViewModel.appResponse.collectAsState()
    val bannerAdUnitId = adConfig?.result?.let { result ->
        if (result.google_ads_on_off == "on" && result.banner_2_on_off == "on") {
            result.banner_2?.takeIf { it.isNotBlank() }
        } else null
    }
    // native_7 is unused elsewhere — tried first here (see NativeOrBannerAdView), falling back to
    // banner_2 above only if it fails to load.
    val nativeAdUnitId = adConfig?.result?.let { result ->
        if (result.google_ads_on_off == "on" && result.native_7_on_off == "on") {
            result.native_7?.takeIf { it.isNotBlank() }
        } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 2.dp).size(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    HeaderActionButton(
                        icon = if (uiState.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        onClick = { viewModel.toggleFavorite() }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    HeaderActionButton(
                        icon = Icons.Outlined.MusicNote,
                        onClick = { onRingtoneClick(uiState.name.ifBlank { uiState.number }, uiState.number) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    HeaderActionButton(
                        icon = Icons.Default.Edit,
                        onClick = { viewModel.showEditSheet(true) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            val shareHeader = stringResource(R.string.call_history_for, uiState.name.ifBlank { uiState.number }, uiState.number)
            val shareEntry = stringResource(R.string.call_history_entry)
            val footerItems = listOf(
                BottomBarActionItem(
                    icon = Icons.Outlined.Share,
                    label = stringResource(R.string.share),
                    onClick = { viewModel.shareHistory(context, shareHeader, shareEntry) }
                ),
                BottomBarActionItem(
                    icon = if (uiState.isBlocked) Icons.Default.Block else Icons.Outlined.Block,
                    label = if (uiState.isBlocked) stringResource(R.string.unblock) else stringResource(R.string.block),
                    onClick = { viewModel.showBlockConfirmation(true) }
                ),
                BottomBarActionItem(
                    icon = Icons.Outlined.DeleteSweep,
                    label = stringResource(R.string.clear_history),
                    onClick = { viewModel.showClearHistoryConfirmation(true) }
                ),
                BottomBarActionItem(
                    icon = Icons.Outlined.Delete,
                    label = stringResource(R.string.delete),
                    onClick = { viewModel.showDeleteConfirmation(true) }
                )
            )
            Column(modifier = Modifier.navigationBarsPadding()) {
                if (nativeAdUnitId != null || bannerAdUnitId != null) {
                    NativeOrBannerAdView(nativeAdUnitId = nativeAdUnitId, bannerAdUnitId = bannerAdUnitId)
                }
                CommonBottomBar(items = footerItems, windowInsets = WindowInsets(0.dp))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (uiState.showBlockDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.showBlockConfirmation(false) },
                title = {
                    Text(
                        text = if (uiState.isBlocked)
                            stringResource(R.string.unblock) + " Contact?"
                        else
                            stringResource(R.string.block) + " Contact?",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = if (uiState.isBlocked)
                            stringResource(R.string.unblock_confirmation)
                        else
                            stringResource(R.string.block_confirmation)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.blockNumber() }) {
                        Text(
                            text = (if (uiState.isBlocked) stringResource(R.string.unblock) else stringResource(R.string.block)).uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.showBlockConfirmation(false) }) {
                        Text(text = stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp)
            )
        }

        if (uiState.showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.showDeleteConfirmation(false) },
                title = { Text(text = stringResource(R.string.delete_contact_title), fontWeight = FontWeight.Bold) },
                text = { Text(text = stringResource(R.string.delete_contact_text)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.deleteHistoryAndContact() }) {
                        Text(text = stringResource(R.string.delete), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.showDeleteConfirmation(false) }) {
                        Text(text = stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp)
            )
        }

        if (uiState.showClearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.showClearHistoryConfirmation(false) },
                title = { Text(text = stringResource(R.string.clear_history_title), fontWeight = FontWeight.Bold) },
                text = { Text(text = stringResource(R.string.clear_history_text, uiState.name.ifBlank { uiState.number })) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearHistory() }) {
                        Text(text = stringResource(R.string.clear_history).uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.showClearHistoryConfirmation(false) }) {
                        Text(text = stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp)
            )
        }

        if (uiState.pendingDeleteCallId != null) {
            AlertDialog(
                onDismissRequest = { viewModel.cancelDeleteCall() },
                title = { Text(text = stringResource(R.string.delete_call_title), fontWeight = FontWeight.Bold) },
                text = { Text(text = stringResource(R.string.delete_call_text)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmDeleteCall() }) {
                        Text(text = stringResource(R.string.delete), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelDeleteCall() }) {
                        Text(text = stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp)
            )
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryGreen)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
            ) {
                item {
                    ContactDetailHeader(
                        name = uiState.name.ifBlank { uiState.number },
                        number = uiState.number,
                        photoUri = uiState.photoUri
                    )
                }

                item {
                    QuickActionsRow(
                        onCallClick = { CallUtils.makeCall(context, uiState.number) },
                        onMessageClick = { MessageUtils.sendMessage(context, uiState.number) },
                        onQrClick = { viewModel.showQr(true) }
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.recent_activity),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                if (uiState.groupedCalls.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_recent_activity),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                uiState.groupedCalls.forEach { (date, calls) ->
                    item {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Column {
                                calls.forEachIndexed { index, call ->
                                    HistoryItem(
                                        call = call,
                                        onLongClick = { viewModel.requestDeleteCall(call.id) }
                                    )
                                    if (index < calls.size - 1) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant
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

    if (uiState.showEditSheet) {
        EditContactSheet(
            initialName = uiState.name,
            initialNumber = uiState.number,
            onSave = { newName, newNumber ->
                viewModel.updateContact(newName, newNumber)
            },
            onMoreDetailsClick = {
                val intent = android.content.Intent(android.content.Intent.ACTION_EDIT).apply {
                    val contactUri = android.content.ContentUris.withAppendedId(
                        android.provider.ContactsContract.Contacts.CONTENT_URI,
                        uiState.contactId.toLong()
                    )
                    data = contactUri
                }
                context.startActivity(intent)
                viewModel.showEditSheet(false)
            },
            onDismiss = { viewModel.showEditSheet(false) }
        )
    }

    if (uiState.showQrDialog) {
        ContactQrDialog(
            contact = Contact(
                id = uiState.contactId,
                name = uiState.name.ifBlank { uiState.number },
                number = uiState.number,
                photoUri = uiState.photoUri
            ),
            qrBitmap = uiState.qrBitmap,
            onDismiss = { viewModel.showQr(false) }
        )
    }
}

@Composable
fun ContactDetailHeader(
    name: String,
    number: String,
    photoUri: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(getAvatarColor(name)),
            contentAlignment = Alignment.Center
        ) {
            if (photoUri != null) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (number.isNotBlank() && number != name) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = number,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun QuickActionsRow(
    onCallClick: () -> Unit,
    onMessageClick: () -> Unit,
    onQrClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickActionButton(icon = Icons.Default.Call, label = stringResource(R.string.call), onClick = onCallClick)
        QuickActionButton(icon = Icons.AutoMirrored.Outlined.Chat, label = stringResource(R.string.message), onClick = onMessageClick)
        QuickActionButton(icon = Icons.Outlined.QrCode, label = stringResource(R.string.qr_code_label), onClick = onQrClick)
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = PrimaryGreen.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = PrimaryGreen, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
