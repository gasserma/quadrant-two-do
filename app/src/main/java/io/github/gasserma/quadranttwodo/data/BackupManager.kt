package io.github.gasserma.quadranttwodo.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * A backup that has been read and checked, waiting for the user to choose merge or replace.
 * [preview] describes a full replace; [mergePreview] a merge into the current data.
 */
data class PendingImport(
    val file: File,
    val displayName: String,
    val preview: ImportResult,
    val mergePreview: MergeSummary,
)

/**
 * Moves backup files between the system file picker (content URIs) and the
 * repository. Files are staged in the app's cache directory.
 */
class BackupManager(private val context: Context, private val repository: TaskRepository) {

    private val staging get() = File(context.cacheDir, "backup").apply { mkdirs() }

    /** Copies the picked file locally and reads it. Nothing is imported yet. */
    suspend fun prepareImport(uri: Uri): PendingImport {
        val name = displayName(uri) ?: uri.lastPathSegment ?: "backup"
        val file = File(staging, "import.db")
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Can't open $name")
            input.use { i -> file.outputStream().use { i.copyTo(it) } }
        }
        return try {
            prepareImport(file, name)
        } catch (e: Exception) {
            file.delete() // not a backup, or unreadable: nothing to keep
            throw e
        }
    }

    suspend fun prepareImport(file: File, displayName: String): PendingImport =
        PendingImport(file, displayName, repository.previewImport(file), repository.previewMerge(file))

    /** Replaces everything with the backup. */
    suspend fun import(pending: PendingImport): ImportResult =
        try { repository.importBackup(pending.file) } finally { discard(pending) }

    /** Merges the backup into the current data (planned afresh, in case data changed since). */
    suspend fun merge(pending: PendingImport): MergeSummary =
        try { repository.mergeBackup(pending.file) } finally { discard(pending) }

    /**
     * Deletes the staged copy of a picked file once it has been imported or the import was
     * cancelled, so no copy of the user's data lingers in the cache. Files the caller passed in
     * directly are left alone.
     */
    fun discard(pending: PendingImport) {
        if (pending.file.parentFile == staging) pending.file.delete()
    }

    /** Writes a backup of everything to [uri]. */
    suspend fun export(uri: Uri): BackupContents {
        val file = File(staging, "export.db")
        try {
            val snapshot = repository.exportBackup(file)
            withContext(Dispatchers.IO) {
                val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Can't write backup")
                output.use { o -> file.inputStream().use { it.copyTo(o) } }
            }
            return snapshot
        } finally {
            file.delete()
        }
    }

    private suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }
}
