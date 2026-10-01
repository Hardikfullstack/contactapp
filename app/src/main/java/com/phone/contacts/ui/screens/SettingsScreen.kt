package com.phone.contacts.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phone.contacts.ui.components.CustomSwitch
import com.phone.contacts.ui.components.ScreenTitleBar

/** Matches the reference app's Settings screen structure: Personalization (App language,
 * Application theme, Call back screen, Blocking, Recycle bin), Appearance (Call button styles,
 * Wallpaper), General (Ringtone, Import/Export, Emergency contacts, Display options), Advance
 * settings (Speed dial, Quick response, Sound and vibration, Keypad tone), About us (Privacy
 * policy, Rate us, Share app, Feedback) — every row uses the reference app's colorful icon-badge
 * style. App language, Application theme, Rate us, Share app, Recycle bin, Import/Export and
 * Blocking are wired to real behavior; the rest mirror the reference app's list but stay disabled
 * since those features don't exist in this app yet. */
@Composable
fun SettingsScreen(
    onLanguageClick: () -> Unit,
    onRecycleBinClick: () -> Unit,
    onImportExportClick: () -> Unit,
    onThemeClick: () -> Unit,
    onBlockingClick: () -> Unit,
    onCallButtonStylesClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        ScreenTitleBar(title = "Settings")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp)
        ) {
            SettingsSectionHeader(title = "Personalization")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Filled.Language,
                    iconBackgroundColor = Color(0xFF2196F3),
                    title = "App language",
                    onClick = onLanguageClick
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Palette,
                    iconBackgroundColor = Color(0xFF9C27B0),
                    title = "Application theme",
                    onClick = onThemeClick
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.PhoneCallback,
                    iconBackgroundColor = Color(0xFF00BCD4),
                    title = "Call back screen",
                    enabled = false,
                    trailing = { CustomSwitch(checked = false, onCheckedChange = {}, enabled = false) }
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Block,
                    iconBackgroundColor = Color(0xFFE0413B),
                    title = "Blocking",
                    onClick = onBlockingClick
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Delete,
                    iconBackgroundColor = Color(0xFF8D6E63),
                    title = "Recycle bin",
                    onClick = onRecycleBinClick
                )
            }

            SettingsSectionHeader(title = "Appearance")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Filled.TouchApp,
                    iconBackgroundColor = Color(0xFFFF9800),
                    title = "Call button styles",
                    onClick = onCallButtonStylesClick
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Wallpaper,
                    iconBackgroundColor = Color(0xFF1DA463),
                    title = "Wallpaper",
                    enabled = false
                )
            }

            SettingsSectionHeader(title = "General")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Filled.MusicNote,
                    iconBackgroundColor = Color(0xFF3F51B5),
                    title = "Ringtone",
                    enabled = false
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.ImportExport,
                    iconBackgroundColor = Color(0xFF009688),
                    title = "Import/Export",
                    onClick = onImportExportClick
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Emergency,
                    iconBackgroundColor = Color(0xFFD32F2F),
                    title = "Emergency contacts",
                    enabled = false
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Tune,
                    iconBackgroundColor = Color(0xFF607D8B),
                    title = "Display options",
                    enabled = false
                )
            }

            SettingsSectionHeader(title = "Advance settings")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Filled.Dialpad,
                    iconBackgroundColor = Color(0xFFFFC107),
                    title = "Speed dial",
                    enabled = false
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.Message,
                    iconBackgroundColor = Color(0xFF9575CD),
                    title = "Quick response",
                    enabled = false
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.MusicNote,
                    iconBackgroundColor = Color(0xFF009688),
                    title = "Sound and vibration",
                    enabled = false
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.VolumeUp,
                    iconBackgroundColor = Color(0xFF673AB7),
                    title = "Keypad tone",
                    enabled = false
                )
            }

            SettingsSectionHeader(title = "About us")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Filled.PrivacyTip,
                    iconBackgroundColor = Color(0xFF455A64),
                    title = "Privacy policy",
                    enabled = false
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.StarRate,
                    iconBackgroundColor = Color(0xFFFBC02D),
                    title = "Rate us",
                    onClick = { openPlayStoreListing(context) }
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Share,
                    iconBackgroundColor = Color(0xFF43A047),
                    title = "Share app",
                    onClick = { shareApp(context) }
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Feedback,
                    iconBackgroundColor = Color(0xFFE91E63),
                    title = "Feedback",
                    enabled = false
                )
            }
        }
    }

}

private fun openPlayStoreListing(context: Context) {
    val packageName = context.packageName
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
        )
    }
}

private fun shareApp(context: Context) {
    val packageName = context.packageName
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=$packageName")
    }
    context.startActivity(Intent.createChooser(intent, "Share app"))
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    // The reference app's colorful icon-badge style (white icon on a colored chip). Falls back
    // to a plain tinted icon if a caller ever omits it.
    iconBackgroundColor: Color? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconBackgroundColor != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = title,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        )
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 54.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    )
}
