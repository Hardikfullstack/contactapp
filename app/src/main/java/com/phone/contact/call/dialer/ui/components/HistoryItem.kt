package com.phone.contact.call.dialer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phone.contact.call.dialer.R
import com.phone.contact.call.dialer.domain.model.CallLogItem
import com.phone.contact.call.dialer.domain.model.CallType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItem(call: CallLogItem, onLongClick: () -> Unit = {}) {
    val context = LocalContext.current
    val timeString = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(call.timestamp))
    val dateString = SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(call.timestamp))
    // Same two-signal check as CallItem/Recents — call.isBlocked (currently in the block list)
    // OR call.type == BLOCKED (the OS's own permanent record this specific call was blocked at
    // the time), so a historical blocked entry keeps showing as Blocked even after unblocking.
    val isBlockedEntry = call.isBlocked || call.type == CallType.BLOCKED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isBlockedEntry) Icons.Default.Block else getCallIcon(call.type),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (isBlockedEntry) MaterialTheme.colorScheme.error else getCallColor(call.type)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            val typeText = if (isBlockedEntry) {
                stringResource(R.string.blocked)
            } else when(call.type) {
                CallType.INCOMING -> stringResource(R.string.incoming_call)
                CallType.OUTGOING -> stringResource(R.string.outgoing_call)
                CallType.MISSED -> stringResource(R.string.missed_call)
                CallType.REJECTED -> stringResource(R.string.rejected_call)
                CallType.SPAM -> stringResource(R.string.spam_call)
                else -> stringResource(R.string.call)
            }
            Text(
                text = typeText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isBlockedEntry) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            // A call that got blocked/missed/rejected instantly has no real duration — only
            // append it when there actually is one, matching the reference dialer's own history
            // rows (some blocked entries show "• 11s", others show no duration at all).
            val durationText = formatCallDuration(call.durationSeconds)
            Text(
                text = if (durationText != null) "$dateString • $timeString • $durationText" else "$dateString • $timeString",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Compact "11s" / "1m 29s" / "1h 5m" style, matching the reference dialer's own call-duration
 * format — null (not "0s") when there's no real duration to show. */
private fun formatCallDuration(totalSeconds: Long): String? {
    if (totalSeconds <= 0) return null
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
        minutes > 0 -> if (seconds > 0) "${minutes}m ${seconds}s" else "${minutes}m"
        else -> "${seconds}s"
    }
}
