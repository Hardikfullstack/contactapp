package com.phone.contact.call.dialer.ui.features.fakecall

import android.os.Bundle
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import com.phone.contact.call.dialer.R
import com.phone.contact.call.dialer.domain.repository.ContactRepository
import com.phone.contact.call.dialer.service.CallAnnouncerManager
import com.phone.contact.call.dialer.service.FakeCallManager
import com.phone.contact.call.dialer.service.FakeCallReceiver
import com.phone.contact.call.dialer.service.FakeCallRingtonePlayer
import com.phone.contact.call.dialer.service.FlashAlertManager
import com.phone.contact.call.dialer.service.SpamManager
import com.phone.contact.call.dialer.service.cancelActiveCallNotification
import com.phone.contact.call.dialer.service.postActiveCallNotification
import com.phone.contact.call.dialer.ui.components.AudioRouteOption
import com.phone.contact.call.dialer.ui.components.AudioRouteSheet
import com.phone.contact.call.dialer.ui.components.CallWallpaperBackground
import com.phone.contact.call.dialer.ui.components.ContactAvatarImage
import com.phone.contact.call.dialer.ui.components.SwipeUpCallButton
import com.phone.contact.call.dialer.ui.components.toComposeShape
import com.phone.contact.call.dialer.ui.features.call.CallActionButtons
import com.phone.contact.call.dialer.ui.features.call.CallControlButton
import com.phone.contact.call.dialer.ui.features.call.DtmfKeypadSheet
import com.phone.contact.call.dialer.ui.features.call.getCallStateText
import com.phone.contact.call.dialer.ui.theme.ContactAppTheme
import com.phone.contact.call.dialer.util.CallAccentColors
import com.phone.contact.call.dialer.util.CallButtonShape
import com.phone.contact.call.dialer.util.CallTheme
import com.phone.contact.call.dialer.util.ContactCallBackgroundManager
import com.phone.contact.call.dialer.util.PhoneNumberFormatter
import com.phone.contact.call.dialer.util.PreferenceManager
import com.phone.contact.call.dialer.util.WallpaperSelection
import com.phone.contact.call.dialer.util.getAvatarColor
import com.phone.contact.call.dialer.util.isDarkOnCallScreen
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

            // Without this, system back just backgrounds this singleInstance Activity instead of
            // ending the call — onDestroy() (where the ringtone actually stops) never runs, so the
            // ringtone keeps playing forever and the only way out is the notification's hang-up
            // action. Treat back exactly like the decline gesture/button.
            BackHandler {
                FakeCallManager.endFromUi()
            }

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
                insetsController.isAppearanceLightNavigationBars = !selection.isDarkOnCallScreen()
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

    // Home/Recents pressed while a fake call is showing should end it too — matching the
    // BackHandler above. Without this, leaving via Home/Recents just backgrounds this
    // singleInstance Activity while the call stays "ringing"/active, stranding the ringtone
    // playing forever with no way to stop it except the notification's hang-up action.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        FakeCallManager.endFromUi()
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
    val displayNumber = remember(number) { PhoneNumberFormatter.withCountryCode(context, number) }
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    var isMuted by remember { mutableStateOf(false) }
    var audioRoute by remember { mutableStateOf(AudioRouteOption.EARPIECE) }
    var showAudioRouteSheet by remember { mutableStateOf(false) }
    var showKeypad by remember { mutableStateOf(false) }
    // No real Telecom CallAudioState to read here (see the comment above) — checked directly off
    // AudioManager's own connected-devices list instead. Re-read each time the button/sheet is
    // shown rather than cached once, since a Bluetooth device can connect/disconnect mid-call.
    fun hasBluetoothDeviceConnected(): Boolean {
        return audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        } == true
    }
    fun applyAudioRoute(route: AudioRouteOption) {
        audioRoute = route
        when (route) {
            AudioRouteOption.SPEAKER -> {
                audioManager?.stopBluetoothSco()
                audioManager?.isBluetoothScoOn = false
                audioManager?.isSpeakerphoneOn = true
            }
            AudioRouteOption.BLUETOOTH -> {
                audioManager?.isSpeakerphoneOn = false
                audioManager?.startBluetoothSco()
                audioManager?.isBluetoothScoOn = true
            }
            AudioRouteOption.EARPIECE, AudioRouteOption.WIRED_HEADSET -> {
                audioManager?.stopBluetoothSco()
                audioManager?.isBluetoothScoOn = false
                audioManager?.isSpeakerphoneOn = false
            }
        }
    }

    // Mirrors a real call's own default behavior: audio goes to a connected Bluetooth device
    // automatically the moment the call becomes active, instead of always starting on the
    // earpiece regardless of what's actually connected — without this, audioRoute's initial
    // EARPIECE default never updates on its own, so the button kept showing the Speaker icon
    // even with a Bluetooth device already connected.
    LaunchedEffect(isAccepted) {
        if (isAccepted && hasBluetoothDeviceConnected()) {
            applyAudioRoute(AudioRouteOption.BLUETOOTH)
        }
    }

    // Add Call here is purely cosmetic — there's no real second caller to add on a fake call,
    // so this is only for the acting illusion.
    var showAddCallDialog by remember { mutableStateOf(false) }
    var fakeSecondaryCallerName by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            // Never leave the device's real mic/speaker/Bluetooth-SCO state altered after the
            // fake call ends.
            audioManager?.isMicrophoneMute = false
            audioManager?.isSpeakerphoneOn = false
            audioManager?.stopBluetoothSco()
            audioManager?.isBluetoothScoOn = false
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
                    .padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Call status line above the avatar — matches the real in-call screen exactly
                // (same wording, same Call.STATE_* constants FakeCallManager already reuses).
                Text(
                    text = getCallStateText(state),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.75f)
                )

                Spacer(modifier = Modifier.height(20.dp))

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
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )

                // The number stays visible the whole time (matching the real in-call screen's own
                // separate name+number lines) instead of being replaced by the timer once
                // accepted — that previously made the picked contact's number disappear the
                // moment the call was answered.
                Text(
                    text = displayNumber,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 8.dp)
                )

                if (isAccepted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isFakeOnHold) stringResource(R.string.on_hold) else formatTimer(timer),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

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
                    // Transparent (not a solid tray color) so the call wallpaper shows through
                    // behind it, matching the real in-call screen exactly.
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
                            // Same 3-column grid as the real in-call screen (Add Call/Speaker,
                            // Hold/Keypad, Message/Mute) instead of fake call's own different
                            // arrangement, so the two controls trays look identical.
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CallControlButton(
                                        // Purely decorative — looks like a normal, tappable button (full
                                        // opacity, not dimmed like a genuinely disabled one) but does nothing,
                                        // since there's no real second caller to add on a fake call.
                                        icon = Icons.Default.PersonAdd,
                                        label = stringResource(R.string.add_call),
                                        active = false,
                                        onClick = {}
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    CallControlButton(
                                        // Only Bluetooth gets its own icon/highlight — Earpiece falls back to
                                        // the plain Speaker icon/label, and only Speaker itself gets the
                                        // "active" highlighted background (matching the reference app;
                                        // Bluetooth being the current route is shown via the icon alone).
                                        icon = if (audioRoute == AudioRouteOption.BLUETOOTH) Icons.Default.Bluetooth else Icons.AutoMirrored.Filled.VolumeUp,
                                        label = if (audioRoute == AudioRouteOption.BLUETOOTH) stringResource(R.string.bluetooth) else stringResource(R.string.speaker),
                                        active = audioRoute == AudioRouteOption.SPEAKER,
                                        onClick = {
                                            if (hasBluetoothDeviceConnected()) {
                                                showAudioRouteSheet = true
                                            } else {
                                                applyAudioRoute(if (audioRoute == AudioRouteOption.SPEAKER) AudioRouteOption.EARPIECE else AudioRouteOption.SPEAKER)
                                            }
                                        }
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CallControlButton(
                                        icon = Icons.Default.Pause,
                                        label = stringResource(R.string.hold),
                                        active = isFakeOnHold,
                                        onClick = { isFakeOnHold = !isFakeOnHold }
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
                                        // Purely decorative too — same reasoning as Add Call above.
                                        icon = Icons.AutoMirrored.Filled.Chat,
                                        label = stringResource(R.string.message),
                                        active = false,
                                        onClick = {}
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    CallControlButton(
                                        icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                        label = stringResource(R.string.mute),
                                        active = isMuted,
                                        onClick = {
                                            isMuted = !isMuted
                                            audioManager?.isMicrophoneMute = isMuted
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            CallActionButtons(
                                icon = Icons.Default.CallEnd,
                                color = Color(0xFFD32F2F),
                                shape = theme.buttonShape.toComposeShape(),
                                onClick = { FakeCallManager.endFromUi() },
                                label = stringResource(R.string.end_call)
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

    if (showAudioRouteSheet) {
        // Earpiece always listed last, matching the reference app's own ordering.
        val availableRoutes = buildList {
            if (hasBluetoothDeviceConnected()) add(AudioRouteOption.BLUETOOTH)
            add(AudioRouteOption.SPEAKER)
            add(AudioRouteOption.EARPIECE)
        }
        AudioRouteSheet(
            availableRoutes = availableRoutes,
            selectedRoute = audioRoute,
            onSelect = { option ->
                showAudioRouteSheet = false
                applyAudioRoute(option)
            },
            onDismiss = { showAudioRouteSheet = false }
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
                    Text(stringResource(R.string.add_call).uppercase())
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

private fun formatTimer(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}
