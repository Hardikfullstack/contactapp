package com.example.contactapp.ui.features.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.contactapp.domain.model.Contact
import com.example.contactapp.domain.repository.CallLogRepository
import com.example.contactapp.domain.repository.ContactRepository
import com.example.contactapp.util.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesUiState(
    val favorites: List<Contact> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: ContactRepository,
    private val callLogRepository: CallLogRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        fetchFavorites()
    }

    private fun fetchFavorites() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            combine(
                repository.fetchFavorites(),
                callLogRepository.getBlockedNumbers(),
                preferenceManager.preferencesFlow
            ) { contacts, blockedNumbers, _ ->
                val normalizedBlocked = blockedNumbers.map { it.replace(Regex("[^0-9]"), "").takeLast(10) }
                
                val processed = contacts.map { contact ->
                    val cleanNum = contact.number.replace(Regex("[^0-9]"), "").takeLast(10)
                    contact.copy(isBlocked = cleanNum.isNotEmpty() && normalizedBlocked.contains(cleanNum))
                }
                val sortOrder = preferenceManager.getContactSortOrder()
                if (sortOrder == "Last Name") {
                    processed.sortedBy { it.name.split(" ").last().lowercase() }
                } else {
                    processed.sortedBy { it.name.lowercase() }
                }
            }.collect { sorted ->
                _uiState.value = _uiState.value.copy(
                    favorites = sorted,
                    isLoading = false
                )
            }
        }
    }
}
