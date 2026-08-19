package com.example.contactapp.service

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
            
            val response = CallResponse.Builder().apply {
                if (status.isSpam()) {
                    setDisallowCall(false) // Still let it ring, but we flag it
                    setRejectCall(false)
                    setSkipCallLog(false)
                    setSkipNotification(false)
                }
            }.build()
            
            respondToCall(callDetails, response)
        }
    }
}
