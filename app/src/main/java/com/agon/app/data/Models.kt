package com.agon.app.data

import kotlinx.serialization.Serializable

/** 随机源 */
enum class RandomSource(
    val displayName: String,
    val shortName: String,
    val description: String,
    val transparency: String,
    val isNetwork: Boolean,
) {
    RANDOM_ORG(
        displayName = "random.org",
        shortName = "random.org",
        description = "基于大气噪声的真随机数服务，1998 年运行至今，被广泛用于抽奖与科学实验。",
        transparency = "通过 HTTPS 调用官方 API，结果原样返回，可在 random.org 网站验证服务状态。",
        isNetwork = true,
    ),
    DRAND(
        displayName = "Cloudflare Drand",
        shortName = "drand",
        description = "League of Entropy 分布式随机信标，多个独立机构共同生成，每 30 秒公开一轮可验证随机数。",
        transparency = "获取最新一轮公开随机信标（含轮次号），任何人可用相同轮次号在链上验证。",
        isNetwork = true,
    ),
    LOCAL(
        displayName = "本地随机",
        shortName = "本地",
        description = "使用设备内置 SecureRandom 加密安全伪随机数生成器，无需网络、即时可用。",
        transparency = "熵来自系统内核熵池（/dev/urandom），不经过任何网络传输。",
        isNetwork = false,
    );
}

/** 一次随机生成的结果 */
data class RandomResult(
    val values: List<Int>,
    val source: RandomSource,
    val detail: String,
    val latencyMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
)

/** 随机源健康状态 */
data class SourceStatus(
    val state: SourceState = SourceState.UNKNOWN,
    val latencyMs: Long = -1,
    val message: String = "",
)

enum class SourceState { UNKNOWN, CHECKING, ONLINE, OFFLINE }

/** 历史记录 */
@Serializable
data class HistoryRecord(
    val id: Long,
    val type: String, // COIN / WHEEL / NUMBER
    val title: String,
    val result: String,
    val source: String,
    val detail: String,
    val timestamp: Long,
)

object RecordType {
    const val COIN = "COIN"
    const val WHEEL = "WHEEL"
    const val NUMBER = "NUMBER"
}

/** 大转盘选项预设 */
@Serializable
data class WheelPreset(
    val id: Long,
    val name: String,
    val options: List<String>,
)

/** 硬币样式 */
enum class CoinStyle(val displayName: String) {
    CLASSIC("经典金币"),
    CAT("猫猫硬币"),
}

/** 应用设置 */
data class AppSettings(
    val themeMode: Int = 0, // 0 跟随系统 1 浅色 2 深色
    val dynamicColor: Boolean = false, // Material You 壁纸取色（API 31+）
    val seedColor: Long = 0L, // 主题种子色 ARGB；0 = 默认品牌薄荷绿
    val coinStyle: String = CoinStyle.CAT.name, // 硬币样式
    val defaultSource: String = RandomSource.LOCAL.name,
    val enabledSources: String = RandomSource.LOCAL.name, // 逗号分隔，可多选组合
    val haptics: Boolean = true,
    val onboardingDone: Boolean = false,
    // ---- 工具页编辑状态（退出后保持） ----
    val coinCount: Int = 1,                 // 抛硬币数量
    val wheelOptions: String = "",          // 转盘选项 JSON；空 = 默认选项
    val numMin: String = "1",               // 随机数最小值（原样保存输入）
    val numMax: String = "100",             // 随机数最大值
    val numCount: Int = 1,                  // 随机数生成数量
    val numUnique: Boolean = false,         // 随机数去重
)
