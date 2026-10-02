package com.phone.contacts.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.util.Locale

object DeviceUtils {
    /** True when the device is set to gesture navigation (no visible nav bar, edge swipes for
     * back/home) rather than 2/3-button navigation. */
    fun isGestureNavigationEnabled(context: Context): Boolean {
        return try {
            val resourceId = context.resources.getIdentifier("config_navBarInteractionMode", "integer", "android")
            resourceId > 0 && context.resources.getInteger(resourceId) == 2
        } catch (e: Exception) {
            false
        }
    }

    /** True on Xiaomi/Redmi/POCO/Black Shark devices (MIUI/HyperOS) - checked against both
     * MANUFACTURER and BRAND since sub-brands can report either. */
    fun isMiui(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        val miuiBrands = listOf("xiaomi", "redmi", "poco", "blackshark")
        return manufacturer in miuiBrands || brand in miuiBrands
    }

    /** MIUI's "Autostart"/"Other permissions" editor groups three toggles together on one screen:
     * "Display pop-up windows", "Display pop-up windows while running in the background", and
     * "Show on Lock screen". Without these, MIUI can silently swallow the call screen's own
     * startActivity() call even though this app is the default dialer and Telecom did hand it the
     * call - these aren't part of the standard Android permission model, so there's no runtime
     * dialog for them. AppOps codes 10021 (background pop-up) and 10020 (lock-screen display) are
     * undocumented MIUI op codes, checked via reflection since AppOpsManager only exposes the
     * standard Android op codes through the public checkOpNoThrow(String, ...) overload. If the
     * reflection call itself fails (e.g. hidden-API restrictions on a newer build), this assumes
     * NOT granted rather than silently skipping the check. */
    fun isMiuiBackgroundPermissionGranted(context: Context): Boolean {
        return try {
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
            )
            val popupResult = method.invoke(appOpsManager, 10021, Process.myUid(), context.packageName) as Int
            val lockScreenResult = method.invoke(appOpsManager, 10020, Process.myUid(), context.packageName) as Int
            popupResult == AppOpsManager.MODE_ALLOWED && lockScreenResult == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /** Deep-links into MIUI's per-app permission editor, where the three toggles above live.
     * Falls back to this app's system App Info page if the direct editor fails to launch (e.g. a
     * MIUI build that renamed/removed this exact activity). */
    fun openMiuiBackgroundPermissionSettings(context: Context) {
        try {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                putExtra("extra_pkgname", context.packageName)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                )
            } catch (e2: Exception) {
                // Nothing reasonable left to fall back to.
            }
        }
    }
}
