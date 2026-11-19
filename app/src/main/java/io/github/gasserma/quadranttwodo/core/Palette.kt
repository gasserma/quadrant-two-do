package io.github.gasserma.quadranttwodo.core

/**
 * The "Dusk" colours shared by the app theme (ui/theme), the widget and the urgency colours, as
 * plain ARGB ints so RemoteViews can use them too. XML resources that need them (the widget
 * preview, the window background) mirror them in res/values/colors.xml.
 */
object Palette {
    /** Blue-slate background. */
    const val SLATE: Int = 0xFF12161D.toInt()

    /** Sea-glass teal accent. */
    const val TEAL: Int = 0xFF5EC4B6.toInt()

    /** Main text. */
    const val INK: Int = 0xFFE8ECF1.toInt()

    /** Secondary text. */
    const val INK_MUTED: Int = 0xFF8B95A5.toInt()
}
