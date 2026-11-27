package app.nastavnik.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.nastavnik.R
import app.nastavnik.ai.QuickAction
import app.nastavnik.session.AnswerUi
import app.nastavnik.session.AskKind
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private enum class DockMode { IDLE, COMPOSE }

/**
 * Живой наставник-компаньон прямо в углу листа тетради:
 * - tap — быстрый вопрос / возврат скрытого ответа;
 * - long press — голосовой вопрос;
 * - double tap — открыть полный чат;
 * умеет уклоняться в противоположный нижний угол, если перо приближается к нему.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoxScope.MentorDock(
    ctl: NotebookController,
    answer: AnswerUi?,
    busy: Boolean,
    listening: Boolean,
    voiceRms: Float,
    partialVoiceText: String,
    problem: String?,
    isSpeaking: Boolean,
    onToggleVoice: () -> Unit,
    onAsk: (String, AskKind) -> Unit,
    onSpeak: (AnswerUi) -> Unit,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    gazeTarget: Offset?,
) {
    var mode by rememberSaveable(ctl.notebookId) { mutableStateOf(DockMode.IDLE) }
    var dismissedSeq by rememberSaveable(ctl.notebookId) { mutableLongStateOf(0L) }
    var unreadSeq by rememberSaveable(ctl.notebookId) { mutableLongStateOf(0L) }
    var bubbleTouchedAt by remember { mutableLongStateOf(0L) }
    var expandedText by remember(answer?.seq) { mutableStateOf(false) }
    var moreChips by remember(mode) { mutableStateOf(false) }
    var question by rememberSaveable(ctl.notebookId) { mutableStateOf("") }
    var dragDismissPx by remember(answer?.seq) { mutableFloatStateOf(0f) }

    val defaultEnd = !ctl.railRight
    val penNearBottom = ctl.writing && ctl.penNormY > 0.68f
    val dodge = penNearBottom && if (defaultEnd) ctl.penNormX > 0.60f else ctl.penNormX < 0.40f
    val alignEnd = if (dodge) !defaultEnd else defaultEnd
    val targetBias = if (alignEnd) 1f else -1f
    val animBias by animateFloatAsState(
        targetValue = targetBias,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 260f),
        label = "dockBias",
    )

    val pending = answer != null && answer.actions.isNotEmpty() && ctl.actionStates[answer.seq] == "PENDING"
    val hasContent = answer != null && (answer.marks.isNotEmpty() || answer.errorText != null || pending)
    val showReply = mode != DockMode.COMPOSE &&
        answer != null &&
        answer.seq != dismissedSeq &&
        hasContent &&
        !(ctl.writing && !pending)

    // Короткий «кивок» орба при поступлении нового ответа и сброс dismissedSeq.
    val nodY = remember { Animatable(0f) }
    var prevSeq by rememberSaveable(ctl.notebookId) { mutableLongStateOf(0L) }
    LaunchedEffect(answer?.seq, hasContent) {
        val seq = answer?.seq ?: 0L
        if (seq != 0L && seq != prevSeq && hasContent) {
            prevSeq = seq
            dismissedSeq = 0L
            unreadSeq = 0L
            dragDismissPx = 0f
            nodY.animateTo(-4f, spring(dampingRatio = 0.55f, stiffness = 700f))
            nodY.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 450f))
        }
    }

    // Автоскрытие обычного ответа через 14 с после завершения стриминга и последнего касания.
    LaunchedEffect(showReply, answer?.seq, answer?.streaming, pending, answer?.errorText, bubbleTouchedAt) {
        val current = answer ?: return@LaunchedEffect
        if (showReply && !current.streaming && !pending && current.errorText == null) {
            delay(14_000L)
            dismissedSeq = current.seq
            unreadSeq = current.seq
        }
    }

    BackHandler(enabled = mode == DockMode.COMPOSE) { mode = DockMode.IDLE }

    val origin = TransformOrigin(if (alignEnd) 1f else 0f, 1f)
    val enterAnim = fadeIn(tween(180)) +
        scaleIn(initialScale = 0.85f, transformOrigin = origin) +
        slideInVertically(tween(180)) { it / 6 }
    val exitAnim = fadeOut(tween(140)) +
        scaleOut(targetScale = 0.92f, transformOrigin = origin)

    val stops = Brand.mascotColor.resolveStops(Brand.Accent.toArgb())
    val glowColor = Color(stops.midArgb)
    val glowAlpha by animateFloatAsState(
        targetValue = if (busy || listening) 0.35f else 0.18f,
        animationSpec = tween(260),
        label = "orbGlow",
    )

    val pulse = rememberInfiniteTransition(label = "orbPulse")
    val breatheScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orbBreathe",
    )
    val badgeAlpha by pulse.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "badgePulse",
    )

    var orbPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (orbPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 600f),
        label = "orbPress",
    )
    val effectiveBreathe = if (mode == DockMode.IDLE && !busy) breatheScale else 1f
    val showBadge = !showReply && ((unreadSeq != 0L && answer?.seq == unreadSeq) || pending)
    val colAlign = if (alignEnd) Alignment.End else Alignment.Start
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .align(BiasAlignment(animBias, 1f))
            .padding(16.dp)
            .zIndex(3f),
        horizontalAlignment = colAlign,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.widthIn(max = 340.dp),
            horizontalAlignment = colAlign,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 1. Живая фраза голосового ввода
            AnimatedVisibility(visible = listening, enter = enterAnim, exit = exitAnim) {
                Row(
                    Modifier
                        .glass(CircleShape, 8.dp, strong = true)
                        .border(0.8.dp, Brand.Accent.copy(alpha = 0.45f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val b1 by animateDpAsState((6 + voiceRms * 12).dp, label = "dv1")
                        val b2 by animateDpAsState((9 + voiceRms * 15).dp, label = "dv2")
                        val b3 by animateDpAsState((5 + voiceRms * 10).dp, label = "dv3")
                        Box(Modifier.width(2.5.dp).height(b1).clip(CircleShape).background(Brand.Accent))
                        Box(Modifier.width(2.5.dp).height(b2).clip(CircleShape).background(Brand.Accent))
                        Box(Modifier.width(2.5.dp).height(b3).clip(CircleShape).background(Brand.Accent))
                    }
                    Text(
                        if (partialVoiceText.isNotBlank()) "«$partialVoiceText»" else "Слушаю…",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 2. Компактный статус размышления при busy
            AnimatedVisibility(visible = busy && mode != DockMode.COMPOSE && !showReply, enter = enterAnim, exit = exitAnim) {
                val statusText = answer?.status ?: "Смотрю на лист…"
                Box(
                    Modifier
                        .glass(CircleShape, 8.dp, strong = true)
                        .border(0.7.dp, Brand.Line, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    AnimatedContent(
                        targetState = statusText,
                        transitionSpec = {
                            (fadeIn(tween(160)) + slideInVertically { it / 3 })
                                .togetherWith(fadeOut(tween(120)) + slideOutVertically { -it / 3 })
                        },
                        label = "dockStatus",
                    ) { txt ->
                        Text(txt, style = MaterialTheme.typography.bodySmall, color = Brand.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // 3. Композер быстрого вопроса
            AnimatedVisibility(visible = mode == DockMode.COMPOSE && !busy, enter = enterAnim, exit = exitAnim) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(26.dp), 12.dp, strong = true)
                        .border(0.8.dp, Brand.Line, RoundedCornerShape(26.dp))
                        .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 400f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    problem?.let { p ->
                        StatusLine(p, Tone.ERROR)
                        if (p.contains("настройки")) LinkText("Открыть настройки", onClick = onOpenSettings)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VoiceWaveButton(listening = listening, voiceRms = voiceRms, onClick = onToggleVoice)
                        CompactInputField(
                            value = question,
                            onValueChange = { question = it },
                            onSend = {
                                if (question.isNotBlank()) {
                                    val q = question.trim()
                                    question = ""
                                    mode = DockMode.IDLE
                                    onAsk(q, AskKind.USER)
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Brand.Ink.copy(alpha = if (Brand.dark) 0.14f else 0.06f))
                                .clickable {
                                    mode = DockMode.IDLE
                                    onOpenChat()
                                }
                                .semantics { contentDescription = "Открыть чат" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.ic_panel), contentDescription = null, tint = Brand.InkSoft, modifier = Modifier.size(16.dp))
                        }
                    }

                    val primaryActions = remember {
                        val wanted = setOf(QuickAction.NEXT_STEP, QuickAction.FIND_ERROR, QuickAction.CHECK_ANSWER)
                        ASKS.filter { it.action in wanted }
                    }
                    val extraActions = remember {
                        val wanted = setOf(QuickAction.NEXT_STEP, QuickAction.FIND_ERROR, QuickAction.CHECK_ANSWER)
                        ASKS.filter { it.action !in wanted }
                    }
                    val shownChips = if (moreChips) primaryActions + extraActions else primaryActions

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        shownChips.forEachIndexed { idx, a ->
                            StaggeredDockChip(index = idx) {
                                QuickChip(
                                    title = a.title,
                                    icon = a.icon,
                                    highlight = false,
                                    enabled = !busy,
                                    onClick = {
                                        mode = DockMode.IDLE
                                        onAsk(a.action.prompt, AskKind.QUICK)
                                    },
                                )
                            }
                        }
                        if (!moreChips && extraActions.isNotEmpty()) {
                            StaggeredDockChip(index = primaryActions.size) {
                                QuickChip(
                                    title = "Ещё",
                                    icon = R.drawable.ic_chevron,
                                    highlight = false,
                                    enabled = !busy,
                                    onClick = { moreChips = true },
                                )
                            }
                        }
                    }
                }
            }

            // 4. Речевой пузырь ответа / разрешения с хвостиком к орбу
            AnimatedVisibility(visible = showReply, enter = enterAnim, exit = exitAnim) {
                val a = answer ?: return@AnimatedVisibility
                val shape = remember(alignEnd) { bubbleShape(tailEnd = alignEnd) }
                val dismissThresholdPx = with(density) { 60.dp.toPx() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, dragDismissPx.roundToInt()) }
                        .pointerInput(a.seq) {
                            detectDragGestures(
                                onDragEnd = {
                                    if (dragDismissPx > dismissThresholdPx) {
                                        dismissedSeq = a.seq
                                        unreadSeq = 0L
                                    }
                                    dragDismissPx = 0f
                                },
                                onDragCancel = { dragDismissPx = 0f },
                            ) { change, dragAmount ->
                                change.consume()
                                bubbleTouchedAt = System.currentTimeMillis()
                                val delta = dragAmount.y + if (alignEnd) dragAmount.x * 0.5f else -dragAmount.x * 0.5f
                                dragDismissPx = (dragDismissPx + delta).coerceIn(0f, dismissThresholdPx * 1.6f)
                            }
                        }
                        .glass(shape, 12.dp, strong = true)
                        .border(0.8.dp, Brand.Line, shape)
                        .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 400f))
                        .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 22.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        a.errorText?.let { err ->
                            StatusLine(err, Tone.ERROR)
                            if (a.error?.needsSettings == true) LinkText("Настройки", onClick = onOpenSettings)
                        }

                        val firstMark = a.marks.firstOrNull()
                        if (firstMark != null) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                var textOverflow by remember(firstMark.text) { mutableStateOf(false) }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val displayText = if (a.streaming) "${firstMark.text} ▍" else firstMark.text
                                    RichText(
                                        text = displayText,
                                        sizePx = 15,
                                        streaming = a.streaming,
                                        maxLines = if (expandedText) Int.MAX_VALUE else 4,
                                        overflow = TextOverflow.Ellipsis,
                                        onTextLayout = { res -> if (!expandedText) textOverflow = res.hasVisualOverflow },
                                    )
                                    if (textOverflow || expandedText) {
                                        Text(
                                            if (expandedText) "Свернуть ↑" else "Ещё ▾",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Brand.Accent,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier
                                                .clickable {
                                                    bubbleTouchedAt = System.currentTimeMillis()
                                                    expandedText = !expandedText
                                                }
                                                .padding(vertical = 2.dp),
                                        )
                                    }
                                    if (a.marks.size > 1) {
                                        Text(
                                            "+${a.marks.size - 1} в чате",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Brand.InkSoft,
                                            modifier = Modifier.clickable(onClick = onOpenChat).padding(vertical = 2.dp),
                                        )
                                    }
                                }
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            dismissedSeq = a.seq
                                            unreadSeq = 0L
                                        }
                                        .semantics { contentDescription = "Закрыть ответ" },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = Brand.InkSoft, modifier = Modifier.size(14.dp))
                                }
                            }
                        } else if (pending) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            dismissedSeq = a.seq
                                            unreadSeq = 0L
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = Brand.InkSoft, modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        if (pending) {
                            PermissionCard(
                                actions = a.actions,
                                state = "PENDING",
                                onApprove = { ctl.approveActions(a) },
                                onDismiss = { ctl.dismissActions(a) },
                            )
                        } else if (!a.streaming && firstMark != null) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (firstMark.box != null) {
                                    Text(
                                        "Показать",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Brand.InkSoft,
                                        modifier = Modifier.clickable {
                                            bubbleTouchedAt = System.currentTimeMillis()
                                            ctl.focusMark(1)
                                        }.padding(vertical = 2.dp),
                                    )
                                }
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            bubbleTouchedAt = System.currentTimeMillis()
                                            onSpeak(a)
                                        }
                                        .semantics { contentDescription = "Озвучить" },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(painterResource(R.drawable.ic_volume), contentDescription = null, tint = Brand.InkSoft, modifier = Modifier.size(15.dp))
                                }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "Чат",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Brand.Accent,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clickable(onClick = onOpenChat).padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Сам живой орб в углу листа
        Box(
            modifier = Modifier
                .size(62.dp)
                .offset { IntOffset(0, nodY.value.dp.roundToPx()) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        val s = effectiveBreathe * pressScale
                        scaleX = s
                        scaleY = s
                    }
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(glowColor.copy(alpha = glowAlpha), Color.Transparent),
                            ),
                            radius = size.minDimension * 0.85f,
                        )
                    }
                    .glass(CircleShape, 10.dp, strong = true)
                    .pointerInput(answer?.seq, listening, unreadSeq, dismissedSeq) {
                        detectTapGestures(
                            onPress = {
                                orbPressed = true
                                tryAwaitRelease()
                                orbPressed = false
                            },
                            onTap = {
                                if (listening) {
                                    onToggleVoice()
                                } else if (unreadSeq != 0L && answer != null && unreadSeq == answer.seq && dismissedSeq == answer.seq) {
                                    dismissedSeq = 0L
                                    unreadSeq = 0L
                                } else {
                                    mode = if (mode == DockMode.IDLE) DockMode.COMPOSE else DockMode.IDLE
                                }
                            },
                            onLongPress = { onToggleVoice() },
                            onDoubleTap = {
                                mode = DockMode.IDLE
                                onOpenChat()
                            },
                        )
                    }
                    .semantics {
                        contentDescription = "Наставник: нажми — вопрос, удерживай — голос, двойное нажатие — чат"
                    },
                contentAlignment = Alignment.Center,
            ) {
                val writingNow = ctl.writing
                if (ctl.autoCheckProgress > 0.02f && !busy && !writingNow) {
                    Canvas(Modifier.fillMaxSize().padding(2.dp)) {
                        drawArc(
                            color = glowColor,
                            startAngle = -90f,
                            sweepAngle = ctl.autoCheckProgress * 360f,
                            useCenter = false,
                            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }
                MascotIcon(
                    writing = writingNow,
                    thinking = busy,
                    speaking = isSpeaking,
                    listening = listening,
                    voiceLevel = voiceRms,
                    sizeDp = 46.dp,
                    gazeTarget = gazeTarget,
                )
            }

            if (showBadge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Brand.Accent.copy(alpha = badgeAlpha)),
                )
            }
        }
    }
}

@Composable
private fun StaggeredDockChip(index: Int, content: @Composable () -> Unit) {
    val alpha = remember(index) { Animatable(0f) }
    val ty = remember(index) { Animatable(8f) }
    LaunchedEffect(index) {
        delay(index * 30L)
        alpha.animateTo(1f, tween(150))
    }
    LaunchedEffect(index) {
        delay(index * 30L)
        ty.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
    }
    Box(
        Modifier.graphicsLayer {
            this.alpha = alpha.value
            translationY = ty.value
        },
    ) {
        content()
    }
}

private fun bubbleShape(tailEnd: Boolean): Shape = GenericShape { size, _ ->
    val w = size.width
    val tailH = (size.height * 0.07f).coerceIn(14f, 24f)
    val bodyH = (size.height - tailH).coerceAtLeast(32f)
    val r = minOf(38f, w * 0.14f, bodyH * 0.42f)
    val tipX = (if (tailEnd) w - 42f else 42f).coerceIn(r + 16f, (w - r - 16f).coerceAtLeast(r + 16f))
    val halfBase = 12f

    moveTo(r, 0f)
    lineTo(w - r, 0f)
    arcTo(Rect(w - 2 * r, 0f, w, 2 * r), -90f, 90f, false)
    lineTo(w, bodyH - r)
    arcTo(Rect(w - 2 * r, bodyH - 2 * r, w, bodyH), 0f, 90f, false)

    lineTo((tipX + halfBase).coerceAtMost(w - r), bodyH)
    lineTo(tipX, size.height)
    lineTo((tipX - halfBase).coerceAtLeast(r), bodyH)

    lineTo(r, bodyH)
    arcTo(Rect(0f, bodyH - 2 * r, 2 * r, bodyH), 90f, 90f, false)
    lineTo(0f, r)
    arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
    close()
}
