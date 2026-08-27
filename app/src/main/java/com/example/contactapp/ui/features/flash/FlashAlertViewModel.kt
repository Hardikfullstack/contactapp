package com.example.contactapp.ui.features.flash

import androidx.lifecycle.ViewModel
import com.example.contactapp.util.AnalyticsManager
import com.example.contactapp.util.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class FlashAlertUiState(
    val isEnabled: Boolean = false,
    val blinkSpeed: Long = 400L
)

@HiltViewModel
class FlashAlertViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FlashAlertUiState(
            isEnabled = preferenceManager.isFlashAlertEnabled(),
            blinkSpeed = preferenceManager.getFlashBlinkSpeed()
        )
    )
    val uiState: StateFlow<FlashAlertUiState> = _uiState.asStateFlow()

    fun toggleEnabled(enabled: Boolean) {
        preferenceManager.setFlashAlertEnabled(enabled)
        _uiState.value = _uiState.value.copy(isEnabled = enabled)
        AnalyticsManager.logEventWithAction("flash_alert_toggled", "FlashAlertScreen", if (enabled) "on" else "off")
    }

    fun setBlinkSpeed(speed: Long) {
        preferenceManager.setFlashBlinkSpeed(speed)
        _uiState.value = _uiState.value.copy(blinkSpeed = speed)
    }
}
