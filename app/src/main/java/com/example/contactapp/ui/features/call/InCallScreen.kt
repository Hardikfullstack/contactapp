package com.example.contactapp.ui.features.call

import android.telecom.Call
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import com.example.contactapp.R
import com.example.contactapp.service.CallManager
import com.example.contactapp.ui.components.CallWallpaperBackground
import com.example.contactapp.ui.components.SwipeUpCallButton
import com.example.contactapp.ui.components.lightened
import com.example.contactapp.ui.components.toComposeShape
import com.example.contactapp.ui.theme.PrimaryGreen
import com.example.contactapp.util.CallAccentColors
import com.example.contactapp.util.CallButtonShape
import com.example.contactapp.util.CallTheme
import com.example.contactapp.util.WallpaperSelection

@Composable
fun InCallScreen(
    onHangup: () -> Unit,
    onDecline: () -> Unit,
    onAnswer: () -> Unit,
    onReportSpam: (String) -> Unit = {},
    isSpam: Boolean = false,
    selection: WallpaperSelection,
    theme: CallTheme = CallTheme(CallAccentColors.findById("green").color, CallButtonShape.CIRCLE)
) {
    val call by CallManager.currentCall.collectAsState()
    val callState by CallManager.callState.collectAsState()

    val number = call?.details?.handle?.schemeSpecificPart ?: "Unknown"
    val displayName = number

    var buttonsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { buttonsVisible = true }

    Box(modifier = Modifier.fillMaxSize()) {
        // Wallpaper Background (falls back to dark charcoal when none selected)
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)))
        CallWallpaperBackground(selection = selection, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 80.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .border(3.dp, theme.accentColor, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = displayName,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = getCallStateText(callState),
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )

                if (isSpam) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color.Red.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.spam_warning),
                                color = Color(0xFFFF5252),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = buttonsVisible,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 80.dp),
                enter = slideInVertically(animationSpec = tween(450)) { fullHeight -> fullHeight } + fadeIn(tween(450))
            ) {
                // Swaps the ringing (decline + answer) button set for the ongoing-call
                // (single end-call) set with an upward slide, mirroring stock dialer behavior.
                AnimatedContent(
                    targetState = callState == Call.STATE_RINGING,
                    transitionSpec = {
                        (slideInVertically(tween(350)) { height -> height } + fadeIn(tween(350))) togetherWith
                            (slideOutVertically(tween(350)) { height -> -height } + fadeOut(tween(200)))
                    },
                    label = "callButtons"
                ) { isRinging ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (isRinging) {
                            // Both controls require an upward swipe while ringing.
                            SwipeUpCallButton(
                                icon = Icons.Default.CallEnd,
                                color = Color(0xFFD32F2F),
                                shape = theme.buttonShape.toComposeShape(),
                                contentDescription = stringResource(R.string.decline_call),
                                onTriggered = onDecline
                            )
                            SwipeUpCallButton(
                                icon = Icons.Default.Call,
                                color = theme.accentColor,
                                shape = theme.buttonShape.toComposeShape(),
                                contentDescription = stringResource(R.string.answer),
                                onTriggered = onAnswer
                            )
                        } else {
                            // Once answered, End Call is a normal tap target again.
                            CallActionButtons(
                                icon = Icons.Default.CallEnd,
                                color = Color(0xFFD32F2F),
                                shape = theme.buttonShape.toComposeShape(),
                                onClick = onHangup,
                                label = stringResource(R.string.end_call)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CallActionButtons(
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    label: String,
    shape: Shape = CircleShape
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(72.dp)
                .shadow(elevation = 10.dp, shape = shape, ambientColor = color, spotColor = color),
            shape = shape,
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(color.lightened(), color))),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun getCallStateText(state: Int): String {
    return when (state) {
        Call.STATE_ACTIVE -> stringResource(R.string.ongoing_call)
        Call.STATE_DIALING -> stringResource(R.string.dialing)
        Call.STATE_RINGING -> stringResource(R.string.incoming_call_status)
        Call.STATE_CONNECTING -> stringResource(R.string.connecting)
        Call.STATE_HOLDING -> stringResource(R.string.on_hold)
        Call.STATE_DISCONNECTED -> stringResource(R.string.disconnected)
        else -> stringResource(R.string.unknown)
    }
}
