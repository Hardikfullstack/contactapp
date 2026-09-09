package com.example.contactapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.contactapp.ui.theme.LocalIsDarkTheme

data class BottomBarActionItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val selected: Boolean = false,
    val selectedIcon: ImageVector? = null
)

@Composable
fun CommonBottomBar(
    items: List<BottomBarActionItem>,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    enabled: Boolean = true
) {
    val unselectedColor = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF656565)

    Column(modifier = modifier) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.outlineVariant else Color(0xFFCDCDCD)
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            windowInsets = windowInsets
        ) {
            items.forEach { item ->
                val isSelected = item.selected
                NavigationBarItem(
                    selected = isSelected,
                    onClick = item.onClick,
                    enabled = enabled,
                    icon = {
                        Icon(
                            imageVector = if (isSelected && item.selectedIcon != null) item.selectedIcon else item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = item.label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = unselectedColor,
                        unselectedTextColor = unselectedColor,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }
}
