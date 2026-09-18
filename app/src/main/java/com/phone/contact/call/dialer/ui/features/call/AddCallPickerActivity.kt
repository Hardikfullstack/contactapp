package com.phone.contact.call.dialer.ui.features.call

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.phone.contact.call.dialer.ui.features.contacts.ContactsScreen
import com.phone.contact.call.dialer.ui.features.recents.SearchScreen
import com.phone.contact.call.dialer.ui.theme.ContactAppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The "Add Call" number picker — reuses the real Contacts screen (same alphabetical list, same
 * search) instead of a bare numeric dial pad or the call log, matching the reference dialer's own
 * Add Call flow: you pick from your contacts, not your recent calls. Tapping a contact (or its
 * call icon, or a search result) doesn't place it directly; it's handed back to InCallActivity via
 * the activity result below, which holds the current call and dials the picked number as the
 * second, simultaneous call.
 */
@AndroidEntryPoint
class AddCallPickerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PICKED_NUMBER = "picked_number"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ContactAppTheme {
                var showSearch by remember { mutableStateOf(false) }
                val onPicked: (String) -> Unit = { number ->
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_PICKED_NUMBER, number))
                    finish()
                }

                if (showSearch) {
                    SearchScreen(
                        onBack = { showSearch = false },
                        onContactClick = { _, _ -> },
                        onNumberPicked = onPicked
                    )
                } else {
                    ContactsScreen(
                        onContactClick = { _, _ -> },
                        onSearchClick = { showSearch = true },
                        onNumberPicked = onPicked
                    )
                }
            }
        }
    }
}
