package com.example.contactapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.contactapp.util.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Restarts the shake-trigger listener after a reboot — "set it and forget it" only holds if a
 *  restart doesn't silently disable the feature until the user reopens the app. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && preferenceManager.isShakeTriggerEnabled()) {
            ShakeDetectionService.start(context)
            ShakeWatchdogScheduler.scheduleNext(context)
        }
    }
}
