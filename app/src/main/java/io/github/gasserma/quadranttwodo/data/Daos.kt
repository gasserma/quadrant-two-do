package io.github.gasserma.quadranttwodo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TaskDao {
    @Query("SELECT * FROM tasks")
    abstract fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE _id = :id")
    abstract fun observe(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE _id = :id")
    abstract suspend fun get(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks")
    abstract suspend fun getAll(): List<TaskEntity>

    @Insert
    abstract suspend fun insert(task: TaskEntity): Long

    /**
     * Updates the editable fields only. `lastperformed` belongs to [LogDao] and is never
     * overwritten from a possibly stale copy of the task.
     */
    @Query(
        "UPDATE tasks SET name = :name, period = :period, firstdue = :firstDue, " +
            "details = :details WHERE _id = :id",
    )
    abstract suspend fun updateFields(
        id: Long,
        name: String,
        period: Int,
        firstDue: String?,
        details: String,
    )

    @Query("DELETE FROM log WHERE taskid IN (:ids)")
    protected abstract suspend fun deleteLogsFor(ids: List<Long>)

    @Query("DELETE FROM tasktags WHERE taskid IN (:ids)")
    protected abstract suspend fun deleteTagLinksFor(ids: List<Long>)

    @Query("DELETE FROM tasks WHERE _id IN (:ids)")
    protected abstract suspend fun deleteRows(ids: List<Long>)

    /** Deletes tasks together with their log entries and tag links. */
    @Transaction
    open suspend fun delete(ids: List<Long>) {
        if (ids.isEmpty()) return
        deleteLogsFor(ids)
        deleteTagLinksFor(ids)
        deleteRows(ids)
    }
}

/**
 * Log access. Every write keeps `tasks.lastperformed` equal to the newest log entry's date (or
 * NULL when the log is empty) inside the same transaction.
 */
@Dao
abstract class LogDao {
    @Query("SELECT * FROM log WHERE taskid = :taskId ORDER BY entrydate DESC, _id DESC")
    abstract fun observeForTask(taskId: Long): Flow<List<LogEntity>>

    @Query("SELECT * FROM log WHERE _id = :id")
    abstract suspend fun get(id: Long): LogEntity?

    @Query("SELECT * FROM log WHERE taskid = :taskId ORDER BY entrydate DESC, _id DESC")
    abstract suspend fun getForTask(taskId: Long): List<LogEntity>

    @Query("SELECT COUNT(*) FROM log")
    abstract suspend fun count(): Int

    @Query(
        "UPDATE tasks SET lastperformed = " +
            "(SELECT MAX(entrydate) FROM log WHERE taskid = :taskId) WHERE _id = :taskId",
    )
    protected abstract suspend fun syncLastPerformed(taskId: Long)

    @Insert
    protected abstract suspend fun insertRow(entry: LogEntity): Long

    @Update
    protected abstract suspend fun updateRow(entry: LogEntity)

    @Query("DELETE FROM log WHERE _id IN (:ids)")
    protected abstract suspend fun deleteRows(ids: List<Long>)

    @Query("SELECT DISTINCT taskid FROM log WHERE _id IN (:ids)")
    protected abstract suspend fun taskIdsOf(ids: List<Long>): List<Long>

    @Query("DELETE FROM log WHERE taskid = :taskId")
    protected abstract suspend fun deleteAllFor(taskId: Long)

    @Transaction
    open suspend fun insert(entry: LogEntity): Long {
        val id = insertRow(entry)
        syncLastPerformed(entry.taskId)
        return id
    }

    /** Logs each task as done on [date] (ISO text). */
    @Transaction
    open suspend fun logDone(taskIds: Collection<Long>, date: String) {
        for (taskId in taskIds) {
            insertRow(LogEntity(taskId = taskId, entryDate = date))
            syncLastPerformed(taskId)
        }
    }

    @Transaction
    open suspend fun update(entry: LogEntity) {
        val previousTask = get(entry.id)?.taskId
        updateRow(entry)
        syncLastPerformed(entry.taskId)
        if (previousTask != null && previousTask != entry.taskId) syncLastPerformed(previousTask)
    }

    @Transaction
    open suspend fun delete(ids: List<Long>) {
        if (ids.isEmpty()) return
        val tasks = taskIdsOf(ids)
        deleteRows(ids)
        tasks.forEach { syncLastPerformed(it) }
    }

    @Transaction
    open suspend fun clear(taskId: Long) {
        deleteAllFor(taskId)
        syncLastPerformed(taskId)
    }
}

/** Whole-database reads and writes for backup import and export. */
@Dao
abstract class BackupDao {
    @Query("SELECT * FROM tasks ORDER BY _id") protected abstract suspend fun allTasks(): List<TaskEntity>
    @Query("SELECT * FROM log ORDER BY _id") protected abstract suspend fun allLog(): List<LogEntity>
    @Query("SELECT * FROM tags ORDER BY _id") protected abstract suspend fun allTags(): List<TagEntity>
    @Query("SELECT * FROM tasktags ORDER BY _id") protected abstract suspend fun allTaskTags(): List<TaskTagEntity>

    /** Everything, read in one transaction so the export is consistent. */
    @Transaction
    open suspend fun readAll(): BackupContents = BackupContents(
        userVersion = BackupFormat.USER_VERSION,
        tasks = allTasks(),
        log = allLog(),
        tags = allTags(),
        taskTags = allTaskTags(),
    )

    @Query("DELETE FROM tasks") protected abstract suspend fun clearTasks()
    @Query("DELETE FROM log") protected abstract suspend fun clearLog()
    @Query("DELETE FROM tags") protected abstract suspend fun clearTags()
    @Query("DELETE FROM tasktags") protected abstract suspend fun clearTaskTags()

    @Insert protected abstract suspend fun insertTasks(rows: List<TaskEntity>)
    @Insert protected abstract suspend fun insertLog(rows: List<LogEntity>)
    @Insert protected abstract suspend fun insertTags(rows: List<TagEntity>)
    @Insert protected abstract suspend fun insertTaskTags(rows: List<TaskTagEntity>)

    @Query(
        "UPDATE tasks SET lastperformed = " +
            "(SELECT MAX(entrydate) FROM log WHERE log.taskid = tasks._id)",
    )
    protected abstract suspend fun syncAllLastPerformed()

    @Insert protected abstract suspend fun insertTask(row: TaskEntity): Long
    @Insert protected abstract suspend fun insertTag(row: TagEntity): Long
    @Query("SELECT MAX(position) FROM tags") protected abstract suspend fun maxTagPosition(): Int?

    /** Applies a [MergePlan] (see Merge.kt) in one transaction, then recomputes `lastperformed`. */
    @Transaction
    open suspend fun merge(plan: MergePlan) {
        val tagIds = allTags().associateTo(HashMap()) { mergeKey(it.tagName) to it.id }
        var position = (maxTagPosition() ?: -1) + 1
        for (name in plan.newTagNames) {
            tagIds[mergeKey(name)] = insertTag(TagEntity(tagName = name, position = position++))
        }
        fun links(taskId: Long, names: Collection<String>) =
            names.mapNotNull { tagIds[mergeKey(it)] }.distinct().map { TaskTagEntity(taskId = taskId, tagId = it) }

        for (n in plan.newTasks) {
            val id = insertTask(n.task.copy(id = 0))
            insertLog(n.logs.map { it.copy(id = 0, taskId = id) })
            insertTaskTags(links(id, n.tagNames))
        }
        insertLog(plan.logsForExisting.map { it.copy(id = 0) })
        insertTaskTags(plan.tagLinksForExisting.flatMap { (taskId, name) -> links(taskId, listOf(name)) })
        syncAllLastPerformed()
    }

    /** Replaces all data with [snapshot], keeping its row ids. */
    @Transaction
    open suspend fun replaceAll(snapshot: BackupContents) {
        clearTaskTags()
        clearTags()
        clearLog()
        clearTasks()
        insertTasks(snapshot.tasks)
        insertLog(snapshot.log)
        insertTags(snapshot.tags)
        insertTaskTags(snapshot.taskTags)
        syncAllLastPerformed()
    }
}
