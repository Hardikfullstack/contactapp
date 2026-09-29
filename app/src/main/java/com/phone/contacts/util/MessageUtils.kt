package com.phone.contacts.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object MessageUtils {
    /** Opens the default SMS app pre-filled with this number's conversation — used by the
     * Recents list row's swipe-left-to-message action. */
    fun sendMessage(context: Context, number: String) {
        if (number.isBlank()) return

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$number")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't open messaging app", Toast.LENGTH_SHORT).show()
        }
    }
}
