package io.github.gasserma.quadranttwodo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.gasserma.quadranttwodo.core.Palette
import io.github.gasserma.quadranttwodo.core.UrgencyScheme
import io.github.gasserma.quadranttwodo.core.urgencyColor

/*
 * Two themes that follow the system's dark-mode setting: "Dusk" (blue-slate, sea-glass teal) and
 * "Day" (off-white, deep teal). The shared base colours are in core/Palette.kt and the urgency
 * colours (teal → amber → coral) in core/UrgencyColor.kt, so the widget can use them too.
 */

private val DuskColors: ColorScheme = Palette.Dark.let { p ->
    darkColorScheme(
        primary = Color(p.accent),
        onPrimary = Color(0xFF0B1F1C),
        primaryContainer = Color(0xFF2F7F75),
        onPrimaryContainer = Color(p.text),
        secondary = Color(p.accent),
        onSecondary = Color(0xFF0B1F1C),
        secondaryContainer = Color(0xFF203A3A),
        onSecondaryContainer = Color(p.text),
        background = Color(p.background),
        onBackground = Color(p.text),
        surface = Color(p.background),
        onSurface = Color(p.text),
        surfaceVariant = Color(0xFF232A35),
        onSurfaceVariant = Color(p.textMuted),
        surfaceContainerLowest = Color(0xFF0E1117),
        surfaceContainerLow = Color(0xFF161B23),
        surfaceContainer = Color(0xFF1B212B),
        surfaceContainerHigh = Color(0xFF212833),
        surfaceContainerHighest = Color(0xFF28303C),
        outline = Color(0xFF3A4352),
        outlineVariant = Color(0xFF262D38),
        error = Color(0xFFF08A7E),
    )
}

private val DayColors: ColorScheme = Palette.Light.let { p ->
    lightColorScheme(
        primary = Color(p.accent),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFBFE8E2),
        onPrimaryContainer = Color(0xFF0B2F2A),
        secondary = Color(p.accent),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD3EEEA),
        onSecondaryContainer = Color(p.text),
        background = Color(p.background),
        onBackground = Color(p.text),
        surface = Color(p.background),
        onSurface = Color(p.text),
        surfaceVariant = Color(0xFFE4E8EE),
        onSurfaceVariant = Color(p.textMuted),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF1F3F6),
        surfaceContainer = Color(0xFFECEFF3),
        surfaceContainerHigh = Color(0xFFE6E9EE),
        surfaceContainerHighest = Color(0xFFE0E4EA),
        outline = Color(0xFFB8C0CC),
        outlineVariant = Color(0xFFDDE2E8),
        error = Color(0xFFB3261E),
    )
}

/** The system font, light for body text: 18sp list rows, 14sp secondary text. */
private val AppTypography: Typography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Light),
        titleMedium = base.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Light),
        bodyLarge = base.bodyLarge.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Light),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Light),
        bodySmall = base.bodySmall.copy(fontWeight = FontWeight.Light),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium),
    )
}

private val LocalPalette = staticCompositionLocalOf { Palette.Dark }
private val LocalScheme = staticCompositionLocalOf { UrgencyScheme.Default }

/** Small all-caps section headings ("PERIOD", "LOG"), in the accent colour. */
val SectionHeading: TextStyle
    @Composable @ReadOnlyComposable
    get() = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
    )

/**
 * The urgency colour (see core/UrgencyColor.kt) in the current theme, for the chosen colour scheme
 * or, to preview another one, [scheme].
 */
@Composable
@ReadOnlyComposable
fun urgencyColorOf(u: Double?, scheme: UrgencyScheme = LocalScheme.current): Color =
    Color(urgencyColor(u, LocalPalette.current, scheme))

/**
 * The app theme: [darkTheme] picks Dusk or Day, and [scheme] the urgency colours. Both come from
 * Settings (see QuadrantTwoDoRoot); the defaults are for previews and tests.
 */
@Composable
fun QuadrantTwoDoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    scheme: UrgencyScheme = UrgencyScheme.Default,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalPalette provides Palette.of(darkTheme), LocalScheme provides scheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DuskColors else DayColors,
            typography = AppTypography,
            content = content,
        )
    }
}
