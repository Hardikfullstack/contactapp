package com.example.contactapp.service

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.example.contactapp.domain.repository.ContactRepository
import com.example.contactapp.ui.features.call.InCallActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ContactCallService : InCallService() {

    @Inject
    lateinit var callAnnouncerManager: CallAnnouncerManager

    @Inject
    lateinit var flashAlertManager: FlashAlertManager

    @Inject
    lateinit var callNotificationManager: CallNotificationManager

    @Inject
    lateinit var spamManager: SpamManager

    @Inject
    lateinit var contactRepository: ContactRepository

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        CallManager.attachService(this)
    }

    override fun onDestroy() {
        CallManager.detachService(this)
        super.onDestroy()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallManager.onAudioStateChanged(audioState)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        val isFirstCall = CallManager.currentCall.value == null
        if (isFirstCall) {
            CallManager.updateCall(call)
        } else {
            // A call already exists — this is the second leg from "Add call", not a
            // replacement for the primary call.
            CallManager.addSecondaryCall(call)
        }

        val number = call.details.handle?.schemeSpecificPart ?: "Unknown"

        // Resolved once and reused everywhere below — call.details.callerDisplayName is
        // Telecom's own caller-ID guess, which is almost always null for a normal saved contact
        // since Telecom doesn't consult this app's Contacts data on its own; a real lookup here
        // is what actually surfaces the saved name instead of silently falling back to the number.
        var resolvedDisplayName = call.details.callerDisplayName ?: number

        // Trigger Call Announcer and Flash Alert for incoming calls
        if (call.state == Call.STATE_RINGING) {
            flashAlertManager.startBlinking()

            scope.launch {
                val contactName = contactRepository.findContactByNumber(number)?.name
                if (contactName != null) resolvedDisplayName = contactName
                callAnnouncerManager.announceCall(resolvedDisplayName)

                val spamStatus = spamManager.checkSpamStatus(number)
                CallManager.setSpam(spamStatus.isSpam())

                // A real fallback, not a redundant duplicate — this app's InCallService declares
                // IN_CALL_SERVICE_UI, so startActivity() below is expected to reliably show the
                // call screen. Only post the Answer/Decline notification if, after giving it a
                // moment, that screen genuinely never came up (rare OEM restriction).
                delay(1000L)
                if (!InCallActivity.isVisible) {
                    callNotificationManager.showIncomingCallNotification(
                        resolvedDisplayName,
                        number,
                        spamStatus.isSpam()
                    )
                }
            }
        }

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                // CallManager tracks state via its own internal callback — this one only drives
                // notification/flash-alert side effects for the primary call.
                if (call !== CallManager.currentCall.value) return

                when (state) {
                    Call.STATE_ACTIVE -> {
                        flashAlertManager.stopBlinking()
                        callNotificationManager.showActiveCallNotification(resolvedDisplayName)
                    }
                    Call.STATE_DISCONNECTED -> {
                        callNotificationManager.cancelNotification()
                    }
                }
            }
        })

        // Show the In-Call UI
        val intent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)

        if (call === CallManager.secondaryCall.value) {
            // Only the second leg ended — the primary call is still up, so none of the
            // ringtone/notification/flash cleanup below applies.
            CallManager.clearSecondaryCall()
            return
        }

        callAnnouncerManager.stopAnnouncing()
        flashAlertManager.stopBlinking()
        callNotificationManager.cancelNotification()

        if (CallManager.secondaryCall.value != null) {
            // The primary ended but a second call is still up — promote it instead of
            // clearing CallManager down to no call at all.
            CallManager.promoteSecondaryToPrimary()
        } else {
            CallManager.updateCall(null)
        }
    }
}
