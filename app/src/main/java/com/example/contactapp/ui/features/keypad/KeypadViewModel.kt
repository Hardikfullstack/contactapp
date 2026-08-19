package com.example.contactapp.ui.features.keypad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.contactapp.domain.model.Contact
import com.example.contactapp.domain.repository.ContactRepository
import com.example.contactapp.util.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeypadViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(KeypadUiState())
    val uiState: StateFlow<KeypadUiState> = _uiState.asStateFlow()

    private var allContacts: List<Contact> = emptyList()

    private val t9Mapping = mapOf(
        '2' to listOf('a', 'b', 'c'),
        '3' to listOf('d', 'e', 'f'),
        '4' to listOf('g', 'h', 'i'),
        '5' to listOf('j', 'k', 'l'),
        '6' to listOf('m', 'n', 'o'),
        '7' to listOf('p', 'q', 'r', 's'),
        '8' to listOf('t', 'u', 'v'),
        '9' to listOf('w', 'x', 'y', 'z')
    )

    init {
        refreshSettings()
        loadContacts()
    }

    fun refreshSettings() {
        _uiState.value = _uiState.value.copy(
            isKeypadSearchEnabled = true
        )
    }

    private fun loadContacts() {
        viewModelScope.launch {
            contactRepository.fetchContacts().collect {
                allContacts = it
                updateSearchResults()
            }
        }
    }

    fun onDigitPressed(digit: String) {
        _uiState.value = _uiState.value.copy(
            typedNumber = _uiState.value.typedNumber + digit
        )
        updateSearchResults()
    }

    fun onBackspace() {
        if (_uiState.value.typedNumber.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                typedNumber = _uiState.value.typedNumber.dropLast(1)
            )
            updateSearchResults()
        }
    }

    fun clearAll() {
        _uiState.value = _uiState.value.copy(
            typedNumber = ""
        )
        updateSearchResults()
    }

    private fun updateSearchResults() {
        val number = _uiState.value.typedNumber
        if (number.isEmpty()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
            return
        }

        val results = allContacts.filter { contact ->
            val cleanContactNum = contact.number.replace(Regex("[^0-9]"), "")
            val matchesByNumber = cleanContactNum.contains(number)
            val matchesByName = matchT9(contact.name.lowercase(), number)
            matchesByNumber || matchesByName
        }.take(5)

        _uiState.value = _uiState.value.copy(searchResults = results)
    }

    private fun matchT9(name: String, digits: String): Boolean {
        if (digits.isEmpty()) return false
        val words = name.split(" ", ".", "-", "_")
        return words.any { word ->
            if (word.length < digits.length) return@any false
            for (i in digits.indices) {
                val digit = digits[i]
                val char = word[i]
                val possibleLetters = t9Mapping[digit] ?: return@any false
                if (char !in possibleLetters) return@any false
            }
            true
        }
    }

    fun showAddContactSheet(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAddContactSheet = show)
    }

    fun validateAndSaveContact(name: String) {
        val number = _uiState.value.typedNumber
        viewModelScope.launch {
            val existing = contactRepository.findContactByNumber(number)
            if (existing != null && existing.name.lowercase() != name.lowercase()) {
                _uiState.value = _uiState.value.copy(
                    duplicateContact = existing,
                    pendingName = name
                )
            } else {
                saveContact(name)
            }
        }
    }

    fun confirmSaveDuplicate() {
        val name = _uiState.value.pendingName ?: return
        saveContact(name)
        clearDuplicateState()
    }

    fun clearDuplicateState() {
        _uiState.value = _uiState.value.copy(
            duplicateContact = null,
            pendingName = null
        )
    }

    fun saveContact(name: String) {
        val number = _uiState.value.typedNumber
        viewModelScope.launch {
            contactRepository.saveContact(name, number)
            showAddContactSheet(false)
            clearAll()
        }
    }
}
