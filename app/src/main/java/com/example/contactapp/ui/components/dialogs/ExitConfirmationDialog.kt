package com.example.contactapp.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.contactapp.R
import com.example.contactapp.ads.NativeAdTemplate
import com.example.contactapp.ads.NativeAdView
import com.example.contactapp.ui.theme.PrimaryGreen

/**
 * Shown on back-press from any top-level tab instead of exiting immediately — a bottom sheet
 * (not a centered dialog) anchored to the bottom edge like the reference app: a "Tap here to
 * exit" bar (the only way this actually exits) above a native ad whose own call-to-action button
 * renders full-width below the ad content, so a back-press impression/click still has a chance to
 * monetize instead of just closing the app for free. Tapping outside the sheet (the scrim above
 * it), or system back again while this is shown, just dismisses it and returns to the app.
 */
@Composable
fun ExitConfirmationDialog(
    nativeAdUnitId: String?,
    onExitClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        // usePlatformDefaultWidth=false makes this dialog's own window fill the whole screen, so
        // Compose's built-in dismissOnClickOutside (which only fires outside the WINDOW's bounds)
        // no longer has any "outside" to detect — the explicit scrim clickable below does that
        // job instead.
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrim: fills the whole dialog, sits behind the sheet, dismisses on tap. The sheet
            // itself consumes its own taps (see below) so they never fall through to this.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    // Consumes taps landing on the sheet itself so they don't pass through to the
                    // scrim behind it and dismiss when the user is just tapping around the ad.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onExitClick)
                        .padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = PrimaryGreen
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.tap_here_to_exit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (nativeAdUnitId != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NativeAdView(
                        adUnitId = nativeAdUnitId,
                        template = NativeAdTemplate.EXIT,
                        modifier = Modifier.navigationBarsPadding()
                    )
                }
            }
        }
    }
}
