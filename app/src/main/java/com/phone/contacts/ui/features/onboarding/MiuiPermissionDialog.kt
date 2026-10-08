package com.phone.contacts.ui.features.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.phone.contacts.R
import com.phone.contacts.ui.theme.primaryAccentColor
import com.phone.contacts.util.DeviceUtils

private enum class MiuiStep { OVERLAY, PERMISSIONS }

/** MIUI-only popup, in two steps:
 * 1. [MiuiStep.OVERLAY] - the standard Android "Display over other apps" permission. MIUI's own
 *    permission editor (step 2) only offers "Display pop-up windows" as a toggle at all once this
 *    is already granted - skipping straight to step 2 on a fresh install left that fourth toggle
 *    missing entirely, which is why our list only ever showed three rows where the reference app's
 *    showed four.
 * 2. [MiuiStep.PERMISSIONS] - MIUI's own "Display pop-up windows"/"...while running in the
 *    background"/"Show on Lock screen" trio, same as before.
 * Shown once the app is already the default dialer (see MainActivity), not before. Deliberately
 * not dismissible (no back press, no tap-outside) - it keeps re-checking on every return from
 * Settings and calls [onGranted] only once both steps actually report granted. */
@Composable
fun MiuiPermissionDialog(onGranted: () -> Unit) {
    val context = LocalContext.current
    var step by remember {
        mutableStateOf(if (Settings.canDrawOverlays(context)) MiuiStep.PERMISSIONS else MiuiStep.OVERLAY)
    }

    fun recheck() {
        step = when {
            !Settings.canDrawOverlays(context) -> MiuiStep.OVERLAY
            !DeviceUtils.isMiuiBackgroundPermissionGranted(context) -> MiuiStep.PERMISSIONS
            else -> {
                onGranted()
                step
            }
        }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { recheck() }

    // Opening Settings always pauses this Activity and resumes it on return (system back gesture
    // or a "done" button inside Settings alike) - that resume is what re-checks the permission.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) recheck()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = primaryAccentColor(),
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(
                        if (step == MiuiStep.OVERLAY) R.string.miui_overlay_title else R.string.miui_permission_title
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        if (step == MiuiStep.OVERLAY) R.string.miui_overlay_description else R.string.miui_permission_description
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        // Returning from system Settings for this permission shouldn't trigger
                        // an App Open ad right as the user comes back to grant it.
                        com.phone.contacts.ads.AppOpenBackgroundReturnTrigger.isAdPaused = true
                        if (step == MiuiStep.OVERLAY) {
                            overlayLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } else {
                            DeviceUtils.openMiuiBackgroundPermissionSettings(context)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (step == MiuiStep.OVERLAY) R.string.action_go_to_settings else R.string.action_grant_permission
                        ),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
