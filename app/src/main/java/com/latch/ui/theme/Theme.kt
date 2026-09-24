package com.latch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Brand: brass (the hardware of a latch) on ink / warm paper. Tertiary = success green.
private val Dark = darkColorScheme(
    primary = Color(0xFFE9B949),
    onPrimary = Color(0xFF1A1405),
    primaryContainer = Color(0xFF3A2E12),
    onPrimaryContainer = Color(0xFFF6D98E),
    secondary = Color(0xFFC9B68A),
    onSecondary = Color(0xFF1A1405),
    secondaryContainer = Color(0xFF3A2E12),
    onSecondaryContainer = Color(0xFFF6D98E),
    tertiary = Color(0xFF5FD39A),
    onTertiary = Color(0xFF00301A),
    tertiaryContainer = Color(0xFF123524),
    onTertiaryContainer = Color(0xFFA6F0C8),
    background = Color(0xFF0E0F12),
    onBackground = Color(0xFFECEDEF),
    surface = Color(0xFF0E0F12),
    onSurface = Color(0xFFECEDEF),
    surfaceVariant = Color(0xFF1E2128),
    onSurfaceVariant = Color(0xFF9AA0AA),
    surfaceContainerLowest = Color(0xFF0A0B0D),
    surfaceContainerLow = Color(0xFF14161A),
    surfaceContainer = Color(0xFF181A1F),
    surfaceContainerHigh = Color(0xFF1E2128),
    surfaceContainerHighest = Color(0xFF262A32),
    outline = Color(0xFF3A3F48),
    outlineVariant = Color(0xFF2A2E36),
    error = Color(0xFFFF7A70),
    onError = Color(0xFF3B0906),
    errorContainer = Color(0xFF3D1512),
    onErrorContainer = Color(0xFFFFD2CC),
)

private val Light = lightColorScheme(
    primary = Color(0xFF9A6B00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF8E2A8),
    onPrimaryContainer = Color(0xFF3A2800),
    secondary = Color(0xFF6E5E3A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF8E2A8),
    onSecondaryContainer = Color(0xFF3A2800),
    tertiary = Color(0xFF1E8E57),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCFF3DE),
    onTertiaryContainer = Color(0xFF00391F),
    background = Color(0xFFF7F5F0),
    onBackground = Color(0xFF15171B),
    surface = Color(0xFFF7F5F0),
    onSurface = Color(0xFF15171B),
    surfaceVariant = Color(0xFFEAE6DC),
    onSurfaceVariant = Color(0xFF5D636E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFAF6),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1EEE7),
    surfaceContainerHighest = Color(0xFFE9E5DC),
    outline = Color(0xFFCFC9BC),
    outlineVariant = Color(0xFFE3DED3),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDE0DC),
    onErrorContainer = Color(0xFF5C0B07),
)

private val Base = Typography()
private val LatchType = Base.copy(
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
)

private val LatchShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun LatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = LatchType,
        shapes = LatchShapes,
        content = content,
    )
}
