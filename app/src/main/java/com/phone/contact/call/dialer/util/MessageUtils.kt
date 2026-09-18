package com.phone.contact.call.dialer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.phone.contact.call.dialer.R

object MessageUtils {
    /** Shows the system "Open with" chooser for messaging this number — the default SMS app plus
     * WhatsApp (if installed), instead of jumping straight into the SMS app alone. WhatsApp doesn't
     * respond to smsto: intents, so it's added as an extra initial option pointed at wa.me with the
     * number's digits (country code included) rather than relying on Android to resolve it itself. */
    fun sendMessage(context: Context, number: String, body: String = "") {
        if (number.isBlank()) {
            Toast.makeText(context, context.getString(R.string.toast_invalid_phone_number), Toast.LENGTH_SHORT).show()
            return
        }

        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$number")
            if (body.isNotBlank()) putExtra("sms_body", body)
        }

        val digitsWithCountryCode = PhoneNumberFormatter.withCountryCode(context, number)
            .filter { it.isDigit() }
        val whatsAppIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://wa.me/$digitsWithCountryCode")
            setPackage("com.whatsapp")
        }
        val whatsAppAvailable = digitsWithCountryCode.isNotBlank() &&
            whatsAppIntent.resolveActivity(context.packageManager) != null

        try {
            val chooserIntent = Intent.createChooser(smsIntent, context.getString(R.string.message)).apply {
                if (whatsAppAvailable) {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(whatsAppIntent))
                }
            }
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_could_not_open_messaging_app), Toast.LENGTH_SHORT).show()
        }
    }
}
