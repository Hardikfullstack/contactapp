package com.phone.contact.call.dialer.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.phone.contact.call.dialer.MainActivity
import com.phone.contact.call.dialer.R
import com.phone.contact.call.dialer.service.MissedCallActionReceiver

/** Notification channels/builders for the After Call feature — the full-screen-intent fallback
 * (used when a direct background startActivity() gets silently blocked on some OEMs) and the
 * "call back" reminder alert. */
object AfterCallNotificationHelper {

    const val AFTER_CALL_CHANNEL_ID = "after_call_screen_channel"
    const val AFTER_CALL_NOTIFICATION_ID = 9101
    const val OVERLAY_MISSING_NOTIFICATION_ID = 9102
    const val REMINDER_CHANNEL_ID = "after_call_reminder_channel"
    const val MISSED_CALL_CHANNEL_ID = "missed_call_channel"

    private fun ensureChannel(context: Context, channelId: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = when (channelId) {
            0 -> AFTER_CALL_CHANNEL_ID
            1 -> REMINDER_CHANNEL_ID
            else -> MISSED_CALL_CHANNEL_ID
        }
        if (manager.getNotificationChannel(id) == null) {
            val importance = if (channelId == 2) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_HIGH
            val name = if (channelId == 2) context.getString(R.string.missed_calls) else context.getString(R.string.feature_after_call_title)
            manager.createNotificationChannel(NotificationChannel(id, name, importance))
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    /** Matches CallNotificationManager's own light/dark-aware name-text color — a plain
     * ?android:attr/textColorPrimary can render as unreadable dark-on-dark in a RemoteViews since
     * it doesn't always resolve against the shade's actual current theme on every OEM. */
    private fun contactNameColor(context: Context): Int {
        val isNightMode = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        return if (isNightMode) 0xFFFFFFFF.toInt() else 0xFF212121.toInt()
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
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(label)
            .setContentText(context.getString(R.string.see_call_information))
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
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(context.getString(R.string.after_call_permission_missing_title))
            .setContentText(context.getString(R.string.after_call_permission_missing_desc))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.after_call_permission_missing_desc)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        NotificationManagerCompat.from(context).notify(OVERLAY_MISSING_NOTIFICATION_ID, builder.build())
    }

    /** Persistent record of a missed call in the shade — distinct from the transient AfterCall
     * overlay (which disappears in a few seconds): this is how the user checks "who called while
     * I was away" later, same as the reference/stock dialer, which always posts this regardless
     * of whether the overlay itself managed to show. Each call gets its own notification ID (not
     * a shared one) so multiple missed calls stack instead of replacing each other. */
    fun showMissedCallNotification(context: Context, number: String, contactName: String?) {
        ensureChannel(context, 2)
        if (!hasNotificationPermission(context)) return

        val notificationId = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        val label = contactName ?: number

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, notificationId, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Routed through CallUtils.makeCall() (via this broadcast) instead of firing a raw
        // ACTION_CALL intent directly — that guarantees the call goes through THIS app's own
        // Telecom flow (TelecomManager.placeCall() while we're the default dialer, so our own
        // InCallActivity shows) rather than possibly resolving to some other phone app.
        val callBackIntent = Intent(context, MissedCallActionReceiver::class.java).apply {
            action = MissedCallActionReceiver.ACTION_CALL_BACK
            putExtra(MissedCallActionReceiver.EXTRA_NUMBER, number)
        }
        val callPendingIntent = PendingIntent.getBroadcast(
            context, notificationId + 1, callBackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Wrapped in a chooser (instead of firing smsto: directly at whatever the default SMS app
        // is) so tapping Message shows the "Open with" picker — same as the reference dialer,
        // and lets the user pick WhatsApp/etc. instead of being locked to one messaging app.
        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$number")
        }
        val smsChooserIntent = Intent.createChooser(smsIntent, context.getString(R.string.message)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val smsPendingIntent = PendingIntent.getActivity(
            context, notificationId + 2, smsChooserIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Same custom-layout treatment (avatar + name/status on the LEFT) as the app's own
        // incoming/active-call notifications, instead of the plain default template — which
        // renders setLargeIcon() as a big circle on the RIGHT, duplicating/clashing with the
        // avatar. "Missed call" is always red here, matching the reference dialer's own styling.
        val hasContactName = contactName != null
        val avatarBitmap = NotificationAvatarUtils.createAvatarBitmap(context, null, label, hasContactName)
        val views = RemoteViews(context.packageName, R.layout.notification_call_missed).apply {
            setTextViewText(R.id.tvContactName, label)
            setTextViewText(R.id.tvCallStatus, context.getString(R.string.missed_call))
            setTextColor(R.id.tvContactName, contactNameColor(context))
            setImageViewBitmap(R.id.ivAvatar, avatarBitmap)
            setOnClickPendingIntent(R.id.btnMessage, smsPendingIntent)
            setOnClickPendingIntent(R.id.btnCallBack, callPendingIntent)
        }

        val builder = NotificationCompat.Builder(context, MISSED_CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(label)
            .setContentText(context.getString(R.string.missed_call))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            // Text-labeled actions for the expanded view — DecoratedCustomViewStyle appends these
            // below the custom layout automatically, alongside the icon-only buttons baked into
            // the collapsed layout above.
            .addAction(0, context.getString(R.string.call_back), callPendingIntent)
            .addAction(0, context.getString(R.string.message), smsPendingIntent)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
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
            .setSmallIcon(R.drawable.notification_icon)
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
