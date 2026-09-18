package com.phone.contact.call.dialer.service

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
import com.phone.contact.call.dialer.R
import com.phone.contact.call.dialer.ui.features.call.InCallActivity
import com.phone.contact.call.dialer.util.NotificationAvatarUtils
import com.phone.contact.call.dialer.util.PhoneNumberFormatter
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
    private var activeCallerHasContactName: Boolean = true

    // Remembered the same way, so a dismissed incoming-call notification (see ACTION_NOTIFICATION_
    // DISMISSED below) can be rebuilt without needing the original caller info threaded back in.
    private var incomingCallerName: String? = null
    private var incomingNumber: String? = null
    private var incomingIsSpam: Boolean = false
    private var incomingPhotoUri: String? = null
    private var incomingHasContactName: Boolean = true

    // Also remembered — so refreshActiveCallNotification() (Mute/Speaker toggles) keeps the same
    // running Chronometer instead of losing/resetting it on every re-render.
    private var activeCallStartTimeMillis: Long? = null

    // Remembered the same way — so a Mute/Speaker-triggered refresh doesn't accidentally drop
    // back to showing the caller's own name/photo mid-conference.
    private var activeCallIsConference: Boolean = false

    companion object {
        const val INCOMING_CALL_CHANNEL_ID = "incoming_call_channel"
        const val ACTIVE_CALL_CHANNEL_ID = "active_call_channel"
        const val NOTIFICATION_ID = 2001
        // A second, distinct ID so a call-waiting notification can be shown ALONGSIDE the
        // existing ongoing-call notification (two stacked heads-up pills), matching the
        // reference/stock dialer — sharing NOTIFICATION_ID would just replace one with the other.
        const val CALL_WAITING_NOTIFICATION_ID = 2002

        const val ACTION_ANSWER = "com.phone.contact.call.dialer.ACTION_ANSWER"
        const val ACTION_DECLINE = "com.phone.contact.call.dialer.ACTION_DECLINE"
        const val ACTION_ANSWER_WAITING = "com.phone.contact.call.dialer.ACTION_ANSWER_WAITING"
        const val ACTION_DECLINE_WAITING = "com.phone.contact.call.dialer.ACTION_DECLINE_WAITING"
        const val ACTION_HANGUP = "com.phone.contact.call.dialer.ACTION_HANGUP"
        const val ACTION_TOGGLE_MUTE = "com.phone.contact.call.dialer.ACTION_TOGGLE_MUTE"
        const val ACTION_TOGGLE_SPEAKER = "com.phone.contact.call.dialer.ACTION_TOGGLE_SPEAKER"
        // Fired via setDeleteIntent() the moment this notification is dismissed by any means
        // (swipe, the shade's "Clear all") — including on some OEMs that clear even an "ongoing"
        // notification when its owning process gets killed. Delivered as a plain broadcast, so it
        // reaches this receiver (and can relaunch/re-notify) even if the app process is dead.
        const val ACTION_NOTIFICATION_DISMISSED = "com.phone.contact.call.dialer.ACTION_NOTIFICATION_DISMISSED"
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
    fun showIncomingCallNotification(
        callerName: String,
        number: String,
        isSpam: Boolean,
        photoUri: String? = null,
        hasContactName: Boolean = true
    ) {
        incomingCallerName = callerName
        incomingNumber = number
        incomingIsSpam = isSpam
        incomingPhotoUri = photoUri
        incomingHasContactName = hasContactName

        val fullScreenIntent = Intent(context, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
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
            setImageViewBitmap(R.id.ivAvatar, NotificationAvatarUtils.createAvatarBitmap(context, photoUri, title, hasContactName))
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

    /** A second call ringing in while already on a call — its own separate notification (see
     * CALL_WAITING_NOTIFICATION_ID) shown alongside the existing ongoing-call one, since the
     * reference/stock dialer shows both simultaneously instead of only ever having one call
     * notification. No setFullScreenIntent here (unlike showIncomingCallNotification) — the app
     * is already on-screen for the first call, so a call-waiting arrival gets a normal heads-up
     * instead of forcibly interrupting whatever's currently showing. */
    fun showCallWaitingNotification(
        callerName: String,
        number: String,
        photoUri: String? = null,
        hasContactName: Boolean = true
    ) {
        val contentIntent = Intent(context, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 1, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerPendingIntent = actionPendingIntent(ACTION_ANSWER_WAITING, 8)
        val declinePendingIntent = actionPendingIntent(ACTION_DECLINE_WAITING, 9)

        val statusText = context.getString(R.string.call_waiting)
        val (nameColor, statusColor) = notificationTextColors()
        val views = RemoteViews(context.packageName, R.layout.notification_call_incoming).apply {
            setTextViewText(R.id.tvContactName, callerName)
            setTextViewText(R.id.tvCallStatus, statusText)
            setTextColor(R.id.tvContactName, nameColor)
            setTextColor(R.id.tvCallStatus, statusColor)
            setImageViewBitmap(R.id.ivAvatar, NotificationAvatarUtils.createAvatarBitmap(context, photoUri, callerName, hasContactName))
            setOnClickPendingIntent(R.id.btnAnswer, answerPendingIntent)
            setOnClickPendingIntent(R.id.btnDecline, declinePendingIntent)
        }

        val builder = NotificationCompat.Builder(context, INCOMING_CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
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

        if (checkNotificationPermission()) {
            notificationManager.notify(CALL_WAITING_NOTIFICATION_ID, builder.build())
        }
    }

    fun cancelCallWaitingNotification() {
        notificationManager.cancel(CALL_WAITING_NOTIFICATION_ID)
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
        callConnectedAtMillis: Long? = activeCallStartTimeMillis,
        hasContactName: Boolean = activeCallerHasContactName,
        // True once this call has merged into a real (2+ participant) conference — swaps the
        // avatar/title to a generic group icon + "Conference call" instead of the last individual
        // caller's own name/photo, matching the reference dialer's own conference notification.
        // Our own small status-bar icon (setSmallIcon below) stays exactly as-is either way.
        isConference: Boolean = activeCallIsConference
    ) {
        activeCallerName = callerName
        activeCallerPhotoUri = photoUri
        activeCallerStatusText = statusText
        activeCallStartTimeMillis = callConnectedAtMillis
        activeCallerHasContactName = hasContactName
        activeCallIsConference = isConference
        val displayName = if (isConference) context.getString(R.string.conference_call) else callerName

        val contentIntent = Intent(context, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
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
            setTextViewText(R.id.tvContactName, displayName)
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
            val avatarBitmap = if (isConference) {
                NotificationAvatarUtils.createConferenceAvatarBitmap(context)
            } else {
                NotificationAvatarUtils.createAvatarBitmap(context, photoUri, callerName, hasContactName)
            }
            setImageViewBitmap(R.id.ivAvatar, avatarBitmap)
            setImageViewResource(R.id.btnMute, if (isMuted) R.drawable.ic_notif_mic_off else R.drawable.ic_notif_mic)
            setImageViewResource(R.id.btnSpeaker, if (isSpeakerOn) R.drawable.ic_notif_volume_up else R.drawable.ic_notif_volume_off)
            setOnClickPendingIntent(R.id.btnMute, mutePendingIntent)
            setOnClickPendingIntent(R.id.btnSpeaker, speakerPendingIntent)
            setOnClickPendingIntent(R.id.btnEndCall, hangupPendingIntent)
        }

        val builder = NotificationCompat.Builder(context, ACTIVE_CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(displayName)
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
        activeCallerHasContactName = true
        activeCallIsConference = false
        notificationManager.cancel(CALL_WAITING_NOTIFICATION_ID)
        incomingCallerName = null
        incomingNumber = null
        incomingPhotoUri = null
        incomingHasContactName = true
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
                showIncomingCallNotification(name, number, incomingIsSpam, incomingPhotoUri, incomingHasContactName)
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
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
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
            CallNotificationManager.ACTION_ANSWER_WAITING -> {
                CallManager.answerSecondaryCall()
                callNotificationManager.cancelCallWaitingNotification()
                val activityIntent = Intent(context, InCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                context.startActivity(activityIntent)
            }
            CallNotificationManager.ACTION_DECLINE_WAITING -> {
                val number = CallManager.secondaryCall.value?.details?.handle?.schemeSpecificPart
                CallManager.rejectSecondaryCall()
                callNotificationManager.cancelCallWaitingNotification()
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
