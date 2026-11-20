package io.github.gasserma.quadranttwodo.data

import com.google.common.truth.Truth.assertThat
import io.github.gasserma.quadranttwodo.core.Settings
import io.github.gasserma.quadranttwodo.core.ThemeMode
import io.github.gasserma.quadranttwodo.core.UrgencyScheme
import io.github.gasserma.quadranttwodo.testSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SettingsRepositoryTest {

    @Test fun startsWithDefaults() = runTest {
        assertThat(testSettings().settings.first()).isEqualTo(Settings())
        assertThat(Settings().widgetOpacity).isEqualTo(70)
        assertThat(Settings().scheme).isEqualTo(UrgencyScheme.Dusk)
        assertThat(Settings().theme).isEqualTo(ThemeMode.System)
    }

    @Test fun savesEachSetting() = runTest {
        val repo = testSettings()
        repo.setTheme(ThemeMode.Dark)
        repo.setWidgetOpacity(35)
        repo.setScheme(UrgencyScheme.BlueOrange)
        assertThat(repo.settings.first()).isEqualTo(Settings(ThemeMode.Dark, 35, UrgencyScheme.BlueOrange))
    }

    @Test fun opacityIsClampedToAPercentage() = runTest {
        val repo = testSettings()
        repo.setWidgetOpacity(150)
        assertThat(repo.settings.first().widgetOpacity).isEqualTo(100)
        repo.setWidgetOpacity(-5)
        assertThat(repo.settings.first().widgetOpacity).isEqualTo(0)
    }
}
