package com.example.contactapp.util

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import java.util.Locale

/** FLAG_ACTIVITY_NEW_TASK is only required when launching from a non-Activity context (e.g. the
 * Application context SettingsViewModel is injected with) — but it also puts the OEM settings
 * screen in a separate task, so pressing back from it goes to the home screen/launcher instead of
 * back into this app. Passing the real Activity context (e.g. LocalContext.current from Compose)
 * avoids the flag entirely, keeping the settings screen on this app's own task back stack so back
 * actually returns here. */
private fun Intent.addNewTaskFlagIfNeeded(context: Context): Intent = apply {
    if (context !is Activity) flags = Intent.FLAG_ACTIVITY_NEW_TASK
}

/**
 * Being the default dialer only makes Telecom *offer* incoming calls to this app's
 * InCallService — it does not stop OEM battery managers (MIUI, ColorOS, FuntouchOS,
 * EMUI, etc.) from freezing/killing the app process in the background, which is the
 * most common reason "outgoing works, incoming doesn't" (and why the After Call screen
 * can silently fail to appear) on a phone that just switched to this app as default dialer.
 * These helpers surface the settings screens that actually fix that on affected devices.
 *
 * Every OEM intent here is launched as an EXPLICIT component intent (setClassName) directly
 * via startActivity() inside try/catch — never gated behind resolveActivity()/queryIntentActivities()
 * first. On Android 11+ (this app's targetSdk), those query APIs are subject to package-visibility
 * filtering and silently return "not found" for every one of these OEM security-center packages
 * since none of them are declared in a <queries> manifest block — resolveActivity() would always
 * report false even when the component genuinely exists and the explicit launch would succeed.
 * An explicit intent bypasses that filtering entirely, so try-and-catch is both the more accurate
 * check and the one that actually works.
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

    /** True on Xiaomi/Redmi/POCO/Black Shark devices (MIUI/HyperOS) — checked against both
     * MANUFACTURER and BRAND since sub-brands can report either. */
    fun isMiui(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        val miuiBrands = listOf("xiaomi", "redmi", "poco", "blackshark")
        return manufacturer in miuiBrands || brand in miuiBrands
    }

    /** True on OnePlus/Oppo (OxygenOS 12+ runs on the same ColorOS-based security components
     * post-merger, so genuine Oppo devices share the identical restriction and fix). */
    fun isOnePlusOrOppo(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        val brands = listOf("oneplus", "oppo")
        return manufacturer in brands || brand in brands
    }

    // Known OEM "autostart" / "protected apps" screens, tried in order until one launches
    // successfully. Manufacturer/brand keys match Build.MANUFACTURER/BRAND (lowercased).
    private val autoStartCandidates: Map<String, List<Pair<String, String>>> = mapOf(
        "xiaomi" to listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ),
        // OnePlus + Oppo share this list post-merger — older OxygenOS component first, then the
        // newer ColorOS-based ones (OxygenOS 12+ / genuine Oppo).
        "oneplus_oppo" to listOf(
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.coloros.bootreg" to "com.coloros.bootreg.activity.MainActivity",
            "com.oplus.safecenter" to "com.oplus.safecenter.startupapp.StartupAppListActivity"
        ),
        "vivo" to listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
        ),
        "huawei" to listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        ),
        "honor" to listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        ),
        "asus" to listOf(
            "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity"
        ),
        "letv" to listOf(
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity"
        ),
        "meizu" to listOf(
            "com.meizu.safe" to "com.meizu.safe.permission.SmartBGActivity"
        )
    )

    private fun autoStartKeyFor(manufacturer: String): String? = when {
        manufacturer == "xiaomi" -> "xiaomi"
        manufacturer in listOf("oneplus", "oppo") -> "oneplus_oppo"
        else -> autoStartCandidates.keys.firstOrNull { it == manufacturer }
    }

    /** True if this device's manufacturer/brand has a known autostart-settings screen worth
     * surfacing a Settings row for — a static manufacturer-name check, not a resolveActivity()
     * probe (see the class doc comment for why that check would be unreliable here). */
    fun hasKnownAutoStartSettings(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        return autoStartKeyFor(manufacturer) != null || autoStartKeyFor(brand) != null
    }

    /** Tries each known candidate for this device in turn (explicit intent + startActivity(),
     * catching ActivityNotFoundException) until one launches; falls back to this app's own
     * Settings page if none of them exist on this device/OS version. Returns true if something
     * was launched (the app-settings fallback counts). */
    fun launchAutoStartSettings(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        val key = autoStartKeyFor(manufacturer) ?: autoStartKeyFor(brand)
        val candidates = key?.let { autoStartCandidates[it] } ?: emptyList()

        for ((pkg, cls) in candidates) {
            try {
                val intent = Intent().apply {
                    setClassName(pkg, cls)
                }.addNewTaskFlagIfNeeded(context)
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // Try the next candidate.
            }
        }
        return try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addNewTaskFlagIfNeeded(context)
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Deep-links into MIUI's per-app permission editor, where "Display pop-up windows while
     * running in the background" lives. Without it, MIUI can silently swallow the
     * startActivity() call InCallService/AfterCallReceiver makes — even though this app is the
     * default dialer and Telecom did hand it the call — since this permission isn't part of the
     * standard Android permission model and can't be requested via the normal runtime dialog.
     * Falls back to this app's system App Info page if the direct editor fails to launch.
     */
    fun openMiuiBackgroundPopupSettings(context: Context) {
        try {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                putExtra("extra_pkgname", context.packageName)
            }.addNewTaskFlagIfNeeded(context)
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    fun openMiuiAutoStartSettings(context: Context) {
        try {
            val intent = Intent().apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            }.addNewTaskFlagIfNeeded(context)
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    fun openAppSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addNewTaskFlagIfNeeded(context)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** AppOps codes 10021 (background pop-up) and 10020 (lock-screen display) — undocumented MIUI
     * op codes, checked via reflection since AppOpsManager only exposes the standard Android
     * op codes through the public checkOpNoThrow(String, ...) overload. If reflection fails for
     * any reason, assume granted rather than blocking the user on a check that can't be trusted. */
    fun isMiuiBackgroundPopupGranted(context: Context): Boolean {
        return try {
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
            )
            val popupResult = method.invoke(appOpsManager, 10021, Process.myUid(), context.packageName) as Int
            val lockScreenResult = method.invoke(appOpsManager, 10020, Process.myUid(), context.packageName) as Int
            popupResult == AppOpsManager.MODE_ALLOWED && lockScreenResult == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            true
        }
    }

    /** AppOps code 10008 — MIUI's autostart-permission op, same reflection caveat as above. */
    fun isMiuiAutostartGranted(context: Context): Boolean {
        return try {
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
            )
            val result = method.invoke(appOpsManager, 10008, Process.myUid(), context.packageName) as Int
            result == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            true
        }
    }
}
