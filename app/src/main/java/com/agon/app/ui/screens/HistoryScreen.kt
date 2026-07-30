package com.agon.app.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.agon.app.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.data.RecordType
import com.agon.app.ui.components.CircleIconButton
import com.agon.app.ui.theme.CategoryCoin
import com.agon.app.ui.theme.CategoryNumber
import com.agon.app.ui.theme.CategoryWheel
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.PageHeader
import com.agon.app.ui.components.formatTime
import com.agon.app.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: AppViewModel) {
    val history by viewModel.history.collectAsState()
    var filter by remember { mutableStateOf<String?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    val filtered = if (filter == null) history else history.filter { it.type == filter }

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
            PageHeader(
                title = "历史",
                subtitle = "共 ${history.size} 条记录",
                actions = {
                    if (history.isNotEmpty()) {
                        CircleIconButton(
                            icon = Icons.Default.DeleteOutline,
                            contentDescription = "清空记录",
                            onClick = { showClearDialog = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 筛选器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null },
                    label = { Text("全部") },
                )
                FilterChip(
                    selected = filter == RecordType.COIN,
                    onClick = { filter = RecordType.COIN },
                    label = { Text("硬币") },
                )
                FilterChip(
                    selected = filter == RecordType.WHEEL,
                    onClick = { filter = RecordType.WHEEL },
                    label = { Text("转盘") },
                )
                FilterChip(
                    selected = filter == RecordType.NUMBER,
                    onClick = { filter = RecordType.NUMBER },
                    label = { Text("随机数") },
                )
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.History,
                        title = if (history.isEmpty()) "暂无记录" else "该分类下暂无记录",
                        subtitle = "使用抛硬币、大转盘或随机数工具后，结果会自动保存在这里。",
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered.size) { i ->
                        val record = filtered[i]
                        val (icon, tint) = when (record.type) {
                            RecordType.COIN -> Icons.Default.MonetizationOn to CategoryCoin
                            RecordType.WHEEL -> Icons.Default.TrackChanges to CategoryWheel
                            else -> Icons.Default.Tag to CategoryNumber
                        }
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
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (record.type == RecordType.COIN) {
                                    // 猫猫硬币：正面猫头 / 反面猫爪
                                    Image(
                                        painter = painterResource(
                                            if (record.result == "正面") R.drawable.coin_cat_heads
                                            else R.drawable.coin_cat_tails
                                        ),
                                        contentDescription = record.result,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = tint.copy(alpha = 0.15f),
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                icon,
                                                contentDescription = null,
                                                tint = tint,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp)
                                ) {
                                    Text(
                                        record.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        record.result,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "${record.source} · ${record.detail}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Text(
                                    formatTime(record.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
