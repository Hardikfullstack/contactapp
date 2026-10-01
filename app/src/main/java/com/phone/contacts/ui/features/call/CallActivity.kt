package com.phone.contacts.ui.features.call

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
import com.phone.contacts.service.CallManager
import com.phone.contacts.ui.theme.ContactsTheme
import com.phone.contacts.util.CallButtonStylePreferences
import com.phone.contacts.util.DeviceUtils
import com.phone.contacts.util.WallpaperPreferences
import com.phone.contacts.util.isDarkOnCallScreen

/** Shows over the lock screen for whichever call [com.phone.contacts.service.ContactsCallService]
 * just handed to [CallManager] — launched fresh from onCallAdded each time, but Telecom hands a
 * new call to the same running process, so this Activity itself just re-reads CallManager's
 * current state on each recomposition rather than owning any call state of its own. */
class CallActivity : ComponentActivity() {

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

                val secondaryIsFront = secondaryCall != null &&
                    callState == android.telecom.Call.STATE_HOLDING &&
                    secondaryCallState != android.telecom.Call.STATE_HOLDING &&
                    secondaryCallState != android.telecom.Call.STATE_RINGING

                val frontCall = if (secondaryIsFront) secondaryCall else call
                val backCall = if (secondaryIsFront) call else secondaryCall
                val frontCallState = if (secondaryIsFront) secondaryCallState else callState
                val backCallState = if (secondaryIsFront) callState else secondaryCallState
                val frontConnectedAtElapsedRealtime = if (secondaryIsFront) secondaryConnectedAtElapsedRealtime else connectedAtElapsedRealtime

                val activeChildren = conferenceChildren.filter { it.state != android.telecom.Call.STATE_DISCONNECTED }
                val isRealConference = activeChildren.size > 1
                val lastRemainingChild = activeChildren.singleOrNull()
                val frontIsConference = !secondaryIsFront && isRealConference
                val backIsConference = secondaryIsFront && isRealConference

                LaunchedEffect(isRealConference) {
                    if (isRealConference) {
                        android.widget.Toast.makeText(
                            this@CallActivity,
                            "Conference call connected",
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
                        swapCallButtons = swapCallButtons
                    )
                }
            }
        }
    }
}
