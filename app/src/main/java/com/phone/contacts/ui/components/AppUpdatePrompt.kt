package com.phone.contacts.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.install.model.AppUpdateType
import com.phone.contacts.R
import com.phone.contacts.util.AppConfigStore
import com.phone.contacts.util.AppUpdateHelper
import com.phone.contacts.util.AnalyticsEvents
import com.phone.contacts.util.AnalyticsManager
import com.phone.contacts.util.InAppUpdateResult
import com.phone.contacts.util.isRemoteVersionNewer

/** Remote-config-driven in-app update prompt, same behavior as the reference app's: the remote
 * config's extra_data_2_message holds the latest version, extra_data_2_on_off picks Play's in-app
 * API over a plain Play Store redirect, extra_data_5_on_off makes the update soft (dismissible).
 * extra_data_1_on_off turns the prompt off entirely. */
@Composable
fun AppUpdatePrompt() {
    val context = LocalContext.current
    val config by AppConfigStore.config.collectAsState()
    val appUpdateHelper = remember { AppUpdateHelper(context) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    val currentVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }
    LaunchedEffect(config) {
        val remoteVersion = config?.result?.extra_data_2_message
        if (!remoteVersion.isNullOrBlank() && isRemoteVersionNewer(remoteVersion, currentVersion)) {
            showUpdateDialog = true
            AnalyticsManager.logEventWithAction(AnalyticsEvents.APP_UPDATE_DIALOG, AnalyticsEvents.SCREEN_APP_UPDATE, AnalyticsEvents.ACTION_SHOWN, mapOf(AnalyticsEvents.PARAM_TYPE to if (config?.result?.extra_data_5_on_off == "on") "soft" else "hard"))
        }
    }

    // Resume a stalled IMMEDIATE update on every resume, and log the outcome of a just-finished
    // flow (see MainActivity.onActivityResult -> InAppUpdateResult).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            InAppUpdateResult.pendingResultCode?.let { resultCode ->
                InAppUpdateResult.pendingResultCode = null
                val outcome = when (resultCode) {
                    Activity.RESULT_OK -> "ok"
                    Activity.RESULT_CANCELED -> "cancelled"
                    com.google.android.play.core.install.model.ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> "failed"
                    else -> "unknown_$resultCode"
                }
                AnalyticsManager.logEventWithAction(AnalyticsEvents.APP_UPDATE_DIALOG, AnalyticsEvents.SCREEN_APP_UPDATE, AnalyticsEvents.ACTION_FLOW_RESULT, mapOf(AnalyticsEvents.PARAM_RESULT to outcome))
            }
            (context as? Activity)?.let { activity ->
                appUpdateHelper.resumeStalledUpdateIfAny { info ->
                    appUpdateHelper.startUpdate(activity, info, AppUpdateType.IMMEDIATE, InAppUpdateResult.REQUEST_CODE)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showUpdateDialog && config?.result?.extra_data_1_on_off != "on") {
        val isSoftUpdate = config?.result?.extra_data_5_on_off == "on"
        UpdateAppDialog(
            title = stringResource(R.string.update_title),
            description = stringResource(R.string.update_desc),
            onOkClick = {
                AnalyticsManager.logEventWithAction(AnalyticsEvents.APP_UPDATE_DIALOG, AnalyticsEvents.SCREEN_APP_UPDATE, AnalyticsEvents.ACTION_UPDATE_ACCEPTED)
                if (config?.result?.extra_data_2_on_off == "on") {
                    startInAppUpdateOrOpenStore(context, appUpdateHelper)
                } else {
                    openPlayStore(context)
                }
            },
            onCancelClick = if (isSoftUpdate) {
                {
                    AnalyticsManager.logEventWithAction(AnalyticsEvents.APP_UPDATE_DIALOG, AnalyticsEvents.SCREEN_APP_UPDATE, AnalyticsEvents.ACTION_SOFT_UPDATE_DISMISSED)
                    showUpdateDialog = false
                }
            } else null
        )
    }
}

/** Play Core's appUpdateInfo Task can, on some devices, never call back at all. This guard forces
 * the Play Store fallback after a few seconds if no listener method fires, instead of leaving the
 * Update button looking like it did nothing. */
private fun startInAppUpdateOrOpenStore(context: Context, appUpdateHelper: AppUpdateHelper) {
    var handled = false
    val timeoutHandler = Handler(Looper.getMainLooper())
    val timeoutRunnable = Runnable {
        if (!handled) {
            handled = true
            openPlayStore(context)
        }
    }
    timeoutHandler.postDelayed(timeoutRunnable, 4000L)

    appUpdateHelper.checkForUpdate(object : AppUpdateHelper.UpdateStatusListener {
        override fun onUpdateAvailable(appUpdateInfo: AppUpdateInfo) {
            if (handled) return
            handled = true
            timeoutHandler.removeCallbacks(timeoutRunnable)
            val activity = context as? Activity
            if (activity != null) {
                appUpdateHelper.startUpdate(
                    activity,
                    appUpdateInfo,
                    AppUpdateType.IMMEDIATE,
                    InAppUpdateResult.REQUEST_CODE,
                    onFailure = { openPlayStore(context) }
                )
            } else {
                openPlayStore(context)
            }
        }

        override fun onUpdateNotAvailable() {
            if (handled) return
            handled = true
            timeoutHandler.removeCallbacks(timeoutRunnable)
            openPlayStore(context)
        }

        override fun onUpdateFailed(e: Exception) {
            if (handled) return
            handled = true
            timeoutHandler.removeCallbacks(timeoutRunnable)
            openPlayStore(context)
        }

        override fun onFlexibleUpdateDownloaded() {}
    })
}

private fun openPlayStore(context: Context) {
    val packageName = context.packageName
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        } catch (_: ActivityNotFoundException) {
            // No Play Store app or browser available — nothing more we can do.
        }
    }
}
