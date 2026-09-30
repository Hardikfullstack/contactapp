package com.phone.contacts.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phone.contacts.data.BlockRepository
import com.phone.contacts.ui.components.CustomSwitch
import kotlinx.coroutines.launch

/** Settings > Blocking — matches the reference app's own screen: a toggle for unidentified
 * callers, a "Manage block list" button, and an "Add a number" link. The toggle is a real,
 * persisted preference, but doesn't yet reject calls by itself — actually screening out private/
 * unknown-number calls needs a CallScreeningService this app doesn't have (a separate, bigger
 * feature, not part of this pass); blocking a specific number via [BlockRepository.blockNumber]
 * is real, since the system's own block list is honored by the telephony stack directly. The
 * reference app's "Number Series" range-blocking row is intentionally left out here too. */
@Composable
fun BlockingScreen(onBack: () -> Unit, onManageBlockList: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var blockUnknownCallers by remember { mutableStateOf(BlockRepository.isBlockUnknownCallersEnabled(context)) }
    var showAddNumberDialog by remember { mutableStateOf(false) }

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
                text = "Blocking",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 7.dp)) {
                    Text(text = "Unknown", color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        text = "Block calls from unidentified callers",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
                CustomSwitch(
                    checked = blockUnknownCallers,
                    onCheckedChange = {
                        blockUnknownCallers = it
                        BlockRepository.setBlockUnknownCallersEnabled(context, it)
                    }
                )
            }
        }

        Button(
            onClick = onManageBlockList,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(text = "Manage block list", color = MaterialTheme.colorScheme.onPrimary)
        }

        Text(
            text = "You won't receive calls or texts from blocked numbers.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Text(
            text = "Add a number",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .clickable { showAddNumberDialog = true }
        )
    }

    if (showAddNumberDialog) {
        AddBlockedNumberDialog(
            onDismiss = { showAddNumberDialog = false },
            onBlock = { number ->
                showAddNumberDialog = false
                coroutineScope.launch { BlockRepository.blockNumber(context, number) }
            }
        )
    }
}

@Composable
private fun AddBlockedNumberDialog(onDismiss: () -> Unit, onBlock: (String) -> Unit) {
    var number by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("Block calls and text from") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                placeholder = { Text("Phone number") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onBlock(number.trim()) },
                enabled = number.isNotBlank()
            ) { Text("Block") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
