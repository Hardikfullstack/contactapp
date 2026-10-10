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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.phone.contacts.data.Contact
import com.phone.contacts.data.ContactRepository
import com.phone.contacts.ui.components.ScreenSearchField
import kotlinx.coroutines.launch

/** Same style as this app's own picker screens (e.g. Emergency contacts) - a plain contact list
 * with a pinned "Favorites" section at the top for staged-as-starred contacts, and the rest
 * grouped alphabetically below. Star taps only stage the change locally - nothing is written to
 * the actual contact (ContactsContract.Contacts.STARRED) until Done is pressed, which diffs the
 * staged set against what was actually starred on open and applies just the changes. Backing out
 * without pressing Done discards every tap made on this screen. */
@Composable
fun SelectFavoriteContactScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var allContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    LaunchedEffect(Unit) {
        ContactRepository.fetchContacts(context).collect { allContacts = it }
    }

    // Captured once from the first non-empty load - not re-synced on every emission, otherwise a
    // live ContentObserver update (e.g. from something else changing a contact) would silently
    // move the "what counts as changed" baseline out from under an in-progress selection.
    var existingStarredIds by remember { mutableStateOf<Set<String>?>(null) }
    var selectedStarredIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(allContacts) {
        if (existingStarredIds == null && allContacts.isNotEmpty()) {
            val starred = allContacts.filter { it.isStarred }.map { it.id }.toSet()
            existingStarredIds = starred
            selectedStarredIds = starred
        }
    }

    fun toggle(id: String) {
        selectedStarredIds = if (id in selectedStarredIds) selectedStarredIds - id else selectedStarredIds + id
    }

    fun applyAndBack() {
        val existing = existingStarredIds ?: emptySet()
        val toStar = selectedStarredIds - existing
        val toUnstar = existing - selectedStarredIds
        if (toStar.isEmpty() && toUnstar.isEmpty()) {
            onBack()
            return
        }
        scope.launch {
            toStar.forEach { id -> ContactRepository.setStarred(context, id, true) }
            toUnstar.forEach { id -> ContactRepository.setStarred(context, id, false) }
            onBack()
        }
    }

    val filteredContacts = remember(allContacts, query) {
        if (query.isBlank()) {
            allContacts
        } else {
            allContacts.filter { it.name.contains(query, ignoreCase = true) || it.number.contains(query) }
        }
    }
    val favoriteContacts = remember(filteredContacts, selectedStarredIds) {
        filteredContacts.filter { it.id in selectedStarredIds }
    }
    val groupedRemaining = remember(filteredContacts, selectedStarredIds) {
        filteredContacts.filter { it.id !in selectedStarredIds }
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
            // Plain back - any taps made on this screen are discarded, nothing is written.
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
            IconButton(onClick = { applyAndBack() }) {
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

        if (filteredContacts.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.no_data_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (favoriteContacts.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(text = stringResource(R.string.favorites), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    items(favoriteContacts, key = { it.id }) { contact ->
                        FavoriteContactPickerRow(contact = contact, selected = true, onClick = { toggle(contact.id) })
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
                        FavoriteContactPickerRow(contact = contact, selected = false, onClick = { toggle(contact.id) })
                    }
                }
            }
        }
    }
}

// Standard "filled favorite star" yellow - same shade this app's Settings > Rate us row already
// uses for its star icon badge, kept consistent rather than introducing a new yellow.
private val FavoriteStarYellow = Color(0xFFFBC02D)

@Composable
private fun FavoriteContactPickerRow(contact: Contact, selected: Boolean, onClick: () -> Unit) {
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
                imageVector = if (selected) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = if (selected) FavoriteStarYellow else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 74.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        )
    }
}
