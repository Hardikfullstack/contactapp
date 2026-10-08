package com.phone.contacts.ui.screens

import android.widget.Toast
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.phone.contacts.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phone.contacts.ads.AdPlacements
import com.phone.contacts.ads.AdType
import com.phone.contacts.ads.NativeAdView
import com.phone.contacts.ads.NativeAdTemplate
import com.phone.contacts.data.Contact
import com.phone.contacts.data.ContactRepository
import com.phone.contacts.ui.components.ScreenSearchField
import com.phone.contacts.util.AppConfigStore
import com.phone.contacts.util.SpeedDialEntry
import com.phone.contacts.util.SpeedDialPreferences

/** A single-select contact picker for one speed-dial key (0-9, *, #) — tapping a contact assigns
 * it immediately and returns, since there's only ever one choice to make here (unlike the
 * multi-select Emergency contacts picker). */
@Composable
fun SelectSpeedDialContactScreen(dialKey: String, onBack: () -> Unit, onAssigned: () -> Unit) {
    val context = LocalContext.current
    val adConfig by AppConfigStore.config.collectAsState()

    var query by remember { mutableStateOf("") }
    var allContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    LaunchedEffect(Unit) {
        ContactRepository.fetchContacts(context).collect { allContacts = it }
    }

    val groupedContacts = remember(allContacts, query) {
        val filtered = if (query.isBlank()) {
            allContacts
        } else {
            allContacts.filter { it.name.contains(query, ignoreCase = true) || it.number.contains(query) }
        }
        filtered.sortedBy { it.name.lowercase() }
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
                text = stringResource(R.string.assign_to_key_title, dialKey),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
        }

        ScreenSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.search_contacts_dots_placeholder),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            groupedContacts.forEach { (letter, contactsInGroup) ->
                item {
                    Box(
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp).size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = letter.toString(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    }
                }
                items(contactsInGroup, key = { it.id }) { contact ->
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    SpeedDialPreferences.setKey(
                                        context,
                                        dialKey,
                                        SpeedDialEntry(contact.id, contact.name, contact.number, contact.photoUri)
                                    )
                                    Toast.makeText(
                                        context,
                                        "Long-press $dialKey on the Keypad to call ${contact.name}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onAssigned()
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ContactAvatar(name = contact.name, photoUri = contact.photoUri, size = 40.dp)
                            Spacer(modifier = Modifier.size(14.dp))
                            Text(text = contact.name, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 74.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                        )
                    }
                }
            }
        }

        AdPlacements.adUnitId(adConfig?.result, AdType.NATIVE, slot = 10)?.let {
            NativeAdView(
                adUnitId = it,
                template = NativeAdTemplate.STRIP
            )
        }
    }
}
