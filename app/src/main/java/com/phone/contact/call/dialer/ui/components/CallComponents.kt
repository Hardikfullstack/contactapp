package com.phone.contact.call.dialer.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallMissedOutgoing
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phone.contact.call.dialer.R
import com.phone.contact.call.dialer.domain.model.CallLogItem
import com.phone.contact.call.dialer.domain.model.CallType
import com.phone.contact.call.dialer.ui.theme.LocalIsDarkTheme
import com.phone.contact.call.dialer.util.getAvatarColor
import java.util.*

@Composable
fun CallItem(
    call: CallLogItem,
    onCallClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val timeString = DateFormat.getTimeFormat(context).format(Date(call.timestamp))
    val hasContactName = !call.name.isNullOrBlank()
    val displayName = if (hasContactName) call.name!! else call.number.ifBlank { stringResource(R.string.unknown) }
    // Two separate signals for "was this blocked": call.isBlocked is a live overlay (is this
    // number CURRENTLY in the block list), while call.type == BLOCKED is the OS's own permanent
    // record that THIS call specifically got blocked at the time — that record doesn't change
    // just because the number was unblocked afterwards, so a historical blocked entry still
    // needs to show as Blocked even after unblocking.
    val isBlockedEntry = call.isBlocked || call.type == CallType.BLOCKED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Optimized Avatar/Blocked Icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isBlockedEntry -> MaterialTheme.colorScheme.surfaceVariant
                        !hasContactName -> Color(0xFF9E9E9E)
                        else -> getAvatarColor(displayName)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isBlockedEntry) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = stringResource(R.string.blocked),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            } else if (call.photoUri != null) {
                ContactAvatarImage(
                    photoUri = call.photoUri,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (hasContactName) {
                Text(
                    text = displayName.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Normal
                )
            } else {
                // A local vector drawable (same one the notification avatar already uses for
                // this exact "unknown caller" case) instead of the material-icons-core
                // Icons.Default.Person — that runtime-built ImageVector has been observed to
                // fail to draw at all (background renders, icon glyph doesn't) on some devices,
                // while a plain AAPT-compiled drawable resource doesn't hit that path.
                Icon(
                    painter = painterResource(R.drawable.ic_unknown_person),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Name & Status Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
                color = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.onSurface else Color(0xFF020202),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val isMissedOrRejected = isBlockedEntry || call.type == CallType.MISSED || call.type == CallType.REJECTED
            val statusColor = if (isMissedOrRejected) {
                // Dark mode keeps the theme's own semantic error color (as it always did);
                // light mode uses the flat #F20004 red requested for this row.
                if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.error else Color(0xFFF20004)
            } else if (LocalIsDarkTheme.current) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                Color(0xFF656565)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isBlockedEntry) Icons.Default.Block else getCallIcon(call.type),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = statusColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                val typeString = if (isBlockedEntry) {
                    stringResource(R.string.blocked)
                } else when(call.type) {
                    CallType.INCOMING -> stringResource(R.string.incoming)
                    CallType.OUTGOING -> stringResource(R.string.outgoing)
                    CallType.MISSED -> stringResource(R.string.missed)
                    CallType.REJECTED -> stringResource(R.string.rejected)
                    CallType.SPAM -> stringResource(R.string.spam_call)
                    CallType.BLOCKED -> stringResource(R.string.blocked)
                    else -> ""
                }
                Text(
                    text = "$typeString • $timeString",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Call Action Button
        IconButton(
            onClick = onCallClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = stringResource(R.string.call),
                tint = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF656565),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

fun getCallIcon(type: CallType): ImageVector {
    return when (type) {
        CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
        CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
        CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed
        CallType.REJECTED -> Icons.AutoMirrored.Filled.CallMissedOutgoing
        CallType.SPAM -> Icons.Default.Report
        CallType.BLOCKED -> Icons.Default.Block
        else -> Icons.Default.Call
    }
}

@Composable
fun getCallColor(type: CallType): Color {
    return when (type) {
        CallType.MISSED, CallType.REJECTED, CallType.SPAM, CallType.BLOCKED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
