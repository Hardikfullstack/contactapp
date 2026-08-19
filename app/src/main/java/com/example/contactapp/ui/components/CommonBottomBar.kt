package com.example.contactapp.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = modifier,
        windowInsets = NavigationBarDefaults.windowInsets
    ) {
        items.forEach { item ->
            val isSelected = item.selected
            NavigationBarItem(
                selected = isSelected,
                onClick = item.onClick,
                icon = {
                    Icon(
                        imageVector = if (isSelected && item.selectedIcon != null) item.selectedIcon else item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
