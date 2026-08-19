package com.example.contactapp.ui.features.autoreply

import androidx.lifecycle.ViewModel
import com.example.contactapp.util.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class AutoReplyUiState(
    val isEnabled: Boolean = false,
    val message: String = ""
)

@HiltViewModel
class AutoReplyViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AutoReplyUiState(
            isEnabled = preferenceManager.isAutoReplyEnabled(),
            message = preferenceManager.getAutoReplyMessage()
        )
    )
    val uiState: StateFlow<AutoReplyUiState> = _uiState.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        preferenceManager.setAutoReplyEnabled(enabled)
        _uiState.value = _uiState.value.copy(isEnabled = enabled)
    }

    fun setMessage(message: String) {
        preferenceManager.setAutoReplyMessage(message)
        _uiState.value = _uiState.value.copy(message = message)
    }
}
