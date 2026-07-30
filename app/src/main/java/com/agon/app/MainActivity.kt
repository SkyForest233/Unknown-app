package com.agon.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.ui.screens.CoinScreen
import com.agon.app.ui.screens.HistoryScreen
import com.agon.app.ui.screens.NumberScreen
import com.agon.app.ui.screens.OnboardingScreen
import com.agon.app.ui.screens.SettingsScreen
import com.agon.app.ui.screens.SourceScreen
import com.agon.app.ui.screens.WheelScreen
import com.agon.app.ui.theme.AgonAppTheme
import com.agon.app.ui.theme.EmphasizedLabel
import com.agon.app.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: AppViewModel = viewModel()
            val settings by viewModel.settings.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings?.themeMode ?: 0) {
                1 -> false
                2 -> true
                else -> systemDark
            }
            AgonAppTheme(
                darkTheme = darkTheme,
                dynamicColor = settings?.dynamicColor ?: false,
                seedColor = settings?.seedColor ?: 0L,
            ) {
                MainApp(viewModel)
            }
        }
    }
}

private data class TabItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val tabs = listOf(
    TabItem("硬币", Icons.Filled.MonetizationOn, Icons.Outlined.MonetizationOn),
    TabItem("转盘", Icons.Filled.TrackChanges, Icons.Outlined.TrackChanges),
    TabItem("随机数", Icons.Filled.Tag, Icons.Outlined.Tag),
    TabItem("历史", Icons.Filled.History, Icons.Outlined.History),
    TabItem("设置", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainApp(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsState()
    var onboardingVisible by remember { mutableStateOf<Boolean?>(null) }
    var showSource by remember { mutableStateOf(false) }

    // 等待设置加载完成后决定是否展示引导
    val s = settings
    if (s == null) {
        Surface(modifier = Modifier.fillMaxSize()) {}
        return
    }
    if (onboardingVisible == null) {
        onboardingVisible = !s.onboardingDone
    }

    if (onboardingVisible == true) {
        OnboardingScreen(onDone = {
            viewModel.setOnboarded(true)
            onboardingVisible = false
        })
        return
    }

    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    val motionScheme = MaterialTheme.motionScheme

    Box(modifier = Modifier.fillMaxSize()) {
        // 页面内容区左右滑动切换 tab（与子屏幕的纵向滚动不冲突）
        var pageDrag by remember { mutableStateOf(0f) }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { pageDrag = 0f },
                        onDragEnd = {
                            if (kotlin.math.abs(pageDrag) > 90f) {
                                val next = currentTab + if (pageDrag < 0) 1 else -1
                                currentTab = next.coerceIn(0, tabs.size - 1)
                            }
                            pageDrag = 0f
                        },
                        onHorizontalDrag = { _, dragAmount -> pageDrag += dragAmount },
                    )
                },
        ) {
            // Tomato 式切换：淡入淡出 + 方向感知的轻微横向位移
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    val forward = targetState > initialState
                    (fadeIn(motionScheme.defaultEffectsSpec()) +
                        slideInHorizontally(motionScheme.defaultSpatialSpec()) {
                            if (forward) it / 10 else -it / 10
                        })
                        .togetherWith(fadeOut(motionScheme.defaultEffectsSpec()))
                },
                modifier = Modifier.fillMaxSize(),
                label = "tabContent",
            ) { page ->
                // MD3 自适应：宽屏（平板/横屏）时内容限宽居中，避免拉伸至全宽
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(modifier = Modifier.widthIn(max = 640.dp)) {
                        when (page) {
                            0 -> CoinScreen(viewModel = viewModel, onOpenSource = { showSource = true })
                            1 -> WheelScreen(viewModel = viewModel, onOpenSource = { showSource = true })
                            2 -> NumberScreen(viewModel = viewModel, onOpenSource = { showSource = true })
                            3 -> HistoryScreen(viewModel)
                            4 -> SettingsScreen(
                                viewModel = viewModel,
                                onNavigateSource = { showSource = true },
                                onShowOnboarding = { onboardingVisible = true },
                            )
                        }
                    }
                }
            }
        }

        // 悬浮胶囊导航：叠加在内容之上，无背景条，内容从其下方穿过
        Box(
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomNav(
                currentPage = currentTab,
                onSelect = { currentTab = it },
                onSwipe = { delta ->
                    currentTab = (currentTab + if (delta < 0) 1 else -1)
                        .coerceIn(0, tabs.size - 1)
                },
            )
        }

        // 随机源选择页：覆盖层，从右侧滑入，支持系统返回
        AnimatedVisibility(
            visible = showSource,
            enter = slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)),
        ) {
            BackHandler { showSource = false }
            Surface(modifier = Modifier.fillMaxSize()) {
                SourceScreen(viewModel = viewModel, onBack = { showSource = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomNav(
    currentPage: Int,
    onSelect: (Int) -> Unit,
    onSwipe: (Float) -> Unit,
) {
    // Tomato 式悬浮工具栏：居中 wrap-content 胶囊，无背景条，内容从下方穿过
    var dragAccum by remember { mutableStateOf(0f) }
    val motionScheme = MaterialTheme.motionScheme
    Box(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier.pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onDragEnd = {
                        if (kotlin.math.abs(dragAccum) > 40f) onSwipe(dragAccum)
                        dragAccum = 0f
                    },
                    onHorizontalDrag = { _, dragAmount -> dragAccum += dragAmount },
                )
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = currentPage == index
                    ToggleButton(
                        checked = selected,
                        onCheckedChange = { if (!selected) onSelect(index) },
                        colors = ToggleButtonDefaults.toggleButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shapes = ToggleButtonDefaults.shapes(
                            CircleShape,
                            CircleShape,
                            CircleShape,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(46.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Crossfade(selected, label = "navIcon") { checked ->
                                Icon(
                                    if (checked) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            AnimatedVisibility(
                                visible = selected,
                                enter = expandHorizontally(motionScheme.defaultSpatialSpec()) +
                                    fadeIn(motionScheme.defaultEffectsSpec()),
                                exit = shrinkHorizontally(motionScheme.defaultSpatialSpec()) +
                                    fadeOut(motionScheme.defaultEffectsSpec()),
                            ) {
                                Text(
                                    text = tab.label,
                                    style = EmphasizedLabel,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
