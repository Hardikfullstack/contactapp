package com.phone.contacts.ui.features.call

import android.telecom.Call
import android.telecom.CallAudioState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phone.contacts.data.Contact
import com.phone.contacts.data.ContactRepository
import com.phone.contacts.ui.screens.ContactAvatar
import com.phone.contacts.util.MessageUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CallScreen(
    number: String,
    callState: Int,
    audioState: CallAudioState?,
    connectedAtElapsedRealtime: Long?,
    secondaryNumber: String = "",
    secondaryCallState: Int = Call.STATE_DISCONNECTED,
    isConference: Boolean = false,
    canMerge: Boolean = false,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onHangup: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleHold: () -> Unit,
    onSelectAudioRoute: (Int) -> Unit,
    onToggleSpeaker: () -> Unit,
    onPlayDtmf: (Char) -> Unit,
    onStopDtmf: () -> Unit,
    onAddCallClick: () -> Unit = {},
    onAnswerSecondary: () -> Unit = {},
    onRejectSecondary: () -> Unit = {},
    onSwap: () -> Unit = {},
    onMerge: () -> Unit = {}
) {
    val context = LocalContext.current
    var contact by remember(number) { mutableStateOf<Contact?>(null) }
    LaunchedEffect(number) {
        contact = ContactRepository.findContactByNumber(context, number)
    }
    val displayName = if (isConference) "Conference call" else contact?.name ?: number.ifBlank { "Unknown Caller" }

    val isRinging = callState == Call.STATE_RINGING
    val isDialing = callState == Call.STATE_DIALING || callState == Call.STATE_CONNECTING
    val isActive = callState == Call.STATE_ACTIVE
    val isOnHold = callState == Call.STATE_HOLDING
    val isSecondaryRinging = secondaryCallState == Call.STATE_RINGING
    val hasSecondaryConnected = !isConference && !isSecondaryRinging && secondaryCallState != Call.STATE_DISCONNECTED

    var elapsedSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(connectedAtElapsedRealtime) {
        if (connectedAtElapsedRealtime == null) return@LaunchedEffect
        while (true) {
            elapsedSeconds = (android.os.SystemClock.elapsedRealtime() - connectedAtElapsedRealtime) / 1000
            delay(1000)
        }
    }

    var showKeypad by remember { mutableStateOf(false) }
    var showAudioRouteSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Compact call-waiting banner — a second call ringing in while this one is up.
            // Scoped down from the reference app's own full-screen call-waiting UI.
            if (isSecondaryRinging) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Call waiting", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        Text(
                            text = secondaryNumber.ifBlank { "Unknown" },
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0413B))
                            .clickable(onClick = onRejectSecondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1DA463))
                            .clickable(onClick = onAnswerSecondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = "Answer", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .padding(top = 64.dp)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val avatarContact = remember(contact, displayName, number) {
                    contact ?: Contact(id = "", name = displayName, number = number)
                }
                ContactAvatar(contact = avatarContact, size = 96.dp)

                Spacer(modifier = Modifier.size(16.dp))

                Text(
                    text = displayName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Only shown when there's a resolved contact name distinct from the raw number —
                // an unknown caller's displayName already IS the number, so showing it twice
                // would just repeat the same line.
                if (number.isNotBlank() && displayName != number) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = number,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.size(8.dp))

                Text(
                    text = when {
                        isActive -> formatDuration(elapsedSeconds)
                        isRinging -> "Incoming call"
                        isDialing -> "Dialing…"
                        else -> "Call Ended"
                    },
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (isRinging) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SlideToAnswer(onAnswer = onAnswer)
                    Spacer(modifier = Modifier.size(20.dp))
                    Text(
                        text = "Decline",
                        color = Color(0xFFFF6B6B),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(onClick = onDecline)
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            } else {
                val hasBluetoothRoute = (audioState?.supportedRouteMask ?: 0) and CallAudioState.ROUTE_BLUETOOTH != 0

                // One shared frosted-glass panel behind the whole button section (Add call/
                // Message/Hold call, Bluetooth/Speaker/Mute, Keypad, End call) — a translucent
                // gradient fill plus a soft light border stands in for a real backdrop blur
                // (not available via a plain Compose modifier without a blur-capable render
                // pipeline/extra library), matching the reference app's glassy look closely
                // enough. Individual buttons no longer have their own background.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f))
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(32.dp))
                        .padding(vertical = 32.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (hasSecondaryConnected) {
                            // Two live calls — Swap/Merge act on them instead of Add call/Hold,
                            // matching contactapp's own pre-merge button layout.
                            CallControlButton(
                                icon = Icons.Filled.SwapCalls,
                                label = "Swap",
                                active = false,
                                onClick = onSwap
                            )
                            CallControlButton(
                                icon = Icons.Filled.CallMerge,
                                label = "Merge",
                                active = false,
                                enabled = canMerge,
                                onClick = onMerge
                            )
                        } else {
                            CallControlButton(
                                icon = Icons.Filled.Add,
                                label = "Add call",
                                active = false,
                                enabled = isActive && !isSecondaryRinging,
                                onClick = onAddCallClick
                            )
                            CallControlButton(
                                icon = Icons.Filled.Pause,
                                label = "Hold call",
                                active = isOnHold,
                                enabled = isActive,
                                onClick = onToggleHold
                            )
                        }
                        CallControlButton(
                            icon = Icons.AutoMirrored.Filled.Message,
                            label = "Message",
                            active = false,
                            onClick = { MessageUtils.sendMessage(context, number) }
                        )
                    }

                    Spacer(modifier = Modifier.size(28.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CallControlButton(
                            icon = if (audioState?.route == CallAudioState.ROUTE_BLUETOOTH) Icons.Filled.Bluetooth else Icons.AutoMirrored.Filled.VolumeUp,
                            label = if (audioState?.route == CallAudioState.ROUTE_BLUETOOTH) "Bluetooth" else "Speaker",
                            active = audioState?.route == CallAudioState.ROUTE_SPEAKER,
                            onClick = { if (hasBluetoothRoute) showAudioRouteSheet = true else onToggleSpeaker() }
                        )
                        CallControlButton(
                            icon = if (audioState?.isMuted == true) Icons.Filled.MicOff else Icons.Filled.Mic,
                            label = "Mute",
                            active = audioState?.isMuted == true,
                            onClick = onToggleMute
                        )
                        CallControlButton(
                            icon = Icons.Filled.Dialpad,
                            label = "Keypad",
                            active = showKeypad,
                            onClick = { showKeypad = true }
                        )
                    }

                    Spacer(modifier = Modifier.size(28.dp))

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0413B))
                            .clickable(onClick = onHangup),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = "End call", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }

    if (showKeypad) {
        DtmfKeypadSheet(
            onDigit = onPlayDtmf,
            onDigitReleased = onStopDtmf,
            onDismiss = { showKeypad = false }
        )
    }

    if (showAudioRouteSheet) {
        AudioRouteSheet(
            supportedRouteMask = audioState?.supportedRouteMask ?: 0,
            currentRoute = audioState?.route,
            onSelect = { route ->
                showAudioRouteSheet = false
                onSelectAudioRoute(route)
            },
            onDismiss = { showAudioRouteSheet = false }
        )
    }
}

/** No individual background of its own — these sit directly on the shared panel background that
 * [CallScreen] draws behind the whole button section, matching the reference app. Active state is
 * shown via icon/label color instead of an inverted circle fill. */
@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Column(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.35f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(30.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = label,
            color = if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp
        )
    }
}

/** A single horizontal slide track — dragging the thumb past ~75% of the track width answers the
 * call, matching the reference app's own "Slide to answer" (rather than contactapp's dual
 * swipe-up-button pattern). Releasing before the threshold springs the thumb back to the start. */
@Composable
private fun SlideToAnswer(onAnswer: () -> Unit) {
    val thumbSizeDp = 56.dp
    val thumbSizePx = with(LocalDensity.current) { thumbSizeDp.toPx() }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var answered by remember { mutableStateOf(false) }

    val maxOffset = (trackWidthPx - thumbSizePx).coerceAtLeast(0f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(thumbSizeDp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "Slide to answer",
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .size(thumbSizeDp)
                .clip(CircleShape)
                .background(Color(0xFF1DA463))
                .pointerInput(answered) {
                    if (answered) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (maxOffset > 0f && offsetX.value >= maxOffset * 0.75f) {
                                    offsetX.animateTo(maxOffset)
                                    if (!answered) {
                                        answered = true
                                        onAnswer()
                                    }
                                } else {
                                    offsetX.animateTo(0f)
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount).coerceIn(0f, maxOffset))
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Call, contentDescription = "Slide to answer", tint = Color.White)
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private val dtmfKeys = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to ""
)

/** Live DTMF tones sent straight to the active call — distinct from KeypadScreen's own dial pad,
 * which appends digits to dial rather than sending tones on an already-connected call. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DtmfKeypadSheet(
    onDigit: (Char) -> Unit,
    onDigitReleased: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            dtmfKeys.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { (digit, letters) ->
                        DtmfKey(digit = digit, letters = letters, onDigit = onDigit, onReleased = onDigitReleased)
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
            }
            Spacer(modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun DtmfKey(digit: String, letters: String, onDigit: (Char) -> Unit, onReleased: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(digit) {
                detectTapGestures(
                    onPress = {
                        onDigit(digit.first())
                        tryAwaitRelease()
                        onReleased()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = digit, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            if (letters.isNotEmpty()) {
                Text(text = letters, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private enum class AudioRoute { EARPIECE, SPEAKER, BLUETOOTH, WIRED_HEADSET }

private fun AudioRoute.toCallAudioRoute(): Int = when (this) {
    AudioRoute.EARPIECE -> CallAudioState.ROUTE_EARPIECE
    AudioRoute.SPEAKER -> CallAudioState.ROUTE_SPEAKER
    AudioRoute.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
    AudioRoute.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
}

private fun routeToAudioRoute(route: Int?): AudioRoute? = when (route) {
    CallAudioState.ROUTE_EARPIECE -> AudioRoute.EARPIECE
    CallAudioState.ROUTE_SPEAKER -> AudioRoute.SPEAKER
    CallAudioState.ROUTE_BLUETOOTH -> AudioRoute.BLUETOOTH
    CallAudioState.ROUTE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
    else -> null
}

/** Only lists routes actually available right now (from [supportedRouteMask]) — e.g. a wired
 * headset row only appears while one is actually plugged in. Earpiece always listed last. */
private fun audioRoutesFromMask(mask: Int): List<AudioRoute> = buildList {
    if (mask and CallAudioState.ROUTE_BLUETOOTH != 0) add(AudioRoute.BLUETOOTH)
    if (mask and CallAudioState.ROUTE_WIRED_HEADSET != 0) add(AudioRoute.WIRED_HEADSET)
    if (mask and CallAudioState.ROUTE_SPEAKER != 0) add(AudioRoute.SPEAKER)
    if (mask and CallAudioState.ROUTE_EARPIECE != 0) add(AudioRoute.EARPIECE)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioRouteSheet(
    supportedRouteMask: Int,
    currentRoute: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val routes = remember(supportedRouteMask) { audioRoutesFromMask(supportedRouteMask) }
    val selected = routeToAudioRoute(currentRoute)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Continue with", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    modifier = Modifier.clickable(onClick = onDismiss)
                )
            }
            routes.forEach { route ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(route.toCallAudioRoute()) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (route) {
                        AudioRoute.EARPIECE -> Icons.Filled.Call
                        AudioRoute.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
                        AudioRoute.BLUETOOTH -> Icons.Filled.Bluetooth
                        AudioRoute.WIRED_HEADSET -> Icons.Filled.Headset
                    }
                    val label = when (route) {
                        AudioRoute.EARPIECE -> "Ear Piece (normal call)"
                        AudioRoute.SPEAKER -> "Speaker"
                        AudioRoute.BLUETOOTH -> "Bluetooth"
                        AudioRoute.WIRED_HEADSET -> "Wired Headset"
                    }
                    val tint = if (route == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    Icon(icon, contentDescription = null, tint = tint)
                    Spacer(modifier = Modifier.size(16.dp))
                    Text(text = label, modifier = Modifier.weight(1f), color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (route == selected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
        }
    }
}
