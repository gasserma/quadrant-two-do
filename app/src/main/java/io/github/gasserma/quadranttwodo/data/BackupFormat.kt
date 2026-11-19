package io.github.gasserma.quadranttwodo.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.core.database.sqlite.transaction
import io.github.gasserma.quadranttwodo.core.MIN_PERIOD
import java.io.File
import java.io.IOException

/** The contents of a backup file: the four tables. */
data class BackupContents(
    val userVersion: Int,
    val tasks: List<TaskEntity>,
    val log: List<LogEntity>,
    val tags: List<TagEntity>,
    val taskTags: List<TaskTagEntity>,
)

/** The file isn't a backup (not SQLite, or missing the `tasks`/`log` tables). */
class NotABackupException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * The backup file format: a plain SQLite file with four tables and `user_version` 8.
 *
 * It is the format of the backup files the Regularly app writes (`regularly.db`), so backups can
 * move between the two apps. The schema was taken from such a backup file itself (its
 * `sqlite_master` table and `PRAGMA user_version`). Exports reproduce it exactly, including the
 * `tags`/`tasktags` tables and the notification columns that Quadrant Two Do doesn't use.
 */
object BackupFormat {
    const val USER_VERSION = 8

    /** The CREATE TABLE statements, verbatim as they appear in a backup file's `sqlite_master`. */
    private val SCHEMA = listOf(
        "CREATE TABLE tasks (_id integer primary key autoincrement, name text not null, " +
            "period integer not null, firstdue text, lastperformed text, lastnotified text, " +
            "created text not null default '', details text not null, notifications_enabled integer not null, " +
            "notifications_time text, notifications_period integer)",
        "CREATE TABLE log (_id integer primary key autoincrement, taskid integer not null, " +
            "entrydate text not null default '', note text not null)",
        "CREATE TABLE tags (_id integer primary key autoincrement, tagname text not null, position integer not null)",
        "CREATE TABLE tasktags (_id integer primary key autoincrement, taskid integer not null, tagid integer not null)",
    )

    // Reading

    /**
     * Reads a backup file. Missing optional columns are tolerated so older backups load too.
     * @throws NotABackupException if [file] isn't a readable backup.
     */
    fun read(file: File): BackupContents = try {
        SQLiteDatabase.openDatabase(
            file.path,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        ).use { db ->
            for (required in listOf("tasks", "log")) {
                if (!db.hasTable(required)) throw NotABackupException("Not a backup: no '$required' table")
            }
            BackupContents(
                userVersion = db.version,
                tasks = db.rows("tasks") { c ->
                    TaskEntity(
                        id = c.rowId(),
                        name = c.string("name") ?: "",
                        period = c.long("period")?.toInt() ?: 0,
                        firstDue = c.string("firstdue"),
                        lastPerformed = c.string("lastperformed"),
                        lastNotified = c.string("lastnotified"),
                        created = c.string("created") ?: "",
                        details = c.string("details") ?: "",
                        notificationsEnabled = c.long("notifications_enabled")?.toInt() ?: 0,
                        notificationsTime = c.string("notifications_time"),
                        notificationsPeriod = c.long("notifications_period")?.toInt(),
                    )
                },
                log = db.rows("log") { c ->
                    LogEntity(
                        id = c.rowId(),
                        taskId = c.long("taskid") ?: 0,
                        entryDate = c.string("entrydate") ?: "",
                        note = c.string("note") ?: "",
                    )
                },
                tags = db.rows("tags") { c ->
                    TagEntity(id = c.rowId(), tagName = c.string("tagname") ?: "", position = c.long("position")?.toInt() ?: 0)
                },
                taskTags = db.rows("tasktags") { c ->
                    TaskTagEntity(id = c.rowId(), taskId = c.long("taskid") ?: 0, tagId = c.long("tagid") ?: 0)
                },
            )
        }
    } catch (e: SQLiteException) {
        throw NotABackupException("Not a backup: ${e.message}", e)
    }

    private fun SQLiteDatabase.hasTable(table: String): Boolean =
        rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use { it.moveToFirst() }

    private fun <T> SQLiteDatabase.rows(table: String, map: (Cursor) -> T): List<T> {
        if (!hasTable(table)) return emptyList()
        return rawQuery("SELECT * FROM $table ORDER BY _id", null).use { c ->
            buildList { while (c.moveToNext()) add(map(c)) }
        }
    }

    private fun Cursor.string(column: String): String? {
        val i = getColumnIndex(column)
        return if (i < 0 || isNull(i)) null else getString(i)
    }

    private fun Cursor.long(column: String): Long? {
        val i = getColumnIndex(column)
        return if (i < 0 || isNull(i)) null else getLong(i)
    }

    private fun Cursor.rowId(): Long = long("_id") ?: throw NotABackupException("Not a backup: a row has no _id")

    // Writing

    /** Writes [backup] to a new file at [file], replacing it. */
    fun write(file: File, backup: BackupContents) {
        file.delete()
        File(file.path + "-journal").delete()
        // Default flags also create android_metadata, which backup files written by Regularly contain too.
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.transaction {
                SCHEMA.forEach(::execSQL)
                backup.tasks.forEach { insertOrThrow("tasks", null, it.toValues()) }
                backup.log.forEach { insertOrThrow("log", null, it.toValues()) }
                backup.tags.forEach { insertOrThrow("tags", null, it.toValues()) }
                backup.taskTags.forEach { insertOrThrow("tasktags", null, it.toValues()) }
            }
            db.version = USER_VERSION
        }
    }

    private fun TaskEntity.toValues() = ContentValues().apply {
        put("_id", id)
        put("name", name)
        put("period", period)
        put("firstdue", firstDue)
        put("lastperformed", lastPerformed)
        put("lastnotified", lastNotified)
        put("created", created)
        put("details", details)
        put("notifications_enabled", notificationsEnabled)
        put("notifications_time", notificationsTime)
        put("notifications_period", notificationsPeriod)
    }

    private fun LogEntity.toValues() = ContentValues().apply {
        put("_id", id)
        put("taskid", taskId)
        put("entrydate", entryDate)
        put("note", note)
    }

    private fun TagEntity.toValues() = ContentValues().apply {
        put("_id", id)
        put("tagname", tagName)
        put("position", position)
    }

    private fun TaskTagEntity.toValues() = ContentValues().apply {
        put("_id", id)
        put("taskid", taskId)
        put("tagid", tagId)
    }
}

/**
 * Drops tasks with period 0 (allowed in the file format, not in Quadrant Two Do) along with their log
 * entries and tag links.
 */
fun BackupContents.withoutPeriodZeroTasks(): BackupContents {
    val keep = tasks.filter { it.period >= MIN_PERIOD }
    val ids = keep.mapTo(HashSet()) { it.id }
    return copy(
        tasks = keep,
        log = log.filter { it.taskId in ids },
        taskTags = taskTags.filter { it.taskId in ids },
    )
}
