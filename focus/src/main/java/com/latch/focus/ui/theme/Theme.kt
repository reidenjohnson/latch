package com.latch.focus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.latch.focus.R
import com.latch.focus.data.Hue

/*
 * Latch's design system. Calm and mostly neutral (Kairos Smoke ↔ Carbon), with the four Kairos hues used as
 * *signals*, each with one job, so no single color takes over:
 *   Teal  — your Latch tag: pairing, tapping, anything NFC
 *   Fern  — a session is running / success / allowed
 *   Amber — time: timers, schedules, streaks
 *   Brick — the emergency exit and destructive actions
 * Modes also wear a hue of the user's choice, which is where most of the color on screen comes from.
 * Hex values are Kairos's (kairos-app ui/Theme.kt). "Lift" = Kairos's lighter step for dark mode.
 */

@Immutable
data class Palette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val dim: Color,
    val faint: Color,
    val line: Color,
    /** Primary buttons: Carbon in light mode, Smoke in dark mode (Apple-style ink buttons). */
    val ink: Color,
    val onInk: Color,
    val teal: Color,
    val fern: Color,
    val amber: Color,
    val brick: Color,
    val dark: Boolean,
) {
    fun hue(h: Hue): Color = when (h) {
        Hue.Teal -> teal
        Hue.Fern -> fern
        Hue.Amber -> amber
        Hue.Brick -> brick
    }

    /** A deep version of a hue, for full-bleed backgrounds (session puck, block screen). */
    fun deep(h: Hue): Color = when (h) {
        Hue.Teal -> Color(0xFF0B4F5E)
        Hue.Fern -> Color(0xFF34452A)
        Hue.Amber -> Color(0xFF8A4F12)
        Hue.Brick -> Color(0xFF6E231B)
    }

    /** The saturated brand value of a hue (same in light and dark), for filled shapes with white on top. */
    fun solid(h: Hue): Color = when (h) {
        Hue.Teal -> Color(0xFF167C93)
        Hue.Fern -> Color(0xFF566E3F)
        Hue.Amber -> Color(0xFFDE8521)
        Hue.Brick -> Color(0xFFB23A2E)
    }
}

private val Light = Palette(
    bg = Color(0xFFF5F5F4),       // Kairos Smoke
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEEEEEC), // Kairos surface2
    text = Color(0xFF1E201D),     // Kairos text
    dim = Color(0xFF5E605B),      // Kairos dim
    faint = Color(0xFF95968F),    // Kairos faint
    line = Color(0x14202321),     // Kairos line
    ink = Color(0xFF202321),      // Kairos Carbon
    onInk = Color(0xFFF5F5F4),
    teal = Color(0xFF167C93),
    fern = Color(0xFF566E3F),
    amber = Color(0xFFC9761C),    // Amber one step deeper so it reads as text on white
    brick = Color(0xFFB23A2E),
    dark = false,
)

private val Dark = Palette(
    bg = Color(0xFF121314),
    surface = Color(0xFF1C1E1D),
    surface2 = Color(0xFF262927),
    text = Color(0xFFF1F1EE),
    dim = Color(0xFFA8A7A9),      // Kairos dim (dark)
    faint = Color(0xFF767579),    // Kairos faint (dark)
    line = Color(0x1AF1F0F2),     // Kairos line (dark)
    ink = Color(0xFFF1F1EE),
    onInk = Color(0xFF202321),
    teal = Color(0xFF57A6BA),     // Kairos TealLift
    fern = Color(0xFF8CB06B),     // Kairos FernLift
    amber = Color(0xFFE89A45),    // Kairos AmberLift
    brick = Color(0xFFE1614E),    // Kairos BrickLift
    dark = true,
)

val LocalPalette = staticCompositionLocalOf { Light }
val palette: Palette @Composable get() = LocalPalette.current

/** Hanken Grotesk (SIL OFL) for everything. One family, tight tracking at large sizes, tabular digits for timers. */
private fun hanken(w: Int) = Font(R.font.hanken_grotesk, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
val Hanken = FontFamily(hanken(400), hanken(500), hanken(600), hanken(700), hanken(800))

object Type {
    val display = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Medium, fontSize = 64.sp, letterSpacing = (-2).sp, fontFeatureSettings = "tnum")
    val largeTitle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-1).sp)
    val title = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp)
    val headline = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp)
    val body = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp)
    val callout = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp)
    val footnote = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp)
    val number = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = (-0.8).sp, fontFeatureSettings = "tnum")
}

@Composable
fun LatchTheme(content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) Dark else Light
    // Material components (dialogs, pickers, switches) pick up the same palette.
    val scheme = if (p.dark) darkColorScheme(
        primary = p.ink, onPrimary = p.onInk, secondary = p.teal, tertiary = p.amber, error = p.brick,
        background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text,
        surfaceVariant = p.surface2, onSurfaceVariant = p.dim, outline = p.faint, outlineVariant = p.line,
        surfaceContainerLowest = p.bg, surfaceContainerLow = p.surface, surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface2, surfaceContainerHighest = p.surface2, surfaceTint = p.surface,
        primaryContainer = p.surface2, onPrimaryContainer = p.text, secondaryContainer = p.surface2, onSecondaryContainer = p.text,
    ) else lightColorScheme(
        primary = p.ink, onPrimary = p.onInk, secondary = p.teal, tertiary = p.amber, error = p.brick,
        background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text,
        surfaceVariant = p.surface2, onSurfaceVariant = p.dim, outline = p.faint, outlineVariant = p.line,
        surfaceContainerLowest = p.surface, surfaceContainerLow = p.bg, surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface2, surfaceContainerHighest = p.surface2, surfaceTint = p.surface,
        primaryContainer = p.surface2, onPrimaryContainer = p.text, secondaryContainer = p.surface2, onSecondaryContainer = p.text,
    )
    val base = Typography()
    val typography = Typography(
        headlineSmall = base.headlineSmall.copy(fontFamily = Hanken, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = Hanken, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = Hanken, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = Hanken, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = Hanken),
        bodyMedium = base.bodyMedium.copy(fontFamily = Hanken),
        bodySmall = base.bodySmall.copy(fontFamily = Hanken),
        labelLarge = base.labelLarge.copy(fontFamily = Hanken, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = Hanken, fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontFamily = Hanken, fontWeight = FontWeight.SemiBold),
        displayLarge = base.displayLarge.copy(fontFamily = Hanken),
        displayMedium = base.displayMedium.copy(fontFamily = Hanken),
        displaySmall = base.displaySmall.copy(fontFamily = Hanken),
        headlineLarge = base.headlineLarge.copy(fontFamily = Hanken, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = Hanken, fontWeight = FontWeight.Bold),
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(30.dp),
            ),
            content = content,
        )
    }
}
