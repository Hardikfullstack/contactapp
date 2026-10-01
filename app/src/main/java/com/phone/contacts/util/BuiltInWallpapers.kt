package com.phone.contacts.util

import androidx.annotation.DrawableRes
import com.phone.contacts.R

/** A single built-in wallpaper. [category] drives grouping in the Call Wallpaper UI — the UI
 * groups wallpapers by whatever distinct category strings appear here, so adding a brand-new
 * category (or a wallpaper to an existing one) never requires UI changes. */
data class BuiltInWallpaper(
    val id: String,
    val category: String,
    @DrawableRes val resId: Int,
    val isDark: Boolean
)

/** Central registry of every built-in wallpaper shipped with the app, matching the reference
 * app's own Call Wallpaper picker. */
object BuiltInWallpapers {
    val all: List<BuiltInWallpaper> = listOf(
        // --- Abstract (generated gradients) ---
        BuiltInWallpaper("abstract_sunset", "Abstract", R.drawable.wallpaper_sunset, isDark = false),
        BuiltInWallpaper("abstract_ocean", "Abstract", R.drawable.wallpaper_ocean, isDark = true),
        BuiltInWallpaper("abstract_aurora", "Abstract", R.drawable.wallpaper_aurora, isDark = true),
        BuiltInWallpaper("abstract_midnight", "Abstract", R.drawable.wallpaper_midnight, isDark = true),
        BuiltInWallpaper("abstract_forest", "Abstract", R.drawable.wallpaper_forest, isDark = true),
        BuiltInWallpaper("abstract_rose", "Abstract", R.drawable.wallpaper_rose, isDark = false),
        BuiltInWallpaper("abstract_slate", "Abstract", R.drawable.wallpaper_slate, isDark = false),
        BuiltInWallpaper("abstract_lavender", "Abstract", R.drawable.wallpaper_lavender, isDark = false),
        BuiltInWallpaper("abstract_citrus", "Abstract", R.drawable.wallpaper_citrus, isDark = false),
        BuiltInWallpaper("abstract_charcoal", "Abstract", R.drawable.wallpaper_charcoal, isDark = true),
        BuiltInWallpaper("abstract_coral_reef", "Abstract", R.drawable.wallpaper_coral_reef, isDark = false),
        BuiltInWallpaper("abstract_nebula", "Abstract", R.drawable.wallpaper_nebula, isDark = true),

        // --- Photos ---
        BuiltInWallpaper("image_1", "Wallpaper", R.drawable.image_1, isDark = false),
        BuiltInWallpaper("image_2", "Wallpaper", R.drawable.image_2, isDark = false),
        BuiltInWallpaper("image_3", "Wallpaper", R.drawable.image_3, isDark = false),
        BuiltInWallpaper("image_4", "Wallpaper", R.drawable.image_4, isDark = false),
        BuiltInWallpaper("image_5", "Wallpaper", R.drawable.image_5, isDark = true),
        BuiltInWallpaper("image_6", "Wallpaper", R.drawable.image_6, isDark = true),
        BuiltInWallpaper("image_7", "Wallpaper", R.drawable.image_7, isDark = true),
        BuiltInWallpaper("image_8", "Wallpaper", R.drawable.image_8, isDark = false),
        BuiltInWallpaper("image_9", "Wallpaper", R.drawable.image_9, isDark = false)
    )

    fun findById(id: String): BuiltInWallpaper? = all.find { it.id == id }
}
