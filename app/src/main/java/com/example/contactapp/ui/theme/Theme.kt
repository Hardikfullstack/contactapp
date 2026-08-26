package com.example.contactapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF005234),
    onPrimaryContainer = Color(0xFF98F7CB),
    secondary = DarkOnSurfaceVariant,
    onSecondary = DarkBackground,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = Color(0xFF3F443F)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = PrimaryGreenLight,
    onPrimaryContainer = Color(0xFF003822),
    secondary = TextSecondary,
    onSecondary = Color.White,
    background = BackgroundLightGrey,
    surface = BackgroundWhite,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

/**
 * The app's actual dark/light state (from the user's in-app theme choice, resolved against
 * system default when set to "System" — see MainActivity), as opposed to [isSystemInDarkTheme].
 * Views that can't read MaterialTheme.colorScheme directly — e.g. AndroidView-hosted native ads,
 * which style themselves from plain color constants, not Compose theme — read this instead of
 * [isSystemInDarkTheme] so they stay in sync with the in-app toggle even when it disagrees with
 * the system (app set to Dark while the phone itself is in light mode, or vice versa).
 */
val LocalIsDarkTheme = compositionLocalOf { false }

@Composable
fun ContactAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set to false to prioritize Figma branding over dynamic OS colors
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

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
