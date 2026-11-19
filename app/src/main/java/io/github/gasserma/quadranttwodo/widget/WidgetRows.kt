package io.github.gasserma.quadranttwodo.widget

import io.github.gasserma.quadranttwodo.core.Task
import io.github.gasserma.quadranttwodo.core.dueLabel
import io.github.gasserma.quadranttwodo.core.rankTasks
import io.github.gasserma.quadranttwodo.core.urgencyColor
import java.time.LocalDateTime

/** One widget row: name on the left, due text on the right in its urgency colour. */
data class WidgetRow(val id: Long, val name: String, val dueText: String, val dueColor: Int)

/** Every task, sorted like the app. */
fun buildWidgetRows(tasks: List<Task>, now: LocalDateTime): List<WidgetRow> {
    val today = now.toLocalDate()
    return rankTasks(tasks, now).map { r ->
        WidgetRow(
            id = r.task.id,
            name = r.task.name,
            dueText = r.task.dueLabel(today),
            dueColor = urgencyColor(r.urgency),
        )
    }
}
