package com.phone.contacts.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.phone.contacts.ui.features.call.CallActivity

/**
 * The InCallService Telecom actually binds to for an incoming/outgoing/ongoing call. Declaring
 * this (see AndroidManifest.xml) is also required for the OS to treat this app as a fully-
 * qualified default-dialer candidate — some OEM telecom stacks (MIUI in particular) silently
 * cancel the ROLE_DIALER request for an app that has the ACTION_DIAL activity but no
 * InCallService.
 */
class ContactsCallService : InCallService() {

    /** Promotes this service to foreground using whatever notification CallNotificationManager
     * just built — without this, the OS/OEM battery manager can kill this process mid-call (e.g.
     * swiping the app away from Recents), since being bound by Telecom alone isn't as strong a
     * "don't kill me" signal as an actual foreground service with a persistent notification. */
    private fun promoteToForeground() {
        val notification = CallNotificationManager.currentNotificationOrNull() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(CallNotificationManager.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            startForeground(CallNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    override fun onCreate() {
        super.onCreate()
        CallManager.bindService(this)
        CallNotificationManager.onNotificationPosted = { promoteToForeground() }
    }

    override fun onDestroy() {
        CallNotificationManager.onNotificationPosted = null
        CallManager.unbindService(this)
        super.onDestroy()
    }

    /** Fires when the user swipes this app away from Recents — the call itself keeps going via
     * Telecom regardless, but some OEMs (MIUI especially) treat a Recents-swipe as a strong "kill
     * everything" signal that can clear even an ongoing/foreground notification along with the
     * task, leaving the user with no way to control an otherwise still-active call. Re-post it
     * immediately so there's something to tap back into — this is exactly the "notification should
     * survive the app being killed from the background" behavior this was built for. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (CallManager.currentCall.value != null) {
            promoteToForeground()
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)
        // NEW_TASK is required here regardless — starting an Activity from a Service (non-
        // Activity) context without it throws AndroidRuntimeException. NEW_DOCUMENT (+
        // CallActivity's own taskAffinity/documentLaunchMode in the manifest) gives the call
        // screen its own separate task/Recents entry instead of merging into the main app's task
        // — matching contactapp's own InCallActivity launch exactly.
        startActivity(
            Intent(this, CallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.onCallRemoved(call)
        if (CallManager.currentCall.value == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallManager.onAudioStateChanged(audioState)
    }
}
