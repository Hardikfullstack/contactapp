package com.example.contactapp.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.contactapp.R

/** Notification channels/builders for the After Call feature — the full-screen-intent fallback
 * (used when a direct background startActivity() gets silently blocked on some OEMs) and the
 * "call back" reminder alert. */
object AfterCallNotificationHelper {

    const val AFTER_CALL_CHANNEL_ID = "after_call_screen_channel"
    const val AFTER_CALL_NOTIFICATION_ID = 9101
    const val OVERLAY_MISSING_NOTIFICATION_ID = 9102
    const val REMINDER_CHANNEL_ID = "after_call_reminder_channel"

    private fun ensureChannel(context: Context, channelId: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = if (channelId == 0) AFTER_CALL_CHANNEL_ID else REMINDER_CHANNEL_ID
        if (manager.getNotificationChannel(id) == null) {
            manager.createNotificationChannel(
                NotificationChannel(id, context.getString(R.string.feature_after_call_title), NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Fallback delivery path for the After Call screen — some OEMs silently swallow a plain
     * background startActivity() call despite the app holding SYSTEM_ALERT_WINDOW. A full-screen
     * intent notification goes through NotificationManager instead (the same Android-blessed
     * mechanism incoming calls/alarms use), which is far less likely to be blocked. On a locked
     * device the system launches [contentIntent] automatically; otherwise this shows as a
     * tappable heads-up notification.
     */
    fun showAfterCallFullScreenNotification(context: Context, contentIntent: Intent, address: String, contactName: String?) {
        ensureChannel(context, 0)
        if (!hasNotificationPermission(context)) return

        val label = contactName ?: address
        val pendingIntent = PendingIntent.getActivity(
            context, AFTER_CALL_NOTIFICATION_ID, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, AFTER_CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(label)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
        NotificationManagerCompat.from(context).notify(AFTER_CALL_NOTIFICATION_ID, builder.build())
    }

    fun cancelAfterCallFullScreenNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(AFTER_CALL_NOTIFICATION_ID)
    }

    /**
     * Nudges the user instead of silently doing nothing when a call just ended but
     * SYSTEM_ALERT_WINDOW is missing — this can happen well after onboarding on MIUI, where
     * removing/re-adding the Default Dialer role has been observed to silently revoke it.
     */
    fun showOverlayPermissionMissingNotification(context: Context) {
        ensureChannel(context, 0)
        if (!hasNotificationPermission(context)) return

        // Goes straight to the overlay-permission settings screen — opening MainActivity here
        // does nothing useful once onboarding is already complete, since nothing about the normal
        // app UI re-surfaces this permission for the user to fix.
        val overlaySettingsIntent = Intent(
            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, OVERLAY_MISSING_NOTIFICATION_ID, overlaySettingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, AFTER_CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.after_call_permission_missing_title))
            .setContentText(context.getString(R.string.after_call_permission_missing_desc))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.after_call_permission_missing_desc)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        NotificationManagerCompat.from(context).notify(OVERLAY_MISSING_NOTIFICATION_ID, builder.build())
    }

    /** Fired by ReminderReceiver when an After Call "remind me to call back" alarm goes off. */
    fun showCallBackNotification(context: Context, reminderId: Long, number: String, contactName: String?, note: String) {
        ensureChannel(context, 1)
        if (!hasNotificationPermission(context)) return

        val label = contactName ?: number
        val title = String.format(context.getString(R.string.after_call_reminder_notification_title_template), label)
        val notificationId = reminderId.toInt()

        val hasCallPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val callIntent = Intent(if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .apply { if (note.isNotBlank()) setContentText(note) }
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .addAction(0, context.getString(R.string.call), pendingIntent)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
}
