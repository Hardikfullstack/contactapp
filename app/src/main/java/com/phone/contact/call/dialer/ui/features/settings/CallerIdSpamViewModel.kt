package com.phone.contact.call.dialer.ui.features.settings

import androidx.lifecycle.ViewModel
import com.phone.contact.call.dialer.util.AnalyticsManager
import com.phone.contact.call.dialer.util.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class CallerIdSpamUiState(
    val isEnabled: Boolean = true,
    /** Numbers the user reported as spam (normalized, as stored). */
    val spamNumbers: List<String> = emptyList()
)

@HiltViewModel
class CallerIdSpamViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CallerIdSpamUiState(
            isEnabled = preferenceManager.isCallerIdSpamProtectionEnabled(),
            spamNumbers = preferenceManager.getSpamNumbers().sorted()
        )
    )
    val uiState: StateFlow<CallerIdSpamUiState> = _uiState.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        preferenceManager.setCallerIdSpamProtectionEnabled(enabled)
        _uiState.value = _uiState.value.copy(isEnabled = enabled)
        AnalyticsManager.logEventWithAction("caller_id_spam_protection_toggled", "CallerIdSpamScreen", if (enabled) "on" else "off")
    }

    /** Removes a number from the spam list. It is flagged again only if auto-detection finds it. */
    fun removeSpamNumber(number: String) {
        preferenceManager.setSpamNumbers(preferenceManager.getSpamNumbers() - number)
        _uiState.value = _uiState.value.copy(spamNumbers = preferenceManager.getSpamNumbers().sorted())
    }
}
