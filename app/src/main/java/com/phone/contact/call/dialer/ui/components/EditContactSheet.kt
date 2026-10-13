package com.phone.contact.call.dialer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phone.contact.call.dialer.R
import com.phone.contact.call.dialer.ui.theme.LocalIsDarkTheme
import com.phone.contact.call.dialer.ui.theme.PrimaryGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditContactSheet(
    initialName: String,
    initialNumber: String,
    onSave: (String, String) -> Unit,
    onMoreDetailsClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var number by remember { mutableStateOf(initialNumber) }
    var nameError by remember { mutableStateOf(false) }
    var numberError by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.edit_contact_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = {
                        nameError = name.isBlank()
                        numberError = number.isBlank()
                        if (!nameError && !numberError) {
                            onSave(name, number)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    Text(text = stringResource(R.string.save), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
                }
            }

            // Form
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; if (it.isNotBlank()) nameError = false },
                label = { Text(stringResource(R.string.name_label)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(stringResource(R.string.field_required)) }
                } else null
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = number,
                onValueChange = { number = it; if (it.isNotBlank()) numberError = false },
                label = { Text(stringResource(R.string.phone_label)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                ),
                isError = numberError,
                supportingText = if (numberError) {
                    { Text(stringResource(R.string.field_required)) }
                } else null
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onMoreDetailsClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.surface else Color(0xFFF3F3F3)
                ),
                border = BorderStroke(1.dp, PrimaryGreen),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(text = stringResource(R.string.more_details), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
