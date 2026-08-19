package com.example.contactapp.ui.features.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.contactapp.domain.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ToolsUiState(
    val isLoading: Boolean = false
)

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val repository: ContactRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState = _uiState.asStateFlow()

    // Add tool-specific logic here as needed
}
