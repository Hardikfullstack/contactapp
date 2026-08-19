package com.example.contactapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.example.contactapp.util.BuiltInWallpapers
import com.example.contactapp.util.WallpaperSelection

/**
 * Renders the selected call wallpaper (color or image) plus its readability scrim.
 * Renders nothing for [WallpaperSelection.None] — callers keep owning their own fallback
 * background, since InCallScreen and FakeCallActivity use different fallback colors.
 */
@Composable
fun CallWallpaperBackground(selection: WallpaperSelection, modifier: Modifier = Modifier) {
    if (selection is WallpaperSelection.None) return

    Box(modifier = modifier) {
        when (selection) {
            is WallpaperSelection.SolidColor -> {
                Box(modifier = Modifier.fillMaxSize().background(Color(selection.colorArgb)))
            }
            is WallpaperSelection.BuiltIn -> {
                val resId = BuiltInWallpapers.findById(selection.id)?.resId
                if (resId != null) {
                    AsyncImage(
                        model = resId,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            is WallpaperSelection.Device -> {
                AsyncImage(
                    model = selection.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            is WallpaperSelection.None -> Unit
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
    }
}
