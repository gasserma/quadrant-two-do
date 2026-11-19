package io.github.gasserma.quadranttwodo.ui.list

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.gasserma.quadranttwodo.AppContainer
import io.github.gasserma.quadranttwodo.core.Task
import io.github.gasserma.quadranttwodo.core.dueLabel
import io.github.gasserma.quadranttwodo.core.rankTasks
import io.github.gasserma.quadranttwodo.core.toIso
import io.github.gasserma.quadranttwodo.data.NotABackupException
import io.github.gasserma.quadranttwodo.data.PendingImport
import io.github.gasserma.quadranttwodo.ui.common.nowTicker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.util.Locale

data class TaskRow(val task: Task, val urgency: Double?, val dueText: String)

data class TaskListState(
    val loaded: Boolean = false,
    val rows: List<TaskRow> = emptyList(),
)

/** A confirmation the screen should show. */
sealed interface ListDialog {
    data class ConfirmLog(val ids: Set<Long>) : ListDialog
    data class ConfirmDelete(val ids: Set<Long>) : ListDialog
    data class ConfirmImport(val pending: PendingImport) : ListDialog
}

/** "Quadrant Two Do", 2026-11-19 → "quadrant-two-do-2026-11-19.db" (lower-case, spaces become hyphens). */
fun backupFileName(appName: String, date: LocalDate): String {
    val slug = appName.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "backup" }
    return "$slug-${date.toIso()}.db"
}

/** One-shot results shown in a snackbar. */
sealed interface ListMessage {
    data class Imported(val tasks: Int, val logEntries: Int, val skippedTasks: Int) : ListMessage
    data class Merged(val matchedTasks: Int, val addedTasks: Int, val addedLogEntries: Int, val skippedTasks: Int) : ListMessage
    data class Exported(val tasks: Int, val logEntries: Int) : ListMessage
    data object NotABackup : ListMessage
    data object ImportFailed : ListMessage
    data object ExportFailed : ListMessage
}

class TaskListViewModel(
    private val container: AppContainer,
    /** Provides the bundled debug backup; null in release builds. */
    private val testDataSource: (suspend () -> File?)? = null,
) : ViewModel() {

    private val repo = container.repository

    val state: StateFlow<TaskListState> = combine(repo.observeTasks(), nowTicker(container.clock)) { tasks, now ->
        val today = now.toLocalDate()
        TaskListState(loaded = true, rows = rankTasks(tasks, now).map { TaskRow(it.task, it.urgency, it.task.dueLabel(today)) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListState())

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    private val _dialog = MutableStateFlow<ListDialog?>(null)
    val dialog: StateFlow<ListDialog?> = _dialog.asStateFlow()

    private val _messages = Channel<ListMessage>(Channel.BUFFERED)
    val messages: Flow<ListMessage> = _messages.receiveAsFlow()

    val hasTestData: Boolean get() = testDataSource != null

    // Selection

    fun toggleSelected(id: Long) = _selection.update { if (id in it) it - id else it + id }

    fun clearSelection() = _selection.update { emptySet() }

    // Log and delete, each confirmed first

    fun requestLog(ids: Set<Long>) {
        if (ids.isNotEmpty()) _dialog.value = ListDialog.ConfirmLog(ids)
    }

    fun requestDelete(ids: Set<Long>) {
        if (ids.isNotEmpty()) _dialog.value = ListDialog.ConfirmDelete(ids)
    }

    fun dismissDialog() {
        (_dialog.value as? ListDialog.ConfirmImport)?.let { container.backup.discard(it.pending) }
        _dialog.value = null
    }

    /** Confirms the open dialog; for an import that is its default action, Merge. */
    fun confirmDialog() {
        when (val d = _dialog.value) {
            is ListDialog.ConfirmLog -> finish { repo.logDone(d.ids) }
            is ListDialog.ConfirmDelete -> finish { repo.deleteTasks(d.ids) }
            is ListDialog.ConfirmImport -> confirmImport(replace = false)
            null -> Unit
        }
    }

    private fun finish(action: suspend () -> Unit) {
        _dialog.value = null
        viewModelScope.launch {
            action()
            clearSelection()
        }
    }

    // Import and export

    /** Suggested name for an exported backup: the app's name and today's date, e.g. "quadrant-two-do-2026-11-19.db". */
    fun suggestedBackupName(appName: String): String = backupFileName(appName, repo.today())

    /** A file picked for import: read and check it, then ask what to do. */
    fun onImportPicked(uri: Uri) = prepareImport { container.backup.prepareImport(uri) }

    /** Debug builds: import the bundled regularly.db, through the same dialog as a real import. */
    fun requestLoadTestData() {
        val source = testDataSource ?: return
        prepareImport { source()?.let { container.backup.prepareImport(it, "sample backup") } }
    }

    private fun prepareImport(read: suspend () -> PendingImport?) {
        viewModelScope.launch {
            try {
                read()?.let { _dialog.value = ListDialog.ConfirmImport(it) }
            } catch (e: NotABackupException) {
                Log.w(TAG, "Rejected import", e)
                _messages.send(ListMessage.NotABackup)
            } catch (e: Exception) {
                Log.w(TAG, "Import failed", e)
                _messages.send(ListMessage.ImportFailed)
            }
        }
    }

    /** Runs the pending import: merge into the current data, or [replace] everything. */
    fun confirmImport(replace: Boolean) {
        val pending = (_dialog.value as? ListDialog.ConfirmImport)?.pending ?: return
        _dialog.value = null
        viewModelScope.launch {
            val message = try {
                if (replace) {
                    val r = container.backup.import(pending)
                    ListMessage.Imported(r.imported.tasks.size, r.imported.log.size, r.skippedTasks)
                } else {
                    val m = container.backup.merge(pending)
                    ListMessage.Merged(m.matchedTasks, m.addedTasks, m.addedLogEntries, m.skippedTasks)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Import failed", e)
                ListMessage.ImportFailed
            }
            clearSelection()
            _messages.send(message)
        }
    }

    /** A destination picked for export. */
    fun onExportPicked(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                val b = container.backup.export(uri)
                ListMessage.Exported(b.tasks.size, b.log.size)
            } catch (e: Exception) {
                Log.w(TAG, "Export failed", e)
                ListMessage.ExportFailed
            }
            _messages.send(message)
        }
    }

    private companion object {
        const val TAG = "TaskList"
    }
}
