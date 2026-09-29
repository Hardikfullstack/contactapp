package com.phone.contacts.service

import android.content.Intent
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

    override fun onCreate() {
        super.onCreate()
        CallManager.bindService(this)
    }

    override fun onDestroy() {
        CallManager.unbindService(this)
        super.onDestroy()
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
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallManager.onAudioStateChanged(audioState)
    }
}
