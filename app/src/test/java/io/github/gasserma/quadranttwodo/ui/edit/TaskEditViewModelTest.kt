package io.github.gasserma.quadranttwodo.ui.edit

import android.os.Looper
import io.github.gasserma.quadranttwodo.AppContainer
import io.github.gasserma.quadranttwodo.TestSupport
import io.github.gasserma.quadranttwodo.ui.list.backupFileName
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class TaskEditViewModelTest {

    private lateinit var container: AppContainer
    private val today = LocalDate.parse("2026-11-18")

    @Before fun setUp() {
        container = TestSupport.installContainer()
    }

    private fun <T : Any> await(read: () -> T?): T {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            read()?.let { return it }
            Thread.sleep(20)
        }
        error("Timed out")
    }

    private fun lastPerformed(id: Long) = runBlocking { container.repository.observeTask(id).first()?.lastPerformed }

    @Test fun logEntriesCanBeInTheFuture() {
        val id = runBlocking { container.repository.createTask("Plan ahead", 7, null, "") }
        val vm = TaskEditViewModel(container, id)
        val observer = CoroutineScope(Dispatchers.Main).launch { vm.state.collect {} } // as the screen does
        await { vm.state.value.takeIf { it.loaded } }

        vm.addLogEntry(today.plusDays(3))
        await { lastPerformed(id)?.takeIf { it == today.plusDays(3) } }

        val entry = runBlocking { container.repository.observeLog(id).first().single() }
        vm.updateLogEntry(entry, today.plusDays(10), "moved")
        await { lastPerformed(id)?.takeIf { it == today.plusDays(10) } }
        observer.cancel()
    }

    @Test fun newTaskScreenStaysNewAfterABackgroundSave() {
        val vm = TaskEditViewModel(container, null)
        val observer = CoroutineScope(Dispatchers.Main).launch { vm.state.collect {} }
        await { vm.state.value.takeIf { it.loaded } }
        assertThat(vm.state.value.openedAsNew).isTrue()

        vm.setName("Water plants")
        vm.setPeriod(14) // saves in the background, creating the task
        await { vm.state.value.takeIf { it.taskId != null } }
        assertThat(vm.state.value.openedAsNew).isTrue()
        observer.cancel()

        val existing = TaskEditViewModel(container, vm.state.value.taskId)
        val observer2 = CoroutineScope(Dispatchers.Main).launch { existing.state.collect {} }
        await { existing.state.value.takeIf { it.loaded } }
        assertThat(existing.state.value.openedAsNew).isFalse()
        observer2.cancel()
    }

    @Test fun backupFileNameFollowsTheAppName() {
        assertThat(backupFileName("Quadrant Two Do", today)).isEqualTo("quadrant-two-do-2026-11-18.db")
        assertThat(backupFileName("Habit Ring!", today)).isEqualTo("habit-ring-2026-11-18.db")
        assertThat(backupFileName("  ", today)).isEqualTo("backup-2026-11-18.db")
    }
}
