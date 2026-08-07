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
    onPrimary          = Color(0xFF003322),
    primaryContainer   = Color(0xFF00573A),
    onPrimaryContainer = Color(0xFF80E6B9),
    secondary          = SecondaryDark,
    onSecondary        = Color(0xFF003322),
    tertiary           = FlameOrange,
    onTertiary         = Color.White,
    background         = EcoDarkBackground,
    onBackground       = Color(0xFFDCEDE5),
    surface            = EcoDarkSurface,
    onSurface          = Color(0xFFDCEDE5),
    surfaceVariant     = EcoDarkSurfaceVar,
    onSurfaceVariant   = Color(0xFF8FB8A4),
    outline            = Color(0xFF3D5F50),
    error              = Color(0xFFFF5449),
    onError            = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary            = EcoGreenPrimary,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFB3E5D0),
    onPrimaryContainer = Color(0xFF004D33),
    secondary          = EcoGreenSecondary,
    onSecondary        = Color.White,
    tertiary           = FlameOrange,
    onTertiary         = Color.White,
    background         = EcoLightBackground,
    onBackground       = Color(0xFF0D1F18),
    surface            = EcoLightSurface,
    onSurface          = Color(0xFF0D1F18),
    surfaceVariant     = EcoLightSurfaceVar,
    onSurfaceVariant   = Color(0xFF3C6352),
    outline            = Color(0xFF6B9B84),
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
