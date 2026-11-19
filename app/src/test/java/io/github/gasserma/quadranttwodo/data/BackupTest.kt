package io.github.gasserma.quadranttwodo.data

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Looper
import io.github.gasserma.quadranttwodo.AppContainer
import io.github.gasserma.quadranttwodo.TestSupport
import io.github.gasserma.quadranttwodo.copyRegularlyDb
import io.github.gasserma.quadranttwodo.ui.list.ListDialog
import io.github.gasserma.quadranttwodo.ui.list.ListMessage
import io.github.gasserma.quadranttwodo.ui.list.TaskListViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference

/** Import and export of backup files (Regularly-compatible format). */
@RunWith(RobolectricTestRunner::class)
class BackupTest {

    private lateinit var container: AppContainer
    private val repo get() = container.repository

    @Before fun setUp() {
        container = TestSupport.installContainer()
    }

    private fun tempFile(name: String) = File.createTempFile(name, ".db").also { it.delete(); it.deleteOnExit() }

    private fun tableSql(file: File): Map<String, String> =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.rawQuery("SELECT name, sql FROM sqlite_master WHERE type = 'table'", null).use { c ->
                buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1) ?: "") }
            }
        }

    @Test fun importThenExportReproducesTheBackupExactly() = runTest {
        val original = copyRegularlyDb()
        repo.importBackup(original)
        val exported = tempFile("export")
        repo.exportBackup(exported)

        assertThat(BackupFormat.read(exported)).isEqualTo(BackupFormat.read(original))
    }

    @Test fun exportUsesTheSharedSchema() = runTest {
        val original = copyRegularlyDb()
        repo.importBackup(original)
        val exported = tempFile("export")
        repo.exportBackup(exported)

        val want = tableSql(original)
        val got = tableSql(exported)
        for (table in listOf("tasks", "log", "tags", "tasktags", "android_metadata")) {
            assertThat(got[table]).isEqualTo(want[table])
        }
        assertThat(BackupFormat.read(exported).userVersion).isEqualTo(BackupFormat.USER_VERSION)
    }

    @Test fun exportIncludesAppChangesAndHiddenTags() = runTest {
        container.repository.importBackup(copyRegularlyDb())
        db().backupDao().replaceAll(
            db().backupDao().readAll().let {
                it.copy(
                    tags = listOf(TagEntity(id = 1, tagName = "Home", position = 0)),
                    taskTags = listOf(TaskTagEntity(id = 1, taskId = it.tasks.first().id, tagId = 1)),
                )
            },
        )
        val id = repo.createTask("Water plants", 7, null, "on the balcony")
        repo.addLogEntry(id, LocalDate.parse("2026-11-17"), "all of them")

        val exported = tempFile("export")
        repo.exportBackup(exported)
        val back = BackupFormat.read(exported)

        assertThat(back.tasks).hasSize(23)
        val task = back.tasks.single { it.id == id }
        assertThat(task.name).isEqualTo("Water plants")
        assertThat(task.period).isEqualTo(7)
        assertThat(task.details).isEqualTo("on the balcony")
        assertThat(task.created).isEqualTo("2026-11-18")
        assertThat(task.lastPerformed).isEqualTo("2026-11-17")
        assertThat(back.log.filter { it.taskId == id }.map { it.entryDate to it.note })
            .containsExactly("2026-11-17" to "all of them")
        assertThat(back.tags.map { it.tagName }).containsExactly("Home")
        assertThat(back.taskTags).hasSize(1)
    }

    @Test fun nonBackupFilesAreRejectedAndNothingChanges() = runTest {
        repo.importBackup(copyRegularlyDb())

        val garbage = tempFile("garbage").apply { writeText("definitely not sqlite") }
        val otherDb = tempFile("other").also { f ->
            SQLiteDatabase.openOrCreateDatabase(f, null).use { it.execSQL("CREATE TABLE notes (x text)") }
        }
        for (f in listOf(garbage, otherDb)) {
            val e = runCatching { repo.importBackup(f) }.exceptionOrNull()
            assertThat(e).isInstanceOf(NotABackupException::class.java)
        }
        assertThat(repo.observeTasks().first()).hasSize(22)
    }

    @Test fun backupManagerMovesFilesThroughUris() = runTest {
        val source = copyRegularlyDb()
        val pending = container.backup.prepareImport(Uri.fromFile(source))
        assertThat(pending.preview.imported.tasks).hasSize(22)
        assertThat(repo.observeTasks().first()).isEmpty() // nothing imported before confirming

        container.backup.import(pending)
        assertThat(repo.observeTasks().first()).hasSize(22)

        val target = tempFile("picked").apply { writeText("old contents to be truncated") }
        container.backup.export(Uri.fromFile(target))
        assertThat(BackupFormat.read(target)).isEqualTo(BackupFormat.read(source))
    }

    @Test fun listScreenImportAsksFirstThenMergesByDefault() {
        val keep = runBlocking { repo.createTask("Phone-only task", 3, null, "") }
        val vm = TaskListViewModel(container)
        vm.onImportPicked(Uri.fromFile(copyRegularlyDb()))
        val dialog = awaitValue { vm.dialog.value as? ListDialog.ConfirmImport }
        assertThat(dialog.pending.preview.imported.tasks).hasSize(22)
        assertThat(dialog.pending.mergePreview).isEqualTo(MergeSummary(0, 22, 7188, 0))
        assertThat(runBlocking { repo.observeTasks().first() }).hasSize(1) // nothing changed yet

        vm.confirmDialog() // the default action
        assertThat(awaitMessage(vm)).isEqualTo(ListMessage.Merged(matchedTasks = 0, addedTasks = 22, addedLogEntries = 7188, skippedTasks = 0))
        val tasks = runBlocking { repo.observeTasks().first() }
        assertThat(tasks).hasSize(23)
        assertThat(tasks.map { it.id }).contains(keep)
    }

    @Test fun listScreenReplaceAllOverwrites() {
        runBlocking { repo.createTask("Phone-only task", 3, null, "") }
        val vm = TaskListViewModel(container)
        vm.onImportPicked(Uri.fromFile(copyRegularlyDb()))
        awaitValue { vm.dialog.value as? ListDialog.ConfirmImport }

        vm.confirmImport(replace = true)
        assertThat(awaitMessage(vm)).isEqualTo(ListMessage.Imported(tasks = 22, logEntries = 7188, skippedTasks = 0))
        val tasks = runBlocking { repo.observeTasks().first() }
        assertThat(tasks).hasSize(22)
        assertThat(tasks.none { it.name == "Phone-only task" }).isTrue()
    }

    @Test fun mergeKeepsPhoneSettingsAddsBackupLogsAndRecomputesLastDone() = runTest {
        val backupFile = copyRegularlyDb()
        repo.importBackup(backupFile)
        val gym = repo.observeTasks().first().single { it.name == "Gym" }
        // On the phone: change Gym's period, log it today, and delete its newest backup entry.
        repo.updateTask(gym.id, "Gym", 3, null, "phone details")
        val newestBackupEntry = TestSupport.database.logDao().getForTask(gym.id).first()
        repo.deleteLogEntries(listOf(newestBackupEntry.id))
        repo.addLogEntry(gym.id, LocalDate.parse("2026-11-18"))
        // Phone-only task.
        repo.createTask("Stretch", 1, null, "")

        val summary = repo.mergeBackup(backupFile)

        assertThat(summary).isEqualTo(MergeSummary(matchedTasks = 22, addedTasks = 0, addedLogEntries = 1, skippedTasks = 0))
        val after = repo.observeTasks().first()
        assertThat(after).hasSize(23)
        val gymAfter = after.single { it.name == "Gym" }
        assertThat(gymAfter.period).isEqualTo(3)                // phone setting kept
        assertThat(gymAfter.details).isEqualTo("phone details")
        assertThat(gymAfter.lastPerformed).isEqualTo(LocalDate.parse("2026-11-18"))
        // Gym's log is now exactly the backup's (including the entry deleted on the phone, which
        // comes back since deletions can't be detected, and the backup's own repeated entries)
        // plus today's phone entry.
        val backupGymDates = BackupFormat.read(backupFile).let { s ->
            val id = s.tasks.single { it.name == "Gym" }.id
            s.log.filter { it.taskId == id }.map { it.entryDate }
        }
        val dates = TestSupport.database.logDao().getForTask(gym.id).map { it.entryDate }
        assertThat(dates).containsExactlyElementsIn(backupGymDates + "2026-11-18")
        assertThat(dates).contains(newestBackupEntry.entryDate)
        assertThat(TestSupport.database.logDao().count()).isEqualTo(7188 + 1)
    }

    @Test fun mergingTheSameBackupTwiceChangesNothing() = runTest {
        val backupFile = copyRegularlyDb()
        repo.mergeBackup(backupFile)
        val first = TestSupport.database.backupDao().readAll()
        val again = repo.mergeBackup(backupFile)
        assertThat(again).isEqualTo(MergeSummary(22, 0, 0, 0))
        assertThat(TestSupport.database.backupDao().readAll()).isEqualTo(first)
    }

    @Test fun listScreenReportsNonBackup() {
        val vm = TaskListViewModel(container)
        vm.onImportPicked(Uri.fromFile(tempFile("garbage").apply { writeText("nope") }))
        assertThat(awaitMessage(vm)).isEqualTo(ListMessage.NotABackup)
        assertThat(vm.dialog.value).isNull()
    }

    private fun db(): QuadrantTwoDoDatabase = TestSupport.database

    /** Runs the main looper until [read] returns non-null (ViewModel work hops between threads). */
    private fun <T : Any> awaitValue(read: () -> T?): T {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            read()?.let { return it }
            Thread.sleep(20)
        }
        error("Timed out waiting for a value")
    }

    /** The next one-shot message from the ViewModel. */
    private fun awaitMessage(vm: TaskListViewModel): ListMessage {
        val result = AtomicReference<ListMessage?>(null)
        val job = CoroutineScope(Dispatchers.IO).launch {
            withTimeout(10_000) { result.set(vm.messages.first()) }
        }
        try {
            return awaitValue { result.get() }
        } finally {
            job.cancel()
        }
    }
}
