package com.example.contactapp.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
import com.example.contactapp.R
import com.example.contactapp.domain.model.CallLogItem
import com.example.contactapp.domain.model.CallType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItem(call: CallLogItem, onLongClick: () -> Unit = {}) {
    val context = LocalContext.current
    val timeString = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(call.timestamp))
    val dateString = SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(call.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = getCallIcon(call.type),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = getCallColor(call.type)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column {
            val typeText = when(call.type) {
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
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$dateString • $timeString",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
