package com.phone.contacts.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phone.contacts.R
import com.phone.contacts.ads.AdPlacements
import com.phone.contacts.ads.AdType
import com.phone.contacts.ads.NativeAdView
import com.phone.contacts.ads.NativeAdTemplate
import com.phone.contacts.data.ContactRepository
import com.phone.contacts.util.AppConfigStore
import kotlinx.coroutines.launch

/** Settings > Import/Export — matches the reference app's own screen exactly: just two rows,
 * "Import from file" (pick a .vcf, parse it, insert each contact) and "Export to file" (write
 * every contact out as one combined .vcf), each showing an inline status line while working and
 * once done, instead of navigating away to a separate progress screen. */
@Composable
fun ImportExportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val adConfig by AppConfigStore.config.collectAsState()

    var importStatus by remember { mutableStateOf<String?>(null) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            isImporting = true
            importStatus = context.getString(R.string.toast_contacts_importing)
            coroutineScope.launch {
                val count = ContactRepository.importContactsFromVcf(context, uri)
                importStatus = if (count > 0) context.getString(R.string.toast_contacts_imported) else context.getString(R.string.toast_import_failed)
                isImporting = false
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri ->
        if (uri != null) {
            isExporting = true
            exportStatus = context.getString(R.string.toast_contacts_exporting)
            coroutineScope.launch {
                val count = ContactRepository.exportContactsToVcf(context, uri)
                exportStatus = if (count > 0) context.getString(R.string.toast_contacts_exported) else context.getString(R.string.toast_export_failed)
                isExporting = false
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
                text = stringResource(R.string.import_export_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // Wrapped so this static content stays at the top and the ad below is pinned to the
        // screen's actual bottom.
        Column(modifier = Modifier.weight(1f)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp, vertical = 15.dp)
            ) {
                Column {
                    ImportExportRow(
                        icon = Icons.Filled.FileDownload,
                        iconBackgroundColor = Color(0xFF009688),
                        title = stringResource(R.string.import_from_file_title),
                        status = importStatus,
                        isBusy = isImporting,
                        onClick = {
                            // Matches the reference app: returning from the system file picker
                            // shouldn't trigger an App Open ad.
                            com.phone.contacts.ads.AppOpenBackgroundReturnTrigger.isAdPaused = true
                            importLauncher.launch(arrayOf("text/x-vcard", "text/vcard"))
                        }
                    )
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ImportExportRow(
                        icon = Icons.Filled.FileUpload,
                        iconBackgroundColor = Color(0xFFFF9800),
                        title = stringResource(R.string.export_to_file_title),
                        status = exportStatus,
                        isBusy = isExporting,
                        onClick = {
                            // Matches the reference app: returning from the system file picker
                            // shouldn't trigger an App Open ad.
                            com.phone.contacts.ads.AppOpenBackgroundReturnTrigger.isAdPaused = true
                            exportLauncher.launch("contacts.vcf")
                        }
                    )
                }
            }
        }

        AdPlacements.adUnitId(adConfig?.result, AdType.NATIVE, slot = 3)?.let {
            NativeAdView(
                adUnitId = it,
                template = NativeAdTemplate.STRIP
            )
        }
    }
}

/** Icon badge (34dp colored circle + 18dp white icon) matches SettingsScreen's own [SettingsRow]
 * style exactly, since this screen is reached from that same Settings list. */
@Composable
private fun ImportExportRow(
    icon: ImageVector,
    iconBackgroundColor: Color,
    title: String,
    status: String?,
    isBusy: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isBusy, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        Spacer(modifier = Modifier.size(15.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = MaterialTheme.colorScheme.onBackground)
            if (status != null) {
                Text(
                    text = status,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}
