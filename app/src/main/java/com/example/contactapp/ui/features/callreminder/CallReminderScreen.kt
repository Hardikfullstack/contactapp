package com.example.contactapp.ui.features.callreminder

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.contactapp.R
import com.example.contactapp.ui.components.CommonHeader
import com.example.contactapp.ui.components.ContactAvatarImage
import com.example.contactapp.ui.theme.PrimaryGreen
import com.example.contactapp.util.CallReminder
import com.example.contactapp.util.getAvatarColor
import java.util.Date

@Composable
fun CallReminderScreen(
    onBack: () -> Unit,
    onAddReminder: () -> Unit,
    viewModel: CallReminderViewModel = hiltViewModel()
) {
    val reminders by viewModel.reminders.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddReminder,
                containerColor = PrimaryGreen,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_reminder))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .statusBarsPadding()
        ) {
            CommonHeader(
                title = stringResource(R.string.call_reminders),
                onBackClick = onBack
            )

            if (reminders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.no_call_reminders),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(reminders, key = { it.id }) { reminder ->
                        ReminderRow(
                            reminder = reminder,
                            onCancel = { viewModel.cancelReminder(reminder) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(reminder: CallReminder, onCancel: () -> Unit) {
    val context = LocalContext.current
    val dateText = DateFormat.getDateFormat(context).format(Date(reminder.timeMillis))
    val timeText = DateFormat.getTimeFormat(context).format(Date(reminder.timeMillis))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(getAvatarColor(reminder.contactName)),
                contentAlignment = Alignment.Center
            ) {
                if (reminder.photoUri != null) {
                    ContactAvatarImage(photoUri = reminder.photoUri, modifier = Modifier.fillMaxSize())
                } else {
                    Text(
                        text = reminder.contactName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.contactName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$dateText • $timeText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.cancel_reminder),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
