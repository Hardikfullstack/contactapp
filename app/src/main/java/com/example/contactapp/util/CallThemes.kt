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
    val nameResId: Int,
    val color: Color,
    val categoryResId: Int,
    val isPremium: Boolean = false
)

object CallAccentColors {
    val all: List<CallAccentColor> = listOf(
        // Greens
        CallAccentColor("green", R.string.color_green, Color(0xFF109D6F), R.string.color_category_greens),
        CallAccentColor("teal", R.string.color_teal, Color(0xFF009688), R.string.color_category_greens),
        CallAccentColor("lime", R.string.color_lime, Color(0xFF8BC34A), R.string.color_category_greens),
        CallAccentColor("sage", R.string.color_sage, Color(0xFF9CAF88), R.string.color_category_greens),
        CallAccentColor("light_mint", R.string.color_light_mint, Color(0xFFDAF7A6), R.string.color_category_greens),
        CallAccentColor("deep_mint", R.string.color_deep_mint, Color(0xFF58D68D), R.string.color_category_greens),

        // Blues
        CallAccentColor("blue", R.string.color_blue, Color(0xFF2196F3), R.string.color_category_blues),
        CallAccentColor("cyan", R.string.color_cyan, Color(0xFF00BCD4), R.string.color_category_blues),
        CallAccentColor("sky_blue", R.string.color_sky_blue, Color(0xFF87CEEB), R.string.color_category_blues),
        CallAccentColor("cloud_blue", R.string.color_cloud_blue, Color(0xFFAED6F1), R.string.color_category_blues),
        CallAccentColor("steel_blue", R.string.color_steel_blue, Color(0xFF3498DB), R.string.color_category_blues),

        // Purples
        CallAccentColor("purple", R.string.color_purple, Color(0xFF9C27B0), R.string.color_category_purples),
        CallAccentColor("indigo", R.string.color_indigo, Color(0xFF3F51B5), R.string.color_category_purples),
        CallAccentColor("lavender_mist", R.string.color_lavender_mist, Color(0xFFE6E6FA), R.string.color_category_purples),
        CallAccentColor("soft_lavender", R.string.color_soft_lavender, Color(0xFFE8DAEF), R.string.color_category_purples),
        CallAccentColor("rich_plum", R.string.color_rich_plum, Color(0xFF8E44AD), R.string.color_category_purples),

        // Reds & Pinks
        CallAccentColor("pink", R.string.color_pink, Color(0xFFE91E63), R.string.color_category_reds_pinks),
        CallAccentColor("dusty_rose", R.string.color_dusty_rose, Color(0xFFC08081), R.string.color_category_reds_pinks),
        CallAccentColor("warm_coral", R.string.color_warm_coral, Color(0xFFFF7F50), R.string.color_category_reds_pinks),
        CallAccentColor("terracotta_shaded", R.string.color_terracotta, Color(0xFFCD5C5C), R.string.color_category_reds_pinks),

        // Warm / Oranges
        CallAccentColor("orange", R.string.color_orange, Color(0xFFFF9800), R.string.color_category_warm),
        CallAccentColor("peach", R.string.color_peach, Color(0xFFFFCC99), R.string.color_category_warm),
        CallAccentColor("pale_peach", R.string.color_pale_peach, Color(0xFFFFDAB9), R.string.color_category_warm),

        // Neutrals
        CallAccentColor("slate", R.string.color_slate, Color(0xFF37474F), R.string.color_category_neutrals),
        CallAccentColor("linen", R.string.color_linen, Color(0xFFFAF0E6), R.string.color_category_neutrals),

        // Premium
        CallAccentColor("gold", R.string.color_gold, Color(0xFFFFC107), R.string.premium_tones, isPremium = true),
        CallAccentColor("emerald", R.string.color_emerald, Color(0xFF00C853), R.string.premium_tones, isPremium = true),
        CallAccentColor("ruby", R.string.color_ruby, Color(0xFFC2185B), R.string.premium_tones, isPremium = true),
        CallAccentColor("sapphire", R.string.color_sapphire, Color(0xFF1565C0), R.string.premium_tones, isPremium = true),
        CallAccentColor("amethyst", R.string.color_amethyst, Color(0xFF6A1B9A), R.string.premium_tones, isPremium = true),
        CallAccentColor("bronze", R.string.color_bronze, Color(0xFF8D6E63), R.string.premium_tones, isPremium = true),
        CallAccentColor("platinum", R.string.color_platinum, Color(0xFF90A4AE), R.string.premium_tones, isPremium = true),
        CallAccentColor("rose_gold", R.string.color_rose_gold, Color(0xFFE8A499), R.string.premium_tones, isPremium = true),
        CallAccentColor("midnight_blue", R.string.color_midnight_blue, Color(0xFF0D1B4C), R.string.premium_tones, isPremium = true),
        CallAccentColor("obsidian", R.string.color_obsidian, Color(0xFF1C1C1E), R.string.premium_tones, isPremium = true)
    )

    fun findById(id: String): CallAccentColor = all.find { it.id == id } ?: all.first()
}

/** Resolved combination actually applied to the call screens. */
data class CallTheme(
    val accentColor: Color,
    val buttonShape: CallButtonShape
)
