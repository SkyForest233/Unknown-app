package com.agon.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agon.app.data.HistoryRecord
import com.agon.app.data.RandomSource
import com.agon.app.data.RecordType
import com.agon.app.ui.components.CheckSwitch
import com.agon.app.ui.components.CircleIconButton
import com.agon.app.ui.components.ErrorCard
import com.agon.app.ui.components.InverseSnackbarHost
import com.agon.app.ui.components.PageHeader
import com.agon.app.ui.components.ResultReveal
import com.agon.app.ui.theme.EmphasizedLabel
import com.agon.app.ui.theme.EmphasizedResultLarge
import com.agon.app.ui.theme.EmphasizedResultMedium
import com.agon.app.ui.components.SectionTitle
import com.agon.app.ui.components.SourceBadgeLabel
import com.agon.app.viewmodel.AppViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private const val MAX_COUNT = 20

private data class NumberPreset(
    val name: String,
    val desc: String,
    val min: Int,
    val max: Int,
    val count: Int,
    val unique: Boolean,
)

private val presets = listOf(
    NumberPreset("骰子", "1–6 · 1 个", 1, 6, 1, false),
    NumberPreset("百分之一", "1–100 · 1 个", 1, 100, 1, false),
    NumberPreset("双色球红球", "1–33 · 6 个 · 去重", 1, 33, 6, true),
    NumberPreset("双色球蓝球", "1–16 · 1 个", 1, 16, 1, false),
    NumberPreset("验证码", "0–9 · 4 位", 0, 9, 4, false),
    NumberPreset("大乐透前区", "1–35 · 5 个 · 去重", 1, 35, 5, true),
)

private sealed interface NumUi {
    data object Idle : NumUi
    /** 加载中：携带参数，用于老虎机式滚动占位数字 */
    data class Loading(val min: Int, val max: Int, val count: Int) : NumUi
    data class Done(val values: List<Int>, val detail: String) : NumUi
    data class Error(val message: String) : NumUi
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NumberScreen(
    viewModel: AppViewModel,
    onOpenSource: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val settingsState by viewModel.settings.collectAsState()
    val hapticsEnabled = settingsState?.haptics ?: true

    // 首帧直接用已持久化的配置初始化，不会闪烁；之后任何变更自动保存
    var minText by remember { mutableStateOf(settingsState?.numMin ?: "1") }
    var maxText by remember { mutableStateOf(settingsState?.numMax ?: "100") }
    var countF by remember {
        mutableFloatStateOf((settingsState?.numCount ?: 1).coerceIn(1, MAX_COUNT).toFloat())
    }
    val count = countF.roundToInt().coerceIn(1, MAX_COUNT)
    var unique by remember { mutableStateOf(settingsState?.numUnique ?: false) }
    LaunchedEffect(Unit) {
        snapshotFlow { listOf(minText, maxText, count.toString(), unique.toString()) }
            .drop(1)
            .collect { viewModel.setNumberConfig(minText, maxText, count, unique) }
    }
    var showPresetSheet by remember { mutableStateOf(false) }
    val presetSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var ui by remember { mutableStateOf<NumUi>(NumUi.Idle) }
    var usedLabel by remember { mutableStateOf(viewModel.sourceLabel) }
    var usedNetwork by remember { mutableStateOf(viewModel.needsNetwork) }
    var inputError by remember { mutableStateOf<String?>(null) }

    fun validate(): Triple<Int, Int, Int>? {
        val min = minText.trim().toIntOrNull()
        val max = maxText.trim().toIntOrNull()
        inputError = when {
            min == null || max == null -> "请输入有效的整数范围"
            max < min -> "最大值必须不小于最小值"
            unique && (max.toLong() - min.toLong() + 1) < count -> "去重模式下区间大小需 ≥ 数量"
            else -> null
        }
        return if (inputError == null) Triple(min!!, max!!, count) else null
    }

    fun generate(overrideSource: RandomSource? = null) {
        val params = validate() ?: return
        if (ui is NumUi.Loading) return
        // 跟手：点击立刻进入滚动态，占位数字马上开始跳动
        ui = NumUi.Loading(params.first, params.second, params.third)
        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            try {
                usedLabel = if (overrideSource != null) overrideSource.shortName else viewModel.sourceLabel
                usedNetwork = if (overrideSource != null) overrideSource.isNetwork else viewModel.needsNetwork
                val result = viewModel.generate(
                    params.first, params.second, params.third,
                    unique = unique, overrideSource = overrideSource,
                )
                ui = NumUi.Done(result.values, result.detail)
                if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.addHistory(
                    HistoryRecord(
                        id = System.currentTimeMillis(),
                        type = RecordType.NUMBER,
                        title = "随机数 ${params.first}–${params.second}" +
                            if (params.third > 1) " ×${params.third}" else "",
                        result = result.values.joinToString(", "),
                        source = usedLabel,
                        detail = result.detail,
                        timestamp = System.currentTimeMillis(),
                    )
                )
            } catch (e: Exception) {
                ui = NumUi.Error(e.message ?: "生成失败")
            }
        }
    }

    Scaffold(
        topBar = {
            PageHeader(
                title = "随机数",
                subtitle = "自定义区间与数量",
                actions = {
                    CircleIconButton(
                        icon = Icons.Default.Tune,
                        contentDescription = "选择随机源",
                        onClick = onOpenSource,
                    )
                },
            )
        },
        snackbarHost = { InverseSnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SourceBadgeLabel(
                label = usedLabel,
                modifier = Modifier.clickable(onClick = onOpenSource),
            )
            Spacer(Modifier.height(16.dp))

            // 输入区
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("参数")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = minText,
                            onValueChange = { minText = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("最小值") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        OutlinedTextField(
                            value = maxText,
                            onValueChange = { maxText = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("最大值") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                        )
                    }

                    // 生成数量：MD3 滑杆
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "生成数量",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "$count 个",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = countF,
                        onValueChange = { countF = it },
                        valueRange = 1f..MAX_COUNT.toFloat(),
                        steps = MAX_COUNT - 2,
                        enabled = ui !is NumUi.Loading,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("结果去重", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "多个结果互不重复",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        CheckSwitch(checked = unique, onCheckedChange = { unique = it })
                    }

                    if (inputError != null) {
                        Text(
                            inputError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 主操作按钮：固定在参数卡片下方，出结果后位置不变，方便连续生成
            Button(
                onClick = { generate() },
                enabled = ui !is NumUi.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape,
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    if (ui is NumUi.Done) "重新生成" else "生成随机数",
                    style = EmphasizedLabel,
                )
            }

            Spacer(Modifier.height(16.dp))

            // 结果区（在按钮下方展示，不挤压操作区）
            AnimatedContent(
                targetState = ui,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut() },
                label = "numResult",
            ) { state ->
                when (state) {
                    is NumUi.Idle -> Text(
                        "设置参数后点击生成",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    is NumUi.Loading -> {
                        // 老虎机式滚动：占位数字快速跳动，直到真实结果返回
                        var rollingText by remember { mutableStateOf("") }
                        LaunchedEffect(state) {
                            val rng = kotlin.random.Random(System.nanoTime())
                            while (true) {
                                rollingText = List(state.count) {
                                    rng.nextInt(state.min, state.max + 1)
                                }.joinToString("  ")
                                kotlinx.coroutines.delay(60)
                            }
                        }
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
                                Text(
                                    rollingText,
                                    style = if (state.count <= 3)
                                        EmphasizedResultLarge
                                    else EmphasizedResultMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    is NumUi.Done -> ResultReveal(triggerKey = state, emphasized = true) {
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
                            Text(
                                state.values.joinToString("  "),
                                style = if (state.values.size <= 3)
                                    EmphasizedResultLarge
                                else EmphasizedResultMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "来源：$usedLabel · ${state.detail}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                )
                                IconButton(onClick = {
                                    clipboard.setText(AnnotatedString(state.values.joinToString(", ")))
                                    scope.launch { snackbarHostState.showSnackbar("已复制到剪贴板") }
                                }) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "复制结果",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }
                    }
                    is NumUi.Error -> ErrorCard(
                        message = state.message,
                        onRetry = { generate() },
                        onFallbackLocal = if (viewModel.enabledSources != listOf(RandomSource.LOCAL)) {
                            { generate(RandomSource.LOCAL) }
                        } else null,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 常用预设入口（放在页面最下方）
            FilledTonalButton(
                onClick = { showPresetSheet = true },
                enabled = ui !is NumUi.Loading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Bookmarks, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("常用预设", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(96.dp))
        }
    }

    // 常用预设 Bottom Sheet
    if (showPresetSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPresetSheet = false },
            sheetState = presetSheetState,
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    "常用预设",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "选择后自动填入区间、数量与去重设置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                presets.forEach { preset ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = {
                            minText = preset.min.toString()
                            maxText = preset.max.toString()
                            countF = preset.count.toFloat()
                            unique = preset.unique
                            showPresetSheet = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    preset.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    preset.desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}
