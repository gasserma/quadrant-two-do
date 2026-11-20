package io.github.gasserma.quadranttwodo.widget

import io.github.gasserma.quadranttwodo.core.Task
import io.github.gasserma.quadranttwodo.core.dueLabel
import io.github.gasserma.quadranttwodo.core.rankTasks
import java.time.LocalDateTime

/** One widget row: name on the left, due text on the right coloured by [urgency] (null: no due date). */
data class WidgetRow(val id: Long, val name: String, val dueText: String, val urgency: Double?)

/** Every task, sorted like the app. */
fun buildWidgetRows(tasks: List<Task>, now: LocalDateTime): List<WidgetRow> {
    val today = now.toLocalDate()
    return rankTasks(tasks, now).map { r ->
        WidgetRow(
            id = r.task.id,
            name = r.task.name,
            dueText = r.task.dueLabel(today),
            urgency = r.urgency,
        )
    }
}
