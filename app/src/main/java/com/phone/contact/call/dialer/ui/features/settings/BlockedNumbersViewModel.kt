package com.phone.contact.call.dialer.ui.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phone.contact.call.dialer.domain.model.Contact
import com.phone.contact.call.dialer.domain.repository.ContactRepository
import com.phone.contact.call.dialer.domain.repository.CallLogRepository
import com.phone.contact.call.dialer.util.AnalyticsManager
import com.phone.contact.call.dialer.util.LocalBlockManager
import com.phone.contact.call.dialer.util.PhoneNumberMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BlockedNumbersUiState(
    val blockedContacts: List<Contact> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class BlockedNumbersViewModel @Inject constructor(
    private val blockManager: LocalBlockManager,
    private val contactRepository: ContactRepository,
    private val callLogRepository: CallLogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlockedNumbersUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            blockManager.getBlockedNumbers().collect { numbers ->
                // A single blocked number is stored under two keys internally (raw string +
                // normalized digits, see LocalBlockManager) — both come back here as separate
                // entries. Once resolved to the same saved contact they'd produce duplicate rows
                // (and duplicate LazyColumn keys, which crashes the list entirely), so dedupe by
                // the underlying phone number before mapping to contacts.
                val distinctNumbers = numbers.distinctBy { PhoneNumberMatcher.normalize(it) }
                val enrichedList = distinctNumbers.map { number ->
                    contactRepository.findContactByNumber(number) ?: Contact(
                        id = number, // Use number as ID for temporary items
                        name = number,
                        number = number,
                        isBlocked = true
                    )
                }
                _uiState.value = BlockedNumbersUiState(blockedContacts = enrichedList, isLoading = false)
            }
        }
    }

    fun unblockNumber(number: String) {
        viewModelScope.launch {
            // Optimistic update: filter out the number instantly
            val currentList = _uiState.value.blockedContacts
            _uiState.value = _uiState.value.copy(
                blockedContacts = currentList.filter { it.number != number }
            )
            
            callLogRepository.blockNumber(number, false)
            AnalyticsManager.logEventWithAction("number_blocked", "BlockedNumbers", "unblock")
        }
    }
}
