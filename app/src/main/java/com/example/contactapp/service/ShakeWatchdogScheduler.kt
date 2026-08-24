package com.example.contactapp.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

/** Schedules/cancels the self-rescheduling alarm that keeps [ShakeDetectionService] alive — see
 *  [ShakeWatchdogReceiver] for why this is needed on top of START_STICKY. */
object ShakeWatchdogScheduler {
    private const val REQUEST_CODE = 9911
    private const val INTERVAL_MS = AlarmManager.INTERVAL_FIFTEEN_MINUTES

    /**
     * Schedules the next single check. Not a true repeating alarm — setExactAndAllowWhileIdle is
     * one-shot by design (there is no "repeating + allow-while-idle" API), so
     * [ShakeWatchdogReceiver] re-calls this after every tick to keep the chain going.
     *
     * This must be exact + allow-while-idle, not setInexactRepeating: an inexact repeating alarm
     * on the non-wakeup ELAPSED_REALTIME clock only fires when the device already happens to be
     * awake, and Doze can stretch its actual interval far past 15 minutes the longer the device
     * sits idle — which is exactly the "stops working after being idle a while" symptom this
     * watchdog exists to prevent in the first place.
     */
    fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
        val pendingIntent = pendingIntent(context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: Exception) {
            // Best-effort — if this throws, ShakeDetectionService's own START_STICKY is still
            // the fallback for ordinary kills.
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ShakeWatchdogReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
