package com.agon.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.res.painterResource
import com.agon.app.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Text
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath

/**
 * M3 Expressive Shape-Morph 硬币。
 *
 * 正面 = 12 齿扇贝形（cookie 形，同 Android 15 开机动画 / M3 loading indicator）
 * 反面 = 正圆形
 *
 * [rotation]  绕 Y 轴的翻转角度（度）。侧立（90°/270°附近）时形状被透视收窄。
 * [settleScale] 落定时的 spring 回弹缩放（由调用方驱动，默认 1f）。
 */
@Composable
fun MorphCoin(
    rotation: Float,
    modifier: Modifier = Modifier,
    settleScale: Float = 1f,
) {
    // 归一化角度，判断当前朝向哪一面
    val normalized = ((rotation % 360f) + 360f) % 360f
    val showHeads = normalized < 90f || normalized > 270f

    // Morph 进度：正面 cookie(0) -> 反面圆形(1)。
    // 余弦映射：0°→0，90°/270°（侧立）→0.5，180°→1，全程平滑连续。
    val morphProgress =
        ((1f - kotlin.math.cos(Math.toRadians(normalized.toDouble()))) / 2f)
            .toFloat()
            .coerceIn(0f, 1f)

    // 扇贝形（cookie 12 齿）与圆形，radius 单位为 1，中心 (0,0)
    val morph = remember {
        val cookie = RoundedPolygon.star(
            numVerticesPerRadius = 12,
            radius = 1f,
            innerRadius = 0.85f,
            rounding = CornerRounding(radius = 0.18f),
        )
        val circle = RoundedPolygon.circle(numVertices = 12, radius = 1f)
        Morph(start = cookie, end = circle)
    }

    // 主题色：正面 primary 系（品牌绿），反面 tertiary 系
    val headsMain = MaterialTheme.colorScheme.primaryContainer
    val headsEdge = MaterialTheme.colorScheme.primary
    val headsText = MaterialTheme.colorScheme.onPrimaryContainer
    val tailsMain = MaterialTheme.colorScheme.tertiaryContainer
    val tailsEdge = MaterialTheme.colorScheme.tertiary
    val tailsText = MaterialTheme.colorScheme.onTertiaryContainer

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                    scaleX = settleScale
                    scaleY = settleScale
                }
        ) {
            val r = size.minDimension / 2f * 0.92f
            val cx = size.width / 2f
            val cy = size.height / 2f

            val main = if (showHeads) headsMain else tailsMain
            val edge = if (showHeads) headsEdge else tailsEdge

            // 当前 morph 形状路径：graphics-shapes 输出单位多边形，映射到画布中心
            val path: Path = morph.toPath(progress = morphProgress).asComposePath()
            val matrix = androidx.compose.ui.graphics.Matrix()
            matrix.translate(cx, cy)
            matrix.scale(r, r)
            path.transform(matrix)

            // 主体渐变填充
            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(main, edge.copy(alpha = 0.55f)),
                    center = Offset(cx - r * 0.3f, cy - r * 0.3f),
                    radius = r * 1.7f,
                ),
            )
            // 内圈描边（跟随形状）
            drawPath(
                path = path,
                color = edge,
                style = Stroke(width = r * 0.045f),
            )
        }

        // 文字仅在接近正/反面平放时显示，侧立时隐藏
        val textVisible = normalized < 70f || normalized > 290f ||
            (normalized in 110f..250f)
        if (textVisible) {
            Text(
                text = if (showHeads) "正" else "反",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = if (showHeads) headsText else tailsText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 猫猫硬币：正面猫猫头像（HEADS）/ 反面猫爪（TAILS）位图样式。
 * 与 [MorphCoin] 相同的翻转参数协议，可在设置中切换。
 * 反面图片预翻转，使其在 rotationY 背面时显示为正像。
 */
@Composable
fun CatCoin(
    rotation: Float,
    modifier: Modifier = Modifier,
    settleScale: Float = 1f,
) {
    val normalized = ((rotation % 360f) + 360f) % 360f
    val showHeads = normalized < 90f || normalized > 270f
    // 硬币正对镜头的程度：1 = 平放，0 = 侧立（用于地面阴影收窄）
    val facing = kotlin.math.abs(kotlin.math.cos(Math.toRadians(normalized.toDouble()))).toFloat()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // 自然的地面柔影：不随硬币旋转，侧立时横向收窄变淡
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val shadowWidth = w * (0.30f + 0.42f * facing) * settleScale
            val shadowHeight = h * 0.07f
            val cx = w / 2f
            val cy = h * 0.97f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.18f + 0.10f * facing),
                        Color.Black.copy(alpha = 0f),
                    ),
                    center = Offset(cx, cy),
                    radius = shadowWidth / 2f,
                ),
                topLeft = Offset(cx - shadowWidth / 2f, cy - shadowHeight / 2f),
                size = androidx.compose.ui.geometry.Size(shadowWidth, shadowHeight),
            )
        }
        Image(
            painter = painterResource(
                if (showHeads) R.drawable.coin_cat_heads else R.drawable.coin_cat_tails
            ),
            contentDescription = if (showHeads) "猫猫硬币正面" else "猫猫硬币反面",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                    scaleX = settleScale * if (showHeads) 1f else -1f
                    scaleY = settleScale
                },
        )
    }
}
