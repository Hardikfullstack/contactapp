package com.example.contactapp.ui.features.keypad.components

import android.R.attr.fontWeight
import android.R.attr.letterSpacing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialButton(
    digit: String,
    letters: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    showBackground: Boolean = true
) {

    var pressed by remember {
        mutableStateOf(false)
    }

    val scale =
        animateFloatAsState(
            if (pressed) 0.94f else 1f,
            label = ""
        )

    Surface(
        modifier = Modifier
            .size(66.dp)
            .scale(scale.value),
        shape = CircleShape,
        color = if (showBackground) MaterialTheme.colorScheme.surface else Color.Transparent,
        tonalElevation = 0.dp
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    interactionSource = remember {
                        MutableInteractionSource()
                    },
                    indication = null,
                    onClick = {
                        pressed = true
                        onClick()
                        pressed = false
                    },
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    text = digit,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )

                    Text(
                        text = if (letters.isBlank()) " " else letters,
                        modifier = Modifier.offset(y = (-7).dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 3.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

            }

        }

    }

}