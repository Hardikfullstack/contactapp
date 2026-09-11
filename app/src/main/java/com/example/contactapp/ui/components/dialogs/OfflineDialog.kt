package com.example.contactapp.ui.components.dialogs

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.contactapp.R
import com.example.contactapp.ads.AppOpenBackgroundReturnTrigger
import com.example.contactapp.ui.theme.PrimaryGreen

/**
 * Shown whenever connectivity drops — not dismissible via outside-tap or back-press, only via the
 * close icon, so the user consciously acknowledges it instead of it disappearing unnoticed. Most
 * of this app's own features (calling, contacts, call log) work fine offline — only ad-backed
 * placements can't load — so this never blocks usage, it just informs. Two buttons deep-link into
 * Wi-Fi and Mobile Data settings separately (being offline could mean either is off), matching the
 * Messages app's own OfflineDialog. Only ever composed from
 * [com.example.contactapp.ui.navigation.MainNavigation], which itself only renders once onboarding
 * is complete — never reachable from Splash/Onboarding.
 */
@Composable
fun OfflineDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false)
    ) {
        Box {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(PrimaryGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.no_internet_connection),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.no_internet_connection_desc),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            // The Settings round trip backgrounds/re-foregrounds this Activity —
                            // without this, that return would look like a normal app-switch-back
                            // and could trigger an App Open ad right as the user is just trying to
                            // fix their connection.
                            AppOpenBackgroundReturnTrigger.isAdPaused = true
                            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        border = BorderStroke(1.dp, PrimaryGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.offline_dialog_button_wifi),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = {
                            AppOpenBackgroundReturnTrigger.isAdPaused = true
                            context.startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.offline_dialog_button_mobile_data),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.cancel),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(18.dp)
                    .clickable(onClick = onDismiss)
            )
        }
    }
}
