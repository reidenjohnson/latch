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

/**
 * Latch uses the Kairos palette (Reiden's brand, from kairos-app ui/Theme.kt). Only four accent hues exist:
 * Fern (primary / success), Teal (secondary / links), Amber (highlight) and Brick (errors), on a warm-neutral
 * Smoke ↔ Carbon ramp. Every other color is a lightness step of one of those, on the same hue.
 * Values marked "Kairos" are copied exactly. "step" = a same-hue lightness step for roles Kairos doesn't define.
 */
private object Brand {
    val Smoke = Color(0xFFF5F5F4)      // Kairos
    val Carbon = Color(0xFF202321)     // Kairos
    val Fern = Color(0xFF566E3F)       // Kairos primary
    val FernLift = Color(0xFF8CB06B)   // Kairos (Fern lifted for dark)
    val Teal = Color(0xFF167C93)       // Kairos water (Teal +2)
    val TealLift = Color(0xFF57A6BA)   // Kairos (dark)
    val Amber = Color(0xFFDE8521)      // Kairos
    val AmberLift = Color(0xFFE89A45)  // Kairos (dark)
    val Brick = Color(0xFFB23A2E)      // Kairos
    val BrickLift = Color(0xFFE1614E)  // Kairos (dark)
}

private val Light = lightColorScheme(
    primary = Brand.Fern,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2EAD8),      // step: pale Fern
    onPrimaryContainer = Color(0xFF2B3A1E),    // step: deep Fern
    secondary = Brand.Teal,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEEF2),    // step: pale Teal (selected chips, tonal buttons)
    onSecondaryContainer = Color(0xFF074552),  // Kairos Brand.Teal
    tertiary = Brand.Amber,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBE8D2),     // step: pale Amber
    onTertiaryContainer = Color(0xFF5A3208),   // step: deep Amber
    background = Brand.Smoke,
    onBackground = Color(0xFF1E201D),          // Kairos text
    surface = Color(0xFFFFFFFF),               // Kairos surface
    onSurface = Color(0xFF1E201D),
    surfaceVariant = Color(0xFFEEEEEC),        // Kairos surface2
    onSurfaceVariant = Color(0xFF5E605B),      // Kairos dim
    surfaceTint = Color(0xFFFFFFFF),           // Kairos: keep Material's tint neutral
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F6),   // Kairos
    surfaceContainer = Color(0xFFFFFFFF),      // Kairos
    surfaceContainerHigh = Color(0xFFEEEEEC),  // Kairos
    surfaceContainerHighest = Color(0xFFE3E3E0), // Kairos
    outline = Color(0xFF95968F),               // Kairos faint
    outlineVariant = Color(0x12202321),        // Kairos line
    error = Brand.Brick,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF6DEDA),        // step: pale Brick
    onErrorContainer = Color(0xFF5C1A12),      // step: deep Brick
)

private val Dark = darkColorScheme(
    primary = Brand.FernLift,
    onPrimary = Color(0xFF0E100E),             // Kairos
    primaryContainer = Color(0xFF3A5029),      // Kairos segBottom (Fern, darker step)
    onPrimaryContainer = Color(0xFFD7E6C4),    // step: pale Fern
    secondary = Brand.TealLift,
    onSecondary = Color(0xFF0E100E),
    secondaryContainer = Color(0xFF074552),    // Kairos Brand.Teal
    onSecondaryContainer = Color(0xFFCDE9F0),  // step: pale Teal
    tertiary = Brand.AmberLift,
    onTertiary = Color(0xFF0E100E),
    tertiaryContainer = Color(0xFF4A2E0C),     // step: deep Amber
    onTertiaryContainer = Color(0xFFFFDDB8),   // step: pale Amber
    background = Color(0xFF19181A),            // Kairos bg
    onBackground = Color(0xFFECEBEC),          // Kairos text
    surface = Color(0xFF232224),               // Kairos surface
    onSurface = Color(0xFFECEBEC),
    surfaceVariant = Color(0xFF2C2B2E),        // Kairos surface2
    onSurfaceVariant = Color(0xFFA8A7A9),      // Kairos dim
    surfaceTint = Color(0xFF232224),
    surfaceContainerLowest = Color(0xFF131214), // Kairos
    surfaceContainerLow = Color(0xFF232224),   // Kairos
    surfaceContainer = Color(0xFF232224),      // cards = Kairos surface
    surfaceContainerHigh = Color(0xFF2C2B2E),  // Kairos
    surfaceContainerHighest = Color(0xFF322F33), // Kairos
    outline = Color(0xFF767579),               // Kairos faint
    outlineVariant = Color(0x1AF1F0F2),        // Kairos line
    error = Brand.BrickLift,
    onError = Color(0xFF2A0A06),               // Kairos
    errorContainer = Color(0xFF3B1814),        // step: deep Brick
    onErrorContainer = Color(0xFFF5C9C1),      // step: pale Brick
)

/**
 * What each brand color means in Latch (target balance like Kairos: ~40 Fern / ~40 Teal / ~20 Amber):
 *   Fern  = writing, success, people (Write, verified, Writable, Genuine, contacts)
 *   Teal  = reading, links, connections (Read tab, Wi-Fi, Bluetooth, social, apps, selected chips)
 *   Amber = highlights and power features (Blueprints, passwords, live links, locked notes, warnings)
 *   Brick = destructive or failed only (Lock forever, errors)
 */
object Accent {
    val fern: Color @Composable get() = MaterialTheme.colorScheme.primary
    val teal: Color @Composable get() = MaterialTheme.colorScheme.secondary
    val amber: Color @Composable get() = MaterialTheme.colorScheme.tertiary
    val brick: Color @Composable get() = MaterialTheme.colorScheme.error
}

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
