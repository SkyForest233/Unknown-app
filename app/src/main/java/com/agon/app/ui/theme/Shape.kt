package com.agon.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * MD3 形状 token（本应用整体走"更圆润"的表达风格）：
 * extraSmall 4dp（指示器等装饰）
 * small      8dp
 * medium     16dp（输入框、小卡片）
 * large      24dp（列表卡片）
 * extraLarge 28dp（主卡片、对话框）
 * 胶囊按钮使用 CircleShape（= full token）。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
