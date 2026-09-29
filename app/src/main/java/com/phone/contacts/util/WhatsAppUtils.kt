package com.phone.contacts.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

private const val WHATSAPP_PACKAGE = "com.whatsapp"

object WhatsAppUtils {
    fun isInstalled(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo(WHATSAPP_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    /** Opens the number's WhatsApp chat. Used for the Message action, and — since Android has no
     * public intent to place a WhatsApp voice/video call directly — reused as the closest
     * available stand-in for the Voice call/Video call actions too (WhatsApp's own call buttons
     * live inside that chat). */
    fun openChat(context: Context, number: String) {
        if (number.isBlank()) return
        val digits = number.filter { it.isDigit() || it == '+' }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).apply {
            setPackage(WHATSAPP_PACKAGE)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "WhatsApp isn't installed", Toast.LENGTH_SHORT).show()
        }
    }
}
