package io.github.gasserma.quadranttwodo.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.gasserma.quadranttwodo.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Emits the current time now and then at the start of every minute, so urgency colours move
 * through the day.
 */
fun nowTicker(clock: Clock): Flow<LocalDateTime> = flow {
    while (true) {
        val now = LocalDateTime.now(clock)
        emit(now)
        val millisIntoMinute = now.second * 1000L + now.nano / 1_000_000L
        delay((60_000L - millisIntoMinute).coerceAtLeast(1_000L))
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm")) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) } },
    )
}

fun LocalDate.toPickerMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

fun Long.fromPickerMillis(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Material date picker in a dialog. [onClear] adds a "Clear" button when given. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(
    initial: LocalDate,
    confirmLabel: String,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toPickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(it.fromPickerMillis()) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            if (onClear != null) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.button_clear)) }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}
