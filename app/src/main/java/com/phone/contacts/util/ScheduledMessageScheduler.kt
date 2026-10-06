package com.phone.contacts.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.phone.contacts.data.local.AppDatabase
import com.phone.contacts.data.local.ScheduledMessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Schedules an SMS queued on the After Call screen. Same alarm rules as the call reminders: an exact
 * alarm when exact alarms are allowed, otherwise an inexact one. */
object ScheduledMessageScheduler {
    const val EXTRA_MESSAGE_ID = "scheduled_message_id"

    fun schedule(context: Context, message: ScheduledMessageEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, message.id)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, message.timeMillis, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, message.timeMillis, pending)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, message.timeMillis, pending)
        }
    }

    fun cancel(context: Context, messageId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, messageId))
    }

    private fun pendingIntent(context: Context, messageId: Long): PendingIntent {
        val intent = Intent(context, ScheduledMessageReceiver::class.java).apply {
            putExtra(EXTRA_MESSAGE_ID, messageId)
        }
        return PendingIntent.getBroadcast(
            context, messageId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** Fires at the scheduled time: sends the SMS (when SEND_SMS is granted) and removes the entry from the list. */
class ScheduledMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ScheduledMessageScheduler.EXTRA_MESSAGE_ID, -1L)
        if (id < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(context).scheduledMessageDao()
                dao.getById(id)?.let { message ->
                    if (MessageUtils.hasSendSmsPermission(context)) {
                        MessageUtils.sendSmsDirectly(context, message.number, message.body)
                    }
                    dao.deleteById(id)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
