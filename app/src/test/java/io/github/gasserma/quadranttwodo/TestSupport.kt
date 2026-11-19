package io.github.gasserma.quadranttwodo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.gasserma.quadranttwodo.data.BackupManager
import io.github.gasserma.quadranttwodo.data.QuadrantTwoDoDatabase
import io.github.gasserma.quadranttwodo.data.TaskRepository
import java.io.File
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

/** "Now" used by the expected results in BLUEPRINT.md. */
val BLUEPRINT_NOW: LocalDateTime = LocalDateTime.parse("2026-11-18T08:48")

fun fixedClock(now: LocalDateTime = BLUEPRINT_NOW): Clock {
    val zone = ZoneId.of("America/Los_Angeles")
    return Clock.fixed(now.atZone(zone).toInstant(), zone)
}

/** Copies the bundled `regularly.db` test resource to a temp file. */
fun copyRegularlyDb(): File {
    val file = File.createTempFile("regularly", ".db")
    file.deleteOnExit()
    val stream = requireNotNull(TestSupport::class.java.classLoader!!.getResourceAsStream("regularly.db")) {
        "regularly.db test resource missing"
    }
    stream.use { input -> file.outputStream().use { input.copyTo(it) } }
    return file
}

object TestSupport {
    /** The in-memory database behind the last installed container. */
    lateinit var database: QuadrantTwoDoDatabase

    /** In-memory app container with a fixed clock; installs it into the Robolectric app. */
    fun installContainer(now: LocalDateTime = BLUEPRINT_NOW): AppContainer {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clock = fixedClock(now)
        database = QuadrantTwoDoDatabase.inMemory(context)
        val repository = TaskRepository(database, clock)
        val container = AppContainer(
            clock = clock,
            repository = repository,
            backup = BackupManager(context, repository),
        )
        (context as QuadrantTwoDoApp).container = container
        return container
    }
}
