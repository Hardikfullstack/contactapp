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

        // Telecom hands over the merged conference as either a call already reporting this
        // capability, or one whose children list is already populated — either way, this is not
        // a plain second call and needs its own promotion path (see promoteToConference's doc).
        val isConferenceCall = call.details.can(Call.Details.CAPABILITY_MANAGE_CONFERENCE) || call.children.isNotEmpty()
        val isFirstCall = CallManager.currentCall.value == null

        // NOTE: an earlier version also promoted an outgoing Add-Call leg straight to primary
        // here (demoting the previous call to secondary) to match the stock dialer's own display.
        // Reverted — Telecom can replace an in-flight outgoing call's Call object once the network
        // confirms it, and onCallRemoved()'s existing "primary ended, promote secondary" cleanup
        // then fired for that transient object, flipping the display back to the original call.
        // Second calls stay secondary-only (as before) until that swap can be redone safely.
        when {
            isConferenceCall -> CallManager.promoteToConference(call)
            isFirstCall -> CallManager.updateCall(call)
            else -> CallManager.addSecondaryCall(call)
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
        // Chronometer keeps counting from the true connect time instead of restarting. Keeps
        // ticking straight through a hold too (never shifted/reset), matching InCallScreen's own
        // elapsed-time counter and the reference/stock dialer's own behavior.
        var callConnectedAtMillis: Long? = null

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
        } else if (call.state == Call.STATE_RINGING) {
            // Call-waiting: a second call ringing in while already on a call. Telecom gives it its
            // own native waiting tone, but this app's OWN notification only ever showed for the
            // first call — so a call-waiting call previously got no notification of its own at
            // all. Show it as a separate, non-full-screen heads-up alongside the existing
            // ongoing-call notification, matching the reference dialer's two-stacked-pills look.
            scope.launch {
                val contact = contactRepository.findContactByNumber(number)
                callNotificationManager.showCallWaitingNotification(
                    contact?.name ?: number,
                    number,
                    contact?.photoUri,
                    hasContactName = contact?.name != null
                )
            }
            call.registerCallback(object : Call.Callback() {
                override fun onStateChanged(call: Call, state: Int) {
                    if (state != Call.STATE_RINGING) {
                        callNotificationManager.cancelCallWaitingNotification()
                        call.unregisterCallback(this)
                    }
                }
            })
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
                        callNotificationManager.showActiveCallNotification(
                            resolvedDisplayName,
                            resolvedPhotoUri,
                            callConnectedAtMillis = callConnectedAtMillis,
                            hasContactName = hasContactName,
                            isConference = call.children.size > 1
                        )
                        promoteToForeground()
                    }
                    Call.STATE_HOLDING -> {
                        // Keep the same Chronometer base ticking straight through the hold —
                        // matching InCallScreen's own timer — instead of freezing it on a static
                        // "On hold" label.
                        callNotificationManager.showActiveCallNotification(
                            resolvedDisplayName,
                            resolvedPhotoUri,
                            callConnectedAtMillis = callConnectedAtMillis,
                            hasContactName = hasContactName,
                            isConference = call.children.size > 1
                        )
                    }
                    Call.STATE_DISCONNECTED -> {
                        callNotificationManager.cancelNotification()
                    }
                }
            }

            override fun onChildrenChanged(call: Call, children: MutableList<Call>) {
                // Telecom can populate this same call's children directly (no separate
                // onCallAdded for the conference) — re-render the notification the moment that
                // happens too, not just on the next state transition, so the "Conference call"
                // icon/title switch shows up immediately when a merge completes.
                if (call !== CallManager.currentCall.value) return
                if (call.state != Call.STATE_ACTIVE && call.state != Call.STATE_HOLDING) return
                callNotificationManager.showActiveCallNotification(
                    resolvedDisplayName,
                    resolvedPhotoUri,
                    callConnectedAtMillis = callConnectedAtMillis,
                    hasContactName = hasContactName,
                    isConference = children.size > 1
                )
            }
        })

        // Show the In-Call UI
        val intent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)

        if (call === CallManager.secondaryCall.value) {
            // Only the second leg ended — the primary call is still up, so none of the
            // ringtone/notification/flash cleanup below applies. Still a safety net for the
            // call-waiting notification itself: if this call is removed straight from RINGING
            // (e.g. the caller hangs up before being answered) without an onStateChanged first.
            callNotificationManager.cancelCallWaitingNotification()
            CallManager.clearSecondaryCall()
            return
        }

        if (call !== CallManager.currentCall.value) {
            // Not the tracked primary or secondary — e.g. one of the two original legs Telecom
            // removed after absorbing it into a new conference Call object via
            // promoteToConference(). That conference is still ongoing under a different Call, so
            // there's nothing to clean up for this one.
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
