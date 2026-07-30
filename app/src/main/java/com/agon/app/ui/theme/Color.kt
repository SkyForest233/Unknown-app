package com.agon.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// ------- Light (mint / forest green style) -------
val LightPrimary = Color(0xFF1F5C4D)          // 深森林绿
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFB5E4C9) // 薄荷绿容器
val LightOnPrimaryContainer = Color(0xFF0E3328)
val LightSecondary = Color(0xFF33566B)        // 深蓝绿
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFC5E7F5) // 淡蓝容器
val LightOnSecondaryContainer = Color(0xFF0B2733)
val LightTertiary = Color(0xFF3E8E7E)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFD4EDE0)
val LightOnTertiaryContainer = Color(0xFF12362C)
val LightError = Color(0xFFB3261E)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFF9DEDC)
val LightOnErrorContainer = Color(0xFF410E0B)
val LightBackground = Color(0xFFD9EBDF)       // 薄荷绿画布
val LightOnBackground = Color(0xFF17251F)
val LightSurface = Color(0xFFD9EBDF)
val LightOnSurface = Color(0xFF17251F)
val LightSurfaceVariant = Color(0xFFCBE0D2)
val LightOnSurfaceVariant = Color(0xFF43514A)
val LightOutline = Color(0xFF6F7E75)
val LightOutlineVariant = Color(0xFFBFD2C5)
val LightSurfaceContainerLowest = Color(0xFFCDE3D5)
val LightSurfaceContainerLow = Color(0xFFE4F1E8)
val LightSurfaceContainer = Color(0xFFF4FAF5)      // 卡片米白
val LightSurfaceContainerHigh = Color(0xFFFDFFFD)  // 更亮卡片
val LightSurfaceContainerHighest = Color(0xFFFFFFFF)
val LightInverseSurface = Color(0xFF2C3A32)
val LightInverseOnSurface = Color(0xFFE9F2EB)
val LightInversePrimary = Color(0xFF8FD5BC)

// ------- Dark -------
val DarkPrimary = Color(0xFF8FD5BC)
val DarkOnPrimary = Color(0xFF0E3A2D)
val DarkPrimaryContainer = Color(0xFF1F5245)
val DarkOnPrimaryContainer = Color(0xFFBEE8D2)
val DarkSecondary = Color(0xFF9CCBE3)
val DarkOnSecondary = Color(0xFF10323F)
val DarkSecondaryContainer = Color(0xFF2B4655)
val DarkOnSecondaryContainer = Color(0xFFC5E7F5)
val DarkTertiary = Color(0xFF9BD0C1)
val DarkOnTertiary = Color(0xFF12362C)
val DarkTertiaryContainer = Color(0xFF2A4A40)
val DarkOnTertiaryContainer = Color(0xFFD4EDE0)
val DarkError = Color(0xFFF2B8B5)
val DarkOnError = Color(0xFF601410)
val DarkErrorContainer = Color(0xFF8C1D18)
val DarkOnErrorContainer = Color(0xFFF9DEDC)
val DarkBackground = Color(0xFF0E1512)
val DarkOnBackground = Color(0xFFDDE6DF)
val DarkSurface = Color(0xFF0E1512)
val DarkOnSurface = Color(0xFFDDE6DF)
val DarkSurfaceVariant = Color(0xFF3C4941)
val DarkOnSurfaceVariant = Color(0xFFB9C6BC)
val DarkOutline = Color(0xFF84918A)
val DarkOutlineVariant = Color(0xFF3C4941)
val DarkSurfaceContainerLowest = Color(0xFF0A100D)
val DarkSurfaceContainerLow = Color(0xFF141B17)
val DarkSurfaceContainer = Color(0xFF19211C)
val DarkSurfaceContainerHigh = Color(0xFF232D26)
val DarkSurfaceContainerHighest = Color(0xFF2E3831)
val DarkInverseSurface = Color(0xFFDDE6DF)
val DarkInverseOnSurface = Color(0xFF2C3A32)
val DarkInversePrimary = Color(0xFF1F5C4D)

// Wheel segment palette — 从当前 MaterialTheme 配色动态派生，
// 跟随浅色/深色/动态取色主题变化，白字始终可读（相邻色深浅交替）
@Composable
fun wheelPalette(): List<Color> {
    val cs = MaterialTheme.colorScheme
    val dark = Color(0xFF14201A)
    val light = Color(0xFFFFFFFF)
    return remember(cs.primary, cs.secondary, cs.tertiary) {
        listOf(
            lerp(cs.primary, dark, 0.15f),
            lerp(cs.tertiary, light, 0.12f),
            lerp(cs.secondary, dark, 0.25f),
            lerp(cs.primary, light, 0.18f),
            lerp(cs.tertiary, dark, 0.30f),
            lerp(cs.secondary, light, 0.10f),
            lerp(cs.primary, dark, 0.40f),
            lerp(cs.tertiary, light, 0.30f).let { lerp(it, cs.secondary, 0.35f) },
        )
    }
}

// Coin colors
val CoinGold = Color(0xFFE9B949)
val CoinGoldDark = Color(0xFFB68912)
val CoinSilver = Color(0xFFAFBDC6)
val CoinSilverDark = Color(0xFF75838F)

// 功能语义色（集中定义，避免屏幕内硬编码）
val StatusOnline = Color(0xFF34A853)   // 在线
val StatusChecking = Color(0xFFF9AB00) // 检测中
val CategoryCoin = CoinGoldDark        // 历史分类：硬币
val CategoryWheel = Color(0xFF33566B)  // 历史分类：转盘
val CategoryNumber = Color(0xFF1F5C4D) // 历史分类：随机数
