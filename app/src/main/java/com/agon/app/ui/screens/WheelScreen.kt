package com.agon.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.agon.app.data.HistoryRecord
import com.agon.app.data.RandomSource
import com.agon.app.data.RecordType
import com.agon.app.ui.components.CircleIconButton
import com.agon.app.ui.components.ErrorCard
import com.agon.app.ui.components.PageHeader
import com.agon.app.ui.components.ResultReveal
import com.agon.app.ui.theme.EmphasizedLabel
import com.agon.app.ui.theme.EmphasizedResultMedium
import com.agon.app.ui.components.SourceBadgeLabel
import com.agon.app.ui.theme.wheelPalette
import com.agon.app.viewmodel.AppViewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private sealed interface WheelUi {
    data object Idle : WheelUi
    data object Spinning : WheelUi
    data class Done(val winner: String, val detail: String, val prob: Float) : WheelUi
    data class Error(val message: String) : WheelUi
}

/** 解析选项倍率：“名称x2 / 名称X2 / 名称×2” → 2 倍概率，无后缀则为 1 倍 */
internal data class WheelOption(val label: String, val weight: Int)

internal fun parseWheelOption(raw: String): WheelOption {
    val m = Regex("^(.*?)\\s*[xX×]\\s*(\\d{1,2})\\s*$").find(raw.trim())
    if (m != null) {
        val label = m.groupValues[1].trim()
        val w = m.groupValues[2].toIntOrNull() ?: 1
        if (label.isNotEmpty() && w in 1..20) return WheelOption(label, w)
    }
    return WheelOption(raw.trim(), 1)
}

private const val MAX_OPTIONS = 20

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun WheelScreen(
    viewModel: AppViewModel,
    onOpenSource: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val settingsState by viewModel.settings.collectAsState()
    val hapticsEnabled = settingsState?.haptics ?: true

    // 首帧直接用已持久化的选项初始化，不会闪默认列表；之后任何变更自动保存
    val options = remember {
        val saved = settingsState?.let { viewModel.decodeWheelOptions(it.wheelOptions) } ?: emptyList()
        if (saved.size >= 2) {
            mutableStateListOf<String>().apply { addAll(saved.take(MAX_OPTIONS)) }
        } else {
            mutableStateListOf("火锅", "烧烤", "寿司", "粉面", "西餐", "轻食")
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { options.toList() }
            .drop(1)
            .collect { viewModel.setWheelOptions(it) }
    }
    var newOption by remember { mutableStateOf("") }
    var ui by remember { mutableStateOf<WheelUi>(WheelUi.Idle) }
    var usedLabel by remember { mutableStateOf(viewModel.sourceLabel) }
    var usedNetwork by remember { mutableStateOf(viewModel.needsNetwork) }
    val rotation = remember { Animatable(0f) }
    // 中奖扇区弹出效果：0 = 归位，1 = 完全弹出
    val segmentPop = remember { Animatable(0f) }
    var winnerIdx by remember { mutableIntStateOf(-1) }
    val scrollState = rememberScrollState()
    var spinCount by remember { mutableIntStateOf(0) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var editingIndex by remember { mutableIntStateOf(-1) }
    var editText by remember { mutableStateOf("") }

    // 预设：保存当前选项为命名预设，一键套用
    val wheelPresets by viewModel.wheelPresets.collectAsState()
    var showSaveInput by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    fun savePreset() {
        val name = presetName.trim()
        if (name.isNotEmpty()) {
            viewModel.saveWheelPreset(name, options.toList())
            presetName = ""
            showSaveInput = false
        }
    }

    fun applyPreset(presetOptions: List<String>) {
        options.clear()
        options.addAll(presetOptions.take(MAX_OPTIONS))
        ui = WheelUi.Idle
        winnerIdx = -1
        editingIndex = -1
    }

    fun addOption() {
        val text = newOption.trim()
        if (parseWheelOption(text).label.isNotEmpty() &&
            options.size < MAX_OPTIONS && !options.contains(text)
        ) {
            options.add(text)
            newOption = ""
            winnerIdx = -1
        }
    }

    fun saveEdit(index: Int) {
        val text = editText.trim()
        if (parseWheelOption(text).label.isNotEmpty() &&
            (text == options[index] || !options.contains(text))
        ) {
            options[index] = text
            editingIndex = -1
            winnerIdx = -1
            ui = WheelUi.Idle
        }
    }

    fun spin(overrideSource: RandomSource? = null) {
        if (ui is WheelUi.Spinning || options.size < 2) return
        ui = WheelUi.Spinning
        winnerIdx = -1
        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            segmentPop.snapTo(0f)
            // 跟手：点击的瞬间转盘立刻开始快速匀速旋转，后台同时获取随机数
            launch {
                rotation.animateTo(
                    targetValue = rotation.value + 3_600_000f,
                    animationSpec = tween(durationMillis = 5_000_000, easing = LinearEasing),
                )
            }
            try {
                usedLabel = if (overrideSource != null) overrideSource.shortName else viewModel.sourceLabel
                usedNetwork = if (overrideSource != null) overrideSource.isNetwork else viewModel.needsNetwork
                // 按权重抽选：在 [0, 总权重) 取随机数，落入哪个累计区间即中哪项
                val parsedNow = options.map { parseWheelOption(it) }
                val weightsNow = parsedNow.map { it.weight }
                val total = weightsNow.sum().coerceAtLeast(1)
                val result = viewModel.generate(0, total - 1, 1, overrideSource = overrideSource)
                val v = result.values.first()
                var acc = 0
                var winnerIndex = 0
                for ((i, w) in weightsNow.withIndex()) {
                    if (v < acc + w) { winnerIndex = i; break }
                    acc += w
                }
                // 中奖扇区角度（扇区宽度与权重成正比）
                val startAngle = 360f * weightsNow.take(winnerIndex).sum() / total
                val sweep = 360f * weightsNow[winnerIndex] / total
                val targetWithin = 360f - (startAngle + sweep / 2f)
                val current = rotation.value
                val target = (kotlin.math.ceil((current + 720f - targetWithin) / 360f)) * 360f + targetWithin
                rotation.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 2200, easing = LinearOutSlowInEasing),
                )
                val winner = parsedNow[winnerIndex].label
                spinCount++
                winnerIdx = winnerIndex
                ui = WheelUi.Done(winner, result.detail, 100f * weightsNow[winnerIndex] / total)
                if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                // 中奖强调：中奖扇区弹性凸出 + 自动滚动展示结果卡片
                launch { scrollState.animateScrollTo(scrollState.maxValue) }
                segmentPop.animateTo(
                    1f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
                viewModel.addHistory(
                    HistoryRecord(
                        id = System.currentTimeMillis(),
                        type = RecordType.WHEEL,
                        title = "大转盘（${options.size} 项）",
                        result = winner,
                        source = usedLabel,
                        detail = result.detail,
                        timestamp = System.currentTimeMillis(),
                    )
                )
            } catch (e: Exception) {
                // 失败时也平滑减速停下，避免突然冻住
                val current = rotation.value
                rotation.animateTo(
                    targetValue = kotlin.math.ceil(current / 360f) * 360f,
                    animationSpec = tween(durationMillis = 900, easing = LinearOutSlowInEasing),
                )
                ui = WheelUi.Error(e.message ?: "生成失败")
            }
        }
    }

    Scaffold(
        topBar = {
            PageHeader(
                title = "大转盘",
                subtitle = "多选项抽选",
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SourceBadgeLabel(
                label = usedLabel,
                modifier = Modifier.clickable(onClick = onOpenSource),
            )
            Spacer(Modifier.height(16.dp))

            // 转盘
            val wheelColors = wheelPalette()
            Box(modifier = Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                val density = LocalDensity.current
                val hubColor = MaterialTheme.colorScheme.surfaceContainerHigh
                val accentColor = MaterialTheme.colorScheme.primary
                val pointerBorder = MaterialTheme.colorScheme.surface
                val divider = MaterialTheme.colorScheme.surface
                // 解析后的选项（含权重），扇区角度与权重成正比
                val parsed = options.map { parseWheelOption(it) }
                val weights = parsed.map { it.weight }
                val totalWeight = weights.sum().coerceAtLeast(1)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = rotation.value
                        }
                ) {
                    val n = options.size.coerceAtLeast(1)
                    // 预留凸出余量，避免弹出的扇区被裁剪
                    val maxPop = 12.dp.toPx()
                    val radius = size.minDimension / 2f - maxPop
                    val center = Offset(size.width / 2f, size.height / 2f)
                    var angleAcc = 0f
                    for (i in 0 until n) {
                        val segment = 360f * weights[i] / totalWeight
                        val start = angleAcc - 90f
                        angleAcc += segment
                        val segColor = wheelColors[i % wheelColors.size]
                        // 中奖扇区沿角平分线方向向外凸出
                        val midRad = Math.toRadians((start + segment / 2f).toDouble())
                        val pop = if (i == winnerIdx) segmentPop.value * maxPop else 0f
                        val dx = (pop * Math.cos(midRad)).toFloat()
                        val dy = (pop * Math.sin(midRad)).toFloat()
                        val segCenter = center + Offset(dx, dy)
                        val rect = androidx.compose.ui.geometry.Rect(
                            segCenter - Offset(radius, radius),
                            Size(radius * 2, radius * 2)
                        )
                        drawArc(
                            color = segColor,
                            startAngle = start,
                            sweepAngle = segment,
                            useCenter = true,
                            topLeft = rect.topLeft,
                            size = rect.size,
                        )
                        drawArc(
                            color = divider.copy(alpha = 0.7f),
                            startAngle = start,
                            sweepAngle = segment,
                            useCenter = true,
                            topLeft = rect.topLeft,
                            size = rect.size,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                        // 文字：根据扇区亮度自动选择黑/白，保证可读性；随扇区一起凸出
                        val optLabel = parsed[i].label
                        val display = (if (optLabel.length > 5) optLabel.take(4) + "…" else optLabel) +
                            (if (parsed[i].weight > 1) " ×${parsed[i].weight}" else "")
                        // 扇区越窄字越小，避免溢出
                        val fontSp = when {
                            segment >= 30f -> 14f
                            segment >= 18f -> 12f
                            else -> 10f
                        }
                        val textColor = if (segColor.luminance() > 0.35f)
                            android.graphics.Color.argb(255, 20, 32, 26)
                        else android.graphics.Color.WHITE
                        val textRadius = radius * 0.62f
                        val tx = segCenter.x + (textRadius * Math.cos(midRad)).toFloat()
                        val ty = segCenter.y + (textRadius * Math.sin(midRad)).toFloat()
                        rotate(degrees = start + segment / 2f + 90f, pivot = Offset(tx, ty)) {
                            drawContext.canvas.nativeCanvas.drawText(
                                display,
                                tx,
                                ty + with(density) { 5.dp.toPx() },
                                android.graphics.Paint().apply {
                                    color = textColor
                                    textSize = with(density) { fontSp.sp.toPx() }
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isAntiAlias = true
                                    isFakeBoldText = true
                                },
                            )
                        }
                    }
                    // 中心圆
                    drawCircle(color = hubColor, radius = radius * 0.16f, center = center)
                    drawCircle(
                        color = accentColor,
                        radius = radius * 0.16f,
                        center = center,
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
                // 顶部指针：主色填充 + 背景色描边，在任意扇区颜色上都清晰可读
                Canvas(
                    modifier = Modifier
                        .size(280.dp)
                ) {
                    val cx = size.width / 2f
                    val pointer = Path().apply {
                        moveTo(cx - 14.dp.toPx(), (-4).dp.toPx())
                        lineTo(cx + 14.dp.toPx(), (-4).dp.toPx())
                        lineTo(cx, 30.dp.toPx())
                        close()
                    }
                    drawPath(pointer, color = accentColor)
                    drawPath(
                        pointer,
                        color = pointerBorder,
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 状态提示行（固定高度；旋转本身就是反馈，不再显示加载文案）
            Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                if (ui is WheelUi.Idle) {
                    Text(
                        if (options.size < 2) "至少添加 2 个选项才能开始" else "点击下方按钮开始抽选",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 结果区：在“再转一次”上方，出结果时按钮自然下移（动态布局）
            when (val state = ui) {
                is WheelUi.Done -> {
                    ResultReveal(triggerKey = spinCount, emphasized = true) {
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
                                    "🎉 ${state.winner}",
                                    style = EmphasizedResultMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "中选概率 ${"%.1f".format(state.prob)}% · 来源：$usedLabel · ${state.detail}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                is WheelUi.Error -> {
                    ErrorCard(
                        message = state.message,
                        onRetry = { spin() },
                        onFallbackLocal = if (viewModel.enabledSources != listOf(RandomSource.LOCAL)) {
                            { spin(RandomSource.LOCAL) }
                        } else null,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                else -> {}
            }

            // 主操作按钮
            Button(
                onClick = { spin() },
                enabled = ui !is WheelUi.Spinning && options.size >= 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape,
            ) {
                Icon(Icons.Default.TrackChanges, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (ui is WheelUi.Done) "再转一次" else "开始旋转", style = EmphasizedLabel)
            }

            Spacer(Modifier.height(12.dp))

            // 选项编辑入口：永远在最下方
            FilledTonalButton(
                onClick = { showOptionsSheet = true },
                enabled = ui !is WheelUi.Spinning,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("编辑选项（${options.size}/$MAX_OPTIONS）", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(96.dp))
        }
    }

    // 选项管理 Bottom Sheet
    if (showOptionsSheet) {
        val sheetWheelColors = wheelPalette()
        ModalBottomSheet(
            onDismissRequest = { showOptionsSheet = false; editingIndex = -1 },
            sheetState = sheetState,
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    "编辑选项",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "共 ${options.size}/$MAX_OPTIONS 项 · 至少保留 2 项 · 名称后加 x数字 可设倍率，如“火锅x2”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = newOption,
                    onValueChange = { newOption = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入新选项，如：火锅 或 火锅x2") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addOption() }),
                    trailingIcon = {
                        IconButton(
                            onClick = { addOption() },
                            enabled = newOption.trim().isNotEmpty() && options.size < MAX_OPTIONS,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "添加选项")
                        }
                    },
                )
                Spacer(Modifier.height(16.dp))

                // Expressive 列表行：色点 + 文字（点击可编辑）+ 删除按钮
                val sheetParsed = options.map { parseWheelOption(it) }
                val sheetTotal = sheetParsed.sumOf { it.weight }.coerceAtLeast(1)
                options.forEachIndexed { index, option ->
                    val p = sheetParsed[index]
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                    ) {
                        if (editingIndex == index) {
                            // 编辑模式：内联输入框
                            OutlinedTextField(
                                value = editText,
                                onValueChange = { editText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { saveEdit(index) }),
                                trailingIcon = {
                                    Row {
                                        IconButton(
                                            onClick = { saveEdit(index) },
                                            enabled = editText.trim().isNotEmpty(),
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = "保存修改")
                                        }
                                        IconButton(onClick = { editingIndex = -1 }) {
                                            Icon(Icons.Default.Close, contentDescription = "取消修改")
                                        }
                                    }
                                },
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editingIndex = index
                                        editText = option
                                    }
                                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Canvas(modifier = Modifier.size(12.dp)) {
                                    drawCircle(color = sheetWheelColors[index % sheetWheelColors.size])
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            p.label,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        if (p.weight > 1) {
                                            Spacer(Modifier.size(6.dp))
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                            ) {
                                                Text(
                                                    "×${p.weight}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        "概率 ${"%.1f".format(100f * p.weight / sheetTotal)}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "编辑 ${p.label}",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.outline,
                                )
                                IconButton(
                                    onClick = {
                                        if (options.size > 2) {
                                            options.removeAt(index)
                                            ui = WheelUi.Idle
                                            winnerIdx = -1
                                            editingIndex = -1
                                        }
                                    },
                                    enabled = options.size > 2,
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除 ${p.label}",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (options.size > 2) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ---- 预设区 ----
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "我的预设",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            if (wheelPresets.isEmpty()) "保存当前选项，下次一键套用"
                            else "点击预设即可套用到转盘",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(
                        onClick = { showSaveInput = !showSaveInput },
                        shape = CircleShape,
                    ) {
                        Icon(
                            Icons.Default.BookmarkAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("保存当前", style = MaterialTheme.typography.labelLarge)
                    }
                }

                // 保存输入行
                AnimatedVisibility(visible = showSaveInput) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = presetName,
                            onValueChange = { presetName = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("预设名称，如：午餐吃什么") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { savePreset() }),
                            trailingIcon = {
                                IconButton(
                                    onClick = { savePreset() },
                                    enabled = presetName.trim().isNotEmpty(),
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "保存预设")
                                }
                            },
                        )
                    }
                }

                if (wheelPresets.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    wheelPresets.forEach { preset ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            onClick = { applyPreset(preset.options) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Bookmark,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp),
                                ) {
                                    Text(
                                        preset.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        preset.options.joinToString("、") { parseWheelOption(it).label }
                                            .let { if (it.length > 24) it.take(24) + "…" else it }
                                            + "（${preset.options.size} 项）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteWheelPreset(preset.id) }) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "删除预设 ${preset.name}",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
