package com.phone.contacts.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phone.contacts.R
import com.phone.contacts.ads.AdPlacements
import com.phone.contacts.ads.AdType
import com.phone.contacts.ads.NativeAdTemplate
import com.phone.contacts.ads.NativeAdView
import com.phone.contacts.ads.rememberBackWithInterstitial
import com.phone.contacts.data.BlockRepository
import com.phone.contacts.ui.components.CustomSwitch
import com.phone.contacts.ui.theme.primaryAccentColor
import com.phone.contacts.util.AppConfigStore
import kotlinx.coroutines.launch

/** Settings > Blocking — matches the reference app's own screen: a toggle for unidentified
 * callers, a "Manage block list" button, and an "Add a number" link. The toggle is a real,
 * persisted preference, but doesn't yet reject calls by itself — actually screening out private/
 * unknown-number calls needs a CallScreeningService this app doesn't have (a separate, bigger
 * feature, not part of this pass); blocking a specific number via [BlockRepository.blockNumber]
 * is real, since the system's own block list is honored by the telephony stack directly. The
 * reference app's "Number Series" range-blocking row is intentionally left out here too. */
@Composable
fun BlockingScreen(onBack: () -> Unit, onManageBlockList: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val adConfig by AppConfigStore.config.collectAsState()
    // Matches the reference app: BlockedActivity shows an interstitial on back. Shares
    // interstitial_7 with Theme and Recycle Bin — only 7 interstitial slots exist.
    val backWithAd = rememberBackWithInterstitial(
        AdPlacements.adUnitId(adConfig?.result, AdType.INTERSTITIAL_ON_BACK, slot = 4),
        onBack
    )
    var blockUnknownCallers by remember { mutableStateOf(BlockRepository.isBlockUnknownCallersEnabled(context)) }
    var showAddNumberDialog by remember { mutableStateOf(false) }

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
            IconButton(onClick = backWithAd) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = stringResource(R.string.blocking_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // Wrapped so this static content stays at the top and the ad below is pinned to the
        // screen's actual bottom, instead of sitting right after whatever this content's own
        // height happens to be.
        Column(modifier = Modifier.weight(1f)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(15.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 7.dp)) {
                        Text(text = stringResource(R.string.unknown_callers_label), color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            text = stringResource(R.string.block_unidentified_callers_description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                    CustomSwitch(
                        checked = blockUnknownCallers,
                        onCheckedChange = {
                            blockUnknownCallers = it
                            BlockRepository.setBlockUnknownCallersEnabled(context, it)
                        }
                    )
                }
            }

            Button(
                onClick = onManageBlockList,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(text = stringResource(R.string.manage_block_list), color = MaterialTheme.colorScheme.onPrimary)
            }

            Text(
                text = stringResource(R.string.blocked_numbers_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Text(
                text = stringResource(R.string.add_a_number),
                color = primaryAccentColor(),
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .clickable { showAddNumberDialog = true }
            )
        }

        // Matches the reference app: native ad (not banner) on this screen. native_1 is reserved
        // for the Language screen, native_2 for Theme. STRIP is the edge-to-edge 50/50 media-left
        // /text-right layout (no card background) - intentionally flush to the screen edges, not
        // padded like MEDIUM.
        AdPlacements.adUnitId(adConfig?.result, AdType.NATIVE, slot = 3)?.let { adUnitId ->
            NativeAdView(
                adUnitId = adUnitId,
                template = NativeAdTemplate.STRIP
            )
        }
    }

    if (showAddNumberDialog) {
        AddBlockedNumberDialog(
            onDismiss = { showAddNumberDialog = false },
            onBlock = { number ->
                showAddNumberDialog = false
                coroutineScope.launch { BlockRepository.blockNumber(context, number) }
            }
        )
    }
}

@Composable
private fun AddBlockedNumberDialog(onDismiss: () -> Unit, onBlock: (String) -> Unit) {
    var number by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text(stringResource(R.string.block_calls_and_text_from)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                placeholder = { Text(stringResource(R.string.hint_phone_number)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onBlock(number.trim()) },
                enabled = number.isNotBlank()
            ) { Text(stringResource(R.string.action_block)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
