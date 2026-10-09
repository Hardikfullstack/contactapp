package com.phone.contacts.util

import com.phone.contacts.R
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.content.ContextCompat

object MessageUtils {
    /** Opens the default SMS app pre-filled with this number's conversation — used by the
     * Recents list row's swipe-left-to-message action. */
    fun sendMessage(context: Context, number: String) {
        if (number.isBlank()) return

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$number")
        }
        try {
            com.phone.contacts.ads.AppOpenBackgroundReturnTrigger.isAdPaused = true
            context.startActivity(Intent.createChooser(intent, null))
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_messaging_open_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun hasSendSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /** Silently sends [text] to [number] with no further user interaction — used by the
     * incoming-call "Message" quick-response sheet to decline-with-a-text in one tap. Caller must
     * have already confirmed [hasSendSmsPermission]. Returns false if the number is blank or the
     * platform SmsManager throws (no SIM, radio off, etc.). */
    fun sendSmsDirectly(context: Context, number: String, text: String): Boolean {
        if (number.isBlank() || text.isBlank()) return false
        return try {
            // SmsManager.getDefault() works back to minSdk — Context.getSystemService(SmsManager::
            // class.java) is API 31+ only and would crash on older devices.
            @Suppress("DEPRECATION")
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(number, null, text, null, null)
            true
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_message_send_failed), Toast.LENGTH_SHORT).show()
            false
        }
    }
}
