package com.phone.contact.call.dialer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.phone.contact.call.dialer.util.CallUtils

/** Handles the "Call Now" action on a fired call-reminder notification. */
class CallReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra(CallReminderReceiver.EXTRA_CONTACT_NUMBER) ?: return
        val notificationId = intent.getIntExtra(CallReminderReceiver.EXTRA_NOTIFICATION_ID, -1)
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
        CallUtils.makeCall(context, number)
    }

    companion object {
        const val ACTION_CALL_NOW = "com.phone.contact.call.dialer.CALL_REMINDER_CALL_NOW"
    }
}
