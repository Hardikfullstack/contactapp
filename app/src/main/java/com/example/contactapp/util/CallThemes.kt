package com.example.contactapp.util

import androidx.compose.ui.graphics.Color
import com.example.contactapp.R

/** Shape applied to both the answer and decline buttons (decline stays red in every theme). */
enum class CallButtonShape {
    CIRCLE, ROUNDED_SQUARE, SQUARE, LEAF, CLOVER, COOKIE, FLOWER, BADGE, SUN, STAMP;

    companion object {
        fun safeValueOf(name: String): CallButtonShape {
            return try {
                valueOf(name)
            } catch (e: Exception) {
                CIRCLE
            }
        }
    }
}

/**
 * An accent color the user can apply to the call screen's answer button and the ring around
 * the avatar — independent of [CallButtonShape] and independent of Call Wallpaper's background.
 */
data class CallAccentColor(
    val id: String,
    val name: String,
    val color: Color,
    val categoryResId: Int,
    val isPremium: Boolean = false
)

object CallAccentColors {
    val all: List<CallAccentColor> = listOf(
        // Greens
        CallAccentColor("green", "Green", Color(0xFF109D6F), R.string.color_category_greens),
        CallAccentColor("teal", "Teal", Color(0xFF009688), R.string.color_category_greens),
        CallAccentColor("lime", "Lime", Color(0xFF8BC34A), R.string.color_category_greens),
        CallAccentColor("sage", "Sage", Color(0xFF9CAF88), R.string.color_category_greens),
        CallAccentColor("light_mint", "Light Mint", Color(0xFFDAF7A6), R.string.color_category_greens),
        CallAccentColor("deep_mint", "Deep Mint", Color(0xFF58D68D), R.string.color_category_greens),

        // Blues
        CallAccentColor("blue", "Blue", Color(0xFF2196F3), R.string.color_category_blues),
        CallAccentColor("cyan", "Cyan", Color(0xFF00BCD4), R.string.color_category_blues),
        CallAccentColor("sky_blue", "Sky Blue", Color(0xFF87CEEB), R.string.color_category_blues),
        CallAccentColor("cloud_blue", "Cloud Blue", Color(0xFFAED6F1), R.string.color_category_blues),
        CallAccentColor("steel_blue", "Steel Blue", Color(0xFF3498DB), R.string.color_category_blues),

        // Purples
        CallAccentColor("purple", "Purple", Color(0xFF9C27B0), R.string.color_category_purples),
        CallAccentColor("indigo", "Indigo", Color(0xFF3F51B5), R.string.color_category_purples),
        CallAccentColor("lavender_mist", "Lavender Mist", Color(0xFFE6E6FA), R.string.color_category_purples),
        CallAccentColor("soft_lavender", "Soft Lavender", Color(0xFFE8DAEF), R.string.color_category_purples),
        CallAccentColor("rich_plum", "Rich Plum", Color(0xFF8E44AD), R.string.color_category_purples),

        // Reds & Pinks
        CallAccentColor("pink", "Pink", Color(0xFFE91E63), R.string.color_category_reds_pinks),
        CallAccentColor("dusty_rose", "Dusty Rose", Color(0xFFC08081), R.string.color_category_reds_pinks),
        CallAccentColor("warm_coral", "Warm Coral", Color(0xFFFF7F50), R.string.color_category_reds_pinks),
        CallAccentColor("terracotta_shaded", "Terracotta", Color(0xFFCD5C5C), R.string.color_category_reds_pinks),

        // Warm / Oranges
        CallAccentColor("orange", "Orange", Color(0xFFFF9800), R.string.color_category_warm),
        CallAccentColor("peach", "Peach", Color(0xFFFFCC99), R.string.color_category_warm),
        CallAccentColor("pale_peach", "Pale Peach", Color(0xFFFFDAB9), R.string.color_category_warm),

        // Neutrals
        CallAccentColor("slate", "Slate", Color(0xFF37474F), R.string.color_category_neutrals),
        CallAccentColor("linen", "Linen", Color(0xFFFAF0E6), R.string.color_category_neutrals),

        // Premium
        CallAccentColor("gold", "Gold", Color(0xFFFFC107), R.string.premium_tones, isPremium = true),
        CallAccentColor("emerald", "Emerald", Color(0xFF00C853), R.string.premium_tones, isPremium = true),
        CallAccentColor("ruby", "Ruby", Color(0xFFC2185B), R.string.premium_tones, isPremium = true),
        CallAccentColor("sapphire", "Sapphire", Color(0xFF1565C0), R.string.premium_tones, isPremium = true),
        CallAccentColor("amethyst", "Amethyst", Color(0xFF6A1B9A), R.string.premium_tones, isPremium = true),
        CallAccentColor("bronze", "Bronze", Color(0xFF8D6E63), R.string.premium_tones, isPremium = true),
        CallAccentColor("platinum", "Platinum", Color(0xFF90A4AE), R.string.premium_tones, isPremium = true),
        CallAccentColor("rose_gold", "Rose Gold", Color(0xFFE8A499), R.string.premium_tones, isPremium = true),
        CallAccentColor("midnight_blue", "Midnight Blue", Color(0xFF0D1B4C), R.string.premium_tones, isPremium = true),
        CallAccentColor("obsidian", "Obsidian", Color(0xFF1C1C1E), R.string.premium_tones, isPremium = true)
    )

    fun findById(id: String): CallAccentColor = all.find { it.id == id } ?: all.first()
}

/** Resolved combination actually applied to the call screens. */
data class CallTheme(
    val accentColor: Color,
    val buttonShape: CallButtonShape
)
