package io.github.gasserma.quadranttwodo.data

import androidx.test.core.app.ApplicationProvider
import io.github.gasserma.quadranttwodo.BLUEPRINT_NOW
import io.github.gasserma.quadranttwodo.copyRegularlyDb
import io.github.gasserma.quadranttwodo.core.rankTasks
import io.github.gasserma.quadranttwodo.fixedClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** Checks the data layer and core logic against a real backup file (the sample regularly.db). */
@RunWith(RobolectricTestRunner::class)
class RegularlyDbTest {

    private lateinit var db: QuadrantTwoDoDatabase
    private lateinit var repo: TaskRepository

    @Before fun setUp() {
        db = QuadrantTwoDoDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = TaskRepository(db, fixedClock())
    }

    @After fun tearDown() = db.close()

    @Test fun backupMatchesDescription() {
        val s = BackupFormat.read(copyRegularlyDb())
        assertThat(s.userVersion).isEqualTo(8)
        assertThat(s.tasks).hasSize(22)
        assertThat(s.log).hasSize(7188)
        assertThat(s.tags).isEmpty()
        assertThat(s.taskTags).isEmpty()
        assertThat(s.log.all { it.note.isEmpty() }).isTrue()
        assertThat(s.tasks.none { it.period == 0 }).isTrue()
        assertThat(s.tasks.map { it.period }.min()).isEqualTo(1)
        assertThat(s.tasks.map { it.period }.max()).isEqualTo(365)
        assertThat(s.tasks.count { it.firstDue != null }).isEqualTo(8)
        assertThat(s.log.minOf { it.entryDate }).isEqualTo("2016-03-29")
        assertThat(s.log.maxOf { it.entryDate }).isEqualTo("2025-12-30")
        // In the backup itself, lastperformed equals each task's newest log entry.
        val newest = s.log.groupBy { it.taskId }.mapValues { e -> e.value.maxOf { it.entryDate } }
        s.tasks.forEach { assertThat(it.lastPerformed).isEqualTo(newest[it.id]) }
    }

    @Test fun importKeepsLastPerformedInSync() = runTest {
        val result = repo.importBackup(copyRegularlyDb())
        assertThat(result.skippedTasks).isEqualTo(0)
        val snapshot = result.imported
        val tasks = db.taskDao().getAll()
        assertThat(tasks).hasSize(22)
        assertThat(db.logDao().count()).isEqualTo(7188)
        for (t in tasks) {
            val newest = db.logDao().getForTask(t.id).firstOrNull()?.entryDate
            assertThat(t.lastPerformed).isEqualTo(newest)
        }
        assertThat(tasks.sortedBy { it.id }).isEqualTo(snapshot.tasks.sortedBy { it.id })
    }

    @Test fun expectedOrderAndUrgencyAsOf20261118() = runTest {
        repo.importBackup(copyRegularlyDb())
        val ranked = rankTasks(repo.observeTasks().first(), BLUEPRINT_NOW)

        val top3 = ranked.take(3)
        assertThat(top3.map { it.task.name })
            .containsExactly("Floss", "Gym", "Stick to Cut and Record")
        top3.forEach { assertThat(it.urgency!!).isWithin(0.01).of(322.12) }
        // Daily tasks tie on urgency and due date, so the name decides.
        assertThat(top3.map { it.task.name })
            .containsExactly("Floss", "Gym", "Stick to Cut and Record").inOrder()

        val last = ranked.last()
        assertThat(last.task.name).isEqualTo("Clean Grill")
        assertThat(last.task.period).isEqualTo(365)
        assertThat(last.nextDue).isEqualTo(LocalDate.parse("2026-11-27"))
        assertThat(last.urgency!!).isWithin(0.01).of(-0.02)

        // Urgency is non-increasing down the list.
        ranked.zipWithNext().forEach { (a, b) -> assertThat(a.urgency!!).isAtLeast(b.urgency!!) }
    }
}
