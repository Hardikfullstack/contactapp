package com.example.contactapp.ui.features.keypad.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonAddAlt1
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.contactapp.R
import com.example.contactapp.ui.components.KeypadActionRow

@Composable
fun KeypadActions(
    visible: Boolean,
    onCreateContact: () -> Unit,
    onAddToContact: () -> Unit,
    onSendMessage: () -> Unit
) {
    if (visible) {
        Column {
            KeypadActionRow(
                icon = Icons.Outlined.PersonAdd,
                text = stringResource(R.string.create_new_contact),
                onClick = onCreateContact
            )

            KeypadActionRow(
                icon = Icons.Outlined.PersonAddAlt1,
                text = stringResource(R.string.add_to_contact),
                onClick = onAddToContact
            )

            KeypadActionRow(
                icon = Icons.Outlined.Sms,
                text = stringResource(R.string.send_message),
                onClick = onSendMessage
            )
        }
    }
}
