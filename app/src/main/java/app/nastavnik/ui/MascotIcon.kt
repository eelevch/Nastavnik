package app.nastavnik.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Процедурный векторный маскот-бот в стиле Grok (120 FPS, идеально чёткий в любом размере
 * и автоматически подстраивается под светлую и тёмную темы через [Brand.Ink] / [Brand.Paper] / [Brand.Accent]):
 * - Пока человек пишет конспект ([writing]): улыбается и машет рукой.
 * - Пока наставник думает и отвечает ([thinking]): вращает орбитальное кольцо и пишет стилусом.
 * - В покое: стильный геометричный бот с диагональным орбитальным кольцом и мягким морганием.
 */
@Composable
fun MascotIcon(
    writing: Boolean = false,
    thinking: Boolean = false,
    sizeDp: Dp = 26.dp,
    modifier: Modifier = Modifier,
) {
    val ink = Brand.Ink
    val paper = Brand.Paper
    val accent = Brand.Accent

    val infinite = rememberInfiniteTransition(label = "grokBot")
    val wavePhase by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(320, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wave",
    )
    val thinkPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "think",
    )
    val blinkPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "blink",
    )

    Canvas(modifier = modifier.size(sizeDp)) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.52f
        val r = size.minDimension * 0.36f

        // 1. Внешнее пульсирующее свечение при размышлении
        if (thinking) {
            val pulse = 0.16f + 0.14f * sin(thinkPhase * 2f * Math.PI).toFloat()
            drawCircle(
                color = accent.copy(alpha = pulse),
                radius = r * 1.28f,
                center = Offset(cx, cy),
            )
        }

        // 2. Основная сфера-голова бота (контрастная к фону темы)
        drawCircle(
            color = ink,
            radius = r,
            center = Offset(cx, cy),
        )

        // 3. Фирменное диагональное орбитальное кольцо в стиле Grok
        val ringAngle = if (thinking) -32f + thinkPhase * 360f else if (writing) -28f + wavePhase * 8f else -32f
        rotate(degrees = ringAngle, pivot = Offset(cx, cy)) {
            drawArc(
                color = accent,
                startAngle = -155f,
                sweepAngle = 200f,
                useCenter = false,
                topLeft = Offset(cx - r * 1.32f, cy - r * 0.44f),
                size = Size(r * 2.64f, r * 0.88f),
                style = Stroke(width = (r * 0.17f).coerceAtLeast(1.8f), cap = StrokeCap.Round),
            )
        }

        // 4. Выразительные глаза-визор
        val eyeY = cy - r * 0.06f
        val eyeOffX = r * 0.30f
        val eyeW = r * 0.22f
        val eyeH = r * 0.34f

        when {
            writing -> {
                // Радостные дуги ^ ^
                val stroke = Stroke(width = (r * 0.14f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
                for (sign in listOf(-1f, 1f)) {
                    val ex = cx + sign * eyeOffX
                    drawArc(
                        color = paper,
                        startAngle = 195f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(ex - eyeW * 0.65f, eyeY - eyeH * 0.35f),
                        size = Size(eyeW * 1.3f, eyeH * 0.9f),
                        style = stroke,
                    )
                }
                // Анимированная машущая рука справа
                val handPivot = Offset(cx + r * 0.92f, cy + r * 0.15f)
                rotate(degrees = 18f + wavePhase * 26f, pivot = handPivot) {
                    drawLine(
                        color = ink,
                        start = handPivot,
                        end = Offset(handPivot.x + r * 0.36f, handPivot.y - r * 0.52f),
                        strokeWidth = (r * 0.24f).coerceAtLeast(2.2f),
                        cap = StrokeCap.Round,
                    )
                    drawCircle(
                        color = accent,
                        radius = r * 0.13f,
                        center = Offset(handPivot.x + r * 0.36f, handPivot.y - r * 0.52f),
                    )
                }
            }

            thinking -> {
                // Задумчивый взгляд вниз-вправо + анимированный стилус
                val lookX = r * 0.10f + sin(thinkPhase * 2f * Math.PI).toFloat() * r * 0.06f
                for (sign in listOf(-1f, 1f)) {
                    val ex = cx + sign * eyeOffX + lookX
                    drawRoundRect(
                        color = paper,
                        topLeft = Offset(ex - eyeW * 0.5f, eyeY - eyeH * 0.35f),
                        size = Size(eyeW, eyeH * 0.8f),
                        cornerRadius = CornerRadius(eyeW * 0.5f, eyeW * 0.5f),
                    )
                }
                // Светящийся стилус пишет внизу справа
                val penShift = sin(thinkPhase * 4f * Math.PI).toFloat() * r * 0.16f
                val tip = Offset(cx + r * 0.55f + penShift, cy + r * 0.82f)
                val top = Offset(tip.x + r * 0.52f, tip.y - r * 0.58f)
                drawLine(
                    color = accent,
                    start = tip,
                    end = top,
                    strokeWidth = (r * 0.18f).coerceAtLeast(2f),
                    cap = StrokeCap.Round,
                )
                drawCircle(color = paper, radius = r * 0.08f, center = tip)
            }

            else -> {
                // Покой: аккуратные капсульные глаза с редким естественным морганием
                val isBlinking = blinkPhase > 0.94f
                val h = if (isBlinking) eyeH * 0.18f else eyeH
                for (sign in listOf(-1f, 1f)) {
                    val ex = cx + sign * eyeOffX
                    drawRoundRect(
                        color = paper,
                        topLeft = Offset(ex - eyeW * 0.5f, eyeY - h * 0.5f),
                        size = Size(eyeW, h),
                        cornerRadius = CornerRadius(eyeW * 0.5f, eyeW * 0.5f),
                    )
                }
            }
        }
    }
}

/** Круглый бейдж с живым маскотом для шапки панели наставника и карточек. */
@Composable
fun MascotBadge(
    writing: Boolean = false,
    thinking: Boolean = false,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier,
) {
    val bg = if (Brand.dark) Brand.Glass.copy(alpha = 0.6f) else Brand.Paper
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        MascotIcon(
            writing = writing,
            thinking = thinking,
            sizeDp = (size.value * 0.82f).dp,
        )
    }
}
