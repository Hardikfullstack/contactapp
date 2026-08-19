package com.example.contactapp.ui.features.keypad

import com.example.contactapp.domain.model.Contact

data class KeypadUiState(
    val typedNumber: String = "",
    val showAddContactSheet: Boolean = false,
    val duplicateContact: Contact? = null,
    val pendingName: String? = null,
    val searchResults: List<Contact> = emptyList(),
    val isKeypadSearchEnabled: Boolean = false
) {
    val displayNumber: String
        get() = when {
            typedNumber.isEmpty() -> ""
            typedNumber.length in 6..10 -> {
                typedNumber.substring(0, 5) + " " + typedNumber.substring(5)
            }
            else -> typedNumber
        }
}
