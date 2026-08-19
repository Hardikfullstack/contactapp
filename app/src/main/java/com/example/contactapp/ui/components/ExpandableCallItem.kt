package com.example.contactapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.example.contactapp.domain.model.CallLogItem

 @OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandableCallItem(
    call: CallLogItem,
    expanded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onCallClick: () -> Unit = {},
    onMessageClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {}
) {

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = Color.Transparent,
        shape = RoundedCornerShape(0.dp)
    ) {

        Column {

            CallItem(
                call = call,
                onCallClick = onCallClick
            )

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() +
                        slideInVertically(initialOffsetY = { it / 2 }) +
                        expandVertically(),

                exit = fadeOut() +
                        slideOutVertically(targetOffsetY = { it / 2 }) +
                        shrinkVertically()
            ) {

                QuickActionRow(
                    onCallClick = onCallClick,
                    onMessageClick = onMessageClick,
                    onHistoryClick = onHistoryClick
                )

            }

        }

    }

}