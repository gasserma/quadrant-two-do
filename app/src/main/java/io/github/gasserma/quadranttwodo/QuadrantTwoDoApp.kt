package io.github.gasserma.quadranttwodo

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.os.Build
import io.github.gasserma.quadranttwodo.core.ThemeMode
import io.github.gasserma.quadranttwodo.data.BackupManager
import io.github.gasserma.quadranttwodo.data.QuadrantTwoDoDatabase
import io.github.gasserma.quadranttwodo.data.SettingsRepository
import io.github.gasserma.quadranttwodo.data.TaskRepository
import io.github.gasserma.quadranttwodo.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock

/** Manual dependency container. */
class AppContainer(
    val clock: Clock,
    val repository: TaskRepository,
    val backup: BackupManager,
    val settings: SettingsRepository,
)

open class QuadrantTwoDoApp : Application() {
    /** Replaceable so tests can install an in-memory container. */
    lateinit var container: AppContainer

    /** Lives as long as the process; used for widget refreshes and the theme setting. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val clock = Clock.systemDefaultZone()
        val repository = TaskRepository(QuadrantTwoDoDatabase.build(this), clock)
        container = AppContainer(
            clock = clock,
            repository = repository,
            backup = BackupManager(this, repository),
            settings = SettingsRepository.create(this),
        )
        startWidgetUpdates()
        startThemeSetting()
    }

    /** Keeps the home-screen widget current; see [WidgetRefresher]. */
    protected open fun startWidgetUpdates() {
        WidgetRefresher.observeChanges(this, container, appScope)
        WidgetRefresher.schedule(this, container.clock)
    }

    /**
     * On Android 12+, tells the system about the Light/Dark setting, so everything the system draws
     * for the app (the launch screen, the window background) matches it too. The Compose theme
     * applies the setting itself on every version.
     */
    protected open fun startThemeSetting() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val uiMode = getSystemService(UiModeManager::class.java)
        appScope.launch {
            container.settings.settings.map { it.theme }.distinctUntilChanged().collect { theme ->
                uiMode.setApplicationNightMode(
                    when (theme) {
                        ThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO
                        ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
                        ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
                    },
                )
            }
        }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as QuadrantTwoDoApp).container
