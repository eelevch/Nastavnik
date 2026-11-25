package app.nastavnik.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val MascotRingBlueLight = Color(0xFF2563EB)
private val MascotRingBlueDark = Color(0xFF3B82F6)
private val MascotSparkBlue = Color(0xFF93C5FD)

/**
 * Процедурный векторный маскот-бот в стиле Grok (120 FPS, всегда с фирменным синим кольцом):
 * - Непрерывная физика на базе кадровых наносекунд и пружинное смешивание всех состояний без резких скачков.
 * - Постоянно живой: невесомое парение с инерцией кольца и лёгкой гироскопической прецессией,
 *   живой саккадный взгляд с 3D-перспективой глаз и плавным двойным/одинарным морганием.
 * - Пока человек пишет ([writing]): улыбка и плавно выезжающая машущая рука с живым кистевым движением.
 * - Пока наставник думает ([thinking]): бесшовная 360° комета с затухающим хвостом и искрой на орбите.
 * - В голосовом режиме ([speaking] / [listening]): расходящиеся звуковые волны и пульсация взгляда.
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

    // Непрерывный таймер без обнуления циклов (120 FPS без аллокаций).
    var timeSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> timeSec = (now - start) / 1_000_000_000f }
        }
    }

    // Плавные пружинные переходы между всеми состояниями (никаких резких переключений за 1 кадр).
    val writingMix by animateFloatAsState(
        targetValue = if (writing) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 360f),
        label = "writingMix",
    )
    val thinkingMix by animateFloatAsState(
        targetValue = if (thinking) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 260f),
        label = "thinkingMix",
    )
    val speakingMix by animateFloatAsState(
        targetValue = if (speaking) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 320f),
        label = "speakingMix",
    )
    val listeningMix by animateFloatAsState(
        targetValue = if (listening) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 340f),
        label = "listeningMix",
    )
    val smoothVoice by animateFloatAsState(
        targetValue = voiceLevel.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
        label = "smoothVoice",
    )

    Canvas(modifier = modifier.size(sizeDp)) {
        val minDim = size.minDimension
        val hoverSin = sin(timeSec * 2.3f)
        val hoverCos = cos(timeSec * 2.3f)
        val writeBounce = sin(timeSec * 9.5f) * minDim * 0.014f * writingMix
        val floatY = hoverSin * minDim * 0.032f + writeBounce

        val cx = size.width * 0.5f
        val cy = size.height * 0.52f + floatY
        val r = minDim * 0.35f

        // Инерционное отставание орбитального кольца для ощущения невесомости.
        val ringLagY = (sin(timeSec * 2.3f - 0.48f) - hoverSin) * minDim * 0.012f
        val ringCy = cy + ringLagY

        // Живое дыхание наклона и глубины эллипса орбиты.
        val idleTilt = sin(timeSec * 1.9f) * 5.2f
        val writeTilt = sin(timeSec * 9.5f) * 6.5f
        val speakTilt = sin(timeSec * 8.2f) * 7.5f
        val thinkTilt = sin(timeSec * 5.2f) * 4.5f
        val ringAngle = -32f +
            idleTilt * (1f - writingMix) * (1f - thinkingMix) * (1f - speakingMix) +
            writeTilt * writingMix +
            thinkTilt * thinkingMix +
            speakTilt * speakingMix

        val ringRx = r * 1.32f
        val ringRy = r * (0.44f + 0.028f * sin(timeSec * 1.9f + 0.8f) + 0.025f * thinkingMix)
        val ringTopLeft = Offset(cx - ringRx, ringCy - ringRy)
        val ringSize = Size(ringRx * 2f, ringRy * 2f)
        val ringStroke = Stroke(width = (r * 0.16f).coerceAtLeast(1.8f), cap = StrokeCap.Round)

        val headDeg = ((timeSec / 1.15f) * 360f) % 360f
        val activeAura = maxOf(thinkingMix, speakingMix, listeningMix)

        // 1. Внешнее синее свечение и расходящиеся звуковые волны.
        if (activeAura > 0.01f) {
            val corePulse = (0.13f + 0.10f * sin(timeSec * 5.4f)) * activeAura
            val voiceSpread = listeningMix * (0.14f + smoothVoice * 0.28f) +
                speakingMix * (0.16f + 0.10f * sin(timeSec * 9.0f)) +
                thinkingMix * 0.10f
            drawCircle(
                color = ringBlue.copy(alpha = corePulse),
                radius = r * (1.20f + voiceSpread),
                center = Offset(cx, cy),
            )
            val voicePresence = maxOf(speakingMix, listeningMix)
            if (voicePresence > 0.01f) {
                for (waveIdx in 0..1) {
                    val p = ((timeSec * 0.85f) + waveIdx * 0.5f) % 1f
                    val waveRadius = r * (1.08f + p * (0.56f + smoothVoice * 0.26f))
                    val waveAlpha = (1f - p) * (1f - p) * 0.52f * voicePresence
                    if (waveAlpha > 0.01f) {
                        drawCircle(
                            color = ringBlue.copy(alpha = waveAlpha),
                            radius = waveRadius,
                            center = Offset(cx, cy),
                            style = Stroke(width = (r * (0.085f - p * 0.045f)).coerceAtLeast(1.1f)),
                        )
                    }
                }
            }
        }

        // 2. Задняя дуга орбиты + задняя часть хвоста кометы (без резких стыков на 180°/360°).
        rotate(degrees = ringAngle, pivot = Offset(cx, ringCy)) {
            drawArc(
                color = ringBlue.copy(alpha = 0.45f - 0.10f * thinkingMix),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = ringStroke,
            )
            if (thinkingMix > 0.01f) {
                drawOrbitCometHalf(
                    isFront = false,
                    headDeg = headDeg,
                    tailLen = 105f,
                    ringTopLeft = ringTopLeft,
                    ringSize = ringSize,
                    baseWidth = (r * 0.16f).coerceAtLeast(1.8f),
                    color = ringBlue,
                    alphaScale = thinkingMix,
                )
            }
        }

        // 3. Основная сфера-голова с мягким squash & stretch при парении и внимании.
        val stretchY = 1f + hoverCos * 0.016f + 0.025f * listeningMix
        val stretchX = 1f - hoverCos * 0.014f + 0.025f * listeningMix

        scale(scaleX = stretchX, scaleY = stretchY, pivot = Offset(cx, cy)) {
            drawCircle(
                color = ink,
                radius = r,
                center = Offset(cx, cy),
            )

            // 4. Органические глаза: саккадный взгляд, 3D-перспектива сферы, плавное моргание и морфинг в ^ ^.
            val eyeY = cy - r * 0.06f
            val eyeOffX = r * 0.30f
            val eyeW = r * 0.22f
            val eyeH = r * 0.34f

            // Саккадный живой взгляд в покое (~7.5 с цикл с плавными фиксациями).
            val saccadeX = saccadeValue(timeSec) * r * 0.085f
            val saccadeY = saccadeYValue(timeSec) * r * 0.032f

            // Взгляд в размышлении — следит за орбитальной кометой.
            val headRad = Math.toRadians(headDeg.toDouble())
            val thinkLookX = cos(headRad).toFloat() * r * 0.14f
            val thinkLookY = sin(headRad).toFloat() * r * 0.045f - r * 0.03f

            val idleWeight = (1f - thinkingMix) * (1f - writingMix) * (1f - speakingMix * 0.6f)
            val lookX = saccadeX * idleWeight + thinkLookX * thinkingMix
            val lookY = saccadeY * idleWeight + thinkLookY * thinkingMix
            val normLookX = (lookX / (r * 0.14f)).coerceIn(-1f, 1f)

            // Плавное одинарное и периодическое двойное моргание.
            val blinkOpen = blinkOpenFactor(timeSec)
            val speakPulse = 0.85f + 0.24f * ((sin(timeSec * 14f) + 1f) * 0.5f)
            val listenWiden = 1f + 0.12f * listeningMix + 0.14f * smoothVoice * listeningMix
            val thinkFocus = 1f - 0.16f * thinkingMix

            val baseEyeH = eyeH *
                (blinkOpen * (1f - speakingMix) + speakPulse * speakingMix) *
                listenWiden *
                thinkFocus

            // Капсульные глаза плавно растворяются при появлении весёлой улыбки ^ ^.
            val capsuleAlpha = (1f - writingMix * 1.15f).coerceIn(0f, 1f)
            if (capsuleAlpha > 0.01f) {
                for (sign in listOf(-1f, 1f)) {
                    // Лёгкое сужение дальнего от центра глаза при повороте взгляда — ощущение объёмной сферы.
                    val perspectiveScale = 1f - sign * normLookX * 0.10f
                    val w = eyeW * perspectiveScale
                    val h = baseEyeH.coerceAtLeast(eyeH * 0.14f)
                    val ex = cx + sign * eyeOffX + lookX
                    val ey = eyeY + lookY
                    drawRoundRect(
                        color = paper.copy(alpha = capsuleAlpha),
                        topLeft = Offset(ex - w * 0.5f, ey - h * 0.5f),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(w * 0.5f, w * 0.5f),
                    )
                }
            }

            // Радостные дуги ^ ^ и плавно выезжающая машущая рука при письме.
            if (writingMix > 0.01f) {
                val stroke = Stroke(width = (r * 0.14f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
                val arcScale = 0.72f + 0.28f * writingMix
                for (sign in listOf(-1f, 1f)) {
                    val ex = cx + sign * eyeOffX
                    val arcW = eyeW * 1.3f * arcScale
                    val arcH = eyeH * 0.9f * arcScale
                    drawArc(
                        color = paper.copy(alpha = writingMix),
                        startAngle = 195f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(ex - arcW * 0.5f, eyeY - eyeH * 0.35f + (1f - writingMix) * r * 0.08f),
                        size = Size(arcW, arcH),
                        style = stroke,
                    )
                }
                val handPivot = Offset(cx + r * (0.72f + 0.20f * writingMix), cy + r * 0.15f)
                val waveAngle = 18f + sin(timeSec * 10.5f) * 24f + sin(timeSec * 21f) * 4f
                val armLenX = r * 0.36f * writingMix
                val armLenY = r * 0.52f * writingMix
                rotate(degrees = waveAngle, pivot = handPivot) {
                    drawLine(
                        color = ink.copy(alpha = writingMix),
                        start = handPivot,
                        end = Offset(handPivot.x + armLenX, handPivot.y - armLenY),
                        strokeWidth = (r * 0.24f * writingMix).coerceAtLeast(1.6f),
                        cap = StrokeCap.Round,
                    )
                    drawCircle(
                        color = ringBlue.copy(alpha = writingMix),
                        radius = r * 0.13f * writingMix,
                        center = Offset(handPivot.x + armLenX, handPivot.y - armLenY),
                    )
                }
            }
        }

        // 5. Передняя дуга синего орбитального кольца + плавная комета и искра.
        rotate(degrees = ringAngle, pivot = Offset(cx, ringCy)) {
            drawArc(
                color = ringBlue.copy(alpha = 1f - 0.55f * thinkingMix),
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = ringStroke,
            )
            if (thinkingMix > 0.01f) {
                drawOrbitCometHalf(
                    isFront = true,
                    headDeg = headDeg,
                    tailLen = 105f,
                    ringTopLeft = ringTopLeft,
                    ringSize = ringSize,
                    baseWidth = (r * 0.16f).coerceAtLeast(1.8f),
                    color = ringBlue,
                    alphaScale = thinkingMix,
                )
                if (headDeg in 0f..180f) {
                    val rad = Math.toRadians(headDeg.toDouble())
                    val horizonFade = sin(rad).toFloat().coerceIn(0f, 1f)
                    val sparkAlpha = (horizonFade * 1.45f).coerceIn(0f, 1f) * thinkingMix
                    val dotX = cx + ringRx * cos(rad).toFloat()
                    val dotY = ringCy + ringRy * sin(rad).toFloat()
                    drawCircle(
                        color = MascotSparkBlue.copy(alpha = sparkAlpha * 0.42f),
                        radius = (r * 0.26f).coerceAtLeast(2.8f),
                        center = Offset(dotX, dotY),
                    )
                    drawCircle(
                        color = MascotSparkBlue.copy(alpha = sparkAlpha),
                        radius = (r * 0.16f).coerceAtLeast(2.0f),
                        center = Offset(dotX, dotY),
                    )
                }
            }
        }
    }
}

/**
 * Отрисовка затухающего хвоста кометы (12 сегментов) без рывков при переходе через 0° и 180°.
 */
private fun DrawScope.drawOrbitCometHalf(
    isFront: Boolean,
    headDeg: Float,
    tailLen: Float,
    ringTopLeft: Offset,
    ringSize: Size,
    baseWidth: Float,
    color: Color,
    alphaScale: Float,
) {
    val segments = 12
    val step = tailLen / segments
    val minHalf = if (isFront) 0f else 180f
    val maxHalf = if (isFront) 180f else 360f

    for (i in 0 until segments) {
        val t = (i + 0.5f) / segments // 0 = хвост, 1 = голова
        val rawStart = headDeg - tailLen + i * step
        val rawEnd = rawStart + step + 0.6f
        val normStart = ((rawStart % 360f) + 360f) % 360f
        val normEnd = normStart + (rawEnd - rawStart)

        // Разбиваем сегмент, если он пересекает 360° -> 0°.
        if (normEnd > 360f) {
            val parts = listOf(normStart to 360f, 0f to (normEnd - 360f))
            for ((p0, p1) in parts) {
                val s = maxOf(minHalf, p0)
                val e = minOf(maxHalf, p1)
                if (e > s) {
                    drawArc(
                        color = color.copy(alpha = (0.08f + 0.92f * t * t) * alphaScale),
                        startAngle = s,
                        sweepAngle = e - s,
                        useCenter = false,
                        topLeft = ringTopLeft,
                        size = ringSize,
                        style = Stroke(width = baseWidth * (0.65f + 0.72f * t), cap = StrokeCap.Round),
                    )
                }
            }
        } else {
            val s = maxOf(minHalf, normStart)
            val e = minOf(maxHalf, normEnd)
            if (e > s) {
                drawArc(
                    color = color.copy(alpha = (0.08f + 0.92f * t * t) * alphaScale),
                    startAngle = s,
                    sweepAngle = e - s,
                    useCenter = false,
                    topLeft = ringTopLeft,
                    size = ringSize,
                    style = Stroke(width = baseWidth * (0.65f + 0.72f * t), cap = StrokeCap.Round),
                )
            }
        }
    }
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Саккадный горизонтальный взгляд с естественными паузами. */
private fun saccadeValue(timeSec: Float): Float {
    val cycle = 7.6f
    val p = (timeSec % cycle) / cycle
    return when {
        p < 0.25f -> 0f
        p < 0.34f -> smoothstep(0.25f, 0.34f, p) * 0.75f
        p < 0.48f -> 0.75f
        p < 0.57f -> 0.75f - smoothstep(0.48f, 0.57f, p) * 1.45f
        p < 0.70f -> -0.70f
        p < 0.80f -> -0.70f + smoothstep(0.70f, 0.80f, p) * 0.70f
        else -> 0f
    }
}

/** Лёгкий вертикальный сдвиг при любопытном взгляде. */
private fun saccadeYValue(timeSec: Float): Float {
    val cycle = 7.6f
    val p = (timeSec % cycle) / cycle
    return when {
        p < 0.48f -> 0f
        p < 0.57f -> -smoothstep(0.48f, 0.57f, p) * 0.5f
        p < 0.70f -> -0.5f
        p < 0.80f -> -0.5f + smoothstep(0.70f, 0.80f, p) * 0.5f
        else -> 0f
    }
}

/** Плавное естественное моргание (1 = глаза открыты, 0.14 = сомкнуты) с двойным морганием раз в 3 цикла. */
private fun blinkOpenFactor(timeSec: Float): Float {
    val cycleLen = 3.8f
    val cycleIndex = (timeSec / cycleLen).toInt()
    val local = timeSec - cycleIndex * cycleLen
    val isDouble = (cycleIndex % 3) == 2

    fun singleBlink(t: Float, center: Float): Float {
        val dt = abs(t - center)
        if (dt >= 0.095f) return 1f
        val k = smoothstep(0f, 0.095f, dt)
        return 0.14f + 0.86f * k
    }

    var open = singleBlink(local, 3.40f)
    if (isDouble) {
        open = minOf(open, singleBlink(local, 3.63f))
    }
    return open
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
