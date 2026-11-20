package io.github.gasserma.quadranttwodo.core

/** Light or dark: follow the phone's setting, or always one or the other. */
enum class ThemeMode(val id: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    /** Whether the dark theme applies, given whether the phone itself is in dark mode. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: System
    }
}

/** The user's choices, from the Settings screen. [id]s are stored, so they must never change. */
data class Settings(
    val theme: ThemeMode = ThemeMode.System,
    /** Opacity of the widget's background panel, in percent (0 = clear, 100 = solid). */
    val widgetOpacity: Int = DEFAULT_WIDGET_OPACITY,
    val scheme: UrgencyScheme = UrgencyScheme.Default,
) {
    companion object {
        /** Enough backing for the text to read over most wallpapers, in either theme. */
        const val DEFAULT_WIDGET_OPACITY = 70
    }
}
