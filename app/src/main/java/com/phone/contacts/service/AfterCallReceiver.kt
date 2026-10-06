package com.phone.contacts.service

import android.Manifest
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CallLog
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.phone.contacts.data.ContactRepository
import com.phone.contacts.ui.features.aftercall.AfterCallActivity
import com.phone.contacts.util.AfterCallPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Listens for the phone going idle right after a call and opens [AfterCallActivity] - the same
 * flow as the sibling contactapp's AfterCallReceiver. Registered in the manifest for PHONE_STATE,
 * so this class must exist: if it is missing, every phone-state broadcast crashes the app. */
class AfterCallReceiver : BroadcastReceiver() {

    companion object {
        private var lastState: String = TelephonyManager.EXTRA_STATE_IDLE
        private const val POLL_MS = 500L
        private const val MAX_WAIT_MS = 5_000L
        private const val SETTLE_MS = 2_000L
        private const val SLACK_MS = 120_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return

        val wasActive = lastState == TelephonyManager.EXTRA_STATE_RINGING || lastState == TelephonyManager.EXTRA_STATE_OFFHOOK
        lastState = state
        // Only a transition INTO idle from an active call.
        if (state != TelephonyManager.EXTRA_STATE_IDLE || !wasActive) return

        if (!AfterCallPreferences.isEnabled(context)) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val endedAt = System.currentTimeMillis()
                val row = waitForCallLogRow(appContext, endedAt) ?: return@launch
                val number = row.number
                if (number.isBlank() || number == "-1" || number == "-2" || number == "-3") return@launch

                // Same gate as contactapp: display-over-other-apps permission, or being the default dialer.
                if (!Settings.canDrawOverlays(appContext) && !isDefaultDialer(appContext)) return@launch

                val name = ContactRepository.findContactByNumber(appContext, number)?.name
                val activityIntent = AfterCallActivity.intentFor(appContext, number, name, row.duration)

                withContext(Dispatchers.Main) {
                    // Invisible overlay + full-screen notification first, then the actual start after
                    // the call's telecom teardown settles - an immediate startActivity() at call end is
                    // what gets silently blocked on several OEMs.
                    AfterCallOverlay.show(appContext)
                    AfterCallActivity.postFullScreenNotification(appContext, activityIntent, name ?: number)
                    delay(SETTLE_MS)
                    AfterCallOverlay.hide()
                    appContext.startActivity(activityIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private data class CallRow(val number: String, val duration: Long, val date: Long)

    /** Polls the CallLog for the just-ended call's row, up to [MAX_WAIT_MS]. */
    private suspend fun waitForCallLogRow(context: Context, endedAt: Long): CallRow? {
        return withTimeoutOrNull(MAX_WAIT_MS) {
            var row: CallRow? = null
            while (row == null) {
                row = queryLatestRow(context, endedAt)
                if (row == null) delay(POLL_MS)
            }
            row
        }
    }

    /** CallLog DATE is the call's START time, so the row is matched on its estimated END time
     * (date + duration) instead - a call longer than the slack window would otherwise never match. */
    private fun queryLatestRow(context: Context, endedAt: Long): CallRow? {
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DURATION, CallLog.Calls.DATE),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val row = CallRow(
                    number = cursor.getString(0).orEmpty(),
                    duration = cursor.getLong(1),
                    date = cursor.getLong(2)
                )
                val estimatedEnd = row.date + row.duration * 1000L
                if (estimatedEnd >= endedAt - SLACK_MS) row else null
            }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun isDefaultDialer(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = context.getSystemService(RoleManager::class.java)
        return roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    }
}
