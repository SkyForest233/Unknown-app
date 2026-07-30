package com.agon.app.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.agon.app.R
import com.agon.app.data.CoinStyle
import com.agon.app.ui.components.CheckSwitch
import com.agon.app.ui.components.PageHeader
import com.agon.app.ui.components.SectionTitle
import com.agon.app.ui.theme.LightPrimary
import com.agon.app.ui.theme.ThemeSeedColors
import com.agon.app.ui.theme.supportsDynamicColor
import com.agon.app.viewmodel.AppViewModel

private val themeLabels = listOf("跟随系统", "浅色", "深色")

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onNavigateSource: () -> Unit,
    onShowOnboarding: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val history by viewModel.history.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var themeMenuExpanded by remember { mutableStateOf(false) }
    var coinMenuExpanded by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空历史记录？") },
            text = { Text("将删除全部 ${history.size} 条记录，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearDialog = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }

    Scaffold(
        topBar = {
            PageHeader(title = "设置", subtitle = "随机 Unknown · 1.0")
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val appearanceRows = if (supportsDynamicColor) 3 else 2
            val dynamicOn = settings?.dynamicColor ?: false
            item { SectionTitle("外观") }
            item {
                SettingRow(
                    icon = Icons.Default.DarkMode,
                    title = "主题",
                    subtitle = "选择应用的主题模式",
                    shape = groupShape(0, appearanceRows),
                    trailing = {
                        Box {
                            Text(
                                themeLabels[settings?.themeMode ?: 0],
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            DropdownMenu(
                                expanded = themeMenuExpanded,
                                onDismissRequest = { themeMenuExpanded = false },
                                shape = MaterialTheme.shapes.large,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                themeLabels.forEachIndexed { index, label ->
                                    val selected = (settings?.themeMode ?: 0) == index
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                label,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            )
                                        },
                                        leadingIcon = {
                                            if (selected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            } else {
                                                Spacer(Modifier.size(20.dp))
                                            }
                                        },
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                            .clip(MaterialTheme.shapes.medium)
                                            .then(
                                                if (selected) Modifier.background(
                                                    MaterialTheme.colorScheme.primaryContainer
                                                ) else Modifier
                                            ),
                                        onClick = {
                                            viewModel.setThemeMode(index)
                                            themeMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                    onClick = { themeMenuExpanded = true },
                )
            }
            if (supportsDynamicColor) {
                item {
                    SettingRow(
                        icon = Icons.Default.Colorize,
                        title = "动态取色",
                        subtitle = "跟随壁纸生成配色（Material You）",
                        shape = groupShape(1, appearanceRows),
                        trailing = {
                            CheckSwitch(
                                checked = dynamicOn,
                                onCheckedChange = { viewModel.setDynamicColor(it) },
                            )
                        },
                        onClick = { viewModel.setDynamicColor(!dynamicOn) },
                    )
                }
            }
            // 配色方案选择器（学习自 Tomato：种子色色盘 + 横向滑动）
            item {
                ColorSchemePickerRow(
                    shape = groupShape(appearanceRows - 1, appearanceRows),
                    currentSeed = settings?.seedColor ?: 0L,
                    enabled = !dynamicOn,
                    onSeedChange = { viewModel.setSeedColor(it) },
                )
            }

            item { Spacer(Modifier.height(10.dp)) }
            item { SectionTitle("随机") }
            item {
                // 组合标签可能很长（如"本地 + drand + random.org"），
                // 放到 subtitle 换行显示，trailing 只留箭头，避免挤压标题
                val sourceLabel = remember(settings) { viewModel.sourceLabel }
                SettingRow(
                    icon = Icons.Outlined.Casino,
                    title = "默认随机源",
                    subtitle = sourceLabel,
                    subtitleColor = MaterialTheme.colorScheme.primary,
                    shape = groupShape(0, 3),
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    },
                    onClick = onNavigateSource,
                )
            }
            item {
                val currentCoinStyle = try {
                    CoinStyle.valueOf(settings?.coinStyle ?: CoinStyle.CAT.name)
                } catch (e: Exception) {
                    CoinStyle.CAT
                }
                SettingRow(
                    icon = Icons.Filled.Pets,
                    title = "硬币样式",
                    subtitle = "抛硬币使用的硬币外观",
                    shape = groupShape(1, 3),
                    trailing = {
                        Box {
                            Text(
                                currentCoinStyle.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            DropdownMenu(
                                expanded = coinMenuExpanded,
                                onDismissRequest = { coinMenuExpanded = false },
                                shape = MaterialTheme.shapes.large,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                CoinStyle.entries.forEach { style ->
                                    val selected = style == currentCoinStyle
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (style == CoinStyle.CAT) {
                                                    Image(
                                                        painter = painterResource(R.drawable.coin_cat_heads),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(28.dp),
                                                    )
                                                    Spacer(Modifier.size(10.dp))
                                                }
                                                Text(
                                                    style.displayName,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            if (selected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            } else {
                                                Spacer(Modifier.size(20.dp))
                                            }
                                        },
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                            .clip(MaterialTheme.shapes.medium)
                                            .then(
                                                if (selected) Modifier.background(
                                                    MaterialTheme.colorScheme.primaryContainer
                                                ) else Modifier
                                            ),
                                        onClick = {
                                            viewModel.setCoinStyle(style.name)
                                            coinMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                    onClick = { coinMenuExpanded = true },
                )
            }
            item {
                SettingRow(
                    icon = Icons.Default.Vibration,
                    title = "振动反馈",
                    subtitle = "生成结果时轻微振动",
                    shape = groupShape(2, 3),
                    trailing = {
                        CheckSwitch(
                            checked = settings?.haptics ?: true,
                            onCheckedChange = { viewModel.setHaptics(it) },
                        )
                    },
                    onClick = { viewModel.setHaptics(!(settings?.haptics ?: true)) },
                )
            }

            item { Spacer(Modifier.height(10.dp)) }
            item { SectionTitle("数据") }
            item {
                SettingRow(
                    icon = Icons.Default.DeleteOutline,
                    title = "清空历史记录",
                    subtitle = "当前共 ${history.size} 条记录",
                    shape = groupShape(0, 1),
                    iconTint = MaterialTheme.colorScheme.error,
                    enabled = history.isNotEmpty(),
                    onClick = { showClearDialog = true },
                )
            }

            item { Spacer(Modifier.height(10.dp)) }
            item { SectionTitle("关于") }
            item {
                SettingRow(
                    icon = Icons.Outlined.School,
                    title = "重看新手引导",
                    subtitle = "了解三种随机源与功能",
                    shape = groupShape(0, 2),
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    },
                    onClick = onShowOnboarding,
                )
            }
            item {
                SettingRow(
                    icon = Icons.Outlined.Info,
                    title = "随机 Unknown 1.0",
                    subtitle = "random.org · Drand · 本地 SecureRandom，网络请求仅用于获取随机数，不收集任何个人数据",
                    shape = groupShape(1, 2),
                    onClick = null,
                )
            }
        }
    }
}

/**
 * 配色方案选择器（学习自 Tomato 的 ColorSchemePickerListItem）：
 * 标题行 + 横向滑动的种子色圆钮列表，选中项打勾。
 * 首位是默认品牌薄荷绿，其后 12 种 Tomato 预设种子色。
 */
@Composable
private fun ColorSchemePickerRow(
    shape: RoundedCornerShape,
    currentSeed: Long,
    enabled: Boolean,
    onSeedChange: (Long) -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.outline,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                ) {
                    Text(
                        "配色方案",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        if (enabled) "选择主题种子色" else "已启用动态取色，配色跟随壁纸",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = true,
            ) {
                // 默认品牌色放首位
                item {
                    ColorPickerButton(
                        color = LightPrimary,
                        isSelected = currentSeed == 0L,
                        enabled = enabled,
                        onClick = { onSeedChange(0L) },
                    )
                }
                items(ThemeSeedColors.size) { i ->
                    val seed = ThemeSeedColors[i]
                    ColorPickerButton(
                        color = Color(seed),
                        isSelected = currentSeed == seed,
                        enabled = enabled,
                        onClick = { onSeedChange(seed) },
                    )
                }
            }
        }
    }
}

/** 种子色圆钮（学习自 Tomato 的 ColorPickerButton） */
@Composable
private fun ColorPickerButton(
    color: Color,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.3f
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = color.copy(alpha = alpha),
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "已选择",
                    tint = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** 参考风格：组内条目使用大圆角首尾 + 小圆角中间的分组卡片 */
private fun groupShape(index: Int, count: Int): RoundedCornerShape {
    val big = 24.dp
    val small = 6.dp
    val top = if (index == 0) big else small
    val bottom = if (index == count - 1) big else small
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    shape: RoundedCornerShape,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)?,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick)
                else Modifier
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (enabled) iconTint else MaterialTheme.colorScheme.outline,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.outline,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                )
            }
            if (trailing != null) trailing()
        }
    }
}
