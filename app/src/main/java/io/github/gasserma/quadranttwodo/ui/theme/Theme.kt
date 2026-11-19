package io.github.gasserma.quadranttwodo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.gasserma.quadranttwodo.core.Palette

/*
 * "Dusk": a blue-slate dark theme with a sea-glass teal accent. The shared base colours are in
 * core/Palette.kt and the urgency colours (teal → amber → coral) in core/UrgencyColor.kt, so the
 * widget can use them too.
 */
private val DuskTeal = Color(Palette.TEAL)
private val DuskTealDeep = Color(0xFF2F7F75)
private val Slate = Color(Palette.SLATE)
private val Ink = Color(Palette.INK)
private val InkMuted = Color(Palette.INK_MUTED)

private val DuskColors = darkColorScheme(
    primary = DuskTeal,
    onPrimary = Color(0xFF0B1F1C),
    primaryContainer = DuskTealDeep,
    onPrimaryContainer = Ink,
    secondary = DuskTeal,
    onSecondary = Color(0xFF0B1F1C),
    secondaryContainer = Color(0xFF203A3A),
    onSecondaryContainer = Ink,
    background = Slate,
    onBackground = Ink,
    surface = Slate,
    onSurface = Ink,
    surfaceVariant = Color(0xFF232A35),
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color(0xFF0E1117),
    surfaceContainerLow = Color(0xFF161B23),
    surfaceContainer = Color(0xFF1B212B),
    surfaceContainerHigh = Color(0xFF212833),
    surfaceContainerHighest = Color(0xFF28303C),
    outline = Color(0xFF3A4352),
    outlineVariant = Color(0xFF262D38),
    error = Color(0xFFF08A7E),
)

/** The system font, light for body text: 18sp list rows, 14sp secondary text. */
private val DuskTypography: Typography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Light),
        titleMedium = base.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Light),
        bodyLarge = base.bodyLarge.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Light),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Light),
        bodySmall = base.bodySmall.copy(fontWeight = FontWeight.Light),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium),
    )
}

/** Small all-caps section headings ("PERIOD", "LOG"). */
val SectionHeading = TextStyle(
    fontSize = 12.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.sp,
    color = DuskTeal,
)

@Composable
fun QuadrantTwoDoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DuskColors, typography = DuskTypography, content = content)
}
