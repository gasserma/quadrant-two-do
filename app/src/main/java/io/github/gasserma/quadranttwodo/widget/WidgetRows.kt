package io.github.gasserma.quadranttwodo.widget

import io.github.gasserma.quadranttwodo.core.Task
import io.github.gasserma.quadranttwodo.core.nextDue
import io.github.gasserma.quadranttwodo.core.rankTasks
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** One widget row: name on the left, due text on the right coloured by [urgency] (null: no due date). */
data class WidgetRow(val id: Long, val name: String, val dueText: String, val urgency: Double?)

/** Every task, sorted like the app. */
fun buildWidgetRows(tasks: List<Task>, now: LocalDateTime): List<WidgetRow> {
    val today = now.toLocalDate()
    return rankTasks(tasks, now).map { r ->
        WidgetRow(
            id = r.task.id,
            name = r.task.name,
            dueText = r.task.shortDueLabel(today),
            urgency = r.urgency,
        )
    }
}

/**
 * The widget's short due text: "2d late", "1d late", "today", "tomorrow", "in 29d". Empty if no due date.
 */
fun Task.shortDueLabel(today: LocalDate): String {
    val until = ChronoUnit.DAYS.between(today, nextDue() ?: return "")
    return when {
        until == 0L -> "today"
        until == 1L -> "tomorrow"
        until > 1 -> "in ${until}d"
        else -> "${-until}d late"
    }
}
