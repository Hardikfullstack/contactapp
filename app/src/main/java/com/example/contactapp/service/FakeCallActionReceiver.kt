package com.example.contactapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.contactapp.ui.features.fakecall.FakeCallActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles the Answer/Decline actions on the ringing fake-call notification. This can't assume
 * FakeCallActivity is alive to observe FakeCallManager's state — the notification (and its
 * actions) is the reliable delivery path precisely for cases where the activity hasn't launched
 * (e.g. tapped while locked), so both branches have to do the real work themselves rather than
 * just flipping a StateFlow nobody may be collecting.
 */
@AndroidEntryPoint
class FakeCallActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var flashAlertManager: FlashAlertManager

    @Inject
    lateinit var ringtonePlayer: FakeCallRingtonePlayer

    @Inject
    lateinit var callAnnouncerManager: CallAnnouncerManager

    override fun onReceive(context: Context, intent: Intent) {
        NotificationManagerCompat.from(context).cancel(FakeCallReceiver.NOTIFICATION_ID)

        when (intent.action) {
            ACTION_ANSWER -> {
                // Launching the activity (not just FakeCallManager.answerFromUi()) is what shows the
                // active-call UI when nothing was on screen — EXTRA_AUTO_ANSWER skips it to STATE_ACTIVE.
                val activityIntent = Intent(context, FakeCallActivity::class.java).apply {
                    putExtra("caller_name", intent.getStringExtra("caller_name"))
                    putExtra("caller_number", intent.getStringExtra("caller_number"))
                    putExtra("caller_photo", intent.getStringExtra("caller_photo"))
                    putExtra(FakeCallActivity.EXTRA_AUTO_ANSWER, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(activityIntent)
            }
            ACTION_DECLINE -> {
                // Self-sufficient cleanup: stop everything directly instead of trusting an
                // activity to be alive to react to endFromUi()'s state flip.
                flashAlertManager.stopBlinking()
                ringtonePlayer.stopRinging()
                callAnnouncerManager.stopAnnouncing()
                cancelActiveCallNotification(context)
                FakeCallManager.endFromUi()
            }
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.example.contactapp.FAKE_ACTION_ANSWER"
        const val ACTION_DECLINE = "com.example.contactapp.FAKE_ACTION_DECLINE"
    }
}
