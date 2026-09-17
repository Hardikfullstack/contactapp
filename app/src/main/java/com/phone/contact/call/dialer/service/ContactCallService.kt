package com.phone.contact.call.dialer.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.phone.contact.call.dialer.R
import com.phone.contact.call.dialer.domain.repository.ContactRepository
import com.phone.contact.call.dialer.ui.features.call.InCallActivity
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

    /** Promotes this service to foreground using whatever notification CallNotificationManager
     * just built — without this, the OS/OEM battery manager can kill this process mid-call (e.g.
     * swiping the app away from Recents), since being bound by Telecom alone isn't as strong a
     * "don't kill me" signal as an actual foreground service with a persistent notification. */
    private fun promoteToForeground() {
        val notification = callNotificationManager.currentNotificationOrNull() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(CallNotificationManager.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            startForeground(CallNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    override fun onCreate() {
        super.onCreate()
        CallManager.attachService(this)
    }

    override fun onDestroy() {
        CallManager.detachService(this)
        super.onDestroy()
    }

    /** Fires when the user swipes this app away from Recents — the call itself keeps going via
     * Telecom regardless, but some OEMs (MIUI especially) treat a Recents-swipe as a strong
     * "kill everything" signal that can clear even an ongoing/foreground notification along with
     * the task, leaving the user with no way to control an otherwise still-active call. Re-post it
     * immediately so there's something to tap back into — this runs before the process is
     * actually torn down, the same technique real dialer/media apps use to survive a task swipe. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (CallManager.currentCall.value != null) {
            promoteToForeground()
        }
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
        com.phone.contact.call.dialer.ads.AppOpenBackgroundReturnTrigger.isAdPaused = true

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
        // True only once a real saved contact is matched — drives the notification/in-call avatar's
        // choice between an initial letter and the generic "unknown person" icon, since falling back
        // to the raw number/Telecom guess is not a real contact name.
        var hasContactName = false
        // Captured once, the first time this call reaches STATE_ACTIVE — reused on every
        // subsequent re-render (Mute/Speaker toggle, resuming from hold) so the notification's
        // Chronometer keeps counting from the true connect time instead of restarting. Shifted
        // forward by however long each hold lasts (see STATE_HOLDING/STATE_ACTIVE below) so the
        // displayed elapsed time pauses during a hold instead of counting through it, matching
        // InCallScreen's own elapsed-time counter.
        var callConnectedAtMillis: Long? = null
        var holdStartedAtElapsedRealtime: Long? = null

        // Trigger Call Announcer and Flash Alert for incoming calls — only for the primary call.
        // A second (call-waiting) call ringing in while already on a call gets Telecom's own
        // native call-waiting tone; re-triggering flash/TTS-announce on top of an ongoing call
        // would just be disruptive.
        if (call.state == Call.STATE_RINGING && isFirstCall) {
            flashAlertManager.startBlinking()

            scope.launch {
                val contact = contactRepository.findContactByNumber(number)
                if (contact?.name != null) {
                    resolvedDisplayName = contact.name
                    hasContactName = true
                }
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
                    resolvedPhotoUri,
                    hasContactName
                )
                promoteToForeground()
            }
        } else if (isFirstCall) {
            // Outgoing call (dialing/connecting) — same "always show" treatment, reusing the
            // active-call notification (Mute/Speaker/End-Call) since there's nothing to
            // Answer/Decline on our own outgoing call.
            scope.launch {
                val contact = contactRepository.findContactByNumber(number)
                if (contact?.name != null) {
                    resolvedDisplayName = contact.name
                    hasContactName = true
                }
                resolvedPhotoUri = contact?.photoUri
                callNotificationManager.showActiveCallNotification(
                    resolvedDisplayName,
                    resolvedPhotoUri,
                    getString(R.string.dialing),
                    callConnectedAtMillis = null,
                    hasContactName = hasContactName
                )
                promoteToForeground()
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
                        // Chronometer's base is measured against SystemClock.elapsedRealtime()
                        // (time since boot), NOT System.currentTimeMillis() (wall-clock epoch) —
                        // passing the wrong clock here is exactly what showed a huge/negative,
                        // backwards-looking number instead of a normal ticking 00:00 upward.
                        if (callConnectedAtMillis == null) {
                            callConnectedAtMillis = android.os.SystemClock.elapsedRealtime()
                        }
                        // Resuming from a hold — push the base forward by however long the hold
                        // lasted, so the displayed elapsed time continues from where it paused
                        // instead of jumping ahead to include the hold gap.
                        holdStartedAtElapsedRealtime?.let { holdStart ->
                            callConnectedAtMillis = callConnectedAtMillis!! + (android.os.SystemClock.elapsedRealtime() - holdStart)
                            holdStartedAtElapsedRealtime = null
                        }
                        callNotificationManager.showActiveCallNotification(
                            resolvedDisplayName,
                            resolvedPhotoUri,
                            callConnectedAtMillis = callConnectedAtMillis,
                            hasContactName = hasContactName
                        )
                        promoteToForeground()
                    }
                    Call.STATE_HOLDING -> {
                        if (holdStartedAtElapsedRealtime == null) {
                            holdStartedAtElapsedRealtime = android.os.SystemClock.elapsedRealtime()
                        }
                        callNotificationManager.showActiveCallNotification(
                            resolvedDisplayName,
                            resolvedPhotoUri,
                            getString(R.string.on_hold),
                            callConnectedAtMillis = null,
                            hasContactName = hasContactName
                        )
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
        stopForeground(STOP_FOREGROUND_REMOVE)

        if (CallManager.secondaryCall.value != null) {
            // The primary ended but a second call is still up — promote it instead of
            // clearing CallManager down to no call at all.
            CallManager.promoteSecondaryToPrimary()
        } else {
            CallManager.updateCall(null)
        }
    }
}
