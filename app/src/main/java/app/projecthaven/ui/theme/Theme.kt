// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.theme

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.projecthaven.R
import app.projecthaven.data.settings.ThemeMode

/** Raw brand colours. Use [HavenColors] in UI code, never these directly. */
object HavenPalette {
    val Black = Color(0xFF000000)
    val Blue = Color(0xFF0A84FF)
    val Surface = Color(0xFF1C1C1E)
    val SurfaceRaised = Color(0xFF2C2C2E)
    val White = Color(0xFFFFFFFF)
    val Gray = Color(0xFF8E8E93)
    val Red = Color(0xFFFF453A)
    val Green = Color(0xFF30D158)
    val Amber = Color(0xFFFF9F0A)

    // Light theme: tuned for contrast on light surfaces rather than inverted.
    val LightBackground = Color(0xFFF2F2F7)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceRaised = Color(0xFFE5E5EA)
    val LightText = Color(0xFF000000)
    val LightSecondary = Color(0xFF5E5E63)  // ≥ 5.9:1 on white
    val LightBlue = Color(0xFF0066D6)       // ≥ 5.4:1 on white, keeps white-on-blue readable
    val LightRed = Color(0xFFD70015)
    val LightGreen = Color(0xFF248A3D)
    val LightAmber = Color(0xFFB25000)
}

@Immutable
data class HavenColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val text: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val emergency: Color,
    val onEmergency: Color,
    val divider: Color,
    val ready: Color,
    val attention: Color,
)

val DarkHavenColors = HavenColors(
    isDark = true,
    background = HavenPalette.Black,
    surface = HavenPalette.Surface,
    surfaceRaised = HavenPalette.SurfaceRaised,
    text = HavenPalette.White,
    textSecondary = HavenPalette.Gray,
    accent = HavenPalette.Blue,
    onAccent = HavenPalette.White,
    emergency = HavenPalette.Red,
    onEmergency = HavenPalette.White,
    divider = Color(0xFF38383A),
    ready = HavenPalette.Green,
    attention = HavenPalette.Amber,
)

val LightHavenColors = HavenColors(
    isDark = false,
    background = HavenPalette.LightBackground,
    surface = HavenPalette.LightSurface,
    surfaceRaised = HavenPalette.LightSurfaceRaised,
    text = HavenPalette.LightText,
    textSecondary = HavenPalette.LightSecondary,
    accent = HavenPalette.LightBlue,
    onAccent = HavenPalette.White,
    emergency = HavenPalette.LightRed,
    onEmergency = HavenPalette.White,
    divider = Color(0xFFD1D1D6),
    ready = HavenPalette.LightGreen,
    attention = HavenPalette.LightAmber,
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Type scale in sp so it follows the system font-size setting. */
@Immutable
data class HavenType(
    val largeTitle: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.4).sp),
    val title: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    val headline: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    val body: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    val callout: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    val footnote: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    val caption: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    val numeral: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-1).sp),
)

object Dimens {
    val gutter = 16.dp
    val gap = 12.dp
    val gapSmall = 8.dp
    val gapLarge = 24.dp
    val cardRadius = 16.dp
    val controlRadius = 12.dp
    val minTouch = 48.dp
    val rowHeight = 56.dp
}

val CardShape = RoundedCornerShape(Dimens.cardRadius)
val ControlShape = RoundedCornerShape(Dimens.controlRadius)

val LocalHavenColors = staticCompositionLocalOf { DarkHavenColors }
val LocalHavenType = staticCompositionLocalOf { HavenType() }

object Haven {
    val colors: HavenColors @Composable get() = LocalHavenColors.current
    val type: HavenType @Composable get() = LocalHavenType.current
}

fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> systemDark
}

@Composable
fun HavenTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode.isDark(isSystemInDarkTheme())
    val c = if (dark) DarkHavenColors else LightHavenColors
    val type = HavenType()
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, secondary = c.accent, onSecondary = c.onAccent,
            background = c.background, onBackground = c.text, surface = c.surface, onSurface = c.text,
            surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.textSecondary, surfaceContainer = c.surface,
            surfaceContainerHigh = c.surfaceRaised, surfaceContainerHighest = c.surfaceRaised,
            error = c.emergency, onError = c.onEmergency, outline = c.divider, outlineVariant = c.divider,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, secondary = c.accent, onSecondary = c.onAccent,
            background = c.background, onBackground = c.text, surface = c.surface, onSurface = c.text,
            surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.textSecondary, surfaceContainer = c.surface,
            surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surfaceRaised,
            error = c.emergency, onError = c.onEmergency, outline = c.divider, outlineVariant = c.divider,
        )
    }
    val m3Type = Typography(
        displayLarge = type.numeral, headlineLarge = type.largeTitle, headlineMedium = type.title,
        titleLarge = type.title, titleMedium = type.headline, titleSmall = type.callout,
        bodyLarge = type.body, bodyMedium = type.callout, bodySmall = type.footnote,
        labelLarge = type.callout, labelMedium = type.caption, labelSmall = type.caption,
    )
    CompositionLocalProvider(LocalHavenColors provides c, LocalHavenType provides type) {
        MaterialTheme(
            colorScheme = scheme,
            typography = m3Type,
            shapes = Shapes(small = ControlShape, medium = CardShape, large = CardShape),
            content = content,
        )
    }
}
