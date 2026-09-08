package com.example.contactapp.ui.features.call

import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.example.contactapp.domain.model.Contact
import com.example.contactapp.domain.repository.ContactRepository
import com.example.contactapp.service.AutoReplyManager
import com.example.contactapp.service.CallManager
import com.example.contactapp.service.CallNotificationManager
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

    companion object {
        /** True while this Activity is started — lets ContactCallService check, after a short
         * delay, whether its startActivity() call actually resulted in this screen showing, so
         * it knows whether the incoming-call notification fallback is needed. */
        var isVisible = false
    }

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
    lateinit var callNotificationManager: CallNotificationManager

    // Proximity-screen-off (like every stock dialer) so the screen turns off against the user's
    // ear during an active earpiece call — without it, FLAG_KEEP_SCREEN_ON below leaves the
    // screen fully lit and touch-responsive against the user's face/cheek for the whole call.
    private var proximityWakeLock: PowerManager.WakeLock? = null

    private fun acquireProximityWakeLock() {
        if (proximityWakeLock?.isHeld == true) return
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            proximityWakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "ContactApp:ProximityCallWakeLock"
            ).apply { acquire(10 * 60 * 1000L /*10 min safety timeout*/) }
        } catch (e: Exception) {
            // Some OEMs restrict this wake lock level despite reporting it as supported — the
            // call screen must never crash over a missing proximity-off nicety.
        }
    }

    private fun releaseProximityWakeLock() {
        proximityWakeLock?.let { if (it.isHeld) it.release() }
        proximityWakeLock = null
    }

    // Last computed "should the proximity sensor be controlling the screen" state, from the
    // call/audio-route LaunchedEffect below — re-applied in onStart() since the wake lock is
    // always fully released in onStop() (see there for why), so coming back to this screen with
    // the exact same call/audio state wouldn't otherwise re-trigger that LaunchedEffect at all.
    private var wantsProximityControl = false

    private fun applyProximityState(nearEar: Boolean) {
        wantsProximityControl = nearEar
        if (nearEar) {
            // FLAG_KEEP_SCREEN_ON would otherwise fight the proximity sensor's own screen-off —
            // hand full control to the wake lock while it's in charge.
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            acquireProximityWakeLock()
        } else {
            releaseProximityWakeLock()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onStart() {
        super.onStart()
        isVisible = true
        // Re-acquire if we're coming back to an already-active earpiece call — released
        // unconditionally in onStop() below, so this is the only thing that restores it.
        if (wantsProximityControl) applyProximityState(true)
    }

    override fun onStop() {
        super.onStop()
        isVisible = false
        // Once this screen isn't visible (user went Home, or swiped it away while the call
        // keeps running via ContactCallService), fighting for proximity-based screen control no
        // longer makes sense — without this, the wake lock stayed held in the background and
        // could turn the screen off on the Home screen/another app if the phone was near the body.
        releaseProximityWakeLock()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseProximityWakeLock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The incoming-call notification is a fallback for when the direct launch gets blocked
        // (OEM restrictions) — reaching onCreate() means it worked, so cancel the now-redundant one.
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
            val call by CallManager.currentCall.collectAsState()
            val rawNumber = call?.details?.handle?.schemeSpecificPart
            val secondaryCall by CallManager.secondaryCall.collectAsState()
            val audioState by CallManager.audioState.collectAsState()

            LaunchedEffect(callState, audioState?.route) {
                // Only take over screen control for an active call on the earpiece — while
                // ringing the user needs to see the Answer/Decline UI, and on speaker/Bluetooth/
                // wired-headset the phone isn't held to the face, so the screen should stay lit.
                val nearEar = callState == Call.STATE_ACTIVE && audioState?.route == CallAudioState.ROUTE_EARPIECE
                applyProximityState(nearEar)
            }

            // Telecom's number format can differ from the contact's stored one — resolve via
            // the same PhoneLookup-backed matching the rest of the app trusts, not raw comparison.
            var resolvedNumber by remember { mutableStateOf<String?>(null) }
            var resolvedContact by remember { mutableStateOf<Contact?>(null) }
            LaunchedEffect(rawNumber) {
                // Once resolved, hold onto it — rawNumber goes null right as the call disconnects,
                // briefly before this Activity finishes; resetting here would flash "Unknown".
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
                // The incoming-call notification posts asynchronously and can race past onCreate()'s
                // one-time cancel, leaving it stuck — re-cancel here, except on STATE_ACTIVE where
                // ContactCallService's own "ongoing call" notification (same ID) could get wiped instead.
                if (callState != Call.STATE_ACTIVE) {
                    callNotificationManager.cancelNotification()
                }

                if (callState == Call.STATE_DISCONNECTED) {
                    // Finishing this screen returns focus to whatever was behind it (e.g.
                    // MainActivity), which AppOpenBackgroundReturnTrigger would otherwise treat
                    // as an ordinary app-switch-back and could show an App Open ad right as the
                    // user just finished a call — skip that one return.
                    com.example.contactapp.ads.AppOpenBackgroundReturnTrigger.isAdPaused = true
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
                val canHold by CallManager.canHold.collectAsState()
                val canAddCall by CallManager.canAddCall.collectAsState()
                val secondaryCallState by CallManager.secondaryCallState.collectAsState()

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
                    canHold = canHold,
                    onToggleHold = { CallManager.toggleHold() },
                    onPlayDtmf = { digit -> CallManager.playDtmfTone(digit) },
                    onStopDtmf = { CallManager.stopDtmfTone() },
                    canAddCall = canAddCall,
                    secondaryCallNumber = secondaryCall?.details?.handle?.schemeSpecificPart,
                    secondaryCallState = secondaryCallState,
                    onAddCall = { number ->
                        // Hold the current call first — Telecom generally does this
                        // automatically when a second call is placed, but making it explicit
                        // avoids depending on that OEM/carrier-specific behavior.
                        val current = CallManager.currentCall.value
                        if (current?.state == Call.STATE_ACTIVE) current.hold()
                        CallUtils.makeCall(this@InCallActivity, number)
                    },
                    onEndSecondaryCall = { secondaryCall?.disconnect() },
                    onAnswerSecondaryCall = { CallManager.answerSecondaryCall() },
                    onDeclineSecondaryCall = { CallManager.rejectSecondaryCall() },
                    onSwapCalls = { CallManager.swapCalls() },
                    selection = selection,
                    theme = callTheme
                )
            }
        }
    }
}
