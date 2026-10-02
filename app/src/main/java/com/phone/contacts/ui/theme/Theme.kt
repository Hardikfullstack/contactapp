package com.phone.contacts.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryContainerDark,
    onPrimaryContainer = BrandOnPrimaryContainerDark,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF1F1F24),
    onSurfaceVariant = Color(0xFFA0A0A8)
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryContainerLight,
    onPrimaryContainer = BrandOnPrimaryContainerLight,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFF7F7F9),
    surface = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF1F1F4),
    onSurfaceVariant = Color(0xFF6B6B72)
)

@Composable
fun ContactsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default so the brand color above isn't overridden by the device wallpaper's palette.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Caps the system "Font size" accessibility setting at 1.2x - lets someone with a mild
    // preference still get bigger text, but stops it scaling all the way up (2x on some devices)
    // and overflowing/clipping layouts that weren't built to flex that far.
    val uncappedDensity = LocalDensity.current
    val cappedDensity = Density(
        density = uncappedDensity.density,
        fontScale = uncappedDensity.fontScale.coerceAtMost(1.2f)
    )

    CompositionLocalProvider(LocalDensity provides cappedDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/** [MaterialTheme.colorScheme.primary] is a fairly dark, saturated blue - fine as a filled
 * element's background (paired with the light `onPrimary` text/icon that sits on top of it), but
 * low-contrast and hard to read when used as plain text/icon color directly on a dark surface.
 * This swaps in the same lighter blue the theme already uses for text on a dark primary
 * container, only when the resolved background is actually dark - light mode (where primary
 * already reads fine on its own) is untouched. */
@Composable
fun primaryAccentColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        BrandPrimaryAccentDark
    } else {
        MaterialTheme.colorScheme.primary
    }