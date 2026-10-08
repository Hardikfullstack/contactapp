package com.phone.contacts.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import androidx.compose.runtime.collectAsState
import com.phone.contacts.R
import com.phone.contacts.ads.AdPlacements
import com.phone.contacts.ads.AdType
import com.phone.contacts.ads.NativeAdView
import com.phone.contacts.ads.NativeAdTemplate
import com.phone.contacts.ads.rememberBackWithInterstitial
import com.phone.contacts.ui.components.CallWallpaperBackground
import com.phone.contacts.ui.components.ColorPickerDialog
import com.phone.contacts.ui.components.CustomSwitch
import com.phone.contacts.ui.features.call.IncomingCallButton
import com.phone.contacts.ui.features.call.SlideToAnswer
import com.phone.contacts.util.AppConfigStore
import com.phone.contacts.util.BuiltInWallpapers
import com.phone.contacts.util.CallButtonStylePreferences
import com.phone.contacts.util.WallpaperPreferences
import com.phone.contacts.util.WallpaperSelection
import com.phone.contacts.util.isDarkOnCallScreen
import kotlinx.coroutines.launch

@Composable
fun CallWallpaperScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    remember { WallpaperPreferences.initialize(context) }
    val scope = rememberCoroutineScope()
    val adConfig by AppConfigStore.config.collectAsState()
    // Matches the reference app: WallpapersActivity shows an interstitial on back.
    val backWithAd = rememberBackWithInterstitial(
        AdPlacements.adUnitId(adConfig?.result, AdType.INTERSTITIAL_ON_BACK, slot = 6),
        onBack
    )
    val selection by WallpaperPreferences.selection
    var showColorPicker by remember { mutableStateOf(false) }
    var previewCandidate by remember { mutableStateOf<WallpaperSelection?>(null) }

    val candidate = previewCandidate
    if (candidate != null) {
        WallpaperPreviewScreen(
            candidate = candidate,
            onBack = { previewCandidate = null },
            onSetWallpaper = { blur ->
                WallpaperPreferences.commit(context, candidate, blur)
                previewCandidate = null
            }
        )
        return
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}
            scope.launch { previewCandidate = WallpaperPreferences.deviceCandidate(context, it) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = backWithAd) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = stringResource(R.string.call_wallpaper_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column {
                WallpaperOptionRow(
                    icon = Icons.Filled.Wallpaper,
                    iconBackgroundColor = Color(0xFFE91E63),
                    title = stringResource(R.string.app_default_wallpaper),
                    selected = selection is WallpaperSelection.None,
                    onClick = { previewCandidate = WallpaperSelection.None }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 70.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                WallpaperOptionRow(
                    icon = Icons.Filled.ColorLens,
                    iconBackgroundColor = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.pick_color),
                    selected = selection is WallpaperSelection.SolidColor,
                    onClick = { showColorPicker = true }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 70.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                WallpaperOptionRow(
                    icon = Icons.Filled.Image,
                    iconBackgroundColor = Color(0xFFFF9800),
                    title = stringResource(R.string.choose_from_gallery),
                    selected = selection is WallpaperSelection.Device,
                    onClick = { launcher.launch("image/*") }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.select_wallpaper_title),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // "None" — a fixed dark color (not MaterialTheme.colorScheme.surfaceVariant, which
            // flips with light/dark app theme) since this swatch previews the real call screen's
            // actual fallback background, always this same dark color regardless of app theme.
            item {
                WallpaperCard(
                    isSelected = selection is WallpaperSelection.None,
                    color = Color(0xFF1A1A1A),
                    onClick = { previewCandidate = WallpaperSelection.None }
                )
            }

            if (selection is WallpaperSelection.Device) {
                item {
                    WallpaperCard(
                        isSelected = true,
                        color = Color.Transparent,
                        imageModel = (selection as WallpaperSelection.Device).uri,
                        onClick = {}
                    )
                }
            }

            val customColorArgb = (selection as? WallpaperSelection.SolidColor)?.colorArgb
            val matchesPreset = customColorArgb != null &&
                WallpaperPreferences.colorPresets.any { it.toInt() == customColorArgb }
            if (customColorArgb != null && !matchesPreset) {
                item {
                    WallpaperCard(
                        isSelected = true,
                        color = Color(customColorArgb),
                        onClick = { showColorPicker = true }
                    )
                }
            }

            items(WallpaperPreferences.colorPresets) { preset ->
                val presetArgb = preset.toInt()
                WallpaperCard(
                    isSelected = selection is WallpaperSelection.SolidColor && (selection as WallpaperSelection.SolidColor).colorArgb == presetArgb,
                    color = Color(preset),
                    onClick = { previewCandidate = WallpaperPreferences.colorCandidate(presetArgb) }
                )
            }

            val wallpapersByCategory = BuiltInWallpapers.all.groupBy { it.category }
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
                        isSelected = selection is WallpaperSelection.BuiltIn && (selection as WallpaperSelection.BuiltIn).id == wallpaper.id,
                        color = Color.Transparent,
                        imageModel = wallpaper.resId,
                        onClick = { previewCandidate = WallpaperPreferences.builtInCandidate(wallpaper.id) }
                    )
                }
            }
        }

        AdPlacements.adUnitId(adConfig?.result, AdType.NATIVE, slot = 9)?.let {
            NativeAdView(
                adUnitId = it,
                template = NativeAdTemplate.STRIP
            )
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            onColorSelected = { argb -> previewCandidate = WallpaperPreferences.colorCandidate(argb) },
            onDismiss = { showColorPicker = false }
        )
    }
}

@Composable
private fun WallpaperOptionRow(
    icon: ImageVector,
    iconBackgroundColor: Color,
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBackgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.size(15.dp))
        Text(text = title, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        if (selected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        } else {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WallpaperCard(
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
        border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
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
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
            }
        }
    }
}

/** A realistic mockup of the incoming-call screen with the candidate wallpaper as background and
 * dummy caller info — matches [com.phone.contacts.ui.screens.CallButtonStylesScreen]'s own
 * "Call button" preview page, including reusing the exact same [SlideToAnswer]/[IncomingCallButton]
 * composables the real call screen renders, plus the user's already-chosen call button style so
 * this preview reflects what a real call actually looks like. */
@Composable
private fun WallpaperPreviewScreen(
    candidate: WallpaperSelection,
    onBack: () -> Unit,
    onSetWallpaper: (blur: Boolean) -> Unit
) {
    var blurPreview by remember { mutableStateOf(WallpaperPreferences.blurEnabled.value) }
    val callButtonStyle = CallButtonStylePreferences.style.value
    val swapButtons = CallButtonStylePreferences.swapButtons.value

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        DisposableEffect(Unit) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            val wasLight = insetsController.isAppearanceLightStatusBars
            insetsController.isAppearanceLightStatusBars = !candidate.isDarkOnCallScreen()
            onDispose { insetsController.isAppearanceLightStatusBars = wasLight }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        CallWallpaperBackground(selection = candidate, blurEnabled = blurPreview, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.wallpaper_label),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                Button(
                    onClick = { onSetWallpaper(blurPreview) },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.action_set_wallpaper), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF9E9E9E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
                Spacer(modifier = Modifier.size(16.dp))
                Text(text = stringResource(R.string.mock_caller_name), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.size(4.dp))
                Text(text = stringResource(R.string.hint_phone_number), color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.weight(1f))

            if (callButtonStyle.isSlider) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SlideToAnswer(onAnswer = {})
                    Spacer(modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.action_decline),
                        color = Color(0xFFFF6B6B),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(onClick = {})
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val declineButton = @Composable {
                        callButtonStyle.decline?.let { face ->
                            IncomingCallButton(
                                face = face,
                                icon = Icons.Filled.CallEnd,
                                contentDescription = "Decline",
                                enableSwipeGesture = callButtonStyle.hasSwipeGesture,
                                onClick = {}
                            )
                        }
                    }
                    val acceptButton = @Composable {
                        callButtonStyle.accept?.let { face ->
                            IncomingCallButton(
                                face = face,
                                icon = Icons.Filled.Call,
                                contentDescription = "Answer",
                                enableSwipeGesture = callButtonStyle.hasSwipeGesture,
                                onClick = {}
                            )
                        }
                    }
                    if (swapButtons) {
                        acceptButton()
                        declineButton()
                    } else {
                        declineButton()
                        acceptButton()
                    }
                }
            }

            Surface(
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.wallpaper_blur_label), color = Color.White, modifier = Modifier.weight(1f))
                    CustomSwitch(checked = blurPreview, onCheckedChange = { blurPreview = it })
                }
            }
        }
    }
}
