package br.com.amamentabebe.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import br.com.amamentabebe.domain.TimerSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")
enum class AlarmStatus { NONE, EXACT, APPROXIMATE, NOTIFICATIONS_BLOCKED, FAILED }

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class NightMode { AUTO, ALWAYS, OFF }
data class AppSettings(
    val intervalMinutes: Int = 120,
    val nextAlarmMillis: Long? = null,
    val alarmStatus: AlarmStatus = AlarmStatus.NONE,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val nightMode: NightMode = NightMode.OFF
)
class SettingsStore(private val context: Context) {
    private object Keys {
        val interval = intPreferencesKey("interval_minutes")
        val nextAlarm = longPreferencesKey("next_alarm_millis")
        val alarmStatus = stringPreferencesKey("alarm_status")
        val sound = booleanPreferencesKey("sound")
        val vibration = booleanPreferencesKey("vibration")
        val theme = stringPreferencesKey("theme")
        val night = stringPreferencesKey("night_mode")
        val start = longPreferencesKey("timer_start")
        val since = longPreferencesKey("timer_since")
        val side = stringPreferencesKey("timer_side")
        val left = longPreferencesKey("timer_left")
        val right = longPreferencesKey("timer_right")
    }
    val values: Flow<AppSettings> = context.dataStore.data.map { prefs -> AppSettings(
        intervalMinutes = prefs[Keys.interval] ?: 120,
        nextAlarmMillis = prefs[Keys.nextAlarm]?.takeIf { it > 0 },
        alarmStatus = runCatching { AlarmStatus.valueOf(prefs[Keys.alarmStatus] ?: "NONE") }.getOrDefault(AlarmStatus.NONE),
        sound = prefs[Keys.sound] ?: true,
        vibration = prefs[Keys.vibration] ?: true,
        theme = runCatching { ThemeMode.valueOf(prefs[Keys.theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
        nightMode = runCatching { NightMode.valueOf(prefs[Keys.night] ?: "OFF") }.getOrDefault(NightMode.OFF)
    ) }
    val timer: Flow<TimerSnapshot> = context.dataStore.data.map { p -> TimerSnapshot(
        p[Keys.start] ?: 0, runCatching { BreastSide.valueOf(p[Keys.side] ?: "LEFT") }.getOrDefault(BreastSide.LEFT),
        p[Keys.since] ?: 0, p[Keys.left] ?: 0, p[Keys.right] ?: 0
    ) }
    suspend fun current() = values.first()
    suspend fun setInterval(minutes: Int) = context.dataStore.edit { it[Keys.interval] = minutes.coerceIn(1, 24 * 60) }
    suspend fun setAlarm(time: Long?) = context.dataStore.edit { if (time == null) it.remove(Keys.nextAlarm) else it[Keys.nextAlarm] = time }
    suspend fun setAlarmStatus(value: AlarmStatus) = context.dataStore.edit { it[Keys.alarmStatus] = value.name }
    suspend fun setSound(value: Boolean) = context.dataStore.edit { it[Keys.sound] = value }
    suspend fun setVibration(value: Boolean) = context.dataStore.edit { it[Keys.vibration] = value }
    suspend fun setTheme(value: ThemeMode) = context.dataStore.edit { it[Keys.theme] = value.name }
    suspend fun setNightMode(value: NightMode) = context.dataStore.edit { it[Keys.night] = value.name }
    suspend fun setTimer(value: TimerSnapshot) = context.dataStore.edit {
        it[Keys.start] = value.startedAt; it[Keys.since] = value.runningSince; it[Keys.side] = value.activeSide.name
        it[Keys.left] = value.leftMillis; it[Keys.right] = value.rightMillis
    }
    suspend fun restoreSettings(value: AppSettings) = context.dataStore.edit {
        it[Keys.interval] = value.intervalMinutes.coerceIn(1,1440)
        it[Keys.sound] = value.sound; it[Keys.vibration] = value.vibration; it[Keys.theme] = value.theme.name
        it[Keys.night] = value.nightMode.name
    }
}
