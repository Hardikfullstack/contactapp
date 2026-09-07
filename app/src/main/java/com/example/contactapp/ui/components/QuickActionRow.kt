package com.example.contactapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Message
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.contactapp.R

@Composable
fun QuickActionRow(
    onCallClick: () -> Unit,
    onMessageClick: () -> Unit,
    onHistoryClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = 16.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {

        QuickActionButton(
            icon = Icons.Outlined.Call,
            text = stringResource(R.string.call),
            onClick = onCallClick,
            modifier = Modifier.weight(1f)
        )

        QuickActionButton(
            icon = Icons.Outlined.Message,
            text = stringResource(R.string.message),
            onClick = onMessageClick,
            modifier = Modifier.weight(1f)
        )

        QuickActionButton(
            icon = Icons.Outlined.History,
            text = stringResource(R.string.history),
            onClick = onHistoryClick,
            modifier = Modifier.weight(1f)
        )

    }

}
