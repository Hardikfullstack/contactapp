package com.phone.contacts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.phone.contacts.util.BuiltInWallpapers
import com.phone.contacts.util.WallpaperSelection

/** Renders the selected call wallpaper (color or image) plus its readability scrim. Renders
 * nothing for [WallpaperSelection.None] — callers keep owning their own fallback background.
 * [blurEnabled] only affects image sources (built-in/device) — a flat color has nothing to blur. */
@Composable
fun CallWallpaperBackground(selection: WallpaperSelection, blurEnabled: Boolean = false, modifier: Modifier = Modifier) {
    if (selection is WallpaperSelection.None) return

    val imageModifier = Modifier.fillMaxSize().then(if (blurEnabled) Modifier.blur(24.dp) else Modifier)

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
                        modifier = imageModifier,
                        contentScale = ContentScale.Crop
                    )
                }
            }
            is WallpaperSelection.Device -> {
                AsyncImage(
                    model = selection.uri,
                    contentDescription = null,
                    modifier = imageModifier,
                    contentScale = ContentScale.Crop
                )
            }
            is WallpaperSelection.None -> Unit
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
    }
}
