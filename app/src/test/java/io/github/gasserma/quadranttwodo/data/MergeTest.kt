package io.github.gasserma.quadranttwodo.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Rules for merging a backup into the phone's data (pure planning; see Merge.kt). */
class MergeTest {

    private fun task(id: Long, name: String, period: Int = 7, details: String = "", firstDue: String? = null) =
        TaskEntity(id = id, name = name, period = period, created = "2026-01-01", details = details, firstDue = firstDue)

    private fun log(id: Long, taskId: Long, date: String, note: String = "") =
        LogEntity(id = id, taskId = taskId, entryDate = date, note = note)

    private fun snapshot(
        tasks: List<TaskEntity>,
        log: List<LogEntity> = emptyList(),
        tags: List<TagEntity> = emptyList(),
        taskTags: List<TaskTagEntity> = emptyList(),
    ) = BackupContents(8, tasks, log, tags, taskTags)

    @Test fun matchingIsByTrimmedCaseInsensitiveName() {
        val phone = snapshot(listOf(task(1, "Water plants")))
        val backup = snapshot(listOf(task(50, "  water PLANTS "), task(51, "Floss")))
        val plan = planMerge(phone, backup)
        assertThat(plan.matchedTasks).isEqualTo(1)
        assertThat(plan.newTasks.map { it.task.name }).containsExactly("Floss")
    }

    @Test fun phoneTaskWinsButBackupLogsAreAdded() {
        val phone = snapshot(
            listOf(task(1, "Gym", period = 2, details = "phone details", firstDue = "2026-02-01")),
            log = listOf(log(1, 1, "2026-03-01"), log(2, 1, "2026-03-03", "legs")),
        )
        val backup = snapshot(
            listOf(task(9, "Gym", period = 1, details = "backup details", firstDue = "2025-01-01")),
            log = listOf(
                log(1, 9, "2026-03-01"),          // duplicate: same date, same note
                log(2, 9, "2026-03-03", "arms"),  // same date, different note: kept
                log(3, 9, "2026-03-05"),          // new
            ),
        )
        val plan = planMerge(phone, backup)
        assertThat(plan.newTasks).isEmpty()
        assertThat(plan.logsForExisting.map { Triple(it.taskId, it.entryDate, it.note) })
            .containsExactly(Triple(1L, "2026-03-03", "arms"), Triple(1L, "2026-03-05", "")).inOrder()
        // The plan never touches the phone task's own fields.
        assertThat(plan.newTasks.none { it.task.name == "Gym" }).isTrue()
    }

    @Test fun backupOnlyTasksAreAddedWithTheirLogs() {
        val phone = snapshot(listOf(task(1, "Gym")))
        val backup = snapshot(
            listOf(task(7, "Dentist", period = 182, details = "Dr. Who")),
            log = listOf(log(1, 7, "2025-06-01"), log(2, 7, "2025-12-01")),
        )
        val n = planMerge(phone, backup).newTasks.single()
        assertThat(n.task.name).isEqualTo("Dentist")
        assertThat(n.task.period).isEqualTo(182)
        assertThat(n.task.details).isEqualTo("Dr. Who")
        assertThat(n.task.id).isEqualTo(0) // a fresh id is assigned on insert
        assertThat(n.logs.map { it.entryDate }).containsExactly("2025-06-01", "2025-12-01").inOrder()
    }

    @Test fun phoneOnlyTasksAreLeftAlone() {
        val phone = snapshot(listOf(task(1, "Phone only")), log = listOf(log(1, 1, "2026-01-02")))
        val plan = planMerge(phone, snapshot(emptyList()))
        assertThat(plan.summary()).isEqualTo(MergeSummary(0, 0, 0, 0))
    }

    @Test fun periodZeroBackupTasksAreSkipped() {
        val backup = snapshot(
            listOf(task(1, "Weekly"), task(2, "Irregular", period = 0)),
            log = listOf(log(1, 1, "2026-01-01"), log(2, 2, "2026-01-01")),
        )
        val plan = planMerge(snapshot(emptyList()), backup)
        assertThat(plan.skippedTasks).isEqualTo(1)
        assertThat(plan.newTasks.map { it.task.name }).containsExactly("Weekly")
        assertThat(plan.addedLogEntries).isEqualTo(1)
    }

    @Test fun duplicateNamesInTheBackupFoldIntoOneTask() {
        val backup = snapshot(
            listOf(task(1, "Floss"), task(2, "floss")),
            log = listOf(log(1, 1, "2026-01-01"), log(2, 2, "2026-01-01"), log(3, 2, "2026-01-02")),
        )
        val n = planMerge(snapshot(emptyList()), backup).newTasks.single()
        assertThat(n.logs.map { it.entryDate }).containsExactly("2026-01-01", "2026-01-02").inOrder()
    }

    @Test fun hiddenTagsAreCombinedByName() {
        val phone = snapshot(
            listOf(task(1, "Gym")),
            tags = listOf(TagEntity(1, "Health", 0)),
            taskTags = listOf(TaskTagEntity(1, 1, 1)),
        )
        val backup = snapshot(
            listOf(task(5, "Gym"), task(6, "Mow lawn")),
            tags = listOf(TagEntity(3, "health", 0), TagEntity(4, "Garden", 1)),
            taskTags = listOf(TaskTagEntity(1, 5, 3), TaskTagEntity(2, 5, 4), TaskTagEntity(3, 6, 4)),
        )
        val plan = planMerge(phone, backup)
        assertThat(plan.newTagNames).containsExactly("Garden")
        assertThat(plan.tagLinksForExisting).containsExactly(1L to "Garden") // Gym–Health already linked
        assertThat(plan.newTasks.single().tagNames).containsExactly("Garden")
    }

    @Test fun repeatedEntriesAreCountedNotCollapsed() {
        // The backup logged Gym twice on 03-01; the phone has it once → one more copy is added.
        val phone = snapshot(listOf(task(1, "Gym")), log = listOf(log(1, 1, "2026-03-01")))
        val backup = snapshot(
            listOf(task(9, "Gym"), task(10, "Floss")),
            log = listOf(log(1, 9, "2026-03-01"), log(2, 9, "2026-03-01"), log(3, 10, "2026-03-02"), log(4, 10, "2026-03-02")),
        )
        val plan = planMerge(phone, backup)
        assertThat(plan.logsForExisting.map { it.entryDate }).containsExactly("2026-03-01")
        assertThat(plan.newTasks.single().logs.map { it.entryDate }).containsExactly("2026-03-02", "2026-03-02")
    }

    @Test fun mergingIntoAnEmptyPhoneKeepsEverythingLikeReplace() {
        val backup = snapshot(
            listOf(task(1, "A"), task(2, "B", period = 0)),
            log = listOf(log(1, 1, "2026-01-01"), log(2, 1, "2026-01-01"), log(3, 2, "2026-01-01")),
        )
        val plan = planMerge(snapshot(emptyList()), backup)
        val replace = backup.withoutPeriodZeroTasks()
        assertThat(plan.addedTasks).isEqualTo(replace.tasks.size)
        assertThat(plan.addedLogEntries).isEqualTo(replace.log.size)
    }

    @Test fun mergingABackupIntoItselfAddsNothing() {
        val s = snapshot(
            listOf(task(1, "A"), task(2, "B")),
            log = listOf(log(1, 1, "2026-01-01"), log(2, 2, "2026-01-02", "n")),
        )
        assertThat(planMerge(s, s).summary()).isEqualTo(MergeSummary(matchedTasks = 2, addedTasks = 0, addedLogEntries = 0, skippedTasks = 0))
    }
}
