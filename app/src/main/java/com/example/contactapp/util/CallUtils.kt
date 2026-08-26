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
import com.example.contactapp.R

object CallUtils {
    fun makeCall(context: Context, number: String) {
        if (number.isBlank()) {
            Toast.makeText(context, context.getString(R.string.toast_invalid_phone_number), Toast.LENGTH_SHORT).show()
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
                    // FLAG_ACTIVITY_NEW_TASK is required when this is called from a non-Activity
                    // context (e.g. a notification action's BroadcastReceiver, like the Call
                    // Reminder "Call Now" action) — harmless when called from an Activity too.
                    val intent = Intent(Intent.ACTION_CALL).apply {
                        data = Uri.parse("tel:$number")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } else {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$number")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_could_not_initiate_call), Toast.LENGTH_SHORT).show()
        }
    }
}
