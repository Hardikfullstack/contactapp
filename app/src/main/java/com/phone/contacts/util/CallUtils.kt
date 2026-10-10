package com.phone.contacts.util

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
import com.phone.contacts.R

object CallUtils {
    fun placeCall(context: Context, number: String) {
        if (number.isBlank()) {
            Toast.makeText(context, context.getString(R.string.toast_invalid_phone_number), Toast.LENGTH_SHORT).show()
            return
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val isDefaultDialer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        } else {
            false
        }

        try {
            AnalyticsManager.logEventWithAction(AnalyticsEvents.CALL_MADE, AnalyticsEvents.SCREEN_CALL, AnalyticsEvents.ACTION_PLACED)
            if (hasCallPermission) {
                if (isDefaultDialer) {
                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    telecomManager.placeCall(Uri.fromParts("tel", number, null), null)
                } else {
                    // FLAG_ACTIVITY_NEW_TASK is required when this is called from a non-Activity
                    // context (e.g. a notification action's BroadcastReceiver, like a Call
                    // Reminder's "Call Now" action) - harmless when called from an Activity too.
                    val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } else {
                // No CALL_PHONE permission - fall back to the system Dialer pre-filled with the
                // number instead of silently doing nothing, so the user can still place the call
                // themselves with one more tap.
                val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_could_not_initiate_call), Toast.LENGTH_SHORT).show()
        }
    }
}
