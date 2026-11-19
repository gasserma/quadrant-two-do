package io.github.gasserma.quadranttwodo.data

import androidx.test.core.app.ApplicationProvider
import io.github.gasserma.quadranttwodo.fixedClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class DaoTest {

    private lateinit var db: QuadrantTwoDoDatabase
    private lateinit var repo: TaskRepository
    private fun d(s: String): LocalDate = LocalDate.parse(s)

    @Before fun setUp() {
        db = QuadrantTwoDoDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = TaskRepository(db, fixedClock())
    }

    @After fun tearDown() = db.close()

    private suspend fun lastPerformed(id: Long) = db.taskDao().get(id)!!.lastPerformed

    @Test fun createdIsToday() = runTest {
        val id = repo.createTask("Water plants", 7, null, "")
        assertThat(db.taskDao().get(id)!!.created).isEqualTo("2026-11-18")
        assertThat(lastPerformed(id)).isNull()
    }

    @Test fun lastPerformedFollowsTheLog() = runTest {
        val id = repo.createTask("T", 7, null, "")
        val a = repo.addLogEntry(id, d("2026-11-01"))
        assertThat(lastPerformed(id)).isEqualTo("2026-11-01")
        val b = repo.addLogEntry(id, d("2026-11-10"))
        repo.addLogEntry(id, d("2026-11-05")) // older entry doesn't move it back
        assertThat(lastPerformed(id)).isEqualTo("2026-11-10")

        repo.deleteLogEntries(listOf(b)) // delete newest → falls back
        assertThat(lastPerformed(id)).isEqualTo("2026-11-05")

        repo.updateLogEntry(a, id, d("2026-11-12"), "moved") // edit a date
        assertThat(lastPerformed(id)).isEqualTo("2026-11-12")

        repo.clearLog(id) // empty log → NULL
        assertThat(lastPerformed(id)).isNull()
    }

    @Test fun deletingLastEntryClearsLastPerformed() = runTest {
        val id = repo.createTask("T", 3, null, "")
        val e = repo.addLogEntry(id, d("2026-11-17"))
        repo.deleteLogEntries(listOf(e))
        assertThat(lastPerformed(id)).isNull()
    }

    @Test fun bulkLogDoneUsesToday() = runTest {
        val a = repo.createTask("A", 1, null, "")
        val b = repo.createTask("B", 30, null, "")
        repo.logDone(listOf(a, b))
        assertThat(lastPerformed(a)).isEqualTo("2026-11-18")
        assertThat(lastPerformed(b)).isEqualTo("2026-11-18")
    }

    @Test fun updateTaskDoesNotTouchLastPerformed() = runTest {
        val id = repo.createTask("T", 7, null, "")
        repo.addLogEntry(id, d("2026-11-10"))
        repo.updateTask(id, "Renamed", 14, d("2026-12-01"), "notes")
        val t = db.taskDao().get(id)!!
        assertThat(t.name).isEqualTo("Renamed")
        assertThat(t.period).isEqualTo(14)
        assertThat(t.firstDue).isEqualTo("2026-12-01")
        assertThat(t.details).isEqualTo("notes")
        assertThat(t.lastPerformed).isEqualTo("2026-11-10")
    }

    /** Tags are hidden, but imported tag links must not outlive their task. */
    @Test fun deletingTaskRemovesLogAndHiddenTagLinks() = runTest {
        db.backupDao().replaceAll(
            BackupContents(
                userVersion = 8,
                tasks = listOf(
                    TaskEntity(id = 1, name = "Keep", period = 7, created = "2026-01-01"),
                    TaskEntity(id = 2, name = "Gone", period = 7, created = "2026-01-01"),
                ),
                log = listOf(
                    LogEntity(id = 1, taskId = 1, entryDate = "2026-11-01"),
                    LogEntity(id = 2, taskId = 2, entryDate = "2026-11-01"),
                ),
                tags = listOf(TagEntity(id = 1, tagName = "Home", position = 0)),
                taskTags = listOf(TaskTagEntity(id = 1, taskId = 1, tagId = 1), TaskTagEntity(id = 2, taskId = 2, tagId = 1)),
            ),
        )

        repo.deleteTasks(listOf(2L))

        assertThat(db.taskDao().getAll().map { it.name }).containsExactly("Keep")
        assertThat(db.logDao().count()).isEqualTo(1)
        val links = db.openHelper.readableDatabase.query("SELECT taskid FROM tasktags").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }
        assertThat(links).containsExactly(1L)
    }

    /** Tasks with period 0 (allowed in backup files) are skipped on import, with their log and tag links. */
    @Test fun importSkipsPeriodZeroTasks() {
        val backup = BackupContents(
            userVersion = 8,
            tasks = listOf(
                TaskEntity(id = 1, name = "Weekly", period = 7, created = "2026-01-01"),
                TaskEntity(id = 2, name = "Irregular", period = 0, created = "2026-01-01"),
            ),
            log = listOf(
                LogEntity(id = 1, taskId = 1, entryDate = "2026-11-01"),
                LogEntity(id = 2, taskId = 2, entryDate = "2026-11-02"),
            ),
            tags = listOf(TagEntity(id = 1, tagName = "Home", position = 0)),
            taskTags = listOf(TaskTagEntity(id = 1, taskId = 1, tagId = 1), TaskTagEntity(id = 2, taskId = 2, tagId = 1)),
        )
        val kept = backup.withoutPeriodZeroTasks()
        assertThat(kept.tasks.map { it.name }).containsExactly("Weekly")
        assertThat(kept.log.map { it.id }).containsExactly(1L)
        assertThat(kept.taskTags.map { it.id }).containsExactly(1L)
        assertThat(kept.tags).isEqualTo(backup.tags)
    }

    @Test fun periodBelowOneIsRejected() = runTest {
        assertThat(runCatching { repo.createTask("Bad", 0, null, "") }.isFailure).isTrue()
        val id = repo.createTask("Good", 1, null, "")
        assertThat(runCatching { repo.updateTask(id, "Good", 0, null, "") }.isFailure).isTrue()
        assertThat(db.taskDao().get(id)!!.period).isEqualTo(1)
    }
}
