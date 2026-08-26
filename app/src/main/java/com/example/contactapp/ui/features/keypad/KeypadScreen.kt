package com.example.contactapp.ui.features.keypad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.example.contactapp.R
import com.example.contactapp.ui.components.AddContactSheet
import com.example.contactapp.ui.components.CommonHeader
import com.example.contactapp.ui.components.HeaderActionButton
import com.example.contactapp.ui.features.keypad.components.BottomDialActions
import com.example.contactapp.ui.features.keypad.components.DialPad
import com.example.contactapp.ui.features.keypad.components.KeypadActions
import com.example.contactapp.ui.features.keypad.components.NumberDisplay
import com.example.contactapp.ui.theme.PrimaryGreen
import com.example.contactapp.util.BuiltInWallpapers
import com.example.contactapp.util.CallUtils
import com.example.contactapp.util.MessageUtils
import com.example.contactapp.util.WallpaperSelection
import com.example.contactapp.util.getAvatarColor

@Composable
fun KeypadScreen(
    viewModel: KeypadViewModel = hiltViewModel(),
    onSearchClick: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    preferenceManager: com.example.contactapp.util.PreferenceManager
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val selection by preferenceManager.wallpaperSelectionFlow.collectAsState(
        initial = preferenceManager.getCallWallpaperSelection()
    )
    val hasWallpaper = selection !is WallpaperSelection.None

    LaunchedEffect(Unit) {
        viewModel.refreshSettings()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Wallpaper Background for Keypad
        if (hasWallpaper) {
            when (val sel = selection) {
                is WallpaperSelection.SolidColor -> {
                    Box(modifier = Modifier.fillMaxSize().background(Color(sel.colorArgb)))
                }
                is WallpaperSelection.BuiltIn -> {
                    val resId = BuiltInWallpapers.findById(sel.id)?.resId
                    if (resId != null) {
                        AsyncImage(
                            model = resId,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alpha = 0.6f // Subtle for keypad readability
                        )
                    }
                }
                is WallpaperSelection.Device -> {
                    AsyncImage(
                        model = sel.uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.6f // Subtle for keypad readability
                    )
                }
                is WallpaperSelection.None -> Unit
            }
            // Scrim
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header - Fixed at top
            CommonHeader(
                title = stringResource(R.string.phone),
                onBackClick = onBackClick,
                actions = {
                    HeaderActionButton(
                        icon = Icons.Outlined.Search,
                        onClick = onSearchClick
                    )
                }
            )

            // Result Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                KeypadActions(
                    visible = uiState.typedNumber.isNotEmpty(),
                    onCreateContact = { viewModel.showAddContactSheet(true) },
                    onAddToContact = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_INSERT_OR_EDIT).apply {
                            type = android.provider.ContactsContract.Contacts.CONTENT_ITEM_TYPE
                            putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, uiState.typedNumber)
                        }
                        context.startActivity(intent)
                    },
                    onSendMessage = {
                        MessageUtils.sendMessage(context, uiState.typedNumber)
                    }
                )

                // Search Results
                uiState.searchResults.forEach { contact ->
                    Surface(
                        onClick = { CallUtils.makeCall(context, contact.number) },
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(getAvatarColor(contact.name)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = contact.name.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = contact.number,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                color = if (hasWallpaper) MaterialTheme.colorScheme.surface.copy(alpha = 0.9f) else MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(1.dp))

                    NumberDisplay(number = uiState.displayNumber)

                    Spacer(modifier = Modifier.height(4.dp))

                    DialPad(
                        onDigitClick = { viewModel.onDigitPressed(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    BottomDialActions(
                        hasNumber = uiState.typedNumber.isNotEmpty(),
                        onCallClick = {
                            CallUtils.makeCall(context, uiState.typedNumber)
                        },
                        onBackspaceClick = {
                            viewModel.onBackspace()
                        },
                        onClearAllClick = {
                            viewModel.clearAll()
                        }
                    )

                    // Ergonomic space at the bottom (inside surface for color continuity)
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .height(20.dp)
                    )
                }
            }
        }
    }

    if (uiState.showAddContactSheet) {
        AddContactSheet(
            phoneNumber = uiState.typedNumber,
            onSave = { name, _ ->
                viewModel.validateAndSaveContact(name)
            },
            onMoreDetailsClick = { name, number ->
                val intent = android.content.Intent(android.content.Intent.ACTION_INSERT_OR_EDIT).apply {
                    type = android.provider.ContactsContract.Contacts.CONTENT_ITEM_TYPE
                    putExtra(android.provider.ContactsContract.Intents.Insert.NAME, name)
                    putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, number)
                }
                context.startActivity(intent)
                viewModel.showAddContactSheet(false)
            },
            onDismiss = { viewModel.showAddContactSheet(false) }
        )
    }

    if (uiState.duplicateContact != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.clearDuplicateState() },
            title = { Text(stringResource(R.string.duplicate_number)) },
            text = { 
                Text(stringResource(R.string.duplicate_number_warning, uiState.duplicateContact?.name ?: "")) 
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.confirmSaveDuplicate() }) {
                    Text(stringResource(R.string.save_anyway), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.clearDuplicateState() }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp)
        )
    }
}
