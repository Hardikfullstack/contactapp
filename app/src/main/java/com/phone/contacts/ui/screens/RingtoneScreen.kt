package com.phone.contacts.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.phone.contacts.R
import com.phone.contacts.ads.AdPlacements
import com.phone.contacts.ads.AdType
import com.phone.contacts.ads.NativeAdView
import com.phone.contacts.ads.NativeAdTemplate
import com.phone.contacts.data.ContactRepository
import com.phone.contacts.ui.theme.primaryAccentColor
import com.phone.contacts.util.AnalyticsEvents
import com.phone.contacts.util.AppConfigStore
import com.phone.contacts.util.AnalyticsManager
import com.phone.contacts.util.DeviceAudioFile
import com.phone.contacts.util.SystemRingtoneItem
import com.phone.contacts.util.formatAudioDuration
import com.phone.contacts.util.queryDeviceAudioFiles
import com.phone.contacts.util.querySystemRingtones
import kotlinx.coroutines.launch

private val RED_ICON_BG = Color(0xFFE0413B)

/** Distinct from both a real ringtone Uri and from null (which means "Silent" — an explicit,
 * playable choice). Only used in contact mode, to represent "no override, follow the system
 * default" — what an absent CUSTOM_RINGTONE column means — since plain null is already taken by
 * Silent and can't do double duty for both meanings. */
private val USE_DEFAULT_SENTINEL: Uri = Uri.parse("ringtone-sentinel://use-default")

/** [contactId] null = Settings > Ringtone (writes the system default ringtone, needs
 * WRITE_SETTINGS). Non-null = Contact detail > More > Set Ringtone (writes that contact's
 * CUSTOM_RINGTONE column via [ContactRepository], no special permission beyond WRITE_CONTACTS
 * already held). "System default" opens the device's own built-in ringtone chooser, "Current
 * ringtone" shows the resolved current pick, "Custom" lists every audio file on the device
 * (READ_MEDIA_AUDIO/READ_EXTERNAL_STORAGE gated, matching the reference app's own Custom section),
 * and "System" lists every stock tone the device ships with (matching contactapp's own Set
 * Ringtone screen) plus an explicit "Silent" choice — an in-app alternative to the picker above
 * for anyone who'd rather not leave this screen. */
@Composable
fun RingtoneScreen(
    contactId: String?,
    contactName: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adConfig by AppConfigStore.config.collectAsState()
    val isContactMode = contactId != null

    var isLoading by remember { mutableStateOf(true) }
    var stagedUri by remember { mutableStateOf<Uri?>(null) }
    var previewRingtone by remember { mutableStateOf<Ringtone?>(null) }

    fun preview(uri: Uri?) {
        previewRingtone?.stop()
        val resolvedUri = if (uri == USE_DEFAULT_SENTINEL) {
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
        } else {
            uri
        }
        previewRingtone = if (resolvedUri != null) {
            try {
                RingtoneManager.getRingtone(context, resolvedUri)?.apply { play() }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    LaunchedEffect(contactId) {
        stagedUri = if (isContactMode) {
            when (val stored = ContactRepository.getCustomRingtone(context, contactId!!)) {
                null -> USE_DEFAULT_SENTINEL // no override set for this contact
                Uri.EMPTY -> null // explicitly silenced
                else -> stored
            }
        } else {
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
        }
        isLoading = false
    }

    DisposableEffect(Unit) {
        onDispose {
            previewRingtone?.stop()
            previewRingtone = null
        }
    }

    var hasWriteSettings by remember { mutableStateOf(Settings.System.canWrite(context)) }
    val writeSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasWriteSettings = Settings.System.canWrite(context)
    }
    fun openWriteSettingsScreen() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        writeSettingsLauncher.launch(intent)
    }

    // Only Android 13+ needs a runtime grant for this (READ_MEDIA_AUDIO) — scoped storage already
    // lets any app query MediaStore.Audio.Media for every device's audio file on API 29-32 with no
    // permission at all, and below that (this app's minSdk 26 floor) the query just comes back
    // empty rather than crashing, since there's no permission declared to request there either.
    val needsRuntimeAudioPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var hasAudioPermission by remember {
        mutableStateOf(
            !needsRuntimeAudioPermission ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPermission = granted }

    // Asked for right away, matching the reference app's own flow (the system dialog appears as
    // soon as this screen opens, from either entry point) instead of waiting for the user to
    // scroll to the Custom section and tap a card.
    LaunchedEffect(Unit) {
        if (needsRuntimeAudioPermission && !hasAudioPermission) {
            audioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        }
    }

    var customFiles by remember { mutableStateOf<List<DeviceAudioFile>>(emptyList()) }
    var customFilesLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission && !customFilesLoaded) {
            customFiles = queryDeviceAudioFiles(context)
            customFilesLoaded = true
        }
    }

    var systemRingtones by remember { mutableStateOf<List<SystemRingtoneItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        systemRingtones = querySystemRingtones(context)
    }

    val systemPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            stagedUri = uri
            preview(uri)
        }
    }
    fun openSystemRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.system_default_label))
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            val existingUri = if (stagedUri == USE_DEFAULT_SENTINEL) {
                RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            } else {
                stagedUri
            }
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
        }
        systemPickerLauncher.launch(intent)
    }

    // Resolved here (stringResource needs a composable context) so the remember{} block below -
    // which @DisallowComposableCalls forbids calling stringResource from directly - can just
    // close over these as plain strings.
    val defaultWithNameLabel = stringResource(R.string.default_with_name_label)
    val defaultLabel = stringResource(R.string.default_label)
    val silentLabel = stringResource(R.string.silent_label)
    val unknownLabel = stringResource(R.string.unknown)
    val currentTitle = remember(stagedUri) {
        when (stagedUri) {
            USE_DEFAULT_SENTINEL -> {
                val defaultUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                val defaultTitle = try {
                    defaultUri?.let { RingtoneManager.getRingtone(context, it)?.getTitle(context) }
                } catch (e: Exception) {
                    null
                }
                if (defaultTitle != null) String.format(defaultWithNameLabel, defaultTitle) else defaultLabel
            }
            null -> silentLabel
            else -> try {
                RingtoneManager.getRingtone(context, stagedUri)?.getTitle(context) ?: unknownLabel
            } catch (e: Exception) {
                unknownLabel
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = if (isContactMode) stringResource(R.string.ringtone_for_contact_title, contactName.orEmpty()) else stringResource(R.string.action_set_ringtone),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                maxLines = 1
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = primaryAccentColor())
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValuesVertical) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable {
                                if (!isContactMode && !hasWriteSettings) {
                                    openWriteSettingsScreen()
                                } else {
                                    openSystemRingtonePicker()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.system_default_label),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.default_tones_description),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.current_ringtone_label),
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    RingtoneRow(
                        title = currentTitle,
                        subtitle = null,
                        selected = true,
                        onClick = { preview(stagedUri) }
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.custom_type_fallback),
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!hasAudioPermission) {
                    item {
                        PermissionCard(
                            title = stringResource(R.string.allow_audio_access_title),
                            description = stringResource(R.string.allow_audio_access_description),
                            buttonLabel = stringResource(R.string.grant_access_label),
                            onClick = { audioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO) }
                        )
                    }
                } else if (customFiles.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_audio_files_found),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    items(customFiles, key = { it.uri.toString() }) { file ->
                        RingtoneRow(
                            title = file.title,
                            subtitle = formatAudioDuration(file.durationMs),
                            selected = file.uri == stagedUri,
                            onClick = {
                                stagedUri = file.uri
                                preview(file.uri)
                            }
                        )
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.system_label),
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    RingtoneRow(
                        title = stringResource(R.string.silent_label),
                        subtitle = null,
                        selected = stagedUri == null,
                        onClick = {
                            stagedUri = null
                            preview(null)
                        }
                    )
                }
                items(systemRingtones, key = { it.uri.toString() }) { ringtone ->
                    RingtoneRow(
                        title = ringtone.title,
                        subtitle = null,
                        selected = ringtone.uri == stagedUri,
                        onClick = {
                            stagedUri = ringtone.uri
                            preview(ringtone.uri)
                        }
                    )
                }
            }
        }

        Button(
            onClick = {
                if (!isContactMode && !hasWriteSettings) {
                    openWriteSettingsScreen()
                } else {
                    scope.launch {
                        if (isContactMode) {
                            val valueToStore = when (stagedUri) {
                                USE_DEFAULT_SENTINEL -> null // clears the override entirely
                                null -> Uri.EMPTY // explicit Silent, distinct from "never set"
                                else -> stagedUri
                            }
                            ContactRepository.setCustomRingtone(context, contactId!!, valueToStore)
                            AnalyticsManager.logEventWithAction(AnalyticsEvents.CONTACT_UPDATED, "RingtoneScreen", AnalyticsEvents.ACTION_RINGTONE_SET)
                        } else {
                            RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, stagedUri)
                        }
                        Toast.makeText(context, context.getString(R.string.toast_ringtone_updated), Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(stringResource(R.string.action_set_ringtone_button), fontWeight = FontWeight.Bold)
        }

        AdPlacements.adUnitId(adConfig?.result, AdType.NATIVE, slot = 17)?.let {
            NativeAdView(
                adUnitId = it,
                template = NativeAdTemplate.STRIP
            )
        }
    }
}

private val PaddingValuesVertical = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)

@Composable
private fun RingtoneRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(RED_ICON_BG),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            if (subtitle != null) {
                Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(width = 1.5.dp, color = MaterialTheme.colorScheme.onSurfaceVariant, shape = CircleShape)
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Text(buttonLabel)
            }
        }
    }
}
