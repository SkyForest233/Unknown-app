package com.agon.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import kotlin.random.Random

class RandomSourceException(message: String) : Exception(message)

@Serializable
private data class DrandBeacon(val round: Long = 0, val randomness: String = "")

object RandomRepository {

    private val secureRandom = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 从指定随机源获取 [count] 个位于 [min]..[max] 的整数。
     * [unique] 为 true 时结果互不重复（要求区间大小 >= count）。
     * 失败时抛出 [RandomSourceException]。
     */
    suspend fun fetchInts(
        source: RandomSource,
        min: Int,
        max: Int,
        count: Int = 1,
        unique: Boolean = false,
    ): RandomResult = withContext(Dispatchers.IO) {
        require(max >= min) { "最大值必须不小于最小值" }
        val rangeSize = max.toLong() - min.toLong() + 1
        if (unique) require(rangeSize >= count) { "去重模式下区间大小必须不小于数量" }

        val start = System.currentTimeMillis()
        when (source) {
            RandomSource.LOCAL -> {
                val values = generateWithRng(min, max, count, unique) { bound ->
                    secureRandom.nextInt(bound)
                }
                RandomResult(values, source, "SecureRandom · 系统熵池", System.currentTimeMillis() - start)
            }

            RandomSource.RANDOM_ORG -> {
                if (!unique) {
                    val body = httpGet(
                        "https://www.random.org/integers/?num=$count&min=$min&max=$max" +
                            "&col=1&base=10&format=plain&rnd=new"
                    )
                    val values = body.trim().lines()
                        .filter { it.isNotBlank() }
                        .map {
                            it.trim().toIntOrNull()
                                ?: throw RandomSourceException("random.org 返回数据异常")
                        }
                    if (values.size != count) throw RandomSourceException("random.org 返回数量不符")
                    RandomResult(values, source, "大气噪声 · 官方 API", System.currentTimeMillis() - start)
                } else {
                    // 去重模式：取 random.org 真随机种子，本地做无重复抽样
                    val body = httpGet(
                        "https://www.random.org/integers/?num=1&min=1&max=1000000000" +
                            "&col=1&base=10&format=plain&rnd=new"
                    )
                    val seed = body.trim().toLongOrNull()
                        ?: throw RandomSourceException("random.org 返回数据异常")
                    val rng = Random(seed xor System.nanoTime())
                    val values = uniqueSample(min, max, count, rng)
                    RandomResult(values, source, "大气噪声种子 · 本地去重抽样", System.currentTimeMillis() - start)
                }
            }

            RandomSource.DRAND -> {
                val body = httpGet("https://drand.cloudflare.com/public/latest")
                val beacon = try {
                    json.decodeFromString<DrandBeacon>(body)
                } catch (e: Exception) {
                    throw RandomSourceException("drand 信标解析失败")
                }
                if (beacon.randomness.length < 16) throw RandomSourceException("drand 信标数据异常")
                val seed = beacon.randomness.take(16).toULong(16).toLong() xor System.nanoTime()
                val rng = Random(seed)
                val values = if (unique) {
                    uniqueSample(min, max, count, rng)
                } else {
                    generateWithRng(min, max, count, false) { bound -> rng.nextInt(bound) }
                }
                RandomResult(values, source, "信标轮次 #${beacon.round}", System.currentTimeMillis() - start)
            }
        }
    }

    /**
     * 多源组合生成：并发从每个源取熵，XOR 混合为种子后本地抽样。
     * 密码学原则：多源 XOR 混合的结果至少与其中最强的源一样随机。
     * 单源时直接走 [fetchInts] 保持原有来源详情。
     * 任一源失败即抛出 [RandomSourceException]（保证组合承诺不降级）。
     */
    suspend fun fetchCombined(
        sources: List<RandomSource>,
        min: Int,
        max: Int,
        count: Int = 1,
        unique: Boolean = false,
    ): RandomResult = withContext(Dispatchers.IO) {
        require(sources.isNotEmpty()) { "至少启用一个随机源" }
        if (sources.size == 1) return@withContext fetchInts(sources[0], min, max, count, unique)

        val start = System.currentTimeMillis()
        // 并发取各源熵值（64bit 种子片段）
        val seeds = sources.map { src ->
            async { src to fetchSeed(src) }
        }.awaitAll()

        var mixed = 0L
        val detailParts = mutableListOf<String>()
        seeds.forEach { (src, pair) ->
            mixed = mixed xor pair.first
            detailParts.add("${src.shortName}(${pair.second})")
        }
        // 加入本地熵搅拌，防止单一网络源被操纵时完全决定输出
        mixed = mixed xor secureRandom.nextLong()

        val rng = Random(mixed)
        val values = if (unique) {
            uniqueSample(min, max, count, rng)
        } else {
            generateWithRng(min, max, count, false) { bound -> rng.nextInt(bound) }
        }
        RandomResult(
            values = values,
            source = sources.first(),
            detail = "XOR 混合：${detailParts.joinToString(" ⊕ ")}",
            latencyMs = System.currentTimeMillis() - start,
        )
    }

    /** 从单个源取一个 64bit 熵种子；返回 (种子, 来源简述) */
    private suspend fun fetchSeed(source: RandomSource): Pair<Long, String> =
        withContext(Dispatchers.IO) {
            when (source) {
                RandomSource.LOCAL -> secureRandom.nextLong() to "系统熵池"
                RandomSource.RANDOM_ORG -> {
                    val body = httpGet(
                        "https://www.random.org/integers/?num=2&min=1&max=1000000000" +
                            "&col=1&base=10&format=plain&rnd=new"
                    )
                    val nums = body.trim().lines().mapNotNull { it.trim().toLongOrNull() }
                    if (nums.size < 2) throw RandomSourceException("random.org 返回数据异常")
                    ((nums[0] shl 30) xor nums[1]) to "大气噪声"
                }
                RandomSource.DRAND -> {
                    val body = httpGet("https://drand.cloudflare.com/public/latest")
                    val beacon = try {
                        json.decodeFromString<DrandBeacon>(body)
                    } catch (e: Exception) {
                        throw RandomSourceException("drand 信标解析失败")
                    }
                    if (beacon.randomness.length < 16) throw RandomSourceException("drand 信标数据异常")
                    beacon.randomness.take(16).toULong(16).toLong() to "轮次#${beacon.round}"
                }
            }
        }

    /** 测试随机源可用性，返回延迟毫秒数；失败抛异常 */
    suspend fun ping(source: RandomSource): Long = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        when (source) {
            RandomSource.LOCAL -> secureRandom.nextInt(2)
            RandomSource.RANDOM_ORG -> httpGet(
                "https://www.random.org/integers/?num=1&min=0&max=1&col=1&base=10&format=plain&rnd=new",
                timeoutMs = 6000,
            )
            RandomSource.DRAND -> httpGet("https://drand.cloudflare.com/public/latest", timeoutMs = 6000)
        }
        System.currentTimeMillis() - start
    }

    // ---------- helpers ----------

    private inline fun generateWithRng(
        min: Int,
        max: Int,
        count: Int,
        unique: Boolean,
        nextInt: (bound: Int) -> Int,
    ): List<Int> {
        val rangeSize = (max.toLong() - min.toLong() + 1)
        if (!unique) {
            return List(count) {
                if (rangeSize > Int.MAX_VALUE) min + nextInt(Int.MAX_VALUE)
                else min + nextInt(rangeSize.toInt())
            }
        }
        val set = LinkedHashSet<Int>()
        while (set.size < count) {
            set.add(min + nextInt(rangeSize.toInt()))
        }
        return set.toList()
    }

    private fun uniqueSample(min: Int, max: Int, count: Int, rng: Random): List<Int> {
        val rangeSize = max.toLong() - min.toLong() + 1
        return if (rangeSize <= 100_000) {
            (min..max).shuffled(rng).take(count)
        } else {
            val set = LinkedHashSet<Int>()
            while (set.size < count) {
                set.add(min + rng.nextInt(rangeSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
            }
            set.toList()
        }
    }

    private fun httpGet(url: String, timeoutMs: Int = 8000): String {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", "TrueRandomApp/1.0")
            val code = conn.responseCode
            if (code != 200) throw RandomSourceException("服务返回错误码 $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: RandomSourceException) {
            throw e
        } catch (e: IOException) {
            throw RandomSourceException("网络连接失败，请检查网络后重试")
        } catch (e: Exception) {
            throw RandomSourceException("请求失败：${e.message ?: "未知错误"}")
        } finally {
            conn?.disconnect()
        }
    }
}
