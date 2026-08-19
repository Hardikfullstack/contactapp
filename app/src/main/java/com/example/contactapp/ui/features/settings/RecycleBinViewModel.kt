package com.example.contactapp.ui.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.contactapp.data.local.entity.DeletedContactEntity
import com.example.contactapp.domain.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecycleBinUiState(
    val deletedContacts: List<DeletedContactEntity> = emptyList(),
    val isLoading: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false
)

@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val repository: ContactRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecycleBinUiState())
    val uiState = _uiState.asStateFlow()

    init {
        fetchDeletedContacts()
    }

    private fun fetchDeletedContacts() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            repository.fetchDeletedContacts().collect { list ->
                _uiState.value = _uiState.value.copy(
                    deletedContacts = list,
                    isLoading = false
                )
            }
        }
    }

    fun toggleSelection(id: String) {
        val current = _uiState.value.selectedIds.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _uiState.value = _uiState.value.copy(
            selectedIds = current,
            isSelectionMode = current.isNotEmpty()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedIds = emptySet(),
            isSelectionMode = false
        )
    }

    fun restoreSelected() {
        viewModelScope.launch {
            val ids = _uiState.value.selectedIds.toList()
            repository.restoreContacts(ids)
            clearSelection()
        }
    }

    fun deletePermanentlySelected() {
        viewModelScope.launch {
            val ids = _uiState.value.selectedIds.toList()
            repository.permanentlyDeleteContacts(ids)
            clearSelection()
        }
    }
}
