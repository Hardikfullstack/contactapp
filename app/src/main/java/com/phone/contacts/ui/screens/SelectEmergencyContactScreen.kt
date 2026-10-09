package com.phone.contacts.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddIcCall
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phone.contacts.R
import com.phone.contacts.ads.AdPlacements
import com.phone.contacts.ads.AdType
import com.phone.contacts.ads.NativeAdView
import com.phone.contacts.ads.NativeAdTemplate
import com.phone.contacts.data.Contact
import com.phone.contacts.data.ContactRepository
import com.phone.contacts.data.local.AppDatabase
import com.phone.contacts.data.local.EmergencyContactEntity
import com.phone.contacts.ui.components.ScreenSearchField
import com.phone.contacts.ui.theme.primaryAccentColor
import com.phone.contacts.util.AppConfigStore
import kotlinx.coroutines.launch

/** A multi-select picker over the device's real contacts — no preset entries, matching the
 * reference app exactly. Already-added contacts arrive pre-checked and pinned to their own
 * "Emergency" section at the top (removed from their normal alphabetical spot, not duplicated);
 * toggling a contact anywhere moves it in/out of that section live. Confirming via the checkmark
 * syncs the diff — newly checked ones are added, newly unchecked ones are removed. */
@Composable
fun SelectEmergencyContactScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adConfig by AppConfigStore.config.collectAsState()
    val dao = remember { AppDatabase.getInstance(context).emergencyContactDao() }

    var query by remember { mutableStateOf("") }
    var allContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    LaunchedEffect(Unit) {
        ContactRepository.fetchContacts(context).collect { allContacts = it }
    }

    var existingKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    // The list below must never render before this finishes — otherwise it briefly (or, if this
    // throws, permanently) shows every row unchecked regardless of what's actually already saved.
    var isSelectionLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val keys = try {
            dao.getAllSourceKeys().toSet()
        } catch (e: Exception) {
            emptySet()
        }
        existingKeys = keys
        selectedKeys = keys
        isSelectionLoaded = true
    }

    fun toggle(key: String) {
        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
    }

    val filteredContacts = remember(allContacts, query) {
        if (query.isBlank()) {
            allContacts
        } else {
            allContacts.filter { it.name.contains(query, ignoreCase = true) || it.number.contains(query) }
        }
    }
    // Selected contacts float to the pinned section and are excluded from the regular A-Z list
    // below — they appear once, not twice.
    val selectedContacts = remember(filteredContacts, selectedKeys) {
        filteredContacts.filter { it.id in selectedKeys }
    }
    val groupedRemaining = remember(filteredContacts, selectedKeys) {
        filteredContacts.filter { it.id !in selectedKeys }
            .sortedBy { it.name.lowercase() }
            .groupBy { it.name.firstOrNull()?.uppercaseChar()?.takeIf { c -> c.isLetter() } ?: '#' }
            .toSortedMap()
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
                text = stringResource(R.string.select_contact_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            IconButton(onClick = {
                scope.launch {
                    val toAdd = selectedKeys - existingKeys
                    val toRemove = existingKeys - selectedKeys
                    if (toRemove.isNotEmpty()) {
                        dao.deleteBySourceKeys(toRemove.toList())
                    }
                    if (toAdd.isNotEmpty()) {
                        val entities = toAdd.mapNotNull { key ->
                            allContacts.find { it.id == key }?.let { contact ->
                                EmergencyContactEntity(sourceKey = contact.id, name = contact.name, number = contact.number, photoUri = contact.photoUri)
                            }
                        }
                        dao.insertAll(entities)
                    }
                    onDone()
                }
            }) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        ScreenSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.search_contacts_dots_placeholder),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (!isSelectionLoaded) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = primaryAccentColor())
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (selectedContacts.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(text = stringResource(R.string.emergency_label), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    items(selectedContacts, key = { it.id }) { contact ->
                        ContactPickerRow(
                            contact = contact,
                            selected = true,
                            onClick = { toggle(contact.id) }
                        )
                    }
                }

                groupedRemaining.forEach { (letter, contactsInGroup) ->
                    item {
                        Box(
                            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp).size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = letter.toString(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    items(contactsInGroup, key = { it.id }) { contact ->
                        ContactPickerRow(
                            contact = contact,
                            selected = false,
                            onClick = { toggle(contact.id) }
                        )
                    }
                }
            }
        }

        // Shares banner_10 with Select Speed Dial Contact — only 10 banner slots exist.
        AdPlacements.adUnitId(adConfig?.result, AdType.NATIVE, slot = 18)?.let {
            NativeAdView(
                adUnitId = it,
                template = NativeAdTemplate.STRIP
            )
        }
    }
}

@Composable
private fun ContactPickerRow(contact: Contact, selected: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(name = contact.name, photoUri = contact.photoUri, size = 40.dp)
            Spacer(modifier = Modifier.size(14.dp))
            Text(
                text = contact.name,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Filled.AddIcCall,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 74.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        )
    }
}
