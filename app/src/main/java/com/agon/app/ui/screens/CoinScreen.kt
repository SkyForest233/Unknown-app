package com.agon.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agon.app.data.CoinStyle
import com.agon.app.data.HistoryRecord
import com.agon.app.data.RandomSource
import com.agon.app.data.RecordType
import com.agon.app.ui.components.CatCoin
import com.agon.app.ui.components.CircleIconButton
import com.agon.app.ui.components.ErrorCard
import com.agon.app.ui.components.MorphCoin
import com.agon.app.ui.components.PageHeader
import com.agon.app.ui.components.ResultReveal
import com.agon.app.ui.components.SourceBadgeLabel
import com.agon.app.ui.theme.EmphasizedLabel
import com.agon.app.ui.theme.EmphasizedResultLarge
import com.agon.app.ui.theme.EmphasizedResultMedium
import com.agon.app.viewmodel.AppViewModel
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

private const val MAX_COINS = 8

private sealed interface CoinUi {
    data object Idle : CoinUi
    data object Flipping : CoinUi
    data class Done(val results: List<Boolean>, val detail: String) : CoinUi
    data class Error(val message: String) : CoinUi
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CoinScreen(
    viewModel: AppViewModel,
    onOpenSource: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val settingsState by viewModel.settings.collectAsState()
    val hapticsEnabled = settingsState?.haptics ?: true

    var ui by remember { mutableStateOf<CoinUi>(CoinUi.Idle) }
    var headsCount by remember { mutableIntStateOf(0) }
    var tailsCount by remember { mutableIntStateOf(0) }
    var flipRound by remember { mutableIntStateOf(0) }
    var usedLabel by remember { mutableStateOf(viewModel.sourceLabel) }

    // 硬币数量（滑杆控制）；首帧直接用已持久化的值初始化，不会闪烁
    var coinCountF by remember {
        mutableFloatStateOf(
            (settingsState?.coinCount ?: 1).coerceIn(1, MAX_COINS).toFloat()
        )
    }
    val coinCount = coinCountF.roundToInt().coerceIn(1, MAX_COINS)

    // 每枚硬币独立的旋转角度
    val rotations = remember { List(MAX_COINS) { Animatable(0f) } }
    // 落定 spring 回弹
    val settle = remember { Animatable(1f) }
    val scrollState = rememberScrollState()

    fun flip(overrideSource: RandomSource? = null) {
        if (ui is CoinUi.Flipping) return
        ui = CoinUi.Flipping
        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val n = coinCount
        scope.launch {
            // 跟手：点击瞬间所有硬币立即开始快速匀速旋转，后台同时获取随机数
            for (i in 0 until n) {
                launch {
                    rotations[i].animateTo(
                        targetValue = rotations[i].value + 3_600_000f,
                        animationSpec = tween(durationMillis = 5_625_000, easing = LinearEasing),
                    )
                }
            }
            try {
                usedLabel = if (overrideSource != null) overrideSource.shortName else viewModel.sourceLabel
                val result = viewModel.generate(0, 1, n, overrideSource = overrideSource)
                val faces = result.values.map { it == 0 }
                // 每枚硬币错峰减速，依次停在各自结果面
                val jobs = mutableListOf<Job>()
                for (i in 0 until n) {
                    jobs += launch {
                        delay(i * 120L)
                        val face = if (faces[i]) 0f else 180f
                        val current = rotations[i].value
                        val target = ceil((current + 540f - face) / 360f) * 360f + face
                        rotations[i].animateTo(
                            targetValue = target,
                            animationSpec = tween(durationMillis = 1100, easing = LinearOutSlowInEasing),
                        )
                    }
                }
                jobs.joinAll()
                headsCount += faces.count { it }
                tailsCount += faces.count { !it }
                flipRound++
                ui = CoinUi.Done(faces, result.detail)
                if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                // 自动滚动，确保结果卡片与“再抛一次”按钮可见
                launch { scrollState.animateScrollTo(scrollState.maxValue / 2) }
                // 落定回弹
                settle.snapTo(0.9f)
                settle.animateTo(
                    1f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
                viewModel.addHistory(
                    HistoryRecord(
                        id = System.currentTimeMillis(),
                        type = RecordType.COIN,
                        title = if (n == 1) "抛硬币" else "抛硬币 ×$n",
                        result = if (n == 1) {
                            if (faces[0]) "正面" else "反面"
                        } else {
                            "正 ×${faces.count { it }} · 反 ×${faces.count { !it }}"
                        },
                        source = usedLabel,
                        detail = result.detail,
                        timestamp = System.currentTimeMillis(),
                    )
                )
            } catch (e: Exception) {
                // 失败时所有硬币平滑减速停在正面
                val jobs = mutableListOf<Job>()
                for (i in 0 until n) {
                    jobs += launch {
                        rotations[i].animateTo(
                            targetValue = ceil(rotations[i].value / 360f) * 360f,
                            animationSpec = tween(durationMillis = 700, easing = LinearOutSlowInEasing),
                        )
                    }
                }
                jobs.joinAll()
                ui = CoinUi.Error(e.message ?: "生成失败")
            }
        }
    }

    Scaffold(
        topBar = {
            PageHeader(
                title = "抛硬币",
                subtitle = "二选一决策",
                actions = {
                    CircleIconButton(
                        icon = Icons.Default.Tune,
                        contentDescription = "选择随机源",
                        onClick = onOpenSource,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 来源标识（点击可切换）
            SourceBadgeLabel(
                label = usedLabel,
                modifier = Modifier.clickable(onClick = onOpenSource),
            )
            Spacer(Modifier.height(20.dp))

            // 硬币区：单枚大硬币 / 多枚小硬币网格
            val coinStyle = settingsState?.coinStyle ?: CoinStyle.CAT.name
            val coinArea = Modifier
                .fillMaxWidth()
                .height(232.dp)
                .clickable(enabled = ui !is CoinUi.Flipping) { flip() }
            Box(modifier = coinArea, contentAlignment = Alignment.Center) {
                if (coinCount == 1) {
                    CoinFace(
                        style = coinStyle,
                        rotation = rotations[0].value,
                        settleScale = settle.value,
                        modifier = Modifier.size(220.dp),
                    )
                } else {
                    val coinSize = when {
                        coinCount <= 2 -> 108.dp
                        coinCount <= 4 -> 100.dp
                        else -> 88.dp
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        maxItemsInEachRow = 4,
                    ) {
                        repeat(coinCount) { i ->
                            CoinFace(
                                style = coinStyle,
                                rotation = rotations[i].value,
                                settleScale = settle.value,
                                modifier = Modifier.size(coinSize),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 状态提示行（固定高度，避免按钮位置跳动）
            Box(
                modifier = Modifier.height(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (ui is CoinUi.Idle) {
                    Text(
                        "点击硬币或下方按钮开始",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 主操作按钮（动态位置）：未出结果时在硬币下方；
            // 出结果后移到结果卡片下方（变成“再抛一次”）
            val flipButton: @Composable () -> Unit = {
                Button(
                    onClick = { flip() },
                    enabled = ui !is CoinUi.Flipping,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Icon(Icons.Default.Casino, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        when {
                            ui is CoinUi.Done -> "再抛一次"
                            coinCount > 1 -> "抛 $coinCount 枚硬币"
                            else -> "抛硬币"
                        },
                        style = EmphasizedLabel,
                    )
                }
            }

            if (ui !is CoinUi.Done) {
                flipButton()
                Spacer(Modifier.height(16.dp))
            }

            // 结果区
            when (val state = ui) {
                is CoinUi.Done -> {
                    ResultReveal(triggerKey = flipRound, emphasized = true) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (state.results.size == 1) {
                                    Text(
                                        if (state.results[0]) "正面" else "反面",
                                        style = EmphasizedResultLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                } else {
                                    Text(
                                        "正 ×${state.results.count { it }} · 反 ×${state.results.count { !it }}",
                                        style = EmphasizedResultMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        state.results.joinToString(" ") { if (it) "正" else "反" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "来源：$usedLabel · ${state.detail}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                is CoinUi.Error -> {
                    ErrorCard(
                        message = state.message,
                        onRetry = { flip() },
                        onFallbackLocal = if (viewModel.enabledSources != listOf(RandomSource.LOCAL)) {
                            { flip(RandomSource.LOCAL) }
                        } else null,
                    )
                }
                else -> {}
            }

            // 出结果后：“再抛一次”紧跟在结果卡片下方
            if (ui is CoinUi.Done) {
                Spacer(Modifier.height(16.dp))
                flipButton()
            }

            Spacer(Modifier.height(16.dp))

            // 本轮统计
            if (headsCount + tailsCount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatItem("正面", headsCount)
                        StatItem("反面", tailsCount)
                        StatItem(
                            "正面占比",
                            value = null,
                            text = "${(headsCount * 100f / (headsCount + tailsCount)).toInt()}%",
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // 设置：硬币数量滑杆（移至页面最下方）
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "硬币数量",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "$coinCount 枚",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = coinCountF,
                        onValueChange = { coinCountF = it },
                        onValueChangeFinished = { viewModel.setCoinCount(coinCount) },
                        valueRange = 1f..MAX_COINS.toFloat(),
                        steps = MAX_COINS - 2,
                        enabled = ui !is CoinUi.Flipping,
                    )
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun CoinFace(
    style: String,
    rotation: Float,
    settleScale: Float,
    modifier: Modifier = Modifier,
) {
    if (style == CoinStyle.CAT.name) {
        CatCoin(rotation = rotation, settleScale = settleScale, modifier = modifier)
    } else {
        MorphCoin(rotation = rotation, settleScale = settleScale, modifier = modifier)
    }
}

@Composable
private fun StatItem(label: String, value: Int?, text: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text ?: value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
