package com.example.contactapp.ui.features.callthemes

import androidx.lifecycle.ViewModel
import com.example.contactapp.util.CallAccentColor
import com.example.contactapp.util.CallAccentColors
import com.example.contactapp.util.CallButtonShape
import com.example.contactapp.util.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class CallThemeUiState(
    val selectedColorId: String = "green",
    val selectedShape: CallButtonShape = CallButtonShape.CIRCLE
)

@HiltViewModel
class CallThemeViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    val colors: List<CallAccentColor> = CallAccentColors.all
    val shapes: List<CallButtonShape> = CallButtonShape.entries

    private val _uiState = MutableStateFlow(
        CallThemeUiState(
            selectedColorId = preferenceManager.getCallAccentColorId(),
            selectedShape = CallButtonShape.valueOf(preferenceManager.getCallButtonShapeName())
        )
    )
    val uiState: StateFlow<CallThemeUiState> = _uiState.asStateFlow()

    fun selectColor(id: String) {
        preferenceManager.setCallAccentColorId(id)
        _uiState.value = _uiState.value.copy(selectedColorId = id)
    }

    fun selectShape(shape: CallButtonShape) {
        preferenceManager.setCallButtonShape(shape)
        _uiState.value = _uiState.value.copy(selectedShape = shape)
    }
}
