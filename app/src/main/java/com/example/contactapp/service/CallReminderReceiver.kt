package com.example.contactapp.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.contactapp.R
import com.example.contactapp.util.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fired by the AlarmManager alarm scheduled from the Call Reminder setup screen. Posts a
 * high-priority "time to call back" notification with a one-tap Call Now action. Unlike
 * FakeCallReceiver this is a reminder about a real call the user intends to place, not a
 * simulated incoming call — no ringing UI/Telecom connection involved, just a normal
 * dismissible notification.
 */
@AndroidEntryPoint
class CallReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val name = intent.getStringExtra(EXTRA_CONTACT_NAME) ?: return
        val number = intent.getStringExtra(EXTRA_CONTACT_NUMBER) ?: return
        val photoUri = intent.getStringExtra(EXTRA_CONTACT_PHOTO)

        // The reminder has now fired — drop it from the persisted list so the reminders
        // screen only ever shows what's still pending.
        preferenceManager.setCallReminders(preferenceManager.getCallReminders().filterNot { it.id == id })

        postNotification(context, id, name, number, photoUri)
    }

    private fun postNotification(context: Context, id: Long, name: String, number: String, photoUri: String?) {
        val notificationId = CallReminderScheduler.requestCode(id)

        val callNowIntent = Intent(context, CallReminderActionReceiver::class.java).apply {
            action = CallReminderActionReceiver.ACTION_CALL_NOW
            putExtra(EXTRA_CONTACT_NUMBER, number)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val callNowPendingIntent = PendingIntent.getBroadcast(
            context, notificationId, callNowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.call_reminder_notification_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.call_reminder_notification_channel_desc)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.call_reminder_notification_title, name))
            .setContentText(number)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_call, context.getString(R.string.call_now), callNowPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "call_reminder_channel"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_CONTACT_NAME = "contact_name"
        const val EXTRA_CONTACT_NUMBER = "contact_number"
        const val EXTRA_CONTACT_PHOTO = "contact_photo"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
