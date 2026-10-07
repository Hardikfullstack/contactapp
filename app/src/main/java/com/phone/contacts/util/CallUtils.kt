package com.phone.contacts.util

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

object CallUtils {
    fun placeCall(context: Context, number: String) {
        if (number.isBlank()) return
        AnalyticsManager.logEventWithAction(AnalyticsEvents.CALL_MADE, AnalyticsEvents.SCREEN_CALL, AnalyticsEvents.ACTION_PLACED)

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCallPermission) return

        val isDefaultDialer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        } else {
            false
        }

        if (isDefaultDialer) {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.placeCall(Uri.fromParts("tel", number, null), null)
        } else {
            val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null))
            context.startActivity(intent)
        }
    }
}
