package com.phone.contacts.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.phone.contacts.R
import com.phone.contacts.ui.features.call.CallActivity
import com.phone.contacts.util.NotificationAvatarUtils

/** Plain `object` (this project has no Hilt/DI) mirroring the reference dialer app's own
 * CallNotificationManager - a custom-layout incoming/ongoing call notification backed by a real
 * foreground service (see [ContactsCallService.promoteToForeground]), so the OS doesn't kill the
 * call process when the app is swiped away from Recents. */
object CallNotificationManager {
    const val INCOMING_CALL_CHANNEL_ID = "incoming_call_channel"
    const val ACTIVE_CALL_CHANNEL_ID = "active_call_channel"
    const val NOTIFICATION_ID = 2001

    // A second, distinct ID so a call-waiting notification can be shown ALONGSIDE the existing
    // ongoing-call notification instead of replacing it.
    const val CALL_WAITING_NOTIFICATION_ID = 2002

    const val ACTION_ANSWER = "com.phone.contacts.ACTION_ANSWER"
    const val ACTION_DECLINE = "com.phone.contacts.ACTION_DECLINE"
    const val ACTION_ANSWER_WAITING = "com.phone.contacts.ACTION_ANSWER_WAITING"
    const val ACTION_DECLINE_WAITING = "com.phone.contacts.ACTION_DECLINE_WAITING"
    const val ACTION_HANGUP = "com.phone.contacts.ACTION_HANGUP"
    const val ACTION_TOGGLE_MUTE = "com.phone.contacts.ACTION_TOGGLE_MUTE"
    const val ACTION_TOGGLE_SPEAKER = "com.phone.contacts.ACTION_TOGGLE_SPEAKER"
    // Fired via setDeleteIntent() the moment this notification is dismissed by any means (swipe,
    // the shade's "Clear all") - delivered as a plain broadcast, so it reaches this receiver (and
    // can re-notify) even if the app process is dead.
    const val ACTION_NOTIFICATION_DISMISSED = "com.phone.contacts.ACTION_NOTIFICATION_DISMISSED"

    private var lastNotification: Notification? = null
    fun currentNotificationOrNull(): Notification? = lastNotification

    // Set by ContactsCallService.onCreate()/cleared in onDestroy() - every freshly-posted
    // notification is handed back so the service can immediately promote itself to foreground
    // with that exact Notification object.
    var onNotificationPosted: ((Notification) -> Unit)? = null

    // Remembered so a Mute/Speaker tap (CallActionReceiver) or a Telecom-driven audio-state change
    // can re-render this notification with fresh toggle state without the caller info being
    // threaded back through every call site.
    private var activeCallerName: String? = null
    private var activeCallerPhotoUri: String? = null
    private var activeCallerStatusText: String? = null
    private var activeCallerHasContactName: Boolean = true
    private var activeCallStartTimeMillis: Long? = null
    private var activeCallIsConference: Boolean = false

    // Remembered the same way, so a dismissed incoming-call notification (see
    // ACTION_NOTIFICATION_DISMISSED) can be rebuilt without needing the original info re-threaded.
    private var incomingCallerName: String? = null
    private var incomingNumber: String? = null
    private var incomingPhotoUri: String? = null
    private var incomingHasContactName: Boolean = true

    private fun notificationManager(context: Context) = NotificationManagerCompat.from(context)

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val incomingChannel = NotificationChannel(
            INCOMING_CALL_CHANNEL_ID,
            context.getString(R.string.incoming_call),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.incoming_call_status)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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

    /** Custom RemoteViews layout (avatar, name, status, Answer/Decline) - shown for every
     * incoming call, not just as a fallback, so it's there even if the call screen itself hasn't
     * drawn yet (or the app process was asleep and is only now being woken by Telecom). */
    fun showIncomingCallNotification(
        context: Context,
        callerName: String,
        number: String,
        photoUri: String? = null,
        hasContactName: Boolean = true
    ) {
        incomingCallerName = callerName
        incomingNumber = number
        incomingPhotoUri = photoUri
        incomingHasContactName = hasContactName

        val fullScreenIntent = Intent(context, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (nameColor, statusColor) = notificationTextColors(context)
        val views = RemoteViews(context.packageName, R.layout.notification_call_incoming).apply {
            setTextViewText(R.id.tvContactName, callerName)
            setTextViewText(R.id.tvCallStatus, number)
            setTextColor(R.id.tvContactName, nameColor)
            setTextColor(R.id.tvCallStatus, statusColor)
            setImageViewBitmap(R.id.ivAvatar, NotificationAvatarUtils.createAvatarBitmap(context, photoUri, callerName, hasContactName))
            setOnClickPendingIntent(R.id.btnAnswer, actionPendingIntent(context, ACTION_ANSWER, 1))
            setOnClickPendingIntent(R.id.btnDecline, actionPendingIntent(context, ACTION_DECLINE, 2))
        }

        val notification = NotificationCompat.Builder(context, INCOMING_CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(callerName)
            .setContentText(number)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            // setFullScreenIntent only auto-launches when the screen is locked/off - without this,
            // tapping the notification body while the screen is already on does nothing.
            .setContentIntent(fullScreenPendingIntent)
            .setDeleteIntent(actionPendingIntent(context, ACTION_NOTIFICATION_DISMISSED, 7))
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .build()

        post(context, NOTIFICATION_ID, notification)
    }

    /** A second call ringing in while already on a call - shown alongside the existing
     * ongoing-call notification (two stacked pills) instead of replacing it. No full-screen intent
     * here - the app is already on-screen for the first call. */
    fun showCallWaitingNotification(
        context: Context,
        callerName: String,
        number: String,
        photoUri: String? = null,
        hasContactName: Boolean = true
    ) {
        val contentIntent = Intent(context, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 1, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = context.getString(R.string.call_waiting)
        val (nameColor, statusColor) = notificationTextColors(context)
        val views = RemoteViews(context.packageName, R.layout.notification_call_incoming).apply {
            setTextViewText(R.id.tvContactName, callerName)
            setTextViewText(R.id.tvCallStatus, statusText)
            setTextColor(R.id.tvContactName, nameColor)
            setTextColor(R.id.tvCallStatus, statusColor)
            setImageViewBitmap(R.id.ivAvatar, NotificationAvatarUtils.createAvatarBitmap(context, photoUri, callerName, hasContactName))
            setOnClickPendingIntent(R.id.btnAnswer, actionPendingIntent(context, ACTION_ANSWER_WAITING, 8))
            setOnClickPendingIntent(R.id.btnDecline, actionPendingIntent(context, ACTION_DECLINE_WAITING, 9))
        }

        val notification = NotificationCompat.Builder(context, INCOMING_CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(callerName)
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentPendingIntent)
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .build()

        if (checkNotificationPermission(context)) {
            notificationManager(context).notify(CALL_WAITING_NOTIFICATION_ID, notification)
        }
    }

    fun cancelCallWaitingNotification(context: Context) {
        notificationManager(context).cancel(CALL_WAITING_NOTIFICATION_ID)
    }

    /** Same custom-layout treatment for the ongoing call - Mute/Speaker reflect CallManager's live
     * audioState so this stays in sync with whatever the call screen itself shows. */
    fun showActiveCallNotification(
        context: Context,
        callerName: String,
        photoUri: String? = null,
        statusText: String = context.getString(R.string.ongoing_call),
        isMutedOverride: Boolean? = null,
        isSpeakerOnOverride: Boolean? = null,
        // Non-null only once the call is genuinely connected - MUST be SystemClock.elapsedRealtime()
        // at connect time (Chronometer's base), not System.currentTimeMillis(). Passing the same
        // value across re-renders (refreshActiveCallNotification) keeps it ticking instead of
        // resetting; omit (null) while still dialing/connecting.
        callConnectedAtMillis: Long? = activeCallStartTimeMillis,
        hasContactName: Boolean = activeCallerHasContactName,
        isConference: Boolean = activeCallIsConference
    ) {
        activeCallerName = callerName
        activeCallerPhotoUri = photoUri
        activeCallerStatusText = statusText
        activeCallStartTimeMillis = callConnectedAtMillis
        activeCallerHasContactName = hasContactName
        activeCallIsConference = isConference
        val displayName = if (isConference) context.getString(R.string.conference_call) else callerName

        val contentIntent = Intent(context, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val audioState = CallManager.audioState.value
        // Telecom's audioState only updates asynchronously once it confirms the route/mute change
        // - the override lets the tap that triggered this render show the correct icon immediately
        // instead of waiting on that callback.
        val isMuted = isMutedOverride ?: (audioState?.isMuted ?: false)
        val isSpeakerOn = isSpeakerOnOverride ?: (audioState?.route == CallAudioState.ROUTE_SPEAKER)

        val (nameColor, statusColor) = notificationTextColors(context)
        val views = RemoteViews(context.packageName, R.layout.notification_call_active).apply {
            setTextViewText(R.id.tvContactName, displayName)
            setTextColor(R.id.tvContactName, nameColor)
            setTextColor(R.id.tvCallStatus, statusColor)
            setTextColor(R.id.chronoCallTimer, statusColor)
            if (callConnectedAtMillis != null) {
                setViewVisibility(R.id.tvCallStatus, View.GONE)
                setViewVisibility(R.id.chronoCallTimer, View.VISIBLE)
                setChronometer(R.id.chronoCallTimer, callConnectedAtMillis, null, true)
            } else {
                setTextViewText(R.id.tvCallStatus, statusText)
                setViewVisibility(R.id.tvCallStatus, View.VISIBLE)
                setViewVisibility(R.id.chronoCallTimer, View.GONE)
            }
            val avatarBitmap = if (isConference) {
                NotificationAvatarUtils.createConferenceAvatarBitmap(context)
            } else {
                NotificationAvatarUtils.createAvatarBitmap(context, photoUri, callerName, hasContactName)
            }
            setImageViewBitmap(R.id.ivAvatar, avatarBitmap)
            setImageViewResource(R.id.btnMute, if (isMuted) R.drawable.ic_notif_mic_off else R.drawable.ic_notif_mic)
            setImageViewResource(R.id.btnSpeaker, if (isSpeakerOn) R.drawable.ic_notif_volume_up else R.drawable.ic_notif_volume_off)
            setOnClickPendingIntent(R.id.btnMute, actionPendingIntent(context, ACTION_TOGGLE_MUTE, 4))
            setOnClickPendingIntent(R.id.btnSpeaker, actionPendingIntent(context, ACTION_TOGGLE_SPEAKER, 5))
            setOnClickPendingIntent(R.id.btnEndCall, actionPendingIntent(context, ACTION_HANGUP, 3))
        }

        val notification = NotificationCompat.Builder(context, ACTIVE_CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(displayName)
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(actionPendingIntent(context, ACTION_NOTIFICATION_DISMISSED, 7))
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .build()

        post(context, NOTIFICATION_ID, notification)
    }

    /** Re-renders the active-call notification with whatever Mute/Speaker state is current - a
     * no-op if there's no active-call notification showing right now. */
    fun refreshActiveCallNotification(context: Context, isMutedOverride: Boolean? = null, isSpeakerOnOverride: Boolean? = null) {
        val callerName = activeCallerName ?: return
        showActiveCallNotification(
            context,
            callerName,
            activeCallerPhotoUri,
            activeCallerStatusText ?: context.getString(R.string.ongoing_call),
            isMutedOverride,
            isSpeakerOnOverride
        )
    }

    fun cancelNotification(context: Context) {
        notificationManager(context).cancel(NOTIFICATION_ID)
        activeCallerName = null
        activeCallerPhotoUri = null
        activeCallerStatusText = null
        activeCallStartTimeMillis = null
        activeCallerHasContactName = true
        activeCallIsConference = false
        notificationManager(context).cancel(CALL_WAITING_NOTIFICATION_ID)
        incomingCallerName = null
        incomingNumber = null
        incomingPhotoUri = null
        incomingHasContactName = true
        lastNotification = null
    }

    /** Called when ACTION_NOTIFICATION_DISMISSED fires (the notification was swiped/cleared) - a
     * no-op if there's genuinely no call running right now, otherwise immediately re-posts
     * whichever notification matches the call's current state. */
    fun repostIfCallStillOngoing(context: Context) {
        when (CallManager.callState.value) {
            Call.STATE_RINGING -> {
                val name = incomingCallerName ?: return
                val number = incomingNumber ?: return
                showIncomingCallNotification(context, name, number, incomingPhotoUri, incomingHasContactName)
            }
            Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_HOLDING -> {
                refreshActiveCallNotification(context)
            }
            else -> {}
        }
    }

    private fun post(context: Context, id: Int, notification: Notification) {
        lastNotification = notification
        if (checkNotificationPermission(context)) {
            notificationManager(context).notify(id, notification)
        }
        onNotificationPosted?.invoke(notification)
    }

    /** RemoteViews resolves `?android:attr/textColorPrimary`/`Secondary` against the notification
     * shade's OWN theme context, which on some OEM skins doesn't reliably follow the system's
     * actual light/dark setting - explicitly checking the current UI mode and forcing the color in
     * code sidesteps that unreliable attribute resolution instead of trusting it. */
    private fun notificationTextColors(context: Context): Pair<Int, Int> {
        val isNightMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (isNightMode) {
            0xFFFFFFFF.toInt() to 0xFFBDBDBD.toInt()
        } else {
            0xFF212121.toInt() to 0xFF757575.toInt()
        }
    }

    private fun actionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, CallActionReceiver::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun checkNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CallNotificationManager.ACTION_ANSWER -> {
                CallManager.answer()
                val activityIntent = Intent(context, CallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                context.startActivity(activityIntent)
            }
            CallNotificationManager.ACTION_DECLINE -> CallManager.reject()
            CallNotificationManager.ACTION_ANSWER_WAITING -> {
                CallManager.answerSecondaryCall()
                CallNotificationManager.cancelCallWaitingNotification(context)
                val activityIntent = Intent(context, CallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                context.startActivity(activityIntent)
            }
            CallNotificationManager.ACTION_DECLINE_WAITING -> {
                CallManager.rejectSecondaryCall()
                CallNotificationManager.cancelCallWaitingNotification(context)
            }
            CallNotificationManager.ACTION_HANGUP -> CallManager.disconnect()
            CallNotificationManager.ACTION_TOGGLE_MUTE -> {
                // Computed before toggling, same reasoning as showActiveCallNotification's own
                // override params - Telecom's audioState can lag/arrive late enough to look stuck.
                val newMuted = !(CallManager.audioState.value?.isMuted ?: false)
                CallManager.toggleMute()
                CallNotificationManager.refreshActiveCallNotification(context, isMutedOverride = newMuted)
            }
            CallNotificationManager.ACTION_TOGGLE_SPEAKER -> {
                val newSpeakerOn = CallManager.audioState.value?.route != CallAudioState.ROUTE_SPEAKER
                CallManager.toggleSpeaker()
                CallNotificationManager.refreshActiveCallNotification(context, isSpeakerOnOverride = newSpeakerOn)
            }
            CallNotificationManager.ACTION_NOTIFICATION_DISMISSED -> {
                CallNotificationManager.repostIfCallStillOngoing(context)
            }
        }
    }
}
