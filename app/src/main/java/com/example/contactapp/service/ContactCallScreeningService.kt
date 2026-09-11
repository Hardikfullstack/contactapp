package com.example.contactapp.service

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
            val response = CallResponse.Builder().apply {
                if (status.isSpam() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setSilenceCall(true)
                    setSkipNotification(true)
                }
            }.build()

            respondToCall(callDetails, response)
        }
    }
}
