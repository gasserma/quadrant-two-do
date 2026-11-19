package io.github.gasserma.quadranttwodo.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.gasserma.quadranttwodo.AppContainer
import io.github.gasserma.quadranttwodo.BuildConfig
import io.github.gasserma.quadranttwodo.ui.edit.TaskEditScreen
import io.github.gasserma.quadranttwodo.ui.edit.TaskEditViewModel
import io.github.gasserma.quadranttwodo.ui.list.TaskListScreen
import io.github.gasserma.quadranttwodo.ui.list.TaskListViewModel
import io.github.gasserma.quadranttwodo.ui.theme.QuadrantTwoDoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val NEW_TASK = -1L

@Composable
fun QuadrantTwoDoRoot(container: AppContainer) {
    val context = LocalContext.current.applicationContext
    QuadrantTwoDoTheme {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = "list") {
            composable("list") {
                val vm = viewModel { TaskListViewModel(container, debugTestDataSource(context)) }
                TaskListScreen(
                    viewModel = vm,
                    onOpenTask = { id -> nav.navigate("task/$id") },
                    onNewTask = { nav.navigate("task/$NEW_TASK") },
                )
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
