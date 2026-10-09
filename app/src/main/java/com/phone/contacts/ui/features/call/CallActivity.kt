package com.phone.contacts.ui.features.call

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.phone.contacts.R
import com.phone.contacts.service.CallManager
import com.phone.contacts.ui.theme.ContactsTheme
import com.phone.contacts.util.CallButtonStylePreferences
import com.phone.contacts.util.DeviceUtils
import com.phone.contacts.util.WallpaperPreferences
import com.phone.contacts.util.isDarkOnCallScreen
import kotlinx.coroutines.delay

/** Shows over the lock screen for whichever call [com.phone.contacts.service.ContactsCallService]
 * just handed to [CallManager] — launched fresh from onCallAdded each time, but Telecom hands a
 * new call to the same running process, so this Activity itself just re-reads CallManager's
 * current state on each recomposition rather than owning any call state of its own. */
class CallActivity : AppCompatActivity() {

    // PROXIMITY_SCREEN_OFF_WAKE_LOCK is the standard way to turn the screen off when the phone is
    // held to the ear during an active earpiece call — without it, FLAG_KEEP_SCREEN_ON below leaves
    // the screen fully lit and touch-responsive against the user's face/cheek for the whole call.
    private var proximityWakeLock: PowerManager.WakeLock? = null

    private fun acquireProximityWakeLock() {
        if (proximityWakeLock?.isHeld == true) return
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            proximityWakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "Contacts:ProximityCallWakeLock"
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
        // Re-acquire if we're coming back to an already-active earpiece call — released
        // unconditionally in onStop() below, so this is the only thing that restores it.
        if (wantsProximityControl) applyProximityState(true)
    }

    override fun onStop() {
        super.onStop()
        // Once this screen isn't visible (user went Home, or swiped it away while the call keeps
        // running via ContactsCallService), fighting for proximity-based screen control no longer
        // makes sense — without this, the wake lock stayed held in the background and could turn
        // the screen off on the Home screen/another app if the phone was near the body.
        releaseProximityWakeLock()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseProximityWakeLock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        // Keeps the screen on (and off the system's normal timeout-then-lock path) for the whole
        // call by default - applyProximityState() clears this temporarily while the proximity
        // wake lock itself is in charge (phone held to the ear), then restores it once not.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enableEdgeToEdge()

        setContent {
            ContactsTheme(darkTheme = true) { // Always dark for the call UI, like the reference app
                val context = LocalContext.current
                remember { CallButtonStylePreferences.initialize(context) }
                val callButtonStyle by CallButtonStylePreferences.style
                val swapCallButtons by CallButtonStylePreferences.swapButtons
                remember { WallpaperPreferences.initialize(context) }
                val wallpaperSelection by WallpaperPreferences.selection
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                if (!DeviceUtils.isGestureNavigationEnabled(context)) {
                    insetsController.hide(WindowInsetsCompat.Type.navigationBars())
                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                // A light wallpaper needs dark status/nav bar icons, otherwise the default
                // always-light icons (matching the always-dark fallback background) go invisible.
                SideEffect {
                    val isLightAppearance = !wallpaperSelection.isDarkOnCallScreen()
                    insetsController.isAppearanceLightStatusBars = isLightAppearance
                    insetsController.isAppearanceLightNavigationBars = isLightAppearance
                }

                val call by CallManager.currentCall.collectAsState()
                val callState by CallManager.callState.collectAsState()
                val audioState by CallManager.audioState.collectAsState()
                val connectedAtElapsedRealtime by CallManager.connectedAtElapsedRealtime.collectAsState()
                val secondaryConnectedAtElapsedRealtime by CallManager.secondaryConnectedAtElapsedRealtime.collectAsState()
                val secondaryCall by CallManager.secondaryCall.collectAsState()
                val secondaryCallState by CallManager.secondaryCallState.collectAsState()
                val conferenceChildren by CallManager.conferenceChildren.collectAsState()
                val canMerge by CallManager.canMerge.collectAsState()
                val canAddCall by CallManager.canAddCall.collectAsState()

                val addCallLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val number = result.data?.getStringExtra(AddCallPickerActivity.EXTRA_PICKED_NUMBER)
                    if (result.resultCode == RESULT_OK && !number.isNullOrBlank()) {
                        CallManager.addCall(context, number)
                    }
                }

                LaunchedEffect(call) {
                    // Plain finish() leaves an empty task card behind in the system app-switcher,
                    // since this Activity runs in its own task (taskAffinity ".incall" in the
                    // manifest, needed for lock-screen visibility) — that's the "separate screen
                    // stays around" bug. Removing the task outright instead both clears that card
                    // and correctly hands focus back to whatever task the user was actually in
                    // before the call (e.g. this app's own MainActivity), matching contactapp's
                    // own InCallActivity fix for the identical setup.
                    if (call == null) finishAndRemoveTask()
                }

                val adConfig by com.phone.contacts.util.AppConfigStore.config.collectAsState()
                LaunchedEffect(adConfig) {
                    com.phone.contacts.ads.AdPlacements.adUnitId(adConfig?.result, com.phone.contacts.ads.AdType.NATIVE, slot = 20)?.let { primaryId ->
                        com.phone.contacts.ads.NativeAdCache.preload(context, primaryId)
                    }
                    com.phone.contacts.ads.AdPlacements.adUnitId(adConfig?.result, com.phone.contacts.ads.AdType.NATIVE, slot = 21)?.let { fallbackId ->
                        com.phone.contacts.ads.NativeAdCache.preload(context, fallbackId)
                    }
                }

                val secondaryIsFront = secondaryCall != null &&
                    callState == android.telecom.Call.STATE_HOLDING &&
                    secondaryCallState != android.telecom.Call.STATE_HOLDING &&
                    secondaryCallState != android.telecom.Call.STATE_RINGING

                val frontCall = if (secondaryIsFront) secondaryCall else call
                val backCall = if (secondaryIsFront) call else secondaryCall
                val frontCallState = if (secondaryIsFront) secondaryCallState else callState
                val backCallState = if (secondaryIsFront) callState else secondaryCallState
                val frontConnectedAtElapsedRealtime = if (secondaryIsFront) secondaryConnectedAtElapsedRealtime else connectedAtElapsedRealtime

                LaunchedEffect(frontCallState, audioState?.route) {
                    // Only take over screen control for an active call on the earpiece — while
                    // ringing the user needs to see the Answer/Decline UI, and on speaker/Bluetooth/
                    // wired-headset the phone isn't held to the face, so the screen should stay lit.
                    val nearEar = frontCallState == Call.STATE_ACTIVE && audioState?.route == CallAudioState.ROUTE_EARPIECE
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

                val activeChildren = conferenceChildren.filter { it.state != android.telecom.Call.STATE_DISCONNECTED }
                val isRealConference = activeChildren.size > 1
                val lastRemainingChild = activeChildren.singleOrNull()
                val frontIsConference = !secondaryIsFront && isRealConference
                val backIsConference = secondaryIsFront && isRealConference

                LaunchedEffect(isRealConference) {
                    if (isRealConference) {
                        android.widget.Toast.makeText(
                            this@CallActivity,
                            this@CallActivity.getString(R.string.toast_conference_connected),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                val frontNumber = if (!secondaryIsFront && lastRemainingChild != null) {
                    lastRemainingChild.details?.handle?.schemeSpecificPart
                } else {
                    frontCall?.details?.handle?.schemeSpecificPart
                }

                val backNumber = if (secondaryIsFront && lastRemainingChild != null) {
                    lastRemainingChild.details?.handle?.schemeSpecificPart
                } else {
                    backCall?.details?.handle?.schemeSpecificPart
                }

                if (call != null) {
                    CallScreen(
                        number = frontNumber ?: "",
                        callState = frontCallState,
                        audioState = audioState,
                        connectedAtElapsedRealtime = frontConnectedAtElapsedRealtime,
                        secondaryNumber = backNumber ?: "",
                        secondaryCallState = backCallState,
                        isConference = isRealConference,
                        showConferenceInMainDisplay = frontIsConference,
                        canMerge = canMerge,
                        canAddCall = canAddCall,
                        conferenceChildren = activeChildren,
                        onAnswer = { CallManager.answer() },
                        onDecline = { CallManager.reject() },
                        onHangup = { frontCall?.disconnect() },
                        onToggleMute = { CallManager.toggleMute() },
                        onToggleHold = {
                            if (frontCallState == android.telecom.Call.STATE_HOLDING) frontCall?.unhold() else frontCall?.hold()
                        },
                        onSelectAudioRoute = { route -> CallManager.setAudioRoute(route) },
                        onToggleSpeaker = { CallManager.toggleSpeaker() },
                        onPlayDtmf = { digit -> CallManager.playDtmfTone(digit) },
                        onStopDtmf = { CallManager.stopDtmfTone() },
                        onAddCallClick = { addCallLauncher.launch(Intent(context, AddCallPickerActivity::class.java)) },
                        onAnswerSecondary = { CallManager.answerSecondaryCall() },
                        onRejectSecondary = { CallManager.rejectSecondaryCall() },
                        onAnswerAndEndOther = { CallManager.answerSecondaryAndEndOther() },
                        onSwap = { CallManager.swapCalls() },
                        onMerge = { CallManager.mergeCalls() },
                        onEndSecondary = { backCall?.disconnect() },
                        onDisconnectParticipant = { participantCall -> CallManager.disconnectConferenceParticipant(participantCall) },
                        callButtonStyle = callButtonStyle,
                        swapCallButtons = swapCallButtons,
                        callerIdName = frontCall?.details?.callerDisplayName?.takeIf { it.isNotBlank() }
                    )
                }
            }
        }
    }
}
