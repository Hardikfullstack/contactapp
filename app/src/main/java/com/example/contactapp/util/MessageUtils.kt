package com.example.contactapp.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object MessageUtils {
    fun sendMessage(context: Context, number: String) {
        if (number.isBlank()) {
            Toast.makeText(context, "Invalid phone number", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$number")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open messaging app", Toast.LENGTH_SHORT).show()
        }
    }
}
