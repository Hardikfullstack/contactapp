package com.phone.contact.call.dialer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.phone.contact.call.dialer.util.CallUtils

/** Handles the "Call back" action on the missed-call notification via CallUtils.makeCall() —
 * not a raw ACTION_CALL intent — so the call always goes through this app's own Telecom flow
 * (TelecomManager.placeCall() while we're the default dialer) instead of possibly resolving to
 * some other phone app on the device. */
class MissedCallActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CALL_BACK = "com.phone.contact.call.dialer.ACTION_MISSED_CALL_BACK"
        const val EXTRA_NUMBER = "number"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CALL_BACK) return
        val number = intent.getStringExtra(EXTRA_NUMBER) ?: return
        CallUtils.makeCall(context, number)
    }
}
