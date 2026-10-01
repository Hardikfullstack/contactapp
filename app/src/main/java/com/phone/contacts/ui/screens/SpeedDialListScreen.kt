package com.phone.contacts.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phone.contacts.util.CallUtils
import com.phone.contacts.util.SpeedDialPreferences
import com.phone.contacts.util.speedDialColorFor
import com.phone.contacts.util.speedDialKeys

/** The management screen reached from Speed dial's "Speed dial list" button — tapping a saved
 * entry calls it directly (same convention as Emergency contacts), the trailing icon removes it
 * immediately with no extra confirmation, matching the reference app's own explicit remove icon. */
@Composable
fun SpeedDialListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    remember { SpeedDialPreferences.initialize(context) }
    val entries by SpeedDialPreferences.entries
    val assignedKeys = remember(entries) { speedDialKeys.filter { it in entries } }

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
                text = "Speed dial list",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
        }

        if (assignedKeys.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No speed dial numbers set yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(assignedKeys, key = { it }) { dialKey ->
                    val entry = entries.getValue(dialKey)
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { CallUtils.placeCall(context, entry.number) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(44.dp).clip(CircleShape).background(speedDialColorFor(dialKey)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = dialKey, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.size(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = entry.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
                                Text(text = entry.number, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { SpeedDialPreferences.setKey(context, dialKey, null) }) {
                                Icon(
                                    Icons.Filled.RemoveCircleOutline,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 78.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    }
                }
            }
        }
    }
}
