package app.nastavnik.ui

import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nastavnik.session.AnswerUi
import app.nastavnik.theme.MentorVisionStyle
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Неперехватывающий (0 pointer modifiers) HUD-слой «живого зрения» наставника поверх листа тетради:
 * 1) Письмо: светящееся волокно и боковые метки на высоте кончика пера.
 * 2) Пауза перед автопроверкой: мягкое дыхание на последней строке + кольцо отсчёта.
 * 3) Чтение страницы: 4-угловой видоискатель, световой луч сканирования и подсветка найденных строк.
 * 4) Анализ шагов: перескакивающий нейронный фокус с плавной кривой связи между формулами.
 */
@Composable
fun MentorVisionLayer(
    ctl: NotebookController,
    mentorAnswer: AnswerUi?,
    silentOkAt: Long,
    style: MentorVisionStyle,
    modifier: Modifier = Modifier,
) {
    if (style == MentorVisionStyle.OFF) return

    val streaming = mentorAnswer?.streaming == true
    val marks = mentorAnswer?.marks.orEmpty()
    val isCountingDown = !ctl.writing && ctl.autoCheckProgress > 0.01f
    val isOkFlashing = silentOkAt > 0L && (System.currentTimeMillis() - silentOkAt) < 2200L

    val stops = Brand.mascotColor.resolveStops(Brand.Accent.toArgb())
    val midColor by animateColorAsState(Color(stops.midArgb), tween(320), label = "visMid")
    val haloColor by animateColorAsState(Color(stops.haloArgb), tween(320), label = "visHalo")
    val sparkColor by animateColorAsState(Color(stops.sparkArgb), tween(320), label = "visSpark")

    var initialScanWindow by remember { mutableStateOf(false) }
    LaunchedEffect(streaming, mentorAnswer?.seq) {
        if (streaming && ctl.debugVisionAnswer == null) {
            initialScanWindow = true
            delay(1350L)
            initialScanWindow = false
        } else {
            initialScanWindow = false
        }
    }

    val isReadingPhase = streaming && (
        mentorAnswer?.status?.contains("Смотрю") == true || initialScanWindow
        )
    val isAnalyzingPhase = streaming && !isReadingPhase

    val writeMix by animateFloatAsState(
        targetValue = if (ctl.writing || ctl.mentorWriting) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
        label = "writeMix",
    )
    val countdownMix by animateFloatAsState(
        targetValue = if (isCountingDown) 1f else 0f,
        animationSpec = tween(260),
        label = "countdownMix",
    )
    val scanMix by animateFloatAsState(
        targetValue = if (isReadingPhase) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 340f),
        label = "scanMix",
    )
    val analyzeMix by animateFloatAsState(
        targetValue = if (isAnalyzingPhase) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 320f),
        label = "analyzeMix",
    )
    val okMix by animateFloatAsState(
        targetValue = if (isOkFlashing) 1f else 0f,
        animationSpec = tween(300),
        label = "okMix",
    )

    val smoothPenX by animateFloatAsState(
        targetValue = ctl.penViewX,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 620f),
        label = "penX",
    )
    val smoothPenY by animateFloatAsState(
        targetValue = ctl.penViewY,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 620f),
        label = "penY",
    )

    val anyActive = ctl.writing || ctl.mentorWriting || isCountingDown || streaming || isOkFlashing ||
        writeMix > 0.01f || countdownMix > 0.01f || scanMix > 0.01f || analyzeMix > 0.01f || okMix > 0.01f

    var timeSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(anyActive) {
        if (!anyActive) {
            timeSec = 0f
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> timeSec = (now - start) / 1_000_000_000f }
        }
    }

    val detectedLines = ctl.detectedPageLines
    val lineCount = detectedLines.size
    val activeFocusIdx = if (analyzeMix > 0.01f && lineCount > 0) {
        ctl.debugVisionFocusIdx?.coerceIn(0, lineCount - 1)
            ?: (((timeSec / 0.95f).toInt()) % lineCount)
    } else {
        0
    }

    val sheetRect = remember { RectF() }
    val mappedRect = remember { RectF() }
    val prevMappedRect = remember { RectF() }
    val tracePath = remember { Path() }

    // Пространственная цель взгляда сферы внутри плашки статуса.
    val pillGaze: Offset? = when {
        ctl.writing || ctl.mentorWriting -> Offset(
            x = -0.85f + (ctl.penNormX - 0.5f) * 0.4f,
            y = ((ctl.penNormY - 0.35f) * 1.1f).coerceIn(-0.85f, 0.85f),
        )
        isAnalyzingPhase && lineCount > 0 -> {
            val l = detectedLines[activeFocusIdx]
            Offset(
                x = -0.7f,
                y = (((l.centerY() / 1414f) - 0.35f) * 1.1f).coerceIn(-0.85f, 0.85f),
            )
        }
        else -> null
    }

    // Пружинный перелёт рамки фокуса между строками в фазе анализа.
    val targetLinePageRect = detectedLines.getOrNull(activeFocusIdx)
    val fLeft by animateFloatAsState(targetLinePageRect?.left ?: 0f, spring(0.84f, 380f), label = "fL")
    val fTop by animateFloatAsState(targetLinePageRect?.top ?: 0f, spring(0.84f, 380f), label = "fT")
    val fRight by animateFloatAsState(targetLinePageRect?.right ?: 0f, spring(0.84f, 380f), label = "fR")
    val fBottom by animateFloatAsState(targetLinePageRect?.bottom ?: 0f, spring(0.84f, 380f), label = "fB")
    val focusPageRect = remember { RectF() }

    Box(modifier = modifier.fillMaxSize()) {
        if (anyActive) {
            Canvas(Modifier.fillMaxSize()) {
                if (!ctl.ink.getSheetBoundsInView(sheetRect)) return@Canvas
                val sL = sheetRect.left
                val sT = sheetRect.top
                val sR = sheetRect.right
                val sB = sheetRect.bottom
                val sW = (sR - sL).coerceAtLeast(1f)
                val sH = (sB - sT).coerceAtLeast(1f)

                // 1. Слежение за кончиком пера (письмо или ожидание автопроверки).
                val penTrackAlpha = maxOf(writeMix, countdownMix * 0.72f)
                if (penTrackAlpha > 0.01f && smoothPenY > 1f) {
                    val py = smoothPenY.coerceIn(sT + 12f, sB - 12f)
                    val filamentH = 78.dp.toPx()
                    val filamentW = 3.5.dp.toPx()
                    val breath = 0.85f + 0.15f * sin(timeSec * 4.5f)
                    val barAlpha = (if (style == MentorVisionStyle.SUBTLE) 0.65f else 0.92f) * penTrackAlpha * breath

                    // Правое световое волокно у кромки листа.
                    val barX = sR - 5.dp.toPx()
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.25f to haloColor.copy(alpha = 0.45f * barAlpha),
                            0.5f to sparkColor.copy(alpha = barAlpha),
                            0.75f to midColor.copy(alpha = 0.55f * barAlpha),
                            1.0f to Color.Transparent,
                            startY = py - filamentH * 0.5f,
                            endY = py + filamentH * 0.5f,
                        ),
                        topLeft = Offset(barX, py - filamentH * 0.5f),
                        size = Size(filamentW, filamentH),
                        cornerRadius = CornerRadius(filamentW * 0.5f, filamentW * 0.5f),
                    )

                    // Мягкое боковое свечение на полях.
                    val auraR = 52.dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to haloColor.copy(alpha = 0.16f * barAlpha),
                            1.0f to Color.Transparent,
                            center = Offset(barX, py),
                            radius = auraR,
                        ),
                        radius = auraR,
                        center = Offset(barX, py),
                    )

                    if (style != MentorVisionStyle.SUBTLE) {
                        // Аккуратные парные риски на левой и правой кромке листа + невесомый горизонтальный луч.
                        val tickW = 8.dp.toPx()
                        val tickH = 2.2.dp.toPx()
                        drawRoundRect(
                            color = midColor.copy(alpha = 0.72f * penTrackAlpha),
                            topLeft = Offset(sL + 4.dp.toPx(), py - tickH * 0.5f),
                            size = Size(tickW, tickH),
                            cornerRadius = CornerRadius(tickH * 0.5f, tickH * 0.5f),
                        )
                        val guideLeft = sL + 14.dp.toPx()
                        val guideW = (sR - 10.dp.toPx() - guideLeft).coerceAtLeast(1f)
                        val guideH = 1.2.dp.toPx()
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                0.0f to midColor.copy(alpha = 0.03f * penTrackAlpha),
                                0.5f to haloColor.copy(alpha = 0.07f * penTrackAlpha),
                                1.0f to sparkColor.copy(alpha = 0.16f * penTrackAlpha),
                                startX = sL,
                                endX = sR,
                            ),
                            topLeft = Offset(guideLeft, py - guideH * 0.5f),
                            size = Size(guideW, guideH),
                            cornerRadius = CornerRadius(guideH * 0.5f, guideH * 0.5f),
                        )
                    }
                }

                // 2. Видоискатель и волна чтения страницы.
                val visionAura = maxOf(scanMix, analyzeMix)
                if (visionAura > 0.01f) {
                    val bracketAlpha = (0.45f + 0.25f * sin(timeSec * 3.8f)) * visionAura
                    drawSheetCornerBrackets(
                        left = sL + 8.dp.toPx(),
                        top = sT + 8.dp.toPx(),
                        right = sR - 8.dp.toPx(),
                        bottom = sB - 8.dp.toPx(),
                        armLen = 22.dp.toPx(),
                        strokePx = 2.dp.toPx(),
                        color = midColor.copy(alpha = bracketAlpha),
                    )
                }

                if (scanMix > 0.01f) {
                    val rawCycle = (timeSec / 1.35f) % 1f
                    // Плавное синусоидальное прохождение сверху вниз и обратно.
                    val sweep = (1f - cos(rawCycle * PI.toFloat())) * 0.5f
                    val scanY = sT + sweep * sH

                    if (style == MentorVisionStyle.AURORA_SCAN) {
                        val beamH = 88.dp.toPx()
                        val beamTop = (scanY - beamH).coerceAtLeast(sT)
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.72f to haloColor.copy(alpha = 0.16f * scanMix),
                                1.0f to sparkColor.copy(alpha = 0.34f * scanMix),
                                startY = beamTop,
                                endY = scanY,
                            ),
                            topLeft = Offset(sL, beamTop),
                            size = Size(sW, (scanY - beamTop).coerceAtLeast(1f)),
                        )
                        val lineLeft = sL + 4.dp.toPx()
                        val lineW = (sR - 4.dp.toPx() - lineLeft).coerceAtLeast(1f)
                        val lineH = 2.4.dp.toPx()
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                0.0f to Color.Transparent,
                                0.2f to sparkColor.copy(alpha = 0.90f * scanMix),
                                0.5f to Color.White.copy(alpha = 0.96f * scanMix),
                                0.8f to sparkColor.copy(alpha = 0.90f * scanMix),
                                1.0f to Color.Transparent,
                                startX = sL,
                                endX = sR,
                            ),
                            topLeft = Offset(lineLeft, scanY - lineH * 0.5f),
                            size = Size(lineW, lineH),
                            cornerRadius = CornerRadius(lineH * 0.5f, lineH * 0.5f),
                        )
                    } else if (style == MentorVisionStyle.SUBTLE) {
                        val barW = sW * 0.45f
                        val barX = sL + (sW - barW) * sweep
                        drawLine(
                            color = sparkColor.copy(alpha = 0.85f * scanMix),
                            start = Offset(barX, sT + 3.dp.toPx()),
                            end = Offset(barX + barW, sT + 3.dp.toPx()),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }

                    // Подсветка распознанных строк, когда луч проходит мимо них.
                    if (style != MentorVisionStyle.SUBTLE) {
                        detectedLines.forEachIndexed { idx, pRect ->
                            if (ctl.ink.mapPageRectToView(pRect, mappedRect)) {
                                val cy = mappedRect.centerY()
                                val dist = if (style == MentorVisionStyle.AURORA_SCAN) {
                                    (1f - abs(cy - scanY) / (sH * 0.24f)).coerceIn(0.25f, 1f)
                                } else {
                                    0.78f
                                }
                                drawDetectedLineHighlight(
                                    rect = mappedRect,
                                    alpha = dist * scanMix,
                                    fillColor = haloColor,
                                    borderColor = midColor,
                                )
                            }
                        }
                    }
                }

                // 3. Глубокий пошаговый анализ: фокус перелетает между формулами со связующим лучом.
                if (analyzeMix > 0.01f && style != MentorVisionStyle.SUBTLE) {
                    detectedLines.forEachIndexed { idx, pRect ->
                        if (ctl.ink.mapPageRectToView(pRect, mappedRect)) {
                            val isFocused = idx == activeFocusIdx
                            drawRoundRect(
                                color = midColor.copy(alpha = (if (isFocused) 0.16f else 0.06f) * analyzeMix),
                                topLeft = Offset(mappedRect.left, mappedRect.top),
                                size = Size(mappedRect.width().coerceAtLeast(12f), mappedRect.height().coerceAtLeast(12f)),
                                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                            )
                            if (!isFocused) {
                                drawRoundRect(
                                    color = midColor.copy(alpha = 0.22f * analyzeMix),
                                    topLeft = Offset(mappedRect.left, mappedRect.top),
                                    size = Size(mappedRect.width().coerceAtLeast(12f), mappedRect.height().coerceAtLeast(12f)),
                                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                                    style = Stroke(width = 1.dp.toPx()),
                                )
                            }
                        }
                    }

                    if (targetLinePageRect != null) {
                        focusPageRect.set(fLeft, fTop, fRight, fBottom)
                        if (ctl.ink.mapPageRectToView(focusPageRect, mappedRect)) {
                            // Связующая кривая от предыдущей строки к текущей.
                            if (lineCount > 1) {
                                val prevIdx = (activeFocusIdx - 1 + lineCount) % lineCount
                                if (ctl.ink.mapPageRectToView(detectedLines[prevIdx], prevMappedRect)) {
                                    tracePath.reset()
                                    val x0 = prevMappedRect.left
                                    val y0 = prevMappedRect.centerY()
                                    val x1 = mappedRect.left
                                    val y1 = mappedRect.centerY()
                                    val ctrlX = minOf(x0, x1) - 26.dp.toPx()
                                    tracePath.moveTo(x0, y0)
                                    tracePath.cubicTo(ctrlX, y0, ctrlX, y1, x1, y1)
                                    drawPath(
                                        path = tracePath,
                                        color = sparkColor.copy(alpha = 0.55f * analyzeMix),
                                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
                                    )
                                }
                            }

                            // Яркая фокусная рамка вокруг анализируемой строки.
                            val pulse = 0.85f + 0.15f * sin(timeSec * 6.0f)
                            drawDetectedLineHighlight(
                                rect = mappedRect,
                                alpha = analyzeMix * pulse,
                                fillColor = sparkColor,
                                borderColor = midColor,
                                strong = true,
                            )
                        }
                    }
                }

                // 4. Вспышка «Всё верно» при тихой проверке.
                if (okMix > 0.01f) {
                    drawRoundRect(
                        color = Brand.Ok.copy(alpha = 0.42f * okMix),
                        topLeft = Offset(sL + 4.dp.toPx(), sT + 4.dp.toPx()),
                        size = Size((sW - 8.dp.toPx()).coerceAtLeast(10f), (sH - 8.dp.toPx()).coerceAtLeast(10f)),
                        cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                }
            }
        }

        // Неблокирующая капсула-телеметрия в углу листа (уходит вниз, если перо в верхней четверти).
        val hudVisible = ctl.writing || ctl.mentorWriting || isCountingDown || streaming || isOkFlashing
        val hudAtBottom = (ctl.writing || ctl.mentorWriting) && ctl.penNormY < 0.24f
        val statusText = when {
            ctl.mentorWriting -> "Пишу на листе…"
            isOkFlashing -> "Проверил шаг · всё верно"
            streaming && marks.isNotEmpty() -> "Отмечаю на листе · ${marks.size}"
            isReadingPhase -> if (lineCount > 0) "Читаю лист · строк: $lineCount" else "Читаю лист…"
            isAnalyzingPhase -> if (lineCount > 0) "Анализирую шаг ${activeFocusIdx + 1} из $lineCount" else "Анализирую решение…"
            isCountingDown -> {
                val leftSec = (((1f - ctl.autoCheckProgress) * 7f) + 0.8f).roundToInt().coerceIn(1, 7)
                "Автопроверка через $leftSec с"
            }
            ctl.writing -> "Слежу за строкой"
            else -> ""
        }

        AnimatedVisibility(
            visible = hudVisible && statusText.isNotEmpty(),
            modifier = Modifier
                .align(if (hudAtBottom) Alignment.BottomEnd else Alignment.TopEnd)
                .padding(14.dp),
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.94f),
        ) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Color(0xDC101218))
                    .border(1.dp, if (isOkFlashing) Brand.Ok.copy(alpha = 0.7f) else Color(0x28FFFFFF), CircleShape)
                    .padding(start = 7.dp, end = 13.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    MascotIcon(
                        writing = ctl.writing,
                        thinking = streaming,
                        sizeDp = 22.dp,
                        gazeTarget = pillGaze,
                    )
                    if (isCountingDown) {
                        val ringColor = midColor
                        Canvas(Modifier.matchParentSize()) {
                            drawArc(
                                color = ringColor,
                                startAngle = -90f,
                                sweepAngle = ctl.autoCheckProgress * 360f,
                                useCenter = false,
                                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
                            )
                        }
                    }
                }
                Text(
                    text = statusText,
                    color = Color(0xFFF3F5FA),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun DrawScope.drawSheetCornerBrackets(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    armLen: Float,
    strokePx: Float,
    color: Color,
) {
    val corners = listOf(
        Triple(Offset(left, top + armLen), Offset(left, top), Offset(left + armLen, top)),
        Triple(Offset(right - armLen, top), Offset(right, top), Offset(right, top + armLen)),
        Triple(Offset(left, bottom - armLen), Offset(left, bottom), Offset(left + armLen, bottom)),
        Triple(Offset(right - armLen, bottom), Offset(right, bottom), Offset(right, bottom - armLen)),
    )
    for ((a, b, c) in corners) {
        drawLine(color = color, start = a, end = b, strokeWidth = strokePx, cap = StrokeCap.Round)
        drawLine(color = color, start = b, end = c, strokeWidth = strokePx, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawDetectedLineHighlight(
    rect: RectF,
    alpha: Float,
    fillColor: Color,
    borderColor: Color,
    strong: Boolean = false,
) {
    val w = rect.width().coerceAtLeast(18f)
    val h = rect.height().coerceAtLeast(18f)
    val rad = 10.dp.toPx()
    drawRoundRect(
        color = fillColor.copy(alpha = (if (strong) 0.16f else 0.10f) * alpha),
        topLeft = Offset(rect.left, rect.top),
        size = Size(w, h),
        cornerRadius = CornerRadius(rad, rad),
    )
    drawRoundRect(
        color = borderColor.copy(alpha = (if (strong) 0.85f else 0.48f) * alpha),
        topLeft = Offset(rect.left, rect.top),
        size = Size(w, h),
        cornerRadius = CornerRadius(rad, rad),
        style = Stroke(width = (if (strong) 2.dp else 1.2.dp).toPx()),
    )

    // Векторный узел строки на левом поле без текстовых аллокаций внутри Canvas.
    val badgeR = (if (strong) 6.5.dp else 5.2.dp).toPx()
    val bx = (rect.left - badgeR - 6.dp.toPx()).coerceAtLeast(badgeR + 2.dp.toPx())
    val by = rect.centerY()
    drawCircle(
        color = fillColor.copy(alpha = (if (strong) 0.28f else 0.14f) * alpha),
        radius = badgeR * 1.45f,
        center = Offset(bx, by),
    )
    drawCircle(
        color = borderColor.copy(alpha = (if (strong) 0.96f else 0.76f) * alpha),
        radius = badgeR,
        center = Offset(bx, by),
    )
    drawCircle(
        color = Color.White.copy(alpha = (if (strong) 0.98f else 0.88f) * alpha),
        radius = (if (strong) 2.5.dp else 1.8.dp).toPx(),
        center = Offset(bx, by),
    )
    if (rect.left > bx + badgeR + 2f) {
        drawRect(
            color = borderColor.copy(alpha = 0.45f * alpha),
            topLeft = Offset(bx + badgeR, by - 0.7.dp.toPx()),
            size = Size(rect.left - bx - badgeR, 1.4.dp.toPx()),
        )
    }
}
