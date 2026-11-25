package app.nastavnik.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nastavnik.theme.MascotVariant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val MascotRingBlueLight = Color(0xFF2563EB)
private val MascotRingBlueDark = Color(0xFF3B82F6)
private val MascotRingDeep = Color(0xFF1D4ED8)
private val MascotRingIndigo = Color(0xFF4F46E5)
private val MascotRingCyan = Color(0xFF38BDF8)
private val MascotRingIce = Color(0xFFBAE6FD)
private val MascotSparkBlue = Color(0xFF93C5FD)

/**
 * Процедурный векторный маскот-бот в стиле Grok (120 FPS, каллиграфические 3D-орбиты без аллокаций в кадре):
 * - Все кисти и геометрия кэшируются через [drawWithCache], в кадре выполняется только лёгкая перерисовка.
 * - Непрерывная физика на базе кадровых наносекунд и пружинное смешивание всех состояний.
 * - Скульптурная сфера с мягким объёмом и отсветом от орбиты, чистая посадка орбит ниже уровня глаз.
 * - 4 модернизированных варианта ([MascotVariant]): Базовый Модерн, Двойная гиро-орбита,
 *   Двойной каскад и Затмение · 2 орбиты.
 */
@Composable
fun MascotIcon(
    writing: Boolean = false,
    thinking: Boolean = false,
    speaking: Boolean = false,
    listening: Boolean = false,
    voiceLevel: Float = 0f,
    sizeDp: Dp = 26.dp,
    variantOverride: MascotVariant? = null,
    modifier: Modifier = Modifier,
) {
    // Непрерывный таймер без обнуления циклов (120 FPS без перекомпозиции).
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

    Spacer(
        modifier = modifier
            .size(sizeDp)
            .drawWithCache {
                val variant = variantOverride ?: Brand.mascotVariant
                val dark = Brand.dark
                val ink = Brand.Ink
                val paper = Brand.Paper
                val ringBlue = if (dark) MascotRingBlueDark else MascotRingBlueLight
                val orbitPath = Path()

                val minDim = size.minDimension
                val r = (minDim * 0.345f).coerceAtLeast(1f)
                val cx = size.width * 0.5f
                val cy = size.height * 0.52f
                val baseRingCy = cy + r * 0.06f

                val primaryHalfWidth = (r * 0.095f).coerceAtLeast(1.1f)
                val secHalfWidth = (r * if (variant == MascotVariant.PARALLEL_ORBIT) 0.045f else 0.052f).coerceAtLeast(0.7f)

                val ringRx = when (variant) {
                    MascotVariant.PARALLEL_ORBIT -> r * 1.28f
                    MascotVariant.CLASSIC -> r * 1.36f
                    else -> r * 1.34f
                }
                val secRx = if (variant == MascotVariant.PARALLEL_ORBIT) r * 1.50f else r * 1.44f

                val sphereTopHighlight = if (dark) ink else lerp(ink, Color(0xFF1E293B), 0.22f)
                val sphereBottomShade = if (dark) lerp(ink, Color(0xFFE2E8F0), 0.20f) else ink
                val sphereBrush = Brush.radialGradient(
                    colors = listOf(sphereTopHighlight, sphereBottomShade),
                    center = Offset(cx - r * 0.30f, cy - r * 0.32f),
                    radius = r * 1.42f,
                )

                val eclipseHaloBrush = Brush.radialGradient(
                    colors = listOf(MascotRingCyan.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(cx - r * 0.24f, cy - r * 0.24f),
                    radius = r * 1.52f,
                )
                val eclipseRimBrush = Brush.linearGradient(
                    colors = listOf(MascotRingIce.copy(alpha = 0.92f), MascotRingCyan.copy(alpha = 0.65f), Color.Transparent),
                    start = Offset(cx - r, cy),
                    end = Offset(cx + r * 0.2f, cy - r),
                )

                val primaryFrontBrush = Brush.horizontalGradient(
                    colors = if (variant == MascotVariant.ECLIPSE_DUO) {
                        listOf(MascotRingIndigo, ringBlue, MascotRingCyan, MascotRingIce, ringBlue)
                    } else {
                        listOf(MascotRingDeep, ringBlue, MascotRingCyan, MascotRingIce, ringBlue)
                    },
                    startX = cx - ringRx,
                    endX = cx + ringRx,
                )
                val primaryBackBrush = Brush.horizontalGradient(
                    colors = if (variant == MascotVariant.ECLIPSE_DUO) {
                        listOf(MascotRingIndigo.copy(alpha = 0.42f), MascotRingCyan.copy(alpha = 0.28f), MascotRingIndigo.copy(alpha = 0.42f))
                    } else {
                        listOf(ringBlue.copy(alpha = 0.42f), MascotRingCyan.copy(alpha = 0.30f), ringBlue.copy(alpha = 0.42f))
                    },
                    startX = cx - ringRx,
                    endX = cx + ringRx,
                )
                val secFrontBrush = Brush.horizontalGradient(
                    colors = if (variant == MascotVariant.ECLIPSE_DUO) {
                        listOf(MascotRingCyan, MascotRingIce, MascotSparkBlue, MascotRingIndigo)
                    } else {
                        listOf(MascotSparkBlue, MascotRingIce, MascotRingCyan, ringBlue)
                    },
                    startX = cx - secRx,
                    endX = cx + secRx,
                )
                val secBackBrush = Brush.horizontalGradient(
                    colors = listOf(
                        MascotSparkBlue.copy(alpha = 0.34f),
                        MascotRingIce.copy(alpha = 0.24f),
                        MascotSparkBlue.copy(alpha = 0.34f),
                    ),
                    startX = cx - secRx,
                    endX = cx + secRx,
                )
                val eyeBottomTint = lerp(paper, MascotSparkBlue, if (dark) 0.22f else 0.14f)

                onDrawBehind {
                    val tSec = timeSec
                    val wMix = writingMix
                    val thMix = thinkingMix
                    val spMix = speakingMix
                    val lMix = listeningMix
                    val vLvl = smoothVoice

                    val hoverSin = sin(tSec * 2.3f)
                    val hoverCos = cos(tSec * 2.3f)
                    val writeBounce = sin(tSec * 9.5f) * minDim * 0.014f * wMix
                    val floatY = hoverSin * minDim * 0.032f + writeBounce
                    val ringLagY = (sin(tSec * 2.3f - 0.48f) - hoverSin) * minDim * 0.012f
                    val ringCy = baseRingCy + ringLagY

                    val idleTilt = sin(tSec * 1.9f) * 4.2f
                    val writeTilt = sin(tSec * 9.5f) * 5.8f
                    val speakTilt = sin(tSec * 8.2f) * 6.8f
                    val thinkTilt = sin(tSec * 5.2f) * 4.0f
                    val baseAngle = when (variant) {
                        MascotVariant.CLASSIC -> -30f
                        MascotVariant.DUAL_ORBIT -> -30f
                        MascotVariant.PARALLEL_ORBIT -> -29f
                        MascotVariant.ECLIPSE_DUO -> -31f
                    }
                    val ringAngle = baseAngle +
                        idleTilt * (1f - wMix) * (1f - thMix) * (1f - spMix) +
                        writeTilt * wMix +
                        thinkTilt * thMix +
                        speakTilt * spMix

                    val ringRy = when (variant) {
                        MascotVariant.PARALLEL_ORBIT -> r * (0.38f + 0.022f * sin(tSec * 1.9f + 0.8f) + 0.02f * thMix)
                        MascotVariant.CLASSIC -> r * (0.42f + 0.024f * sin(tSec * 1.9f + 0.8f) + 0.02f * thMix)
                        else -> r * (0.40f + 0.024f * sin(tSec * 1.9f + 0.8f) + 0.02f * thMix)
                    }
                    val ringTopLeft = Offset(cx - ringRx, ringCy - ringRy)
                    val ringSize = Size(ringRx * 2f, ringRy * 2f)

                    val hasSecondary = variant != MascotVariant.CLASSIC
                    val secTilt = sin(tSec * 1.6f + 1.2f) * 3.8f
                    val secAngle = when (variant) {
                        MascotVariant.PARALLEL_ORBIT -> ringAngle
                        MascotVariant.ECLIPSE_DUO -> 21f + secTilt
                        else -> 20f + secTilt
                    }
                    val secRy = if (variant == MascotVariant.PARALLEL_ORBIT) {
                        r * (0.46f + 0.022f * cos(tSec * 1.8f) + 0.02f * thMix)
                    } else {
                        r * (0.34f + 0.020f * cos(tSec * 1.7f) + 0.018f * thMix)
                    }
                    val secTopLeft = Offset(cx - secRx, ringCy - secRy)
                    val secSize = Size(secRx * 2f, secRy * 2f)

                    val headDeg = ((tSec / 1.15f) * 360f) % 360f
                    val headDeg2 = if (variant == MascotVariant.PARALLEL_ORBIT) {
                        ((tSec / 1.15f) * 360f + 165f) % 360f
                    } else {
                        (((-tSec / 1.45f) * 360f + 180f) % 360f + 360f) % 360f
                    }
                    val activeAura = maxOf(thMix, spMix, lMix)

                    translate(top = floatY) {
                        // 1. Внешнее свечение и звуковые волны голоса (плюс лунный ореол у ECLIPSE_DUO).
                        if (variant == MascotVariant.ECLIPSE_DUO) {
                            drawCircle(
                                brush = eclipseHaloBrush,
                                radius = r * 1.52f,
                                center = Offset(cx, cy),
                                alpha = 0.85f + 0.15f * sin(tSec * 2.0f),
                            )
                        }
                        if (activeAura > 0.01f) {
                            val corePulse = (0.13f + 0.10f * sin(tSec * 5.4f)) * activeAura
                            val voiceSpread = lMix * (0.14f + vLvl * 0.28f) +
                                spMix * (0.16f + 0.10f * sin(tSec * 9.0f)) +
                                thMix * 0.10f
                            drawCircle(
                                color = ringBlue.copy(alpha = corePulse),
                                radius = r * (1.20f + voiceSpread),
                                center = Offset(cx, cy),
                            )
                            val voicePresence = maxOf(spMix, lMix)
                            if (voicePresence > 0.01f) {
                                for (waveIdx in 0..1) {
                                    val p = ((tSec * 0.85f) + waveIdx * 0.5f) % 1f
                                    val waveRadius = r * (1.08f + p * (0.56f + vLvl * 0.26f))
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

                        // 2. Задние 3D-дуги орбит + задняя часть хвоста кометы.
                        if (hasSecondary) {
                            rotate(degrees = secAngle, pivot = Offset(cx, ringCy)) {
                                drawTaperedOrbitHalf(
                                    path = orbitPath,
                                    isFront = false,
                                    cx = cx,
                                    cy = ringCy,
                                    rx = secRx,
                                    ry = secRy,
                                    baseHalfWidth = secHalfWidth,
                                    brush = secBackBrush,
                                    alpha = 0.92f - 0.14f * thMix,
                                )
                                if (thMix > 0.01f) {
                                    drawOrbitCometHalf(
                                        isFront = false,
                                        headDeg = headDeg2,
                                        tailLen = 92f,
                                        ringTopLeft = secTopLeft,
                                        ringSize = secSize,
                                        baseWidth = (secHalfWidth * 1.9f).coerceAtLeast(1.2f),
                                        color = MascotRingCyan,
                                        alphaScale = thMix * 0.9f,
                                    )
                                }
                            }
                        }
                        rotate(degrees = ringAngle, pivot = Offset(cx, ringCy)) {
                            drawTaperedOrbitHalf(
                                path = orbitPath,
                                isFront = false,
                                cx = cx,
                                cy = ringCy,
                                rx = ringRx,
                                ry = ringRy,
                                baseHalfWidth = primaryHalfWidth,
                                brush = primaryBackBrush,
                                alpha = 0.92f - 0.14f * thMix,
                            )
                            if (thMix > 0.01f) {
                                drawOrbitCometHalf(
                                    isFront = false,
                                    headDeg = headDeg,
                                    tailLen = 105f,
                                    ringTopLeft = ringTopLeft,
                                    ringSize = ringSize,
                                    baseWidth = (primaryHalfWidth * 1.65f).coerceAtLeast(1.8f),
                                    color = ringBlue,
                                    alphaScale = thMix,
                                )
                            }
                        }

                        // 3. Скульптурная сфера-голова: мягкий объём, нижний рефлекс и серп затмения.
                        val stretchY = 1f + hoverCos * 0.016f + 0.025f * lMix
                        val stretchX = 1f - hoverCos * 0.014f + 0.025f * lMix

                        scale(scaleX = stretchX, scaleY = stretchY, pivot = Offset(cx, cy)) {
                            drawCircle(
                                brush = sphereBrush,
                                radius = r,
                                center = Offset(cx, cy),
                            )

                            drawArc(
                                color = MascotRingCyan.copy(alpha = if (dark) 0.42f else 0.34f),
                                startAngle = 22f,
                                sweepAngle = 110f,
                                useCenter = false,
                                topLeft = Offset(cx - r * 0.92f, cy - r * 0.92f),
                                size = Size(r * 1.84f, r * 1.84f),
                                style = Stroke(width = (r * 0.055f).coerceAtLeast(0.8f), cap = StrokeCap.Round),
                            )

                            if (variant == MascotVariant.ECLIPSE_DUO) {
                                drawArc(
                                    brush = eclipseRimBrush,
                                    startAngle = 195f,
                                    sweepAngle = 95f,
                                    useCenter = false,
                                    topLeft = Offset(cx - r * 0.96f, cy - r * 0.96f),
                                    size = Size(r * 1.92f, r * 1.92f),
                                    style = Stroke(width = (r * 0.095f).coerceAtLeast(1.3f), cap = StrokeCap.Round),
                                )
                            }

                            // 4. Органические глаза выше фронтальной орбиты с 3D-перспективой и мягким морганием.
                            val eyeY = cy - r * 0.10f
                            val eyeOffX = r * 0.30f
                            val eyeW = r * 0.22f
                            val eyeH = r * 0.34f

                            val saccadeX = saccadeValue(tSec) * r * 0.085f
                            val saccadeY = saccadeYValue(tSec) * r * 0.032f

                            val headRad = Math.toRadians(headDeg.toDouble())
                            val thinkLookX = cos(headRad).toFloat() * r * 0.14f
                            val thinkLookY = sin(headRad).toFloat() * r * 0.045f - r * 0.03f

                            val idleWeight = (1f - thMix) * (1f - wMix) * (1f - spMix * 0.6f)
                            val lookX = saccadeX * idleWeight + thinkLookX * thMix
                            val lookY = saccadeY * idleWeight + thinkLookY * thMix
                            val normLookX = (lookX / (r * 0.14f)).coerceIn(-1f, 1f)

                            val blinkOpen = blinkOpenFactor(tSec)
                            val speakPulse = 0.85f + 0.24f * ((sin(tSec * 14f) + 1f) * 0.5f)
                            val listenWiden = 1f + 0.12f * lMix + 0.14f * vLvl * lMix
                            val thinkFocus = 1f - 0.16f * thMix

                            val baseEyeH = eyeH *
                                (blinkOpen * (1f - spMix) + speakPulse * spMix) *
                                listenWiden *
                                thinkFocus

                            val capsuleAlpha = (1f - wMix * 1.15f).coerceIn(0f, 1f)
                            if (capsuleAlpha > 0.01f) {
                                val topEyeColor = paper.copy(alpha = capsuleAlpha)
                                val bottomEyeColor = eyeBottomTint.copy(alpha = capsuleAlpha)
                                for (sign in listOf(-1f, 1f)) {
                                    val perspectiveScale = 1f - sign * normLookX * 0.10f
                                    val w = eyeW * perspectiveScale
                                    val h = baseEyeH.coerceAtLeast(eyeH * 0.14f)
                                    val ex = cx + sign * eyeOffX + lookX
                                    val ey = eyeY + lookY
                                    val eyeTop = ey - h * 0.5f
                                    // Деликатный отсвет орбиты по нижней трети глаза без аллокации нового Brush.
                                    drawRoundRect(
                                        color = topEyeColor,
                                        topLeft = Offset(ex - w * 0.5f, eyeTop),
                                        size = Size(w, h),
                                        cornerRadius = CornerRadius(w * 0.5f, w * 0.5f),
                                    )
                                    drawRoundRect(
                                        color = bottomEyeColor.copy(alpha = capsuleAlpha * 0.35f),
                                        topLeft = Offset(ex - w * 0.45f, ey + h * 0.05f),
                                        size = Size(w * 0.90f, h * 0.44f),
                                        cornerRadius = CornerRadius(w * 0.45f, w * 0.45f),
                                    )
                                }
                            }

                            if (wMix > 0.01f) {
                                val stroke = Stroke(width = (r * 0.14f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
                                val arcScale = 0.72f + 0.28f * wMix
                                for (sign in listOf(-1f, 1f)) {
                                    val ex = cx + sign * eyeOffX
                                    val arcW = eyeW * 1.3f * arcScale
                                    val arcH = eyeH * 0.9f * arcScale
                                    drawArc(
                                        color = paper.copy(alpha = wMix),
                                        startAngle = 195f,
                                        sweepAngle = 150f,
                                        useCenter = false,
                                        topLeft = Offset(ex - arcW * 0.5f, eyeY - eyeH * 0.35f + (1f - wMix) * r * 0.08f),
                                        size = Size(arcW, arcH),
                                        style = stroke,
                                    )
                                }
                                val handPivot = Offset(cx + r * (0.72f + 0.20f * wMix), cy + r * 0.15f)
                                val waveAngle = 18f + sin(tSec * 10.5f) * 24f + sin(tSec * 21f) * 4f
                                val armLenX = r * 0.36f * wMix
                                val armLenY = r * 0.52f * wMix
                                rotate(degrees = waveAngle, pivot = handPivot) {
                                    drawLine(
                                        color = ink.copy(alpha = wMix),
                                        start = handPivot,
                                        end = Offset(handPivot.x + armLenX, handPivot.y - armLenY),
                                        strokeWidth = (r * 0.24f * wMix).coerceAtLeast(1.6f),
                                        cap = StrokeCap.Round,
                                    )
                                    drawCircle(
                                        color = ringBlue.copy(alpha = wMix),
                                        radius = r * 0.13f * wMix,
                                        center = Offset(handPivot.x + armLenX, handPivot.y - armLenY),
                                    )
                                }
                            }
                        }

                        // 5. Передние 3D-дуги орбит + кометы и орбитальная искра.
                        if (hasSecondary) {
                            rotate(degrees = secAngle, pivot = Offset(cx, ringCy)) {
                                drawTaperedOrbitHalf(
                                    path = orbitPath,
                                    isFront = true,
                                    cx = cx,
                                    cy = ringCy,
                                    rx = secRx,
                                    ry = secRy,
                                    baseHalfWidth = secHalfWidth,
                                    brush = secFrontBrush,
                                    alpha = 0.96f - 0.38f * thMix,
                                )
                                if (variant == MascotVariant.PARALLEL_ORBIT && thMix < 0.6f) {
                                    val orbDeg = ((tSec * 52f) % 180f)
                                    val orbRad = Math.toRadians(orbDeg.toDouble())
                                    val fade = sin(orbRad).toFloat().coerceIn(0f, 1f) * (1f - thMix * 1.6f).coerceIn(0f, 1f)
                                    if (fade > 0.02f) {
                                        val sx = cx + secRx * cos(orbRad).toFloat()
                                        val sy = ringCy + secRy * sin(orbRad).toFloat()
                                        drawCircle(
                                            color = MascotRingCyan.copy(alpha = fade * 0.36f),
                                            radius = (r * 0.19f).coerceAtLeast(2.2f),
                                            center = Offset(sx, sy),
                                        )
                                        drawCircle(
                                            color = MascotRingIce.copy(alpha = fade),
                                            radius = (r * 0.095f).coerceAtLeast(1.3f),
                                            center = Offset(sx, sy),
                                        )
                                    }
                                }
                                if (thMix > 0.01f) {
                                    drawOrbitCometHalf(
                                        isFront = true,
                                        headDeg = headDeg2,
                                        tailLen = 92f,
                                        ringTopLeft = secTopLeft,
                                        ringSize = secSize,
                                        baseWidth = (secHalfWidth * 1.9f).coerceAtLeast(1.2f),
                                        color = MascotRingCyan,
                                        alphaScale = thMix * 0.9f,
                                    )
                                }
                            }
                        }
                        rotate(degrees = ringAngle, pivot = Offset(cx, ringCy)) {
                            drawTaperedOrbitHalf(
                                path = orbitPath,
                                isFront = true,
                                cx = cx,
                                cy = ringCy,
                                rx = ringRx,
                                ry = ringRy,
                                baseHalfWidth = primaryHalfWidth,
                                brush = primaryFrontBrush,
                                alpha = 1f - 0.42f * thMix,
                            )
                            if (thMix > 0.01f) {
                                drawOrbitCometHalf(
                                    isFront = true,
                                    headDeg = headDeg,
                                    tailLen = 105f,
                                    ringTopLeft = ringTopLeft,
                                    ringSize = ringSize,
                                    baseWidth = (primaryHalfWidth * 1.65f).coerceAtLeast(1.8f),
                                    color = ringBlue,
                                    alphaScale = thMix,
                                )
                                if (headDeg in 0f..180f) {
                                    val rad = Math.toRadians(headDeg.toDouble())
                                    val horizonFade = sin(rad).toFloat().coerceIn(0f, 1f)
                                    val sparkAlpha = (horizonFade * 1.45f).coerceIn(0f, 1f) * thMix
                                    val dotX = cx + ringRx * cos(rad).toFloat()
                                    val dotY = ringCy + ringRy * sin(rad).toFloat()
                                    drawCircle(
                                        color = MascotRingIce.copy(alpha = sparkAlpha * 0.45f),
                                        radius = (r * 0.25f).coerceAtLeast(2.6f),
                                        center = Offset(dotX, dotY),
                                    )
                                    drawCircle(
                                        color = MascotSparkBlue.copy(alpha = sparkAlpha),
                                        radius = (r * 0.15f).coerceAtLeast(1.9f),
                                        center = Offset(dotX, dotY),
                                    )
                                }
                            }
                        }
                    }
                }
            },
    )
}

/**
 * Отрисовка половины орбиты переменной толщины (3D-перспектива ленты без стыков на 0° и 180°).
 */
private fun DrawScope.drawTaperedOrbitHalf(
    path: Path,
    isFront: Boolean,
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    baseHalfWidth: Float,
    brush: Brush,
    alpha: Float = 1f,
) {
    val steps = 28
    val startDeg = if (isFront) 0f else 180f
    val endDeg = if (isFront) 180f else 360f
    path.rewind()

    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val deg = startDeg + (endDeg - startDeg) * t
        val rad = deg * (PI.toFloat() / 180f)
        val c = cos(rad)
        val s = sin(rad)
        val x = cx + rx * c
        val y = cy + ry * s
        val nxRaw = ry * c
        val nyRaw = rx * s
        val nLen = hypot(nxRaw, nyRaw).coerceAtLeast(1e-4f)
        val nx = nxRaw / nLen
        val ny = nyRaw / nLen
        val halfW = if (s >= 0f) {
            baseHalfWidth * (0.46f + 0.78f * s)
        } else {
            baseHalfWidth * (0.46f + 0.18f * s)
        }
        val ox = x + nx * halfW
        val oy = y + ny * halfW
        if (i == 0) path.moveTo(ox, oy) else path.lineTo(ox, oy)
    }

    for (i in steps downTo 0) {
        val t = i.toFloat() / steps
        val deg = startDeg + (endDeg - startDeg) * t
        val rad = deg * (PI.toFloat() / 180f)
        val c = cos(rad)
        val s = sin(rad)
        val x = cx + rx * c
        val y = cy + ry * s
        val nxRaw = ry * c
        val nyRaw = rx * s
        val nLen = hypot(nxRaw, nyRaw).coerceAtLeast(1e-4f)
        val nx = nxRaw / nLen
        val ny = nyRaw / nLen
        val halfW = if (s >= 0f) {
            baseHalfWidth * (0.46f + 0.78f * s)
        } else {
            baseHalfWidth * (0.46f + 0.18f * s)
        }
        path.lineTo(x - nx * halfW, y - ny * halfW)
    }

    path.close()
    drawPath(path = path, brush = brush, alpha = alpha)
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
        val t = (i + 0.5f) / segments
        val rawStart = headDeg - tailLen + i * step
        val rawEnd = rawStart + step + 0.6f
        val normStart = ((rawStart % 360f) + 360f) % 360f
        val normEnd = normStart + (rawEnd - rawStart)

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
    variantOverride: MascotVariant? = null,
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
            variantOverride = variantOverride,
        )
    }
}
