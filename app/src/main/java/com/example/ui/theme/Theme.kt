package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.core.ThemePreference

private val JarvisDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianBlack,
    primaryContainer = Color(0xFF0A324A),
    onPrimaryContainer = CyanGlow,
    secondary = CobaltBlue,
    onSecondary = IceWhite,
    secondaryContainer = Color(0xFF152A54),
    onSecondaryContainer = Color(0xFFBFDBFE),
    tertiary = NeonViolet,
    onTertiary = IceWhite,
    tertiaryContainer = Color(0xFF281A4A),
    onTertiaryContainer = SoftViolet,
    background = ObsidianBlack,
    onBackground = IceWhite,
    surface = DeepNavy,
    onSurface = IceWhite,
    surfaceVariant = MidnightSurface,
    onSurfaceVariant = CoolSlate,
    surfaceContainerLowest = ObsidianBlack,
    surfaceContainerLow = DeepNavy,
    surfaceContainer = GlassCardDark,
    surfaceContainerHigh = ElevatedNavy,
    surfaceContainerHighest = Color(0xFF223564),
    outline = GlassOutlineDark,
    outlineVariant = Color(0xFF19284C),
    error = CrimsonError,
    onError = IceWhite,
    errorContainer = Color(0xFF451219),
    onErrorContainer = Color(0xFFFCA5A5)
)

private val JarvisLightColorScheme = lightColorScheme(
    primary = DeepCobaltLight,
    onPrimary = PureSurfaceLight,
    primaryContainer = SoftTintLight,
    onPrimaryContainer = Color(0xFF003B7A),
    secondary = DeepCyanLight,
    onSecondary = PureSurfaceLight,
    secondaryContainer = Color(0xFFD8F5F8),
    onSecondaryContainer = Color(0xFF004D57),
    tertiary = DeepVioletLight,
    onTertiary = PureSurfaceLight,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF4C1D95),
    background = CloudFrost,
    onBackground = InkNavy,
    surface = PureSurfaceLight,
    onSurface = InkNavy,
    surfaceVariant = SoftTintLight,
    onSurfaceVariant = SlateSecondaryLight,
    surfaceContainerLowest = PureSurfaceLight,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFCBD5E1),
    outline = GlassOutlineLight,
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = PureSurfaceLight,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

val JarvisShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JarvisTheme(
    themePreference: ThemePreference = ThemePreference.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDark) JarvisDarkColorScheme else JarvisLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content
    )
}
