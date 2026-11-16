package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.models.FontSizeOption
import com.example.models.ThemeMode
import com.example.models.UiLanguage

private val P2PDarkColorScheme = darkColorScheme(
    primary = NeonViolet,
    onPrimary = Color.White,
    primaryContainer = NeonVioletDeep,
    onPrimaryContainer = TextPrimary,
    secondary = ElectricCyan,
    onSecondary = GraphiteVoid,
    secondaryContainer = ElectricCyanDeep,
    onSecondaryContainer = TextPrimary,
    tertiary = NeonVioletBright,
    onTertiary = GraphiteVoid,
    background = GraphiteVoid,
    onBackground = TextPrimary,
    surface = GraphiteSurface,
    onSurface = TextPrimary,
    surfaceVariant = GraphiteCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerHigh = GraphiteCardElevated,
    outline = GraphiteBorder,
    outlineVariant = GraphiteBorder.copy(alpha = 0.6f),
    error = ErrorCrimson,
    onError = Color.White
)

private val P2PLightColorScheme = lightColorScheme(
    primary = LightVioletAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = LightCyanAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = NeonViolet,
    onTertiary = Color.White,
    background = FrostBackground,
    onBackground = FrostTextPrimary,
    surface = FrostSurface,
    onSurface = FrostTextPrimary,
    surfaceVariant = FrostCard,
    onSurfaceVariant = FrostTextSecondary,
    surfaceContainerHigh = FrostCardElevated,
    outline = FrostBorder,
    outlineVariant = FrostBorder.copy(alpha = 0.7f),
    error = ErrorCrimson,
    onError = Color.White
)

val P2PShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

val LocalIsDarkTheme = staticCompositionLocalOf { true }
val LocalHapticEnabled = staticCompositionLocalOf { true }

@Composable
fun P2PChatTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    language: UiLanguage = UiLanguage.PERSIAN,
    fontSize: FontSizeOption = FontSizeOption.NORMAL,
    hapticEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (isDark) P2PDarkColorScheme else P2PLightColorScheme
    val typography = buildAppTypography(language = language, scale = fontSize.scale)

    CompositionLocalProvider(
        LocalIsDarkTheme provides isDark,
        LocalHapticEnabled provides hapticEnabled
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = P2PShapes,
            content = content
        )
    }
}
