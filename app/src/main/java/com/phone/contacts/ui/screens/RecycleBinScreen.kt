package com.phone.contacts.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phone.contacts.R
import com.phone.contacts.data.ContactRepository
import com.phone.contacts.data.local.DeletedContactEntity
import com.phone.contacts.ui.components.CustomSwitch
import kotlinx.coroutines.launch

/** Settings > Recycle bin — deleted contacts stay here (via [ContactRepository.moveToRecycleBin])
 * until restored or permanently removed. Matches the reference app's own Recycle Bin screen: an
 * always-visible "Delete contacts" auto-purge toggle (30-day cutoff) + "Empty bin now" button
 * above the list, deletion-time shown per row instead of the number, a Select/Select all overflow
 * menu (no "Empty bin" there — that's the persistent button, not a menu item), and long-press to
 * enter selection mode on a row instead of a plain tap. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecycleBinScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<DeletedContactEntity>>(emptyList()) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var autoDeleteEnabled by remember { mutableStateOf(ContactRepository.isRecycleBinAutoDeleteEnabled(context)) }
    var showEmptyBinConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    val effectiveSelectionMode = selectionMode || selectedIds.isNotEmpty()

    LaunchedEffect(Unit) {
        ContactRepository.purgeExpiredRecycleBinEntriesIfEnabled(context)
        ContactRepository.recycleBinFlow(context).collect { entries = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (effectiveSelectionMode) {
                IconButton(onClick = { selectionMode = false; selectedIds = emptySet() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel selection", tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(
                    text = "${selectedIds.size}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                IconButton(onClick = { showRestoreConfirm = true }, enabled = selectedIds.isNotEmpty()) {
                    Icon(Icons.Filled.Restore, contentDescription = "Restore", tint = MaterialTheme.colorScheme.onBackground)
                }
                IconButton(onClick = { showDeleteConfirm = true }, enabled = selectedIds.isNotEmpty()) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = "Delete permanently", tint = MaterialTheme.colorScheme.onBackground)
                }
            } else {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(
                    text = stringResource(R.string.recycle_bin_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                if (entries.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.select)) },
                                onClick = {
                                    menuExpanded = false
                                    selectionMode = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.select_all_label)) },
                                onClick = {
                                    menuExpanded = false
                                    selectionMode = true
                                    selectedIds = entries.map { it.id }.toSet()
                                }
                            )
                        }
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 15.dp)) {
                    Text(
                        text = stringResource(R.string.delete_contacts_toggle_label),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.auto_delete_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                CustomSwitch(
                    checked = autoDeleteEnabled,
                    onCheckedChange = {
                        autoDeleteEnabled = it
                        ContactRepository.setRecycleBinAutoDeleteEnabled(context, it)
                    }
                )
            }
        }

        if (entries.isNotEmpty()) {
            Button(
                onClick = { showEmptyBinConfirm = true },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 15.dp, vertical = 4.dp)
            ) {
                Text(text = stringResource(R.string.empty_bin_now_label), color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.no_data_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries, key = { it.id }) { entry ->
                    val isSelected = entry.id in selectedIds
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (effectiveSelectionMode) {
                                            selectedIds = if (isSelected) selectedIds - entry.id else selectedIds + entry.id
                                        }
                                    },
                                    onLongClick = {
                                        if (!effectiveSelectionMode) {
                                            selectionMode = true
                                            selectedIds = selectedIds + entry.id
                                        }
                                    }
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (effectiveSelectionMode) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text(
                                            text = entry.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                ContactAvatar(name = entry.name, photoUri = null, size = 40.dp)
                            }
                            Spacer(modifier = Modifier.size(14.dp))
                            Text(
                                text = entry.name,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            if (autoDeleteEnabled) {
                                Text(
                                    text = formatDaysLeft(entry.deletedAt),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 74.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    }
                }
            }
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text(stringResource(R.string.restore_contacts_title)) },
            text = { Text(stringResource(R.string.restore_contacts_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val toRestore = entries.filter { it.id in selectedIds }
                    showRestoreConfirm = false
                    selectionMode = false
                    selectedIds = emptySet()
                    coroutineScope.launch {
                        toRestore.forEach { ContactRepository.restoreFromRecycleBin(context, it) }
                    }
                }) { Text(stringResource(R.string.action_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text(stringResource(R.string.delete_contacts_title)) },
            text = { Text(stringResource(R.string.delete_contacts_forever_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selectedIds.toList()
                    showDeleteConfirm = false
                    selectionMode = false
                    selectedIds = emptySet()
                    coroutineScope.launch { ContactRepository.deleteFromRecycleBinPermanently(context, ids) }
                }) { Text(stringResource(R.string.action_remove), color = Color(0xFFE0413B)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showEmptyBinConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyBinConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text(stringResource(R.string.empty_bin_title)) },
            text = { Text(stringResource(R.string.empty_bin_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showEmptyBinConfirm = false
                    coroutineScope.launch { ContactRepository.clearRecycleBin(context) }
                }) { Text(stringResource(R.string.action_remove), color = Color(0xFFE0413B)) }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyBinConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

private const val RECYCLE_BIN_RETENTION_DAYS = 30
private const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000

/** Matches the reference app's own row trailing text — a countdown to the 30-day auto-delete
 * cutoff (its own "Delete contacts" toggle above), shown regardless of whether that toggle is
 * currently on, rather than a plain deletion timestamp. */
private fun formatDaysLeft(deletedAt: Long): String {
    val elapsedDays = (System.currentTimeMillis() - deletedAt) / ONE_DAY_MILLIS
    val daysLeft = (RECYCLE_BIN_RETENTION_DAYS - elapsedDays).coerceAtLeast(0)
    return "$daysLeft days left"
}
