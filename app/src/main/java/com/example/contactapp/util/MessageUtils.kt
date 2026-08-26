package com.example.contactapp.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.contactapp.R

object MessageUtils {
    fun sendMessage(context: Context, number: String, body: String = "") {
        if (number.isBlank()) {
            Toast.makeText(context, context.getString(R.string.toast_invalid_phone_number), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$number")
                if (body.isNotBlank()) putExtra("sms_body", body)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_could_not_open_messaging_app), Toast.LENGTH_SHORT).show()
        }
    }
}
