package app.nastavnik.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nastavnik.R
import app.nastavnik.app
import app.nastavnik.theme.TabBarPosition
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

enum class TabletTab(val title: String, val icon: Int) {
    HOME("Главная", R.drawable.ic_home),
    SCHEDULE("Расписание", R.drawable.ic_calendar),
    GRADES("Оценки", R.drawable.ic_chart),
    STUDY("Учёба", R.drawable.ic_cards),
}

data class CapsuleTabItem(
    val title: String,
    val icon: Int,
    val isSelected: Boolean,
    val onClick: () -> Unit,
)

private val BezelObsidian = Color(0xFF0E1117)

/**
 * Планшетная оболочка: полноширинный экран и вылазка вкладок с края при нажатии (слева или справа).
 */
@Composable
fun TabletShell(
    tab: TabletTab,
    onTab: (TabletTab) -> Unit,
    tabBarPosition: TabBarPosition,
    sideCapsuleBias: Float = 0.55f,
    resumeTick: Int,
    go: (String) -> Unit,
    openNotebook: (Long) -> Unit,
) {
    val side = TabBarPosition.forTablet(tabBarPosition.id)
    val railItems = TabletTab.entries.map { entry ->
        CapsuleTabItem(
            title = entry.title,
            icon = entry.icon,
            isSelected = entry == tab,
            onClick = { onTab(entry) },
        )
    }

    Box(Modifier.fillMaxSize().paperBackdrop()) {
        CompositionLocalProvider(LocalBottomBarInset provides 24.dp) {
            Box(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (fadeIn(tween(240, easing = Motion.Decelerate)) +
                            slideInVertically(tween(280, easing = Motion.Emphasized)) { (it * 0.06f * dir).toInt() })
                            .togetherWith(
                                fadeOut(tween(160, easing = Motion.Accelerate)) +
                                    slideOutVertically(tween(260, easing = Motion.Emphasized)) { (-it * 0.06f * dir).toInt() },
                            )
                    },
                    label = "tabletTab",
                    modifier = Modifier.fillMaxSize(),
                ) { current ->
                    when (current) {
                        TabletTab.HOME -> HomeScreen(resumeTick, go, openNotebook)
                        TabletTab.SCHEDULE -> ScheduleScreen(onBack = null)
                        TabletTab.GRADES -> GradesScreen(
                            onBack = null,
                            onSettings = { go(MainActivity.SCREEN_SETTINGS) },
                            onLks = { go(MainActivity.SCREEN_LKS) },
                        )
                        TabletTab.STUDY -> TabletStudyTab(resumeTick, go)
                    }
                }
            }
        }

        EdgeBezelCapsule(
            items = railItems,
            isRight = side == TabBarPosition.RIGHT,
            verticalBias = sideCapsuleBias,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Единая моментная «вылазка» вкладок с края экрана в стиле iPhone Camera Control:
 * в покое — деликатный 5 dp бортик у рамки, при зажатии выезжает чёрная капсульная вылазка
 * со всеми вкладками и управлением вверх/вниз; как только палец отпускается — плавно скрывается.
 */
@Composable
fun EdgeBezelCapsule(
    items: List<CapsuleTabItem>,
    isRight: Boolean,
    verticalBias: Float = 0.55f,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val currentItems by rememberUpdatedState(items)
    val selectedIndex = items.indexOfFirst { it.isSelected }.coerceAtLeast(0)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)

    val bulgeW = 196.dp
    val bulgeH = 284.dp
    val bulgeHpx = with(density) { bulgeH.toPx() }
    val halfBulgePx = bulgeHpx * 0.5f

    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    var anchorY by remember { mutableFloatStateOf(0f) }
    var hasCustomAnchor by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableIntStateOf(selectedIndex) }
    var settledIndex by remember { mutableIntStateOf(selectedIndex) }
    var analogSlot by remember { mutableFloatStateOf(selectedIndex.toFloat()) }

    val expandProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = if (isPressed) {
            spring(dampingRatio = 0.82f, stiffness = 580f, visibilityThreshold = 0.002f)
        } else {
            spring(dampingRatio = 1.0f, stiffness = 1450f, visibilityThreshold = 0.004f)
        },
        label = "bezelExpand",
    )

    LaunchedEffect(selectedIndex, isPressed, expandProgress < 0.02f) {
        if (!isPressed && expandProgress < 0.02f) {
            settledIndex = selectedIndex
            previewIndex = selectedIndex
            analogSlot = selectedIndex.toFloat()
        }
    }

    val defaultAnchorY = if (containerHeightPx > 0f) {
        (containerHeightPx * verticalBias.coerceIn(0.18f, 0.82f))
            .coerceIn(halfBulgePx, (containerHeightPx - halfBulgePx).coerceAtLeast(halfBulgePx))
    } else {
        0f
    }
    val targetAnchorY = if (hasCustomAnchor) anchorY else defaultAnchorY
    val smoothAnchorY by animateFloatAsState(
        targetValue = targetAnchorY,
        animationSpec = if (expandProgress < 0.03f) {
            snap()
        } else {
            spring(dampingRatio = 0.92f, stiffness = 280f)
        },
        label = "bezelAnchorY",
    )

    val maxIdx = (items.size - 1).coerceAtLeast(0)
    val smoothSlot by animateFloatAsState(
        targetValue = if (isPressed) analogSlot else settledIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 620f),
        label = "bezelSlot",
    )
    val pillTarget = if (isPressed) {
        continuousSlotCurve(analogSlot, maxIdx)
    } else {
        settledIndex.toFloat()
    }
    val pillSlot by animateFloatAsState(
        targetValue = pillTarget,
        animationSpec = spring(dampingRatio = 0.90f, stiffness = 560f),
        label = "bezelPillSlot",
    )

    val bulgePath = remember { Path() }
    val isDark = Brand.dark
    val accentColor = Brand.Accent
    val onAccentColor = Brand.OnAccent

    Box(modifier.fillMaxSize()) {
        if (expandProgress > 0.008f) {
            val pClamped = expandProgress.coerceIn(0f, 1f)
            val scrimCurve = if (isPressed) pClamped else pClamped.pow(1.7f)
            val scrimAlpha = (if (isDark) 0.28f else 0.10f) * scrimCurve
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha)),
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .onSizeChanged { containerHeightPx = it.height.toFloat() },
        ) {
            // Деликатный намёк вдоль всего активного края экрана в состоянии покоя.
            val idleStripAlpha = ((1f - expandProgress * 3.2f).coerceIn(0f, 1f)) * 0.55f
            if (idleStripAlpha > 0.01f) {
                Canvas(
                    Modifier
                        .align(if (isRight) Alignment.CenterEnd else Alignment.CenterStart)
                        .fillMaxHeight(0.76f)
                        .width(4.dp),
                ) {
                    val lineW = 2.dp.toPx()
                    val x = if (isRight) size.width - lineW - 0.5.dp.toPx() else 0.5.dp.toPx()
                    val brush = Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.15f to accentColor.copy(alpha = idleStripAlpha * 0.65f),
                        0.50f to accentColor.copy(alpha = idleStripAlpha),
                        0.85f to accentColor.copy(alpha = idleStripAlpha * 0.65f),
                        1.00f to Color.Transparent,
                    )
                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(x, 0f),
                        size = Size(lineW, size.height),
                        cornerRadius = CornerRadius(lineW * 0.5f, lineW * 0.5f),
                    )
                }
            }

            // Органическая вылазка от рамки экрана, всплывающая на высоте нажатия пальца.
            if (expandProgress > 0.004f) {
                val bulgeTopPx = (smoothAnchorY - halfBulgePx).roundToInt()
                val isClosing = !isPressed
                Box(
                    Modifier
                        .align(if (isRight) Alignment.TopEnd else Alignment.TopStart)
                        .offset { IntOffset(0, bulgeTopPx) }
                        .size(width = bulgeW, height = bulgeH),
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val p = expandProgress.coerceIn(0f, 1f)
                        val widthProgress = if (isClosing) {
                            p.pow(1.48f)
                        } else {
                            1f - (1f - p).pow(1.25f)
                        }
                        val heightProgress = if (isClosing) {
                            sin(p * (PI.toFloat() * 0.5f)).pow(0.72f)
                        } else {
                            sin(p * (PI.toFloat() * 0.5f))
                        }
                        val tensionWave = sin(p * PI.toFloat())
                        val bodyW = lerp(0f, 180.dp.toPx(), widthProgress)
                        val bodyH = lerp(32.dp.toPx(), 216.dp.toPx(), heightProgress)
                        val shoulder = lerp(8.dp.toPx(), 32.dp.toPx(), heightProgress) +
                            tensionWave * (if (isClosing) 10.dp.toPx() else 4.dp.toPx())
                        val cornerR = lerp(2.dp.toPx(), 26.dp.toPx(), widthProgress)
                            .coerceAtMost(bodyW * 0.85f)
                            .coerceAtMost(bodyH * 0.45f)

                        if (bodyW > 0.5f) {
                            drawBezelBulgePath(
                                path = bulgePath,
                                isRight = isRight,
                                bodyW = bodyW,
                                bodyH = bodyH,
                                shoulder = shoulder,
                                cornerR = cornerR,
                            )

                            drawPath(path = bulgePath, color = BezelObsidian)

                            if (widthProgress > 0.04f) {
                                val rimAlpha = (if (isDark) 0.20f else 0.14f) * widthProgress
                                drawPath(
                                    path = bulgePath,
                                    color = Color.White.copy(alpha = rimAlpha),
                                    style = Stroke(width = 1.dp.toPx()),
                                )
                            }
                        }

                        // Акцентный световой импульс «запечатывания» в рамку при отпускании.
                        if (isClosing && p in 0.015f..0.60f) {
                            val gleamPhase = ((p - 0.015f) / (0.60f - 0.015f)).coerceIn(0f, 1f)
                            val gleamIntensity = sin(gleamPhase * PI.toFloat()).coerceIn(0f, 1f)
                            val itemFraction = if (items.isNotEmpty()) {
                                ((settledIndex + 0.5f) / items.size.toFloat()).coerceIn(0.12f, 0.88f)
                            } else {
                                0.5f
                            }
                            val gleamCenterY = size.height * 0.5f - bodyH * 0.44f + bodyH * 0.88f * itemFraction
                            val gleamH = 44.dp.toPx() * (0.7f + 0.5f * gleamIntensity)
                            val gleamTop = (gleamCenterY - gleamH * 0.5f).coerceIn(0f, size.height)
                            val gleamBottom = (gleamCenterY + gleamH * 0.5f).coerceIn(gleamTop, size.height)
                            val gleamW = lerp(2.dp.toPx(), 3.5.dp.toPx(), gleamIntensity)
                            val gleamX = if (isRight) size.width - gleamW else 0f
                            val gleamBrush = Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.5f to accentColor.copy(alpha = 0.92f * gleamIntensity),
                                1.0f to Color.Transparent,
                                startY = gleamTop,
                                endY = gleamBottom,
                            )
                            drawRoundRect(
                                brush = gleamBrush,
                                topLeft = Offset(gleamX, gleamTop),
                                size = Size(gleamW, (gleamBottom - gleamTop).coerceAtLeast(1f)),
                                cornerRadius = CornerRadius(gleamW * 0.5f, gleamW * 0.5f),
                            )
                        }

                        // Тонкие тактильные риски вдоль края, как на диске управления iPhone.
                        val dialThreshold = if (isClosing) 0.36f else 0.22f
                        if (p > dialThreshold && items.size > 1) {
                            val dialAlpha = ((p - dialThreshold) / (1f - dialThreshold).coerceAtLeast(0.2f))
                                .coerceIn(0f, 1f)
                            val spanTop = size.height * 0.5f - bodyH * 0.40f
                            val spanH = bodyH * 0.80f
                            val xEdge = if (isRight) size.width else 0f
                            val dir = if (isRight) -1f else 1f
                            val tickInset = 4.dp.toPx()
                            val totalTicks = 13
                            for (i in 0 until totalTicks) {
                                val tNorm = i.toFloat() / (totalTicks - 1)
                                val y = spanTop + tNorm * spanH
                                val slotPos = tNorm * (items.size - 1)
                                val isMajor = i % 4 == 0
                                val dist = abs(slotPos - smoothSlot)
                                val focus = (1f - dist).coerceIn(0f, 1f)
                                val len = (if (isMajor) 7.dp else 4.dp).toPx() + focus * 2.dp.toPx()
                                val baseAlpha = (if (isMajor) 0.42f else 0.18f) * dialAlpha
                                val color = lerp(
                                    Color.White.copy(alpha = baseAlpha),
                                    accentColor.copy(alpha = dialAlpha),
                                    focus,
                                )
                                drawLine(
                                    color = color,
                                    start = Offset(xEdge + dir * tickInset, y),
                                    end = Offset(xEdge + dir * (tickInset + len), y),
                                    strokeWidth = (if (isMajor) 1.5.dp else 1.dp).toPx(),
                                    cap = StrokeCap.Round,
                                )
                            }
                        }
                    }

                    // Контейнер вкладок внутри вылазки с плавающей плашкой выбора.
                    val contentProgress = if (isClosing) {
                        ((expandProgress - 0.34f) / 0.66f).coerceIn(0f, 1f)
                    } else {
                        ((expandProgress - 0.14f) / 0.62f).coerceIn(0f, 1f)
                    }
                    val easedContent = contentProgress * contentProgress * (3f - 2f * contentProgress)
                    if (easedContent > 0.01f) {
                        val shiftPx = with(density) {
                            (1f - easedContent) * (if (isClosing) 38.dp else 26.dp).toPx() * (if (isRight) 1f else -1f)
                        }
                        val pivotY = if (items.isNotEmpty()) {
                            ((settledIndex + 0.5f) / items.size.toFloat()).coerceIn(0.12f, 0.88f)
                        } else {
                            0.5f
                        }
                        val stepPx = with(density) { 50.dp.toPx() }
                        val pillOffsetY = (pillSlot * stepPx).roundToInt()

                        Box(
                            Modifier
                                .align(if (isRight) Alignment.CenterEnd else Alignment.CenterStart)
                                .padding(
                                    end = if (isRight) 20.dp else 0.dp,
                                    start = if (!isRight) 20.dp else 0.dp,
                                )
                                .width(148.dp)
                                .height(196.dp)
                                .graphicsLayer {
                                    alpha = easedContent
                                    translationX = shiftPx
                                    scaleX = lerp(if (isClosing) 0.72f else 0.84f, 1f, easedContent)
                                    scaleY = lerp(if (isClosing) 0.84f else 0.92f, 1f, easedContent)
                                    transformOrigin = TransformOrigin(if (isRight) 1f else 0f, pivotY)
                                },
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .offset { IntOffset(0, pillOffsetY) }
                                    .clip(CircleShape)
                                    .background(accentColor),
                            )

                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items.forEachIndexed { idx, item ->
                                    val focus = (1f - abs(idx - pillSlot)).coerceIn(0f, 1f)
                                    val tint = lerp(
                                        Color.White.copy(alpha = 0.72f),
                                        onAccentColor,
                                        focus,
                                    )
                                    val iconScale = lerp(1f, 1.08f, focus)
                                    val isHighlighted = idx == (if (isPressed) previewIndex else settledIndex)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                            .clip(CircleShape)
                                            .padding(horizontal = 14.dp)
                                            .semantics {
                                                contentDescription = item.title
                                                selected = isHighlighted
                                            },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(
                                            painterResource(item.icon),
                                            contentDescription = null,
                                            tint = tint,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                                        )
                                        Text(
                                            item.title,
                                            fontSize = 13.sp,
                                            fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Medium,
                                            color = tint,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Полновысотная сенсорная полоса вдоль края (с динамическим 200dp systemGestureExclusion):
            // зажим на любой высоте края открывает вылазку под пальцем, смена вкладки происходит при отпускании.
            val exclusionHalfPx = with(density) { 100.dp.toPx() }
            val exclusionTopPx = (smoothAnchorY - exclusionHalfPx)
                .coerceIn(0f, (containerHeightPx - exclusionHalfPx * 2f).coerceAtLeast(0f))
                .roundToInt()

            Box(
                Modifier
                    .align(if (isRight) Alignment.CenterEnd else Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(26.dp)
                    .semantics { contentDescription = "Панель вкладок" }
                    .pointerInput(items.size, isRight) {
                        val stepPx = 40.dp.toPx()
                        val touchSlopPx = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downX = down.position.x
                            val downY = down.position.y
                            val clampedY = if (containerHeightPx > 0f) {
                                downY.coerceIn(
                                    halfBulgePx,
                                    (containerHeightPx - halfBulgePx).coerceAtLeast(halfBulgePx),
                                )
                            } else {
                                downY
                            }

                            // Даём 60 мс на распознавание быстрого горизонтального системного свайпа «Назад».
                            val quickCancel = withTimeoutOrNull(60L) {
                                var canceledAsBack = false
                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    val ch = ev.changes.firstOrNull { it.id == down.id }
                                    if (ch == null || ch.changedToUpIgnoreConsumed() || !ch.pressed) {
                                        canceledAsBack = true
                                        break
                                    }
                                    val dx = ch.position.x - downX
                                    val dy = ch.position.y - downY
                                    val inwardDx = if (isRight) -dx else dx
                                    if (inwardDx > touchSlopPx && inwardDx > abs(dy) * 1.4f) {
                                        canceledAsBack = true
                                        break
                                    }
                                    if (abs(dy) > touchSlopPx) {
                                        break
                                    }
                                }
                                canceledAsBack
                            }
                            if (quickCancel == true) return@awaitEachGesture

                            val startIdx = currentSelectedIndex.coerceIn(0, (currentItems.size - 1).coerceAtLeast(0))
                            anchorY = clampedY
                            hasCustomAnchor = true
                            previewIndex = startIdx
                            settledIndex = startIdx
                            analogSlot = startIdx.toFloat()
                            isPressed = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                            var liftedNormally = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                                        liftedNormally = true
                                        break
                                    }
                                    change.consume()
                                    val dy = change.position.y - downY
                                    val maxSlot = (currentItems.size - 1).coerceAtLeast(0).toFloat()
                                    val nextSlot = (startIdx + dy / stepPx).coerceIn(0f, maxSlot)
                                    analogSlot = nextSlot
                                    val candidateIdx = nextSlot.roundToInt()
                                        .coerceIn(0, currentItems.lastIndex.coerceAtLeast(0))
                                    if (candidateIdx != previewIndex) {
                                        previewIndex = candidateIdx
                                        settledIndex = candidateIdx
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            } finally {
                                if (liftedNormally && currentItems.isNotEmpty()) {
                                    val commitIdx = previewIndex.coerceIn(0, currentItems.lastIndex)
                                    settledIndex = commitIdx
                                    isPressed = false
                                    if (commitIdx != currentSelectedIndex) {
                                        currentItems[commitIdx].onClick()
                                    }
                                } else {
                                    isPressed = false
                                }
                            }
                        }
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .offset { IntOffset(0, exclusionTopPx) }
                        .systemGestureExclusion(),
                )
            }
        }
    }
}

/** C¹/C²-непрерывная кривая магнитного притяжения к центрам вкладок без резких скачков. */
private fun continuousSlotCurve(slot: Float, maxIdx: Int): Float {
    if (maxIdx <= 0) return 0f
    val clamped = slot.coerceIn(0f, maxIdx.toFloat())
    val base = floor(clamped).toInt().coerceIn(0, (maxIdx - 1).coerceAtLeast(0))
    val frac = (clamped - base).coerceIn(0f, 1f)
    val s = frac * frac * (3f - 2f * frac)
    val smoother = s * s * (3f - 2f * s)
    return (base + smoother).coerceIn(0f, maxIdx.toFloat())
}

/** Органическая форма вылазки, плавно вытягивающаяся из рамки экрана с вогнутыми плечами. */
private fun DrawScope.drawBezelBulgePath(
    path: Path,
    isRight: Boolean,
    bodyW: Float,
    bodyH: Float,
    shoulder: Float,
    cornerR: Float,
) {
    val w = size.width
    val cy = size.height * 0.5f
    val halfH = bodyH * 0.5f
    val shoulderHalf = minOf(halfH + shoulder, cy)
    val topRootY = cy - shoulderHalf
    val bottomRootY = cy + shoulderHalf
    val topBodyY = cy - halfH
    val bottomBodyY = cy + halfH

    val xEdge = if (isRight) w else 0f
    val dir = if (isRight) -1f else 1f
    val xInner = xEdge + dir * bodyW
    val cR = cornerR.coerceAtMost(bodyW * 0.85f).coerceAtMost(halfH * 0.90f)
    val fW = minOf(shoulder * 0.85f, (bodyW - cR).coerceAtLeast(0f))
    val k = 0.55228f * cR

    path.rewind()
    path.moveTo(xEdge, topRootY)
    path.cubicTo(
        xEdge,
        topRootY + shoulder * 0.55f,
        xEdge + dir * fW * 0.35f,
        topBodyY,
        xEdge + dir * fW,
        topBodyY,
    )
    path.lineTo(xInner - dir * cR, topBodyY)
    path.cubicTo(
        xInner - dir * (cR - k),
        topBodyY,
        xInner,
        topBodyY + (cR - k),
        xInner,
        topBodyY + cR,
    )
    path.lineTo(xInner, bottomBodyY - cR)
    path.cubicTo(
        xInner,
        bottomBodyY - (cR - k),
        xInner - dir * (cR - k),
        bottomBodyY,
        xInner - dir * cR,
        bottomBodyY,
    )
    path.lineTo(xEdge + dir * fW, bottomBodyY)
    path.cubicTo(
        xEdge + dir * fW * 0.35f,
        bottomBodyY,
        xEdge,
        bottomRootY - shoulder * 0.55f,
        xEdge,
        bottomRootY,
    )
    path.close()
}

/**
 * Плотная возвышенная капсула с чёткой кромкой и реальной тенью для нижней плавающей панели.
 */
@Composable
private fun CapsuleSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val surfaceColor = if (Brand.dark) lerp(Brand.Glass, Color.White, 0.06f) else Brand.Glass
    val borderColor = if (Brand.dark) Color.White.copy(alpha = 0.11f) else Brand.Line.copy(alpha = 0.72f)
    Box(
        modifier = modifier
            .shadow(
                elevation = if (Brand.dark) 18.dp else 12.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = if (Brand.dark) 0.50f else 0.16f),
                spotColor = Color.Black.copy(alpha = if (Brand.dark) 0.60f else 0.22f),
            )
            .clip(CircleShape)
            .background(surfaceColor)
            .border(1.dp, borderColor, CircleShape),
        content = content,
    )
}

/** Пружинное вычисление границ перетекающей «ртутной» плашки активной вкладки. */
@Composable
private fun rememberMercuryPillBounds(
    selectedIndex: Int,
    slots: List<Pair<Float, Float>>,
): Pair<Float, Float> {
    var lastIdx by remember { mutableIntStateOf(selectedIndex) }
    var movingForward by remember { mutableStateOf(true) }
    if (selectedIndex != lastIdx) {
        movingForward = selectedIndex > lastIdx
        lastIdx = selectedIndex
    }
    val fastSpec = remember { spring<Float>(dampingRatio = 0.78f, stiffness = 580f) }
    val tailSpec = remember { spring<Float>(dampingRatio = 0.75f, stiffness = 360f) }
    val slot = slots.getOrNull(selectedIndex) ?: (0f to 0f)
    val targetStart = slot.first
    val targetEnd = slot.first + slot.second

    val animStart by animateFloatAsState(
        targetValue = targetStart,
        animationSpec = if (movingForward) tailSpec else fastSpec,
        label = "mercuryPillStart",
    )
    val animEnd by animateFloatAsState(
        targetValue = targetEnd,
        animationSpec = if (movingForward) fastSpec else tailSpec,
        label = "mercuryPillEnd",
    )
    return animStart to animEnd
}

/** Поиск индекса вкладки по координате пальца вдоль оси капсулы. */
private fun hitSlotIndex(pos: Float, slots: List<Pair<Float, Float>>): Int {
    if (slots.isEmpty()) return 0
    val exact = slots.indexOfFirst { (start, len) -> len > 0f && pos >= start && pos <= start + len }
    if (exact >= 0) return exact
    return slots.indices.minByOrNull { i ->
        val (s, l) = slots[i]
        abs(pos - (s + l * 0.5f))
    } ?: 0
}

/**
 * Компактная плотная капсула снизу для телефона:
 * живая «ртутная» акцентная плашка, скраббинг проведением пальца и раскрытие подписи активной вкладки.
 */
@Composable
fun PhoneBottomCapsule(
    items: List<CapsuleTabItem>,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val slots = remember(items.size) { mutableStateListOf<Pair<Float, Float>>().apply { repeat(items.size) { add(0f to 0f) } } }
    val selectedIndex = items.indexOfFirst { it.isSelected }.coerceAtLeast(0)
    val (pillStart, pillEnd) = rememberMercuryPillBounds(selectedIndex, slots)
    val pillWidthPx = (pillEnd - pillStart).coerceAtLeast(0f)

    fun selectIndex(idx: Int) {
        if (idx in items.indices && idx != selectedIndex) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            items[idx].onClick()
        }
    }

    CapsuleSurface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .height(IntrinsicSize.Min),
    ) {
        Box(
            Modifier
                .padding(6.dp)
                .height(40.dp)
                .pointerInput(items.size) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val startX = down.position.x
                        var scrubbing = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (!scrubbing && abs(change.position.x - startX) > touchSlop) {
                                scrubbing = true
                            }
                            if (scrubbing) {
                                change.consume()
                                selectIndex(hitSlotIndex(change.position.x, slots))
                            }
                        }
                    }
                },
        ) {
            if (pillWidthPx > 1f) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(with(density) { pillWidthPx.toDp() })
                        .offset { IntOffset(pillStart.roundToInt(), 0) }
                        .clip(CircleShape)
                        .background(Brand.Accent),
                )
            }
            Row(
                modifier = Modifier.animateContentSize(tween(240, easing = Motion.Emphasized)),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { idx, item ->
                    val src = remember(item.title) { MutableInteractionSource() }
                    val tint by animateColorAsState(
                        targetValue = if (item.isSelected) Brand.OnAccent else Brand.InkSoft,
                        animationSpec = tween(220),
                        label = "bottomCapsuleTint",
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (item.isSelected) 1.08f else 1f,
                        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
                        label = "bottomCapsuleIconScale",
                    )

                    val sizeMod = if (item.isSelected) {
                        Modifier.height(40.dp)
                    } else {
                        Modifier.size(width = 44.dp, height = 40.dp)
                    }

                    Row(
                        modifier = sizeMod
                            .onGloballyPositioned { coords ->
                                val pos = coords.positionInParent().x
                                val len = coords.size.width.toFloat()
                                if (idx < slots.size && slots[idx] != (pos to len)) {
                                    slots[idx] = pos to len
                                }
                            }
                            .clip(CircleShape)
                            .clickable(interactionSource = src, indication = ripple(bounded = true)) {
                                selectIndex(idx)
                            }
                            .then(if (item.isSelected) Modifier.padding(horizontal = 16.dp) else Modifier)
                            .semantics {
                                contentDescription = item.title
                                selected = item.isSelected
                            },
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(item.icon),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                        )
                        if (item.isSelected) {
                            Text(
                                item.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tint,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Вкладка «Учёба» на планшете: карточки, прогресс, экзамены, чистовики и конспекты. */
@Composable
private fun TabletStudyTab(resumeTick: Int, go: (String) -> Unit) {
    val context = LocalContext.current
    val nowMs = remember(resumeTick) { System.currentTimeMillis() }
    val due by remember(nowMs) { context.app.db.cards().observeDueCount(nowMs) }.collectAsStateWithLifecycle(initialValue = 0)
    val total by remember { context.app.db.cards().observeCount() }.collectAsStateWithLifecycle(initialValue = 0)

    ScreenScaffold(title = "Учёба", onBack = null) {
        SectionCard("Карточки для повторения") {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AnimatedCount(due)
                    Text("на сегодня из $total", style = MaterialTheme.typography.bodyMedium, color = Brand.InkSoft)
                }
                InkButton(if (due > 0) "Повторить" else "Открыть", icon = R.drawable.ic_cards) {
                    go(MainActivity.SCREEN_REVIEW)
                }
            }
        }
        SectionLabel("Разделы")
        SectionsGrid(go = go, columns = 2, items = TABLET_SECTIONS)
    }
}
