package com.example.contactapp.util

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.core.content.ContextCompat

object CallUtils {
    fun makeCall(context: Context, number: String) {
        if (number.isBlank()) {
            Toast.makeText(context, "Invalid phone number", Toast.LENGTH_SHORT).show()
            return
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val isDefaultDialer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        } else {
            false
        }

        try {
            if (hasCallPermission) {
                if (isDefaultDialer) {
                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    val uri = Uri.parse("tel:$number")
                    telecomManager.placeCall(uri, null)
                } else {
                    val intent = Intent(Intent.ACTION_CALL).apply {
                        data = Uri.parse("tel:$number")
                    }
                    context.startActivity(intent)
                }
            } else {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$number")
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not initiate call", Toast.LENGTH_SHORT).show()
        }
    }
}
