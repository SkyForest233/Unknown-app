package com.agon.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "truerandom_prefs")

class SettingsStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SEED_COLOR = longPreferencesKey("seed_color")
        val COIN_STYLE = stringPreferencesKey("coin_style")
        val DEFAULT_SOURCE = stringPreferencesKey("default_source")
        val ENABLED_SOURCES = stringPreferencesKey("enabled_sources")
        val HAPTICS = booleanPreferencesKey("haptics")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val HISTORY = stringPreferencesKey("history_json")
        val WHEEL_PRESETS = stringPreferencesKey("wheel_presets_json")

        // 工具页编辑状态
        val COIN_COUNT = intPreferencesKey("coin_count")
        val WHEEL_OPTIONS = stringPreferencesKey("wheel_options_json")
        val NUM_MIN = stringPreferencesKey("num_min")
        val NUM_MAX = stringPreferencesKey("num_max")
        val NUM_COUNT = intPreferencesKey("num_count")
        val NUM_UNIQUE = booleanPreferencesKey("num_unique")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME_MODE] ?: 0,
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: false,
            seedColor = p[Keys.SEED_COLOR] ?: 0L,
            coinStyle = p[Keys.COIN_STYLE] ?: CoinStyle.CAT.name,
            defaultSource = p[Keys.DEFAULT_SOURCE] ?: RandomSource.LOCAL.name,
            enabledSources = p[Keys.ENABLED_SOURCES]
                ?: p[Keys.DEFAULT_SOURCE] // 兼容旧版单选设置
                ?: RandomSource.LOCAL.name,
            haptics = p[Keys.HAPTICS] ?: true,
            onboardingDone = p[Keys.ONBOARDED] ?: false,
            coinCount = p[Keys.COIN_COUNT] ?: 1,
            wheelOptions = p[Keys.WHEEL_OPTIONS] ?: "",
            numMin = p[Keys.NUM_MIN] ?: "1",
            numMax = p[Keys.NUM_MAX] ?: "100",
            numCount = p[Keys.NUM_COUNT] ?: 1,
            numUnique = p[Keys.NUM_UNIQUE] ?: false,
        )
    }

    val historyFlow: Flow<List<HistoryRecord>> = context.dataStore.data.map { p ->
        val raw = p[Keys.HISTORY] ?: return@map emptyList()
        try {
            json.decodeFromString<List<HistoryRecord>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    val wheelPresetsFlow: Flow<List<WheelPreset>> = context.dataStore.data.map { p ->
        val raw = p[Keys.WHEEL_PRESETS] ?: return@map emptyList()
        try {
            json.decodeFromString<List<WheelPreset>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setSeedColor(argb: Long) {
        context.dataStore.edit { it[Keys.SEED_COLOR] = argb }
    }

    suspend fun setCoinStyle(style: String) {
        context.dataStore.edit { it[Keys.COIN_STYLE] = style }
    }

    suspend fun setDefaultSource(source: String) {
        context.dataStore.edit { it[Keys.DEFAULT_SOURCE] = source }
    }

    suspend fun setEnabledSources(sources: String) {
        context.dataStore.edit { it[Keys.ENABLED_SOURCES] = sources }
    }

    suspend fun setHaptics(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS] = enabled }
    }

    suspend fun setOnboarded(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDED] = done }
    }

    suspend fun setCoinCount(count: Int) {
        context.dataStore.edit { it[Keys.COIN_COUNT] = count }
    }

    suspend fun setWheelOptions(options: List<String>) {
        context.dataStore.edit { it[Keys.WHEEL_OPTIONS] = json.encodeToString(options) }
    }

    suspend fun setNumberConfig(min: String, max: String, count: Int, unique: Boolean) {
        context.dataStore.edit {
            it[Keys.NUM_MIN] = min
            it[Keys.NUM_MAX] = max
            it[Keys.NUM_COUNT] = count
            it[Keys.NUM_UNIQUE] = unique
        }
    }

    fun decodeWheelOptions(raw: String): List<String> =
        if (raw.isBlank()) emptyList()
        else try {
            json.decodeFromString<List<String>>(raw)
        } catch (e: Exception) {
            emptyList()
        }

    suspend fun addHistory(record: HistoryRecord) {
        context.dataStore.edit { p ->
            val current = try {
                p[Keys.HISTORY]?.let { json.decodeFromString<List<HistoryRecord>>(it) } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            val updated = (listOf(record) + current).take(200)
            p[Keys.HISTORY] = json.encodeToString(updated)
        }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { it.remove(Keys.HISTORY) }
    }

    suspend fun addWheelPreset(preset: WheelPreset) {
        context.dataStore.edit { p ->
            val current = try {
                p[Keys.WHEEL_PRESETS]?.let { json.decodeFromString<List<WheelPreset>>(it) } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            val updated = (listOf(preset) + current).take(20)
            p[Keys.WHEEL_PRESETS] = json.encodeToString(updated)
        }
    }

    suspend fun deleteWheelPreset(id: Long) {
        context.dataStore.edit { p ->
            val current = try {
                p[Keys.WHEEL_PRESETS]?.let { json.decodeFromString<List<WheelPreset>>(it) } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            p[Keys.WHEEL_PRESETS] = json.encodeToString(current.filterNot { it.id == id })
        }
    }
}
