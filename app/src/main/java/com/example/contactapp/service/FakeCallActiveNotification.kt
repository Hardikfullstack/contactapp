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
import com.example.contactapp.ui.features.fakecall.FakeCallActivity

const val FAKE_CALL_ACTIVE_CHANNEL_ID = "fake_call_active_channel"
const val FAKE_CALL_ACTIVE_NOTIFICATION_ID = 1002

/**
 * Low-importance "ongoing call" notification shown while a fake call is active, so navigating
 * away from FakeCallActivity still leaves a way back (and to hang up) — mirrors the system's own
 * in-call status indicator that real Telecom calls get automatically. Lives on its own channel:
 * notification channel importance is immutable once created, and the ringing notification's
 * channel (FakeCallReceiver.CHANNEL_ID) is intentionally IMPORTANCE_HIGH for its full-screen
 * intent, so this can't reuse it.
 */
fun postActiveCallNotification(context: Context, callerName: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            FAKE_CALL_ACTIVE_CHANNEL_ID,
            context.getString(R.string.fake_call_active_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.fake_call_active_notification_channel_desc)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    val contentIntent = Intent(context, FakeCallActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    }
    val contentPendingIntent = PendingIntent.getActivity(
        context,
        FAKE_CALL_ACTIVE_NOTIFICATION_ID,
        contentIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val hangupIntent = Intent(context, FakeCallHangupReceiver::class.java)
    val hangupPendingIntent = PendingIntent.getBroadcast(
        context,
        FAKE_CALL_ACTIVE_NOTIFICATION_ID,
        hangupIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, FAKE_CALL_ACTIVE_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(callerName)
        .setContentText(context.getString(R.string.ongoing_call))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setOngoing(true)
        .setContentIntent(contentPendingIntent)
        .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.end_call), hangupPendingIntent)
        .build()

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    ) {
        NotificationManagerCompat.from(context).notify(FAKE_CALL_ACTIVE_NOTIFICATION_ID, notification)
    }
}

fun cancelActiveCallNotification(context: Context) {
    NotificationManagerCompat.from(context).cancel(FAKE_CALL_ACTIVE_NOTIFICATION_ID)
}

/** Hang-up action on the ongoing-call notification. */
class FakeCallHangupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FakeCallManager.endFromUi()
        // Defensive: if the Activity was killed, endFromUi() alone won't reach its
        // LaunchedEffect — cancel here too so no stale notification is left behind.
        cancelActiveCallNotification(context)
    }
}
