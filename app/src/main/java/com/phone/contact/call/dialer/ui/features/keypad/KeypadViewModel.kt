package com.phone.contact.call.dialer.ui.features.keypad

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phone.contact.call.dialer.domain.model.Contact
import com.phone.contact.call.dialer.domain.repository.ContactRepository
import com.phone.contact.call.dialer.util.PreferenceManager
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

    // Lazily created (most sessions use the keypad at least once if it's open at all) and kept
    // for the ViewModel's lifetime rather than one-shot per tap - constructing a ToneGenerator
    // repeatedly is comparatively expensive and can introduce an audible delay before the tone
    // starts. STREAM_DTMF matches what the in-call dialpad/telecom stack uses for the same tones.
    private val toneGenerator: ToneGenerator by lazy { ToneGenerator(AudioManager.STREAM_DTMF, 75) }

    private fun playKeyTone(digit: String) {
        val tone = when (digit) {
            "0" -> ToneGenerator.TONE_DTMF_0
            "1" -> ToneGenerator.TONE_DTMF_1
            "2" -> ToneGenerator.TONE_DTMF_2
            "3" -> ToneGenerator.TONE_DTMF_3
            "4" -> ToneGenerator.TONE_DTMF_4
            "5" -> ToneGenerator.TONE_DTMF_5
            "6" -> ToneGenerator.TONE_DTMF_6
            "7" -> ToneGenerator.TONE_DTMF_7
            "8" -> ToneGenerator.TONE_DTMF_8
            "9" -> ToneGenerator.TONE_DTMF_9
            "*" -> ToneGenerator.TONE_DTMF_S
            "#" -> ToneGenerator.TONE_DTMF_P
            // "+" (long-press on 0) isn't a real DTMF digit - no standard tone for it.
            else -> return
        }
        try {
            toneGenerator.startTone(tone, 150)
        } catch (e: Exception) {
            // Some OEMs throw if the DTMF audio stream is unavailable (e.g. during another call) -
            // the typed digit itself already landed, so a missing tone isn't worth crashing over.
        }
    }

    override fun onCleared() {
        super.onCleared()
        toneGenerator.release()
    }

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
        playKeyTone(digit)
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

    fun validateAndSaveContact(name: String, number: String, isFavorite: Boolean = false) {
        viewModelScope.launch {
            val existing = contactRepository.findContactByNumber(number)
            if (existing != null && existing.name.lowercase() != name.lowercase()) {
                _uiState.value = _uiState.value.copy(
                    duplicateContact = existing,
                    pendingName = name,
                    pendingNumber = number,
                    pendingIsFavorite = isFavorite
                )
            } else {
                saveContact(name, number, isFavorite)
            }
        }
    }

    fun confirmSaveDuplicate() {
        val name = _uiState.value.pendingName ?: return
        val number = _uiState.value.pendingNumber ?: _uiState.value.typedNumber
        saveContact(name, number, _uiState.value.pendingIsFavorite)
        clearDuplicateState()
    }

    fun clearDuplicateState() {
        _uiState.value = _uiState.value.copy(
            duplicateContact = null,
            pendingName = null,
            pendingNumber = null,
            pendingIsFavorite = false
        )
    }

    fun saveContact(name: String, number: String, isFavorite: Boolean = false) {
        viewModelScope.launch {
            contactRepository.saveContact(name, number)
            if (isFavorite) contactRepository.toggleFavorite(number, true)
            showAddContactSheet(false)
            clearAll()
        }
    }
}
