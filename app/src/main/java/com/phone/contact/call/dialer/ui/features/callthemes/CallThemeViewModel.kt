package com.phone.contact.call.dialer.ui.features.callthemes

import androidx.lifecycle.ViewModel
import com.phone.contact.call.dialer.util.CallAccentColor
import com.phone.contact.call.dialer.util.CallAccentColors
import com.phone.contact.call.dialer.util.CallButtonShape
import com.phone.contact.call.dialer.util.PreferenceManager
import com.phone.contact.call.dialer.util.WallpaperSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
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

    // So the live preview at the top of this screen shows the same background the real call
    // screen will use, instead of a plain hardcoded color.
    val wallpaperSelectionFlow: Flow<WallpaperSelection> = preferenceManager.wallpaperSelectionFlow
    fun getCurrentWallpaperSelection(): WallpaperSelection = preferenceManager.getCallWallpaperSelection()

    private val _uiState = MutableStateFlow(
        CallThemeUiState(
            selectedColorId = preferenceManager.getCallAccentColorId(),
            selectedShape = CallButtonShape.safeValueOf(preferenceManager.getCallButtonShapeName())
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
