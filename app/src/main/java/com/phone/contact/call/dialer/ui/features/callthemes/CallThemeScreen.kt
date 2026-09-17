package com.phone.contact.call.dialer.ui.features.callthemes

import android.app.Activity
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phone.contact.call.dialer.R
import com.phone.contact.call.dialer.ads.InterstitialAdManager
import com.phone.contact.call.dialer.ui.components.CallWallpaperBackground
import com.phone.contact.call.dialer.ui.components.CommonHeader
import com.phone.contact.call.dialer.ui.components.lightened
import com.phone.contact.call.dialer.ui.components.toComposeShape
import com.phone.contact.call.dialer.ui.theme.PrimaryGreen
import com.phone.contact.call.dialer.util.CallAccentColor
import com.phone.contact.call.dialer.util.CallButtonShape
import com.phone.contact.call.dialer.util.WallpaperSelection
import com.phone.contact.call.dialer.viewmodel.AppConfigViewModel

@Composable
fun CallThemeScreen(
    onBack: () -> Unit,
    viewModel: CallThemeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedColor = com.phone.contact.call.dialer.util.CallAccentColors.findById(uiState.selectedColorId)
    val wallpaperSelection by viewModel.wallpaperSelectionFlow.collectAsState(
        initial = viewModel.getCurrentWallpaperSelection()
    )

    val context = LocalContext.current
    val activity = context as? Activity
    val appConfigViewModel: AppConfigViewModel = viewModel(context as ComponentActivity)
    val adConfig by appConfigViewModel.appResponse.collectAsState()
    // Dedicated slot for the premium-color unlock interstitial — interstitial_3 was previously
    // unused by any screen.
    val premiumColorInterstitialAdUnitId = adConfig?.result?.let { result ->
        if (result.google_ads_on_off == "on" && result.interstitial_3_on_off == "on") {
            result.interstitial_3?.takeIf { it.isNotBlank() }
        } else null
    }
    LaunchedEffect(premiumColorInterstitialAdUnitId) {
        premiumColorInterstitialAdUnitId?.let { InterstitialAdManager.preload(context, it) }
    }

    // Color tapped while a premium unlock is pending (dialog shown) — null when no dialog is up.
    var pendingPremiumColor by remember { mutableStateOf<CallAccentColor?>(null) }

    pendingPremiumColor?.let { colorPendingUnlock ->
        val premiumGold = Color(0xFFFFC107)
        val iconSize = 72.dp
        Dialog(
            onDismissRequest = { pendingPremiumColor = null },
            properties = DialogProperties(dismissOnClickOutside = false)
        ) {
            Box(contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .padding(top = iconSize / 2)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 24.dp)
                        .padding(top = iconSize / 2 + 16.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.premium_color_dialog_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(colorPendingUnlock.color.lightened(), colorPendingUnlock.color)))
                            .border(2.dp, premiumGold, CircleShape)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.premium_color_dialog_message),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            pendingPremiumColor = null
                            val proceed = { viewModel.selectColor(colorPendingUnlock.id) }
                            if (activity != null && premiumColorInterstitialAdUnitId != null &&
                                InterstitialAdManager.isReady(premiumColorInterstitialAdUnitId)
                            ) {
                                InterstitialAdManager.show(activity, premiumColorInterstitialAdUnitId) { proceed() }
                            } else {
                                // Not ready (still loading, failed to load, or no activity) — never
                                // block the user on the ad, grant access right away either way.
                                proceed()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = premiumGold, contentColor = Color(0xFF3D2E00)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_unlock),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    TextButton(
                        onClick = { pendingPremiumColor = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(premiumGold.lightened(), premiumGold))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFF3D2E00),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }

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

            Spacer(modifier = Modifier.height(8.dp))

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
                        val isSelected = uiState.selectedColorId == accentColor.id
                        ColorSwatch(
                            accentColor = accentColor,
                            shape = uiState.selectedShape.toComposeShape(),
                            isSelected = isSelected,
                            onClick = {
                                if (accentColor.isPremium && !isSelected) {
                                    pendingPremiumColor = accentColor
                                } else if (!accentColor.isPremium) {
                                    viewModel.selectColor(accentColor.id)
                                }
                            }
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
