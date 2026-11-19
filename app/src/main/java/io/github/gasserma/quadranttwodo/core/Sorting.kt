package io.github.gasserma.quadranttwodo.core

import java.time.LocalDate
import java.time.LocalDateTime

/** A task with its derived values computed once for a given moment. */
data class RankedTask(
    val task: Task,
    val nextDue: LocalDate?,
    val urgency: Double?,
)

/**
 * Most urgent first, then earliest due, then name (case-insensitively). Tasks without a due
 * date go last.
 */
private val order: Comparator<RankedTask> =
    compareByDescending<RankedTask, Double?>(nullsFirst()) { it.urgency }
        .thenBy(nullsLast()) { it.nextDue }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.task.name }

fun rankTasks(tasks: List<Task>, now: LocalDateTime): List<RankedTask> =
    tasks.map { RankedTask(it, it.nextDue(), it.urgency(now)) }.sortedWith(order)
