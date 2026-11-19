package io.github.gasserma.quadranttwodo.ui.edit

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.gasserma.quadranttwodo.AppContainer
import io.github.gasserma.quadranttwodo.R
import io.github.gasserma.quadranttwodo.core.Task
import io.github.gasserma.quadranttwodo.core.format
import io.github.gasserma.quadranttwodo.core.logTimings
import io.github.gasserma.quadranttwodo.data.LogEntry
import io.github.gasserma.quadranttwodo.ui.common.nowTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime

/** The period a new task starts with: weekly. */
private const val NEW_TASK_PERIOD = 7

/** The editable fields, held locally until saved. */
data class EditForm(
    val name: String = "",
    val period: Int = NEW_TASK_PERIOD,
    val firstDue: LocalDate? = null,
    val details: String = "",
)

data class LogRow(val entry: LogEntry, val timing: String)

data class TaskEditState(
    val loaded: Boolean = false,
    val taskId: Long? = null,
    val form: EditForm = EditForm(),
    /** The form applied to the stored task, for derived values (next due, last done). */
    val preview: Task = Task(0, "", NEW_TASK_PERIOD),
    val log: List<LogRow> = emptyList(),
    val now: LocalDateTime = LocalDateTime.MIN,
    /** This screen was opened to create a task (it stays a "New task" screen until closed). */
    val openedAsNew: Boolean = false,
) {
    val today: LocalDate get() = now.toLocalDate()
}

sealed interface EditEvent {
    data object Close : EditEvent
    data class Message(@StringRes val text: Int) : EditEvent
}

sealed interface EditDialog {
    data object Period : EditDialog
    data object FirstDue : EditDialog
    data object PickLogDate : EditDialog
    data class EditLog(val entry: LogEntry) : EditDialog
    data object ConfirmDeleteTask : EditDialog
    data object ConfirmDiscard : EditDialog
    data class ConfirmDeleteLogs(val ids: Set<Long>) : EditDialog
    data object ConfirmClearLog : EditDialog
}

@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditViewModel(
    private val container: AppContainer,
    initialTaskId: Long?,
) : ViewModel() {

    private val repo = container.repository
    private val saveLock = Mutex()

    private val taskId = MutableStateFlow(initialTaskId)
    private val form = MutableStateFlow<EditForm?>(if (initialTaskId == null) EditForm() else null)

    private val _dialog = MutableStateFlow<EditDialog?>(null)
    val dialog: StateFlow<EditDialog?> = _dialog.asStateFlow()

    private val _logSelection = MutableStateFlow<Set<Long>>(emptySet())
    val logSelection: StateFlow<Set<Long>> = _logSelection.asStateFlow()

    private val _events = Channel<EditEvent>(Channel.BUFFERED)
    val events: Flow<EditEvent> = _events.receiveAsFlow()

    init {
        if (initialTaskId != null) {
            viewModelScope.launch {
                val t = repo.observeTask(initialTaskId).first()
                if (t == null) {
                    _events.send(EditEvent.Close)
                } else {
                    form.value = EditForm(t.name, t.period, t.firstDue, t.details)
                }
            }
        }
    }

    private val stored: Flow<Task?> = taskId.flatMapLatest { id -> id?.let(repo::observeTask) ?: flowOf(null) }
    private val log: Flow<List<LogEntry>> = taskId.flatMapLatest { id -> id?.let(repo::observeLog) ?: flowOf(emptyList()) }

    val state: StateFlow<TaskEditState> = combine(taskId, form, stored, log, nowTicker(container.clock)) { id, f, task, entries, now ->
        if (f == null) return@combine TaskEditState()
        val timings = logTimings(entries.map { it.date }, f.period)
        TaskEditState(
            loaded = true,
            openedAsNew = initialTaskId == null,
            taskId = id,
            form = f,
            preview = Task(
                id = id ?: 0,
                name = f.name,
                period = f.period,
                firstDue = f.firstDue,
                lastPerformed = task?.lastPerformed,
                created = task?.created ?: now.toLocalDate(),
            ),
            log = entries.zip(timings) { e, t -> LogRow(e, t.format()) },
            now = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskEditState())

    // Form edits

    fun setName(name: String) = form.update { it?.copy(name = name) }
    fun setDetails(details: String) = form.update { it?.copy(details = details) }

    fun setPeriod(period: Int) {
        form.update { it?.copy(period = period) }
        _dialog.value = null
        saveInBackground()
    }

    fun setFirstDue(date: LocalDate?) {
        form.update { it?.copy(firstDue = date) }
        _dialog.value = null
        saveInBackground()
    }

    // Dialogs

    fun showDialog(d: EditDialog) {
        _dialog.value = d
    }

    fun dismissDialog() {
        _dialog.value = null
    }

    // Saving

    /**
     * Writes the form. Creates the task on first save. Returns false (and says why) when the
     * name is blank.
     */
    private suspend fun save(): Boolean = saveLock.withLock {
        val f = form.value ?: return false
        val name = f.name.trim()
        if (name.isEmpty()) return false
        val id = taskId.value
        if (id == null) {
            taskId.value = repo.createTask(name, f.period, f.firstDue, f.details)
        } else {
            repo.updateTask(id, name, f.period, f.firstDue, f.details)
        }
        true
    }

    private suspend fun saveOrPrompt(): Boolean {
        if (save()) return true
        _events.send(EditEvent.Message(R.string.error_name_required))
        return false
    }

    private fun saveInBackground() {
        viewModelScope.launch { save() }
    }

    /** Autosave when the screen goes to the background. */
    fun onStop() = saveInBackground()

    /** Back/up: save and close; a blank new task asks to be discarded. */
    fun onBack() {
        viewModelScope.launch {
            val f = form.value
            when {
                f == null -> _events.send(EditEvent.Close)
                f.name.isNotBlank() -> {
                    save()
                    _events.send(EditEvent.Close)
                }
                taskId.value == null && f == EditForm() -> _events.send(EditEvent.Close)
                taskId.value == null -> _dialog.value = EditDialog.ConfirmDiscard
                else -> _events.send(EditEvent.Message(R.string.error_name_required))
            }
        }
    }

    fun discardNewTask() {
        _dialog.value = null
        viewModelScope.launch { _events.send(EditEvent.Close) }
    }

    fun deleteTask() {
        _dialog.value = null
        viewModelScope.launch {
            taskId.value?.let { repo.deleteTasks(listOf(it)) }
            _events.send(EditEvent.Close)
        }
    }

    // Log

    /** Adds a log entry on any date, past or future (e.g. logging something planned ahead). */
    fun addLogEntry(date: LocalDate) {
        _dialog.value = null
        viewModelScope.launch {
            if (!saveOrPrompt()) return@launch
            repo.addLogEntry(taskId.value!!, date)
        }
    }

    fun updateLogEntry(entry: LogEntry, date: LocalDate, note: String) {
        _dialog.value = null
        viewModelScope.launch { repo.updateLogEntry(entry.id, entry.taskId, date, note.trim()) }
    }

    fun toggleLogSelected(id: Long) = _logSelection.update { if (id in it) it - id else it + id }
    fun clearLogSelection() = _logSelection.update { emptySet() }

    fun deleteLogEntries(ids: Set<Long>) {
        _dialog.value = null
        clearLogSelection()
        viewModelScope.launch { repo.deleteLogEntries(ids) }
    }

    fun clearLog() {
        _dialog.value = null
        viewModelScope.launch { taskId.value?.let { repo.clearLog(it) } }
    }
}
