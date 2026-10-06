package com.phone.contacts.util

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.phone.contacts.MainActivity
import com.phone.contacts.R
import com.phone.contacts.data.local.AppDatabase
import com.phone.contacts.data.local.ReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Schedules the "call back" notification for a reminder created on the After Call screen. Same as
 * contactapp: an exact alarm when SCHEDULE_EXACT_ALARM is granted, otherwise an inexact one. */
object CallReminderScheduler {
    // New ID: a channel keeps the importance it was first created with, so the old ID may be silent.
    private const val CHANNEL_ID = "call_reminders_alert_channel"
    private const val TIMING_TAG = "ReminderTiming"
    const val EXTRA_REMINDER_ID = "reminder_id"

    /** Same as contactapp: an alarm-clock alarm, which fires on the exact minute and is not held back
     * by Doze. Falls back to an inexact alarm only if the system refuses the exact one. */
    fun schedule(context: Context, reminder: ReminderEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, reminder.id)
        Log.d(TIMING_TAG, "scheduled id=${reminder.id} for=${reminder.timeMillis} now=${System.currentTimeMillis()}")
        try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(reminder.timeMillis, pending), pending)
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.timeMillis, pending)
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, reminderId))
    }

    private fun pendingIntent(context: Context, reminderId: Long): PendingIntent {
        val intent = Intent(context, CallReminderReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context, reminderId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    internal fun showNotification(context: Context, reminder: ReminderEntity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.call_reminder_channel_name), NotificationManager.IMPORTANCE_HIGH)
            )
        }
        // Tapping the notification opens the app, like the Contactstwo reference.
        val contentIntent = PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Direct call when CALL_PHONE is granted, otherwise the dialer - same as contactapp.
        val hasCallPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val callNowIntent = PendingIntent.getActivity(
            context,
            reminder.id.toInt() + 1,
            Intent(if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:${reminder.number}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(context.getString(R.string.call_reminder_title, reminder.name))
            // The note is the message - show it alone; the number only when there is no note.
            .setContentText(reminder.note.ifBlank { reminder.number })
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_call, context.getString(R.string.action_call), callNowIntent)
        // The note is optional - when present, show it in the expanded view with the number.
        if (reminder.note.isNotBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(reminder.note))
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(reminder.id.toInt(), builder.build())
        }
    }
}

class CallReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(CallReminderScheduler.EXTRA_REMINDER_ID, -1L)
        Log.d("ReminderTiming", "onReceive id=$reminderId now=${System.currentTimeMillis()}")
        if (reminderId < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(context).reminderDao()
                dao.getById(reminderId)?.let {
                    val now = System.currentTimeMillis()
                    Log.d("ReminderTiming", "fired id=$reminderId for=${it.timeMillis} now=$now late=${now - it.timeMillis}ms")
                    CallReminderScheduler.showNotification(context, it)
                    // Fired - drop it from the reminders list so only pending reminders stay listed.
                    dao.deleteById(reminderId)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
