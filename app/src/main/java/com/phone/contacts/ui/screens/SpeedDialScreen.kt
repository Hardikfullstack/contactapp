package com.phone.contacts.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phone.contacts.R
import com.phone.contacts.ui.theme.primaryAccentColor
import com.phone.contacts.util.SpeedDialEntry
import com.phone.contacts.util.SpeedDialPreferences
import com.phone.contacts.util.speedDialColorFor
import com.phone.contacts.util.speedDialKeys

/** Settings > Speed dial — a grid of every dial-pad key (0-9, *, #), kept deliberately different
 * from the Keypad itself (no ABC/DEF letter hints — those mean nothing for assignment): an
 * assigned slot shows the contact's actual avatar/photo with a small colored key badge, so it
 * reads as a contacts feature rather than a second keypad. Tapping any key shows a confirm dialog
 * before handing off to the contact picker, whether that key is empty or already assigned. A
 * "Speed dial list" button opens the separate management screen where assigned entries are
 * actually called or removed. */
@Composable
fun SpeedDialScreen(onBack: () -> Unit, onAssignClick: (String) -> Unit, onListClick: () -> Unit) {
    val context = LocalContext.current
    remember { SpeedDialPreferences.initialize(context) }
    val entries by SpeedDialPreferences.entries
    var confirmingKey by remember { mutableStateOf<String?>(null) }

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
                text = stringResource(R.string.speed_dial_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
        }

        Text(
            text = stringResource(R.string.tap_on_number_hint),
            color = primaryAccentColor(),
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            speedDialKeys.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        SpeedDialKey(
                            dialKey = key,
                            entry = entries[key],
                            onClick = { confirmingKey = key }
                        )
                    }
                }
            }
        }

        Button(
            onClick = onListClick,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp).height(48.dp)
        ) {
            Text(stringResource(R.string.speed_dial_list_title), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }

    confirmingKey?.let { key ->
        AlertDialog(
            onDismissRequest = { confirmingKey = null },
            title = { Text(stringResource(R.string.speed_dial_title)) },
            text = { Text(stringResource(R.string.set_speed_dial_number_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingKey = null
                    onAssignClick(key)
                }) {
                    Text(stringResource(R.string.action_set))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingKey = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun SpeedDialKey(dialKey: String, entry: SpeedDialEntry?, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(58.dp).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (entry != null) {
                ContactAvatar(name = entry.name, photoUri = entry.photoUri, size = 50.dp)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(speedDialColorFor(dialKey)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dialKey,
                        color = Color.White,
                        // Same asterisk-glyph-is-tiny fix as the Keypad's own digit buttons, plus
                        // a downward nudge since its ink sits higher in the glyph box than a
                        // digit's does — without it, "*" reads as off-center even though the Text
                        // itself is centered.
                        fontSize = if (dialKey == "*") 14.sp else 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = if (dialKey == "*") Modifier.offset(y = 2.dp) else Modifier
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dialKey,
                        fontSize = if (dialKey == "*") 26.sp else 19.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (dialKey == "*") Modifier.offset(y = 3.5.dp) else Modifier
                    )
                }
            }
        }
        Text(
            text = entry?.name?.substringBefore(" ") ?: stringResource(R.string.empty_label),
            fontSize = 10.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}
