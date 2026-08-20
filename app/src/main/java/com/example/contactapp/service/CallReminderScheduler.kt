package com.example.contactapp.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.contactapp.util.CallReminder

/** Schedules/cancels the wall-clock AlarmManager alarm backing one [CallReminder]. */
object CallReminderScheduler {

    fun schedule(context: Context, reminder: CallReminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntentFor(context, reminder)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.timeMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.timeMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.timeMillis, pendingIntent)
            }
        } catch (e: Exception) {
            // SecurityException on some OEMs if exact-alarm permission was revoked after being
            // granted — the reminder stays in the persisted list either way, so it's still
            // visible/cancelable even if the underlying alarm didn't actually get scheduled.
        }
    }

    fun cancel(context: Context, reminder: CallReminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntentFor(context, reminder)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    // Extras don't factor into PendingIntent identity (only component/action/data/category do),
    // so the request code alone determines whether this matches an existing scheduled alarm —
    // reminder.id (unique per reminder) is reused as that code, letting cancel() reconstruct the
    // exact same PendingIntent schedule() created without needing the extras to match too.
    private fun pendingIntentFor(context: Context, reminder: CallReminder): PendingIntent {
        val intent = Intent(context, CallReminderReceiver::class.java).apply {
            putExtra(CallReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(CallReminderReceiver.EXTRA_CONTACT_NAME, reminder.contactName)
            putExtra(CallReminderReceiver.EXTRA_CONTACT_NUMBER, reminder.contactNumber)
            putExtra(CallReminderReceiver.EXTRA_CONTACT_PHOTO, reminder.photoUri)
            putExtra(CallReminderReceiver.EXTRA_NOTE, reminder.note)
        }
        return PendingIntent.getBroadcast(
            context, requestCode(reminder.id), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun requestCode(id: Long): Int = (id % Int.MAX_VALUE).toInt()
}
