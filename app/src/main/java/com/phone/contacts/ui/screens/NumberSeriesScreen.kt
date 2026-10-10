package com.phone.contacts.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phone.contacts.R
import com.phone.contacts.ads.AdPlacements
import com.phone.contacts.ads.AdType
import com.phone.contacts.ads.NativeAdTemplate
import com.phone.contacts.ads.NativeAdView
import com.phone.contacts.data.local.AppDatabase
import com.phone.contacts.data.local.NumberSeriesEntity
import com.phone.contacts.data.local.NumberSeriesMatchType
import com.phone.contacts.ui.theme.primaryAccentColor
import com.phone.contacts.util.AppConfigStore
import kotlinx.coroutines.launch

@Composable
private fun NumberSeriesMatchType.label(): String = when (this) {
    NumberSeriesMatchType.STARTS_WITH -> stringResource(R.string.type_starts_with)
    NumberSeriesMatchType.CONTAINS -> stringResource(R.string.type_contains)
    NumberSeriesMatchType.ENDS_WITH -> stringResource(R.string.type_ends_with)
}

/** Settings > Blocking > Number Series — block a whole range of numbers by prefix/substring/
 * suffix instead of one exact number at a time (e.g. every telemarketer number starting with a
 * known series). Enforced by [com.phone.contacts.service.ContactsCallScreeningService]. Matches
 * the reference app's own screen (type picker + text field + Add, then the saved list below), but
 * styled like this app's own cards/sheets (see Blocking's own Unknown-callers card and Call
 * screen's audio-route sheet) rather than copying the reference app's look. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberSeriesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.getInstance(context).numberSeriesDao() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val adConfig by AppConfigStore.config.collectAsState()

    var entries by remember { mutableStateOf<List<NumberSeriesEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        dao.getAll().collect { entries = it }
    }

    var selectedType by remember { mutableStateOf(NumberSeriesMatchType.STARTS_WITH) }
    var pattern by remember { mutableStateOf("") }
    var showTypePicker by remember { mutableStateOf(false) }

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
                text = stringResource(R.string.number_series_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Surface(
            onClick = { showTypePicker = true },
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedType.label(),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        OutlinedTextField(
            value = pattern,
            onValueChange = { pattern = it },
            placeholder = { Text(stringResource(R.string.hint_enter_number_series)) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
        )

        Button(
            onClick = {
                val trimmed = pattern.trim()
                if (trimmed.isNotBlank()) {
                    scope.launch { dao.insert(NumberSeriesEntity(matchType = selectedType.name, pattern = trimmed)) }
                    pattern = ""
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            },
            enabled = pattern.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(text = stringResource(R.string.add_to_blocklist), color = MaterialTheme.colorScheme.onPrimary)
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(entries, key = { it.id }) { entry ->
                NumberSeriesRow(
                    entry = entry,
                    onRemove = { scope.launch { dao.deleteById(entry.id) } }
                )
            }
        }

        // Own slot (10), unique from Blocking's native_3 and every other screen's slot, as asked.
        AdPlacements.adUnitId(adConfig?.result, AdType.NATIVE, slot = 10)?.let { adUnitId ->
            NativeAdView(
                adUnitId = adUnitId,
                template = NativeAdTemplate.STRIP
            )
        }
    }

    if (showTypePicker) {
        NumberSeriesTypeSheet(
            selected = selectedType,
            onSelect = {
                selectedType = it
                showTypePicker = false
            },
            onDismiss = { showTypePicker = false }
        )
    }
}

// Same red/pink used elsewhere in this app for a blocked number's own text (Manage Block List,
// Contact Detail's "Blocked" state), kept consistent rather than introducing a new color.
@Composable
private fun blockedPatternColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFF6B9D) else Color(0xFFD32F2F)

@Composable
private fun NumberSeriesRow(entry: NumberSeriesEntity, onRemove: () -> Unit) {
    val matchType = remember(entry.matchType) {
        try {
            NumberSeriesMatchType.valueOf(entry.matchType)
        } catch (_: IllegalArgumentException) {
            NumberSeriesMatchType.STARTS_WITH
        }
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(45.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Block, contentDescription = null, tint = blockedPatternColor(), modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.size(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = matchType.label(), color = MaterialTheme.colorScheme.onBackground)
                Text(
                    text = entry.pattern,
                    color = blockedPatternColor(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = "Remove", tint = blockedPatternColor())
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 78.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberSeriesTypeSheet(
    selected: NumberSeriesMatchType,
    onSelect: (NumberSeriesMatchType) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.number_series_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.clickable(onClick = onDismiss))
            }
            NumberSeriesMatchType.entries.forEach { type ->
                val tint = if (type == selected) primaryAccentColor() else MaterialTheme.colorScheme.onSurface
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(type) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = type.label(),
                        color = tint,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (type == selected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = tint)
                    }
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
        }
    }
}
