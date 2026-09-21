package com.phone.contact.call.dialer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phone.contact.call.dialer.R

/** The audio outputs a call can be routed to — a UI-level model shared by the real in-call screen
 * (backed by Telecom's CallAudioState route bitmask) and the fake-call screen (backed by plain
 * AudioManager calls, since a fake call has no real Telecom audio session) so both can show the
 * exact same "Continue with" picker despite routing through different mechanisms underneath. */
enum class AudioRouteOption { EARPIECE, SPEAKER, BLUETOOTH, WIRED_HEADSET }

private fun AudioRouteOption.icon(): ImageVector = when (this) {
    AudioRouteOption.EARPIECE -> Icons.Default.Call
    AudioRouteOption.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
    AudioRouteOption.BLUETOOTH -> Icons.Default.Bluetooth
    AudioRouteOption.WIRED_HEADSET -> Icons.Default.Headset
}

@Composable
private fun AudioRouteOption.label(): String = when (this) {
    AudioRouteOption.EARPIECE -> stringResource(R.string.ear_piece_normal_call)
    AudioRouteOption.SPEAKER -> stringResource(R.string.speaker)
    AudioRouteOption.BLUETOOTH -> stringResource(R.string.bluetooth)
    AudioRouteOption.WIRED_HEADSET -> stringResource(R.string.wired_headset)
}

/**
 * "Continue with" bottom sheet — shown instead of a plain Speaker toggle once a Bluetooth device
 * is available, matching the reference app's own call screen. Only lists [availableRoutes] (e.g.
 * a wired headset row only appears while one is actually plugged in), with [selectedRoute] ticked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioRouteSheet(
    availableRoutes: List<AudioRouteOption>,
    selectedRoute: AudioRouteOption?,
    onSelect: (AudioRouteOption) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.continue_with),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                }
            }
            availableRoutes.forEachIndexed { index, option ->
                Surface(
                    onClick = { onSelect(option) },
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = option.icon(),
                            contentDescription = null,
                            tint = if (option == selectedRoute) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = option.label(),
                            modifier = Modifier.weight(1f),
                            color = if (option == selectedRoute) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (option == selectedRoute) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (index != availableRoutes.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
