package com.example.contactapp.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.contactapp.R
import com.example.contactapp.ui.features.fakecall.FakeCallActivity
import com.example.contactapp.util.NotificationAvatarUtils
import com.example.contactapp.util.PhoneNumberFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "FakeCallDebug"

/**
 * Fired by the AlarmManager alarm scheduled in FakeCallSetupScreen. Delivery has two layers:
 *
 * 1. A best-effort attempt to register the call as a self-managed Telecom call (see
 *    [FakeCallConnectionService]) — Telecom-driven calls are exempt from Android's restriction
 *    on starting an Activity from a background app, so when it succeeds it pops the screen open
 *    immediately even while the phone is unlocked and in active use.
 * 2. A high-priority full-screen-intent notification — a true fallback, not a redundant
 *    duplicate: [android.telecom.TelecomManager.addNewIncomingCall] is fire-and-forget, so
 *    Telecom can silently decline the connection (stale/unregistered account, OEM policy, etc.)
 *    without ever throwing back to the caller. After a short delay, this only posts if
 *    [com.example.contactapp.ui.features.fakecall.FakeCallActivity] genuinely never became
 *    visible — same pattern as the real incoming-call fallback in ContactCallService.
 */
@AndroidEntryPoint
class FakeCallReceiver : BroadcastReceiver() {

    @Inject
    lateinit var spamManager: SpamManager

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("caller_name") ?: "Unknown"
        val number = intent.getStringExtra("caller_number") ?: "0000000000"
        val photoUri = intent.getStringExtra("caller_photo")

        // A fake call popping FakeCallActivity from the background is the same kind of
        // return-to-foreground AppOpenBackgroundReturnTrigger watches for as a real incoming call
        // (see ContactCallService.onCallAdded) — without this, an App Open ad can race on top of
        // the fake call screen right as it appears.
        com.example.contactapp.ads.AppOpenBackgroundReturnTrigger.isAdPaused = true

        try {
            deliverAsTelecomCall(context, name, number, photoUri)
        } catch (e: Exception) {
            Log.e(TAG, "onReceive: deliverAsTelecomCall FAILED — ${e.javaClass.simpleName}: ${e.message}", e)
        }

        // goAsync() holds process priority past onReceive() returning — Telecom's follow-up
        // onCreateIncomingConnection() call is a separate async binder transaction an OEM process
        // manager could otherwise freeze/drop before it reaches app code.
        val pendingResult = goAsync()
        scope.launch {
            try {
                // A real fallback, not a redundant duplicate — deliverAsTelecomCall()/Telecom's own
                // onShowIncomingCallUi() is expected to reliably show the fake call screen. Only post
                // the ringing notification if, after giving it a moment, that screen genuinely never
                // came up (Telecom rejected the connection, or an OEM background-start restriction).
                delay(1000L)
                if (!FakeCallActivity.isVisible) {
                    val status = spamManager.checkSpamStatus(number)
                    deliverAsNotification(context, name, number, photoUri, status.isSpam())
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun deliverAsTelecomCall(context: Context, name: String, number: String, photoUri: String?) {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        FakeCallConnectionService.registerPhoneAccount(context)

        val callInfo = Bundle().apply {
            putString(FakeCallConnectionService.EXTRA_CALLER_NAME, name)
            putString(FakeCallConnectionService.EXTRA_CALLER_NUMBER, number)
            photoUri?.let { putString(FakeCallConnectionService.EXTRA_CALLER_PHOTO, it) }
        }
        val extras = Bundle().apply {
            putBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, callInfo)
        }

        telecomManager.addNewIncomingCall(FakeCallConnectionService.phoneAccountHandle(context), extras)
    }

    private fun deliverAsNotification(context: Context, name: String, number: String, photoUri: String?, isSpam: Boolean) {
        val activityIntent = Intent(context, FakeCallActivity::class.java).apply {
            putExtra("caller_name", name)
            putExtra("caller_number", number)
            putExtra("caller_photo", photoUri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            FULL_SCREEN_REQUEST_CODE,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // FakeCallActionReceiver can't rely on FakeCallManager already being seeded (that only
        // happens once FakeCallActivity itself has launched) — the notification action buttons
        // must be able to fire before the activity ever exists, so the caller info rides along.
        val answerIntent = Intent(context, FakeCallActionReceiver::class.java).apply {
            action = FakeCallActionReceiver.ACTION_ANSWER
            putExtra("caller_name", name)
            putExtra("caller_number", number)
            putExtra("caller_photo", photoUri)
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            context, 1, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(context, FakeCallActionReceiver::class.java).apply { action = FakeCallActionReceiver.ACTION_DECLINE }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context, 2, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.fake_call_notification_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.fake_call_notification_channel_desc)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val title = if (isSpam) context.getString(R.string.spam_warning) else name
        // Shows the number under the name (matching the real incoming-call notification's own
        // layout) instead of a generic "Incoming call" label — formatted with its country code.
        val text = PhoneNumberFormatter.withCountryCode(context, number)

        val (nameColor, statusColor) = notificationTextColors(context)
        val views = RemoteViews(context.packageName, R.layout.notification_call_incoming).apply {
            setTextViewText(R.id.tvContactName, title)
            setTextViewText(R.id.tvCallStatus, text)
            setTextColor(R.id.tvContactName, nameColor)
            setTextColor(R.id.tvCallStatus, statusColor)
            setImageViewBitmap(R.id.ivAvatar, NotificationAvatarUtils.createAvatarBitmap(context, photoUri, title))
            setOnClickPendingIntent(R.id.btnAnswer, answerPendingIntent)
            setOnClickPendingIntent(R.id.btnDecline, declinePendingIntent)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } else {
            Log.e(TAG, "deliverAsNotification: POST_NOTIFICATIONS not granted — notification NOT posted")
        }

        // Best-effort instant path for the unlocked-screen case — full-screen-intent notifications
        // only auto-launch when locked/off; additive on top of the notification above, not a replacement.
        try {
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            // The full-screen-intent notification above still covers this case.
        }
    }

    /** RemoteViews resolves `?android:attr/textColorPrimary`/`Secondary` against the notification
     * shade's OWN theme context, which on some OEM skins doesn't reliably follow the system's
     * actual light/dark setting — same fix as the real incoming-call notification
     * (CallNotificationManager.notificationTextColors). */
    private fun notificationTextColors(context: Context): Pair<Int, Int> {
        val isNightMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (isNightMode) {
            0xFFFFFFFF.toInt() to 0xFFBDBDBD.toInt()
        } else {
            0xFF212121.toInt() to 0xFF757575.toInt()
        }
    }

    companion object {
        const val CHANNEL_ID = "fake_call_channel"
        const val NOTIFICATION_ID = 1001
        private const val FULL_SCREEN_REQUEST_CODE = 2001
    }
}
