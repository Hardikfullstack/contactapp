package com.example.contactapp.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.example.contactapp.R
import com.example.contactapp.ui.features.fakecall.FakeCallActivity

private const val TAG = "FakeCallDebug"

/**
 * Registers scheduled fake calls as genuine self-managed Telecom calls instead of a plain
 * notification. Android exempts Telecom-driven calls from the background-activity-start
 * restriction that otherwise blocks a killed/backgrounded app from popping open a screen — the
 * same exemption [ContactCallService] already relies on for real incoming calls — so
 * [FakeCallConnection.onShowIncomingCallUi] can reliably launch [FakeCallActivity] full-screen
 * regardless of whether the device is locked, unlocked and in use, or the app process is dead.
 */
class FakeCallConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest
    ): Connection {
        Log.d(TAG, "onCreateIncomingConnection: Telecom accepted the call request")
        val callInfo = request.extras?.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS)
        val name = callInfo?.getString(EXTRA_CALLER_NAME) ?: "Unknown"
        val number = callInfo?.getString(EXTRA_CALLER_NUMBER) ?: "0000000000"
        val photoUri = callInfo?.getString(EXTRA_CALLER_PHOTO)

        val connection = FakeCallConnection(applicationContext, name, number, photoUri)
        connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED)
        connection.setAudioModeIsVoip(true)
        connection.setCallerDisplayName(name, TelecomManager.PRESENTATION_ALLOWED)
        connection.setAddress(Uri.fromParts("tel", number, null), TelecomManager.PRESENTATION_ALLOWED)
        connection.setRinging()

        FakeCallManager.attachConnection(connection)
        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest
    ) {
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
        // If you see this in logcat, Telecom rejected the self-managed call outright — the
        // notification fallback in FakeCallReceiver is the only thing that ran.
        Log.e(TAG, "onCreateIncomingConnectionFailed: Telecom REJECTED the call request — falling back to notification only")
    }

    companion object {
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_CALLER_NUMBER = "caller_number"
        const val EXTRA_CALLER_PHOTO = "caller_photo"

        private const val ACCOUNT_ID = "fake_call_account"

        fun phoneAccountHandle(context: Context): PhoneAccountHandle =
            PhoneAccountHandle(ComponentName(context, FakeCallConnectionService::class.java), ACCOUNT_ID)

        /** Safe to call repeatedly (e.g. on every app start) — registering is idempotent. */
        fun registerPhoneAccount(context: Context) {
            try {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                val account = PhoneAccount.builder(phoneAccountHandle(context), context.getString(R.string.fake_call_notification_channel))
                    .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                    .build()
                telecomManager.registerPhoneAccount(account)
                Log.d(TAG, "registerPhoneAccount: registered OK")
            } catch (e: Exception) {
                // Some OEM Telecom stacks reject self-managed registration outright — the
                // notification fallback in FakeCallReceiver still covers call delivery.
                Log.e(TAG, "registerPhoneAccount: FAILED — ${e.javaClass.simpleName}: ${e.message}", e)
            }
        }

        /**
         * Fake calls are simulated, not real — they should never show up in Recents/History.
         * Telecom still auto-logs self-managed calls to the system Call Log by default though,
         * so this purges only rows written under our own fake-call PhoneAccount (matched by
         * account id + component, never by phone number) so a real contact who happens to share
         * that number keeps their genuine call history untouched.
         */
        fun purgeCallLogEntries(context: Context) {
            try {
                val componentName = ComponentName(context, FakeCallConnectionService::class.java).flattenToString()
                val selection = "${CallLog.Calls.PHONE_ACCOUNT_ID} = ? AND ${CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME} = ?"
                val args = arrayOf(ACCOUNT_ID, componentName)
                val deleted = context.contentResolver.delete(CallLog.Calls.CONTENT_URI, selection, args)
                Log.d(TAG, "purgeCallLogEntries: removed $deleted row(s)")
            } catch (e: Exception) {
                // Missing call-log access — nothing more we can do beyond this best-effort cleanup.
                Log.e(TAG, "purgeCallLogEntries: FAILED — ${e.javaClass.simpleName}: ${e.message}", e)
            }
        }
    }
}

/** The self-managed Telecom connection representing one ringing/active fake call. */
class FakeCallConnection(
    private val context: Context,
    private val name: String,
    private val number: String,
    private val photoUri: String?
) : Connection() {

    override fun onShowIncomingCallUi() {
        // Diagnostic: missing from logcat means Telecom never invoked this (OS decision, not ours);
        // present but no UI means startActivity was blocked by an OEM background-start restriction.
        Log.d(TAG, "onShowIncomingCallUi: launching FakeCallActivity")
        val intent = Intent(context, FakeCallActivity::class.java).apply {
            putExtra("caller_name", name)
            putExtra("caller_number", number)
            putExtra("caller_photo", photoUri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            context.startActivity(intent)
            Log.d(TAG, "onShowIncomingCallUi: startActivity did not throw")
        } catch (e: Exception) {
            Log.e(TAG, "onShowIncomingCallUi: startActivity FAILED — ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    override fun onAnswer() {
        Log.d(TAG, "onAnswer (Telecom-driven)")
        setActive()
        FakeCallManager.notifyAnsweredByTelecom()
    }

    override fun onReject() {
        Log.d(TAG, "onReject (Telecom-driven)")
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
        FakeCallManager.notifyEndedByTelecom()
    }

    override fun onDisconnect() {
        Log.d(TAG, "onDisconnect (Telecom-driven)")
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
        FakeCallManager.notifyEndedByTelecom()
    }

    override fun onStateChanged(state: Int) {
        super.onStateChanged(state)
        Log.d(TAG, "onStateChanged: $state")
        if (state == Connection.STATE_DISCONNECTED) {
            // Telecom writes its own Call Log entry asynchronously around this same point —
            // two spaced-out purge attempts avoid racing that write on slower devices.
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({ FakeCallConnectionService.purgeCallLogEntries(context) }, 1000L)
            handler.postDelayed({ FakeCallConnectionService.purgeCallLogEntries(context) }, 3000L)
        }
    }
}
