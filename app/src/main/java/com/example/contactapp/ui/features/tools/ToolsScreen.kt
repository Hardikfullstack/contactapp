package com.example.contactapp.ui.features.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.contactapp.R
import com.example.contactapp.ui.components.CommonHeader
import com.example.contactapp.ui.theme.*

data class ToolItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconBackground: Color,
    val onClick: () -> Unit
)

@Composable
fun ToolsScreen(
    onRecycleBinClick: () -> Unit,
    onAnalyticsClick: () -> Unit,
    onFakeCallClick: () -> Unit,
    onAnnouncerClick: () -> Unit,
    onFlashAlertClick: () -> Unit,
    onWallpaperClick: () -> Unit,
    onCallThemesClick: () -> Unit,
    onSetRingtoneClick: () -> Unit,
    onAutoReplyClick: () -> Unit,
    onKeypadClick: () -> Unit,
    onCallReminderClick: () -> Unit,
    viewModel: ToolsViewModel = hiltViewModel()
) {
    val toolItems = listOf(
        ToolItem(
            title = stringResource(R.string.tool_call_analytics),
            description = stringResource(R.string.tool_call_analytics_desc),
            icon = Icons.Outlined.BarChart,
            iconBackground = Color(0xFF4CAF50),
            onClick = onAnalyticsClick
        ),
        ToolItem(
            title = stringResource(R.string.tool_fake_call),
            description = stringResource(R.string.tool_fake_call_desc),
            icon = Icons.Outlined.TheaterComedy,
            iconBackground = Color(0xFF03A9F4),
            onClick = onFakeCallClick
        ),
        ToolItem(
            title = stringResource(R.string.tool_call_announcer),
            description = stringResource(R.string.tool_call_announcer_desc),
            icon = Icons.Outlined.VolumeUp,
            iconBackground = Color(0xFF9C27B0),
            onClick = onAnnouncerClick
        ),
        ToolItem(
            title = stringResource(R.string.tool_flash_alert),
            description = stringResource(R.string.tool_flash_alert_desc),
            icon = Icons.Outlined.FlashlightOn,
            iconBackground = Color(0xFFE91E63),
            onClick = onFlashAlertClick
        ),
        ToolItem(
            title = stringResource(R.string.tool_call_wallpaper),
            description = stringResource(R.string.tool_call_wallpaper_desc),
            icon = Icons.Outlined.Image,
            iconBackground = Color(0xFFFF9800),
            onClick = onWallpaperClick
        ),
        ToolItem(
            title = stringResource(R.string.tool_call_themes),
            description = stringResource(R.string.tool_call_themes_desc),
            icon = Icons.Outlined.Palette,
            iconBackground = Color(0xFF3F51B5),
            onClick = onCallThemesClick
        ),
        ToolItem(
            title = stringResource(R.string.tool_set_ringtone),
            description = stringResource(R.string.tool_set_ringtone_desc),
            icon = Icons.Outlined.MusicNote,
            iconBackground = Color(0xFF673AB7),
            onClick = onSetRingtoneClick
        ),
        ToolItem(
            title = stringResource(R.string.tool_auto_reply),
            description = stringResource(R.string.tool_auto_reply_desc),
            icon = Icons.AutoMirrored.Outlined.Chat,
            iconBackground = Color(0xFF009688),
            onClick = onAutoReplyClick
        ),
        ToolItem(
            title = stringResource(R.string.tool_call_reminder),
            description = stringResource(R.string.tool_call_reminder_desc),
            icon = Icons.Outlined.NotificationsActive,
            iconBackground = Color(0xFFFF5722),
            onClick = onCallReminderClick
        )
    )

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onKeypadClick,
                containerColor = PrimaryGreen,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Dialpad, contentDescription = "Keypad")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .statusBarsPadding()
        ) {
            CommonHeader(
                title = stringResource(R.string.tools),
                actions = {
                    ProBadge()
                }
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(toolItems) { item ->
                    ToolCard(item = item)
                }
            }
        }
    }
}

@Composable
fun ToolCard(item: ToolItem) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { item.onClick() },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(item.iconBackground.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = item.iconBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ProBadge() {
    Surface(
        modifier = Modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2))
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MilitaryTech,
                contentDescription = null,
                tint = Color(0xFFFFA000),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.pro_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFE65100),
                fontSize = 14.sp
            )
        }
    }
}
