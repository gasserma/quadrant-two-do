package io.github.gasserma.quadranttwodo.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A task as the app logic sees it. Dates are local dates without a time of day.
 *
 * Every task repeats: [period] is at least one day.
 */
data class Task(
    val id: Long,
    val name: String,
    /** Period in days, at least 1. */
    val period: Int,
    val firstDue: LocalDate? = null,
    val lastPerformed: LocalDate? = null,
    val created: LocalDate? = null,
    val details: String = "",
) {
    init {
        require(period >= MIN_PERIOD) { "Task period must be at least $MIN_PERIOD day, was $period" }
    }
}

const val MIN_PERIOD = 1

private const val MS_PER_DAY = 86_400_000.0

/** Tasks become due at 06:00 local time on their due day. */
val DUE_TIME: LocalTime = LocalTime.of(6, 0)

/**
 * Next due date: last performed + period, else first due, else created + period.
 * Null only for imported data that has none of those dates.
 */
fun Task.nextDue(): LocalDate? = when {
    lastPerformed != null -> lastPerformed.plusDays(period.toLong())
    firstDue != null -> firstDue
    else -> created?.plusDays(period.toLong())
}

/**
 * Urgency score: days past the due moment (due date at 06:00) divided by the period.
 * 0 = due now, -1 = just done, +1 = a whole period overdue. Null when there is no due date.
 * It changes continuously through the day.
 *
 * Uses local date-time arithmetic, so a daylight-saving change between the due date and now is
 * ignored (an error of at most an hour, i.e. 1/24 day divided by the period).
 */
fun Task.urgency(now: LocalDateTime): Double? {
    val due = nextDue() ?: return null
    val ms = Duration.between(due.atTime(DUE_TIME), now).toMillis()
    return ms / MS_PER_DAY / period
}
