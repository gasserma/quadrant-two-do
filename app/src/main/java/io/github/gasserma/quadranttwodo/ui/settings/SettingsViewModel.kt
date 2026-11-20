package io.github.gasserma.quadranttwodo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.gasserma.quadranttwodo.core.Settings
import io.github.gasserma.quadranttwodo.core.ThemeMode
import io.github.gasserma.quadranttwodo.core.UrgencyScheme
import io.github.gasserma.quadranttwodo.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Settings screen: shows the stored settings and saves each change straight away. */
class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    /** Null until loaded, so the screen never shows defaults that aren't the user's choices. */
    val settings: StateFlow<Settings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTheme(theme: ThemeMode) {
        viewModelScope.launch { repository.setTheme(theme) }
    }

    fun setWidgetOpacity(percent: Int) {
        viewModelScope.launch { repository.setWidgetOpacity(percent) }
    }

    fun setScheme(scheme: UrgencyScheme) {
        viewModelScope.launch { repository.setScheme(scheme) }
    }
}
