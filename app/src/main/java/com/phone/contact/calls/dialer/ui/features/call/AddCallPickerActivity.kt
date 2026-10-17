package com.phone.contact.calls.dialer.ui.features.call

import android.content.Intent
import android.os.Bundle
import android.telephony.PhoneNumberUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phone.contact.calls.dialer.R
import com.phone.contact.calls.dialer.data.Contact
import com.phone.contact.calls.dialer.data.ContactRepository
import com.phone.contact.calls.dialer.service.CallManager
import com.phone.contact.calls.dialer.ui.components.ScreenSearchField
import com.phone.contact.calls.dialer.ui.components.highlightMatches
import com.phone.contact.calls.dialer.ui.screens.ContactAvatar
import com.phone.contact.calls.dialer.ui.theme.ContactsTheme
import com.phone.contact.calls.dialer.ui.theme.primaryAccentColor

/** Plain contact picker with no Telecom involvement at all — mirrors contactapp's own
 * AddCallPickerActivity exactly: it just returns a phone number via [setResult], the actual
 * second call is placed back in [CallActivity] via [com.phone.contact.calls.dialer.service.CallManager.addCall]. */
class AddCallPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PICKED_NUMBER = "picked_number"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ContactsTheme {
                AddCallPickerScreen(
                    onPick = { number ->
                        setResult(RESULT_OK, Intent().putExtra(EXTRA_PICKED_NUMBER, number))
                        finish()
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
private fun AddCallPickerScreen(onPick: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var allContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }

    LaunchedEffect(Unit) {
        ContactRepository.fetchContacts(context).collect { allContacts = it }
    }

    // Live - recomposes the moment a conference participant hangs up (or a new one joins), so
    // they drop out of / reappear in this list immediately rather than needing the picker reopened.
    val activeCallNumbers by CallManager.activeCallNumbers.collectAsState()

    val filtered = remember(allContacts, query, activeCallNumbers) {
        val notOnCall = allContacts.filter { contact ->
            activeCallNumbers.none { PhoneNumberUtils.compare(it, contact.number) }
        }
        if (query.isBlank()) {
            notOnCall
        } else {
            notOnCall.filter { it.name.contains(query, ignoreCase = true) || it.number.contains(query) }
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
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = stringResource(R.string.add_call_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        ScreenSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.search_contacts_placeholder),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.size(8.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.empty_no_contacts_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.id }) { contact ->
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(contact.number) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ContactAvatar(contact = contact, size = 40.dp)
                        Spacer(modifier = Modifier.size(14.dp))
                        Text(
                            text = highlightMatches(contact.name, query, primaryAccentColor()),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 74.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                }
            }
        }
    }
}
