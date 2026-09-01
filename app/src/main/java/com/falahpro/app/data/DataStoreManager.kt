package com.falahpro.app.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore by preferencesDataStore(name = "tasbih_prefs")
private val ACCOUNT_CREATED = stringPreferencesKey("account_created")

object DataStoreManager {

    // ===============================
    // 🔹 TASBIH COUNTER
    // ===============================

    private fun countKey(dhikr: String) =
        intPreferencesKey("count_$dhikr")

    fun getCount(context: Context, dhikr: String): Flow<Int> {
        return context.dataStore.data.map { preferences: Preferences ->
            preferences[countKey(dhikr)] ?: 0
        }
    }

    suspend fun saveCount(context: Context, dhikr: String, count: Int) {
        context.dataStore.edit { preferences: MutablePreferences ->
            preferences[countKey(dhikr)] = count
        }
    }

    // ===============================
    // 🔹 PRAYER TRACKER
    // ===============================

    private val DATE_KEY = stringPreferencesKey("saved_date")

    private fun prayerKey(name: String) =
        booleanPreferencesKey("prayer_state_$name")

    fun getPrayerState(context: Context, name: String): Flow<Boolean> {
        return context.dataStore.data.map { prefs: Preferences ->
            prefs[prayerKey(name)] ?: false
        }
    }

    suspend fun savePrayerState(context: Context, name: String, value: Boolean) {
        context.dataStore.edit { prefs: MutablePreferences ->
            prefs[prayerKey(name)] = value
            prefs[DATE_KEY] = LocalDate.now().toString()
        }
    }

    suspend fun checkAndResetIfNewDay(context: Context) {
        context.dataStore.edit { prefs: MutablePreferences ->
            val today = LocalDate.now().toString()
            val savedDate = prefs[DATE_KEY]

            if (savedDate != today) {
                // Remove ONLY prayer state keys (explicit list to avoid asMap() API differences)
                listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha").forEach { name ->
                    prefs.remove(prayerKey(name))
                }
                prefs[DATE_KEY] = today
            }
        }
    }

    suspend fun saveAccountCreationDate(context: Context) {
        context.dataStore.edit { preferences: MutablePreferences ->
            if (preferences[ACCOUNT_CREATED] == null) {
                preferences[ACCOUNT_CREATED] = java.time.LocalDate.now().toString()
            }
        }
    }

    fun getAccountCreationDate(context: Context): Flow<String?> {
        return context.dataStore.data.map { prefs: Preferences ->
            prefs[ACCOUNT_CREATED]
        }
    }

    // ===============================
    // 🔔 AZAN MODE
    // ===============================

    private val AZAN_MODE_KEY = stringPreferencesKey("azan_mode")

    suspend fun saveAzanMode(context: Context, mode: AzanMode) {
        context.dataStore.edit { prefs: MutablePreferences ->
            prefs[AZAN_MODE_KEY] = mode.name
        }
    }

    fun getAzanMode(context: Context): Flow<AzanMode> {
        return context.dataStore.data.map { prefs: Preferences ->
            AzanMode.valueOf(
                // Default FULL_SOUND so a fresh install / applicationId rename
                // still schedules prayer alarms (SILENT cancels all alarms).
                prefs[AZAN_MODE_KEY] ?: AzanMode.FULL_SOUND.name
            )
        }
    }

    // AZAN-FIX-3C / AZAN-FIX-7: prompt timestamps (not boolean "asked forever")
    private val EXACT_ALARM_PROMPT_LAST_SHOWN_MS =
        longPreferencesKey("exact_alarm_prompt_last_shown_ms")
    private val OEM_WIZARD_SHOWN_MS = longPreferencesKey("oem_wizard_shown_ms")

    suspend fun getExactAlarmPromptLastShownMs(context: Context): Long {
        return context.dataStore.data.map { it[EXACT_ALARM_PROMPT_LAST_SHOWN_MS] ?: 0L }.first()
    }

    suspend fun setExactAlarmPromptLastShownMs(context: Context, value: Long) {
        context.dataStore.edit { it[EXACT_ALARM_PROMPT_LAST_SHOWN_MS] = value }
    }

    suspend fun getOemWizardShownMs(context: Context): Long {
        return context.dataStore.data.map { it[OEM_WIZARD_SHOWN_MS] ?: 0L }.first()
    }

    suspend fun setOemWizardShownMs(context: Context, value: Long) {
        context.dataStore.edit { it[OEM_WIZARD_SHOWN_MS] = value }
    }
}
