package io.github.gasserma.quadranttwodo.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

const val DAYS_PER_WEEK = 7
const val DAYS_PER_YEAR = 365

/** A number of days split into years (365 days), weeks and days: 401 → 1y 5w 1d. */
data class YearsWeeksDays(val years: Int, val weeks: Int, val days: Int) {
    val totalDays: Int get() = years * DAYS_PER_YEAR + weeks * DAYS_PER_WEEK + days

    companion object {
        fun of(totalDays: Int): YearsWeeksDays {
            require(totalDays >= 0) { "negative day count: $totalDays" }
            val years = totalDays / DAYS_PER_YEAR
            val rest = totalDays % DAYS_PER_YEAR
            return YearsWeeksDays(years, rest / DAYS_PER_WEEK, rest % DAYS_PER_WEEK)
        }
    }
}

/** How every day count is shown: "1y 5w 1d", "2w", "3d"; zero parts are left out ("0d" for 0). */
fun formatDays(days: Long): String {
    val ywd = YearsWeeksDays.of(Math.toIntExact(days))
    val parts = buildList {
        if (ywd.years > 0) add("${ywd.years}y")
        if (ywd.weeks > 0) add("${ywd.weeks}w")
        if (ywd.days > 0) add("${ywd.days}d")
    }
    return parts.joinToString(" ").ifEmpty { "0d" }
}

/**
 * Relative-day wording: "today", "tomorrow", "yesterday", "in 1w 2d", "46w ago".
 * [days] is positive for the future.
 */
fun relativeDays(days: Long): String = when {
    days == 0L -> "today"
    days == 1L -> "tomorrow"
    days == -1L -> "yesterday"
    days > 1 -> "in ${formatDays(days)}"
    else -> "${formatDays(-days)} ago"
}

/** The due column of a task: "due today", "due in 1w 2d", "due 46w ago"; empty if no due date. */
fun Task.dueLabel(today: LocalDate): String =
    nextDue()?.let { "due ${relativeDays(ChronoUnit.DAYS.between(today, it))}" } ?: ""
