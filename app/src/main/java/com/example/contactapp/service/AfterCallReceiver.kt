package com.example.contactapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.contactapp.R
import com.example.contactapp.ads.NativeAdCache
import com.example.contactapp.ads.AppOpenBackgroundReturnTrigger
import com.example.contactapp.ui.features.aftercall.AfterCallActivity
import com.example.contactapp.util.AfterCallNotificationHelper
import com.example.contactapp.util.AfterCallState
import com.example.contactapp.viewmodel.AppConfigViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "AfterCallDebug"

/** Numbers CallLog uses for withheld/unknown numbers — nothing we can message/call back, so skip these. */
private val UNACTIONABLE_NUMBERS = setOf("-1", "-2", "-3")

private data class CallLogMatch(val number: String?, val duration: Int, val type: Int)

/** Listens for the phone going idle right after a call, and launches [AfterCallActivity] with
 * that call's details — the "After Call" quick-actions screen. */
class AfterCallReceiver : BroadcastReceiver() {

    companion object {
        private var lastState: String = TelephonyManager.EXTRA_STATE_IDLE
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val wasActive = lastState == TelephonyManager.EXTRA_STATE_RINGING || lastState == TelephonyManager.EXTRA_STATE_OFFHOOK
        lastState = state

        // Only act on a transition INTO idle from an active call — not app startup or repeats.
        if (state != TelephonyManager.EXTRA_STATE_IDLE || !wasActive) return

        if (!AfterCallState.readEnabled(context)) {
            Log.d(TAG, "onReceive: skipped — After Call is disabled in Settings")
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "onReceive: skipped — 'Display over other apps' permission not granted")
            return
        }
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "onReceive: skipped — READ_CALL_LOG permission not granted")
            return
        }

        Log.d(TAG, "onReceive: call just ended, all gates passed — looking up the CallLog row")

        val appContext = context.applicationContext

        val cachedResult = AppConfigViewModel.readCachedResult(appContext)
        if (cachedResult?.google_ads_on_off == "on" && cachedResult.native_7_on_off == "on") {
            cachedResult.native_7?.takeIf { it.isNotBlank() }?.let {
                NativeAdCache.preload(appContext, it)
            }
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val callEndTimeMs = System.currentTimeMillis()
                val match = waitForCallLogRow(appContext, callEndTimeMs)
                if (match == null) {
                    Log.w(TAG, "onReceive: gave up waiting for the CallLog row after 5s")
                    return@launch
                }

                val number = match.number
                if (number.isNullOrBlank() || number in UNACTIONABLE_NUMBERS) {
                    Log.d(TAG, "onReceive: skipped — number is unactionable ($number)")
                    return@launch
                }
                val duration = match.duration
                val type = match.type

                val minutes = duration / 60
                val seconds = duration % 60
                val durationText = String.format("%02d:%02d", minutes, seconds)

                val callInfoLine1 = when (type) {
                    CallLog.Calls.OUTGOING_TYPE -> String.format(appContext.getString(R.string.after_call_duration_outgoing), durationText)
                    CallLog.Calls.INCOMING_TYPE -> String.format(appContext.getString(R.string.after_call_duration_incoming), durationText)
                    else -> appContext.getString(R.string.after_call_missed)
                }
                val timeText = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                val callInfoLine2 = String.format(appContext.getString(R.string.after_call_just_now_template), timeText)

                val contactName = lookupContactName(appContext, number)

                withContext(Dispatchers.Main) {
                    AppOpenBackgroundReturnTrigger.isAdPaused = true
                    val activityIntent = Intent(appContext, AfterCallActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("number", number)
                        putExtra("callInfoLine1", callInfoLine1)
                        putExtra("callInfoLine2", callInfoLine2)
                        if (contactName != null) putExtra("contactName", contactName)
                    }
                    appContext.startActivity(activityIntent)

                    // Belt-and-suspenders fallback: on OEMs where the startActivity() above gets
                    // silently swallowed, a short delayed check catches it and falls back to a
                    // full-screen-intent notification instead. Not awaited — on devices where the
                    // direct launch already works this is a no-op, since isVisible flips true
                    // almost immediately.
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(1000)
                        if (!AfterCallActivity.isVisible) {
                            AfterCallNotificationHelper.showAfterCallFullScreenNotification(appContext, activityIntent, number, contactName)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Matches CallLog.Calls.DATE (the call's START time, not end) against [callEndTimeMs] by
     * adding the row's own duration to estimate when it actually ended — a long answered call
     * legitimately started well before it ended, so comparing the raw start time against "now"
     * would reject the very row we just want. 120s of slack on top covers ringing time (not
     * included in duration) before the call connected. */
    private fun checkCallLogRow(context: Context, callEndTimeMs: Long): CallLogMatch? {
        // No SQL-level LIMIT here — some OEM CallLogProvider implementations (e.g. MIUI's) reject
        // a "LIMIT" token in the sortOrder string with IllegalArgumentException. We only ever read
        // the first row anyway, so plain DESC ordering is enough.
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DURATION, CallLog.Calls.TYPE, CallLog.Calls.DATE),
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val rowDate = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE))
                val rowDuration = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                val estimatedEndTimeMs = rowDate + (rowDuration * 1000L)
                if (estimatedEndTimeMs >= callEndTimeMs - 120_000L) {
                    return CallLogMatch(
                        number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)),
                        duration = rowDuration,
                        type = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                    )
                }
            }
        }
        return null
    }

    /**
     * Waits for the just-ended call's CallLog row instead of polling on a fixed interval —
     * reacts the moment the OS actually writes it via a ContentObserver, so there's no artificial
     * per-tick delay once the row is genuinely available. A 500ms fallback tick rides along on
     * the same trigger in case some OEM's CallLogProvider doesn't call notifyChange() reliably,
     * and everything is capped at a 5s ceiling either way.
     */
    private suspend fun waitForCallLogRow(context: Context, callEndTimeMs: Long): CallLogMatch? {
        checkCallLogRow(context, callEndTimeMs)?.let { return it }

        val trigger = Channel<Unit>(Channel.CONFLATED)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trigger.trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)

        try {
            return withTimeoutOrNull(5000L) {
                val fallbackTicker = launch {
                    while (isActive) {
                        delay(500)
                        trigger.trySend(Unit)
                    }
                }
                try {
                    var result: CallLogMatch? = null
                    for (unit in trigger) {
                        val match = checkCallLogRow(context, callEndTimeMs)
                        if (match != null) {
                            result = match
                            break
                        }
                    }
                    result
                } finally {
                    fallbackTicker.cancel()
                }
            }
        } finally {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    /** Best-effort contact name lookup for the number, matching the pattern used elsewhere in the app. */
    private fun lookupContactName(context: Context, number: String): String? {
        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
