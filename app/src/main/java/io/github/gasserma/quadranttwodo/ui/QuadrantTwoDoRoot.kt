package io.github.gasserma.quadranttwodo.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.gasserma.quadranttwodo.AppContainer
import io.github.gasserma.quadranttwodo.BuildConfig
import io.github.gasserma.quadranttwodo.core.Settings
import io.github.gasserma.quadranttwodo.ui.edit.TaskEditScreen
import io.github.gasserma.quadranttwodo.ui.edit.TaskEditViewModel
import io.github.gasserma.quadranttwodo.ui.list.TaskListScreen
import io.github.gasserma.quadranttwodo.ui.list.TaskListViewModel
import io.github.gasserma.quadranttwodo.ui.settings.SettingsScreen
import io.github.gasserma.quadranttwodo.ui.settings.SettingsViewModel
import io.github.gasserma.quadranttwodo.ui.theme.QuadrantTwoDoTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val NEW_TASK = -1L

/**
 * The app's screens, in the theme the settings ask for. Until the settings have loaded the defaults
 * apply; on Android 12+ the system already knows the Light/Dark choice (see QuadrantTwoDoApp), so
 * the first frame is right there too.
 */
@Composable
fun QuadrantTwoDoRoot(container: AppContainer) {
    val context = LocalContext.current.applicationContext
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    QuadrantTwoDoTheme(darkTheme = settings.theme.isDark(isSystemInDarkTheme()), scheme = settings.scheme) {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = "list") {
            composable("list") {
                val vm = viewModel { TaskListViewModel(container, debugTestDataSource(context)) }
                TaskListScreen(
                    viewModel = vm,
                    onOpenTask = { id -> nav.navigate("task/$id") },
                    onNewTask = { nav.navigate("task/$NEW_TASK") },
                    onOpenSettings = { nav.navigate("settings") },
                )
            }
            composable("settings") {
                val vm = viewModel { SettingsViewModel(container.settings) }
                SettingsScreen(viewModel = vm, onBack = { nav.popBackStack() })
            }
            composable(
                "task/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id")?.takeIf { it != NEW_TASK }
                val vm = viewModel { TaskEditViewModel(container, id) }
                TaskEditScreen(viewModel = vm, onClose = { nav.popBackStack() })
            }
        }
    }
}

/**
 * Debug builds bundle a sample backup (regularly.db) as an asset so the UI can be
 * reviewed with real data. Release builds have no such asset and no menu item.
 */
private fun debugTestDataSource(context: Context): (suspend () -> File?)? {
    if (!BuildConfig.DEBUG) return null
    if (context.assets.list("")?.contains("regularly.db") != true) return null
    return {
        withContext(Dispatchers.IO) {
            val out = File(context.cacheDir, "regularly-test.db")
            context.assets.open("regularly.db").use { input -> out.outputStream().use { input.copyTo(it) } }
            out
        }
    }
}
