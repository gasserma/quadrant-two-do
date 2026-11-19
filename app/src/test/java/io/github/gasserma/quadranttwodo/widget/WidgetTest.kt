package io.github.gasserma.quadranttwodo.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import io.github.gasserma.quadranttwodo.BLUEPRINT_NOW
import io.github.gasserma.quadranttwodo.copyRegularlyDb
import io.github.gasserma.quadranttwodo.core.Task
import io.github.gasserma.quadranttwodo.core.urgencyColor
import io.github.gasserma.quadranttwodo.data.BackupFormat
import io.github.gasserma.quadranttwodo.data.toDomain
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class WidgetTest {

    private val backupTasks by lazy { BackupFormat.read(copyRegularlyDb()).tasks.map { it.toDomain() } }

    @Test fun rowsFromBackupMatchTheApp() {
        val rows = buildWidgetRows(backupTasks, BLUEPRINT_NOW)
        assertThat(rows).hasSize(22)
        assertThat(rows.take(3).map { it.name }).containsExactly("Floss", "Gym", "Stick to Cut and Record").inOrder()
        assertThat(rows.first().dueText).isEqualTo("due 46w ago")
        assertThat(rows.first().dueColor).isEqualTo(urgencyColor(322.0)) // coral: long overdue
        assertThat(rows.last().name).isEqualTo("Clean Grill")
        assertThat(rows.last().dueText).isEqualTo("due in 1w 2d")
    }

    @Test fun neverDoneTaskIsDueOnePeriodAfterCreation() {
        val today = BLUEPRINT_NOW.toLocalDate()
        val tasks = listOf(Task(1, "New", 7, created = today.minusDays(2)))
        val rows = buildWidgetRows(tasks, BLUEPRINT_NOW)
        assertThat(rows.single().dueText).isEqualTo("due in 5d")
    }

    @Test fun dailyRefreshAimsJustAfterSix() {
        fun delay(t: String) = WidgetRefresher.delayUntilNextDueTime(LocalDateTime.parse(t))
        assertThat(delay("2026-11-18T05:00")).isEqualTo(Duration.ofMinutes(61))
        assertThat(delay("2026-11-18T08:48")).isEqualTo(Duration.ofHours(21).plusMinutes(13))
        assertThat(delay("2026-11-18T06:01")).isEqualTo(Duration.ofDays(1))
    }

    @Test fun rendersRowsAndEmptyState() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        val rows = buildWidgetRows(backupTasks, BLUEPRINT_NOW)
        provideComposable { WidgetContent(rows.take(5), "No tasks yet") }
        onNode(hasTestTag("row:Floss")).assertExists()
        onNode(hasText("Floss")).assertExists()
        onAllNodes(hasText("due 46w ago")).assertCountEquals(4) // Floss, Gym, Stick to Cut, Gratitude
    }

    @Test fun rendersEmptyText() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        provideComposable { WidgetContent(emptyList(), "No tasks yet") }
        onNode(hasText("No tasks yet")).assertExists()
    }
}
