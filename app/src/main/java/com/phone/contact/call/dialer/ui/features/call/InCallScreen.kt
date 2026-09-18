package com.phone.contact.call.dialer.ui.features.call

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
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
import com.phone.contact.call.dialer.R
import com.phone.contact.call.dialer.ui.components.CallWallpaperBackground
import com.phone.contact.call.dialer.ui.components.ContactAvatarImage
import com.phone.contact.call.dialer.ui.components.SwipeUpCallButton
import com.phone.contact.call.dialer.ui.components.lightened
import com.phone.contact.call.dialer.ui.components.toComposeShape
import com.phone.contact.call.dialer.ui.features.keypad.components.DialPad
import com.phone.contact.call.dialer.util.CallAccentColors
import com.phone.contact.call.dialer.util.CallButtonShape
import com.phone.contact.call.dialer.util.CallTheme
import com.phone.contact.call.dialer.util.MessageUtils
import com.phone.contact.call.dialer.util.PhoneNumberFormatter
import com.phone.contact.call.dialer.util.WallpaperSelection
import com.phone.contact.call.dialer.util.getAvatarColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InCallScreen(
    contactName: String?,
    number: String,
    photoUri: String?,
    // The state of whichever call is currently shown "in front" — NOT necessarily always
    // Telecom's own primary call. When a just-placed Add Call leg is active/dialing while the
    // original call sits on hold, InCallActivity swaps which call's info/state is passed as the
    // "front" one here (and which is passed as the secondary*, below), so this screen always
    // matches the reference dialer's own "whichever call you're not holding is up front" rule
    // without this composable needing to know about that swap itself.
    callState: Int,
    onHangup: () -> Unit,
    onDecline: () -> Unit,
    onAnswer: () -> Unit,
    onSendQuickReply: (String) -> Unit = {},
    onReportSpam: (String) -> Unit = {},
    isSpam: Boolean = false,
    audioState: CallAudioState? = null,
    onToggleMute: () -> Unit = {},
    onToggleSpeaker: () -> Unit = {},
    canHold: Boolean = false,
    onToggleHold: () -> Unit = {},
    onPlayDtmf: (Char) -> Unit = {},
    onStopDtmf: () -> Unit = {},
    canAddCall: Boolean = false,
    canMerge: Boolean = false,
    secondaryCallNumber: String? = null,
    secondaryCallContactName: String? = null,
    secondaryCallState: Int = Call.STATE_DISCONNECTED,
    onAddCallClick: () -> Unit = {},
    onEndSecondaryCall: () -> Unit = {},
    onAnswerSecondaryCall: () -> Unit = {},
    onDeclineSecondaryCall: () -> Unit = {},
    onAnswerAndEndOtherCall: () -> Unit = {},
    onSwapCalls: () -> Unit = {},
    onMergeCalls: () -> Unit = {},
    // Whether the FRONT call specifically is the conference — false while a conference exists but
    // sits on hold as the "back" chip (e.g. a third call being added on top of it), in which case
    // the main avatar/name area should show that new front call's own info, not a generic
    // "Conference call". Separate from conferenceParticipants.isNotEmpty(), which still drives the
    // Manage Call sheet's Conference List option regardless of which one is currently in front.
    showConferenceInMainDisplay: Boolean = false,
    conferenceParticipants: List<ConferenceParticipant> = emptyList(),
    onDisconnectParticipant: (Call) -> Unit = {},
    selection: WallpaperSelection,
    theme: CallTheme = CallTheme(CallAccentColors.findById("green").color, CallButtonShape.CIRCLE)
) {
    // Telecom hands over a bare local number with no country code at all — format it with one
    val context = LocalContext.current
    val displayNumber = remember(number) { PhoneNumberFormatter.withCountryCode(context, number) }
    val displayName = contactName ?: displayNumber.ifBlank { stringResource(R.string.unknown) }
    val isRinging = callState == Call.STATE_RINGING
    val isActive = callState == Call.STATE_ACTIVE
    val isOnHold = callState == Call.STATE_HOLDING
    val isDialing = callState == Call.STATE_DIALING || callState == Call.STATE_CONNECTING

    var buttonsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { buttonsVisible = true }

    var showQuickReplySheet by remember { mutableStateOf(false) }
    var showKeypad by remember { mutableStateOf(false) }
    var showManageCallSheet by remember { mutableStateOf(false) }
    var showConferenceListSheet by remember { mutableStateOf(false) }
    // A conference has no single handle/number of its own (secondaryCallNumber stays null for
    // it), so the chip's visibility can't rely on the number alone — secondaryCallContactName is
    // always set to "Conference call" for that case and needs to count too, or the chip never
    // renders at all even though there's very much a call sitting there to show.
    val hasSecondaryCall = secondaryCallNumber != null || secondaryCallContactName != null
    val hasConference = conferenceParticipants.isNotEmpty()
    // Covers both the "second call, not yet merged" and "merged into a conference" states — both
    // show the same "Manage Call" slot instead of "Add Call".
    val showManageCallOption = hasSecondaryCall || hasConference
    val displaySecondaryCallNumber = remember(secondaryCallNumber) {
        secondaryCallNumber?.let { PhoneNumberFormatter.withCountryCode(context, it) }
    }

    // Call-waiting (someone calling in while already on a call) gets its OWN full screen — same
    // as the reference/stock dialer — instead of being crammed into a small chip on top of the
    // existing call's screen. The waiting caller becomes the prominent display here; the call
    // already in progress becomes the small "Active"/"On hold" chip instead.
    if (secondaryCallState == Call.STATE_RINGING) {
        CallWaitingScreen(
            waitingName = secondaryCallContactName ?: displaySecondaryCallNumber ?: stringResource(R.string.unknown),
            waitingNumber = displaySecondaryCallNumber ?: "",
            hasWaitingContactName = secondaryCallContactName != null,
            activeCallLabel = "$displayName - ${getCallStateText(callState)}",
            selection = selection,
            theme = theme,
            onMessageWaitingCaller = {
                if (!secondaryCallNumber.isNullOrBlank()) MessageUtils.sendMessage(context, secondaryCallNumber)
            },
            onAnswerAndEndOtherCall = onAnswerAndEndOtherCall,
            onAnswerSecondaryCall = onAnswerSecondaryCall,
            onDeclineSecondaryCall = onDeclineSecondaryCall
        )
        return
    }

    // Counts total call duration since it first connected — keeps running straight through a
    // hold instead of pausing, matching the reference/stock dialer's own behavior (the timer
    // reflects true wall-clock time since connection, not just "actively talking" time).
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    val isConnected = isActive || isOnHold
    LaunchedEffect(isConnected) {
        if (isConnected) {
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Wallpaper Background (falls back to a flat dark base when none selected)
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)))
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

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(top = 60.dp)
            ) {
                Text(
                    text = getCallStateText(callState),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.75f)
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (showConferenceInMainDisplay) {
                    // Matches the reference/stock dialer's own post-merge display — a generic
                    // group icon and participant count instead of either caller's own name/photo,
                    // since the call is no longer "about" just one of them.
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(56.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = stringResource(R.string.conference_call),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.conference_contacts_count, conferenceParticipants.size),
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                } else {
                    CallAvatar(
                        photoUri = photoUri,
                        name = displayName,
                        hasContactName = contactName != null,
                        accentColor = theme.accentColor,
                        pulsing = isRinging || isDialing
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = displayName,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (contactName != null && number.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = displayNumber,
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                if (isActive || isOnHold) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatTimer(elapsedSeconds),
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                // Call-waiting (secondaryCallState == RINGING) never reaches here — it returns its
                // own full-screen CallWaitingScreen above instead, so this chip only ever shows the
                // "second leg already connected" (Active/On hold) case, with Swap/End actions.
                // Tapping the chip itself (anywhere but the End button) swaps calls — same as
                // tapping the held caller's own row in the reference dialer.
                if (hasSecondaryCall) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        onClick = onSwapCalls,
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
                            Spacer(modifier = Modifier.width(10.dp))
                            // Name and status on one line ("Tushar Bhai Aavakar - On hold"),
                            // matching the reference dialer's own chip.
                            Text(
                                text = "${secondaryCallContactName ?: displaySecondaryCallNumber ?: ""} - ${stringResource(R.string.on_hold)}",
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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

            // Ringing keeps the plain floating swipe-button layout (matches stock Android);
            // everything else sits in a rounded-top tray, like the Keypad screen's dial pad tray.
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
                        color = Color.Transparent
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 20.dp)
                                .navigationBarsPadding(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // A single 3-column row (Add Call/Speaker, Hold/Keypad, Message/Mute)
                            // instead of two independently-centered rows — SpaceEvenly on rows with
                            // different item counts wouldn't actually line their buttons up
                            // vertically, so each column gets equal weight and Add Call sits directly
                            // above Speaker, Hold above Keypad, Message directly above Mute.
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Once a second call exists (or the calls are already merged
                                    // into a conference), this same slot switches to "Manage Call"
                                    // — same as the stock/reference dialer, which drops "Add Call"
                                    // the moment a second call is already up, since it can't take
                                    // a third that way.
                                    CallControlButton(
                                        icon = if (showManageCallOption) Icons.Default.PhoneInTalk else Icons.Default.PersonAdd,
                                        label = if (showManageCallOption) stringResource(R.string.manage_call) else stringResource(R.string.add_call),
                                        active = false,
                                        enabled = if (showManageCallOption) true else canAddCall,
                                        onClick = { if (showManageCallOption) showManageCallSheet = true else onAddCallClick() }
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    CallControlButton(
                                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                                        label = stringResource(R.string.speaker),
                                        active = audioState?.route == CallAudioState.ROUTE_SPEAKER,
                                        onClick = onToggleSpeaker
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CallControlButton(
                                        icon = Icons.Default.Pause,
                                        label = stringResource(R.string.hold),
                                        active = isOnHold,
                                        enabled = canHold,
                                        onClick = onToggleHold
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    CallControlButton(
                                        icon = Icons.Default.Dialpad,
                                        label = stringResource(R.string.keypad),
                                        active = showKeypad,
                                        onClick = { showKeypad = true }
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CallControlButton(
                                        icon = Icons.AutoMirrored.Filled.Chat,
                                        label = stringResource(R.string.message),
                                        active = false,
                                        onClick = { MessageUtils.sendMessage(context, number) }
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    CallControlButton(
                                        icon = if (audioState?.isMuted == true) Icons.Default.MicOff else Icons.Default.Mic,
                                        label = stringResource(R.string.mute),
                                        active = audioState?.isMuted == true,
                                        onClick = onToggleMute
                                    )
                                }
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

    if (showManageCallSheet) {
        ManageCallSheet(
            isConference = hasConference,
            hasSecondaryCall = hasSecondaryCall,
            canMerge = canMerge,
            canAddCall = canAddCall,
            onMerge = {
                showManageCallSheet = false
                onMergeCalls()
            },
            onSwap = {
                showManageCallSheet = false
                onSwapCalls()
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

/** The full-screen call-waiting UI (someone calling in while already on a call) — the waiting
 * caller is the prominent display here, matching the reference/stock dialer, with the call
 * already in progress demoted to a small "Active"/"On hold" chip instead of the other way around. */
@Composable
private fun CallWaitingScreen(
    waitingName: String,
    waitingNumber: String,
    hasWaitingContactName: Boolean,
    activeCallLabel: String,
    selection: WallpaperSelection,
    theme: CallTheme,
    onMessageWaitingCaller: () -> Unit,
    onAnswerAndEndOtherCall: () -> Unit,
    onAnswerSecondaryCall: () -> Unit,
    onDeclineSecondaryCall: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)))
        CallWallpaperBackground(selection = selection, modifier = Modifier.fillMaxSize())
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
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(if (hasWaitingContactName) getAvatarColor(waitingName) else Color(0xFF9E9E9E)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasWaitingContactName) {
                        Text(text = waitingName.take(1).uppercase(), color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = waitingName,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                if (hasWaitingContactName && waitingNumber.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = waitingNumber, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.call_waiting),
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
                            imageVector = Icons.Default.PhonePaused,
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
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.message), color = Color.White)
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
                        Text(stringResource(R.string.answer_and_end_other_call), color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    CallActionButtons(
                        icon = Icons.Default.CallEnd,
                        color = Color(0xFFD32F2F),
                        onClick = onDeclineSecondaryCall,
                        label = stringResource(R.string.decline_call_short)
                    )
                    CallActionButtons(
                        icon = Icons.Default.Call,
                        color = theme.accentColor,
                        onClick = onAnswerSecondaryCall,
                        label = stringResource(R.string.answer)
                    )
                }
            }
        }
    }
}

/** Mirrors the stock/reference dialer's own "Manage Call" bottom sheet. Before a merge: Merge and
 * Swap act on the two live calls, Add Call is shown but disabled (a third simultaneous call isn't
 * supported that way). After a merge: Conference List replaces Merge/Swap, and Add Call becomes
 * enabled again — a further call can still be placed and later merged into the same conference. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCallSheet(
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
                text = stringResource(R.string.manage_call),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            if (isConference) {
                ManageCallRow(
                    icon = Icons.Default.Groups,
                    label = stringResource(R.string.conference_list),
                    enabled = true,
                    onClick = onConferenceList
                )
                // A further call placed on top of an already-merged conference (Add Call was
                // re-enabled for exactly this) still needs its own way back into that same
                // conference — Merge here targets Call.conference() on the conference call itself,
                // which adds the new call as another participant rather than starting a second one.
                if (hasSecondaryCall) {
                    ManageCallRow(
                        icon = Icons.Default.CallMerge,
                        label = stringResource(R.string.merge),
                        enabled = canMerge,
                        onClick = onMerge
                    )
                }
                // canAddCall's own capability check (CAPABILITY_HOLD) isn't reliably reported on
                // the conference call itself on every telephony stack — same gap already found for
                // canMerge — so once merged, this only needs the same "no call already pending"
                // gate canAddCall applies pre-merge, without depending on that capability bit too.
                ManageCallRow(
                    icon = Icons.Default.PersonAdd,
                    label = stringResource(R.string.add_call),
                    enabled = canAddCall || !hasSecondaryCall,
                    onClick = onAddCall
                )
            } else {
                ManageCallRow(
                    icon = Icons.Default.CallMerge,
                    label = stringResource(R.string.merge),
                    enabled = canMerge,
                    onClick = onMerge
                )
                ManageCallRow(
                    icon = Icons.Default.SwapCalls,
                    label = stringResource(R.string.swap),
                    enabled = true,
                    onClick = onSwap
                )
                ManageCallRow(
                    icon = Icons.Default.PersonAdd,
                    label = stringResource(R.string.add_call),
                    enabled = false,
                    onClick = {}
                )
            }
        }
    }
}

/** Lists each merged conference participant with a per-row hang-up action — the only way to drop
 * a single participant out of an otherwise-continuing conference. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConferenceListSheet(
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
                    text = stringResource(R.string.conference_list),
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
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (participant.photoUri != null) Color.Transparent else getAvatarColor(participant.name)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (participant.photoUri != null) {
                            ContactAvatarImage(photoUri = participant.photoUri, modifier = Modifier.fillMaxSize())
                        } else {
                            Text(text = participant.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = participant.name, fontSize = 16.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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

/** Simple display bundle for one merged-conference participant — kept separate from the raw
 * [Call] so InCallScreen's UI never needs to reach into Telecom details itself; the [call]
 * reference is only ever used to disconnect that one participant. */
data class ConferenceParticipant(
    val call: Call,
    val number: String,
    val name: String,
    val photoUri: String?
)

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
            Spacer(modifier = Modifier.width(24.dp))
            Text(
                text = label,
                fontSize = 16.sp,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}

@Composable
fun CallAvatar(
    photoUri: String?,
    name: String,
    hasContactName: Boolean,
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
                .background(
                    when {
                        photoUri != null -> Color.Transparent
                        hasContactName -> getAvatarColor(name)
                        else -> Color(0xFF9E9E9E)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (photoUri != null) {
                ContactAvatarImage(photoUri = photoUri, modifier = Modifier.fillMaxSize())
            } else if (hasContactName) {
                Text(
                    text = name.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(if (enabled) 1f else 0.4f)
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(68.dp),
            shape = CircleShape,
            color = if (active) Color.White else Color.White.copy(alpha = 0.16f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) Color(0xFF1A1A1A) else Color.White,
                    modifier = Modifier.size(28.dp)
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
                },
                showBackground = false
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
