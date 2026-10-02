package com.phone.contacts.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phone.contacts.R
import com.phone.contacts.util.ContactNameFormat
import com.phone.contacts.util.ContactSortOrder
import com.phone.contacts.util.DisplayOptionsPreferences

private enum class DisplayOptionPage { NONE, SORT_BY, NAME_FORMAT }

@Composable
fun DisplayOptionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    remember { DisplayOptionsPreferences.initialize(context) }
    val sortOrder by DisplayOptionsPreferences.sortOrder
    val nameFormat by DisplayOptionsPreferences.nameFormat
    var page by remember { mutableStateOf(DisplayOptionPage.NONE) }

    when (page) {
        DisplayOptionPage.SORT_BY -> {
            SingleChoiceScreen(
                title = stringResource(R.string.sort_by_title),
                options = ContactSortOrder.entries,
                labelOf = { it.label },
                selected = sortOrder,
                onBack = { page = DisplayOptionPage.NONE },
                onSelect = {
                    DisplayOptionsPreferences.setSortOrder(context, it)
                    page = DisplayOptionPage.NONE
                }
            )
            return
        }
        DisplayOptionPage.NAME_FORMAT -> {
            SingleChoiceScreen(
                title = stringResource(R.string.name_format_title),
                options = ContactNameFormat.entries,
                labelOf = { it.label },
                selected = nameFormat,
                onBack = { page = DisplayOptionPage.NONE },
                onSelect = {
                    DisplayOptionsPreferences.setNameFormat(context, it)
                    page = DisplayOptionPage.NONE
                }
            )
            return
        }
        DisplayOptionPage.NONE -> Unit
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
                text = stringResource(R.string.display_option_title),
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
                DisplayOptionRow(
                    icon = Icons.AutoMirrored.Filled.Sort,
                    iconBackgroundColor = Color(0xFFE91E63),
                    title = stringResource(R.string.sort_by_title),
                    subtitle = sortOrder.label,
                    onClick = { page = DisplayOptionPage.SORT_BY }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 70.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                DisplayOptionRow(
                    icon = Icons.Filled.FormatListNumbered,
                    iconBackgroundColor = Color(0xFFFF9800),
                    title = stringResource(R.string.name_format_title),
                    subtitle = nameFormat.label,
                    onClick = { page = DisplayOptionPage.NAME_FORMAT }
                )
            }
        }
    }
}

@Composable
private fun DisplayOptionRow(
    icon: ImageVector,
    iconBackgroundColor: Color,
    title: String,
    subtitle: String,
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
                .clip(CircleShape)
                .background(iconBackgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.size(15.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A plain text single-choice picker — tapping an option commits it immediately and returns,
 * matching how a binary "Sort by"/"Name format" picker only needs one tap, no separate confirm
 * step. Shared by both pickers here since neither option set needs per-choice icons. */
@Composable
private fun <T> SingleChoiceScreen(
    title: String,
    options: List<T>,
    labelOf: (T) -> String,
    selected: T,
    onBack: () -> Unit,
    onSelect: (T) -> Unit
) {
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
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
        }

        options.forEach { option ->
            Surface(
                onClick = { onSelect(option) },
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = labelOf(option),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                }
            }
        }
    }
}
