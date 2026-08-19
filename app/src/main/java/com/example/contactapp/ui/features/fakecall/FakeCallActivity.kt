package com.example.contactapp.ui.features.fakecall

import android.os.Bundle
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
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
import com.example.contactapp.ui.components.SwipeUpCallButton
import com.example.contactapp.ui.components.lightened
import com.example.contactapp.ui.components.toComposeShape
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

        // The call screen is now on screen (whether launched directly or via the
        // full-screen-intent notification) — the notification has done its job.
        NotificationManagerCompat.from(this).cancel(FakeCallReceiver.NOTIFICATION_ID)

        // Seeds FakeCallManager's single source of truth for this call — always resets any
        // stale state/connection from a previous fake call first (see FakeCallManager.startRinging).
        FakeCallManager.startRinging(FakeCallManager.FakeCallInfo(name, number, photoUri))

        // Start flashing for fake call
        flashAlertManager.startBlinking()
        // Telecom does not auto-ring self-managed connections — this app has to play the
        // ringtone itself, same as it owns the incoming-call UI.
        ringtonePlayer.startRinging(number)
        // Same announcer behavior as a real incoming call (STATE_RINGING) — see ContactCallService.
        callAnnouncerManager.announceCall(name)

        setContent {
            val fakeCallState by FakeCallManager.callState.collectAsState()

            var isSpam by remember { mutableStateOf(false) }
            LaunchedEffect(number) {
                isSpam = spamManager.checkSpamStatus(number).isSpam()
            }

            // Single reaction point for every state transition — mirrors InCallActivity's
            // LaunchedEffect(callState) pattern for real calls, so notification/cleanup/task
            // removal all happen exactly once regardless of which path (swipe gesture or a
            // Telecom-driven answer/end) drove the transition.
            LaunchedEffect(fakeCallState) {
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
                buttonShape = CallButtonShape.valueOf(callButtonShapeName)
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
    LaunchedEffect(Unit) { buttonsVisible = true }

    LaunchedEffect(isAccepted) {
        if (isAccepted) {
            // Flash Alert and the ringtone are ringing indicators, not things that should keep
            // going for the whole call — stop them the moment it's answered, whether via the
            // swipe gesture below or a Telecom-driven answer (both funnel through this state).
            flashAlertManager.stopBlinking()
            ringtonePlayer.stopRinging()
            while (true) {
                delay(1000)
                timer++
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121412)) // Deep charcoal default
    ) {
        // Wallpaper Background
        CallWallpaperBackground(selection = selection, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
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
                    AsyncImage(
                        model = photoUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
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
                text = if (isAccepted) formatTimer(timer) else number,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 8.dp)
            )

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

            Spacer(modifier = Modifier.weight(1f))

            // Action Buttons
            AnimatedVisibility(
                visible = buttonsVisible,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 80.dp),
                enter = slideInVertically(animationSpec = tween(450)) { fullHeight -> fullHeight } + fadeIn(tween(450))
            ) {
                // Swaps the ringing (decline + accept) button set for the ongoing-call
                // (single end-call) set with an upward slide, mirroring stock dialer behavior.
                AnimatedContent(
                    targetState = isAccepted,
                    transitionSpec = {
                        (slideInVertically(tween(350)) { height -> height } + fadeIn(tween(350))) togetherWith
                            (slideOutVertically(tween(350)) { height -> -height } + fadeOut(tween(200)))
                    },
                    label = "callButtons"
                ) { accepted ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (!accepted) {
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
                        } else {
                            // End Active Call
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
