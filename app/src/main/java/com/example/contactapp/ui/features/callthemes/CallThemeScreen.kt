package com.example.contactapp.ui.features.callthemes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.contactapp.R
import com.example.contactapp.ui.components.CallWallpaperBackground
import com.example.contactapp.ui.components.CommonHeader
import com.example.contactapp.ui.components.lightened
import com.example.contactapp.ui.components.toComposeShape
import com.example.contactapp.ui.theme.PrimaryGreen
import com.example.contactapp.util.CallAccentColor
import com.example.contactapp.util.CallButtonShape
import com.example.contactapp.util.WallpaperSelection

@Composable
fun CallThemeScreen(
    onBack: () -> Unit,
    viewModel: CallThemeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedColor = com.example.contactapp.util.CallAccentColors.findById(uiState.selectedColorId)
    val wallpaperSelection by viewModel.wallpaperSelectionFlow.collectAsState(
        initial = viewModel.getCurrentWallpaperSelection()
    )

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .statusBarsPadding()
        ) {
            CommonHeader(
                title = stringResource(R.string.tool_call_themes),
                onBackClick = onBack
            )

            LivePreview(
                accentColor = selectedColor.color,
                shape = uiState.selectedShape,
                wallpaperSelection = wallpaperSelection
            )

            Text(
                text = stringResource(R.string.button_shape),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp)
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                lazyItems(viewModel.shapes) { shape ->
                    ShapeOption(
                        shape = shape,
                        isSelected = uiState.selectedShape == shape,
                        accentColor = selectedColor.color,
                        onClick = { viewModel.selectShape(shape) },
                        modifier = Modifier.width(96.dp)
                    )
                }
            }

            val groupedColors = viewModel.colors.groupBy { it.categoryResId }

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                groupedColors.forEach { (categoryResId, colorsInCategory) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(categoryResId),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }

                    items(colorsInCategory) { accentColor ->
                        ColorSwatch(
                            accentColor = accentColor,
                            shape = uiState.selectedShape.toComposeShape(),
                            isSelected = uiState.selectedColorId == accentColor.id,
                            onClick = { viewModel.selectColor(accentColor.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LivePreview(accentColor: Color, shape: CallButtonShape, wallpaperSelection: WallpaperSelection) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1A1A1A)
    ) {
        Box {
            // Shows the same background the real call screen will use — falls back to the
            // Surface's own dark color above when no wallpaper is selected.
            CallWallpaperBackground(selection = wallpaperSelection, modifier = Modifier.matchParentSize())

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .border(3.dp, accentColor, CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                val declineColor = Color(0xFFD32F2F)
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .shadow(elevation = 6.dp, shape = shape.toComposeShape(), ambientColor = declineColor, spotColor = declineColor)
                        .clip(shape.toComposeShape())
                        .background(Brush.verticalGradient(listOf(declineColor.lightened(), declineColor))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .shadow(elevation = 6.dp, shape = shape.toComposeShape(), ambientColor = accentColor, spotColor = accentColor)
                        .clip(shape.toComposeShape())
                        .background(Brush.verticalGradient(listOf(accentColor.lightened(), accentColor))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            }
        }
    }
}

@Composable
private fun ShapeOption(
    shape: CallButtonShape,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when (shape) {
        CallButtonShape.CIRCLE -> stringResource(R.string.shape_circle)
        CallButtonShape.ROUNDED_SQUARE -> stringResource(R.string.shape_rounded_square)
        CallButtonShape.SQUARE -> stringResource(R.string.shape_square)
        CallButtonShape.LEAF -> stringResource(R.string.shape_leaf)
        CallButtonShape.CLOVER -> stringResource(R.string.shape_clover)
        CallButtonShape.COOKIE -> stringResource(R.string.shape_cookie)
        CallButtonShape.FLOWER -> stringResource(R.string.shape_flower)
        CallButtonShape.BADGE -> stringResource(R.string.shape_badge)
        CallButtonShape.SUN -> stringResource(R.string.shape_sun)
        CallButtonShape.STAMP -> stringResource(R.string.shape_stamp)
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (isSelected) BorderStroke(2.dp, PrimaryGreen) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(shape.toComposeShape())
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    accentColor: CallAccentColor,
    shape: Shape,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                onClick = onClick,
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                color = accentColor.color,
                border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }

            if (accentColor.isPremium) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp),
                    shape = CircleShape,
                    color = Color(0xFFFFC107),
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Star, contentDescription = stringResource(R.string.premium), tint = Color(0xFF7A5B00), modifier = Modifier.size(11.dp))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(accentColor.nameResId),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
