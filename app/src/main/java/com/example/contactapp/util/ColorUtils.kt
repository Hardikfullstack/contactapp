package com.example.contactapp.util

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

fun getAvatarColor(seed: String): Color {
    val colors = listOf(
        Color(0xFF109D6F), Color(0xFF2196F3), Color(0xFFF44336),
        Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFF00BCD4)
    )
    return colors[abs(seed.hashCode()) % colors.size]
}
