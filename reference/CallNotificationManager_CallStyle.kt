// ARCHIVED REFERENCE — NOT PART OF THE BUILD (lives outside app/src/main/java on purpose).
//
// This is the NotificationCompat.CallStyle-based version of CallNotificationManager.kt,
// swapped out on 2026-10-02 at the user's request ("isko code yad rakhna but abhi ke liye designe
// jaisa tha wese kardo" — keep this code, but restore the old custom design for now).
//
// Why this version exists: NotificationCompat.CallStyle is Android's own dedicated call-
// notification template (API 31+, gracefully degraded below that). Unlike a custom RemoteViews
// "ongoing" notification, the OS itself refuses to let the user swipe a CallStyle notification
// away while the call is ongoing — a genuine system-level guarantee, not an app-side best-effort
// recovery. The tradeoff: the notification's visual layout (avatar circle, name, status line,
// button row) is then rendered BY THE SYSTEM, not by this app's own custom design — Mute/Speaker
// had to become plain addAction() entries since CallStyle has no slot for them.
//
// To bring this back: replace the active CallNotificationManager.kt with this file's content,
// and delete the custom-layout version's now-unused resources again (notification_call_incoming.xml,
// notification_call_active.xml, bg_notif_circle_*.xml, ic_notif_call.xml, ic_notif_call_end.xml —
// see the reverted CallNotificationManager.kt for what it currently uses instead). Also re-add the
// action_mute/action_unmute/action_speaker_on/action_speaker_off string resources this version
// needs for its addAction() labels.

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
import android.os.Build
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.phone.contacts.R
import com.phone.contacts.ui.features.call.CallActivity
import com.phone.contacts.util.NotificationAvatarUtils

/** Plain `object` (this project has no Hilt/DI). Built on [NotificationCompat.CallStyle] -
 * Android's own dedicated call-notification template (API 31+, gracefully degraded below that) -
 * rather than a custom layout: the OS itself refuses to let the user swipe away a CallStyle
 * notification while the call is ongoing, which a plain "ongoing" custom-layout notification does
 * NOT reliably get on every Android version/OEM. Backed by a real foreground service (see
 * [ContactsCallService.promoteToForeground]) so the OS doesn't kill the call process when the app
 * is swiped away from Recents. */
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
    // Fired via setDeleteIntent() if this notification is ever dismissed anyway (CallStyle's
    // non-dismissible guarantee isn't identically enforced on every OEM skin) - delivered as a
    // plain broadcast, so it reaches this receiver (and can re-notify) even if the app is dead.
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

    private fun personFor(context: Context, name: String, photoUri: String?, hasContactName: Boolean): Person {
        val bitmap = NotificationAvatarUtils.createAvatarBitmap(context, photoUri, name, hasContactName)
        return Person.Builder()
            .setName(name)
            .setIcon(IconCompat.createWithBitmap(bitmap))
            .setImportant(true)
            .build()
    }

    /** Shown for every incoming call, not just as a fallback, so it's there even if the call
     * screen itself hasn't drawn yet. Uses [NotificationCompat.CallStyle.forIncomingCall] -
     * the system renders its own Answer/Decline buttons and, critically, won't let the user
     * swipe this away while it's an active incoming call. */
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

        val person = personFor(context, callerName, photoUri, hasContactName)
        val callStyle = NotificationCompat.CallStyle.forIncomingCall(
            person,
            actionPendingIntent(context, ACTION_DECLINE, 2),
            actionPendingIntent(context, ACTION_ANSWER, 1)
        )

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
            .setStyle(callStyle)
            .addPerson(person)
            .build()

        post(context, NOTIFICATION_ID, notification)
    }

    /** A second call ringing in while already on a call - shown alongside the existing
     * ongoing-call notification (its own CallStyle pill) instead of replacing it. No full-screen
     * intent here - the app is already on-screen for the first call. */
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

        val person = personFor(context, callerName, photoUri, hasContactName)
        val callStyle = NotificationCompat.CallStyle.forIncomingCall(
            person,
            actionPendingIntent(context, ACTION_DECLINE_WAITING, 9),
            actionPendingIntent(context, ACTION_ANSWER_WAITING, 8)
        )

        val notification = NotificationCompat.Builder(context, INCOMING_CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(callerName)
            .setContentText(context.getString(R.string.call_waiting))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentPendingIntent)
            .setStyle(callStyle)
            .addPerson(person)
            .build()

        if (checkNotificationPermission(context)) {
            notificationManager(context).notify(CALL_WAITING_NOTIFICATION_ID, notification)
        }
    }

    fun cancelCallWaitingNotification(context: Context) {
        notificationManager(context).cancel(CALL_WAITING_NOTIFICATION_ID)
    }

    /** Same CallStyle treatment for the ongoing call - [NotificationCompat.CallStyle.forOngoingCall]
     * supplies the system's own hang-up button (and its non-dismissible-while-ongoing guarantee);
     * Mute/Speaker are added as extra actions since CallStyle itself has no slot for them. */
    fun showActiveCallNotification(
        context: Context,
        callerName: String,
        photoUri: String? = null,
        statusText: String = context.getString(R.string.ongoing_call),
        isMutedOverride: Boolean? = null,
        isSpeakerOnOverride: Boolean? = null,
        // Non-null only once the call is genuinely connected - MUST be SystemClock.elapsedRealtime()
        // at connect time. Passing the same value across re-renders (refreshActiveCallNotification)
        // keeps the notification's chronometer ticking instead of resetting; omit (null) while
        // still dialing/connecting.
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

        val person = if (isConference) {
            Person.Builder()
                .setName(displayName)
                .setIcon(IconCompat.createWithBitmap(NotificationAvatarUtils.createConferenceAvatarBitmap(context)))
                .setImportant(true)
                .build()
        } else {
            personFor(context, callerName, photoUri, hasContactName)
        }
        val callStyle = NotificationCompat.CallStyle.forOngoingCall(
            person,
            actionPendingIntent(context, ACTION_HANGUP, 3)
        )

        val muteAction = NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, if (isMuted) R.drawable.ic_notif_mic_off else R.drawable.ic_notif_mic),
            context.getString(if (isMuted) R.string.action_unmute else R.string.action_mute),
            actionPendingIntent(context, ACTION_TOGGLE_MUTE, 4)
        ).build()
        val speakerAction = NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, if (isSpeakerOn) R.drawable.ic_notif_volume_up else R.drawable.ic_notif_volume_off),
            context.getString(if (isSpeakerOn) R.string.action_speaker_off else R.string.action_speaker_on),
            actionPendingIntent(context, ACTION_TOGGLE_SPEAKER, 5)
        ).build()

        val builder = NotificationCompat.Builder(context, ACTIVE_CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(displayName)
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(actionPendingIntent(context, ACTION_NOTIFICATION_DISMISSED, 7))
            .setStyle(callStyle)
            .addPerson(person)
            .addAction(muteAction)
            .addAction(speakerAction)

        if (callConnectedAtMillis != null) {
            // Notification.setWhen()/setUsesChronometer() ticks from a wall-clock epoch, unlike
            // our internal SystemClock.elapsedRealtime()-based anchor - converting here keeps the
            // notification's timer showing the true elapsed call duration either way.
            val elapsedSinceConnect = SystemClock.elapsedRealtime() - callConnectedAtMillis
            builder.setWhen(System.currentTimeMillis() - elapsedSinceConnect).setUsesChronometer(true)
        } else {
            builder.setUsesChronometer(false)
        }

        post(context, NOTIFICATION_ID, builder.build())
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

    /** Called when ACTION_NOTIFICATION_DISMISSED fires (the notification was swiped/cleared
     * anyway, despite CallStyle's usual non-dismissible guarantee) - a no-op if there's genuinely
     * no call running right now, otherwise immediately re-posts whichever notification matches
     * the call's current state. */
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
