package io.github.gasserma.quadranttwodo.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gasserma.quadranttwodo.R
import io.github.gasserma.quadranttwodo.core.MIN_PERIOD
import io.github.gasserma.quadranttwodo.core.YearsWeeksDays
import io.github.gasserma.quadranttwodo.core.dueLabel
import io.github.gasserma.quadranttwodo.core.formatDays
import io.github.gasserma.quadranttwodo.core.longFormat
import io.github.gasserma.quadranttwodo.core.nextDue
import io.github.gasserma.quadranttwodo.core.relativeDays
import io.github.gasserma.quadranttwodo.core.shortFormat
import io.github.gasserma.quadranttwodo.core.urgency
import io.github.gasserma.quadranttwodo.core.weekdayCaps
import io.github.gasserma.quadranttwodo.data.LogEntry
import io.github.gasserma.quadranttwodo.ui.common.ConfirmDialog
import io.github.gasserma.quadranttwodo.ui.common.DatePickDialog
import io.github.gasserma.quadranttwodo.ui.theme.SectionHeading
import io.github.gasserma.quadranttwodo.ui.theme.urgencyColorOf
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(viewModel: TaskEditViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val logSelection by viewModel.logSelection.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                EditEvent.Close -> onClose()
                is EditEvent.Message -> snackbar.showSnackbar(resources.getString(e.text))
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStop() }

    val selecting = logSelection.isNotEmpty()
    BackHandler {
        if (selecting) viewModel.clearLogSelection() else viewModel.onBack()
    }

    Scaffold(
        topBar = {
            if (selecting) {
                LogSelectionTopBar(
                    count = logSelection.size,
                    onClose = viewModel::clearLogSelection,
                    onDelete = { viewModel.showDialog(EditDialog.ConfirmDeleteLogs(logSelection)) },
                )
            } else {
                EditTopBar(
                    isNew = state.openedAsNew,
                    hasLog = state.log.isNotEmpty(),
                    onBack = viewModel::onBack,
                    onDelete = { viewModel.showDialog(EditDialog.ConfirmDeleteTask) },
                    onClearLog = { viewModel.showDialog(EditDialog.ConfirmClearLog) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loaded) {
            TaskEditContent(state, logSelection, viewModel, Modifier.padding(padding))
        }
    }

    EditDialogs(dialog, state, viewModel)
}

/** Back, title, and for an existing task: delete, plus "Clear task log" when it has a log. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTopBar(
    isNew: Boolean,
    hasLog: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onClearLog: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
        },
        title = { Text(stringResource(if (isNew) R.string.title_new_task else R.string.title_task)) },
        actions = {
            if (!isNew) {
                IconButton(onClick = onDelete, modifier = Modifier.testTag("deleteTask")) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.action_delete_task))
                }
            }
            if (hasLog) {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_clear_log)) },
                        onClick = { menu = false; onClearLog() },
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogSelectionTopBar(count: Int, onClose: () -> Unit, onDelete: () -> Unit) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close_selection)) }
        },
        title = { Text(pluralStringResource(R.plurals.log_entries_selected, count, count)) },
        actions = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, stringResource(R.string.action_delete_log_entries))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    )
}

@Composable
private fun TaskEditContent(
    state: TaskEditState,
    logSelection: Set<Long>,
    viewModel: TaskEditViewModel,
    modifier: Modifier,
) {
    val form = state.form
    val preview = state.preview
    val today = state.today
    LazyColumn(modifier.fillMaxSize().testTag("editList")) {
        item {
            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.label_name)) },
                placeholder = { Text(stringResource(R.string.hint_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp).testTag("nameField"),
            )
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Field(
                    heading = stringResource(R.string.label_period),
                    value = formatDays(form.period.toLong()),
                    modifier = Modifier.weight(1f).testTag("periodField"),
                    onClick = { viewModel.showDialog(EditDialog.Period) },
                )
                Field(
                    heading = stringResource(R.string.label_next_due),
                    value = preview.nextDue()?.longFormat() ?: "—",
                    secondary = preview.dueLabel(today),
                    secondaryColor = urgencyColorOf(preview.urgency(state.now)),
                    // The first due date only matters until the task is first done.
                    hint = if (preview.lastPerformed == null) {
                        form.firstDue?.let { stringResource(R.string.label_first_due) + ": " + it.shortFormat() }
                            ?: stringResource(R.string.hint_first_due)
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f).testTag("nextDueField"),
                    onClick = { viewModel.showDialog(EditDialog.FirstDue) },
                )
            }
        }
        // A new task has no history yet: no Last done, no Log (Next due sets when it's first due).
        if (!state.openedAsNew) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    val last = preview.lastPerformed
                    Field(
                        heading = stringResource(R.string.label_last_done),
                        value = last?.longFormat() ?: stringResource(R.string.never),
                        secondary = last?.let { relativeDays(-ChronoUnit.DAYS.between(it, today)) },
                        modifier = Modifier.weight(1f).testTag("lastDoneField"),
                    )
                    Spacer(Modifier.weight(1f)) // keeps Last done aligned under Period
                }
            }
        }
        item {
            OutlinedTextField(
                value = form.details,
                onValueChange = viewModel::setDetails,
                label = { Text(stringResource(R.string.label_details)) },
                placeholder = { Text(stringResource(R.string.hint_details)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp).testTag("detailsField"),
            )
        }
        if (state.openedAsNew) return@LazyColumn
        item {
            LogHeader(today = today, onAdd = viewModel::addLogEntry, onPick = { viewModel.showDialog(EditDialog.PickLogDate) })
        }
        if (state.log.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.log_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 12.dp),
                )
            }
        }
        items(state.log, key = { it.entry.id }) { row ->
            LogRowItem(
                row = row,
                checked = row.entry.id in logSelection,
                onClick = {
                    if (logSelection.isNotEmpty()) viewModel.toggleLogSelected(row.entry.id)
                    else viewModel.showDialog(EditDialog.EditLog(row.entry))
                },
                onLongClick = { viewModel.toggleLogSelected(row.entry.id) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** A heading plus value, shown two to a row. */
@Composable
private fun Field(
    heading: String,
    value: String,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    secondaryColor: Color? = null,
    hint: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp, 10.dp),
    ) {
        Text(heading.uppercase(), style = SectionHeading)
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
        if (secondary != null) {
            Text(
                secondary,
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "LOG" heading with an "Add log entry" menu: Today, Yesterday or a picked date. */
@Composable
private fun LogHeader(today: LocalDate, onAdd: (LocalDate) -> Unit, onPick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
    ) {
        Text(stringResource(R.string.label_log).uppercase(), style = SectionHeading, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { menu = true }, modifier = Modifier.testTag("addLog")) {
                Icon(Icons.Filled.Add, null)
                Text(stringResource(R.string.action_add_log_entry), modifier = Modifier.padding(start = 4.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_log_today)) },
                    onClick = { menu = false; onAdd(today) },
                    modifier = Modifier.testTag("addLogToday"),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_log_yesterday)) },
                    onClick = { menu = false; onAdd(today.minusDays(1)) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_log_pick_date)) },
                    onClick = { menu = false; onPick() },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogRowItem(row: LogRow, checked: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val date = row.entry.date
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("log:${date ?: "?"}"),
    ) {
        Text(
            date?.weekdayCaps() ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(date?.shortFormat() ?: "?", style = MaterialTheme.typography.bodyLarge)
            if (row.entry.note.isNotBlank()) {
                Text(
                    row.entry.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(row.timing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EditDialogs(dialog: EditDialog?, state: TaskEditState, viewModel: TaskEditViewModel) {
    when (dialog) {
        EditDialog.Period -> PeriodDialog(state.form.period, viewModel::setPeriod, viewModel::dismissDialog)
        EditDialog.FirstDue -> DatePickDialog(
            initial = state.form.firstDue ?: state.preview.nextDue() ?: state.today,
            confirmLabel = stringResource(R.string.button_set_date),
            onConfirm = viewModel::setFirstDue,
            onDismiss = viewModel::dismissDialog,
            onClear = if (state.form.firstDue != null) ({ viewModel.setFirstDue(null) }) else null,
        )
        EditDialog.PickLogDate -> DatePickDialog(
            initial = state.today,
            confirmLabel = stringResource(R.string.button_add_date),
            onConfirm = viewModel::addLogEntry,
            onDismiss = viewModel::dismissDialog,
        )
        is EditDialog.EditLog -> EditLogDialog(dialog.entry, state.today, viewModel)
        EditDialog.ConfirmDeleteTask -> ConfirmDialog(
            title = stringResource(R.string.confirm_delete_task_title),
            text = stringResource(R.string.confirm_delete_task_prompt, state.form.name),
            confirmLabel = stringResource(R.string.button_delete),
            onConfirm = viewModel::deleteTask,
            onDismiss = viewModel::dismissDialog,
        )
        EditDialog.ConfirmDiscard -> ConfirmDialog(
            title = stringResource(R.string.title_new_task),
            text = stringResource(R.string.confirm_discard_prompt),
            confirmLabel = stringResource(R.string.button_discard),
            onConfirm = viewModel::discardNewTask,
            onDismiss = viewModel::dismissDialog,
        )
        is EditDialog.ConfirmDeleteLogs -> ConfirmDialog(
            title = stringResource(R.string.confirm_delete_log_entries_title),
            text = pluralStringResource(R.plurals.confirm_delete_log_entries_prompt, dialog.ids.size, dialog.ids.size),
            confirmLabel = stringResource(R.string.button_delete),
            onConfirm = { viewModel.deleteLogEntries(dialog.ids) },
            onDismiss = viewModel::dismissDialog,
        )
        EditDialog.ConfirmClearLog -> ConfirmDialog(
            title = stringResource(R.string.confirm_clear_log_title),
            text = pluralStringResource(R.plurals.confirm_clear_log_prompt, state.log.size, state.log.size),
            confirmLabel = stringResource(R.string.button_delete),
            onConfirm = viewModel::clearLog,
            onDismiss = viewModel::dismissDialog,
        )
        null -> Unit
    }
}

/**
 * The period as three boxes, years (365 days), weeks and days, pre-filled from the current
 * period. Empty boxes count as zero; the total must be at least one day.
 */
@Composable
private fun PeriodDialog(current: Int, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    val input = rememberSaveable(saver = PeriodInput.Saver) { PeriodInput.of(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.period_dialog_title)) },
        text = { PeriodFields(input) },
        confirmButton = {
            TextButton(onClick = { onSet(input.totalDays) }, enabled = input.isValid, modifier = Modifier.testTag("setPeriod")) {
                Text(stringResource(R.string.button_set))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) } },
    )
}

/** The text in the period dialog's three boxes. Empty boxes count as zero. */
@Stable
internal class PeriodInput(years: String, weeks: String, days: String) {
    var years by mutableStateOf(years)
    var weeks by mutableStateOf(weeks)
    var days by mutableStateOf(days)

    val totalDays: Int
        get() = YearsWeeksDays(years.toIntOrNull() ?: 0, weeks.toIntOrNull() ?: 0, days.toIntOrNull() ?: 0).totalDays

    val isValid: Boolean get() = totalDays >= MIN_PERIOD

    companion object {
        private fun box(n: Int) = if (n == 0) "" else n.toString()

        /** Pre-filled from a period, e.g. 401 → 1 / 5 / 1. */
        fun of(period: Int) = YearsWeeksDays.of(period).let { PeriodInput(box(it.years), box(it.weeks), box(it.days)) }

        val Saver = listSaver<PeriodInput, String>(
            save = { listOf(it.years, it.weeks, it.days) },
            restore = { PeriodInput(it[0], it[1], it[2]) },
        )
    }
}

/**
 * The period as three boxes, years (365 days), weeks and days, and the resulting total.
 * Kept separate from the dialog so it can be tested: under Robolectric, text fields inside a
 * dialog window never let Compose go idle.
 */
@Composable
internal fun PeriodFields(input: PeriodInput) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodBox(stringResource(R.string.years), input.years, "periodYears") { input.years = it }
            PeriodBox(stringResource(R.string.weeks), input.weeks, "periodWeeks") { input.weeks = it }
            PeriodBox(stringResource(R.string.days), input.days, "periodDays") { input.days = it }
        }
        Text(
            if (input.isValid) pluralStringResource(R.plurals.period_total_days, input.totalDays, input.totalDays)
            else stringResource(R.string.period_too_short),
            color = if (input.isValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("periodTotal"),
        )
    }
}

/** One number box with its caption above it (a fixed caption reads better than a floating label here). */
@Composable
private fun RowScope.PeriodBox(label: String, value: String, tag: String, onChange: (String) -> Unit) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.filter(Char::isDigit).take(3)) },
            placeholder = { Text("0") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
    }
}

@Composable
private fun EditLogDialog(entry: LogEntry, today: LocalDate, viewModel: TaskEditViewModel) {
    var date by rememberSaveable { mutableStateOf(entry.date ?: today) }
    var note by rememberSaveable { mutableStateOf(entry.note) }
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        title = { Text(stringResource(R.string.edit_log_entry_title)) },
        text = {
            Column {
                Text(stringResource(R.string.label_date).uppercase(), style = SectionHeading)
                TextButton(onClick = { picking = true }) { Text(date.longFormat()) }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.label_note)) },
                    placeholder = { Text(stringResource(R.string.hint_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.updateLogEntry(entry, date, note) }) { Text(stringResource(R.string.button_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { viewModel.deleteLogEntries(setOf(entry.id)) }) {
                    Text(stringResource(R.string.button_delete))
                }
                TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.button_cancel)) }
            }
        },
    )
    if (picking) {
        DatePickDialog(
            initial = date,
            confirmLabel = stringResource(R.string.button_set_date),
            onConfirm = { date = it; picking = false },
            onDismiss = { picking = false },
        )
    }
}
