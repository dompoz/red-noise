package com.pinktakhyper.deeprednoise.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class NoiseSettings(
    val volume: Float = 0.8f,
    val redness: Float = 0.5f,
    val timerMinutes: Int = 0,
)

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_VOLUME = floatPreferencesKey("volume")
        private val KEY_REDNESS = floatPreferencesKey("redness")
        private val KEY_TIMER = intPreferencesKey("timer_minutes")
    }

    val settings: Flow<NoiseSettings> = context.dataStore.data.map { prefs ->
        NoiseSettings(
            volume = prefs[KEY_VOLUME] ?: 0.8f,
            redness = prefs[KEY_REDNESS] ?: 0.5f,
            timerMinutes = prefs[KEY_TIMER] ?: 0,
        )
    }

    suspend fun saveVolume(volume: Float) {
        context.dataStore.edit { it[KEY_VOLUME] = volume }
    }

    suspend fun saveRedness(redness: Float) {
        context.dataStore.edit { it[KEY_REDNESS] = redness }
    }

    suspend fun saveTimer(minutes: Int) {
        context.dataStore.edit { it[KEY_TIMER] = minutes }
    }
}
