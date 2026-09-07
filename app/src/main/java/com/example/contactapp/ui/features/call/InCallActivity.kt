package com.example.contactapp.ui.features.call

import android.os.Bundle
import android.telecom.Call
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

    override fun onStart() {
        super.onStart()
        isVisible = true
    }

    override fun onStop() {
        super.onStop()
        isVisible = false
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
                val canHold by CallManager.canHold.collectAsState()
                val canAddCall by CallManager.canAddCall.collectAsState()

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
                    onAddCall = { number ->
                        // Hold the current call first — Telecom generally does this
                        // automatically when a second call is placed, but making it explicit
                        // avoids depending on that OEM/carrier-specific behavior.
                        val current = CallManager.currentCall.value
                        if (current?.state == Call.STATE_ACTIVE) current.hold()
                        CallUtils.makeCall(this@InCallActivity, number)
                    },
                    onEndSecondaryCall = { secondaryCall?.disconnect() },
                    selection = selection,
                    theme = callTheme
                )
            }
        }
    }
}
