package br.com.amamentabebe.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val intervalMinutes: Int = 120,
    val nextAlarmMillis: Long? = null,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val interval = intPreferencesKey("interval_minutes")
        val nextAlarm = longPreferencesKey("next_alarm_millis")
        val sound = booleanPreferencesKey("sound")
        val vibration = booleanPreferencesKey("vibration")
        val theme = stringPreferencesKey("theme")
    }

    val values: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            intervalMinutes = prefs[Keys.interval] ?: 120,
            nextAlarmMillis = prefs[Keys.nextAlarm]?.takeIf { it > 0 },
            sound = prefs[Keys.sound] ?: true,
            vibration = prefs[Keys.vibration] ?: true,
            theme = runCatching { ThemeMode.valueOf(prefs[Keys.theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
        )
    }

    suspend fun current() = values.first()
    suspend fun setInterval(minutes: Int) = context.dataStore.edit { it[Keys.interval] = minutes.coerceIn(1, 24 * 60) }
    suspend fun setAlarm(time: Long?) = context.dataStore.edit { if (time == null) it.remove(Keys.nextAlarm) else it[Keys.nextAlarm] = time }
    suspend fun setSound(value: Boolean) = context.dataStore.edit { it[Keys.sound] = value }
    suspend fun setVibration(value: Boolean) = context.dataStore.edit { it[Keys.vibration] = value }
    suspend fun setTheme(value: ThemeMode) = context.dataStore.edit { it[Keys.theme] = value.name }
}
