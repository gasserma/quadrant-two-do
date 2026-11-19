package io.github.gasserma.quadranttwodo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * The four tables. They use the same schema as Regularly-format backup files (see BackupFormat),
 * so backups move between the two without conversion. Dates are ISO `YYYY-MM-DD` text, kept as raw
 * strings here so imported rows round-trip unchanged; they're parsed when mapping to the domain model.
 *
 * There are no foreign keys, because imported backups may contain orphaned rows.
 */

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    /** Days, at least 1. (Backup files may contain 0; import skips those tasks.) */
    @ColumnInfo(name = "period") val period: Int,
    @ColumnInfo(name = "firstdue") val firstDue: String? = null,
    /** Copy of the newest log entry's date, maintained by [LogDao]. */
    @ColumnInfo(name = "lastperformed") val lastPerformed: String? = null,
    // Notification columns are unused (Quadrant Two Do has no reminders) but kept so that old
    // backups load and export losslessly.
    @ColumnInfo(name = "lastnotified") val lastNotified: String? = null,
    @ColumnInfo(name = "created", defaultValue = "''") val created: String = "",
    @ColumnInfo(name = "details") val details: String = "",
    @ColumnInfo(name = "notifications_enabled") val notificationsEnabled: Int = 0,
    @ColumnInfo(name = "notifications_time") val notificationsTime: String? = null,
    @ColumnInfo(name = "notifications_period") val notificationsPeriod: Int? = null,
)

@Entity(tableName = "log", indices = [Index("taskid")])
data class LogEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Long = 0,
    @ColumnInfo(name = "taskid") val taskId: Long,
    @ColumnInfo(name = "entrydate", defaultValue = "''") val entryDate: String = "",
    @ColumnInfo(name = "note") val note: String = "",
)

/*
 * Tags are not used by Quadrant Two Do. The tables are kept, hidden, so backups import and export
 * without losing anything.
 */

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Long = 0,
    @ColumnInfo(name = "tagname") val tagName: String,
    @ColumnInfo(name = "position") val position: Int,
)

@Entity(tableName = "tasktags", indices = [Index("taskid"), Index("tagid")])
data class TaskTagEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Long = 0,
    @ColumnInfo(name = "taskid") val taskId: Long,
    @ColumnInfo(name = "tagid") val tagId: Long,
)
