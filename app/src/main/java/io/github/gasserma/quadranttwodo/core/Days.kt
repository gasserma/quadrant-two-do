package io.github.gasserma.quadranttwodo.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

const val DAYS_PER_WEEK = 7
const val DAYS_PER_YEAR = 365

/** A number of days split into years (365 days), weeks and days, for the period dialog: 401 → 1, 5, 1. */
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

/**
 * How every day count is shown: plain days, "401 days", "1 day". Only the period dialog splits a
 * count into years, weeks and days (see [YearsWeeksDays]).
 */
fun formatDays(days: Long): String = if (days == 1L) "1 day" else "$days days"

/**
 * Relative-day wording: "today", "tomorrow", "yesterday", "in 9 days", "322 days ago".
 * [days] is positive for the future.
 */
fun relativeDays(days: Long): String = when {
    days == 0L -> "today"
    days == 1L -> "tomorrow"
    days == -1L -> "yesterday"
    days > 1 -> "in ${formatDays(days)}"
    else -> "${formatDays(-days)} ago"
}

/** The due column of a task: "due today", "due in 9 days", "due 322 days ago"; empty if no due date. */
fun Task.dueLabel(today: LocalDate): String =
    nextDue()?.let { "due ${relativeDays(ChronoUnit.DAYS.between(today, it))}" } ?: ""
