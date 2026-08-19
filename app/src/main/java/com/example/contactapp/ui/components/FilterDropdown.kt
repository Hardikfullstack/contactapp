package com.example.contactapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.contactapp.R
import com.example.contactapp.ui.features.recents.CallFilter
import com.example.contactapp.ui.theme.PrimaryGreen

@Composable
fun FilterDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    selectedFilter: CallFilter,
    onFilterSelected: (CallFilter) -> Unit
) {
    MaterialTheme(
        shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            offset = DpOffset(x = (-145).dp, y = 13.dp),
            modifier = Modifier
                .width(170.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
        ) {
            CallFilter.values().forEach { filter ->
                val isSelected = selectedFilter == filter
                DropdownMenuItem(
                    modifier = Modifier.height(35.dp),
                    text = {
                        Text(
                            text = when (filter) {
                                CallFilter.ALL -> stringResource(R.string.filter_all)
                                CallFilter.MISSED -> stringResource(R.string.filter_missed)
                                CallFilter.CONTACTS -> stringResource(R.string.filter_contacts)
                                CallFilter.INCOMING -> stringResource(R.string.filter_incoming)
                                CallFilter.OUTGOING -> stringResource(R.string.filter_outgoing)
                                CallFilter.SPAM -> stringResource(R.string.filter_spam)
                            },
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp,
                        )
                    },
                    onClick = {
                        onFilterSelected(filter)
                        onDismissRequest()
                    }
                )
            }
        }
    }
}
