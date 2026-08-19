package com.example.contactapp.ui.features.announcer

import androidx.lifecycle.ViewModel
import com.example.contactapp.util.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class CallAnnouncerUiState(
    val isEnabled: Boolean = false,
    val repeatCount: Int = 1
)

@HiltViewModel
class CallAnnouncerViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CallAnnouncerUiState(
            isEnabled = preferenceManager.isCallAnnouncerEnabled(),
            repeatCount = preferenceManager.getAnnouncerRepeatCount()
        )
    )
    val uiState: StateFlow<CallAnnouncerUiState> = _uiState.asStateFlow()

    fun toggleEnabled(enabled: Boolean) {
        preferenceManager.setCallAnnouncerEnabled(enabled)
        _uiState.value = _uiState.value.copy(isEnabled = enabled)
    }

    fun setRepeatCount(count: Int) {
        preferenceManager.setAnnouncerRepeatCount(count)
        _uiState.value = _uiState.value.copy(repeatCount = count)
    }
}
