package com.phone.contacts.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.phone.contacts.ui.components.CustomSwitch
import com.phone.contacts.ui.features.call.ButtonFace
import com.phone.contacts.ui.features.call.CallButtonStyle
import com.phone.contacts.ui.features.call.IncomingCallButton
import com.phone.contacts.ui.features.call.SlideToAnswer
import com.phone.contacts.ui.features.call.SwipeUpChevrons
import com.phone.contacts.util.CallButtonStylePreferences

/** Settings > Call button styles — a grid of live phone-mockup previews (matching the reference
 * app's own grid picker, `AdapterCallButtonPreview`). The "Swap call buttons" toggle applies
 * immediately; tapping a style opens a full realistic preview (matching the reference app's own
 * "Call button" confirmation page) where "Set call button" actually commits that style. The
 * reference app locks most styles behind a rewarded-ad unlock; this app has no ads SDK, so every
 * style here is simply free (see CallButtonStyle's doc). */
@Composable
fun CallButtonStylesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var currentStyle by remember { mutableStateOf(CallButtonStylePreferences.style.value) }
    var swapButtons by remember { mutableStateOf(CallButtonStylePreferences.swapButtons.value) }
    var previewingStyle by remember { mutableStateOf<CallButtonStyle?>(null) }

    val styleToPreview = previewingStyle
    if (styleToPreview != null) {
        CallButtonPreviewScreen(
            style = styleToPreview,
            swapButtons = swapButtons,
            onBack = { previewingStyle = null },
            onSetCallButton = {
                CallButtonStylePreferences.setStyle(context, styleToPreview)
                currentStyle = styleToPreview
                previewingStyle = null
            }
        )
        return
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
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = "Call button styles",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.size(15.dp))
                Text(
                    text = "Swap call buttons",
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                CustomSwitch(
                    checked = swapButtons,
                    onCheckedChange = {
                        swapButtons = it
                        CallButtonStylePreferences.setSwapButtons(context, it)
                    }
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(CallButtonStyle.entries) { callStyle ->
                CallButtonStylePreview(
                    callStyle = callStyle,
                    swapButtons = swapButtons,
                    selected = callStyle == currentStyle,
                    onClick = { previewingStyle = callStyle }
                )
            }
        }
    }
}

/** A realistic mockup of the incoming-call screen with dummy caller info — matches the reference
 * app's own full-screen "Call button" preview/confirm page, reusing the exact same
 * [SlideToAnswer]/[IncomingCallButton] composables the real call screen renders (not a
 * reimplementation), so this is a true preview rather than an approximation. */
@Composable
private fun CallButtonPreviewScreen(
    style: CallButtonStyle,
    swapButtons: Boolean,
    onBack: () -> Unit,
    onSetCallButton: () -> Unit
) {
    // This screen's background is always dark, regardless of the app's own Light/Dark setting —
    // without this, a Light-themed app leaves the status bar icons dark too, invisible against
    // this screen's dark backdrop (looks like a plain black bar). Restored on exit so leaving back
    // to a Light-themed screen doesn't get stuck with white icons.
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        DisposableEffect(Unit) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            val wasLight = insetsController.isAppearanceLightStatusBars
            insetsController.isAppearanceLightStatusBars = false
            onDispose { insetsController.isAppearanceLightStatusBars = wasLight }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
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
                text = "Call button",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            Button(
                onClick = onSetCallButton,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Set call button", color = Color.White, fontWeight = FontWeight.SemiBold)
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
            Text(text = "Caller name", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.size(4.dp))
            Text(text = "Phone number", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        if (style.isSlider) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SlideToAnswer(onAnswer = {})
                Spacer(modifier = Modifier.size(20.dp))
                Text(
                    text = "Decline",
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
                    .padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 56.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val declineButton = @Composable {
                    style.decline?.let { face ->
                        IncomingCallButton(
                            face = face,
                            icon = Icons.Filled.CallEnd,
                            contentDescription = "Decline",
                            enableSwipeGesture = style.hasSwipeGesture,
                            onClick = {}
                        )
                    }
                }
                val acceptButton = @Composable {
                    style.accept?.let { face ->
                        IncomingCallButton(
                            face = face,
                            icon = Icons.Filled.Call,
                            contentDescription = "Answer",
                            enableSwipeGesture = style.hasSwipeGesture,
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
    }
}

@Composable
private fun CallButtonStylePreview(
    callStyle: CallButtonStyle,
    swapButtons: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.62f)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF0D1B2A), Color(0xFF1B2A3D))))
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        if (callStyle.isSlider) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 12.dp, start = 8.dp, end = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF39B54A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(text = "Slide to answer", color = Color.White, fontSize = 8.sp)
                }
                Spacer(modifier = Modifier.size(6.dp))
                Text(text = "Decline", color = Color(0xFFFF6B6B), fontSize = 9.sp, fontWeight = FontWeight.Medium)
            }
        } else {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val declineDot = @Composable {
                    callStyle.decline?.let { MiniCallButton(it, Icons.Filled.CallEnd, "Decline", callStyle.hasSwipeGesture) }
                }
                val acceptDot = @Composable {
                    callStyle.accept?.let { MiniCallButton(it, Icons.Filled.Call, "Accept", callStyle.hasSwipeGesture) }
                }
                if (swapButtons) {
                    acceptDot()
                    declineDot()
                } else {
                    declineDot()
                    acceptDot()
                }
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun MiniCallButton(
    face: ButtonFace,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    showSwipeChevron: Boolean
) {
    val brush = if (face.background.size > 1) {
        Brush.verticalGradient(face.background)
    } else {
        Brush.verticalGradient(listOf(face.background[0], face.background[0]))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (showSwipeChevron) {
            SwipeUpChevrons(color = face.background.last(), iconSize = 10.dp)
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(brush),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = face.iconColor, modifier = Modifier.size(14.dp))
        }
        Spacer(modifier = Modifier.size(3.dp))
        Text(text = label, color = Color.White, fontSize = 8.sp)
    }
}
