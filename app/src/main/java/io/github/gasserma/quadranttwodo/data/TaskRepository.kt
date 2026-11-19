package io.github.gasserma.quadranttwodo.data

import io.github.gasserma.quadranttwodo.core.MIN_PERIOD
import io.github.gasserma.quadranttwodo.core.Task
import io.github.gasserma.quadranttwodo.core.parseIsoDate
import io.github.gasserma.quadranttwodo.core.toIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.LocalDate

data class LogEntry(val id: Long, val taskId: Long, val date: LocalDate?, val note: String)

fun TaskEntity.toDomain() = Task(
    id = id,
    name = name,
    period = period,
    firstDue = parseIsoDate(firstDue),
    lastPerformed = parseIsoDate(lastPerformed),
    created = parseIsoDate(created),
    details = details,
)

fun LogEntity.toDomain() = LogEntry(id, taskId, parseIsoDate(entryDate), note)

/** The app's single entry point to stored data. */
class TaskRepository(
    private val db: QuadrantTwoDoDatabase,
    private val clock: Clock,
) {
    private val tasks = db.taskDao()
    private val log = db.logDao()

    fun today(): LocalDate = LocalDate.now(clock)

    // Tasks

    fun observeTasks(): Flow<List<Task>> = tasks.observeAll().map { it.map(TaskEntity::toDomain) }

    fun observeTask(id: Long): Flow<Task?> = tasks.observe(id).map { it?.toDomain() }

    suspend fun createTask(name: String, period: Int, firstDue: LocalDate?, details: String): Long {
        require(period >= MIN_PERIOD) { "period must be at least $MIN_PERIOD" }
        return tasks.insert(
            TaskEntity(
                name = name,
                period = period,
                firstDue = firstDue?.toIso(),
                created = today().toIso(),
                details = details,
            ),
        )
    }

    suspend fun updateTask(id: Long, name: String, period: Int, firstDue: LocalDate?, details: String) {
        require(period >= MIN_PERIOD) { "period must be at least $MIN_PERIOD" }
        tasks.updateFields(id, name, period, firstDue?.toIso(), details)
    }

    suspend fun deleteTasks(ids: Collection<Long>) = tasks.delete(ids.toList())

    // Log

    fun observeLog(taskId: Long): Flow<List<LogEntry>> =
        log.observeForTask(taskId).map { it.map(LogEntity::toDomain) }

    suspend fun logDone(taskIds: Collection<Long>, date: LocalDate = today()) =
        log.logDone(taskIds, date.toIso())

    suspend fun addLogEntry(taskId: Long, date: LocalDate, note: String = ""): Long =
        log.insert(LogEntity(taskId = taskId, entryDate = date.toIso(), note = note))

    suspend fun updateLogEntry(id: Long, taskId: Long, date: LocalDate, note: String) =
        log.update(LogEntity(id = id, taskId = taskId, entryDate = date.toIso(), note = note))

    suspend fun deleteLogEntries(ids: Collection<Long>) = log.delete(ids.toList())

    suspend fun clearLog(taskId: Long) = log.clear(taskId)

    // Backups (Regularly-compatible file format, see BackupFormat)

    /**
     * Reads a backup without changing anything, to show what an import would do.
     * @throws NotABackupException if the file isn't a backup.
     */
    suspend fun previewImport(file: File): ImportResult = withContext(Dispatchers.IO) {
        val all = BackupFormat.read(file)
        val kept = all.withoutPeriodZeroTasks()
        ImportResult(kept, skippedTasks = all.tasks.size - kept.tasks.size)
    }

    /**
     * Replaces everything with the contents of a backup file. Tasks with period 0 are skipped
     * along with their log entries and tag links.
     * @throws NotABackupException if the file isn't a backup; nothing changes then.
     */
    suspend fun importBackup(file: File): ImportResult {
        val result = previewImport(file)
        db.backupDao().replaceAll(result.imported)
        return result
    }

    /** What merging [file] into the current data would do (nothing is changed). See Merge.kt. */
    suspend fun previewMerge(file: File): MergeSummary {
        val backup = withContext(Dispatchers.IO) { BackupFormat.read(file) }
        return planMerge(db.backupDao().readAll(), backup).summary()
    }

    /**
     * Merges a backup into the current data: matching tasks (by name) keep the phone's
     * settings and gain the backup's log entries; other backup tasks are added. See Merge.kt.
     * @throws NotABackupException if the file isn't a backup; nothing changes then.
     */
    suspend fun mergeBackup(file: File): MergeSummary {
        val backup = withContext(Dispatchers.IO) { BackupFormat.read(file) }
        val plan = planMerge(db.backupDao().readAll(), backup)
        db.backupDao().merge(plan)
        return plan.summary()
    }

    /** Writes everything, including the unused tag tables, to [file] as a backup. */
    suspend fun exportBackup(file: File): BackupContents {
        val snapshot = db.backupDao().readAll()
        withContext(Dispatchers.IO) { BackupFormat.write(file, snapshot) }
        return snapshot
    }
}

/** What a "Replace all" import keeps from a backup, and how many period-0 tasks it skips. */
data class ImportResult(val imported: BackupContents, val skippedTasks: Int)
