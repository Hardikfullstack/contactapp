package com.phone.contact.call.dialer.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phone.contact.call.dialer.R
import com.phone.contact.call.dialer.ui.theme.LocalIsDarkTheme
import com.phone.contact.call.dialer.ui.theme.PrimaryGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactSheet(
    phoneNumber: String,
    onSave: (String, String, Boolean) -> Unit,
    onMoreDetailsClick: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf(phoneNumber) }
    var isFavorite by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }
    var numberError by remember { mutableStateOf(false) }
    val contactSavedMessage = stringResource(R.string.contact_saved)

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
                    text = stringResource(R.string.add_to_contacts),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = stringResource(R.string.favorites),
                            // PrimaryGreen is the app's `primary` color in both the light and dark
                            // schemes (see Theme.kt), so it reads correctly in both without needing
                            // a separate white-on-badge treatment.
                            tint = if (isFavorite) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            nameError = name.isBlank()
                            numberError = number.isBlank()
                            if (!nameError && !numberError) {
                                onSave(name, number, isFavorite)
                                Toast.makeText(context, contactSavedMessage, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp)
                    ) {
                        Text(text = stringResource(R.string.save), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Name Input
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; if (it.isNotBlank()) nameError = false },
                label = { Text(stringResource(R.string.name_label)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(stringResource(R.string.field_required)) }
                } else null
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Phone Input — always editable, whether it started blank (typed manually from this
            // sheet) or pre-filled from the Keypad's typed number. Previously, a pre-filled number
            // rendered as a plain, non-editable Text() with no way to correct a mis-dialed digit.
            OutlinedTextField(
                value = number,
                onValueChange = { number = it; if (it.isNotBlank()) numberError = false },
                label = { Text(stringResource(R.string.phone_label)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                ),
                isError = numberError,
                supportingText = if (numberError) {
                    { Text(stringResource(R.string.field_required)) }
                } else null
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.saving_to_device_only),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onMoreDetailsClick(name, number, isFavorite) },
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
