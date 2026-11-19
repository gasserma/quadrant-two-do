package io.github.gasserma.quadranttwodo.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Parses the ISO `YYYY-MM-DD` text the database stores. Blank or malformed → null. */
fun parseIsoDate(s: String?): LocalDate? {
    if (s.isNullOrBlank()) return null
    return try {
        LocalDate.parse(s.trim().take(10))
    } catch (_: DateTimeParseException) {
        null
    }
}

fun LocalDate.toIso(): String = format(DateTimeFormatter.ISO_LOCAL_DATE)

private fun ordinal(day: Int): String {
    val suffix = if (day in 11..13) "th" else when (day % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}

/** Long date in US English: "Wednesday, November 18th 2026". */
fun LocalDate.longFormat(locale: Locale = Locale.US): String =
    DateTimeFormatter.ofPattern("EEEE, MMMM", locale).format(this) +
        " " + ordinal(dayOfMonth) + " " + year

/** Compact date for log rows: "Nov 18, 2026". */
fun LocalDate.shortFormat(locale: Locale = Locale.US): String =
    DateTimeFormatter.ofPattern("MMM d, yyyy", locale).format(this)

/** Weekday caption for log rows: "WED". */
fun LocalDate.weekdayCaps(locale: Locale = Locale.US): String =
    DateTimeFormatter.ofPattern("EEE", locale).format(this).uppercase(locale)
