package app.nastavnik.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nastavnik.theme.MascotColor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Живой сферический маскот-наставник (120 FPS):
 * - Объёмная 3D-сфера с динамическим смещением ядра радиального градиента и внутренними биолюминесцентными бликами.
 * - Саккадный взгляд с 3D-перспективой глаз, плавным одинарным и двойным морганием и микро-наклонами головы.
 * - В режиме [writing]: плавный морфинг глаз в радостные дуги `^ ^`, мягкое оживлённое подпрыгивание и тёплое сияние.
 * - В режиме [thinking]: вихрь светящихся внутренних потоков внутри сферы и встречные орбитальные дуги по краю.
 * - В голосовом режиме ([speaking] / [listening]): концентрические звуковые волны и отзывчивое расширение сферы.
 */
@Composable
fun MascotIcon(
    writing: Boolean = false,
    thinking: Boolean = false,
    speaking: Boolean = false,
    listening: Boolean = false,
    voiceLevel: Float = 0f,
    sizeDp: Dp = 26.dp,
    colorOverride: MascotColor? = null,
    gazeTarget: Offset? = null,
    modifier: Modifier = Modifier,
) {
    val activePreset = colorOverride ?: Brand.mascotColor
    val stops = activePreset.resolveStops(Brand.Accent.toArgb())

    val colorSpec = tween<Color>(380)
    val coreColor by animateColorAsState(Color(stops.coreArgb), colorSpec, label = "mascotCore")
    val midColor by animateColorAsState(Color(stops.midArgb), colorSpec, label = "mascotMid")
    val rimColor by animateColorAsState(Color(stops.rimArgb), colorSpec, label = "mascotRim")
    val haloColor by animateColorAsState(Color(stops.haloArgb), colorSpec, label = "mascotHalo")
    val sparkColor by animateColorAsState(Color(stops.sparkArgb), colorSpec, label = "mascotSpark")

    // Непрерывный таймер (120 FPS) без сброса фазы.
    var timeSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> timeSec = (now - start) / 1_000_000_000f }
        }
    }

    // Плавные пружинные переходы между всеми состояниями.
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
    val hasGazeMix by animateFloatAsState(
        targetValue = if (gazeTarget != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 360f),
        label = "hasGazeMix",
    )
    val gazeX by animateFloatAsState(
        targetValue = (gazeTarget?.x ?: 0f).coerceIn(-1f, 1f),
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 360f),
        label = "gazeX",
    )
    val gazeY by animateFloatAsState(
        targetValue = (gazeTarget?.y ?: 0f).coerceIn(-1f, 1f),
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 360f),
        label = "gazeY",
    )

    val sphereClip = remember { Path() }

    Canvas(modifier = modifier.size(sizeDp)) {
        val minDim = size.minDimension
        val r = minDim * 0.41f
        val hoverSin = sin(timeSec * 2.2f)
        val hoverCos = cos(timeSec * 2.2f)
        val writeBounce = sin(timeSec * 9.2f) * minDim * 0.015f * writingMix
        val floatY = hoverSin * minDim * 0.028f + writeBounce

        val cx = size.width * 0.5f
        val cy = size.height * 0.51f + floatY
        val center = Offset(cx, cy)

        // Вектор взгляда: саккады в покое, круговое слежение за светом в размышлении, внимательная фиксация при голосе.
        val headRad = (timeSec * (2f * PI.toFloat() / 1.45f))
        val saccadeX = saccadeValue(timeSec) * r * 0.11f
        val saccadeY = saccadeYValue(timeSec) * r * 0.045f
        val thinkLookX = cos(headRad) * r * 0.14f
        val thinkLookY = sin(headRad * 2f) * r * 0.045f - r * 0.02f

        val idleWeight = (1f - thinkingMix) * (1f - writingMix) * (1f - maxOf(speakingMix, listeningMix) * 0.75f)
        val autonomousX = saccadeX * idleWeight + thinkLookX * thinkingMix * (1f - writingMix * 0.5f)
        val autonomousY = saccadeY * idleWeight + thinkLookY * thinkingMix * (1f - writingMix * 0.5f)
        val spatialX = gazeX * r * 0.20f
        val spatialY = gazeY * r * 0.14f
        val lookX = autonomousX * (1f - hasGazeMix) + spatialX * hasGazeMix
        val lookY = autonomousY * (1f - hasGazeMix) + spatialY * hasGazeMix
        val normLookX = (lookX / (r * 0.18f)).coerceIn(-1f, 1f)

        val voicePresence = maxOf(speakingMix, listeningMix)
        val activeAura = maxOf(thinkingMix, speakingMix, listeningMix, writingMix * 0.65f)

        // 1. Внешнее свечение и концентрические звуковые волны.
        val haloAlpha = (0.16f + 0.20f * activeAura + 0.04f * hoverSin + 0.08f * smoothVoice * voicePresence)
            .coerceIn(0.05f, 0.48f)
        val haloRadius = r * (1.28f + 0.16f * activeAura + 0.16f * smoothVoice * voicePresence)
        drawCircle(
            brush = Brush.radialGradient(
                0.0f to haloColor.copy(alpha = haloAlpha),
                0.6f to haloColor.copy(alpha = haloAlpha * 0.38f),
                1.0f to Color.Transparent,
                center = center,
                radius = haloRadius,
            ),
            radius = haloRadius,
            center = center,
        )

        if (voicePresence > 0.01f) {
            for (waveIdx in 0..2) {
                val p = ((timeSec * 0.90f) + waveIdx / 3f) % 1f
                val waveRadius = r * (1.03f + p * (0.48f + smoothVoice * 0.30f))
                val fade = (1f - p)
                val waveAlpha = fade * fade * 0.55f * voicePresence
                if (waveAlpha > 0.01f) {
                    drawCircle(
                        color = haloColor.copy(alpha = waveAlpha),
                        radius = waveRadius,
                        center = center,
                        style = Stroke(width = (r * (0.075f - p * 0.042f)).coerceAtLeast(1.1f)),
                    )
                }
            }
        }

        // 2. Встречные светящиеся орбитальные дуги в режиме размышления.
        if (thinkingMix > 0.01f) {
            val arcR = r * 1.12f
            val arcTopLeft = Offset(cx - arcR, cy - arcR)
            val arcSize = Size(arcR * 2f, arcR * 2f)
            val arcStroke = Stroke(width = (r * 0.085f).coerceAtLeast(1.4f), cap = StrokeCap.Round)
            val spinA = (timeSec * 220f) % 360f
            val spinB = (-timeSec * 155f + 135f) % 360f
            drawArc(
                color = sparkColor.copy(alpha = 0.85f * thinkingMix),
                startAngle = spinA,
                sweepAngle = 72f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = arcStroke,
            )
            drawArc(
                color = midColor.copy(alpha = 0.65f * thinkingMix),
                startAngle = spinB,
                sweepAngle = 54f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = arcStroke,
            )
        }

        // 3. Сфера-наставник с 3D squash & stretch, микро-наклоном и внутренним плазменным градиентом.
        val voicePulse = 1f +
            0.045f * listeningMix +
            0.055f * smoothVoice * voicePresence +
            0.022f * sin(timeSec * 12.5f) * speakingMix
        val stretchY = (1f + hoverCos * 0.018f + 0.020f * abs(sin(timeSec * 9.2f)) * writingMix) * voicePulse
        val stretchX = (1f - hoverCos * 0.014f + 0.020f * sin(timeSec * 4.6f) * writingMix) * voicePulse
        val tiltDeg = normLookX * 3.2f * idleWeight +
            sin(timeSec * 8.6f) * 5.2f * writingMix +
            sin(timeSec * 2.4f) * 3.0f * thinkingMix

        rotate(degrees = tiltDeg, pivot = center) {
            scale(scaleX = stretchX, scaleY = stretchY, pivot = center) {
                sphereClip.reset()
                sphereClip.addOval(Rect(cx - r, cy - r, cx + r, cy + r))

                clipPath(sphereClip) {
                    // Базовый обод сферы.
                    drawCircle(color = rimColor, radius = r, center = center)

                    // Динамическое смещение ядра градиента (3D параллакс за взглядом + плавный органический дрейф).
                    val driftX = sin(timeSec * 1.35f) * r * 0.08f
                    val driftY = cos(timeSec * 1.05f) * r * 0.08f
                    val coreCenter = Offset(cx + lookX * 0.78f + driftX, cy + lookY * 0.78f + driftY)
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to coreColor,
                            0.50f to midColor,
                            0.86f to rimColor,
                            1.0f to rimColor,
                            center = coreCenter,
                            radius = r * 1.24f,
                        ),
                        radius = r,
                        center = center,
                    )

                    // Внутренний биолюминесцентный вихрь (в покое — лёгкое дыхание, в размышлении — яркий поток).
                    val swirlSpeed = 0.9f + thinkingMix * 2.1f + writingMix * 1.1f
                    val blobAlphaA = (0.14f + thinkingMix * 0.28f + writingMix * 0.16f + smoothVoice * voicePresence * 0.14f)
                        .coerceIn(0f, 0.55f)
                    val blobACenter = Offset(
                        cx + cos(timeSec * swirlSpeed) * r * (0.24f + 0.16f * thinkingMix),
                        cy + sin(timeSec * swirlSpeed * 1.18f) * r * (0.24f + 0.16f * thinkingMix),
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to sparkColor.copy(alpha = blobAlphaA),
                            1.0f to Color.Transparent,
                            center = blobACenter,
                            radius = r * 0.78f,
                        ),
                        radius = r * 0.78f,
                        center = blobACenter,
                    )

                    val blobAlphaB = (0.10f + thinkingMix * 0.26f + speakingMix * 0.16f).coerceIn(0f, 0.45f)
                    val blobBCenter = Offset(
                        cx - cos(timeSec * swirlSpeed * 0.85f + 1.2f) * r * 0.30f,
                        cy + sin(timeSec * swirlSpeed * 0.95f + 0.5f) * r * 0.28f,
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to coreColor.copy(alpha = blobAlphaB),
                            1.0f to Color.Transparent,
                            center = blobBCenter,
                            radius = r * 0.70f,
                        ),
                        radius = r * 0.70f,
                        center = blobBCenter,
                    )

                    // Верхний мягкий внутренний рефлекс света на поверхности сферы.
                    val sheenCenter = Offset(cx - r * 0.12f, cy - r * 0.58f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to Color.White.copy(alpha = 0.20f + 0.08f * writingMix),
                            1.0f to Color.Transparent,
                            center = sheenCenter,
                            radius = r * 0.72f,
                        ),
                        radius = r * 0.72f,
                        center = sheenCenter,
                    )
                }

                // Тонкая световая кромка для чёткого контура на светлом и тёмном фоне.
                val rimStrokeWidth = (r * 0.042f).coerceIn(0.8f, 1.5f.dp.toPx())
                drawCircle(
                    brush = Brush.verticalGradient(
                        0.0f to Color.White.copy(alpha = 0.30f),
                        0.5f to Color.White.copy(alpha = 0.06f),
                        1.0f to Color.White.copy(alpha = 0.14f),
                        startY = cy - r,
                        endY = cy + r,
                    ),
                    radius = r - rimStrokeWidth * 0.5f,
                    center = center,
                    style = Stroke(width = rimStrokeWidth),
                )

                // 4. Фирменные белые вертикальные глаза-капсулы в стиле Bencho.
                val eyeY = cy - r * 0.02f
                val eyeOffX = r * 0.26f
                val eyeW = r * 0.285f
                val eyeH = r * 0.45f

                val blinkOpen = blinkOpenFactor(timeSec)
                val speakPulse = 0.82f + (0.20f + 0.16f * smoothVoice) * ((sin(timeSec * 14f) * 0.65f + cos(timeSec * 23f) * 0.35f + 1f) * 0.5f)
                val listenWiden = 1f + 0.10f * listeningMix + 0.14f * smoothVoice * listeningMix
                val thinkFocus = 1f - 0.16f * thinkingMix

                val baseEyeH = eyeH *
                    (blinkOpen * (1f - speakingMix) + speakPulse * speakingMix) *
                    listenWiden *
                    thinkFocus *
                    (1f - 0.14f * writingMix * hasGazeMix)

                // Если передана пространственная цель взгляда (перо или читаемая строка на листе),
                // сфера продолжает следить открытыми глазами вместо дуг ^ ^.
                val effectiveWritingArcs = (writingMix * (1f - hasGazeMix)).coerceIn(0f, 1f)
                val capsuleAlpha = (1f - effectiveWritingArcs * 1.18f).coerceIn(0f, 1f)
                if (capsuleAlpha > 0.01f) {
                    val eyeShadowOffset = (r * 0.03f).coerceAtLeast(0.6f)
                    for (sign in listOf(-1f, 1f)) {
                        val perspectiveScale = 1f - sign * normLookX * 0.12f
                        val w = eyeW * perspectiveScale
                        val h = baseEyeH.coerceAtLeast(eyeH * 0.15f)
                        val ex = cx + sign * eyeOffX * (1f - abs(normLookX) * 0.04f) + lookX
                        val ey = eyeY + lookY
                        val topLeft = Offset(ex - w * 0.5f, ey - h * 0.5f)
                        val eyeSize = Size(w, h)
                        val cr = CornerRadius(w * 0.5f, w * 0.5f)

                        drawRoundRect(
                            color = Color(0x26000000).copy(alpha = 0.15f * capsuleAlpha),
                            topLeft = Offset(topLeft.x, topLeft.y + eyeShadowOffset),
                            size = eyeSize,
                            cornerRadius = cr,
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = capsuleAlpha),
                            topLeft = topLeft,
                            size = eyeSize,
                            cornerRadius = cr,
                        )
                    }
                }

                // Радостные белые дуги `^ ^` при письме (когда нет пространственного трекинга пера).
                if (effectiveWritingArcs > 0.01f) {
                    val stroke = Stroke(width = (r * 0.145f).coerceAtLeast(1.4f), cap = StrokeCap.Round)
                    val arcScale = 0.74f + 0.26f * effectiveWritingArcs
                    for (sign in listOf(-1f, 1f)) {
                        val ex = cx + sign * eyeOffX
                        val arcW = eyeW * 1.24f * arcScale
                        val arcH = eyeH * 0.76f * arcScale
                        drawArc(
                            color = Color.White.copy(alpha = effectiveWritingArcs),
                            startAngle = 195f,
                            sweepAngle = 150f,
                            useCenter = false,
                            topLeft = Offset(ex - arcW * 0.5f, eyeY - eyeH * 0.26f + (1f - effectiveWritingArcs) * r * 0.07f),
                            size = Size(arcW, arcH),
                            style = stroke,
                        )
                    }
                }
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

/** Компактный живой бейдж с маскотом-сферой для карточек главного экрана и шапок. */
@Composable
fun MascotBadge(
    writing: Boolean = false,
    thinking: Boolean = false,
    size: Dp = 32.dp,
    colorOverride: MascotColor? = null,
    gazeTarget: Offset? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        MascotIcon(
            writing = writing,
            thinking = thinking,
            sizeDp = (size.value * 0.94f).dp,
            colorOverride = colorOverride,
            gazeTarget = gazeTarget,
        )
    }
}
