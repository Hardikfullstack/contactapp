package com.phone.contacts.ui.features.aftercall

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.phone.contacts.R
import com.phone.contacts.ui.theme.ContactsTheme

/** Shown right after an answered call ends. Android often refuses to start an activity from the
 * background at the exact moment a call drops, so [launch] also posts a full-screen-intent
 * notification - the system opens this screen from that notification, which it allows. */
class AfterCallActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent system bars: the screen background shows behind the navigation bar too.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        val number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME)
        val durationSeconds = intent.getLongExtra(EXTRA_DURATION_SECONDS, 0L)
        setContent {
            ContactsTheme {
                AfterCallScreen(
                    number = number,
                    displayName = name,
                    durationSeconds = durationSeconds,
                    onFinish = { finishAndRemoveTask() }
                )
            }
        }
    }

    // Home/Recents pressed while this screen is showing closes it, same as the contactapp's
    // after-call screen — it shouldn't linger in the background or task switcher.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isFinishing) finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_NUMBER = "after_call_number"
        const val EXTRA_NAME = "after_call_name"
        const val EXTRA_DURATION_SECONDS = "after_call_duration_seconds"
        // New ID on purpose: a channel's sound is fixed once created, so the previous
        // "after_call_alert_channel" (which plays the default notification sound) can't be made
        // silent in place — a fresh ID is the only way the after-call alert stops making noise.
        private const val CHANNEL_ID = "after_call_silent_channel"
        private const val OLD_CHANNEL_ID = "after_call_channel"
        private const val PREVIOUS_CHANNEL_ID = "after_call_alert_channel"
        private const val NOTIFICATION_ID = 2003

        fun intentFor(context: Context, number: String, name: String?, durationSeconds: Long): Intent =
            Intent(context, AfterCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_NUMBER, number)
                putExtra(EXTRA_NAME, name)
                putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
            }

        /** Posted right away when the call ends - the system opens this screen from it even when the
         * background-start restriction would block a direct startActivity(). */
        fun postFullScreenNotification(context: Context, intent: Intent, label: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.deleteNotificationChannel(OLD_CHANNEL_ID)
                manager.deleteNotificationChannel(PREVIOUS_CHANNEL_ID)
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.after_call_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                        setSound(null, null)
                        enableVibration(false)
                    }
                )
            }
            val pending = PendingIntent.getActivity(
                context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_small)
                .setContentTitle(context.getString(R.string.call_ended))
                .setContentText(label)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setFullScreenIntent(pending, true)
                .setContentIntent(pending)
                .setSilent(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
