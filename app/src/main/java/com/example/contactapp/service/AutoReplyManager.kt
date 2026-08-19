package com.example.contactapp.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.contactapp.util.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends the user's configured auto-reply text to a caller they just declined. Only fires for
 * real calls — a decline on a fake/simulated call has no real person on the other end to text.
 */
@Singleton
class AutoReplyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManager: PreferenceManager
) {
    fun sendReplyIfEnabled(number: String) {
        if (!preferenceManager.isAutoReplyEnabled()) return
        if (number.isBlank()) return

        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val message = preferenceManager.getAutoReplyMessage()
        if (message.isBlank()) return

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(number, null, message, null, null)
        } catch (e: Exception) {
            Log.e("AutoReplyManager", "sendReplyIfEnabled: FAILED — ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }
}
