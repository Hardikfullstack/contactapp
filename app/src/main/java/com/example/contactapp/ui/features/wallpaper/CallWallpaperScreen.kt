package com.example.contactapp.ui.features.wallpaper

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.example.contactapp.R
import com.example.contactapp.ui.components.CommonHeader
import com.example.contactapp.ui.components.ColorPickerDialog
import com.example.contactapp.ui.theme.PrimaryGreen
import com.example.contactapp.util.WallpaperSelection

@Composable
fun CallWallpaperScreen(
    onBack: () -> Unit,
    viewModel: CallWallpaperViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selection = uiState.selection
    val context = LocalContext.current
    var showColorPicker by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}
            viewModel.selectDeviceImage(it)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .statusBarsPadding()
        ) {
            CommonHeader(
                title = stringResource(R.string.call_wallpaper),
                onBackClick = onBack
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Cards Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Gallery Picker
                ActionCard(
                    label = stringResource(R.string.custom_image),
                    icon = Icons.Default.Image,
                    onClick = { launcher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                )

                // Color Picker
                ActionCard(
                    label = stringResource(R.string.pick_color),
                    icon = Icons.Default.ColorLens,
                    onClick = { showColorPicker = true },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.select_wallpaper),
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. "None" option
                item {
                    WallpaperCard(
                        isSelected = selection is WallpaperSelection.None,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { viewModel.selectNone() }
                    )
                }

                // 2. Currently selected device photo (if any)
                if (selection is WallpaperSelection.Device) {
                    item {
                        WallpaperCard(
                            isSelected = true,
                            color = Color.Transparent,
                            imageModel = selection.uri,
                            onClick = { /* Already selected */ }
                        )
                    }
                }

                // 2b. Currently selected custom color from the color picker, if it isn't one of
                // the fixed presets below — without this, picking a custom color leaves nothing
                // in the grid showing as selected (it's still correctly saved/applied, just
                // invisible here), which looks exactly like "reset to default" and gives no way
                // to tell which color is actually active.
                val customColorArgb = (selection as? WallpaperSelection.SolidColor)?.colorArgb
                val matchesPreset = customColorArgb != null && viewModel.colorPresets.any { it.toArgb() == customColorArgb }
                if (customColorArgb != null && !matchesPreset) {
                    item {
                        WallpaperCard(
                            isSelected = true,
                            color = Color(customColorArgb),
                            onClick = { showColorPicker = true }
                        )
                    }
                }

                // 3. Color presets
                items(viewModel.colorPresets) { preset ->
                    val presetArgb = preset.toArgb()
                    WallpaperCard(
                        isSelected = selection is WallpaperSelection.SolidColor && selection.colorArgb == presetArgb,
                        color = preset,
                        onClick = { viewModel.selectColor(presetArgb) }
                    )
                }

                // 4. Built-in wallpapers, grouped by category — new categories or images
                // registered in BuiltInWallpapers.all appear here automatically.
                val wallpapersByCategory = viewModel.builtInWallpapers.groupBy { it.category }
                wallpapersByCategory.forEach { (category, wallpapersInCategory) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = category,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(wallpapersInCategory) { wallpaper ->
                        WallpaperCard(
                            isSelected = selection is WallpaperSelection.BuiltIn && selection.id == wallpaper.id,
                            color = Color.Transparent,
                            imageModel = wallpaper.resId,
                            onClick = { viewModel.selectBuiltIn(wallpaper.id) }
                        )
                    }
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            onColorSelected = { argb -> viewModel.selectColor(argb) },
            onDismiss = { showColorPicker = false }
        )
    }
}

@Composable
fun ActionCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PrimaryGreen.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun WallpaperCard(
    isSelected: Boolean,
    color: Color,
    imageModel: Any? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(0.7f)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = color,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(3.dp, PrimaryGreen) else null
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
            }
        }
    }
}
