package com.example.contactapp.service

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
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
import com.example.contactapp.util.AfterCallMiniOverlay
import com.example.contactapp.util.AfterCallNotificationHelper
import com.example.contactapp.util.AfterCallState
import com.example.contactapp.util.CallReliabilityUtils
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

        // Starts loading After Call's native ad as early as the call's RINGING/OFFHOOK state —
        // not at call-end — so it has the whole call's duration as a head start instead of just
        // the ~2s settle delay between call-end and AfterCallActivity actually showing. Safe to
        // call multiple times per call (RINGING, then OFFHOOK, then again at IDLE below):
        // NativeAdCache.preload() self-guards against a duplicate in-flight/already-cached load.
        if (state == TelephonyManager.EXTRA_STATE_RINGING || state == TelephonyManager.EXTRA_STATE_OFFHOOK) {
            preloadAfterCallNativeAds(context)
        }

        val wasActive = lastState == TelephonyManager.EXTRA_STATE_RINGING || lastState == TelephonyManager.EXTRA_STATE_OFFHOOK
        lastState = state

        // Only act on a transition INTO idle from an active call — not app startup or repeats.
        if (state != TelephonyManager.EXTRA_STATE_IDLE || !wasActive) return

        if (!AfterCallState.readEnabled(context)) {
            Log.d(TAG, "onReceive: skipped — After Call is disabled in Settings")
            return
        }
        // MIUI has its own separate "background pop-up" AppOp, distinct from — and not satisfied
        // by — the standard overlay permission, so it needs its own check instead of
        // Settings.canDrawOverlays() there. Being the default dialer alone lets Android treat the
        // post-call startActivity() as a legitimate continuation of a Telecom-handled call, so it
        // doesn't need the display permission on top of that — same as the reference app, which
        // only nags for it when the app is ALSO not the default dialer. Only bail out (and nudge
        // the user to fix it) when neither is true.
        val hasDisplayPermission = if (CallReliabilityUtils.isMiui()) {
            CallReliabilityUtils.isMiuiBackgroundPopupGranted(context)
        } else {
            Settings.canDrawOverlays(context)
        }
        if (!hasDisplayPermission && !isDefaultDialer(context)) {
            Log.w(TAG, "onReceive: skipped — no display-over-other-apps permission and not the default dialer")
            AfterCallNotificationHelper.showOverlayPermissionMissingNotification(context.applicationContext)
            return
        }
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "onReceive: skipped — READ_CALL_LOG permission not granted")
            return
        }

        Log.d(TAG, "onReceive: call just ended, all gates passed — looking up the CallLog row")

        val appContext = context.applicationContext

        preloadAfterCallNativeAds(appContext)

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

                    // Immediate, always-safe feedback — a real (if invisible) SYSTEM_ALERT_WINDOW
                    // view appears to keep this process out of the freeze/kill window some OEMs
                    // (MIUI in particular) apply right after a call ends.
                    AfterCallMiniOverlay.show(appContext)

                    // Posted right away, not just as a delayed fallback — this is what gives the
                    // user instant call-info feedback (duration/time) during the settle delay
                    // below, before the actual screen appears. AfterCallActivity cancels this
                    // itself once it's actually on screen, so it never lingers once redundant.
                    AfterCallNotificationHelper.showAfterCallFullScreenNotification(appContext, activityIntent, number, contactName)

                    // Calling startActivity() immediately on call-end is exactly what those OEMs
                    // silently block — waiting briefly for the call's telecom/audio teardown to
                    // settle first is what makes the same call reliably succeed instead.
                    delay(2000)
                    AfterCallMiniOverlay.hide()
                    appContext.startActivity(activityIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Preloads After Call's native ad (primary native_4 + fallback native_5, in parallel) —
     * called both as early as the call's RINGING/OFFHOOK state and again at call-end, since
     * NativeAdCache.preload() self-guards against a duplicate in-flight/already-cached load.
     * Fallback (native_5) preloaded in parallel with the primary, not only after it fails —
     * matches AfterCallScreen's own primary/fallback failover, which needs native_5 ready to
     * switch to immediately rather than starting its load only once native_4 has already failed. */
    private fun preloadAfterCallNativeAds(context: Context) {
        val appContext = context.applicationContext
        val cachedResult = AppConfigViewModel.readCachedResult(appContext) ?: return
        if (cachedResult.google_ads_on_off != "on") return
        if (cachedResult.native_4_on_off == "on") {
            cachedResult.native_4?.takeIf { it.isNotBlank() }?.let {
                NativeAdCache.preload(appContext, it)
            }
        }
        if (cachedResult.native_5_on_off == "on") {
            cachedResult.native_5?.takeIf { it.isNotBlank() }?.let {
                NativeAdCache.preload(appContext, it)
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

    private fun isDefaultDialer(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = context.getSystemService(RoleManager::class.java)
        return roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
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
