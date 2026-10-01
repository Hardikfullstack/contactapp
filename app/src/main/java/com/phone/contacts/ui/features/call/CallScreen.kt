package com.phone.contacts.ui.features.call

import android.telecom.Call
import android.telecom.CallAudioState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhonePaused
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
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
import com.phone.contacts.ui.components.CallWallpaperBackground
import com.phone.contacts.ui.screens.ContactAvatar
import com.phone.contacts.util.MessageUtils
import com.phone.contacts.util.QuickResponsePreferences
import com.phone.contacts.util.QuickResponseTemplate
import com.phone.contacts.util.WallpaperPreferences
import com.phone.contacts.util.WallpaperSelection
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
    showConferenceInMainDisplay: Boolean = false,
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
    onAnswerAndEndOther: () -> Unit = {},
    onSwap: () -> Unit = {},
    onMerge: () -> Unit = {},
    onEndSecondary: () -> Unit = {},
    canAddCall: Boolean = false,
    conferenceChildren: List<Call> = emptyList(),
    onDisconnectParticipant: (Call) -> Unit = {},
    callButtonStyle: CallButtonStyle = CallButtonStyle.SLIDER,
    swapCallButtons: Boolean = false
) {
    val context = LocalContext.current
    var contact by remember { mutableStateOf<Contact?>(null) }
    var resolvedNumber by remember { mutableStateOf(number) }
    LaunchedEffect(number) {
        // A call's own number can transiently go blank right as it disconnects (Telecom clears
        // call details before the call is actually removed) — re-resolving on that blank number
        // would wipe the already-resolved name and flash "Unknown Caller" for a frame right before
        // the screen closes. Keep showing whatever was last resolved instead.
        if (number.isBlank()) return@LaunchedEffect
        val fetched = ContactRepository.findContactByNumber(context, number)
        contact = fetched
        resolvedNumber = fetched?.number ?: number
    }
    val displayName = if (showConferenceInMainDisplay) "Conference call" else contact?.name ?: resolvedNumber.ifBlank { "Unknown Caller" }

    val isRinging = callState == Call.STATE_RINGING
    val isDialing = callState == Call.STATE_DIALING || callState == Call.STATE_CONNECTING
    val isActive = callState == Call.STATE_ACTIVE
    val isOnHold = callState == Call.STATE_HOLDING
    val isSecondaryRinging = secondaryCallState == Call.STATE_RINGING
    val hasSecondaryConnected = !isSecondaryRinging && secondaryCallState != Call.STATE_DISCONNECTED

    var buttonsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { buttonsVisible = true }

    remember { WallpaperPreferences.initialize(context) }
    val wallpaperSelection by WallpaperPreferences.selection
    val wallpaperBlur by WallpaperPreferences.blurEnabled

    var secondaryContact by remember { mutableStateOf<Contact?>(null) }
    var resolvedSecondaryNumber by remember { mutableStateOf(secondaryNumber) }
    LaunchedEffect(secondaryNumber) {
        if (secondaryNumber.isNotBlank()) {
            val fetched = ContactRepository.findContactByNumber(context, secondaryNumber)
            secondaryContact = fetched
            resolvedSecondaryNumber = fetched?.number ?: secondaryNumber
        }
    }
    val secondaryDisplayName = if (isConference && !showConferenceInMainDisplay) {
        "Conference call"
    } else {
        secondaryContact?.name ?: resolvedSecondaryNumber.ifBlank { "Unknown Caller" }
    }
    val hasSecondaryContactName = secondaryDisplayName != resolvedSecondaryNumber && secondaryDisplayName != "Unknown Caller"

    // Call-waiting (someone calling in while already on a call) gets its own full screen instead
    // of a small banner crammed on top of the existing call's screen — the waiting caller becomes
    // the prominent display, the call already in progress becomes a small status chip.
    if (isSecondaryRinging) {
        CallWaitingScreen(
            waitingName = secondaryDisplayName,
            waitingNumber = secondaryNumber,
            waitingPhotoUri = secondaryContact?.photoUri,
            hasWaitingContactName = hasSecondaryContactName,
            activeCallLabel = "$displayName — ${if (isOnHold) "On hold" else "Active"}",
            isOnHold = isOnHold,
            wallpaperSelection = wallpaperSelection,
            wallpaperBlur = wallpaperBlur,
            onMessageWaitingCaller = { MessageUtils.sendMessage(context, secondaryNumber) },
            onAnswerAndEndOtherCall = onAnswerAndEndOther,
            onAnswerSecondaryCall = onAnswerSecondary,
            onDeclineSecondaryCall = onRejectSecondary
        )
        return
    }

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
    var showQuickResponseSheet by remember { mutableStateOf(false) }
    var showManageCallSheet by remember { mutableStateOf(false) }
    var showConferenceListSheet by remember { mutableStateOf(false) }
    val showManageCallOption = hasSecondaryConnected || isConference

    LaunchedEffect(isConference) {
        if (!isConference) {
            showConferenceListSheet = false
        }
    }

    var conferenceParticipants by remember { mutableStateOf<List<ConferenceParticipant>>(emptyList()) }
    LaunchedEffect(conferenceChildren) {
        conferenceParticipants = conferenceChildren.map { child ->
            val childNumber = child.details?.handle?.schemeSpecificPart.orEmpty()
            val childContact = ContactRepository.findContactByNumber(context, childNumber)
            ConferenceParticipant(
                call = child,
                number = childNumber,
                name = childContact?.name ?: childNumber.ifBlank { "Unknown" },
                photoUri = childContact?.photoUri
            )
        }
    }

    remember { QuickResponsePreferences.initialize(context) }
    val quickResponseTemplates by QuickResponsePreferences.templates
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) showQuickResponseSheet = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        CallWallpaperBackground(selection = wallpaperSelection, blurEnabled = wallpaperBlur, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 64.dp)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showConferenceInMainDisplay) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                } else {
                    val avatarContact = remember(contact, displayName, resolvedNumber) {
                        contact ?: Contact(id = "", name = displayName, number = resolvedNumber)
                    }
                    ContactAvatar(contact = avatarContact, size = 96.dp)
                }

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
                if (resolvedNumber.isNotBlank() && displayName != resolvedNumber) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = resolvedNumber,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.size(8.dp))

                Text(
                    text = when {
                        isActive -> formatDuration(elapsedSeconds)
                        isOnHold -> "On hold"
                        isRinging -> "Incoming call"
                        isDialing -> "Dialing…"
                    else -> "Call Ended"
                },
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.75f)
            )

            if (hasSecondaryConnected) {
                Spacer(modifier = Modifier.size(16.dp))
                Surface(
                    onClick = onSwap,
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhonePaused,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = "$secondaryDisplayName - On hold",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onEndSecondary) {
                            Text("End call", color = Color(0xFFFF8A80), fontSize = 13.sp)
                        }
                    }
                }
            }
        }

            Spacer(modifier = Modifier.weight(1f))

            if (isRinging) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = buttonsVisible,
                    modifier = Modifier.fillMaxWidth(),
                    enter = androidx.compose.animation.slideInVertically(animationSpec = androidx.compose.animation.core.tween(450)) { it } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(450))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            if (MessageUtils.hasSendSmsPermission(context)) {
                                showQuickResponseSheet = true
                            } else {
                                smsPermissionLauncher.launch(android.Manifest.permission.SEND_SMS)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Message,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = "Message", color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Medium)
                }

                if (callButtonStyle.isSlider) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 56.dp),
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 56.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val declineButton = @Composable {
                            callButtonStyle.decline?.let { face ->
                                IncomingCallButton(
                                    face = face,
                                    icon = Icons.Filled.CallEnd,
                                    contentDescription = "Decline",
                                    enableSwipeGesture = callButtonStyle.hasSwipeGesture,
                                    onClick = onDecline
                                )
                            }
                        }
                        val acceptButton = @Composable {
                            callButtonStyle.accept?.let { face ->
                                IncomingCallButton(
                                    face = face,
                                    icon = Icons.Filled.Call,
                                    contentDescription = "Answer",
                                    enableSwipeGesture = callButtonStyle.hasSwipeGesture,
                                    onClick = onAnswer
                                )
                            }
                        }
                        if (swapCallButtons) {
                            acceptButton()
                            declineButton()
                        } else {
                            declineButton()
                            acceptButton()
                        }
                    }
                }
                    }
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
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(32.dp))
                        .padding(vertical = 32.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Once a second call exists (or the calls are already merged into a
                        // conference), this same slot switches from "Add call" to "Manage call" —
                        // same as the reference/stock dialer, which drops "Add call" the moment a
                        // second call is already up, since it can't take a third that way. Hold
                        // stays its own persistent button either way — it's single-call and
                        // unaffected by a second call existing.
                        CallControlButton(
                            icon = if (showManageCallOption) Icons.Filled.PhoneInTalk else Icons.Filled.Add,
                            label = if (showManageCallOption) "Manage call" else "Add call",
                            active = false,
                            enabled = if (showManageCallOption) true else canAddCall,
                            onClick = { if (showManageCallOption) showManageCallSheet = true else onAddCallClick() }
                        )
                        CallControlButton(
                            icon = if (isOnHold) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            label = "Hold call",
                            active = isOnHold,
                            // Must stay enabled while on hold too — otherwise, once the call
                            // is actually held (callState flips to STATE_HOLDING, so isActive
                            // becomes false), this button disables itself right when it's
                            // needed to resume the call.
                            enabled = isActive || isOnHold,
                            onClick = onToggleHold
                        )
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

    if (showQuickResponseSheet) {
        QuickResponseSheet(
            templates = quickResponseTemplates,
            onSelect = { template ->
                showQuickResponseSheet = false
                if (MessageUtils.sendSmsDirectly(context, number, template.text)) {
                    onDecline()
                }
            },
            onDismiss = { showQuickResponseSheet = false }
        )
    }

    if (showManageCallSheet) {
        ManageCallSheet(
            isConference = isConference,
            hasSecondaryCall = hasSecondaryConnected,
            canMerge = canMerge,
            canAddCall = canAddCall,
            onSwap = {
                showManageCallSheet = false
                onSwap()
            },
            onMerge = {
                showManageCallSheet = false
                onMerge()
            },
            onConferenceList = {
                showManageCallSheet = false
                showConferenceListSheet = true
            },
            onAddCall = {
                showManageCallSheet = false
                onAddCallClick()
            },
            onDismiss = { showManageCallSheet = false }
        )
    }

    if (showConferenceListSheet) {
        ConferenceListSheet(
            participants = conferenceParticipants,
            onDisconnect = onDisconnectParticipant,
            onDismiss = { showConferenceListSheet = false }
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
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.35f)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // A plain color-tinted icon on this same translucent-glass panel read as washed out —
        // matching how a real phone call screen highlights an engaged toggle, active state gets
        // a solid filled circle behind the icon instead, with the icon flipping to a contrasting
        // color on top of it.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = label,
            color = if (active) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp
        )
    }
}

/** One accept/decline circle for the non-slider [CallButtonStyle]s — solid color when
 * [ButtonFace.background] has one entry, a top-to-bottom gradient when it has two, matching how
 * the reference app's own icon assets are built. When [enableSwipeGesture] is true (only
 * [CallButtonStyle.STYLE_6]), it also shows the animated up-chevron hint and lets dragging it up
 * past ~40% of [maxTravel] trigger [onClick], matching the reference app's own drag-to-answer
 * touch handling for that one style. Every other style is plain tap-only, no chevron. */
@Composable
internal fun IncomingCallButton(
    face: ButtonFace,
    icon: ImageVector,
    contentDescription: String,
    enableSwipeGesture: Boolean,
    onClick: () -> Unit
) {
    val brush = if (face.background.size > 1) {
        Brush.verticalGradient(face.background)
    } else {
        Brush.verticalGradient(listOf(face.background[0], face.background[0]))
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (enableSwipeGesture) {
            SwipeUpChevrons(color = face.background.last())
            Spacer(modifier = Modifier.size(4.dp))
            val maxTravel = with(LocalDensity.current) { 80.dp.toPx() }
            val offsetY = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()
            var triggered by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, offsetY.value.toInt()) }
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(brush)
                    .pointerInput(triggered) {
                        if (triggered) return@pointerInput
                        detectVerticalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (offsetY.value <= -maxTravel * 0.4f) {
                                        triggered = true
                                        onClick()
                                    } else {
                                        offsetY.animateTo(0f)
                                    }
                                }
                            },
                            onDragCancel = { scope.launch { offsetY.animateTo(0f) } },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    offsetY.snapTo((offsetY.value + dragAmount).coerceIn(-maxTravel, 0f))
                                }
                            }
                        )
                    }
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = contentDescription, tint = face.iconColor, modifier = Modifier.size(28.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(brush)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = contentDescription, tint = face.iconColor, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** A single horizontal slide track — dragging the thumb past ~75% of the track width answers the
 * call, matching the reference app's own "Slide to answer" (rather than contactapp's dual
 * swipe-up-button pattern). Releasing before the threshold springs the thumb back to the start. */
@Composable
internal fun SlideToAnswer(onAnswer: () -> Unit) {
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

/** The templates managed in Settings > Quick response — picking one here sends it as an SMS
 * straight to the ringing caller (no further confirmation) and declines the call, matching the
 * reference app's own "decline with a text" flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickResponseSheet(
    templates: List<QuickResponseTemplate>,
    onSelect: (QuickResponseTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Quick response", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    modifier = Modifier.clickable(onClick = onDismiss)
                )
            }
            templates.forEach { template ->
                Text(
                    text = template.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(template) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
        }
    }
}

data class ConferenceParticipant(
    val call: Call,
    val number: String,
    val name: String,
    val photoUri: String?
)

@Composable
private fun CallWaitingScreen(
    waitingName: String,
    waitingNumber: String,
    waitingPhotoUri: String?,
    hasWaitingContactName: Boolean,
    activeCallLabel: String,
    isOnHold: Boolean,
    wallpaperSelection: WallpaperSelection,
    wallpaperBlur: Boolean,
    onMessageWaitingCaller: () -> Unit,
    onAnswerAndEndOtherCall: () -> Unit,
    onAnswerSecondaryCall: () -> Unit,
    onDeclineSecondaryCall: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)))
        CallWallpaperBackground(selection = wallpaperSelection, blurEnabled = wallpaperBlur, modifier = Modifier.fillMaxSize())
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
                val avatarContact = remember(waitingName, waitingNumber, waitingPhotoUri) {
                    Contact(id = "", name = waitingName, number = waitingNumber, photoUri = waitingPhotoUri)
                }
                ContactAvatar(contact = avatarContact, size = 96.dp)

                Spacer(modifier = Modifier.size(16.dp))

                Text(
                    text = waitingName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                if (hasWaitingContactName && waitingNumber.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = waitingNumber, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Call waiting",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.75f)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isOnHold) Icons.Default.PhonePaused else Icons.Default.Call,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = activeCallLabel,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 40.dp).navigationBarsPadding()
            ) {
                Surface(
                    onClick = onMessageWaitingCaller,
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Message", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    onClick = onAnswerAndEndOtherCall,
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Answer and end other call", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    CallActionButtons(
                        icon = Icons.Default.CallEnd,
                        color = Color(0xFFD32F2F),
                        onClick = onDeclineSecondaryCall,
                        label = "Decline"
                    )
                    CallActionButtons(
                        icon = Icons.Default.Call,
                        color = Color(0xFF1DA463),
                        onClick = onAnswerSecondaryCall,
                        label = "Answer"
                    )
                }
            }
        }
    }
}

@Composable
private fun CallActionButtons(icon: ImageVector, color: Color, onClick: () -> Unit, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, color = Color.White)
    }
}

@Composable
private fun ManageCallRow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageCallSheet(
    isConference: Boolean,
    hasSecondaryCall: Boolean,
    canMerge: Boolean,
    canAddCall: Boolean,
    onMerge: () -> Unit,
    onSwap: () -> Unit,
    onConferenceList: () -> Unit,
    onAddCall: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = "Manage call",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            if (isConference) {
                ManageCallRow(
                    icon = Icons.Default.Groups,
                    label = "Conference list",
                    enabled = true,
                    onClick = onConferenceList
                )
                if (hasSecondaryCall) {
                    ManageCallRow(
                        icon = Icons.Default.CallMerge,
                        label = "Merge",
                        enabled = canMerge,
                        onClick = onMerge
                    )
                }
                ManageCallRow(
                    icon = Icons.Default.PersonAdd,
                    label = "Add call",
                    enabled = canAddCall || !hasSecondaryCall,
                    onClick = onAddCall
                )
            } else {
                ManageCallRow(
                    icon = Icons.Default.CallMerge,
                    label = "Merge calls",
                    enabled = canMerge,
                    onClick = onMerge
                )
                ManageCallRow(
                    icon = Icons.Default.SwapCalls,
                    label = "Swap calls",
                    enabled = true,
                    onClick = onSwap
                )
                ManageCallRow(
                    icon = Icons.Default.PersonAdd,
                    label = "Add call",
                    enabled = false,
                    onClick = {}
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConferenceListSheet(
    participants: List<ConferenceParticipant>,
    onDisconnect: (Call) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Conference list",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }
            participants.forEach { participant ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val avatarContact = remember(participant) {
                        Contact(id = "", name = participant.name, number = participant.number, photoUri = participant.photoUri)
                    }
                    ContactAvatar(contact = avatarContact, size = 44.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = participant.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (participant.name != participant.number) {
                            Text(
                                text = participant.number,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { onDisconnect(participant.call) },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFFFCDD2))
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = null, tint = Color(0xFFD32F2F))
                    }
                }
            }
        }
    }
}
