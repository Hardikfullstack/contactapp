package com.phone.contacts.ui.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import com.phone.contacts.R
import com.phone.contacts.ui.theme.primaryAccentColor

/**
 * Shown instead of a bare [PlaceholderScreen] when a screen needs a runtime permission it doesn't
 * have — unlike that plain text, this gives the user something to actually do about it. [permissions]
 * is whatever the screen's own system permission request launched with; by the time this dialog is
 * shown, that request has already fired once (every call site here only reaches this after its own
 * auto-launch LaunchedEffect), so [ActivityCompat.shouldShowRequestPermissionRationale] reliably
 * means "permanently denied" here rather than its other case of "never asked yet" — the button
 * offers the system prompt again where that's still possible, and falls back to this app's own
 * Settings page once the system won't show its dialog again at all.
 *
 * Not dismissible by back press or tapping outside — the screen behind it has nothing useful to
 * show without the permission, so leaving it open is the correct state until the user acts.
 */
@Composable
fun PermissionRequiredDialog(
    message: String,
    permissions: List<String>,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val canShowSystemPrompt = activity == null ||
        permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }

    AlertDialog(
        onDismissRequest = {},
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text(stringResource(R.string.permission_required_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    if (canShowSystemPrompt) {
                        onRequestPermission()
                    } else {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            )
                        } catch (_: Exception) {
                            // No such settings screen on this OEM build - nothing reasonable to fall back to.
                        }
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = primaryAccentColor())
            ) {
                Text(
                    if (canShowSystemPrompt) stringResource(R.string.action_grant_permission) else stringResource(R.string.action_go_to_settings)
                )
            }
        }
    )
}
