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
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                if (!DeviceUtils.isGestureNavigationEnabled(context)) {
                    insetsController.hide(WindowInsetsCompat.Type.navigationBars())
                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false

                val call by CallManager.currentCall.collectAsState()
                val callState by CallManager.callState.collectAsState()
                val audioState by CallManager.audioState.collectAsState()
                val connectedAtElapsedRealtime by CallManager.connectedAtElapsedRealtime.collectAsState()
                val secondaryCall by CallManager.secondaryCall.collectAsState()
                val secondaryCallState by CallManager.secondaryCallState.collectAsState()
                val conferenceChildren by CallManager.conferenceChildren.collectAsState()
                val canMerge by CallManager.canMerge.collectAsState()

                val addCallLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val number = result.data?.getStringExtra(AddCallPickerActivity.EXTRA_PICKED_NUMBER)
                    if (result.resultCode == RESULT_OK && !number.isNullOrBlank()) {
                        CallManager.addCall(context, number)
                    }
                }

                LaunchedEffect(call) {
                    if (call == null) finish()
                }

                if (call != null) {
                    CallScreen(
                        number = call?.details?.handle?.schemeSpecificPart ?: "",
                        callState = callState,
                        audioState = audioState,
                        connectedAtElapsedRealtime = connectedAtElapsedRealtime,
                        secondaryNumber = secondaryCall?.details?.handle?.schemeSpecificPart ?: "",
                        secondaryCallState = secondaryCallState,
                        isConference = conferenceChildren.isNotEmpty(),
                        canMerge = canMerge,
                        onAnswer = { CallManager.answer() },
                        onDecline = { CallManager.reject() },
                        onHangup = { CallManager.disconnect() },
                        onToggleMute = { CallManager.toggleMute() },
                        onToggleHold = { CallManager.toggleHold() },
                        onSelectAudioRoute = { route -> CallManager.setAudioRoute(route) },
                        onToggleSpeaker = { CallManager.toggleSpeaker() },
                        onPlayDtmf = { digit -> CallManager.playDtmfTone(digit) },
                        onStopDtmf = { CallManager.stopDtmfTone() },
                        onAddCallClick = { addCallLauncher.launch(Intent(context, AddCallPickerActivity::class.java)) },
                        onAnswerSecondary = { CallManager.answerSecondaryCall() },
                        onRejectSecondary = { CallManager.rejectSecondaryCall() },
                        onSwap = { CallManager.swapCalls() },
                        onMerge = { CallManager.mergeCalls() },
                        callButtonStyle = callButtonStyle,
                        swapCallButtons = swapCallButtons
                    )
                }
            }
        }
    }
}
