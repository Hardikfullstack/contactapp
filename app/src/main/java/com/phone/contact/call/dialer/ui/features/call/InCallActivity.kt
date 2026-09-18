package com.phone.contact.call.dialer.ui.features.call

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import com.phone.contact.call.dialer.R
import com.phone.contact.call.dialer.domain.model.Contact
import com.phone.contact.call.dialer.domain.repository.ContactRepository
import com.phone.contact.call.dialer.service.AutoReplyManager
import com.phone.contact.call.dialer.service.CallManager
import com.phone.contact.call.dialer.service.CallNotificationManager
import com.phone.contact.call.dialer.service.SpamManager
import com.phone.contact.call.dialer.ui.theme.ContactAppTheme
import com.phone.contact.call.dialer.util.CallAccentColors
import com.phone.contact.call.dialer.util.CallButtonShape
import com.phone.contact.call.dialer.util.CallTheme
import com.phone.contact.call.dialer.util.CallUtils
import com.phone.contact.call.dialer.util.ContactCallBackgroundManager
import com.phone.contact.call.dialer.util.PreferenceManager
import com.phone.contact.call.dialer.util.WallpaperSelection
import com.phone.contact.call.dialer.util.isDarkOnCallScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
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
            val secondaryCall by CallManager.secondaryCall.collectAsState()
            val secondaryCallState by CallManager.secondaryCallState.collectAsState()
            val audioState by CallManager.audioState.collectAsState()
            val conferenceChildren by CallManager.conferenceChildren.collectAsState()

            // Which of the two simultaneous calls is shown "in front" (big avatar/name/state) is
            // decided purely from their live states, never by reassigning which Call object
            // Telecom/CallManager itself tracks as primary vs secondary — an earlier version did
            // that reassignment and an in-flight outgoing call's Call object getting replaced by
            // Telecom mid-dial flipped the display back to the original call. Deciding it fresh
            // from state on every recomposition means there's nothing to get out of sync: whichever
            // call isn't on hold is the one currently "in use" and belongs up front, matching the
            // reference dialer's own behavior after Add Call and after every Swap.
            val secondaryIsFront = secondaryCall != null &&
                callState == Call.STATE_HOLDING &&
                secondaryCallState != Call.STATE_HOLDING &&
                secondaryCallState != Call.STATE_RINGING
            val frontCall = if (secondaryIsFront) secondaryCall else call
            val backCall = if (secondaryIsFront) call else secondaryCall
            val frontCallState = if (secondaryIsFront) secondaryCallState else callState
            val backCallState = if (secondaryIsFront) callState else secondaryCallState

            // conferenceChildren is always seeded from whichever call CallManager tracks as
            // primary (`call`) — a conference is never the secondary call, only ever promoted to
            // primary. Once every participant but one has left, this isn't really a "conference"
            // display anymore — it should fall back to looking like an ordinary single call again,
            // showing that one remaining participant's own name/number instead of the parent
            // conference call's own (always blank) handle.
            val isRealConference = conferenceChildren.size > 1
            val lastRemainingChild = conferenceChildren.singleOrNull()
            // "is call the front" mirrors secondaryIsFront's own logic (call is front unless the
            // secondary is) — conferenceChildren only ever applies to `call`, never `secondaryCall`.
            val frontIsConference = !secondaryIsFront && isRealConference
            val backIsConference = secondaryIsFront && isRealConference
            val rawNumber = if (!secondaryIsFront && lastRemainingChild != null) {
                lastRemainingChild.details?.handle?.schemeSpecificPart
            } else {
                frontCall?.details?.handle?.schemeSpecificPart
            }

            // A one-time toast right when the conference actually forms — conferenceChildren
            // flips from empty to non-empty at exactly that moment, so this only fires once per
            // merge, not on every recomposition while it stays a conference.
            val hasConferenceNow = isRealConference
            LaunchedEffect(hasConferenceNow) {
                if (hasConferenceNow) {
                    android.widget.Toast.makeText(
                        this@InCallActivity,
                        getString(R.string.conference_call_connected),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // Safety net for canMerge: Call.Details.CAPABILITY_MERGE_CONFERENCE is supposed to
            // trigger onDetailsChanged when the network grants it, but that hasn't always been
            // observed to fire promptly (or at all) on every OEM/carrier telephony stack — a short
            // poll while two calls are up guarantees Merge enables itself as soon as it's actually
            // available, regardless of whether that event was missed.
            LaunchedEffect(call, secondaryCall) {
                while (call != null && secondaryCall != null) {
                    CallManager.refreshCapabilities()
                    delay(1000)
                }
            }

            // Resolved the same way as the primary/secondary callers — Telecom's children only
            // carry a raw number, not a saved contact's name/photo.
            var conferenceParticipants by remember { mutableStateOf<List<ConferenceParticipant>>(emptyList()) }
            LaunchedEffect(conferenceChildren) {
                conferenceParticipants = conferenceChildren.map { child ->
                    val childNumber = child.details?.handle?.schemeSpecificPart ?: ""
                    val contact = if (childNumber.isNotBlank()) contactRepository.findContactByNumber(childNumber) else null
                    ConferenceParticipant(
                        call = child,
                        number = contact?.number ?: childNumber,
                        name = contact?.name ?: childNumber,
                        photoUri = contact?.photoUri
                    )
                }
            }

            // Add Call opens the real Recents screen (AddCallPickerActivity) instead of a bare
            // dial pad — the picked number comes back here, where it's dialed as the second,
            // simultaneous call exactly like tapping a Recents entry's call button normally would.
            val addCallPickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val number = result.data?.getStringExtra(AddCallPickerActivity.EXTRA_PICKED_NUMBER)
                if (!number.isNullOrBlank()) {
                    val current = CallManager.currentCall.value
                    if (current != null && current.state == Call.STATE_ACTIVE) {
                        // Wait for the hold to actually be CONFIRMED (STATE_HOLDING) before dialing
                        // the second call — firing hold() and the new call back-to-back raced ahead
                        // of the modem on some devices/carriers, surfacing the system's own
                        // "Couldn't make call" error instead of placing it.
                        current.registerCallback(object : Call.Callback() {
                            override fun onStateChanged(call: Call, state: Int) {
                                if (state == Call.STATE_HOLDING || state == Call.STATE_DISCONNECTED) {
                                    call.unregisterCallback(this)
                                    CallUtils.makeCall(this@InCallActivity, number)
                                }
                            }
                        })
                        current.hold()
                    } else {
                        CallUtils.makeCall(this@InCallActivity, number)
                    }
                }
            }

            LaunchedEffect(callState, audioState?.route) {
                // Only take over screen control for an active call on the earpiece — while
                // ringing the user needs to see the Answer/Decline UI, and on speaker/Bluetooth/
                // wired-headset the phone isn't held to the face, so the screen should stay lit.
                val nearEar = callState == Call.STATE_ACTIVE && audioState?.route == CallAudioState.ROUTE_EARPIECE
                if (nearEar) {
                    // A short grace period before handing screen control to the proximity sensor —
                    // answering (from the notification or the on-screen button) puts a finger right
                    // near the earpiece/sensor at the exact moment the call goes ACTIVE, which would
                    // otherwise read as a false "near ear" and blank the screen the instant the user
                    // answers. Cancelled automatically (LaunchedEffect restarts) if the state changes
                    // again before this delay elapses — e.g. switching to speaker right away.
                    delay(800L)
                }
                applyProximityState(nearEar)
            }

            // Telecom's number format can differ from the contact's stored one — resolve via
            // the same PhoneLookup-backed matching the rest of the app trusts, not raw comparison.
            var resolvedNumber by remember { mutableStateOf<String?>(null) }
            var resolvedContact by remember { mutableStateOf<Contact?>(null) }
            // Telecom/carrier-provided caller ID (CNAP) for a number with no saved contact match —
            // ContactCallService already shows this same name in the incoming-call notification;
            // this surfaces it on the call screen too instead of just the bare number.
            var resolvedCallerIdName by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(rawNumber) {
                // Once resolved, hold onto it — rawNumber goes null right as the call disconnects,
                // briefly before this Activity finishes; resetting here would flash "Unknown".
                val raw = rawNumber ?: return@LaunchedEffect
                val contact = contactRepository.findContactByNumber(raw)
                resolvedContact = contact
                resolvedNumber = contact?.number ?: raw
                resolvedCallerIdName = if (contact == null) {
                    frontCall?.details?.callerDisplayName?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }

            // Same resolution for whichever call is currently the small "secondary" chip — the
            // reference dialer shows a saved contact's name there too, not just a bare number. A
            // conference has no single handle/number to resolve at all — when it's the one
            // demoted to "back" (e.g. a third call being added on top of it), the chip needs a
            // generic "Conference call" label instead, matching the reference dialer's own chip.
            val conferenceCallLabel = stringResource(R.string.conference_call)
            var backContactName by remember { mutableStateOf<String?>(null) }
            val backNumber = if (secondaryIsFront && lastRemainingChild != null) {
                lastRemainingChild.details?.handle?.schemeSpecificPart
            } else {
                backCall?.details?.handle?.schemeSpecificPart
            }
            LaunchedEffect(backNumber, backIsConference) {
                backContactName = if (backIsConference) {
                    conferenceCallLabel
                } else {
                    backNumber?.let { contactRepository.findContactByNumber(it)?.name }
                }
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
                    com.phone.contact.call.dialer.ads.AppOpenBackgroundReturnTrigger.isAdPaused = true
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
                val canMerge by CallManager.canMerge.collectAsState()

                InCallScreen(
                    contactName = resolvedContact?.name ?: resolvedCallerIdName,
                    number = resolvedNumber ?: rawNumber ?: "",
                    photoUri = resolvedContact?.photoUri,
                    callState = frontCallState,
                    onHangup = { frontCall?.let { CallManager.disconnectCall(it) } },
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
                    onToggleHold = { frontCall?.let { CallManager.toggleHoldForCall(it) } },
                    onPlayDtmf = { digit -> CallManager.playDtmfTone(digit) },
                    onStopDtmf = { CallManager.stopDtmfTone() },
                    canAddCall = canAddCall,
                    canMerge = canMerge,
                    secondaryCallNumber = backCall?.details?.handle?.schemeSpecificPart,
                    secondaryCallContactName = backContactName,
                    secondaryCallState = backCallState,
                    onAddCallClick = {
                        addCallPickerLauncher.launch(Intent(this@InCallActivity, AddCallPickerActivity::class.java))
                    },
                    onEndSecondaryCall = { backCall?.disconnect() },
                    onAnswerSecondaryCall = { CallManager.answerSecondaryCall() },
                    onDeclineSecondaryCall = { CallManager.rejectSecondaryCall() },
                    onAnswerAndEndOtherCall = { CallManager.answerAndEndOtherCall() },
                    onSwapCalls = { CallManager.swapCalls() },
                    onMergeCalls = { CallManager.mergeCalls() },
                    showConferenceInMainDisplay = frontIsConference,
                    conferenceParticipants = conferenceParticipants,
                    onDisconnectParticipant = { participantCall -> CallManager.disconnectConferenceParticipant(participantCall) },
                    selection = selection,
                    theme = callTheme
                )
            }
        }
    }
}
