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
import android.telecom.Call
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.contactapp.R
import com.example.contactapp.ui.features.call.InCallActivity
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    companion object {
        const val INCOMING_CALL_CHANNEL_ID = "incoming_call_channel"
        const val ACTIVE_CALL_CHANNEL_ID = "active_call_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_ANSWER = "com.example.contactapp.ACTION_ANSWER"
        const val ACTION_DECLINE = "com.example.contactapp.ACTION_DECLINE"
        const val ACTION_HANGUP = "com.example.contactapp.ACTION_HANGUP"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val incomingChannel = NotificationChannel(
                INCOMING_CALL_CHANNEL_ID,
                context.getString(R.string.incoming_call),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.incoming_call_status)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val activeChannel = NotificationChannel(
                ACTIVE_CALL_CHANNEL_ID,
                context.getString(R.string.ongoing_call),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.ongoing_call)
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(incomingChannel)
            manager.createNotificationChannel(activeChannel)
        }
    }

    fun showIncomingCallNotification(callerName: String, number: String, isSpam: Boolean) {
        val fullScreenIntent = Intent(context, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = Intent(context, CallActionReceiver::class.java).apply { action = ACTION_ANSWER }
        val answerPendingIntent = PendingIntent.getBroadcast(
            context, 1, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(context, CallActionReceiver::class.java).apply { action = ACTION_DECLINE }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context, 2, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isSpam) context.getString(R.string.spam_warning) else callerName
        val text = if (isSpam) number else context.getString(R.string.incoming_call)

        val builder = NotificationCompat.Builder(context, INCOMING_CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_call, context.getString(R.string.answer_call), answerPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.decline_call_short), declinePendingIntent)

        if (checkNotificationPermission()) {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }
    }

    fun showActiveCallNotification(callerName: String) {
        val contentIntent = Intent(context, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangupIntent = Intent(context, CallActionReceiver::class.java).apply { action = ACTION_HANGUP }
        val hangupPendingIntent = PendingIntent.getBroadcast(
            context, 3, hangupIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, ACTIVE_CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(callerName)
            .setContentText(context.getString(R.string.ongoing_call))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.end_call), hangupPendingIntent)

        if (checkNotificationPermission()) {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}

@AndroidEntryPoint
class CallActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var autoReplyManager: AutoReplyManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CallNotificationManager.ACTION_ANSWER -> {
                CallManager.answer()
                // Ensure the UI opens immediately when answering from a heads-up notification.
                val activityIntent = Intent(context, InCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                context.startActivity(activityIntent)
            }
            CallNotificationManager.ACTION_DECLINE -> {
                // Same "decline sends the auto-reply" behavior as the in-call swipe gesture
                // (InCallActivity) — this is just the other delivery path for the same action.
                val number = CallManager.currentCall.value?.details?.handle?.schemeSpecificPart
                CallManager.reject()
                number?.let { autoReplyManager.sendReplyIfEnabled(it) }
            }
            CallNotificationManager.ACTION_HANGUP -> CallManager.disconnect()
        }
    }
}
