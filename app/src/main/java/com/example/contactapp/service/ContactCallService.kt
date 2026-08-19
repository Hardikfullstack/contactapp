package com.example.contactapp.service

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import com.example.contactapp.ui.features.call.InCallActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ContactCallService : InCallService() {

    @Inject
    lateinit var callAnnouncerManager: CallAnnouncerManager

    @Inject
    lateinit var flashAlertManager: FlashAlertManager

    @Inject
    lateinit var callNotificationManager: CallNotificationManager

    @Inject
    lateinit var spamManager: SpamManager

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.updateCall(call)

        val number = call.details.handle?.schemeSpecificPart ?: "Unknown"

        // Trigger Call Announcer and Flash Alert for incoming calls
        if (call.state == Call.STATE_RINGING) {
            callAnnouncerManager.announceCall(number)
            flashAlertManager.startBlinking()
            
            scope.launch {
                val spamStatus = spamManager.checkSpamStatus(number)
                CallManager.setSpam(spamStatus.isSpam())
                
                val displayName = call.details.callerDisplayName ?: number
                callNotificationManager.showIncomingCallNotification(
                    displayName, 
                    number, 
                    spamStatus.isSpam()
                )
            }
        }

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                CallManager.updateCall(call) // Ensure CallManager stays in sync
                
                when (state) {
                    Call.STATE_ACTIVE -> {
                        flashAlertManager.stopBlinking()
                        val displayName = call.details.callerDisplayName ?: number
                        callNotificationManager.showActiveCallNotification(displayName)
                    }
                    Call.STATE_DISCONNECTED -> {
                        callNotificationManager.cancelNotification()
                    }
                }
            }
        })

        // Show the In-Call UI
        val intent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        callAnnouncerManager.stopAnnouncing()
        flashAlertManager.stopBlinking()
        callNotificationManager.cancelNotification()
        CallManager.updateCall(null)
    }
}
