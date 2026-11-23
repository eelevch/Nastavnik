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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

private val MascotRingBlueLight = Color(0xFF2563EB)
private val MascotRingBlueDark = Color(0xFF3B82F6)

/**
 * Процедурный векторный маскот-бот в стиле Grok (120 FPS, всегда с фирменным синим кольцом):
 * - Постоянно живой: мягко покачивается, орбитальное синее кольцо плавно дышит, глаза естественно моргают.
 * - Пока человек пишет ([writing]): улыбается и машет рукой.
 * - Пока наставник думает ([thinking]): синее кольцо вращается, глаза задумчиво смотрят по сторонам.
 * - В голосовом режиме ([speaking] / [listening]): живые синие звуковые волны вокруг бота.
 */
@Composable
fun MascotIcon(
    writing: Boolean = false,
    thinking: Boolean = false,
    speaking: Boolean = false,
    listening: Boolean = false,
    voiceLevel: Float = 0f,
    sizeDp: Dp = 26.dp,
    modifier: Modifier = Modifier,
) {
    val ink = Brand.Ink
    val paper = Brand.Paper
    val ringBlue = if (Brand.dark) MascotRingBlueDark else MascotRingBlueLight

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
    val idlePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "idle",
    )
    val blinkPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing), RepeatMode.Restart),
        label = "blink",
    )

    Canvas(modifier = modifier.size(sizeDp)) {
        val floatY = sin(idlePhase * 2f * Math.PI).toFloat() * size.minDimension * 0.032f
        val cx = size.width * 0.5f
        val cy = size.height * 0.52f + floatY
        val r = size.minDimension * 0.35f

        // 1. Внешнее синее свечение при размышлении или в голосовом режиме
        if (thinking || speaking || listening) {
            val basePulse = 0.14f + 0.12f * sin(thinkPhase * 2f * Math.PI).toFloat()
            val extraScale = when {
                listening -> 0.14f + voiceLevel * 0.28f
                speaking -> 0.18f + 0.12f * sin(wavePhase * Math.PI).toFloat()
                else -> 0.10f
            }
            drawCircle(
                color = ringBlue.copy(alpha = basePulse),
                radius = r * (1.22f + extraScale),
                center = Offset(cx, cy),
            )
            if (speaking || listening) {
                drawCircle(
                    color = ringBlue.copy(alpha = basePulse * 0.55f),
                    radius = r * (1.40f + extraScale * 1.2f),
                    center = Offset(cx, cy),
                    style = Stroke(width = (r * 0.07f).coerceAtLeast(1.4f)),
                )
            }
        }

        // 2. Основная сфера-голова бота (контрастная к фону темы)
        drawCircle(
            color = ink,
            radius = r,
            center = Offset(cx, cy),
        )

        // 3. Фирменное диагональное СИНЕЕ орбитальное кольцо (постоянно в движении)
        val idleTilt = sin(idlePhase * 2f * Math.PI).toFloat() * 7f
        val ringAngle = when {
            thinking -> -32f + thinkPhase * 360f
            speaking -> -32f + wavePhase * 14f
            writing -> -28f + wavePhase * 9f
            else -> -32f + idleTilt
        }
        rotate(degrees = ringAngle, pivot = Offset(cx, cy)) {
            drawArc(
                color = ringBlue,
                startAngle = -155f,
                sweepAngle = 205f,
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
                        color = ringBlue,
                        radius = r * 0.13f,
                        center = Offset(handPivot.x + r * 0.36f, handPivot.y - r * 0.52f),
                    )
                }
            }

            thinking -> {
                // Задумчивый взгляд — плавно переводит глаза без стилуса
                val lookX = sin(thinkPhase * 2f * Math.PI).toFloat() * r * 0.12f
                val lookY = -r * 0.04f
                for (sign in listOf(-1f, 1f)) {
                    val ex = cx + sign * eyeOffX + lookX
                    drawRoundRect(
                        color = paper,
                        topLeft = Offset(ex - eyeW * 0.5f, eyeY + lookY - eyeH * 0.4f),
                        size = Size(eyeW, eyeH * 0.8f),
                        cornerRadius = CornerRadius(eyeW * 0.5f, eyeW * 0.5f),
                    )
                }
            }

            speaking -> {
                // Живые глаза при разговоре (мягко пульсируют по высоте)
                val h = eyeH * (0.85f + 0.22f * ((wavePhase + 1f) * 0.5f))
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

            else -> {
                // Постоянно живой покой: капсульные глаза, лёгкий взгляд и естественное моргание
                val isBlinking = blinkPhase > 0.94f
                val h = if (isBlinking) eyeH * 0.16f else eyeH
                val gazeX = sin(idlePhase * 2f * Math.PI).toFloat() * r * 0.04f
                for (sign in listOf(-1f, 1f)) {
                    val ex = cx + sign * eyeOffX + gazeX
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

/** Круглый бейдж с живым маскотом для карточек главного экрана. */
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
