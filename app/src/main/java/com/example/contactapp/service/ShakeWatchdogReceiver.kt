package com.example.contactapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.contactapp.util.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Periodic self-healing check for the shake-trigger foreground service. START_STICKY only
 * covers ordinary OS-triggered kills (e.g. memory pressure) — it does nothing if an OEM battery
 * manager kills the process outside Android's normal lifecycle, which is what aggressive OEM
 * builds (TCL included — see the earlier Telecom self-managed-call investigation) do despite the
 * standard battery-optimization exemption already being granted. This alarm restarts the service
 * if it's supposed to be running but isn't, without the user needing to reopen the app.
 *
 * This can't recover from an actual force-stop though — Android disables an app's alarms and
 * receivers entirely after a force-stop, until the user manually relaunches it. That's a hard
 * OS-level lockout no app code can bypass, not something this watchdog (or anything else running
 * inside the app) can work around.
 */
@AndroidEntryPoint
class ShakeWatchdogReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun onReceive(context: Context, intent: Intent) {
        if (preferenceManager.isShakeTriggerEnabled()) {
            // Safe to call unconditionally even if the service is already alive — onCreate()
            // (and the sensor listener registration inside it) only runs once per live instance;
            // this just delivers an extra onStartCommand() when nothing was actually wrong.
            ShakeDetectionService.start(context)
        }
    }
}
