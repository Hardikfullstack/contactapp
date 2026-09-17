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
import android.telecom.CallAudioState
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.contactapp.R
import com.example.contactapp.ui.features.call.InCallActivity
import com.example.contactapp.util.NotificationAvatarUtils
import com.example.contactapp.util.PhoneNumberFormatter
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    // Exposed so ContactCallService can promote itself to a foreground service using this exact
    // notification — without that, the OS/OEM can kill this app's process mid-call (e.g. swiping
    // it away from Recents), since a Telecom binding alone isn't as strong a "don't kill me"
    // signal as an actual foreground service.
    private var lastNotification: android.app.Notification? = null
    fun currentNotificationOrNull(): android.app.Notification? = lastNotification

    // Remembered so a Mute/Speaker tap (CallActionReceiver) or a Telecom-driven audio-state change
    // (ContactCallService.onCallAudioStateChanged) can re-render this notification with fresh
    // toggle state without the caller info being threaded back through every call site.
    private var activeCallerName: String? = null
    private var activeCallerPhotoUri: String? = null
    private var activeCallerStatusText: String? = null

    // Remembered the same way, so a dismissed incoming-call notification (see ACTION_NOTIFICATION_
    // DISMISSED below) can be rebuilt without needing the original caller info threaded back in.
    private var incomingCallerName: String? = null
    private var incomingNumber: String? = null
    private var incomingIsSpam: Boolean = false
    private var incomingPhotoUri: String? = null

    // Also remembered — so refreshActiveCallNotification() (Mute/Speaker toggles) keeps the same
    // running Chronometer instead of losing/resetting it on every re-render.
    private var activeCallStartTimeMillis: Long? = null

    companion object {
        const val INCOMING_CALL_CHANNEL_ID = "incoming_call_channel"
        const val ACTIVE_CALL_CHANNEL_ID = "active_call_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_ANSWER = "com.example.contactapp.ACTION_ANSWER"
        const val ACTION_DECLINE = "com.example.contactapp.ACTION_DECLINE"
        const val ACTION_HANGUP = "com.example.contactapp.ACTION_HANGUP"
        const val ACTION_TOGGLE_MUTE = "com.example.contactapp.ACTION_TOGGLE_MUTE"
        const val ACTION_TOGGLE_SPEAKER = "com.example.contactapp.ACTION_TOGGLE_SPEAKER"
        // Fired via setDeleteIntent() the moment this notification is dismissed by any means
        // (swipe, the shade's "Clear all") — including on some OEMs that clear even an "ongoing"
        // notification when its owning process gets killed. Delivered as a plain broadcast, so it
        // reaches this receiver (and can relaunch/re-notify) even if the app process is dead.
        const val ACTION_NOTIFICATION_DISMISSED = "com.example.contactapp.ACTION_NOTIFICATION_DISMISSED"
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

    /** Custom RemoteViews layout (contact photo/avatar, name, status, big Answer/Decline circles) —
     * shown for every incoming call, not just as a fallback, matching the reference dialer app's
     * own incoming-call notification (which has no "is the call screen already visible" check). */
    fun showIncomingCallNotification(callerName: String, number: String, isSpam: Boolean, photoUri: String? = null) {
        incomingCallerName = callerName
        incomingNumber = number
        incomingIsSpam = isSpam
        incomingPhotoUri = photoUri

        val fullScreenIntent = Intent(context, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerPendingIntent = actionPendingIntent(ACTION_ANSWER, 1)
        val declinePendingIntent = actionPendingIntent(ACTION_DECLINE, 2)

        val title = if (isSpam) context.getString(R.string.spam_warning) else callerName
        // Shows the number under the name (matching the reference/system dialer's own heads-up
        // layout) instead of a generic "Incoming call" label — the caller's number is more useful
        // at a glance than a label that's already implied by the notification showing up at all.
        // Formatted with its country code — Telecom can hand this over as a bare local number.
        val statusText = PhoneNumberFormatter.withCountryCode(context, number)

        val (nameColor, statusColor) = notificationTextColors()
        val views = RemoteViews(context.packageName, R.layout.notification_call_incoming).apply {
            setTextViewText(R.id.tvContactName, title)
            setTextViewText(R.id.tvCallStatus, statusText)
            setTextColor(R.id.tvContactName, nameColor)
            setTextColor(R.id.tvCallStatus, statusColor)
            setImageViewBitmap(R.id.ivAvatar, NotificationAvatarUtils.createAvatarBitmap(context, photoUri, title))
            setOnClickPendingIntent(R.id.btnAnswer, answerPendingIntent)
            setOnClickPendingIntent(R.id.btnDecline, declinePendingIntent)
        }

        val builder = NotificationCompat.Builder(context, INCOMING_CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(title)
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            // setFullScreenIntent only auto-launches when the screen is locked/off — without this,
            // tapping the notification body (the avatar/name area, anything besides the two
            // action buttons) while the screen is already on does nothing.
            .setContentIntent(fullScreenPendingIntent)
            .setDeleteIntent(actionPendingIntent(ACTION_NOTIFICATION_DISMISSED, 7))
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())

        val notification = builder.build()
        lastNotification = notification
        if (checkNotificationPermission()) {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    /** Same custom-layout treatment for the ongoing call — Mute/Speaker reflect CallManager's
     * live audioState so this stays in sync with whatever InCallActivity itself shows. */
    fun showActiveCallNotification(
        callerName: String,
        photoUri: String? = null,
        statusText: String = context.getString(R.string.ongoing_call),
        isMutedOverride: Boolean? = null,
        isSpeakerOnOverride: Boolean? = null,
        // Non-null only once the call is genuinely connected (not dialing/ringing) — shows a
        // live-ticking Chronometer in place of statusText. MUST be SystemClock.elapsedRealtime()
        // at connect time, NOT System.currentTimeMillis() — Chronometer's base is measured
        // against the former; passing the latter shows a huge/backwards-counting number instead
        // of a normal ticking 00:00 upward. Passing the SAME value across subsequent re-renders
        // (refreshActiveCallNotification) keeps the Chronometer running instead of resetting;
        // omit (null) while still dialing/connecting.
        callConnectedAtMillis: Long? = activeCallStartTimeMillis
    ) {
        activeCallerName = callerName
        activeCallerPhotoUri = photoUri
        activeCallerStatusText = statusText
        activeCallStartTimeMillis = callConnectedAtMillis

        val contentIntent = Intent(context, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangupPendingIntent = actionPendingIntent(ACTION_HANGUP, 3)
        val mutePendingIntent = actionPendingIntent(ACTION_TOGGLE_MUTE, 4)
        val speakerPendingIntent = actionPendingIntent(ACTION_TOGGLE_SPEAKER, 5)

        val audioState = CallManager.audioState.value
        // Telecom's audioState only updates asynchronously once it confirms the route/mute
        // change, so re-rendering right after a toggle (before that confirmation lands) would
        // otherwise still read the pre-toggle value — the override lets the tap that triggered
        // this render show the correct icon immediately instead of waiting on that callback.
        val isMuted = isMutedOverride ?: (audioState?.isMuted ?: false)
        val isSpeakerOn = isSpeakerOnOverride ?: (audioState?.route == CallAudioState.ROUTE_SPEAKER)

        val (nameColor, statusColor) = notificationTextColors()
        val views = RemoteViews(context.packageName, R.layout.notification_call_active).apply {
            setTextViewText(R.id.tvContactName, callerName)
            setTextColor(R.id.tvContactName, nameColor)
            setTextColor(R.id.tvCallStatus, statusColor)
            setTextColor(R.id.chronoCallTimer, statusColor)
            if (callConnectedAtMillis != null) {
                setViewVisibility(R.id.tvCallStatus, android.view.View.GONE)
                setViewVisibility(R.id.chronoCallTimer, android.view.View.VISIBLE)
                setChronometer(R.id.chronoCallTimer, callConnectedAtMillis, null, true)
            } else {
                setTextViewText(R.id.tvCallStatus, statusText)
                setViewVisibility(R.id.tvCallStatus, android.view.View.VISIBLE)
                setViewVisibility(R.id.chronoCallTimer, android.view.View.GONE)
            }
            setImageViewBitmap(R.id.ivAvatar, NotificationAvatarUtils.createAvatarBitmap(context, photoUri, callerName))
            setImageViewResource(R.id.btnMute, if (isMuted) R.drawable.ic_notif_mic_off else R.drawable.ic_notif_mic)
            setImageViewResource(R.id.btnSpeaker, if (isSpeakerOn) R.drawable.ic_notif_volume_up else R.drawable.ic_notif_volume_off)
            setOnClickPendingIntent(R.id.btnMute, mutePendingIntent)
            setOnClickPendingIntent(R.id.btnSpeaker, speakerPendingIntent)
            setOnClickPendingIntent(R.id.btnEndCall, hangupPendingIntent)
        }

        val builder = NotificationCompat.Builder(context, ACTIVE_CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(callerName)
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(actionPendingIntent(ACTION_NOTIFICATION_DISMISSED, 7))
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())

        val notification = builder.build()
        lastNotification = notification
        if (checkNotificationPermission()) {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    /** Re-renders the active-call notification with whatever Mute/Speaker state is current —
     * a no-op if there's no active-call notification showing right now. Pass an override when
     * called right after the user tapped Mute/Speaker themselves, so the icon flips immediately
     * instead of waiting on Telecom's async audioState confirmation (see showActiveCallNotification). */
    fun refreshActiveCallNotification(isMutedOverride: Boolean? = null, isSpeakerOnOverride: Boolean? = null) {
        val callerName = activeCallerName ?: return
        showActiveCallNotification(
            callerName,
            activeCallerPhotoUri,
            activeCallerStatusText ?: context.getString(R.string.ongoing_call),
            isMutedOverride,
            isSpeakerOnOverride
        )
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
        activeCallerName = null
        activeCallerPhotoUri = null
        activeCallerStatusText = null
        activeCallStartTimeMillis = null
        incomingCallerName = null
        incomingNumber = null
        incomingPhotoUri = null
        lastNotification = null
    }

    /** Called when ACTION_NOTIFICATION_DISMISSED fires (the notification was swiped/cleared) — a
     * no-op if there's genuinely no call running right now (a normal dismissal after the call
     * already ended), otherwise immediately re-posts whichever notification matches the call's
     * current state so the user always has a way back into it. */
    fun repostIfCallStillOngoing() {
        when (CallManager.callState.value) {
            android.telecom.Call.STATE_RINGING -> {
                val name = incomingCallerName ?: return
                val number = incomingNumber ?: return
                showIncomingCallNotification(name, number, incomingIsSpam, incomingPhotoUri)
            }
            android.telecom.Call.STATE_ACTIVE, android.telecom.Call.STATE_DIALING,
            android.telecom.Call.STATE_CONNECTING, android.telecom.Call.STATE_HOLDING -> {
                refreshActiveCallNotification()
            }
            else -> {}
        }
    }

    /** RemoteViews resolves `?android:attr/textColorPrimary`/`Secondary` against the notification
     * shade's OWN theme context, which on some OEM skins (confirmed on this project's test device)
     * doesn't reliably follow the system's actual light/dark setting — silently keeping the dark
     * (light-mode) text color even when the shade itself is rendering dark, making the name
     * invisible. Explicitly checking the current UI mode and forcing the color in code sidesteps
     * that unreliable attribute resolution instead of trusting it. */
    private fun notificationTextColors(): Pair<Int, Int> {
        val isNightMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (isNightMode) {
            0xFFFFFFFF.toInt() to 0xFFBDBDBD.toInt()
        } else {
            0xFF212121.toInt() to 0xFF757575.toInt()
        }
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, CallActionReceiver::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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

    @Inject
    lateinit var callNotificationManager: CallNotificationManager

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
            CallNotificationManager.ACTION_TOGGLE_MUTE -> {
                // Computed before toggling — CallManager.audioState only updates once Telecom
                // confirms the change, which can lag (or on some OEMs, arrive late enough that the
                // icon looks stuck). Passing the intended new state renders it immediately;
                // ContactCallService.onCallAudioStateChanged reconciles with the real state right
                // after, a harmless no-op re-render when it agrees.
                val newMuted = !(CallManager.audioState.value?.isMuted ?: false)
                CallManager.toggleMute()
                callNotificationManager.refreshActiveCallNotification(isMutedOverride = newMuted)
            }
            CallNotificationManager.ACTION_TOGGLE_SPEAKER -> {
                val newSpeakerOn = CallManager.audioState.value?.route != CallAudioState.ROUTE_SPEAKER
                CallManager.toggleSpeaker()
                callNotificationManager.refreshActiveCallNotification(isSpeakerOnOverride = newSpeakerOn)
            }
            CallNotificationManager.ACTION_NOTIFICATION_DISMISSED -> {
                callNotificationManager.repostIfCallStillOngoing()
            }
        }
    }
}
