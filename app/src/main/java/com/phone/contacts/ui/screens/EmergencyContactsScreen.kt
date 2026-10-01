package com.phone.contacts.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phone.contacts.data.local.AppDatabase
import com.phone.contacts.data.local.EmergencyContactEntity
import com.phone.contacts.util.CallUtils
import kotlinx.coroutines.launch

/** Settings > Emergency contacts, and also reachable to add more at any time via the top-bar
 * "Add" pill once the list isn't empty. Matches the reference app's own two states: a single
 * centered "Add new contact" button when empty, a plain list with a top-right "Add" pill once
 * populated. Tapping a saved entry calls it directly (matching the reference app, which has no
 * other entry point for these at all — no SOS button, no keypad shortcut, no lock-screen
 * integration; this list is the only place the call happens) — removing is a long-press instead,
 * so an accidental tap can't delete an entry. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmergencyContactsScreen(onBack: () -> Unit, onAddClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.getInstance(context).emergencyContactDao() }
    val entries by dao.getAll().collectAsState(initial = emptyList())
    var removingEntry by remember { mutableStateOf<EmergencyContactEntity?>(null) }

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
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = "Emergency contacts",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            if (entries.isNotEmpty()) {
                Button(
                    onClick = onAddClick,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Add", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(
                    onClick = onAddClick,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Add new contact", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(entries, key = { it.id }) { entry ->
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { CallUtils.placeCall(context, entry.number) },
                                    onLongClick = { removingEntry = entry }
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ContactAvatar(name = entry.name, photoUri = entry.photoUri, size = 44.dp)
                            Spacer(modifier = Modifier.size(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = entry.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
                                Text(text = entry.number, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF1DA463)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(20.dp))
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

    removingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { removingEntry = null },
            title = { Text("Remove ${entry.name}?") },
            text = { Text("It will be removed from your emergency contacts.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { dao.deleteById(entry.id) }
                    removingEntry = null
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removingEntry = null }) { Text("Cancel") }
            }
        )
    }
}
