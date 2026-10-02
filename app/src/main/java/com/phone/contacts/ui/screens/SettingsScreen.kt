package com.phone.contacts.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phone.contacts.R
import com.phone.contacts.ui.components.CustomSwitch
import com.phone.contacts.ui.components.RateUsDialog
import com.phone.contacts.ui.components.ScreenTitleBar
import com.phone.contacts.ui.theme.primaryAccentColor
import com.phone.contacts.util.KeypadTonePreferences
import com.phone.contacts.util.RateUsHelper

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
    onCallButtonStylesClick: () -> Unit,
    onWallpaperClick: () -> Unit,
    onDisplayOptionsClick: () -> Unit,
    onRingtoneClick: () -> Unit,
    onEmergencyContactsClick: () -> Unit,
    onSpeedDialClick: () -> Unit,
    onQuickResponseClick: () -> Unit
) {
    val context = LocalContext.current
    remember { KeypadTonePreferences.initialize(context) }
    var keypadToneEnabled by remember { mutableStateOf(KeypadTonePreferences.enabled.value) }
    var showRateUsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        ScreenTitleBar(title = stringResource(R.string.settings))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp)
        ) {
            SettingsSectionHeader(title = stringResource(R.string.settings_section_personalization))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Filled.Language,
                    iconBackgroundColor = Color(0xFF2196F3),
                    title = stringResource(R.string.app_language),
                    onClick = onLanguageClick
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Palette,
                    iconBackgroundColor = Color(0xFF9C27B0),
                    title = stringResource(R.string.application_theme_title),
                    onClick = onThemeClick
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.PhoneCallback,
                    iconBackgroundColor = Color(0xFF00BCD4),
                    title = stringResource(R.string.call_back_screen_title),
                    enabled = false,
                    trailing = { CustomSwitch(checked = false, onCheckedChange = {}, enabled = false) }
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Block,
                    iconBackgroundColor = Color(0xFFE0413B),
                    title = stringResource(R.string.blocking_title),
                    onClick = onBlockingClick
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Delete,
                    iconBackgroundColor = Color(0xFF8D6E63),
                    title = stringResource(R.string.recycle_bin_title),
                    onClick = onRecycleBinClick
                )
            }

            SettingsSectionHeader(title = stringResource(R.string.settings_section_appearance))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Filled.TouchApp,
                    iconBackgroundColor = Color(0xFFFF9800),
                    title = stringResource(R.string.call_button_styles_title),
                    onClick = onCallButtonStylesClick
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Wallpaper,
                    iconBackgroundColor = Color(0xFF1DA463),
                    title = stringResource(R.string.wallpaper_label),
                    onClick = onWallpaperClick
                )
            }

            SettingsSectionHeader(title = stringResource(R.string.settings_section_general))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Filled.MusicNote,
                    iconBackgroundColor = Color(0xFF3F51B5),
                    title = stringResource(R.string.action_set_ringtone),
                    onClick = onRingtoneClick
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.ImportExport,
                    iconBackgroundColor = Color(0xFF009688),
                    title = stringResource(R.string.import_export_title),
                    onClick = onImportExportClick
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Emergency,
                    iconBackgroundColor = Color(0xFFD32F2F),
                    title = stringResource(R.string.emergency_contacts_title),
                    onClick = onEmergencyContactsClick
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Tune,
                    iconBackgroundColor = Color(0xFF607D8B),
                    title = stringResource(R.string.display_options_title),
                    onClick = onDisplayOptionsClick
                )
            }

            SettingsSectionHeader(title = stringResource(R.string.settings_section_advance))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Filled.Dialpad,
                    iconBackgroundColor = Color(0xFFFFC107),
                    title = stringResource(R.string.speed_dial_title),
                    onClick = onSpeedDialClick
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.Message,
                    iconBackgroundColor = Color(0xFF9575CD),
                    title = stringResource(R.string.quick_response_title),
                    onClick = onQuickResponseClick
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.MusicNote,
                    iconBackgroundColor = Color(0xFF009688),
                    title = stringResource(R.string.sound_and_vibration_title),
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
                        } catch (e: Exception) {
                            // No such screen on this OEM's build — nothing reasonable to fall back to.
                        }
                    }
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.VolumeUp,
                    iconBackgroundColor = Color(0xFF673AB7),
                    title = stringResource(R.string.keypad_tone_title),
                    trailing = {
                        CustomSwitch(
                            checked = keypadToneEnabled,
                            onCheckedChange = {
                                keypadToneEnabled = it
                                KeypadTonePreferences.setEnabled(context, it)
                            }
                        )
                    }
                )
            }

            SettingsSectionHeader(title = stringResource(R.string.settings_section_about_us))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Filled.PrivacyTip,
                    iconBackgroundColor = Color(0xFF455A64),
                    title = stringResource(R.string.privacy_policy_title),
                    enabled = false
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Description,
                    iconBackgroundColor = Color(0xFF02B98E),
                    title = stringResource(R.string.terms_and_conditions_title),
                    enabled = false
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.StarRate,
                    iconBackgroundColor = Color(0xFFFBC02D),
                    title = stringResource(R.string.rate_us_title),
                    onClick = { showRateUsDialog = true }
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Share,
                    iconBackgroundColor = Color(0xFF43A047),
                    title = stringResource(R.string.share_app_title),
                    onClick = { shareApp(context) }
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Feedback,
                    iconBackgroundColor = Color(0xFFE91E63),
                    title = stringResource(R.string.feedback_title),
                    onClick = { RateUsHelper.openFeedbackEmail(context) }
                )
            }
        }
    }

    if (showRateUsDialog) {
        RateUsDialog(
            onRateClick = { stars ->
                showRateUsDialog = false
                RateUsHelper.handleRating(context, stars)
            },
            onDismiss = { showRateUsDialog = false }
        )
    }
}

private fun shareApp(context: Context) {
    val packageName = context.packageName
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=$packageName")
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_app_title)))
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
                tint = primaryAccentColor(),
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
