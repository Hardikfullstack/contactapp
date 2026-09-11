package com.example.contactapp.util

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.UpdateAvailability

/** Thin wrapper around Play Core's In-App Update API. */
class AppUpdateHelper(context: Context) {
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)

    interface UpdateStatusListener {
        fun onUpdateAvailable(appUpdateInfo: AppUpdateInfo)
        fun onUpdateNotAvailable()
        fun onUpdateFailed(e: Exception)
        fun onFlexibleUpdateDownloaded()
    }

    fun checkForUpdate(listener: UpdateStatusListener) {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    listener.onUpdateAvailable(info)
                } else {
                    listener.onUpdateNotAvailable()
                }
            }
            .addOnFailureListener { e ->
                listener.onUpdateFailed(e as? Exception ?: Exception(e))
            }
    }

    /** Google's own guidance: an IMMEDIATE update can get interrupted mid-flow (a call comes in,
     * the app backgrounds, etc.) and silently stall instead of resuming on its own — call this on
     * every app resume/entry to re-launch the flow if one was left in that state. [onResumable] is
     * only invoked when there's genuinely something to resume; a normal "no update in progress"
     * result stays silent. */
    fun resumeStalledUpdateIfAny(onResumable: (AppUpdateInfo) -> Unit) {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    onResumable(info)
                }
            }
    }

    fun startUpdate(
        activity: Activity,
        appUpdateInfo: AppUpdateInfo,
        updateType: Int,
        requestCode: Int,
        onFailure: (() -> Unit)? = null
    ) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                activity,
                AppUpdateOptions.newBuilder(updateType).build(),
                requestCode
            )
        } catch (e: IntentSender.SendIntentException) {
            e.printStackTrace()
            onFailure?.invoke()
        }
    }
}

/**
 * Compares dotted version strings (e.g. "1.0.1" vs "1.0.0") numerically, component by
 * component — a plain string comparison would wrongly say "1.9" > "1.10".
 */
fun isRemoteVersionNewer(remoteVersion: String, currentVersion: String): Boolean {
    val remoteParts = remoteVersion.trim().split(".").mapNotNull { it.toIntOrNull() }
    val currentParts = currentVersion.trim().split(".").mapNotNull { it.toIntOrNull() }
    val maxLen = maxOf(remoteParts.size, currentParts.size)
    for (i in 0 until maxLen) {
        val remotePart = remoteParts.getOrElse(i) { 0 }
        val currentPart = currentParts.getOrElse(i) { 0 }
        if (remotePart != currentPart) return remotePart > currentPart
    }
    return false
}
