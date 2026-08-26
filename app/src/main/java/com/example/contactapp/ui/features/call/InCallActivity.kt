package com.example.contactapp.ui.features.call

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.Call
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.contactapp.R
import com.example.contactapp.domain.model.Contact
import com.example.contactapp.domain.repository.ContactRepository
import com.example.contactapp.service.AutoReplyManager
import com.example.contactapp.service.CallManager
import com.example.contactapp.service.CallNotificationManager
import com.example.contactapp.service.CallRecorder
import com.example.contactapp.service.SpamManager
import com.example.contactapp.ui.theme.ContactAppTheme
import com.example.contactapp.util.CallAccentColors
import com.example.contactapp.util.CallButtonShape
import com.example.contactapp.util.CallTheme
import com.example.contactapp.util.CallUtils
import com.example.contactapp.util.ContactCallBackgroundManager
import com.example.contactapp.util.PreferenceManager
import com.example.contactapp.util.WallpaperSelection
import com.example.contactapp.util.isDarkOnCallScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@AndroidEntryPoint
class InCallActivity : ComponentActivity() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    @Inject
    lateinit var contactCallBackgroundManager: ContactCallBackgroundManager

    @Inject
    lateinit var contactRepository: ContactRepository

    @Inject
    lateinit var autoReplyManager: AutoReplyManager

    @Inject
    lateinit var spamManager: SpamManager

    @Inject
    lateinit var callRecorder: CallRecorder

    @Inject
    lateinit var callNotificationManager: CallNotificationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ContactCallService always posts the incoming-call notification alongside launching this
        // Activity directly — that notification exists purely as a fallback for when the direct
        // launch gets blocked (OEM background restrictions). Reaching onCreate() here means the
        // direct launch worked, so the notification is now redundant and would otherwise sit in
        // the shade next to the call screen it's supposed to be a fallback for.
        callNotificationManager.cancelNotification()

        // Show over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        enableEdgeToEdge()

        setContent {
            val callState by CallManager.callState.collectAsState()
            // Not read directly — collecting it just forces a recomposition whenever Telecom
            // updates call capabilities (e.g. CAPABILITY_HOLD granted a moment after the call
            // goes ACTIVE) without a state change, so canHold()/canAddCall() below get re-checked
            // instead of staying stuck at whatever was true right after connecting.
            @Suppress("UNUSED_VARIABLE")
            val detailsVersion by CallManager.detailsVersion.collectAsState()
            val call by CallManager.currentCall.collectAsState()
            val rawNumber = call?.details?.handle?.schemeSpecificPart
            val secondaryCall by CallManager.secondaryCall.collectAsState()
            val isRecording by callRecorder.isRecording.collectAsState()
            val recordingSeconds by callRecorder.elapsedSeconds.collectAsState()

            var pendingRecordingStart by remember { mutableStateOf(false) }
            val recordAudioLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted && pendingRecordingStart) {
                    val started = callRecorder.start(rawNumber ?: "call")
                    if (!started) {
                        Toast.makeText(this@InCallActivity, R.string.recording_not_supported, Toast.LENGTH_LONG).show()
                    }
                }
                pendingRecordingStart = false
            }

            // The number Telecom hands back (call.details.handle) can be formatted
            // differently from what's stored on the contact (country code, spacing, etc.),
            // so resolve it through the same PhoneLookup-backed contact matching the rest
            // of the app already trusts, rather than comparing raw strings directly.
            var resolvedNumber by remember { mutableStateOf<String?>(null) }
            var resolvedContact by remember { mutableStateOf<Contact?>(null) }
            LaunchedEffect(rawNumber) {
                // Once resolved, hold onto it — the Call object (and rawNumber with it) goes null
                // right as the call disconnects, briefly before this Activity finishes (see the
                // callState LaunchedEffect below); resetting to null here would flash "Unknown"
                // during that window instead of keeping the name/number on screen.
                val raw = rawNumber ?: return@LaunchedEffect
                val contact = contactRepository.findContactByNumber(raw)
                resolvedContact = contact
                resolvedNumber = contact?.number ?: raw
            }

            val globalSelection by preferenceManager.wallpaperSelectionFlow.collectAsState(
                initial = preferenceManager.getCallWallpaperSelection()
            )
            // This caller's own "calling card" (set on their contact detail page) takes
            // priority over the app-wide Tools > Call Wallpaper pick when present.
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

            LaunchedEffect(callState) {
                // The incoming-call notification is posted asynchronously (behind a spam-status
                // check in ContactCallService) and can race past the one-time cancel in onCreate()
                // above, landing after it and never getting cleared — leaving it stuck even after
                // declining. Re-cancelling here closes that race. Skipped for STATE_ACTIVE:
                // ContactCallService posts the legitimate "ongoing call" notification (same ID)
                // for that exact transition from its own callback, and cancelling here could race
                // ahead of that post and wipe it out instead of just clearing the stale ringing one.
                if (callState != Call.STATE_ACTIVE) {
                    callNotificationManager.cancelNotification()
                }

                if (callState == Call.STATE_DISCONNECTED) {
                    // Never leave a recording running past the call it belongs to.
                    callRecorder.stop()
                    // Plain finish() can leave an empty task card behind in the system
                    // app-switcher for a singleInstance activity like this one — remove it
                    // outright instead, since there's nothing to return to in this task.
                    finishAndRemoveTask()
                }
            }

            val insetsController = remember { WindowCompat.getInsetsController(window, window.decorView) }
            SideEffect {
                insetsController.isAppearanceLightStatusBars = !selection.isDarkOnCallScreen()
            }

            ContactAppTheme(darkTheme = true) { // Always dark for call UI — matches FakeCallActivity
                val isSpamByCallManager by CallManager.isSpam.collectAsState()
                val audioState by CallManager.audioState.collectAsState()

                InCallScreen(
                    contactName = resolvedContact?.name,
                    number = resolvedNumber ?: rawNumber ?: "",
                    photoUri = resolvedContact?.photoUri,
                    onHangup = { CallManager.disconnect() },
                    onDecline = {
                        CallManager.reject()
                        resolvedNumber?.let { autoReplyManager.sendReplyIfEnabled(it) }
                    },
                    onAnswer = { CallManager.answer() },
                    onSendQuickReply = { message ->
                        CallManager.reject()
                        resolvedNumber?.let { autoReplyManager.sendQuickReply(it, message) }
                    },
                    isSpam = isSpamByCallManager,
                    onReportSpam = { num -> spamManager.reportSpam(num, true) },
                    audioState = audioState,
                    onToggleMute = { CallManager.toggleMute() },
                    onToggleSpeaker = { CallManager.toggleSpeaker() },
                    canHold = CallManager.canHold(),
                    onToggleHold = { CallManager.toggleHold() },
                    onPlayDtmf = { digit -> CallManager.playDtmfTone(digit) },
                    onStopDtmf = { CallManager.stopDtmfTone() },
                    canAddCall = CallManager.canAddCall(),
                    secondaryCallNumber = secondaryCall?.details?.handle?.schemeSpecificPart,
                    onAddCall = { number ->
                        // Hold the current call first — Telecom generally does this
                        // automatically when a second call is placed, but making it explicit
                        // avoids depending on that OEM/carrier-specific behavior.
                        val current = CallManager.currentCall.value
                        if (current?.state == Call.STATE_ACTIVE) current.hold()
                        CallUtils.makeCall(this@InCallActivity, number)
                    },
                    onEndSecondaryCall = { secondaryCall?.disconnect() },
                    isRecording = isRecording,
                    recordingSeconds = recordingSeconds,
                    onStartRecording = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            this@InCallActivity, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            val started = callRecorder.start(rawNumber ?: "call")
                            if (!started) {
                                Toast.makeText(this@InCallActivity, R.string.recording_not_supported, Toast.LENGTH_LONG).show()
                            }
                        } else {
                            pendingRecordingStart = true
                            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopRecording = {
                        val file = callRecorder.stop()
                        if (file != null) {
                            Toast.makeText(this@InCallActivity, getString(R.string.recording_saved, file.name), Toast.LENGTH_LONG).show()
                        }
                    },
                    selection = selection,
                    theme = callTheme
                )
            }
        }
    }
}
