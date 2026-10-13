package dev.morse.launcher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.morse.core.settings.LauncherSettings
import dev.morse.core.settings.SettingsCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

/** Launcher settings, stored as one JSON document in DataStore. */
class SettingsRepository(
    private val store: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    private val key = stringPreferencesKey("settings_json")

    val settings: StateFlow<LauncherSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { SettingsCodec.decode(it[key]) }
        .stateIn(scope, SharingStarted.Eagerly, LauncherSettings())

    suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        store.edit { prefs ->
            val current = SettingsCodec.decode(prefs[key])
            val next = transform(current)
            if (next != current) prefs[key] = SettingsCodec.encode(next)
        }
    }
}
