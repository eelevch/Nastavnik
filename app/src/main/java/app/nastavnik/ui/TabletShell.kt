package app.nastavnik.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
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
import kotlin.math.abs
import kotlin.math.roundToInt

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
    verticalBias: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val currentItems by rememberUpdatedState(items)
    val selectedIndex = items.indexOfFirst { it.isSelected }.coerceAtLeast(0)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)

    var isPressed by remember { mutableStateOf(false) }
    var analogSlot by remember { mutableFloatStateOf(selectedIndex.toFloat()) }

    val expandProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = if (isPressed) {
            spring(dampingRatio = 0.72f, stiffness = 460f)
        } else {
            spring(dampingRatio = 0.86f, stiffness = 520f)
        },
        label = "bezelExpand",
    )
    val smoothSlot by animateFloatAsState(
        targetValue = if (isPressed) analogSlot else selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 680f),
        label = "bezelSlot",
    )

    val bulgePath = remember { Path() }
    val clampedBias = (verticalBias.coerceIn(0.15f, 0.88f) * 2f - 1f)
    val isDark = Brand.dark
    val idleLipTint = Brand.Ink.copy(alpha = if (isDark) 0.38f else 0.24f)
    val accentColor = Brand.Accent
    val onAccentColor = Brand.OnAccent

    Box(modifier.fillMaxSize()) {
        if (expandProgress > 0.01f) {
            val scrimAlpha = (if (isDark) 0.28f else 0.10f) * expandProgress.coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha)),
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            Box(
                Modifier
                    .align(BiasAlignment(horizontalBias = if (isRight) 1f else -1f, verticalBias = clampedBias))
                    .size(width = 196.dp, height = 284.dp),
            ) {
                // Органическая вылазка от рамки экрана + iPhone dial-риски.
                Canvas(Modifier.fillMaxSize()) {
                    val p = expandProgress.coerceAtLeast(0f)
                    val bodyW = lerp(5.dp.toPx(), 180.dp.toPx(), p)
                    val bodyH = lerp(74.dp.toPx(), 216.dp.toPx(), p)
                    val shoulder = lerp(10.dp.toPx(), 32.dp.toPx(), p)
                    val cornerR = lerp(4.dp.toPx(), 26.dp.toPx(), p)
                        .coerceAtMost(bodyW * 0.85f)
                        .coerceAtMost(bodyH * 0.45f)

                    drawBezelBulgePath(
                        path = bulgePath,
                        isRight = isRight,
                        bodyW = bodyW,
                        bodyH = bodyH,
                        shoulder = shoulder,
                        cornerR = cornerR,
                    )

                    val fillBlend = (p / 0.35f).coerceIn(0f, 1f)
                    val fillColor = lerp(idleLipTint, BezelObsidian, fillBlend)
                    drawPath(path = bulgePath, color = fillColor)

                    if (p > 0.05f) {
                        val rimAlpha = (if (isDark) 0.18f else 0.12f) * p.coerceAtMost(1f)
                        drawPath(
                            path = bulgePath,
                            color = Color.White.copy(alpha = rimAlpha),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }

                    // Деликатный акцентный штрих в состоянии покоя.
                    if (p < 0.4f) {
                        val restAlpha = ((1f - p * 2.5f).coerceIn(0f, 1f)) * 0.85f
                        val barW = 2.5.dp.toPx()
                        val barH = 20.dp.toPx()
                        val barX = if (isRight) size.width - barW - 1.dp.toPx() else 1.dp.toPx()
                        drawRoundRect(
                            color = accentColor.copy(alpha = restAlpha),
                            topLeft = Offset(barX, size.height * 0.5f - barH * 0.5f),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW * 0.5f, barW * 0.5f),
                        )
                    }

                    // Тонкие тактильные риски вдоль края, как на диске управления iPhone.
                    if (p > 0.25f && items.size > 1) {
                        val dialAlpha = ((p - 0.25f) / 0.55f).coerceIn(0f, 1f)
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
                val contentAlpha = ((expandProgress - 0.15f) / 0.55f).coerceIn(0f, 1f)
                if (contentAlpha > 0.01f) {
                    val shiftPx = with(density) {
                        (1f - expandProgress.coerceAtMost(1f)) * 36.dp.toPx() * (if (isRight) 1f else -1f)
                    }
                    val stepPx = with(density) { 50.dp.toPx() }
                    val pillOffsetY = (smoothSlot * stepPx).roundToInt()

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
                                alpha = contentAlpha
                                translationX = shiftPx
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
                                val active = idx == selectedIndex
                                val tint by animateColorAsState(
                                    targetValue = if (active) onAccentColor else Color.White.copy(alpha = 0.72f),
                                    animationSpec = tween(160),
                                    label = "bezelItemTint",
                                )
                                val iconScale by animateFloatAsState(
                                    targetValue = if (active) 1.08f else 1f,
                                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 540f),
                                    label = "bezelIconScale",
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .clip(CircleShape)
                                        .padding(horizontal = 14.dp)
                                        .semantics {
                                            contentDescription = item.title
                                            selected = active
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
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                        color = tint,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                // Невидимая сенсорная зона у края: зажал и ведёшь вверх/вниз — отпустил и всё скрылось.
                Box(
                    Modifier
                        .align(if (isRight) Alignment.CenterEnd else Alignment.CenterStart)
                        .size(width = 28.dp, height = 152.dp)
                        .systemGestureExclusion()
                        .semantics { contentDescription = "Панель вкладок" }
                        .pointerInput(items.size) {
                            val stepPx = 36.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val startY = down.position.y
                                val startIdx = currentSelectedIndex.toFloat()
                                isPressed = true
                                analogSlot = startIdx
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        change.consume()
                                        val dy = change.position.y - startY
                                        val maxIdx = (currentItems.size - 1).coerceAtLeast(0).toFloat()
                                        analogSlot = (startIdx + dy / stepPx).coerceIn(0f, maxIdx)
                                        val targetIdx = analogSlot.roundToInt().coerceIn(0, currentItems.lastIndex)
                                        if (targetIdx != currentSelectedIndex) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            currentItems[targetIdx].onClick()
                                        }
                                    }
                                } finally {
                                    isPressed = false
                                }
                            }
                        },
                )
            }
        }
    }
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
