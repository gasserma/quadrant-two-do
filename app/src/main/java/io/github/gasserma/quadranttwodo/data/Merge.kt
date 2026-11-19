package io.github.gasserma.quadranttwodo.data

import java.util.Locale

/**
 * Merging a backup into the existing data.
 *
 * Neither side has stable ids or edit times, so tasks are matched by **name** (trimmed,
 * case-insensitive):
 *  - a task on both sides keeps the phone's name, period, first due date and details; the
 *    backup's log entries are added to it, except ones the phone already has (same date and
 *    note, counted, so genuine repeated entries are kept);
 *  - a task only in the backup is added, with its log entries;
 *  - a task only on the phone is left alone;
 *  - period-0 tasks in the backup are skipped, as on a full import;
 *  - hidden tags are combined by name, with their task links.
 *
 * Deletions can't be detected, so anything deleted on the phone but still in the backup comes
 * back. `lastperformed` is recomputed from the merged log afterwards.
 */
data class MergePlan(
    /** Backup tasks that matched a phone task. */
    val matchedTasks: Int,
    /** Backup tasks with no match, to be inserted (ids assigned on insert). */
    val newTasks: List<NewTask>,
    /** Backup log entries added to phone tasks (taskId = phone task id, id = 0). */
    val logsForExisting: List<LogEntity>,
    /** Tags (by name) that don't exist yet on the phone. */
    val newTagNames: List<String>,
    /** Tag links to add to phone tasks: phone task id → tag name. */
    val tagLinksForExisting: List<Pair<Long, String>>,
    val skippedTasks: Int,
) {
    data class NewTask(val task: TaskEntity, val logs: List<LogEntity>, val tagNames: Set<String>)

    val addedTasks: Int get() = newTasks.size
    val addedLogEntries: Int get() = logsForExisting.size + newTasks.sumOf { it.logs.size }
}

/** Short summary of a merge, for the confirmation dialog and the result message. */
data class MergeSummary(val matchedTasks: Int, val addedTasks: Int, val addedLogEntries: Int, val skippedTasks: Int)

fun MergePlan.summary() = MergeSummary(matchedTasks, addedTasks, addedLogEntries, skippedTasks)

internal fun mergeKey(name: String) = name.trim().lowercase(Locale.ROOT)

fun planMerge(phone: BackupContents, backupAll: BackupContents): MergePlan {
    val backup = backupAll.withoutPeriodZeroTasks()
    val skipped = backupAll.tasks.size - backup.tasks.size

    // Phone side: first task per name wins if the phone itself has duplicate names.
    val phoneByKey = HashMap<String, TaskEntity>()
    phone.tasks.sortedBy { it.id }.forEach { phoneByKey.putIfAbsent(mergeKey(it.name), it) }
    // Log entries are matched as a multiset: for each (task, date, note) the result keeps as many
    // copies as the side with more of them. So merging into an empty phone equals a full import,
    // merging the same backup twice adds nothing, and the backup's own duplicates survive.
    val phoneLogCounts = HashMap<Triple<Long, String, String>, Int>()
    phone.log.forEach { phoneLogCounts.merge(Triple(it.taskId, it.entryDate, it.note), 1, Int::plus) }
    val phoneTagByKey = phone.tags.associateBy { mergeKey(it.tagName) }
    val phoneTagNameById = phone.tags.associate { it.id to it.tagName }
    val phoneLinks = phone.taskTags.mapNotNullTo(HashSet()) { l ->
        phoneTagNameById[l.tagId]?.let { l.taskId to mergeKey(it) }
    }

    val backupTagNameById = backup.tags.associate { it.id to it.tagName }
    val backupLogsByTask = backup.log.sortedBy { it.id }.groupBy { it.taskId }
    val backupTagsByTask = backup.taskTags.groupBy({ it.taskId }, { backupTagNameById[it.tagId] })

    var matched = 0
    val newTasks = LinkedHashMap<String, MergePlan.NewTask>() // keyed by name; later duplicates fold in
    val logsForExisting = ArrayList<LogEntity>()
    val linksForExisting = LinkedHashSet<Pair<Long, String>>()
    val tagNamesUsed = LinkedHashMap<String, String>() // key → display name, in first-seen order

    for (task in backup.tasks.sortedBy { it.id }) {
        val key = mergeKey(task.name)
        val logs = backupLogsByTask[task.id].orEmpty()
        val tagNames = backupTagsByTask[task.id].orEmpty().filterNotNull()
        tagNames.forEach { tagNamesUsed.putIfAbsent(mergeKey(it), it) }
        val target = phoneByKey[key]
        if (target != null) {
            matched++
            for (log in logs) {
                val k = Triple(target.id, log.entryDate, log.note)
                val onPhone = phoneLogCounts[k] ?: 0
                if (onPhone > 0) phoneLogCounts[k] = onPhone - 1 else logsForExisting += log.copy(id = 0, taskId = target.id)
            }
            for (t in tagNames) {
                if ((target.id to mergeKey(t)) !in phoneLinks) linksForExisting += target.id to t
            }
        } else {
            // A later backup task with the same name folds into the first, with the same counting rule.
            val existing = newTasks[key]
            val have = HashMap<Pair<String, String>, Int>()
            existing?.logs?.forEach { have.merge(it.entryDate to it.note, 1, Int::plus) }
            val addLogs = logs.filter { log ->
                val k = log.entryDate to log.note
                val n = have[k] ?: 0
                if (n > 0) { have[k] = n - 1; false } else true
            }.map { it.copy(id = 0, taskId = 0) }
            newTasks[key] = if (existing == null) {
                MergePlan.NewTask(task.copy(id = 0, lastPerformed = null), addLogs, tagNames.toSet())
            } else {
                existing.copy(logs = existing.logs + addLogs, tagNames = existing.tagNames + tagNames)
            }
        }
    }

    return MergePlan(
        matchedTasks = matched,
        newTasks = newTasks.values.toList(),
        logsForExisting = logsForExisting,
        newTagNames = tagNamesUsed.filterKeys { it !in phoneTagByKey }.values.toList(),
        tagLinksForExisting = linksForExisting.toList(),
        skippedTasks = skipped,
    )
}
