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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Планшетная оболочка с плотной плавающей капсульной панелью вкладок (слева или справа по выбору).
 */
@Composable
fun TabletShell(
    tab: TabletTab,
    onTab: (TabletTab) -> Unit,
    tabBarPosition: TabBarPosition,
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
        CompositionLocalProvider(LocalBottomBarInset provides 16.dp) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (side == TabBarPosition.LEFT) 98.dp else 0.dp,
                        end = if (side == TabBarPosition.RIGHT) 98.dp else 0.dp,
                    ),
            ) {
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

        CapsuleSideRail(
            items = railItems,
            modifier = Modifier
                .align(if (side == TabBarPosition.RIGHT) Alignment.CenterEnd else Alignment.CenterStart)
                .systemBarsPadding()
                .padding(horizontal = 12.dp),
        )
    }
}

/**
 * Плотная возвышенная капсула с чёткой кромкой и реальной тенью (без «полустекла»).
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
 * Вертикальная капсульная панель вкладок для планшета с тактильным скраббингом и «ртутной» плашкой.
 */
@Composable
fun CapsuleSideRail(
    items: List<CapsuleTabItem>,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val slots = remember(items.size) { mutableStateListOf<Pair<Float, Float>>().apply { repeat(items.size) { add(0f to 0f) } } }
    val selectedIndex = items.indexOfFirst { it.isSelected }.coerceAtLeast(0)
    val (pillStart, pillEnd) = rememberMercuryPillBounds(selectedIndex, slots)
    val pillHeightPx = (pillEnd - pillStart).coerceAtLeast(0f)

    fun selectIndex(idx: Int) {
        if (idx in items.indices && idx != selectedIndex) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            items[idx].onClick()
        }
    }

    CapsuleSurface(
        modifier = modifier
            .width(80.dp)
            .height(IntrinsicSize.Min),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp)
                .pointerInput(items.size) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val startY = down.position.y
                        var scrubbing = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (!scrubbing && abs(change.position.y - startY) > touchSlop) {
                                scrubbing = true
                            }
                            if (scrubbing) {
                                change.consume()
                                selectIndex(hitSlotIndex(change.position.y, slots))
                            }
                        }
                    }
                },
        ) {
            if (pillHeightPx > 1f) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(with(density) { pillHeightPx.toDp() })
                        .offset { IntOffset(0, pillStart.roundToInt()) }
                        .clip(CircleShape)
                        .background(Brand.Accent),
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items.forEachIndexed { idx, item ->
                    CapsuleNavButton(
                        item = item,
                        onClick = { selectIndex(idx) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                val pos = coords.positionInParent().y
                                val len = coords.size.height.toFloat()
                                if (idx < slots.size && slots[idx] != (pos to len)) {
                                    slots[idx] = pos to len
                                }
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun CapsuleNavButton(
    item: CapsuleTabItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val src = remember(item.title) { MutableInteractionSource() }
    val tint by animateColorAsState(
        targetValue = if (item.isSelected) Brand.OnAccent else Brand.InkSoft,
        animationSpec = tween(220),
        label = "capsuleTint",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (item.isSelected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "capsuleIconScale",
    )

    Column(
        modifier = modifier
            .clip(CircleShape)
            .clickable(interactionSource = src, indication = ripple(bounded = true), onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp)
            .semantics {
                contentDescription = item.title
                selected = item.isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            painterResource(item.icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
        )
        Text(
            item.title,
            fontSize = 10.sp,
            fontWeight = if (item.isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Тактильная боковая капсула для телефона:
 * - плотная возвышенная поверхность без сворачивания;
 * - мгновенное проведение пальцем переключает вкладки;
 * - удержание + перетаскивание перемещает капсулу по высоте вдоль края экрана.
 */
@Composable
fun PhoneSideCapsule(
    items: List<CapsuleTabItem>,
    sideBias: Float,
    containerHeightPx: Float,
    verticalPaddingPx: Float,
    onBiasCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val slots = remember(items.size) { mutableStateListOf<Pair<Float, Float>>().apply { repeat(items.size) { add(0f to 0f) } } }
    val selectedIndex = items.indexOfFirst { it.isSelected }.coerceAtLeast(0)
    val (pillStart, pillEnd) = rememberMercuryPillBounds(selectedIndex, slots)
    val pillHeightPx = (pillEnd - pillStart).coerceAtLeast(0f)

    var dragging by remember { mutableStateOf(false) }
    var localBias by remember(sideBias) { mutableFloatStateOf(sideBias.coerceIn(0.15f, 0.90f)) }
    var capsuleTopInContainerPx by remember { mutableFloatStateOf(0f) }
    var capsuleHeightPx by remember { mutableFloatStateOf(0f) }

    val capsuleScale by animateFloatAsState(
        targetValue = if (dragging) 1.07f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 480f),
        label = "sideCapsuleDragScale",
    )

    fun selectIndex(idx: Int) {
        if (idx in items.indices && idx != selectedIndex) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            items[idx].onClick()
        }
    }

    CapsuleSurface(
        modifier = modifier
            .width(54.dp)
            .height(IntrinsicSize.Min)
            .graphicsLayer {
                scaleX = capsuleScale
                scaleY = capsuleScale
            }
            .onGloballyPositioned { coords ->
                capsuleTopInContainerPx = coords.positionInParent().y
                capsuleHeightPx = coords.size.height.toFloat()
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .pointerInput(items.size, containerHeightPx, verticalPaddingPx) {
                    val touchSlop = viewConfiguration.touchSlop
                    val longPressMs = viewConfiguration.longPressTimeoutMillis
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val startY = down.position.y
                        var mode = 0 // 0 = undecided, 1 = scrub, 2 = drag position
                        var grabOffsetY = down.position.y

                        // Ждём движения > touchSlop (скраб) или удержания longPressMs (перемещение капсулы).
                        val timedOut = withTimeoutOrNull(longPressMs) {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                                if (!change.pressed) return@withTimeoutOrNull false
                                if (abs(change.position.y - startY) > touchSlop) {
                                    mode = 1
                                    change.consume()
                                    selectIndex(hitSlotIndex(change.position.y, slots))
                                    return@withTimeoutOrNull false
                                }
                            }
                        }
                        if (timedOut == null) {
                            mode = 2
                            dragging = true
                            grabOffsetY = startY + with(density) { 6.dp.toPx() }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        if (mode == 1) {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                selectIndex(hitSlotIndex(change.position.y, slots))
                            }
                        } else if (mode == 2) {
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    val travel = (containerHeightPx - verticalPaddingPx - capsuleHeightPx).coerceAtLeast(1f)
                                    val innerPadTop = with(density) { 6.dp.toPx() }
                                    val fingerInContainerY = capsuleTopInContainerPx + innerPadTop + change.position.y
                                    val topPad = verticalPaddingPx * 0.5f
                                    localBias = ((fingerInContainerY - topPad - grabOffsetY) / travel).coerceIn(0.15f, 0.90f)
                                    onBiasCommit(localBias)
                                }
                            } finally {
                                dragging = false
                            }
                        }
                    }
                },
        ) {
            if (pillHeightPx > 1f) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(with(density) { pillHeightPx.toDp() })
                        .offset { IntOffset(0, pillStart.roundToInt()) }
                        .clip(CircleShape)
                        .background(Brand.Accent),
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items.forEachIndexed { idx, item ->
                    val src = remember(item.title) { MutableInteractionSource() }
                    val tint by animateColorAsState(
                        targetValue = if (item.isSelected) Brand.OnAccent else Brand.InkSoft,
                        animationSpec = tween(220),
                        label = "sideIconTint",
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (item.isSelected) 1.08f else 1f,
                        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
                        label = "sideIconScale",
                    )
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = src,
                                indication = ripple(bounded = false, radius = 21.dp),
                            ) { selectIndex(idx) }
                            .onGloballyPositioned { coords ->
                                val pos = coords.positionInParent().y
                                val len = coords.size.height.toFloat()
                                if (idx < slots.size && slots[idx] != (pos to len)) {
                                    slots[idx] = pos to len
                                }
                            }
                            .semantics {
                                contentDescription = item.title
                                selected = item.isSelected
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(item.icon),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier
                                .size(21.dp)
                                .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                        )
                    }
                }
            }
        }
    }
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
