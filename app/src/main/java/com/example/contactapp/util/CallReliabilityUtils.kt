package com.example.contactapp.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Being the default dialer only makes Telecom *offer* incoming calls to this app's
 * InCallService — it does not stop OEM battery managers (MIUI, ColorOS, FuntouchOS,
 * EMUI, etc.) from freezing/killing the app process in the background, which is the
 * most common reason "outgoing works, incoming doesn't" on a phone that just switched
 * to this app as default dialer. These helpers surface the two settings screens that
 * actually fix that on affected devices.
 */
object CallReliabilityUtils {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun batteryOptimizationIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
    }

    // Known OEM "autostart" / "protected apps" / background-permission screens.
    // Manufacturer strings match Build.MANUFACTURER (lowercased).
    private val autoStartIntents: Map<String, List<Intent>> = mapOf(
        "xiaomi" to listOf(
            Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        ),
        "oppo" to listOf(
            Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            Intent().setClassName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
        ),
        "vivo" to listOf(
            Intent().setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            Intent().setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
        ),
        "huawei" to listOf(
            Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
        ),
        "honor" to listOf(
            Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
        ),
        "oneplus" to listOf(
            Intent().setClassName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
        ),
        "asus" to listOf(
            Intent().setClassName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")
        ),
        "letv" to listOf(
            Intent().setClassName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")
        ),
        "meizu" to listOf(
            Intent().setClassName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity")
        )
    )

    /** Returns a resolvable OEM autostart-settings intent for this device, or null if none is known/available. */
    fun autoStartIntent(context: Context): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val candidates = autoStartIntents[manufacturer] ?: return null
        return candidates.firstOrNull { intent ->
            intent.resolveActivity(context.packageManager) != null
        }
    }
}
