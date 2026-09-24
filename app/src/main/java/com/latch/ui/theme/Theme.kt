package com.latch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.latch.R
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

/**
 * Kairos's type system: Bricolage Grotesque for display/headings, Hanken Grotesk for body/UI. Both are variable
 * fonts, so each weight pins the `wght` axis. SIL Open Font License, see /licenses.
 */
private fun bricolage(w: Int) = Font(R.font.bricolage_grotesque, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
private fun hanken(w: Int) = Font(R.font.hanken_grotesk, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
val Bricolage = FontFamily(bricolage(500), bricolage(700), bricolage(800))
val Hanken = FontFamily(hanken(400), hanken(500), hanken(600), hanken(700))

private val Base = Typography()
private val LatchType = Base.copy(
    displaySmall = Base.displaySmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, letterSpacing = (-1).sp),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    titleLarge = Base.titleLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
    titleSmall = Base.titleSmall.copy(fontFamily = Hanken, fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(fontFamily = Hanken),
    bodyMedium = Base.bodyMedium.copy(fontFamily = Hanken),
    bodySmall = Base.bodySmall.copy(fontFamily = Hanken),
    labelLarge = Base.labelLarge.copy(fontFamily = Hanken, fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontFamily = Hanken, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    labelSmall = Base.labelSmall.copy(fontFamily = Hanken, fontWeight = FontWeight.SemiBold),
)

/** Kairos's atmosphere tokens (values copied from kairos ui/Theme.kt) plus bold gradient pairs per accent. */
data class Extras(
    val washTop: Color, val washBottom: Color,
    val heroTop: Color, val heroBottom: Color,
    val seg: Color, val onSeg: Color,
    val shadow: Color, val line: Color,
    val fernGradient: List<Color>, val tealGradient: List<Color>, val amberGradient: List<Color>, val brickGradient: List<Color>,
)

private val LightExtras = Extras(
    washTop = Color(0xFFEDF2F3), washBottom = Color(0xFFF1F3EC),      // Kairos
    heroTop = Color(0xFFEAF1F1), heroBottom = Color(0xFFF4F6F3),      // Kairos
    seg = Color(0xFF5C7642), onSeg = Color(0xFFF3F2EF),               // Kairos segTop / onSeg
    shadow = Color(0xFF1B2B31), line = Color(0x14202321),             // Kairos shadowSpot / cardBorder
    fernGradient = listOf(Color(0xFF5C7642), Color(0xFF445734)),      // Kairos segTop → segBottom
    tealGradient = listOf(Color(0xFF167C93), Color(0xFF074552)),      // Kairos water → Brand.Teal
    amberGradient = listOf(Color(0xFFDE8521), Color(0xFFB0661A)),     // Kairos Amber → step darker
    brickGradient = listOf(Color(0xFFB23A2E), Color(0xFF8A2A21)),     // Kairos Brick → step darker
)

private val DarkExtras = Extras(
    washTop = Color(0xFF171C1E), washBottom = Color(0xFF1A1B17),      // Kairos
    heroTop = Color(0xFF1E2528), heroBottom = Color(0xFF1D1C1E),      // Kairos
    seg = Color(0xFF4E6A37), onSeg = Color(0xFFF1F1EE),               // Kairos
    shadow = Color(0xFF000000), line = Color(0x1FF1F0F2),             // Kairos
    fernGradient = listOf(Color(0xFF4E6A37), Color(0xFF3A5029)),      // Kairos dark seg
    tealGradient = listOf(Color(0xFF167C93), Color(0xFF074552)),
    amberGradient = listOf(Color(0xFFC9761C), Color(0xFF8F5314)),     // step: Amber, dimmed for dark
    brickGradient = listOf(Color(0xFFB23A2E), Color(0xFF7A251D)),
)

val LocalExtras = staticCompositionLocalOf { LightExtras }
val extras: Extras @Composable get() = LocalExtras.current

/** The Kairos screen wash: faint teal at the top, neutral in the middle, faint fern at the bottom. */
@Composable
fun screenWash(): Brush = Brush.verticalGradient(listOf(extras.washTop, MaterialTheme.colorScheme.background, extras.washBottom))

private val LatchShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun LatchTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalExtras provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = LatchType,
            shapes = LatchShapes,
            content = content,
        )
    }
}
