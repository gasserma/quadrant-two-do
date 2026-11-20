package io.github.gasserma.quadranttwodo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.gasserma.quadranttwodo.core.Settings
import io.github.gasserma.quadranttwodo.core.ThemeMode
import io.github.gasserma.quadranttwodo.core.UrgencyScheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The user's settings, kept in a small DataStore file (`datastore/settings.preferences_pb`, included
 * in Android's backup). Missing or unknown values fall back to the defaults in [Settings].
 */
class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            theme = ThemeMode.fromId(p[THEME]),
            widgetOpacity = (p[WIDGET_OPACITY] ?: Settings.DEFAULT_WIDGET_OPACITY).coerceIn(0, 100),
            scheme = UrgencyScheme.fromId(p[SCHEME]),
        )
    }

    suspend fun setTheme(theme: ThemeMode) {
        store.edit { it[THEME] = theme.id }
    }

    suspend fun setWidgetOpacity(percent: Int) {
        store.edit { it[WIDGET_OPACITY] = percent.coerceIn(0, 100) }
    }

    suspend fun setScheme(scheme: UrgencyScheme) {
        store.edit { it[SCHEME] = scheme.id }
    }

    companion object {
        /** File name under files/datastore/; the backup rules name it too. */
        const val FILE_NAME = "settings"

        private val THEME = stringPreferencesKey("theme")
        private val WIDGET_OPACITY = intPreferencesKey("widget_opacity")
        private val SCHEME = stringPreferencesKey("urgency_scheme")

        /** The app's settings store. Create only one per process (DataStore requires it). */
        fun create(context: Context): SettingsRepository = SettingsRepository(
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE_NAME) },
        )
    }
}
