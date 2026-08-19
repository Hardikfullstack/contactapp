package com.example.contactapp.ui.features.recents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.contactapp.domain.model.Contact
import com.example.contactapp.domain.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<Contact> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ContactRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var allContacts: List<Contact> = emptyList()

    init {
        loadAllContacts()
    }

    private fun loadAllContacts() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            repository.fetchContacts().collect { contacts ->
                allContacts = contacts
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        filterContacts(newQuery)
    }

    private fun filterContacts(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                results = emptyList(),
                hasSearched = false
            )
            return
        }

        val filtered = allContacts.filter { item ->
            val nameMatch = item.name.contains(query, ignoreCase = true)
            val numberMatch = item.number.contains(query)
            nameMatch || numberMatch
        }.sortedBy { it.name.lowercase() }

        _uiState.value = _uiState.value.copy(
            results = filtered,
            hasSearched = true
        )
    }
}
