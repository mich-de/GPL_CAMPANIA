package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary            = PrimaryDark,
    onPrimary          = Color(0xFF00344D),
    primaryContainer   = Color(0xFF0D4A6E),
    onPrimaryContainer = Color(0xFF81D4FA),
    secondary          = SecondaryDark,
    onSecondary        = Color(0xFF00344D),
    tertiary           = FlameOrange,
    onTertiary         = Color(0xFF2A1D02),
    background         = EcoDarkBackground,
    onBackground       = Color(0xFFDCE6F5),
    surface            = EcoDarkSurface,
    onSurface          = Color(0xFFDCE6F5),
    surfaceVariant     = EcoDarkSurfaceVar,
    onSurfaceVariant   = Color(0xFF8FA8CC),
    outline            = Color(0xFF3D5578),
    error              = Color(0xFFFF5449),
    onError            = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary            = EcoGreenPrimary,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFCFE3FA),
    onPrimaryContainer = Color(0xFF04345C),
    secondary          = EcoGreenSecondary,
    onSecondary        = Color.White,
    tertiary           = FlameOrange,
    onTertiary         = Color(0xFF2A1D02),
    background         = EcoLightBackground,
    onBackground       = Color(0xFF0C1526),
    surface            = EcoLightSurface,
    onSurface          = Color(0xFF0C1526),
    surfaceVariant     = EcoLightSurfaceVar,
    onSurfaceVariant   = Color(0xFF3D537A),
    outline            = Color(0xFF7C93B8),
    error              = Color(0xFFBA1A1A),
    onError            = Color.White
)

@Composable
fun GplTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    GplTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
