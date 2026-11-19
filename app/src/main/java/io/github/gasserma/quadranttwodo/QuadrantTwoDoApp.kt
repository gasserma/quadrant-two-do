package io.github.gasserma.quadranttwodo

import android.app.Application
import android.content.Context
import io.github.gasserma.quadranttwodo.data.BackupManager
import io.github.gasserma.quadranttwodo.data.QuadrantTwoDoDatabase
import io.github.gasserma.quadranttwodo.data.TaskRepository
import io.github.gasserma.quadranttwodo.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock

/** Manual dependency container. */
class AppContainer(
    val clock: Clock,
    val repository: TaskRepository,
    val backup: BackupManager,
)

open class QuadrantTwoDoApp : Application() {
    /** Replaceable so tests can install an in-memory container. */
    lateinit var container: AppContainer

    /** Lives as long as the process; used for widget refreshes. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val clock = Clock.systemDefaultZone()
        val repository = TaskRepository(QuadrantTwoDoDatabase.build(this), clock)
        container = AppContainer(clock, repository, BackupManager(this, repository))
        startWidgetUpdates()
    }

    /** Keeps the home-screen widget current; see [WidgetRefresher]. */
    protected open fun startWidgetUpdates() {
        WidgetRefresher.observeChanges(this, container, appScope)
        WidgetRefresher.schedule(this, container.clock)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as QuadrantTwoDoApp).container
