package com.example.contactapp.util

import androidx.compose.ui.graphics.Color

/** Shape applied to both the answer and decline buttons (decline stays red in every theme). */
enum class CallButtonShape { CIRCLE, ROUNDED_SQUARE, SQUARE, PILL, LEAF, ARCH, CLOVER, COOKIE }

/**
 * An accent color the user can apply to the call screen's answer button and the ring around
 * the avatar — independent of [CallButtonShape] and independent of Call Wallpaper's background.
 */
data class CallAccentColor(
    val id: String,
    val name: String,
    val color: Color,
    val isPremium: Boolean = false
)

object CallAccentColors {
    val all: List<CallAccentColor> = listOf(
        // Free
        CallAccentColor("green", "Green", Color(0xFF109D6F)),
        CallAccentColor("blue", "Blue", Color(0xFF2196F3)),
        CallAccentColor("orange", "Orange", Color(0xFFFF9800)),
        CallAccentColor("purple", "Purple", Color(0xFF9C27B0)),
        CallAccentColor("pink", "Pink", Color(0xFFE91E63)),
        CallAccentColor("teal", "Teal", Color(0xFF009688)),
        CallAccentColor("slate", "Slate", Color(0xFF37474F)),
        CallAccentColor("indigo", "Indigo", Color(0xFF3F51B5)),
        CallAccentColor("lime", "Lime", Color(0xFF8BC34A)),
        CallAccentColor("cyan", "Cyan", Color(0xFF00BCD4)),

        // Premium
        CallAccentColor("gold", "Gold", Color(0xFFFFC107), isPremium = true),
        CallAccentColor("emerald", "Emerald", Color(0xFF00C853), isPremium = true),
        CallAccentColor("ruby", "Ruby", Color(0xFFC2185B), isPremium = true),
        CallAccentColor("sapphire", "Sapphire", Color(0xFF1565C0), isPremium = true),
        CallAccentColor("amethyst", "Amethyst", Color(0xFF6A1B9A), isPremium = true),
        CallAccentColor("bronze", "Bronze", Color(0xFF8D6E63), isPremium = true),
        CallAccentColor("platinum", "Platinum", Color(0xFF90A4AE), isPremium = true),
        CallAccentColor("rose_gold", "Rose Gold", Color(0xFFE8A499), isPremium = true),
        CallAccentColor("midnight_blue", "Midnight Blue", Color(0xFF0D1B4C), isPremium = true),
        CallAccentColor("obsidian", "Obsidian", Color(0xFF1C1C1E), isPremium = true)
    )

    fun findById(id: String): CallAccentColor = all.find { it.id == id } ?: all.first()
}

/** Resolved combination actually applied to the call screens. */
data class CallTheme(
    val accentColor: Color,
    val buttonShape: CallButtonShape
)
