package io.github.gasserma.quadranttwodo.ui.list

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gasserma.quadranttwodo.R
import io.github.gasserma.quadranttwodo.core.filledQuadrants
import io.github.gasserma.quadranttwodo.core.urgencyColor
import io.github.gasserma.quadranttwodo.data.PendingImport
import io.github.gasserma.quadranttwodo.ui.common.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    viewModel: TaskListViewModel,
    onOpenTask: (Long) -> Unit,
    onNewTask: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val appName = stringResource(R.string.app_name)

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { m ->
            val text = when (m) {
                is ListMessage.Imported -> buildString {
                    append(resources.getString(R.string.import_replaced, m.tasks, m.logEntries))
                    if (m.skippedTasks > 0) {
                        append(" ")
                        append(resources.getQuantityString(R.plurals.import_skipped, m.skippedTasks, m.skippedTasks))
                    }
                }
                is ListMessage.Merged -> buildString {
                    append(resources.getString(R.string.import_merged, m.addedTasks, m.addedLogEntries, m.matchedTasks))
                    if (m.skippedTasks > 0) {
                        append(" ")
                        append(resources.getQuantityString(R.plurals.import_skipped, m.skippedTasks, m.skippedTasks))
                    }
                }
                is ListMessage.Exported -> resources.getString(R.string.export_done, m.tasks, m.logEntries)
                ListMessage.NotABackup -> resources.getString(R.string.import_not_a_backup)
                ListMessage.ImportFailed -> resources.getString(R.string.import_failed)
                ListMessage.ExportFailed -> resources.getString(R.string.export_failed)
            }
            snackbar.showSnackbar(text)
        }
    }

    // System file pickers. Backup files have no reliable MIME type, so any file is allowed;
    // the import is checked and confirmed before anything changes.
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onImportPicked)
    }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-sqlite3"),
    ) { uri -> uri?.let(viewModel::onExportPicked) }

    val selecting = selection.isNotEmpty()
    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionTopBar(
                    count = selection.size,
                    onClose = viewModel::clearSelection,
                    onLog = { viewModel.requestLog(selection) },
                    onDelete = { viewModel.requestDelete(selection) },
                )
            } else {
                ListTopBar(
                    onNewTask = onNewTask,
                    onImport = { importPicker.launch(arrayOf("*/*")) },
                    onExport = { exportPicker.launch(viewModel.suggestedBackupName(appName)) },
                    onLoadTestData = if (viewModel.hasTestData) viewModel::requestLoadTestData else null,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            TaskList(
                rows = state.rows,
                loaded = state.loaded,
                selection = selection,
                onClick = { id -> if (selecting) viewModel.toggleSelected(id) else onOpenTask(id) },
                onLongClick = viewModel::toggleSelected,
                onDone = { id -> viewModel.requestLog(setOf(id)) },
            )
        }
    }

    when (val d = dialog) {
        is ListDialog.ConfirmLog -> ConfirmDialog(
            title = stringResource(R.string.confirm_log_title),
            text = pluralStringResource(R.plurals.confirm_log_prompt, d.ids.size, d.ids.size),
            confirmLabel = stringResource(R.string.button_log),
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
        )
        is ListDialog.ConfirmDelete -> ConfirmDialog(
            title = stringResource(R.string.confirm_delete_tasks_title),
            text = pluralStringResource(R.plurals.confirm_delete_tasks_prompt, d.ids.size, d.ids.size),
            confirmLabel = stringResource(R.string.button_delete),
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
        )
        is ListDialog.ConfirmImport -> ImportDialog(
            pending = d.pending,
            onMerge = { viewModel.confirmImport(replace = false) },
            onReplace = { viewModel.confirmImport(replace = true) },
            onDismiss = viewModel::dismissDialog,
        )
        null -> Unit
    }
}

/** Import choice: Merge (default) or Replace all, with what each would do. */
@Composable
private fun ImportDialog(
    pending: PendingImport,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit,
) {
    val all = pending.preview.imported
    val m = pending.mergePreview
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.import_contains, pending.displayName, all.tasks.size, all.log.size))
                Text(
                    stringResource(R.string.import_merge_explain, m.matchedTasks, m.addedTasks, m.addedLogEntries),
                    modifier = Modifier.testTag("mergeExplain"),
                )
                Text(stringResource(R.string.import_replace_explain))
                if (m.skippedTasks > 0) {
                    Text(pluralStringResource(R.plurals.import_will_skip, m.skippedTasks, m.skippedTasks))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onMerge, modifier = Modifier.testTag("merge")) {
                Text(stringResource(R.string.button_merge))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) }
                TextButton(onClick = onReplace, modifier = Modifier.testTag("replace")) {
                    Text(stringResource(R.string.button_replace_all), color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListTopBar(
    onNewTask: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onLoadTestData: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_quadrant_mark), contentDescription = null, modifier = Modifier.size(26.dp))
                Text(stringResource(R.string.app_name), modifier = Modifier.padding(start = 12.dp))
            }
        },
        actions = {
            IconButton(onClick = onNewTask, modifier = Modifier.testTag("newTask")) {
                Icon(Icons.Filled.Add, stringResource(R.string.action_new_task))
            }
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("overflow")) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_import)) },
                    onClick = { menu = false; onImport() },
                    modifier = Modifier.testTag("importBackup"),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_export)) },
                    onClick = { menu = false; onExport() },
                    modifier = Modifier.testTag("exportBackup"),
                )
                if (onLoadTestData != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.debug_load_sample)) },
                        onClick = { menu = false; onLoadTestData() },
                        modifier = Modifier.testTag("loadTestData"),
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, onClose: () -> Unit, onLog: () -> Unit, onDelete: () -> Unit) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close_selection)) }
        },
        title = { Text(pluralStringResource(R.plurals.tasks_selected, count, count)) },
        actions = {
            IconButton(onClick = onLog, modifier = Modifier.testTag("logSelected")) {
                Icon(Icons.Filled.DoneAll, stringResource(R.string.action_log_selected))
            }
            IconButton(onClick = onDelete, modifier = Modifier.testTag("deleteSelected")) {
                Icon(Icons.Filled.Delete, stringResource(R.string.action_delete_selected))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    )
}

@Composable
private fun TaskList(
    rows: List<TaskRow>,
    loaded: Boolean,
    selection: Set<Long>,
    onClick: (Long) -> Unit,
    onLongClick: (Long) -> Unit,
    onDone: (Long) -> Unit,
) {
    if (rows.isEmpty()) {
        if (loaded) {
            Box(Modifier.fillMaxSize().padding(24.dp)) {
                Text(
                    stringResource(R.string.list_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("taskList")) {
        items(rows, key = { it.task.id }) { row ->
            TaskRowItem(
                row = row,
                checked = row.task.id in selection,
                onClick = { onClick(row.task.id) },
                onLongClick = { onLongClick(row.task.id) },
                onDone = { onDone(row.task.id) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** One task row: the quadrant mark (which is also the "done" button), name and due text. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskRowItem(
    row: TaskRow,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDone: () -> Unit,
) {
    val color = Color(urgencyColor(row.urgency))
    val doneLabel = stringResource(R.string.action_log_done_today, row.task.name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
            .testTag("task:${row.task.name}"),
    ) {
        // The mark doubles as the "done" button (it asks for confirmation first).
        IconButton(onClick = onDone, modifier = Modifier.size(48.dp).testTag("done:${row.task.name}")) {
            QuadrantMark(
                row.urgency,
                color,
                Modifier
                    .size(22.dp)
                    .semantics { contentDescription = doneLabel },
            )
        }
        Text(
            text = row.task.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        )
        Text(
            text = row.dueText,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Cells of the mark as (column, row), in the order they fill. Quadrant two, top-right, is last. */
private val FILL_ORDER = listOf(0 to 0, 0 to 1, 1 to 1, 1 to 0)

/**
 * How far through its period a task is, as a 2×2 grid after the Eisenhower matrix. One cell fills
 * per quarter of the period: top-left, bottom-left, bottom-right, then top-right (quadrant two:
 * important, not urgent) when the task falls due. Overdue tasks stay full; the colour shows how late.
 */
@Composable
fun QuadrantMark(urgency: Double?, color: Color, modifier: Modifier = Modifier) {
    val filled = filledQuadrants(urgency)
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val cell = (size.minDimension - gap) / 2
        val corner = CornerRadius(cell * 0.2f)
        FILL_ORDER.forEachIndexed { index, (column, row) ->
            drawRoundRect(
                color = if (index < filled) color else color.copy(alpha = 0.25f),
                topLeft = Offset(column * (cell + gap), row * (cell + gap)),
                size = Size(cell, cell),
                cornerRadius = corner,
            )
        }
    }
}
