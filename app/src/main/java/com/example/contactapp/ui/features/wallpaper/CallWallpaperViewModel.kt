package com.example.contactapp.ui.features.wallpaper

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.contactapp.util.BuiltInWallpaper
import com.example.contactapp.util.BuiltInWallpapers
import com.example.contactapp.util.PreferenceManager
import com.example.contactapp.util.WallpaperSelection
import com.example.contactapp.util.computeIsDarkForImage
import com.example.contactapp.util.isColorDark
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CallWallpaperUiState(
    val selection: WallpaperSelection = WallpaperSelection.None
)

@HiltViewModel
class CallWallpaperViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val colorPresets: List<Color> = listOf(
        Color(0xFF109D6F), // Original Green
        Color(0xFF2196F3), // Blue
        Color(0xFF9C27B0), // Purple
        Color(0xFFFF9800), // Orange
        Color(0xFFE91E63), // Pink
        Color(0xFF607D8B), // Slate
        Color(0xFF3F51B5), // Indigo
        Color(0xFFC62828), // Deep Red
        Color(0xFF009688), // Teal
        Color(0xFFFFC107), // Amber
        Color(0xFF4527A0), // Deep Purple
        Color(0xFF212121)  // Dark Gray
    )

    val builtInWallpapers: List<BuiltInWallpaper> = BuiltInWallpapers.all

    private val _uiState = MutableStateFlow(
        CallWallpaperUiState(selection = preferenceManager.getCallWallpaperSelection())
    )
    val uiState: StateFlow<CallWallpaperUiState> = _uiState.asStateFlow()

    fun selectNone() = persist(WallpaperSelection.None)

    fun selectColor(argb: Int) = persist(WallpaperSelection.SolidColor(argb, isColorDark(argb)))

    fun selectBuiltIn(id: String) {
        val wallpaper = BuiltInWallpapers.findById(id) ?: return
        persist(WallpaperSelection.BuiltIn(id, wallpaper.isDark))
    }

    fun selectDeviceImage(uri: Uri) {
        viewModelScope.launch {
            val isDark = computeIsDarkForImage(appContext, uri)
            persist(WallpaperSelection.Device(uri.toString(), isDark))
        }
    }

    private fun persist(selection: WallpaperSelection) {
        preferenceManager.setCallWallpaperSelection(selection)
        _uiState.value = _uiState.value.copy(selection = selection)
    }
}
