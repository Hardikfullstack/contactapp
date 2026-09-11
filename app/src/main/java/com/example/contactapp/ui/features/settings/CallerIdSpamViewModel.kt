package com.example.contactapp.ui.features.settings

import androidx.lifecycle.ViewModel
import com.example.contactapp.util.AnalyticsManager
import com.example.contactapp.util.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class CallerIdSpamUiState(
    val isEnabled: Boolean = true
)

@HiltViewModel
class CallerIdSpamViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CallerIdSpamUiState(isEnabled = preferenceManager.isCallerIdSpamProtectionEnabled())
    )
    val uiState: StateFlow<CallerIdSpamUiState> = _uiState.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        preferenceManager.setCallerIdSpamProtectionEnabled(enabled)
        _uiState.value = _uiState.value.copy(isEnabled = enabled)
        AnalyticsManager.logEventWithAction("caller_id_spam_protection_toggled", "CallerIdSpamScreen", if (enabled) "on" else "off")
    }
}
