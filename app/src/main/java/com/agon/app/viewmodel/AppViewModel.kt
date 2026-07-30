package com.agon.app.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.data.AppSettings
import com.agon.app.data.HistoryRecord
import com.agon.app.data.RandomRepository
import com.agon.app.data.RandomResult
import com.agon.app.data.RandomSource
import com.agon.app.data.SettingsStore
import com.agon.app.data.SourceState
import com.agon.app.data.SourceStatus
import com.agon.app.data.WheelPreset
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)

    /**
     * 同步读取初始设置：首帧即拥有正确的主题/配置，
     * 避免“先闪默认绿色主题/1枚硬币，再跳到已保存值”的闪烁。
     * DataStore 首次读取仅几毫秒（小 prefs 文件），发生在 Application 级 ViewModel 创建时。
     */
    private val initialSettings: AppSettings = runBlocking { store.settingsFlow.first() }

    val settings: StateFlow<AppSettings?> =
        store.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, initialSettings)

    val history: StateFlow<List<HistoryRecord>> =
        store.historyFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val wheelPresets: StateFlow<List<WheelPreset>> =
        store.wheelPresetsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 各随机源健康状态 */
    val sourceStatus = mutableStateMapOf<RandomSource, SourceStatus>().apply {
        RandomSource.entries.forEach { put(it, SourceStatus()) }
    }

    val currentSource: RandomSource
        get() = settings.value?.let {
            try {
                RandomSource.valueOf(it.defaultSource)
            } catch (e: Exception) {
                RandomSource.LOCAL
            }
        } ?: RandomSource.LOCAL

    /** 已启用的随机源组合（至少保证一个，兼容脏数据） */
    val enabledSources: List<RandomSource>
        get() = settings.value?.enabledSources
            ?.split(",")
            ?.mapNotNull { name ->
                try {
                    RandomSource.valueOf(name.trim())
                } catch (e: Exception) {
                    null
                }
            }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(RandomSource.LOCAL)

    /** 组合标签，如 "random.org + 本地" */
    val sourceLabel: String
        get() = enabledSources.joinToString(" + ") { it.shortName }

    /** 当前组合是否需要网络 */
    val needsNetwork: Boolean
        get() = enabledSources.any { it.isNetwork }

    fun selectSource(source: RandomSource) {
        viewModelScope.launch { store.setDefaultSource(source.name) }
    }

    /** 开关某个随机源；至少保留一个 */
    fun toggleSource(source: RandomSource) {
        val current = enabledSources
        val updated = if (source in current) {
            if (current.size <= 1) return // 至少保留一个
            current - source
        } else {
            current + source
        }
        viewModelScope.launch {
            store.setEnabledSources(updated.joinToString(",") { it.name })
        }
    }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch { store.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { store.setDynamicColor(enabled) }
    }

    fun setCoinStyle(style: String) {
        viewModelScope.launch { store.setCoinStyle(style) }
    }

    fun setSeedColor(argb: Long) {
        viewModelScope.launch { store.setSeedColor(argb) }
    }

    fun setHaptics(enabled: Boolean) {
        viewModelScope.launch { store.setHaptics(enabled) }
    }

    fun setOnboarded(done: Boolean) {
        viewModelScope.launch { store.setOnboarded(done) }
    }

    // ---- 工具页编辑状态持久化 ----

    fun setCoinCount(count: Int) {
        viewModelScope.launch { store.setCoinCount(count) }
    }

    fun setWheelOptions(options: List<String>) {
        viewModelScope.launch { store.setWheelOptions(options) }
    }

    fun setNumberConfig(min: String, max: String, count: Int, unique: Boolean) {
        viewModelScope.launch { store.setNumberConfig(min, max, count, unique) }
    }

    fun decodeWheelOptions(raw: String): List<String> = store.decodeWheelOptions(raw)

    fun clearHistory() {
        viewModelScope.launch { store.clearHistory() }
    }

    fun addHistory(record: HistoryRecord) {
        viewModelScope.launch { store.addHistory(record) }
    }

    fun saveWheelPreset(name: String, options: List<String>) {
        viewModelScope.launch {
            store.addWheelPreset(
                WheelPreset(
                    id = System.currentTimeMillis(),
                    name = name,
                    options = options,
                )
            )
        }
    }

    fun deleteWheelPreset(id: Long) {
        viewModelScope.launch { store.deleteWheelPreset(id) }
    }

    /** 生成随机数（多源组合混合；可覆盖源列表，用于失败后本地兜底） */
    suspend fun generate(
        min: Int,
        max: Int,
        count: Int = 1,
        unique: Boolean = false,
        overrideSource: RandomSource? = null,
    ): RandomResult {
        val sources = if (overrideSource != null) listOf(overrideSource) else enabledSources
        return RandomRepository.fetchCombined(sources, min, max, count, unique)
    }

    fun checkSource(source: RandomSource) {
        sourceStatus[source] = SourceStatus(SourceState.CHECKING)
        viewModelScope.launch {
            sourceStatus[source] = try {
                val latency = RandomRepository.ping(source)
                SourceStatus(SourceState.ONLINE, latency)
            } catch (e: Exception) {
                SourceStatus(SourceState.OFFLINE, -1, e.message ?: "不可用")
            }
        }
    }

    fun checkAllSources() {
        RandomSource.entries.forEach { checkSource(it) }
    }
}
