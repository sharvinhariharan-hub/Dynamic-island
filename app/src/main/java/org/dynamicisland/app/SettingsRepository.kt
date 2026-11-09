package org.dynamicisland.app

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.*

private val Context.islandDataStore by preferencesDataStore("island_settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.islandDataStore
    val settings: Flow<IslandSettings> =
        store.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map(::decode)

    suspend fun update(transform: (IslandSettings) -> IslandSettings) {
        store.edit { p -> encode(p, transform(decode(p))) }
    }

    suspend fun reset() {
        store.edit { it.clear() }
    }

    companion object {
        private val enabled = booleanPreferencesKey("enabled")
        private val apps = stringSetPreferencesKey("apps")
        private val x = intPreferencesKey("x")
        private val y = intPreferencesKey("y")
        private val width = intPreferencesKey("width")
        private val duration = intPreferencesKey("duration")
        private val speed = floatPreferencesKey("speed")
        private val haptics = intPreferencesKey("haptics")
        private val locked = booleanPreferencesKey("hide_locked")
        private val content = booleanPreferencesKey("hide_content")

        internal fun decode(p: Preferences) =
            IslandSettings(
                p[enabled] ?: false,
                p[apps] ?: emptySet(),
                p[x] ?: 0,
                p[y] ?: 4,
                (p[width] ?: 280).coerceIn(180, 360),
                (p[duration] ?: 5).coerceIn(2, 15),
                (p[speed] ?: 1f).coerceIn(0.5f, 2f),
                (p[haptics] ?: 1).coerceIn(0, 2),
                p[locked] ?: true,
                p[content] ?: false,
            )

        internal fun encode(p: MutablePreferences, s: IslandSettings) {
            p[enabled] = s.enabled
            p[apps] = s.apps
            p[x] = s.x
            p[y] = s.y
            p[width] = s.width
            p[duration] = s.duration
            p[speed] = s.speed
            p[haptics] = s.haptics
            p[locked] = s.hideLocked
            p[content] = s.hideContent
        }
    }
}
