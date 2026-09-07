package com.example.contactapp.ui.features.fakecall

import android.os.Bundle
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.media.AudioManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import com.example.contactapp.R
import com.example.contactapp.domain.repository.ContactRepository
import com.example.contactapp.service.CallAnnouncerManager
import com.example.contactapp.service.FakeCallManager
import com.example.contactapp.service.FakeCallReceiver
import com.example.contactapp.service.FakeCallRingtonePlayer
import com.example.contactapp.service.FlashAlertManager
import com.example.contactapp.service.SpamManager
import com.example.contactapp.service.cancelActiveCallNotification
import com.example.contactapp.service.postActiveCallNotification
import com.example.contactapp.ui.components.CallWallpaperBackground
import com.example.contactapp.ui.components.ContactAvatarImage
import com.example.contactapp.ui.components.SwipeUpCallButton
import com.example.contactapp.ui.components.lightened
import com.example.contactapp.ui.components.toComposeShape
import com.example.contactapp.ui.features.call.CallControlButton
import com.example.contactapp.ui.features.call.DtmfKeypadSheet
import com.example.contactapp.ui.theme.ContactAppTheme
import com.example.contactapp.util.CallAccentColors
import com.example.contactapp.util.CallButtonShape
import com.example.contactapp.util.CallTheme
import com.example.contactapp.util.ContactCallBackgroundManager
import com.example.contactapp.util.PreferenceManager
import com.example.contactapp.util.WallpaperSelection
import com.example.contactapp.util.getAvatarColor
import com.example.contactapp.util.isDarkOnCallScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@AndroidEntryPoint
class FakeCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_AUTO_ANSWER = "auto_answer"

        /** True while this Activity is started — lets FakeCallReceiver check, after a short
         * delay, whether its startActivity() call actually resulted in this screen showing, so
         * it knows whether the ringing notification fallback is needed. */
        var isVisible = false
    }

    @Inject
    lateinit var flashAlertManager: FlashAlertManager

    @Inject
    lateinit var ringtonePlayer: FakeCallRingtonePlayer

    @Inject
    lateinit var callAnnouncerManager: CallAnnouncerManager

    @Inject
    lateinit var preferenceManager: PreferenceManager

    @Inject
    lateinit var contactCallBackgroundManager: ContactCallBackgroundManager

    @Inject
    lateinit var contactRepository: ContactRepository

    @Inject
    lateinit var spamManager: SpamManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val name = intent.getStringExtra("caller_name") ?: "Unknown"
        val number = intent.getStringExtra("caller_number") ?: "0000000000"
        val photoUri = intent.getStringExtra("caller_photo")
        val autoAnswer = intent.getBooleanExtra(EXTRA_AUTO_ANSWER, false)

        // The call screen is now on screen (whether launched directly or via the
        // full-screen-intent notification) — the notification has done its job.
        NotificationManagerCompat.from(this).cancel(FakeCallReceiver.NOTIFICATION_ID)

        // Seeds FakeCallManager's single source of truth for this call — always resets any
        // stale state/connection from a previous fake call first (see FakeCallManager.startRinging).
        FakeCallManager.startRinging(FakeCallManager.FakeCallInfo(name, number, photoUri))

        if (autoAnswer) {
            // Came from tapping Answer on the notification (FakeCallActionReceiver) — skip
            // straight to the active state instead of ringing first just to immediately stop.
            FakeCallManager.answerFromUi()
        } else {
            // Start flashing for fake call
            flashAlertManager.startBlinking()
            // Telecom does not auto-ring self-managed connections — this app has to play the
            // ringtone itself, same as it owns the incoming-call UI.
            ringtonePlayer.startRinging(number)
            // Same announcer behavior as a real incoming call (STATE_RINGING) — see ContactCallService.
            callAnnouncerManager.announceCall(name)
        }

        setContent {
            val fakeCallState by FakeCallManager.callState.collectAsState()

            var isSpam by remember { mutableStateOf(false) }
            LaunchedEffect(number) {
                isSpam = spamManager.checkSpamStatus(number).isSpam()
            }

            // Single reaction point for every state transition — mirrors InCallActivity's
            // LaunchedEffect(callState) so notification/cleanup happen once regardless of the path.
            LaunchedEffect(fakeCallState) {
                // FakeCallReceiver's ringing notification posts asynchronously and can land after
                // onCreate()'s one-shot cancel — re-cancel on every state change to close that race.
                NotificationManagerCompat.from(this@FakeCallActivity).cancel(FakeCallReceiver.NOTIFICATION_ID)

                when (fakeCallState) {
                    Call.STATE_ACTIVE -> {
                        postActiveCallNotification(this@FakeCallActivity, name)
                    }
                    Call.STATE_DISCONNECTED -> {
                        flashAlertManager.stopBlinking()
                        ringtonePlayer.stopRinging()
                        callAnnouncerManager.stopAnnouncing()
                        cancelActiveCallNotification(this@FakeCallActivity)
                        // Plain finish() can leave an empty task card behind in the system
                        // app-switcher for a singleInstance activity like this one — remove it
                        // outright instead, since there's nothing to return to in this task.
                        finishAndRemoveTask()
                    }
                }
            }

            val globalSelection by preferenceManager.wallpaperSelectionFlow.collectAsState(
                initial = preferenceManager.getCallWallpaperSelection()
            )

            // Resolve through the same PhoneLookup-backed contact matching real calls use,
            // in case this fake number's formatting differs from what's stored on the contact.
            var resolvedNumber by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(number) {
                resolvedNumber = contactRepository.findContactByNumber(number)?.number ?: number
            }

            // If this fake caller's number matches a real contact with a calling card set,
            // it takes priority — same rule as a real incoming/outgoing call.
            val callingCard by remember(resolvedNumber) {
                if (resolvedNumber != null) contactCallBackgroundManager.getBackgroundFlow(resolvedNumber!!) else flowOf(WallpaperSelection.None)
            }.collectAsState(initial = WallpaperSelection.None)

            val selection = if (callingCard !is WallpaperSelection.None) callingCard else globalSelection

            val callAccentColorId by preferenceManager.callAccentColorFlow.collectAsState(
                initial = preferenceManager.getCallAccentColorId()
            )
            val callButtonShapeName by preferenceManager.callButtonShapeFlow.collectAsState(
                initial = preferenceManager.getCallButtonShapeName()
            )
            val callTheme = CallTheme(
                accentColor = CallAccentColors.findById(callAccentColorId).color,
                buttonShape = CallButtonShape.safeValueOf(callButtonShapeName)
            )

            val insetsController = remember { WindowCompat.getInsetsController(window, window.decorView) }
            SideEffect {
                insetsController.isAppearanceLightStatusBars = !selection.isDarkOnCallScreen()
            }

            ContactAppTheme(darkTheme = true) { // Always dark for call UI
                FakeCallContent(
                    name = name,
                    number = number,
                    photoUri = photoUri,
                    selection = selection,
                    state = fakeCallState,
                    flashAlertManager = flashAlertManager,
                    ringtonePlayer = ringtonePlayer,
                    theme = callTheme,
                    isSpam = isSpam
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        flashAlertManager.stopBlinking()
        ringtonePlayer.stopRinging()
        callAnnouncerManager.stopAnnouncing()
        cancelActiveCallNotification(this)
    }

    override fun onStart() {
        super.onStart()
        isVisible = true
    }

    override fun onStop() {
        super.onStop()
        isVisible = false
    }
}

@Composable
fun FakeCallContent(
    name: String,
    number: String,
    photoUri: String?,
    selection: WallpaperSelection,
    state: Int,
    flashAlertManager: FlashAlertManager,
    ringtonePlayer: FakeCallRingtonePlayer,
    theme: CallTheme = CallTheme(CallAccentColors.findById("green").color, CallButtonShape.CIRCLE),
    isSpam: Boolean = false
) {
    val isAccepted = state == Call.STATE_ACTIVE
    var timer by remember { mutableIntStateOf(0) }
    var buttonsVisible by remember { mutableStateOf(false) }
    // Purely cosmetic, same as Add Call below — no real Telecom call to actually hold.
    var isFakeOnHold by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { buttonsVisible = true }

    LaunchedEffect(isAccepted) {
        if (isAccepted) {
            // Flash Alert and the ringtone are ringing indicators, not things that should keep
            // going for the whole call — stop them the moment it's answered, whether via the
            // swipe gesture below or a Telecom-driven answer (both funnel through this state).
            flashAlertManager.stopBlinking()
            ringtonePlayer.stopRinging()
        }
    }

    // Timer pauses while cosmetically "on hold", matching the real in-call screen's behavior.
    LaunchedEffect(isAccepted, isFakeOnHold) {
        if (isAccepted && !isFakeOnHold) {
            while (true) {
                delay(1000)
                timer++
            }
        }
    }

    // No real Telecom audio session exists for a fake call (see FakeCallManager) — toggle the
    // device's actual mic/speakerphone directly instead, fitting for just sounding authentic.
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var showKeypad by remember { mutableStateOf(false) }

    // Add Call here is purely cosmetic — there's no real second caller to add on a fake call,
    // so this is only for the acting illusion.
    var showAddCallDialog by remember { mutableStateOf(false) }
    var fakeSecondaryCallerName by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            // Never leave the device's real mic/speaker state altered after the fake call ends.
            audioManager?.isMicrophoneMute = false
            audioManager?.isSpeakerphoneOn = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)) // Same flat dark base as the real in-call screen
    ) {
        // Wallpaper Background
        CallWallpaperBackground(selection = selection, modifier = Modifier.fillMaxSize())


        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .border(3.dp, theme.accentColor, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUri != null) {
                        ContactAvatarImage(
                            photoUri = photoUri,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(getAvatarColor(name)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = if (isAccepted) { if (isFakeOnHold) stringResource(R.string.on_hold) else formatTimer(timer) } else number,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 8.dp)
                )

                if (fakeSecondaryCallerName != null) {
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
                                text = fakeSecondaryCallerName ?: "",
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { fakeSecondaryCallerName = null }) {
                                Text(stringResource(R.string.end_call), color = Color(0xFFFF8A80), fontSize = 13.sp)
                            }
                        }
                    }
                }

                if (!isAccepted && isSpam) {
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
                }
            }

            // Ringing keeps the plain floating swipe-button layout (matches stock Android);
            // once accepted, controls sit in a rounded-top tray, matching the real in-call screen.
            if (!isAccepted) {
                AnimatedVisibility(
                    visible = buttonsVisible,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 80.dp),
                    enter = slideInVertically(animationSpec = tween(450)) { fullHeight -> fullHeight } + fadeIn(tween(450))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Decline: requires an upward swipe while ringing.
                        SwipeUpCallButton(
                            icon = Icons.Default.CallEnd,
                            color = Color(0xFFD32F2F),
                            shape = theme.buttonShape.toComposeShape(),
                            contentDescription = stringResource(R.string.decline_call),
                            onTriggered = { FakeCallManager.endFromUi() }
                        )

                        // Accept: requires an upward swipe while ringing.
                        SwipeUpCallButton(
                            icon = Icons.Default.Call,
                            color = theme.accentColor,
                            shape = theme.buttonShape.toComposeShape(),
                            contentDescription = stringResource(R.string.answer),
                            onTriggered = { FakeCallManager.answerFromUi() }
                        )
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
                                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    label = stringResource(R.string.mute),
                                    active = isMuted,
                                    onClick = {
                                        isMuted = !isMuted
                                        audioManager?.isMicrophoneMute = isMuted
                                    }
                                )
                                CallControlButton(
                                    icon = Icons.Default.VolumeUp,
                                    label = stringResource(R.string.speaker),
                                    active = isSpeakerOn,
                                    onClick = {
                                        isSpeakerOn = !isSpeakerOn
                                        audioManager?.isSpeakerphoneOn = isSpeakerOn
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                CallControlButton(
                                    icon = Icons.Default.Pause,
                                    label = stringResource(R.string.hold),
                                    active = isFakeOnHold,
                                    onClick = { isFakeOnHold = !isFakeOnHold }
                                )
                                CallControlButton(
                                    icon = Icons.Default.PersonAdd,
                                    label = stringResource(R.string.add_call),
                                    active = false,
                                    enabled = fakeSecondaryCallerName == null,
                                    onClick = { showAddCallDialog = true }
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            CallActionButton(
                                icon = Icons.Default.CallEnd,
                                color = Color(0xFFD32F2F),
                                shape = theme.buttonShape.toComposeShape(),
                                onClick = { FakeCallManager.endFromUi() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showKeypad) {
        // No real call session to send DTMF through — this is purely for the acting illusion
        // (e.g. pretending to enter an extension code), so digits are just shown, not dialed.
        DtmfKeypadSheet(
            onDigit = {},
            onDigitReleased = {},
            onDismiss = { showKeypad = false }
        )
    }

    if (showAddCallDialog) {
        var enteredName by remember { mutableStateOf("") }
        val unknownLabel = stringResource(R.string.unknown)
        AlertDialog(
            onDismissRequest = { showAddCallDialog = false },
            title = { Text(stringResource(R.string.add_call)) },
            text = {
                OutlinedTextField(
                    value = enteredName,
                    onValueChange = { enteredName = it },
                    label = { Text(stringResource(R.string.caller_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    fakeSecondaryCallerName = enteredName.ifBlank { unknownLabel }
                    showAddCallDialog = false
                }) {
                    Text(stringResource(R.string.add_call))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCallDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun CallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    shape: Shape = CircleShape
) {
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

private fun formatTimer(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}
