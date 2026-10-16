package com.ikhanai.timesheet.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ikhanai.timesheet.domain.BookingSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

/** Spec FR-6 settings with their defaults. */
data class AppSettings(
    val availableMinutes: Int = 8 * 60,
    val stepMinutes: Int = 30,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val weekendDays: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
    val allowOvertime: Boolean = false,
    val overheadSystem: BookingSystem? = null,
    val allowFutureDates: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderTime: LocalTime = LocalTime.of(17, 0),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsStore

    val settings: Flow<AppSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map(::read)

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private fun read(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            availableMinutes = prefs[AVAILABLE] ?: defaults.availableMinutes,
            stepMinutes = prefs[STEP] ?: defaults.stepMinutes,
            weekStart = prefs[WEEK_START]?.let(DayOfWeek::valueOf) ?: defaults.weekStart,
            weekendDays = prefs[WEEKEND]?.let { value ->
                value.split(',').filter { it.isNotBlank() }.map(DayOfWeek::valueOf).toSet()
            } ?: defaults.weekendDays,
            allowOvertime = prefs[OVERTIME] ?: defaults.allowOvertime,
            overheadSystem = prefs[OVERHEAD_SYSTEM]?.takeIf { it.isNotEmpty() }?.let(BookingSystem::valueOf),
            allowFutureDates = prefs[FUTURE] ?: defaults.allowFutureDates,
            reminderEnabled = prefs[REMINDER] ?: defaults.reminderEnabled,
            reminderTime = prefs[REMINDER_TIME]?.let { LocalTime.ofSecondOfDay(it * 60L) } ?: defaults.reminderTime,
            themeMode = prefs[THEME]?.let(ThemeMode::valueOf) ?: defaults.themeMode,
        )
    }

    private fun write(prefs: androidx.datastore.preferences.core.MutablePreferences, s: AppSettings) {
        prefs[AVAILABLE] = s.availableMinutes
        prefs[STEP] = s.stepMinutes
        prefs[WEEK_START] = s.weekStart.name
        prefs[WEEKEND] = s.weekendDays.sorted().joinToString(",") { it.name }
        prefs[OVERTIME] = s.allowOvertime
        prefs[OVERHEAD_SYSTEM] = s.overheadSystem?.name.orEmpty()
        prefs[FUTURE] = s.allowFutureDates
        prefs[REMINDER] = s.reminderEnabled
        prefs[REMINDER_TIME] = s.reminderTime.hour * 60 + s.reminderTime.minute
        prefs[THEME] = s.themeMode.name
    }

    private companion object {
        val AVAILABLE = intPreferencesKey("available_minutes")
        val STEP = intPreferencesKey("step_minutes")
        val WEEK_START = stringPreferencesKey("week_start")
        val WEEKEND = stringPreferencesKey("weekend_days")
        val OVERTIME = booleanPreferencesKey("allow_overtime")
        val OVERHEAD_SYSTEM = stringPreferencesKey("overhead_system")
        val FUTURE = booleanPreferencesKey("allow_future_dates")
        val REMINDER = booleanPreferencesKey("reminder_enabled")
        val REMINDER_TIME = intPreferencesKey("reminder_minute_of_day")
        val THEME = stringPreferencesKey("theme_mode")
    }
}
