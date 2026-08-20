package com.example.contactapp.ui.components

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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.contactapp.R
import com.example.contactapp.domain.model.CallLogItem
import com.example.contactapp.domain.model.CallType
import com.example.contactapp.util.getAvatarColor
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
                .background(if (call.isBlocked) MaterialTheme.colorScheme.surfaceVariant else getAvatarColor(displayName)),
            contentAlignment = Alignment.Center
        ) {
            if (call.isBlocked) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = "Blocked",
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
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
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
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = getCallIcon(call.type),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = getCallColor(call.type)
                )
                Spacer(modifier = Modifier.width(4.dp))
                val typeString = when(call.type) {
                    CallType.INCOMING -> stringResource(R.string.incoming)
                    CallType.OUTGOING -> stringResource(R.string.outgoing)
                    CallType.MISSED -> stringResource(R.string.missed)
                    CallType.REJECTED -> stringResource(R.string.rejected)
                    CallType.SPAM -> stringResource(R.string.spam_call)
                    else -> ""
                }
                Text(
                    text = "$typeString • $timeString",
                    style = MaterialTheme.typography.bodySmall,
                    color = getCallColor(call.type),
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        else -> Icons.Default.Call
    }
}

@Composable
fun getCallColor(type: CallType): Color {
    return when (type) {
        CallType.MISSED, CallType.REJECTED, CallType.SPAM -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
