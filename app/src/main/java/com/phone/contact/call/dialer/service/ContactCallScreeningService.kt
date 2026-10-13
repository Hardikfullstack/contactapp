package com.phone.contact.call.dialer.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ContactCallScreeningService : CallScreeningService() {

    @Inject
    lateinit var spamManager: SpamManager

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: ""

        scope.launch {
            val status = spamManager.checkSpamStatus(phoneNumber)

            // Manually-blocked numbers (Blocked Numbers screen) never reach here at all — Telecom
            // rejects those upstream via BlockedNumberContract. What lands here is only
            // MANUAL_SPAM (user tapped "report spam" on a call, without blocking it outright) and
            // DETECTED_SPAM (pattern-based auto-detection) — neither is a hard block, since a false
            // positive would otherwise silently drop a real call the user actually wanted. Instead,
            // silence the ring (no ringtone/vibration/heads-up notification) while still letting the
            // call through and logging it — matching how Truecaller/Google Phone handle
            // reported-but-not-blocked spam.
            //
            // setSkipCallLog/setSkipNotification/setRejectCall are NOT set here even for spam —
            // CallScreeningService.respondToCall() throws IllegalStateException("Invalid response
            // state for allowed call.") if any of those three are set while setDisallowCall is left
            // false/unset (an "allowed" response). setSilenceCall is the only flag the framework
            // permits on an allowed call, and it alone already suppresses the ringtone/vibration/
            // heads-up behavior this is going for.
            val response = CallResponse.Builder().apply {
                if (status.isSpam() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setSilenceCall(true)
                }
            }.build()

            respondToCall(callDetails, response)
        }
    }
}
