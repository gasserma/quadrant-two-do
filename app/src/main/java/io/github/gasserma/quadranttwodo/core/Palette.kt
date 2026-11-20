package io.github.gasserma.quadranttwodo.core

/**
 * The base colours of the dark and light themes, shared by the app theme (ui/theme), the widget
 * and the urgency colours, as plain ARGB ints so RemoteViews can use them too. XML resources that
 * need them (the widget preview, the window background) mirror them in res/values/colors.xml
 * (light) and res/values-night/colors.xml (dark).
 */
class Palette private constructor(
    val isDark: Boolean,
    /** Screen background. */
    val background: Int,
    /** Accent: headings, buttons, the logo. */
    val accent: Int,
    /** Main text. */
    val text: Int,
    /** Secondary text, and urgency colour for tasks with no due date. */
    val textMuted: Int,
    /** Urgency colours: scheme saturations are multiplied by this (capped at 1). */
    val urgencySaturationScale: Float,
    /** Urgency colours: the HSV value to start from before adjusting for contrast. */
    val urgencyValue: Float,
) {
    companion object {
        /** "Dusk": blue-slate background, sea-glass teal accent, bright urgency colours. */
        val Dark = Palette(
            isDark = true,
            background = 0xFF12161D.toInt(),
            accent = 0xFF5EC4B6.toInt(),
            text = 0xFFE8ECF1.toInt(),
            textMuted = 0xFF8B95A5.toInt(),
            urgencySaturationScale = 1f,
            urgencyValue = 0.93f,
        )

        /** "Day": off-white background, deep teal accent, richer and darker urgency colours. */
        val Light = Palette(
            isDark = false,
            background = 0xFFF7F8FA.toInt(),
            accent = 0xFF1A7F72.toInt(),
            text = 0xFF1A2029.toInt(),
            textMuted = 0xFF5B6575.toInt(),
            urgencySaturationScale = 1.5f,
            urgencyValue = 0.62f,
        )

        fun of(dark: Boolean): Palette = if (dark) Dark else Light
    }
}
