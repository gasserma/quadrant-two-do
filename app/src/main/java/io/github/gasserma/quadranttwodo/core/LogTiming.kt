package io.github.gasserma.quadranttwodo.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How a log entry relates to the one before it: the first entry is the baseline; after that the
 * gap since the previous entry is compared with the period ("on schedule", "2d late", "1d early").
 */
sealed interface LogTiming {
    data object Baseline : LogTiming
    data object OnSchedule : LogTiming
    data class Early(val days: Long) : LogTiming
    data class Late(val days: Long) : LogTiming

    /** The entry has no readable date (possible only in imported data). */
    data object Undated : LogTiming
}

/**
 * Timing for each date in [dates] (any order), returned in the same order as the input.
 * Each entry is compared with the previous dated entry in chronological order; null dates are
 * [LogTiming.Undated] and are left out of the comparison.
 */
fun logTimings(dates: List<LocalDate?>, period: Int): List<LogTiming> {
    val result = MutableList<LogTiming>(dates.size) { LogTiming.Undated }
    val dated = dates.withIndex()
        .mapNotNull { (index, date) -> date?.let { index to it } }
        .sortedWith(compareBy({ it.second }, { it.first }))
    dated.forEachIndexed { rank, (index, date) ->
        result[index] = if (rank == 0) {
            LogTiming.Baseline
        } else {
            val delta = ChronoUnit.DAYS.between(dated[rank - 1].second, date) - period
            when {
                delta == 0L -> LogTiming.OnSchedule
                delta > 0 -> LogTiming.Late(delta)
                else -> LogTiming.Early(-delta)
            }
        }
    }
    return result
}

fun LogTiming.format(): String = when (this) {
    LogTiming.Baseline -> "baseline"
    LogTiming.OnSchedule -> "on schedule"
    is LogTiming.Early -> "${formatDays(days)} early"
    is LogTiming.Late -> "${formatDays(days)} late"
    LogTiming.Undated -> ""
}
