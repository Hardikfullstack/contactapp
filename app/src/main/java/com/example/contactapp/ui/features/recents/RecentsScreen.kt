package com.example.contactapp.ui.features.recents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.contactapp.R
import com.example.contactapp.ui.components.*
import com.example.contactapp.ui.theme.PrimaryGreen
import com.example.contactapp.util.CallUtils
import com.example.contactapp.util.MessageUtils

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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
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
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    sections.forEach { (key, titleRes) ->
                        val calls = uiState.groupedCalls[key]
                        if (!calls.isNullOrEmpty()) {
                            item(key = "header_$key") {
                                Text(
                                    text = stringResource(titleRes),
                                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
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
                                        .padding(horizontal = 15.dp)
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
