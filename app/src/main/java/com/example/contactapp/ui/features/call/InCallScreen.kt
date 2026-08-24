package com.example.contactapp.ui.features.call

import android.telecom.Call
import android.telecom.CallAudioState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.contactapp.R
import com.example.contactapp.service.CallManager
import com.example.contactapp.ui.components.CallWallpaperBackground
import com.example.contactapp.ui.components.ContactAvatarImage
import com.example.contactapp.ui.components.SwipeUpCallButton
import com.example.contactapp.ui.components.lightened
import com.example.contactapp.ui.components.toComposeShape
import com.example.contactapp.ui.features.keypad.components.DialPad
import com.example.contactapp.util.CallAccentColors
import com.example.contactapp.util.CallButtonShape
import com.example.contactapp.util.CallTheme
import com.example.contactapp.util.WallpaperSelection
import com.example.contactapp.util.getAvatarColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InCallScreen(
    contactName: String?,
    number: String,
    photoUri: String?,
    onHangup: () -> Unit,
    onDecline: () -> Unit,
    onAnswer: () -> Unit,
    onSendQuickReply: (String) -> Unit = {},
    onReportSpam: (String) -> Unit = {},
    isSpam: Boolean = false,
    audioState: CallAudioState? = null,
    onToggleMute: () -> Unit = {},
    onToggleSpeaker: () -> Unit = {},
    onPlayDtmf: (Char) -> Unit = {},
    onStopDtmf: () -> Unit = {},
    canAddCall: Boolean = false,
    secondaryCallNumber: String? = null,
    onAddCall: (String) -> Unit = {},
    onEndSecondaryCall: () -> Unit = {},
    isRecording: Boolean = false,
    recordingSeconds: Int = 0,
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    selection: WallpaperSelection,
    theme: CallTheme = CallTheme(CallAccentColors.findById("green").color, CallButtonShape.CIRCLE)
) {
    val callState by CallManager.callState.collectAsState()

    val displayName = contactName ?: number.ifBlank { stringResource(R.string.unknown) }
    val isRinging = callState == Call.STATE_RINGING
    val isActive = callState == Call.STATE_ACTIVE
    val isOnHold = callState == Call.STATE_HOLDING
    val isDialing = callState == Call.STATE_DIALING || callState == Call.STATE_CONNECTING

    var buttonsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { buttonsVisible = true }

    var showQuickReplySheet by remember { mutableStateOf(false) }
    var showKeypad by remember { mutableStateOf(false) }
    var showAddCallSheet by remember { mutableStateOf(false) }
    var showRecordingDisclaimer by remember { mutableStateOf(false) }
    val hasSecondaryCall = secondaryCallNumber != null

    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(isActive) {
        if (isActive) {
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        } else {
            elapsedSeconds = 0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Wallpaper Background (falls back to a flat dark base when none selected)
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212)))
        CallWallpaperBackground(selection = selection, modifier = Modifier.fillMaxSize())
        // Subtle top/bottom scrim so text/controls stay legible over any photo or wallpaper.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.45f),
                    0.35f to Color.Transparent,
                    0.75f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.55f)
                )
            )
        )

        // Small floating recording indicator, independent of the control tray below.
        if (isRecording) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(formatTimer(recordingSeconds), color = Color.White, fontSize = 13.sp)
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(top = 80.dp)
            ) {
                Text(
                    text = getCallStateText(callState),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.75f)
                )

                Spacer(modifier = Modifier.height(20.dp))

                CallAvatar(
                    photoUri = photoUri,
                    name = displayName,
                    accentColor = theme.accentColor,
                    pulsing = isRinging || isDialing
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = displayName,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                if (contactName != null && number.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = number,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                if (isActive || isOnHold) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isOnHold) stringResource(R.string.on_hold) else formatTimer(elapsedSeconds),
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                if (hasSecondaryCall) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = secondaryCallNumber ?: "",
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onEndSecondaryCall) {
                                Text(stringResource(R.string.end_call), color = Color(0xFFFF8A80), fontSize = 13.sp)
                            }
                        }
                    }
                }

                if (isSpam) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color.Red.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.spam_warning),
                                color = Color(0xFFFF5252),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else if (isRinging) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { onReportSpam(number) }) {
                        Icon(Icons.Default.Report, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.report_spam), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    }
                }
            }

            // Ringing keeps the plain floating swipe-button layout (matches stock Android —
            // there's no bottom tray while a call is still just ringing). Everything else
            // (dialing/active/holding) sits inside one distinct rounded-top tray, visually
            // separated from the caller-info area above it, matching stock Android's in-call
            // layout — the same "surface at elevation" pattern this app already uses for the
            // Keypad screen's bottom dial pad tray.
            if (isRinging) {
                AnimatedVisibility(
                    visible = buttonsVisible,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 60.dp),
                    enter = slideInVertically(animationSpec = tween(450)) { fullHeight -> fullHeight } + fadeIn(tween(450))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Both controls require an upward swipe while ringing.
                            SwipeUpCallButton(
                                icon = Icons.Default.CallEnd,
                                color = Color(0xFFD32F2F),
                                shape = theme.buttonShape.toComposeShape(),
                                contentDescription = stringResource(R.string.decline_call),
                                onTriggered = onDecline
                            )
                            SwipeUpCallButton(
                                icon = Icons.Default.Call,
                                color = theme.accentColor,
                                shape = theme.buttonShape.toComposeShape(),
                                contentDescription = stringResource(R.string.answer),
                                onTriggered = onAnswer
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { showQuickReplySheet = true }) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.reply_with_message), color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            } else {
                AnimatedVisibility(
                    visible = buttonsVisible,
                    modifier = Modifier.fillMaxWidth(),
                    enter = slideInVertically(animationSpec = tween(450)) { fullHeight -> fullHeight } + fadeIn(tween(450))
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = Color(0xFF1E1E1E)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 20.dp)
                                .navigationBarsPadding(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                CallControlButton(
                                    icon = Icons.Default.Dialpad,
                                    label = stringResource(R.string.keypad),
                                    active = showKeypad,
                                    onClick = { showKeypad = true }
                                )
                                CallControlButton(
                                    icon = if (audioState?.isMuted == true) Icons.Default.MicOff else Icons.Default.Mic,
                                    label = stringResource(R.string.mute),
                                    active = audioState?.isMuted == true,
                                    onClick = onToggleMute
                                )
                                CallControlButton(
                                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                                    label = stringResource(R.string.speaker),
                                    active = audioState?.route == CallAudioState.ROUTE_SPEAKER,
                                    onClick = onToggleSpeaker
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Second row — Add call / Record. Flat and always visible rather than
                            // hidden behind a "More" toggle: fewer taps, nothing to discover, and
                            // no extra animated panel to manage.
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                CallControlButton(
                                    icon = Icons.Default.PersonAdd,
                                    label = stringResource(R.string.add_call),
                                    active = false,
                                    enabled = canAddCall,
                                    onClick = { showAddCallSheet = true }
                                )
                                CallControlButton(
                                    icon = if (isRecording) Icons.Default.Stop else Icons.Default.Circle,
                                    label = stringResource(if (isRecording) R.string.stop_recording else R.string.record_call),
                                    active = isRecording,
                                    onClick = {
                                        if (isRecording) onStopRecording() else showRecordingDisclaimer = true
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            CallActionButtons(
                                icon = Icons.Default.CallEnd,
                                color = Color(0xFFD32F2F),
                                shape = theme.buttonShape.toComposeShape(),
                                onClick = onHangup,
                                label = stringResource(R.string.end_call)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQuickReplySheet) {
        QuickReplySheet(
            onSelect = { message ->
                showQuickReplySheet = false
                onSendQuickReply(message)
            },
            onDismiss = { showQuickReplySheet = false }
        )
    }

    if (showKeypad) {
        DtmfKeypadSheet(
            onDigit = onPlayDtmf,
            onDigitReleased = onStopDtmf,
            onDismiss = { showKeypad = false }
        )
    }

    if (showAddCallSheet) {
        AddCallSheet(
            onCall = { number ->
                showAddCallSheet = false
                onAddCall(number)
            },
            onDismiss = { showAddCallSheet = false }
        )
    }

    if (showRecordingDisclaimer) {
        AlertDialog(
            onDismissRequest = { showRecordingDisclaimer = false },
            title = { Text(stringResource(R.string.recording_disclaimer_title)) },
            text = { Text(stringResource(R.string.recording_disclaimer_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showRecordingDisclaimer = false
                    onStartRecording()
                }) {
                    Text(stringResource(R.string.start_recording))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecordingDisclaimer = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCallSheet(onCall: (String) -> Unit, onDismiss: () -> Unit) {
    var number by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.add_call),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = number.ifEmpty { stringResource(R.string.enter_number) },
                style = MaterialTheme.typography.headlineSmall,
                color = if (number.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 20.dp)
            )
            DialPad(onDigitClick = { digit -> number += digit })
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { if (number.isNotBlank()) onCall(number) },
                enabled = number.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp)
            ) {
                Icon(Icons.Default.Call, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_call))
            }
        }
    }
}

@Composable
fun CallAvatar(
    photoUri: String?,
    name: String,
    accentColor: Color,
    pulsing: Boolean,
    modifier: Modifier = Modifier
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        // The animation itself (not just its visual output) is gated behind `pulsing` so it
        // doesn't run at all outside the ringing/dialing states.
        if (pulsing) {
            val infiniteTransition = rememberInfiniteTransition(label = "avatarPulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "avatarPulseScale"
            )
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "avatarPulseAlpha"
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = pulseAlpha))
            )
        }
        Box(
            modifier = Modifier
                .size(120.dp)
                .border(3.dp, accentColor, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(if (photoUri != null) Color.Transparent else getAvatarColor(name)),
            contentAlignment = Alignment.Center
        ) {
            if (photoUri != null) {
                ContactAvatarImage(photoUri = photoUri, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CallControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = if (active) Color.White else Color.White.copy(alpha = 0.16f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) Color(0xFF1A1A1A) else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickReplySheet(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val messages = listOf(
        stringResource(R.string.quick_reply_1),
        stringResource(R.string.quick_reply_2),
        stringResource(R.string.quick_reply_3),
        stringResource(R.string.quick_reply_4)
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.reply_with_message),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            messages.forEach { message ->
                Surface(
                    onClick = { onSelect(message) },
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DtmfKeypadSheet(
    onDigit: (Char) -> Unit,
    onDigitReleased: () -> Unit,
    onDismiss: () -> Unit
) {
    var typed by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = typed.ifEmpty { " " },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 20.dp)
            )
            DialPad(
                onDigitClick = { digit ->
                    typed += digit
                    val tone = digit.firstOrNull()
                    if (tone != null) {
                        onDigit(tone)
                        // DialPad only exposes a click (not press/release), so a DTMF tone is
                        // played as a brief fixed-length burst per tap rather than sustained for
                        // however long the user physically holds the key down.
                        scope.launch {
                            delay(150)
                            onDigitReleased()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun CallActionButtons(
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    label: String,
    shape: Shape = CircleShape
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(72.dp)
                .shadow(elevation = 10.dp, shape = shape, ambientColor = color, spotColor = color),
            shape = shape,
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(color.lightened(), color))),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, color = Color.White, fontSize = 12.sp)
    }
}

private fun formatTimer(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}

@Composable
private fun getCallStateText(state: Int): String {
    return when (state) {
        Call.STATE_ACTIVE -> stringResource(R.string.ongoing_call)
        Call.STATE_DIALING -> stringResource(R.string.dialing)
        Call.STATE_RINGING -> stringResource(R.string.incoming_call_status)
        Call.STATE_CONNECTING -> stringResource(R.string.connecting)
        Call.STATE_HOLDING -> stringResource(R.string.on_hold)
        Call.STATE_DISCONNECTED -> stringResource(R.string.disconnected)
        else -> stringResource(R.string.unknown)
    }
}
