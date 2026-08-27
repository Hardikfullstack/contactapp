package com.example.contactapp.ui.features.contacts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.contactapp.R
import com.example.contactapp.ui.components.AddContactSheet
import com.example.contactapp.ui.components.CommonHeader
import com.example.contactapp.ui.components.HeaderActionButton
import com.example.contactapp.ui.components.ContactItem
import com.example.contactapp.ui.theme.PrimaryGreen
import com.example.contactapp.util.CallUtils
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    onContactClick: (String, String) -> Unit,
    onSearchClick: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var currentDragY by remember { mutableStateOf<Float?>(null) }
    var alphabetColumnHeight by remember { mutableStateOf(0f) }
    val alphabet = remember { (('A'..'Z') + '#').toList() }

    val initialToIndex = remember(uiState.groupedContacts) {
        val mapping = mutableMapOf<Char, Int>()
        var currentIndex = 0
        uiState.groupedContacts.forEach { (initial, contacts) ->
            mapping[initial] = currentIndex
            currentIndex += 1 // for stickyHeader
            currentIndex += contacts.size // for items
        }
        mapping
    }

    val exportSuccessMsg = stringResource(R.string.export_success)
    val exportFailedMsg = stringResource(R.string.export_failed)
    val importSuccessMsg = stringResource(R.string.import_success)
    val importFailedMsg = stringResource(R.string.import_failed)

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    viewModel.importContactsFromVcf(stream) { count ->
                        android.widget.Toast.makeText(
                            context,
                            java.lang.String.format(importSuccessMsg, count),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, importFailedMsg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/x-vcard")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { 
                        viewModel.exportContactsToVcf(it)
                    }
                    android.widget.Toast.makeText(context, exportSuccessMsg, android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, exportFailedMsg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                FloatingActionButton(
                    onClick = { viewModel.showAddContactSheet(true) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.offset(y = 24.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Contact")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) {
        // No bottom padding here — the outer MainNavigation Scaffold already reserves space for
        // the shared bottom nav bar/banner ad; applying innerPadding's bottom here would double-count it.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            if (uiState.isSelectionMode) {
                // Selection Mode Header
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        
                        Text(
                            text = stringResource(R.string.recycle_bin_selected_count, uiState.selectedIds.size),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        IconButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                CommonHeader(
                    title = stringResource(R.string.contacts),
                    actions = {
                        HeaderActionButton(
                            icon = Icons.Outlined.Search,
                            onClick = onSearchClick
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            HeaderActionButton(
                                icon = Icons.Default.MoreVert,
                                onClick = { showMenu = true }
                            )
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.import_contacts)) },
                                    onClick = {
                                        showMenu = false
                                        importLauncher.launch("text/x-vcard")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_contacts)) },
                                    onClick = {
                                        showMenu = false
                                        exportLauncher.launch("contacts.vcf")
                                    }
                                )
                            }
                        }
                    }
                )
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
                    ) {
                        uiState.groupedContacts.forEach { (initial, contacts) ->
                            stickyHeader(key = "header_$initial") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(horizontal = 24.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = initial.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            items(
                                items = contacts,
                                key = { it.id }
                            ) { contact ->
                                ContactItem(
                                    contact = contact,
                                    isSelected = uiState.selectedIds.contains(contact.id),
                                    isSelectionMode = uiState.isSelectionMode,
                                    onClick = {
                                        if (uiState.isSelectionMode) {
                                            viewModel.toggleSelection(contact.id)
                                        } else {
                                            onContactClick(contact.name, contact.number)
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.toggleSelection(contact.id)
                                    },
                                    onCallClick = { CallUtils.makeCall(context, contact.number) }
                                )
                            }
                        }
                    }

                    // Alphabet Fast Scroller - Positioned to avoid FAB
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 4.dp, bottom = 80.dp)
                            .fillMaxHeight()
                            .onGloballyPositioned { alphabetColumnHeight = it.size.height.toFloat() }
                            .pointerInput(alphabetColumnHeight, initialToIndex) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentDragY = offset.y
                                        scrollToCharacter(offset.y, alphabet, alphabetColumnHeight, initialToIndex, listState, coroutineScope)
                                    },
                                    onDrag = { change, _ ->
                                        currentDragY = change.position.y
                                        scrollToCharacter(change.position.y, alphabet, alphabetColumnHeight, initialToIndex, listState, coroutineScope)
                                    },
                                    onDragEnd = { currentDragY = null },
                                    onDragCancel = { currentDragY = null }
                                )
                            }
                            .pointerInput(alphabetColumnHeight, initialToIndex) {
                                detectTapGestures { offset ->
                                    currentDragY = offset.y
                                    scrollToCharacter(offset.y, alphabet, alphabetColumnHeight, initialToIndex, listState, coroutineScope)
                                    // Reset after a short delay for smooth fade out
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        alphabet.forEachIndexed { index, char ->
                            val hasContacts = initialToIndex.containsKey(char)
                            
                            // Distance calculation for fish-eye zoom
                            val itemCenterY = if (alphabetColumnHeight > 0) {
                                (index + 0.5f) * (alphabetColumnHeight / alphabet.size)
                            } else 0f
                            
                            val distance = currentDragY?.let { abs(it - itemCenterY) } ?: Float.MAX_VALUE
                            val scale by animateFloatAsState(
                                targetValue = if (distance < 120f) {
                                    1f + (1.8f * (1f - (distance / 120f).coerceIn(0f, 1f)))
                                } else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "scale"
                            )

                            Text(
                                text = char.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (hasContacts) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = - (scale - 1f) * 25f // Pop out effect
                                    }
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    
    // LaunchedEffect to clear drag state after tap
    LaunchedEffect(currentDragY) {
        if (currentDragY != null) {
            kotlinx.coroutines.delay(800)
            currentDragY = null
        }
    }

    if (uiState.showAddContactSheet) {
        AddContactSheet(
            phoneNumber = "",
            onSave = { name, number ->
                viewModel.validateAndSaveContact(name, number)
            },
            onMoreDetailsClick = { name, number ->
                val intent = android.content.Intent(android.content.Intent.ACTION_INSERT_OR_EDIT).apply {
                    type = android.provider.ContactsContract.Contacts.CONTENT_ITEM_TYPE
                    putExtra(android.provider.ContactsContract.Intents.Insert.NAME, name)
                    putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, number)
                }
                context.startActivity(intent)
                viewModel.showAddContactSheet(false)
            },
            onDismiss = { viewModel.showAddContactSheet(false) }
        )
    }

    if (uiState.duplicateContact != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearDuplicateState() },
            title = { Text(stringResource(R.string.duplicate_number)) },
            text = { 
                Text(stringResource(R.string.duplicate_number_warning, uiState.duplicateContact?.name ?: "")) 
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmSaveDuplicate() }) {
                    Text(stringResource(R.string.save_anyway), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearDuplicateState() }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
        )
    }
}

private fun scrollToCharacter(
    y: Float,
    alphabet: List<Char>,
    height: Float,
    mapping: Map<Char, Int>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    if (height <= 0f) return
    val index = (y / height * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)
    val char = alphabet[index]
    mapping[char]?.let { targetIndex ->
        scope.launch {
            listState.scrollToItem(targetIndex)
        }
    }
}
