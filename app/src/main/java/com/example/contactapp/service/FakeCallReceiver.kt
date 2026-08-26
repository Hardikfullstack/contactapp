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
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.contactapp.R
import com.example.contactapp.ui.features.fakecall.FakeCallActivity
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
 * 1. A high-priority full-screen-intent notification — always posted, and the mechanism proven
 *    to reliably wake the call screen when the device is locked or idle.
 * 2. A best-effort attempt to also register the call as a self-managed Telecom call (see
 *    [FakeCallConnectionService]) — Telecom-driven calls are exempt from Android's restriction
 *    on starting an Activity from a background app, so when it succeeds it pops the screen open
 *    immediately even while the phone is unlocked and in active use.
 *
 * [android.telecom.TelecomManager.addNewIncomingCall] is fire-and-forget: Telecom can silently
 * decline to create the connection (stale/unregistered account, OEM policy, etc.) without ever
 * throwing back to the caller, so success there can't be assumed just because no exception was
 * thrown. The notification is therefore never gated on it — layer 2 is purely additive on top of
 * layer 1, not a replacement for it. [com.example.contactapp.ui.features.fakecall.FakeCallActivity]
 * cancels the notification the instant it's actually shown, so there's no cost when both fire.
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
        Log.d(TAG, "onReceive: alarm fired for '$name' ($number)")

        try {
            deliverAsTelecomCall(context, name, number, photoUri)
            Log.d(TAG, "onReceive: addNewIncomingCall did not throw (this only means the REQUEST was " +
                "accepted, not that the connection was actually created — check for " +
                "onCreateIncomingConnection / onCreateIncomingConnectionFailed next)")
        } catch (e: Exception) {
            Log.e(TAG, "onReceive: deliverAsTelecomCall FAILED — ${e.javaClass.simpleName}: ${e.message}", e)
        }

        // A plain BroadcastReceiver only gets elevated process priority while onReceive() is
        // actually running — the instant it returns, this process is eligible to be
        // deprioritized/frozen again. Telecom's follow-up call into
        // FakeCallConnectionService.onCreateIncomingConnection() is a SEPARATE, asynchronous
        // binder transaction that happens after addNewIncomingCall() above returns, so without
        // goAsync() holding that priority a little longer, an aggressive OEM process manager can
        // freeze this process in the gap and silently drop Telecom's callback before it ever
        // reaches app code — indistinguishable from Telecom rejecting the call outright.
        val pendingResult = goAsync()
        scope.launch {
            try {
                val status = spamManager.checkSpamStatus(number)
                deliverAsNotification(context, name, number, photoUri, status.isSpam())
                delay(2000L)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun deliverAsTelecomCall(context: Context, name: String, number: String, photoUri: String?) {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        FakeCallConnectionService.registerPhoneAccount(context)

        val handle = FakeCallConnectionService.phoneAccountHandle(context)
        // Purely diagnostic — some Android versions now gate getPhoneAccount() behind
        // READ_PHONE_NUMBERS (which this app doesn't request, since it isn't otherwise needed).
        // This must never be allowed to block the real addNewIncomingCall() attempt below —
        // it previously did, since an uncaught SecurityException here aborted this whole function
        // before ever reaching it.
        try {
            val registeredAccount = telecomManager.getPhoneAccount(handle)
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            Log.d(TAG, "deliverAsTelecomCall: account registered=${registeredAccount != null}, " +
                "enabled=${registeredAccount?.isEnabled}, " +
                "ignoringBatteryOptimizations=${powerManager.isIgnoringBatteryOptimizations(context.packageName)}, " +
                "manufacturer=${Build.MANUFACTURER}, sdk=${Build.VERSION.SDK_INT}")
        } catch (e: Exception) {
            Log.w(TAG, "deliverAsTelecomCall: diagnostic getPhoneAccount() check failed (non-fatal) — ${e.javaClass.simpleName}: ${e.message}")
        }

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
        val text = if (isSpam) number else context.getString(R.string.incoming_call)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_call, context.getString(R.string.answer_call), answerPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.decline_call_short), declinePendingIntent)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "deliverAsNotification: notification posted")
        } else {
            Log.e(TAG, "deliverAsNotification: POST_NOTIFICATIONS not granted — notification NOT posted")
        }

        // Best-effort instant path: only succeeds when the caller already has a legitimate
        // foreground-adjacent presence (e.g. the shake-trigger foreground service, or the app
        // already being open) — that's exactly the unlocked-screen case a full-screen-intent
        // notification can't cover on its own, since Android only auto-launches those when the
        // screen is locked/off; on an unlocked screen it's just a heads-up requiring a manual
        // tap. Purely additive on top of the notification above, never a replacement for it.
        try {
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            // The full-screen-intent notification above still covers this case.
        }
    }

    companion object {
        const val CHANNEL_ID = "fake_call_channel"
        const val NOTIFICATION_ID = 1001
        private const val FULL_SCREEN_REQUEST_CODE = 2001
    }
}
