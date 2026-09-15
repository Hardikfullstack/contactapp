package com.example.contactapp.service

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.example.contactapp.R
import com.example.contactapp.domain.repository.ContactRepository
import com.example.contactapp.ui.features.call.InCallActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        // Keeps the notification's Mute/Speaker icons in sync — covers both a toggle tapped from
        // the notification itself (whose own immediate refresh can race ahead of Telecom actually
        // applying the change) and external route changes (e.g. a headset connecting).
        if (CallManager.callState.value == Call.STATE_ACTIVE) {
            callNotificationManager.refreshActiveCallNotification()
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        // A call (incoming or outgoing) launching InCallActivity from the background is exactly
        // the kind of return-to-foreground AppOpenBackgroundReturnTrigger normally watches for —
        // skip its next check so a full-screen App Open ad never races on top of the call screen.
        com.example.contactapp.ads.AppOpenBackgroundReturnTrigger.isAdPaused = true

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
        var resolvedPhotoUri: String? = null

        // Trigger Call Announcer and Flash Alert for incoming calls — only for the primary call.
        // A second (call-waiting) call ringing in while already on a call gets Telecom's own
        // native call-waiting tone; re-triggering flash/TTS-announce on top of an ongoing call
        // would just be disruptive.
        if (call.state == Call.STATE_RINGING && isFirstCall) {
            flashAlertManager.startBlinking()

            scope.launch {
                val contact = contactRepository.findContactByNumber(number)
                if (contact?.name != null) resolvedDisplayName = contact.name
                resolvedPhotoUri = contact?.photoUri
                callAnnouncerManager.announceCall(resolvedDisplayName)

                val spamStatus = spamManager.checkSpamStatus(number)
                CallManager.setSpam(spamStatus.isSpam())

                // Shown for every incoming call, not just as a fallback — matches the reference
                // dialer app, whose own incoming-call notification has no "is the call screen
                // already visible" check either (see CallNotificationManager doc comment).
                callNotificationManager.showIncomingCallNotification(
                    resolvedDisplayName,
                    number,
                    spamStatus.isSpam(),
                    resolvedPhotoUri
                )
            }
        } else if (isFirstCall) {
            // Outgoing call (dialing/connecting) — same "always show" treatment, reusing the
            // active-call notification (Mute/Speaker/End-Call) since there's nothing to
            // Answer/Decline on our own outgoing call.
            scope.launch {
                val contact = contactRepository.findContactByNumber(number)
                if (contact?.name != null) resolvedDisplayName = contact.name
                resolvedPhotoUri = contact?.photoUri
                callNotificationManager.showActiveCallNotification(
                    resolvedDisplayName,
                    resolvedPhotoUri,
                    getString(R.string.dialing)
                )
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
                        callNotificationManager.showActiveCallNotification(resolvedDisplayName, resolvedPhotoUri)
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
