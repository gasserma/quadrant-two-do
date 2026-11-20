package io.github.gasserma.quadranttwodo

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import io.github.gasserma.quadranttwodo.core.ThemeMode
import io.github.gasserma.quadranttwodo.ui.QuadrantTwoDoRoot
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Transparent system bars whose icons match the theme setting (see Settings).
        lifecycleScope.launch {
            appContainer.settings.settings.map { it.theme }.distinctUntilChanged().collect { theme ->
                val style = when (theme) {
                    ThemeMode.System -> SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
                    ThemeMode.Light -> SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    ThemeMode.Dark -> SystemBarStyle.dark(Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
        }
        setContent { QuadrantTwoDoRoot(appContainer) }
    }
}
