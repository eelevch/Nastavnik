package app.nastavnik.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.graphics.nativeCanvas
import app.nastavnik.ink.AndroidMathMetrics
import app.nastavnik.ink.InkPage
import app.nastavnik.ink.MentorNotebookWriter
import app.nastavnik.ink.Stroke
import app.nastavnik.ink.TextBox
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.unit.sp
import app.nastavnik.ai.MentorActionKind
import app.nastavnik.ai.MentorPageAction
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import app.nastavnik.theme.MentorVisionStyle
import kotlin.math.sin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nastavnik.BuildConfig
import app.nastavnik.R
import app.nastavnik.ai.Mark
import app.nastavnik.ai.QuickAction
import app.nastavnik.app
import app.nastavnik.ink.PageDrawer
import app.nastavnik.ink.Paper
import app.nastavnik.ink.Tool
import app.nastavnik.overlay.NoteColors
import app.nastavnik.overlay.SpeechInput
import app.nastavnik.session.AnswerUi
import app.nastavnik.session.AskKind
import app.nastavnik.study.ruPlural
import app.nastavnik.study.TrainResult
import app.nastavnik.study.PracticeTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Тетрадь: пишешь стилусом прямо в приложении, а наставник рядом — в панели справа. Его заметки не закрывают
 * записи: на листе только подсветка и номер, а текст — в панели под тем же номером.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotebookScreen(
    notebookId: Long,
    onBack: () -> Unit,
    onCleanReady: () -> Unit,
    onSettings: () -> Unit,
    onOpenNotebook: (Long) -> Unit = {},
    debugDemoTick: Int = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ctl = remember(notebookId) { NotebookController(context.app, notebookId, scope) }
    var tips by remember { mutableStateOf(false) }
    LaunchedEffect(notebookId, debugDemoTick) {
        if (!ctl.loaded) {
            ctl.load()
            // Первый раз — коротко, как устроен лист.
            if (!context.app.settings.editorTipsShown()) tips = true
        } else if (BuildConfig.DEBUG) {
            NotebookVisionDemoStart.take(notebookId)?.let { mode ->
                ctl.applyDebugVisionDemoWhenReady(mode)
            }
        }
    }
    DisposableEffect(ctl) { onDispose { ctl.close() } }
    val close = {
        ctl.close()
        onBack()
    }
    BackHandler(onBack = close)

    val state by ctl.mentor.state.collectAsState()
    val effectiveAnswer = state.answer ?: ctl.debugVisionAnswer
    val effectiveSilentOkAt = if (ctl.debugSilentOkAt > 0L) ctl.debugSilentOkAt else state.silentOkAt
    LaunchedEffect(state.answer, ctl.index) { ctl.refreshMarks(state.answer) }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val wide = screenWidthDp >= 840
    // Окно чата больше не открывается само: с наставником можно общаться прямо на листе.
    var panel by rememberSaveable(notebookId) { mutableStateOf(false) }
    var quickProblem by remember { mutableStateOf<String?>(null) }
    val settingsRepo = context.app.settings
    val appSettings by remember { settingsRepo.flow() }.collectAsStateWithLifecycle(initialValue = settingsRepo.load())
    val dockOn = appSettings.showAmbientMentor

    val speaker = context.app.speaker
    val isSpeaking by speaker.isSpeaking.collectAsState()
    var listening by remember { mutableStateOf(false) }
    var voiceRms by remember { mutableFloatStateOf(0f) }
    var partialVoiceText by remember { mutableStateOf("") }
    var speechProblem by remember { mutableStateOf<String?>(null) }

    fun askMentor(text: String, kind: AskKind) {
        val reason = context.app.settings.notReadyReason()
        if (reason != null) {
            quickProblem = "$reason. Открой настройки → «Нейросеть»."
            return
        }
        quickProblem = null
        speechProblem = null
        ctl.ask(text, kind)
    }

    val speech = remember(ctl) {
        SpeechInput(context, object : SpeechInput.Callback {
            override fun onListening(active: Boolean) {
                listening = active
                if (!active) {
                    voiceRms = 0f
                    partialVoiceText = ""
                }
            }
            override fun onPartial(text: String) {
                partialVoiceText = text
            }
            override fun onResult(text: String) {
                listening = false
                voiceRms = 0f
                val query = text.trim()
                partialVoiceText = ""
                if (query.isNotBlank()) {
                    askMentor(query, AskKind.USER)
                }
            }
            override fun onError(message: String, needsPermission: Boolean) {
                listening = false
                voiceRms = 0f
                partialVoiceText = ""
                speechProblem = message
            }
            override fun onRmsChanged(rmsdB: Float) {
                voiceRms = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }
        })
    }
    DisposableEffect(speech) {
        onDispose {
            speech.destroy()
            speaker.stop()
        }
    }

    LaunchedEffect(ctl.debugForcePanel) {
        ctl.debugForcePanel?.let { panel = it }
    }
    var panelWidthDp by rememberSaveable { mutableFloatStateOf(390f) }
    val minPanelWidthDp = 340f
    val maxPanelWidthDp = (screenWidthDp - 380f).coerceAtMost(screenWidthDp * 0.7f).coerceAtLeast(minPanelWidthDp)
    val effectivePanelWidth = panelWidthDp.coerceIn(minPanelWidthDp, maxPanelWidthDp)
    var pagesOpen by rememberSaveable { mutableStateOf(false) }
    val importers = rememberImporters(onImages = { ctl.addImages(it) }, onPdf = { ctl.addPdf(it) })
        // Лист всегда белый, как бумага; от темы зависит только стол вокруг него.
    val dark = Brand.dark
    val desk = Brand.Desk.toArgb()
    SideEffect { ctl.ink.setTheme(dark, desk) }

    if (tips) EditorTips { tips = false; context.app.settings.setEditorTipsShown() }
    if (pagesOpen) PagesOverview(ctl, onAdd = { ctl.addPage(ctl.page?.paper ?: Paper.GRID) }) { pagesOpen = false }
    TextDialog(ctl)

    val mentorGaze: Offset? = when {
        ctl.writing -> Offset(
            x = (-0.90f + (ctl.penNormX - 0.5f) * 0.35f).coerceIn(-1f, -0.2f),
            y = ((ctl.penNormY - 0.35f) * 1.15f).coerceIn(-0.85f, 0.85f),
        )
        effectiveAnswer?.streaming == true && ctl.detectedPageLines.isNotEmpty() -> {
            val l = ctl.detectedPageLines[(ctl.debugVisionFocusIdx ?: 0).coerceIn(0, ctl.detectedPageLines.lastIndex)]
            Offset(
                x = -0.82f,
                y = (((l.centerY() / 1414f) - 0.35f) * 1.15f).coerceIn(-0.85f, 0.85f),
            )
        }
        else -> null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(desk))
            .systemBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            NotebookTopBar(
                ctl = ctl,
                panel = panel,
                dockOn = dockOn,
                listening = listening,
                voiceRms = voiceRms,
                gazeTarget = mentorGaze,
                onBack = close,
                onOpenNotebook = { targetId ->
                    if (targetId != notebookId) {
                        ctl.close()
                        onOpenNotebook(targetId)
                    }
                },
                onToggleVoice = {
                    if (isSpeaking) speaker.stop()
                    speech.toggle()
                },
                onTogglePanel = { panel = !panel },
                onPages = { pagesOpen = true },
                importers = importers,
            )
            val panelSlideState = animateFloatAsState(
                targetValue = if (panel && wide) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.92f, stiffness = 380f),
                label = "panelSlide",
            )
            // Наставник не закрывает лист, а сдвигает его: лист вписывается в оставшуюся слева часть.
            val insetPx = with(LocalDensity.current) { if (panel && wide) (effectivePanelWidth.dp + 12.dp).toPx() else 0f }
            LaunchedEffect(insetPx) { ctl.ink.setRightInset(insetPx) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        // Обрезка обязательна: без неё лист (AndroidView) рисует стол поверх панели инструментов.
                        .clip(RoundedCornerShape(24.dp)),
                ) {
                    androidx.compose.runtime.key(ctl) {
                        AndroidView(
                            factory = { ctx ->
                                (ctl.ink.parent as? android.view.ViewGroup)?.removeView(ctl.ink)
                                ctl.attachContext(ctx)
                                ctl.ink
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    MentorVisionLayer(
                        ctl = ctl,
                        mentorAnswer = effectiveAnswer,
                        silentOkAt = effectiveSilentOkAt,
                        style = Brand.visionStyle,
                        modifier = Modifier.fillMaxSize(),
                    )
                    SelectionMenu(ctl)
                    EditorHint(ctl, Modifier.align(Alignment.TopCenter).padding(top = 14.dp))

                    val busyNow = state.busy || (ctl.debugVisionAnswer?.streaming == true)
                    if (!panel && dockOn && ctl.loaded) {
                        MentorDock(
                            ctl = ctl,
                            answer = effectiveAnswer,
                            busy = busyNow,
                            listening = listening,
                            voiceRms = voiceRms,
                            partialVoiceText = partialVoiceText,
                            problem = quickProblem ?: speechProblem,
                            isSpeaking = isSpeaking,
                            onToggleVoice = {
                                if (isSpeaking) speaker.stop()
                                speech.toggle()
                            },
                            onAsk = ::askMentor,
                            onSpeak = { ans ->
                                ans.marks.joinToString(". ") { it.text }
                                    .takeIf { it.isNotBlank() }
                                    ?.let(speaker::speak)
                            },
                            onOpenChat = { panel = true },
                            onOpenSettings = onSettings,
                            gazeTarget = mentorGaze,
                        )
                    }
                    // Лист увеличен — маленькая плашка с масштабом; нажатие возвращает по ширине.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = abs(ctl.zoom - 1f) > 0.04f,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .graphicsLayer {
                                if (wide) {
                                    translationX = -panelSlideState.value * (effectivePanelWidth.dp.toPx() + 16.dp.toPx()) * 0.5f
                                }
                            },
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f),
                    ) {
                        Text(
                            "${(ctl.zoom * 100).roundToInt()} %",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .glass(CircleShape, 6.dp, strong = true)
                                .clickable { ctl.fitWidth() }
                                .semantics { contentDescription = "По ширине листа" }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }

                    // Страница задачи ДЗ — плашка с проверкой.
                    HomeworkBar(
                        ctl,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 72.dp, end = 16.dp, bottom = 56.dp)
                            .graphicsLayer {
                                if (wide) {
                                    translationX = -panelSlideState.value * (effectivePanelWidth.dp.toPx() + 16.dp.toPx()) * 0.5f
                                }
                            },
                    )

                    // Плавающая боковая панель инструментов (когда выбрано «Панель кистей: сбоку»).
                    val railMaxHeight = maxHeight
                    Box(
                        Modifier
                            .fillMaxSize()
                            .zIndex(2f)
                            .graphicsLayer {
                                translationX = if (wide && ctl.railRight) {
                                    -panelSlideState.value * (effectivePanelWidth.dp.toPx() + 16.dp.toPx())
                                } else {
                                    0f
                                }
                            },
                    ) {
                        EditorRail(ctl, railMaxHeight)
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = !ctl.loaded,
                        modifier = Modifier.align(Alignment.Center),
                        exit = fadeOut(),
                    ) { CircularProgressIndicator(color = Brand.Accent) }
                }
                if (wide) {
                    val fullPanelWidth = effectivePanelWidth.dp + 16.dp
                    Row(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .requiredWidth(fullPanelWidth)
                            .graphicsLayer {
                                val s = panelSlideState.value
                                translationX = (1f - s) * (size.width + 24.dp.toPx())
                                alpha = (s * 1.4f).coerceIn(0f, 1f)
                                compositingStrategy = if (s in 0.001f..0.999f) {
                                    CompositingStrategy.Offscreen
                                } else {
                                    CompositingStrategy.Auto
                                }
                            }
                            .then(if (!panel) Modifier.clearAndSetSemantics {} else Modifier),
                    ) {
                        PanelSplitter(
                            onDragDeltaDp = { deltaDp ->
                                panelWidthDp = (panelWidthDp - deltaDp).coerceIn(minPanelWidthDp, maxPanelWidthDp)
                            },
                        )
                        MentorPanel(
                            ctl = ctl,
                            listening = listening,
                            voiceRms = voiceRms,
                            partialVoiceText = partialVoiceText,
                            speechProblem = speechProblem,
                            onToggleVoice = {
                                if (isSpeaking) speaker.stop()
                                speech.toggle()
                            },
                            onAskExternal = ::askMentor,
                            onClose = { panel = false },
                            onCleanReady = onCleanReady,
                            onSettings = onSettings,
                            modifier = Modifier
                                .width(effectivePanelWidth.dp)
                                .fillMaxHeight()
                                .padding(top = 0.dp, end = 12.dp, bottom = 12.dp)
                                .imePadding(),
                        )
                    }
                }
            }
        }
        if (!wide) {
            val panelBottomSlideState = animateFloatAsState(
                targetValue = if (panel) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.92f, stiffness = 380f),
                label = "panelBottomSlide",
            )
            val panelHeight = (LocalConfiguration.current.screenHeightDp * 0.65f).dp
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(panelHeight)
                    .padding(12.dp)
                    .imePadding()
                    .graphicsLayer {
                        val s = panelBottomSlideState.value
                        translationY = (1f - s) * (size.height + 24.dp.toPx())
                        alpha = (s * 1.4f).coerceIn(0f, 1f)
                        compositingStrategy = if (s in 0.001f..0.999f) {
                            CompositingStrategy.Offscreen
                        } else {
                            CompositingStrategy.Auto
                        }
                    }
                    .then(if (!panel) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                MentorPanel(
                    ctl = ctl,
                    listening = listening,
                    voiceRms = voiceRms,
                    partialVoiceText = partialVoiceText,
                    speechProblem = speechProblem,
                    onToggleVoice = {
                        if (isSpeaking) speaker.stop()
                        speech.toggle()
                    },
                    onAskExternal = ::askMentor,
                    onClose = { panel = false },
                    onCleanReady = onCleanReady,
                    onSettings = onSettings,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Интерактивный сплиттер (разделитель) между рабочей областью заметок и окном наставника на планшете.
 * Позволяет плавно регулировать ширину окна наставника пальцем или стилусом.
 */
@Composable
private fun PanelSplitter(
    onDragDeltaDp: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val lineWidth by animateDpAsState(if (isDragging) 3.dp else 1.5.dp, label = "splitterLine")
    val handleWidth by animateDpAsState(if (isDragging) 8.dp else 5.dp, label = "splitterHandleW")
    val handleColor by animateColorAsState(if (isDragging) Brand.Accent else Brand.InkSoft.copy(alpha = 0.5f), label = "splitterHandleColor")
    val lineColor by animateColorAsState(if (isDragging) Brand.Accent else Brand.Line.copy(alpha = 0.65f), label = "splitterLineColor")

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(20.dp)
            .pointerInput(density) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        onDragDeltaDp(dragAmount / density.density)
                    }
                )
            }
            .semantics { contentDescription = "Разделитель окна наставника" },
        contentAlignment = Alignment.Center,
    ) {
        // Тонкая линия-разделитель
        Box(
            Modifier
                .width(lineWidth)
                .fillMaxHeight()
                .padding(vertical = 12.dp)
                .clip(CircleShape)
                .background(lineColor)
        )
        // Ручка-ползунок для перетаскивания стилусом или пальцем
        Box(
            Modifier
                .size(handleWidth, 48.dp)
                .clip(CircleShape)
                .background(handleColor)
        )
    }
}

// ---------------- верхняя панель ----------------

@Composable
private fun NotebookTopBar(
    ctl: NotebookController,
    panel: Boolean,
    dockOn: Boolean,
    listening: Boolean,
    voiceRms: Float,
    gazeTarget: Offset?,
    onBack: () -> Unit,
    onOpenNotebook: (Long) -> Unit,
    onToggleVoice: () -> Unit,
    onTogglePanel: () -> Unit,
    onPages: () -> Unit,
    importers: Importers,
) {
    var renaming by remember { mutableStateOf(false) }
    var choosingSubject by remember { mutableStateOf(false) }
    var switchNotebookMenu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var paperDialog by remember { mutableStateOf(false) }
    var examDialog by remember { mutableStateOf(false) }
    var deletePageConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var existingSubjects by remember { mutableStateOf<List<String>>(emptyList()) }
    var recentNotebooks by remember { mutableStateOf<List<app.nastavnik.ink.NotebookMeta>>(emptyList()) }
    LaunchedEffect(ctl.notebookId) {
        withContext(Dispatchers.IO) {
            val all = context.app.notebooks.store.list()
            recentNotebooks = all.take(8)
            existingSubjects = all.map { it.subject.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        }
    }
    if (paperDialog) PaperDialog(ctl) { paperDialog = false }
    if (examDialog) ExamDialog(onStart = { m -> examDialog = false; ctl.startExam(m) }, onDismiss = { examDialog = false })
    val meta = ctl.meta
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BarButton(R.drawable.ic_arrow_back, "Назад", onClick = onBack)
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            if (recentNotebooks.size > 1) switchNotebookMenu = true else renaming = true
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        meta?.title ?: "Тетрадь",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = if (ctl.toolBarTop) 150.dp else 220.dp),
                    )
                    if (recentNotebooks.size > 1) {
                        Icon(
                            painterResource(R.drawable.ic_chevron),
                            contentDescription = "Сменить тетрадь",
                            tint = Brand.InkSoft,
                            modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = 90f },
                        )
                    }
                }
                DropdownMenu(
                    expanded = switchNotebookMenu,
                    onDismissRequest = { switchNotebookMenu = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    recentNotebooks.forEach { nb ->
                        val current = nb.id == ctl.notebookId
                        DropdownMenuItem(
                            text = {
                                Text(
                                    (if (current) "✓ " else "") + nb.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (current) Brand.Accent else Brand.Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            onClick = {
                                switchNotebookMenu = false
                                onOpenNotebook(nb.id)
                            },
                        )
                    }
                }
            }
            val subj = meta?.subject?.trim().orEmpty()
            if (subj.isNotEmpty() && !ctl.toolBarTop) {
                Text(
                    subj,
                    style = MaterialTheme.typography.labelSmall,
                    color = Brand.Accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brand.Accent.copy(alpha = 0.12f))
                        .clickable { choosingSubject = true }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (ctl.toolBarTop) {
                // Кисти — в одной строке со всеми кнопками: шторка сверху в одну строку и ниже.
                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TopToolBar(ctl, Modifier.horizontalScroll(rememberScrollState()))
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            // Страницы: стрелки, номер и кнопка быстрого удаления страницы
            Row(
                Modifier.glass(CircleShape, 4.dp, strong = true).padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIcon(R.drawable.ic_chevron_left, "Предыдущая страница", enabled = ctl.index > 0) { ctl.showPage(ctl.index - 1) }
                Text(
                    "${ctl.index + 1} / ${ctl.pages.size.coerceAtLeast(1)}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable(onClick = onPages).padding(horizontal = 4.dp),
                )
                BarIcon(R.drawable.ic_chevron, "Следующая страница", enabled = ctl.index < ctl.pages.lastIndex) { ctl.showPage(ctl.index + 1) }
                if (ctl.pages.size > 1) {
                    BarIcon(R.drawable.ic_trash, "Удалить страницу") { deletePageConfirm = true }
                }
            }
            // Режим фиксации листа: только 2 пальца + фиксированный зум
            BarButton(
                if (ctl.lockMode) R.drawable.ic_lock else R.drawable.ic_unlock,
                if (ctl.lockMode) "Фиксация включена (только 2 пальца, зум зафиксирован)" else "Включить фиксацию листа",
                active = ctl.lockMode,
                onClick = { ctl.toggleLockMode() },
            )
            BarButton(R.drawable.ic_undo, "Отменить", enabled = ctl.canUndo) { ctl.undo() }
            BarButton(R.drawable.ic_redo, "Вернуть", enabled = ctl.canRedo) { ctl.redo() }
            // Всё остальное — в одном меню: страницы, бумага, контрольная, проверка.
            Box {
                BarButton(R.drawable.ic_more, "Ещё") { moreMenu = true }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
                    MenuItem(if (ctl.toolBarTop) "Панель кистей: сбоку" else "Панель кистей: сверху", R.drawable.ic_panel) {
                        moreMenu = false
                        ctl.toggleToolBarPosition()
                    }
                    MenuItem(if (ctl.lockMode) "Выключить фиксацию" else "Фиксация (2 пальца + зум)", if (ctl.lockMode) R.drawable.ic_lock else R.drawable.ic_unlock) {
                        moreMenu = false
                        ctl.toggleLockMode()
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuItem("Новая страница", R.drawable.ic_plus) { moreMenu = false; ctl.addPage(ctl.page?.paper ?: Paper.GRID) }
                    MenuItem("Фото или PDF как страница", R.drawable.ic_camera) { moreMenu = false; addMenu = true }
                    MenuItem("Все страницы", R.drawable.ic_pages) { moreMenu = false; onPages() }
                    MenuItem("Бумага…", R.drawable.ic_notebook) { moreMenu = false; paperDialog = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuItem("Контрольная на время…", R.drawable.ic_timer) { moreMenu = false; examDialog = true }
                    MenuItem((if (ctl.autoCheck) "✓ " else "") + "Проверять, когда остановлюсь", R.drawable.ic_eye) { moreMenu = false; ctl.toggleAutoCheck() }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuItem(if (subj.isNotEmpty()) "Группа: $subj" else "Добавить в группу…", R.drawable.ic_panel) { moreMenu = false; choosingSubject = true }
                    MenuItem("Переименовать", R.drawable.ic_pen) { moreMenu = false; renaming = true }
                    if (ctl.pages.size > 1) {
                        MenuItem("Удалить текущую страницу", R.drawable.ic_trash) { moreMenu = false; deletePageConfirm = true }
                    }
                    MenuItem("Очистить страницу", R.drawable.ic_eraser) { moreMenu = false; ctl.clearPage() }
                }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
                    MenuItem("Сфотографировать", R.drawable.ic_camera) { addMenu = false; importers.camera() }
                    MenuItem("Фото из галереи", R.drawable.ic_image) { addMenu = false; importers.gallery() }
                    MenuItem("Страницы из PDF", R.drawable.ic_pdf) { addMenu = false; importers.pdf() }
                }
            }
            val mentorState by ctl.mentor.state.collectAsState()
            val effectiveBusy = mentorState.busy || (ctl.debugVisionAnswer?.streaming == true)
            val chatDesc = if (panel) "Скрыть чат" else "Открыть чат"
            if (dockOn) {
                BarButton(
                    icon = R.drawable.ic_panel,
                    description = chatDesc,
                    active = panel,
                    onClick = onTogglePanel,
                )
            } else {
                Box(
                    Modifier
                        .size(34.dp)
                        .then(
                            if (listening) Modifier.clip(CircleShape).background(Brand.Accent)
                            else Modifier.glass(CircleShape, 4.dp, strong = true)
                        )
                        .clickable(onClick = onToggleVoice)
                        .semantics { contentDescription = if (listening) "Остановить голос" else "Спросить голосом" },
                    contentAlignment = Alignment.Center,
                ) {
                    val micScale = if (listening) 1f + voiceRms * 0.22f else 1f
                    Icon(
                        painterResource(R.drawable.ic_mic),
                        contentDescription = null,
                        tint = if (listening) Brand.OnAccent else Brand.Ink,
                        modifier = Modifier.size(17.dp).graphicsLayer { scaleX = micScale; scaleY = micScale },
                    )
                }
                MascotBarButton(
                    writing = ctl.writing || ctl.mentorWriting,
                    thinking = effectiveBusy,
                    autoCheckProgress = ctl.autoCheckProgress,
                    gazeTarget = gazeTarget,
                    description = chatDesc,
                    active = panel,
                    onClick = onTogglePanel,
                )
            }
        }
    }
    if (deletePageConfirm) {
        AlertDialog(
            onDismissRequest = { deletePageConfirm = false },
            title = { Text("Удалить страницу ${ctl.index + 1}?") },
            text = { Text("Страница будет удалена из тетради.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                InkButton("Удалить", danger = true) {
                    ctl.deletePage()
                    deletePageConfirm = false
                }
            },
            dismissButton = { SoftButton("Отмена") { deletePageConfirm = false } },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
        )
    }
    if (renaming && meta != null) {
        var text by remember { mutableStateOf(meta.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Название тетради") },
            text = { AppTextField(text, { text = it }, "Например: Матанализ, пределы") },
            confirmButton = { InkButton("Сохранить") { ctl.rename(text); renaming = false } },
            dismissButton = { SoftButton("Отмена") { renaming = false } },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
        )
    }
    if (choosingSubject && meta != null) {
        var subjectText by remember { mutableStateOf(meta.subject) }
        AlertDialog(
            onDismissRequest = { choosingSubject = false },
            title = { Text("Группа конспекта") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Выберите существующую группу или введите новую:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (existingSubjects.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ChipButton("Без группы", selected = subjectText.isBlank()) {
                                subjectText = ""
                            }
                            existingSubjects.forEach { s ->
                                ChipButton(s, selected = subjectText.trim() == s) {
                                    subjectText = s
                                }
                            }
                        }
                    }
                    AppTextField(subjectText, { subjectText = it }, "Название группы", placeholder = "Например: Физика, Матанализ")
                }
            },
            confirmButton = {
                InkButton("Сохранить") {
                    ctl.setSubject(subjectText)
                    choosingSubject = false
                }
            },
            dismissButton = { SoftButton("Отмена") { choosingSubject = false } },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
        )
    }
}

@Composable
private fun MenuItem(text: String, icon: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, tint = Brand.Ink, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
    )
}

/** Круглая стеклянная кнопка вызова наставника с живым маскотом (следит за пером, показывает отсчёт и размышление). */
@Composable
private fun MascotBarButton(
    writing: Boolean,
    thinking: Boolean,
    autoCheckProgress: Float = 0f,
    gazeTarget: Offset? = null,
    description: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val stops = Brand.mascotColor.resolveStops(Brand.Accent.toArgb())
    Box(
        Modifier
            .size(34.dp)
            .then(
                if (active) Modifier.clip(CircleShape).background(Brand.Accent.copy(alpha = 0.22f))
                else Modifier.glass(CircleShape, 4.dp, strong = true)
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (autoCheckProgress > 0.02f && !thinking && !writing) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(1.5.dp)) {
                drawArc(
                    color = Color(stops.midArgb),
                    startAngle = -90f,
                    sweepAngle = autoCheckProgress * 360f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    ),
                )
            }
        }
        MascotIcon(writing = writing, thinking = thinking, sizeDp = 24.dp, gazeTarget = gazeTarget)
    }
}

/** Круглая стеклянная кнопка панели; [active] — включённое состояние (графитовая). */
@Composable
private fun BarButton(icon: Int, description: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    val tint = when {
        active -> Brand.OnInk
        enabled -> Brand.Ink
        else -> Brand.Ink.copy(alpha = 0.3f)
    }
    Box(
        Modifier
            .size(34.dp)
            .then(if (active) Modifier.clip(CircleShape).background(Brand.Accent) else Modifier.glass(CircleShape, 4.dp, strong = true))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun BarIcon(icon: Int, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (enabled) Brand.Ink else Brand.Ink.copy(alpha = 0.25f), modifier = Modifier.size(16.dp))
    }
}

/** Бумага: превью и выбор — для этой страницы или для всей тетради. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaperDialog(ctl: NotebookController, onDismiss: () -> Unit) {
    var all by remember { mutableStateOf(false) }
    val current = ctl.page?.paper
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Бумага") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Paper.entries.forEach { p ->
                        val on = p == current
                        Column(
                            Modifier
                                .width(92.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (all) ctl.setPaperAll(p) else ctl.setPaper(p)
                                    onDismiss()
                                }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f / 1.414f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(PageDrawer.PAPER))
                                    .border(if (on) 2.dp else 1.dp, if (on) Brand.Accent else Brand.Line, RoundedCornerShape(10.dp)),
                            ) { PaperPreview(p) }
                            Text(p.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { all = !all }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Для всей тетради", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    AppSwitch(all) { all = it }
                }
                if (ctl.page?.background != null) {
                    Text("На фото и страницах PDF разлиновка рисуется поверх картинки.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { SoftButton("Закрыть", onClick = onDismiss) },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun Divider() {
    Box(Modifier.width(1.dp).height(22.dp).background(Brand.Ink.copy(alpha = 0.12f)))
}

// ---------------- наставник ----------------

/** Быстрые вопросы наставнику: значок, название и что он сделает. */
internal class Ask(val action: QuickAction, val icon: Int, val title: String, val hint: String)

internal val ASKS = listOf(
    Ask(QuickAction.NEXT_STEP, R.drawable.ic_bulb, "Подсказка", "подскажет шаг"),
    Ask(QuickAction.WRITE_ON_PAGE, R.drawable.ic_pen, "На лист", "запишет на странице"),
    Ask(QuickAction.DRAW_ON_PAGE, R.drawable.ic_shapes, "Начертить", "график или схему"),
    Ask(QuickAction.FIND_ERROR, R.drawable.ic_search, "Где ошибка?", "проверит записи"),
    Ask(QuickAction.CHECK_ANSWER, R.drawable.ic_check, "Проверить ответ", "верно ли всё"),
    Ask(QuickAction.THEORY, R.drawable.ic_book, "Теория", "формулы и правила"),
    Ask(QuickAction.SHOW_STEP, R.drawable.ic_chevron, "Показать шаг", "ход решения"),
)

/**
 * Панель наставника в виде спокойного, чистого чата.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MentorPanel(
    ctl: NotebookController,
    listening: Boolean,
    voiceRms: Float,
    partialVoiceText: String,
    speechProblem: String?,
    onToggleVoice: () -> Unit,
    onAskExternal: (String, AskKind) -> Unit,
    onClose: () -> Unit,
    onCleanReady: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val speaker = context.app.speaker
    val isSpeaking by speaker.isSpeaking.collectAsState()

    var problem by remember { mutableStateOf<String?>(null) }
    var question by remember { mutableStateOf("") }
    var selectPages by remember { mutableStateOf(false) }
    var voiceOnly by rememberSaveable { mutableStateOf(false) }

    val mentorState by ctl.mentor.state.collectAsState()
    val effectiveAnswer = mentorState.answer ?: ctl.debugVisionAnswer
    val busy = mentorState.busy || (ctl.debugVisionAnswer?.streaming == true)
    var lastSpokenSeq by remember { mutableStateOf(0L) }
    val latestAnswer = ctl.chatMessages.lastOrNull { !it.isUser }?.answer ?: effectiveAnswer
    LaunchedEffect(voiceOnly, latestAnswer?.seq, latestAnswer?.streaming) {
        if (voiceOnly && latestAnswer != null && !latestAnswer.streaming && latestAnswer.seq != lastSpokenSeq && latestAnswer.marks.isNotEmpty()) {
            lastSpokenSeq = latestAnswer.seq
            speaker.speak(latestAnswer.marks.joinToString(". ") { it.text })
        }
    }

    fun ask(text: String, kind: AskKind) {
        val reason = context.app.settings.notReadyReason()
        if (reason != null) {
            problem = "$reason. Открой настройки → «Нейросеть»."
            return
        }
        problem = null
        onAskExternal(text, kind)
    }

    Box(modifier.glass(RoundedCornerShape(28.dp), 14.dp)) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides Brand.Ink,
        ) {
        Column(Modifier.fillMaxSize()) {
            // Верхняя шапка окна наставника: заголовок, голосовой режим, страницы, глазик, очистка и закрытие
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(if (voiceOnly) "Голосовой режим" else "Наставник", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!voiceOnly && ctl.chatMessages.isNotEmpty()) {
                        val count = ctl.chatMessages.count { it.isUser }
                        Text(
                            "$count ${if (count == 1) "вопрос" else if (count in 2..4) "вопроса" else "вопросов"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Brand.InkSoft,
                        )
                    }
                }
                // Кнопка переключения в онли-голосовой режим (маскот на всю панель + формулы и боксы)
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (voiceOnly) Color(0xFF2563EB) else Brand.Accent.copy(alpha = 0.12f))
                        .clickable {
                            voiceOnly = !voiceOnly
                            if (!voiceOnly) {
                                if (listening) onToggleVoice()
                                speaker.stop()
                            }
                        }
                        .semantics { contentDescription = if (voiceOnly) "Выйти из голосового режима" else "Голосовой режим" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (voiceOnly) R.drawable.ic_note else R.drawable.ic_mic),
                        contentDescription = null,
                        tint = if (voiceOnly) Color.White else Brand.Accent,
                        modifier = Modifier.size(16.dp),
                    )
                }
                // Контекст страниц: компактный интуитивный бейдж
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Brand.Accent.copy(alpha = 0.12f))
                        .clickable { selectPages = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    val count = ctl.selectedContextPages?.size ?: 1
                    Text(
                        if (count <= 1) "Стр. ${ctl.index + 1}" else "$count стр.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Brand.Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (ctl.ink.marks.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable { ctl.toggleMarks() }
                            .semantics { contentDescription = if (ctl.marksVisible) "Скрыть выделение" else "Показать выделение" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(if (ctl.marksVisible) R.drawable.ic_eye_off else R.drawable.ic_eye),
                            contentDescription = null,
                            tint = Brand.InkSoft,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                if (ctl.chatMessages.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable { ctl.clearChat() }
                            .semantics { contentDescription = "Очистить чат" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_trash), contentDescription = null, tint = Brand.InkSoft, modifier = Modifier.size(17.dp))
                    }
                }
                // Крестик закрытия панели наставника
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose)
                        .semantics { contentDescription = "Закрыть наставника" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = Brand.Ink, modifier = Modifier.size(18.dp))
                }
            }

            HorizontalDivider(color = Brand.Line.copy(alpha = 0.4f), thickness = 0.8.dp)

            if (ctl.examActive) {
                Box(Modifier.weight(1f).padding(14.dp)) {
                    ExamCard(ctl)
                }
                return@Column
            }

            (problem ?: speechProblem)?.let { p ->
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Column {
                        StatusLine(p, Tone.ERROR)
                        if (p.contains("настройки")) LinkText("Открыть настройки", onClick = onSettings)
                    }
                }
            }

            // Центральная зона: онли-голосовой режим ИЛИ текстовый чат
            if (voiceOnly) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Большой живой маскот по центру — реагирует на письмо, голос и размышления
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clickable { onToggleVoice() }
                            .semantics { contentDescription = if (listening) "Остановить запись" else "Спросить голосом" },
                        contentAlignment = Alignment.Center,
                    ) {
                        MascotIcon(
                            writing = ctl.writing,
                            thinking = busy,
                            speaking = isSpeaking,
                            listening = listening,
                            voiceLevel = voiceRms,
                            sizeDp = 148.dp,
                            gazeTarget = when {
                                ctl.writing -> Offset(-0.75f, ((ctl.penNormY - 0.35f) * 1.1f).coerceIn(-0.85f, 0.85f))
                                else -> null
                            },
                        )
                    }

                    val voiceStatus = when {
                        listening && partialVoiceText.isNotBlank() -> "«$partialVoiceText»"
                        listening -> "Слушаю тебя…"
                        busy -> latestAnswer?.status ?: "Думаю над задачей…"
                        isSpeaking -> "Объясняю голосом…"
                        else -> "Нажми на бота или микрофон, чтобы спросить"
                    }
                    Text(
                        voiceStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (listening || isSpeaking) Color(0xFF2563EB) else Brand.InkSoft,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VoiceWaveButton(
                            listening = listening,
                            voiceRms = voiceRms,
                            onClick = onToggleVoice,
                        )
                        if (isSpeaking) {
                            SoftButton("Стоп", icon = R.drawable.ic_close) { speaker.stop() }
                        }
                    }

                    // Ниже под маскотом — формулы, подсказки и переходы к выделенным боксам на листе
                    val marks = latestAnswer?.marks.orEmpty()
                    if (marks.isNotEmpty()) {
                        HorizontalDivider(color = Brand.Line.copy(alpha = 0.4f), thickness = 0.8.dp)
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            marks.forEachIndexed { i, m ->
                                CalmNoteCard(
                                    number = i + 1,
                                    m = m,
                                    topic = ctl.meta?.title.orEmpty(),
                                    streaming = latestAnswer?.streaming == true,
                                    isLatest = true,
                                    onLocate = { ctl.focusMark(i + 1) },
                                    onWriteToPage = { ctl.writeMarkToPage(m, latestAnswer?.geometry) },
                                    onNotHere = { ctl.onMarkNotHere(i + 1) },
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            } else {
            // Центральная зона: если чат пуст — экран первого запуска с плитками; если есть сообщения — лента чата
            if (ctl.chatMessages.isEmpty()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ctl.meta?.tasks?.takeIf { it.isNotEmpty() }?.let { VariantCard(it) }
                    Text(
                        "Быстрые действия:",
                        style = MaterialTheme.typography.labelMedium,
                        color = Brand.InkSoft,
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                    )
                    ASKS.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { a ->
                                AskTile(a, enabled = !busy, modifier = Modifier.weight(1f)) {
                                    ask(a.action.prompt, AskKind.QUICK)
                                }
                            }
                        }
                    }
                }
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(ctl.chatMessages.size, ctl.chatMessages.lastOrNull()?.answer?.marks?.size, ctl.chatMessages.lastOrNull()?.answer?.streaming) {
                    if (ctl.chatMessages.isNotEmpty()) {
                        listState.animateScrollToItem(ctl.chatMessages.lastIndex)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 10.dp),
                ) {
                    items(ctl.chatMessages.size, key = { idx -> ctl.chatMessages[idx].id }) { idx ->
                        val msg = ctl.chatMessages[idx]
                        val isLatest = idx >= ctl.chatMessages.lastIndex - 1
                        Box(Modifier.fillMaxWidth().animateItem()) {
                            if (msg.isUser) {
                                UserChatBubble(msg)
                            } else {
                                AssistantChatBubble(
                                    ctl = ctl,
                                    msg = msg,
                                    isLatestTurn = isLatest,
                                    onSettings = onSettings,
                                    onCleanReady = onCleanReady,
                                    onRetry = { ask(msg.answer?.question.orEmpty(), msg.kind) },
                                )
                            }
                        }
                    }
                }
            }

            // Нижняя панель действий: быстрое меню, живой значок микрофона и компактный ввод своего вопроса
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brand.Glass.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Если чат уже ведётся, прямо под ним ряд быстрых действий
                if (ctl.chatMessages.isNotEmpty()) {
                    QuickAskChips(enabled = !busy, onAsk = ::ask)
                }

                // Индикатор распознавания речи на лету (живая фраза)
                androidx.compose.animation.AnimatedVisibility(
                    visible = listening && partialVoiceText.isNotBlank(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brand.Accent.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "«$partialVoiceText»",
                            style = MaterialTheme.typography.bodySmall,
                            color = Brand.Accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Постоянная компактная строка (38dp): живой значок голосового ввода + инлайн-поле
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VoiceWaveButton(
                        listening = listening,
                        voiceRms = voiceRms,
                        onClick = onToggleVoice,
                    )

                    CompactInputField(
                        value = question,
                        onValueChange = { question = it },
                        onSend = {
                            if (question.isNotBlank()) {
                                ask(question.trim(), AskKind.USER)
                                question = ""
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            }
        }
        }
    }

    if (selectPages) {
        SelectPagesDialog(ctl) { selectPages = false }
    }
}

@Composable
private fun UserChatBubble(msg: NotebookController.MentorChatMessage) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        val label = when (msg.text) {
            QuickAction.NEXT_STEP.prompt -> "Подсказка"
            QuickAction.WRITE_ON_PAGE.prompt -> "Записать на лист"
            QuickAction.DRAW_ON_PAGE.prompt -> "Начертить"
            QuickAction.FIND_ERROR.prompt -> "Где ошибка?"
            QuickAction.CHECK_ANSWER.prompt -> "Проверить"
            QuickAction.THEORY.prompt -> "Теория"
            QuickAction.SHOW_STEP.prompt -> "Покажи шаг"
            QuickAction.SIMILAR.prompt -> "Похожая задача"
            else -> msg.text
        }
        val icon = when (msg.text) {
            QuickAction.NEXT_STEP.prompt -> R.drawable.ic_bulb
            QuickAction.WRITE_ON_PAGE.prompt -> R.drawable.ic_pen
            QuickAction.DRAW_ON_PAGE.prompt -> R.drawable.ic_shapes
            QuickAction.FIND_ERROR.prompt -> R.drawable.ic_search
            QuickAction.CHECK_ANSWER.prompt -> R.drawable.ic_check
            QuickAction.THEORY.prompt -> R.drawable.ic_book
            QuickAction.SHOW_STEP.prompt -> R.drawable.ic_chevron
            QuickAction.SIMILAR.prompt -> R.drawable.ic_refresh
            else -> null
        }
        Box(
            Modifier.fillMaxWidth(0.86f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row(
                Modifier
                    .wrapContentWidth()
                    .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                    .background(Brand.Ink.copy(alpha = if (Brand.dark) 0.14f else 0.06f))
                    .padding(horizontal = 13.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (icon != null) {
                    Icon(painterResource(icon), contentDescription = null, tint = Brand.InkSoft, modifier = Modifier.size(14.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium.copy(color = Brand.Ink, fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

@Composable
private fun AssistantChatBubble(
    ctl: NotebookController,
    msg: NotebookController.MentorChatMessage,
    isLatestTurn: Boolean,
    onSettings: () -> Unit,
    onCleanReady: () -> Unit,
    onRetry: () -> Unit,
) {
    val answer = msg.answer
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            answer == null -> {
                ThinkingRow(msg.id, "Думаю…", ctl.detectedPageLines.size)
            }
            answer.errorText != null -> {
                StatusLine(answer.errorText, Tone.ERROR)
                if (answer.error?.needsSettings == true) {
                    LinkText("Открыть настройки", onClick = onSettings)
                } else {
                    SoftButton("Повторить", icon = R.drawable.ic_refresh, onClick = onRetry)
                }
            }
            else -> {
                if (answer.streaming && answer.marks.isEmpty() && answer.actions.isEmpty()) {
                    ThinkingRow(answer.seq, answer.status ?: "Думаю…", ctl.detectedPageLines.size)
                }
                answer.marks.forEachIndexed { i, m ->
                    CalmNoteCard(
                        number = i + 1,
                        m = m,
                        topic = ctl.meta?.title.orEmpty(),
                        streaming = answer.streaming,
                        isLatest = isLatestTurn,
                        onLocate = { ctl.focusMark(i + 1) },
                        onWriteToPage = { ctl.writeMarkToPage(m, answer.geometry) },
                        onNotHere = { ctl.onMarkNotHere(i + 1) },
                    )
                }
                if (answer.actions.isNotEmpty() && !answer.streaming) {
                    PermissionCard(
                        actions = answer.actions,
                        state = ctl.actionStates[answer.seq] ?: "PENDING",
                        onApprove = { ctl.approveActions(answer) },
                        onDismiss = { ctl.dismissActions(answer) },
                    )
                }
                if (answer.streaming && (answer.marks.isNotEmpty() || answer.actions.isNotEmpty())) {
                    ThinkingRow(answer.seq, "Дописываю…", ctl.detectedPageLines.size)
                }
                if (answer.done && !answer.streaming && isLatestTurn) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InkButton("Сделать чистовик", icon = R.drawable.ic_note, modifier = Modifier.weight(1f)) {
                            scope.launch {
                                val images = ctl.pageImages()
                                val clean = context.app.clean
                                clean.cancel()
                                clean.addImages(images)
                                clean.build()
                                onCleanReady()
                            }
                        }
                        if (answer.marks.isNotEmpty()) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Brand.Ink.copy(alpha = 0.06f))
                                    .clickable {
                                        context.app.speaker.speak(answer.marks.joinToString(". ") { it.text })
                                    }
                                    .semantics { contentDescription = "Прочитать ответ вслух" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_volume),
                                    contentDescription = null,
                                    tint = Brand.InkSoft,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAskChips(
    enabled: Boolean,
    onAsk: (String, AskKind) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
    ) {
        item {
            QuickChip(
                title = "Подсказка",
                icon = R.drawable.ic_bulb,
                highlight = false,
                enabled = enabled,
                onClick = { onAsk(QuickAction.NEXT_STEP.prompt, AskKind.QUICK) },
            )
        }
        item {
            QuickChip(
                title = "На лист",
                icon = R.drawable.ic_pen,
                highlight = false,
                enabled = enabled,
                onClick = { onAsk(QuickAction.WRITE_ON_PAGE.prompt, AskKind.QUICK) },
            )
        }
        item {
            QuickChip(
                title = "Начертить",
                icon = R.drawable.ic_shapes,
                highlight = false,
                enabled = enabled,
                onClick = { onAsk(QuickAction.DRAW_ON_PAGE.prompt, AskKind.QUICK) },
            )
        }
        item {
            QuickChip(
                title = "Где ошибка?",
                icon = R.drawable.ic_search,
                highlight = false,
                enabled = enabled,
                onClick = { onAsk(QuickAction.FIND_ERROR.prompt, AskKind.QUICK) },
            )
        }
        item {
            QuickChip(
                title = "Проверить",
                icon = R.drawable.ic_check,
                highlight = false,
                enabled = enabled,
                onClick = { onAsk(QuickAction.CHECK_ANSWER.prompt, AskKind.QUICK) },
            )
        }
        item {
            QuickChip(
                title = "Теория",
                icon = R.drawable.ic_book,
                highlight = false,
                enabled = enabled,
                onClick = { onAsk(QuickAction.THEORY.prompt, AskKind.QUICK) },
            )
        }
        item {
            QuickChip(
                title = "Покажи шаг",
                icon = R.drawable.ic_chevron,
                highlight = false,
                enabled = enabled,
                onClick = { onAsk(QuickAction.SHOW_STEP.prompt, AskKind.QUICK) },
            )
        }
        item {
            QuickChip(
                title = "Похожая",
                icon = R.drawable.ic_refresh,
                highlight = false,
                enabled = enabled,
                onClick = { onAsk(QuickAction.SIMILAR.prompt, AskKind.QUICK) },
            )
        }
    }
}

@Composable
internal fun QuickChip(
    title: String,
    icon: Int,
    highlight: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.6f, stiffness = 800f), label = "chipScale")
    val bg = if (highlight) Brand.Accent.copy(alpha = 0.15f) else Brand.Glass.copy(alpha = 0.8f)
    val borderColor = if (highlight) Brand.Accent else Brand.Line
    val textColor = if (highlight) Brand.Accent else Brand.Ink

    Row(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = textColor, modifier = Modifier.size(15.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium.copy(
                color = textColor,
                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Medium,
            ),
        )
    }
}

/**
 * Компактный значок голосового ввода: реагирует на голос в реальном времени,
 * анимирует пульсирующий ореол и динамические звуковые волны в зависимости от громкости речи.
 */
@Composable
internal fun VoiceWaveButton(
    listening: Boolean,
    voiceRms: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (listening) 1f + voiceRms * 0.22f else 1f,
        animationSpec = spring(stiffness = 700f),
        label = "voiceScale",
    )
    val haloScale by animateFloatAsState(
        targetValue = if (listening) 1f + voiceRms * 0.5f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "voiceHaloScale",
    )
    val haloAlpha by animateFloatAsState(
        targetValue = if (listening) (0.25f + voiceRms * 0.45f).coerceIn(0f, 0.7f) else 0f,
        animationSpec = tween(140),
        label = "voiceHaloAlpha",
    )
    val bgColor by animateColorAsState(
        targetValue = if (listening) Brand.Accent else Brand.Glass.copy(alpha = 0.8f),
        animationSpec = tween(220),
        label = "voiceBg",
    )
    val iconTint by animateColorAsState(
        targetValue = if (listening) Brand.OnAccent else Brand.Ink,
        animationSpec = tween(220),
        label = "voiceTint",
    )

    Box(
        modifier = modifier
            .size(38.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        if (listening) {
            Box(
                Modifier
                    .size(38.dp)
                    .graphicsLayer {
                        scaleX = haloScale
                        scaleY = haloScale
                    }
                    .clip(CircleShape)
                    .background(Brand.Accent.copy(alpha = haloAlpha))
            )
        }

        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(bgColor)
                .border(0.8.dp, if (listening) Brand.Accent else Brand.Line, CircleShape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = if (listening) "Остановить запись" else "Спросить голосом" },
            contentAlignment = Alignment.Center,
        ) {
            if (listening) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val w1 by animateDpAsState((6 + voiceRms * 14).dp, label = "v1")
                    val w2 by animateDpAsState((10 + voiceRms * 18).dp, label = "v2")
                    val w3 by animateDpAsState((5 + voiceRms * 12).dp, label = "v3")
                    Box(Modifier.width(2.5.dp).height(w1).clip(CircleShape).background(Brand.OnAccent))
                    Box(Modifier.width(2.5.dp).height(w2).clip(CircleShape).background(Brand.OnAccent))
                    Box(Modifier.width(2.5.dp).height(w3).clip(CircleShape).background(Brand.OnAccent))
                }
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_mic),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

/**
 * Компактная фиксированная строка ввода своего вопроса:
 * Всегда ровно 38dp, аккуратная капсула, без трансформаций и раздувания.
 * Инлайн-поле ввода, при наборе текста справа появляется кнопка отправки.
 */
@Composable
internal fun CompactInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(Brand.Glass.copy(alpha = 0.7f))
            .border(0.8.dp, Brand.Line, RoundedCornerShape(19.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_keyboard),
            contentDescription = null,
            tint = Brand.InkSoft,
            modifier = Modifier.size(18.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Brand.Ink),
            cursorBrush = SolidColor(Brand.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            "Свой вопрос…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Brand.InkSoft,
                        )
                    }
                    innerTextField()
                }
            },
        )
        AnimatedVisibility(
            visible = value.isNotBlank(),
            enter = fadeIn(tween(120)) + scaleIn(spring(stiffness = 800f)),
            exit = fadeOut(tween(100)) + scaleOut(spring(stiffness = 800f)),
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Brand.Accent)
                    .clickable(enabled = enabled && value.isNotBlank(), onClick = onSend)
                    .semantics { contentDescription = "Отправить вопрос" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_send),
                    contentDescription = null,
                    tint = Brand.OnAccent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun SelectPagesDialog(ctl: NotebookController, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Страницы для наставника") },
        text = {
            Column {
                Text("Текущая страница отправляется всегда.")
                Row {
                    LinkText("Все", onClick = { ctl.selectAllContextPages() })
                    Spacer(Modifier.width(24.dp))
                    LinkText("Снять все", onClick = { ctl.selectNoContextPages() })
                }
                Column(Modifier.height(240.dp).verticalScroll(rememberScrollState())) {
                    ctl.pages.forEachIndexed { i, p ->
                        val current = i == ctl.index
                        val checked = current || ctl.selectedContextPages?.contains(p.id) != false
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !current) { ctl.selectContextPage(p.id, !checked) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, enabled = !current, onCheckedChange = { ctl.selectContextPage(p.id, it) })
                            Text("Страница ${i + 1}" + if (current) " · текущая" else "")
                        }
                    }
                }
            }
        },
        confirmButton = { LinkText("Готово", onClick = onDismiss) },
    )
}

/** Плитка вопроса: значок, название и коротко — что будет. Нажатие «проседает». */
@Composable
private fun AskTile(a: Ask, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 800f), label = "tile")
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(16.dp))
            .background(Brand.Glass.copy(alpha = 0.7f))
            .border(0.6.dp, Brand.Line, RoundedCornerShape(16.dp))
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .semantics { contentDescription = a.title },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Brand.Accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(a.icon), contentDescription = null, tint = Brand.Accent, modifier = Modifier.size(17.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(a.title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = Brand.Ink, maxLines = 1)
            Text(a.hint, style = MaterialTheme.typography.bodySmall, color = Brand.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Наставник читает/анализирует страницу: живая сфера, этап работы, распознанные строки и нейроволна. */
@Composable
private fun ThinkingRow(seq: Long, status: String?, lineCount: Int = 0) {
    val start = remember(seq) { System.currentTimeMillis() }
    val seconds by produceState(0, seq) {
        while (true) {
            value = ((System.currentTimeMillis() - start) / 1000).toInt()
            delay(250)
        }
    }
    val stops = Brand.mascotColor.resolveStops(Brand.Accent.toArgb())
    val accent = Color(stops.midArgb)
    val pulse = rememberInfiniteTransition(label = "thinking")
    val stageTitle = when {
        status?.contains("Смотрю") == true -> "Читаю рукопись на странице…"
        status?.contains("Дописываю") == true -> "Отмечаю подсказки на листе…"
        else -> "Разбираю шаги и формулы…"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp), 4.dp, strong = true)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MascotIcon(thinking = true, sizeDp = 30.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stageTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (lineCount > 0) {
                Text(
                    "Найдено строк: $lineCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.InkSoft,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(4) { i ->
                val a by pulse.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(460, delayMillis = i * 110, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "wave$i",
                )
                Box(
                    Modifier
                        .width(3.dp)
                        .height((6f + 12f * a).dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.55f + 0.45f * a)),
                )
            }
        }
        if (seconds > 0) Text("$seconds с", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}



/**
 * Спокойная карточка заметки наставника в чате: без пёстрой «радуги»,
 * старые сообщения автоматически сворачивают второстепенные кнопки и раскрываются по нажатию.
 */
@Composable
private fun CalmNoteCard(
    number: Int,
    m: Mark,
    topic: String,
    streaming: Boolean = false,
    isLatest: Boolean = true,
    onLocate: () -> Unit,
    onWriteToPage: (() -> Unit)? = null,
    onNotHere: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val speak: (String) -> Unit = context.app.speaker::speak
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable(number, m.text) { mutableStateOf(false) }
    var actionsExpanded by remember(number, isLatest) { mutableStateOf(isLatest) }
    var notHereReported by rememberSaveable(number) { mutableStateOf(false) }
    var carded by remember(m.text) { mutableStateOf<String?>(null) }
    var carding by remember(m.text) { mutableStateOf(false) }
    val dotColor = when (m.kind) {
        app.nastavnik.ai.MarkKind.ERROR -> Brand.Error
        app.nastavnik.ai.MarkKind.OK -> Brand.Ok
        else -> Brand.InkSoft
    }
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize(tween(200))
            .clip(RoundedCornerShape(18.dp))
            .background(Brand.Ink.copy(alpha = if (Brand.dark) 0.08f else 0.035f))
            .border(0.6.dp, Brand.Line.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
            .clickable(enabled = m.box != null || !isLatest) {
                if (m.box != null) onLocate() else actionsExpanded = !actionsExpanded
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
            Text(
                NoteColors.title(m.kind) + if (m.box != null) " · №$number" else "",
                style = MaterialTheme.typography.labelMedium,
                color = Brand.InkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (m.box != null) {
                Text(
                    "↗",
                    style = MaterialTheme.typography.labelMedium,
                    color = Brand.InkSoft,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onLocate)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (!isLatest) {
                Text(
                    if (actionsExpanded) "Скрыть" else "···",
                    style = MaterialTheme.typography.labelSmall,
                    color = Brand.InkSoft,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { actionsExpanded = !actionsExpanded }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (actionsExpanded) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable { speak(if (expanded && m.detail != null) "${m.text}. ${m.detail}" else m.text) }
                        .semantics { contentDescription = "Прочитать вслух" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_volume), contentDescription = null, tint = Brand.InkSoft, modifier = Modifier.size(15.dp))
                }
            }
        }
        RichText(m.text, sizePx = 15, streaming = streaming)
        if (m.detail != null && expanded) RichText(m.detail, sizePx = 15, streaming = streaming)
        if (actionsExpanded) {
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (m.detail != null) {
                    Text(
                        if (expanded) "Свернуть ↑" else "Подробнее",
                        style = MaterialTheme.typography.labelSmall,
                        color = Brand.InkSoft,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 2.dp),
                    )
                }
                if (onWriteToPage != null && m.text.isNotBlank()) {
                    Text(
                        "На лист ✎",
                        style = MaterialTheme.typography.labelSmall,
                        color = Brand.Ink,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.clickable(onClick = onWriteToPage).padding(vertical = 2.dp),
                    )
                }
                if (m.box != null && onNotHere != null) {
                    Text(
                        if (notHereReported) "✓ промах" else "Не здесь",
                        style = MaterialTheme.typography.labelSmall,
                        color = Brand.InkSoft,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.clickable(enabled = !notHereReported) {
                            notHereReported = true
                            onNotHere()
                        }.padding(vertical = 2.dp),
                    )
                }
                when {
                    carding -> Text("…", style = MaterialTheme.typography.labelSmall, color = Brand.InkSoft, maxLines = 1, softWrap = false)
                    carded != null -> Text(carded!!, style = MaterialTheme.typography.labelSmall, color = Brand.InkSoft, maxLines = 1, softWrap = false)
                    else -> Text(
                        "В карточки +",
                        style = MaterialTheme.typography.labelSmall,
                        color = Brand.InkSoft,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.clickable {
                            carding = true
                            scope.launch {
                                val hint = if (m.detail != null) "${m.text}\n\n${m.detail}" else m.text
                                carded = when (val r = context.app.trainer.cardFromHint(hint, topic)) {
                                    is TrainResult.Ok -> "✓ Карточка"
                                    is TrainResult.Failed -> r.message
                                }
                                carding = false
                            }
                        }.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** Запрос явного разрешения перед записью или изменением страниц со стороны наставника. */
@Composable
internal fun PermissionCard(
    actions: List<MentorPageAction>,
    state: String,
    onApprove: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brand.Ink.copy(alpha = if (Brand.dark) 0.12f else 0.05f))
            .border(0.9.dp, Brand.Line, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (state == "APPROVED") Brand.Ok else if (state == "DISMISSED") Brand.InkSoft else Brand.Accent),
            )
            Text(
                when (state) {
                    "APPROVED" -> "Выполнено на конспекте"
                    "DISMISSED" -> "Изменение отклонено"
                    else -> "Наставник просит разрешение"
                },
                style = MaterialTheme.typography.labelLarge,
                color = Brand.Ink,
            )
        }
        actions.forEach { act ->
            Text(
                "• ${act.summaryLabel()}",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.InkSoft,
            )
            if (act.kind == MentorActionKind.WRITE_TEXT || act.kind == MentorActionKind.DIAGRAM) {
                MentorActionPreview(act)
            }
        }
        if (state == "PENDING") {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InkButton("Разрешить", modifier = Modifier.weight(1f), onClick = onApprove)
                SoftButton("Отклонить", onClick = onDismiss)
            }
        }
    }
}

private data class PreviewBuilt(
    val strokes: List<Stroke>,
    val texts: List<TextBox>,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

@Composable
private fun MentorActionPreview(action: MentorPageAction) {
    val preview = remember(action.id, action.text) {
        val blank = InkPage(0L, Paper.BLANK)
        val ink = android.graphics.Color.rgb(24, 24, 28)
        val (strokes, texts) = when (action.kind) {
            MentorActionKind.WRITE_TEXT -> {
                val (s, t, _) = MentorNotebookWriter.buildWriteAddition(blank, action.text, null, ink, 22f, AndroidMathMetrics)
                s to t
            }
            MentorActionKind.DIAGRAM -> {
                val (s, t, _) = MentorNotebookWriter.buildDiagramAddition(blank, action.text, null, ink, AndroidMathMetrics)
                s to t
            }
            else -> emptyList<Stroke>() to emptyList<TextBox>()
        }
        var minL = Float.MAX_VALUE
        var minT = Float.MAX_VALUE
        var maxR = -Float.MAX_VALUE
        var maxB = -Float.MAX_VALUE
        for (s in strokes) {
            val b = s.bounds()
            minL = minOf(minL, b[0]); minT = minOf(minT, b[1]); maxR = maxOf(maxR, b[2]); maxB = maxOf(maxB, b[3])
        }
        for (t in texts) {
            val b = t.bounds()
            minL = minOf(minL, b[0]); minT = minOf(minT, b[1]); maxR = maxOf(maxR, b[2]); maxB = maxOf(maxB, b[3])
        }
        if (minL >= maxR || minT >= maxB) null
        else PreviewBuilt(strokes, texts, minL, minT, (maxR - minL).coerceAtLeast(30f), (maxB - minT).coerceAtLeast(24f))
    } ?: return

    val drawer = remember { PageDrawer().apply { dark = false } }
    val aspect = (preview.width / preview.height).coerceIn(1.1f, 4.2f)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp, max = 150.dp)
            .aspectRatio(aspect, matchHeightConstraintsFirst = false)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(PageDrawer.PAPER))
            .border(0.6.dp, Brand.Line, RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val scale = minOf(size.width / preview.width, size.height / preview.height).coerceAtLeast(0.05f)
            val dx = (size.width - preview.width * scale) * 0.5f - preview.left * scale
            val dy = (size.height - preview.height * scale) * 0.5f - preview.top * scale
            val nc = drawContext.canvas.nativeCanvas
            nc.save()
            nc.translate(dx, dy)
            nc.scale(scale, scale)
            for (s in preview.strokes) drawer.drawStroke(nc, s)
            for (t in preview.texts) drawer.drawText(nc, t)
            nc.restore()
        }
    }
}

/** Задачи тренировочного варианта: условия всегда под рукой, ответы знает только наставник. */
@Composable
private fun VariantCard(tasks: List<PracticeTask>) {
    var open by rememberSaveable { mutableStateOf(true) }
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp), 4.dp, strong = true)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Вариант · ${tasks.size} ${ruPlural(tasks.size, "задача", "задачи", "задач")}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(if (open) "свернуть" else "показать", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) {
            tasks.forEachIndexed { i, t ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i + 1}.", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 2.dp))
                    RichText(t.task, sizePx = 15)
                }
            }
        }
    }
}

/** Идёт контрольная: сколько осталось, «Сдать работу». Подсказок нет — как на настоящей контрольной. */
@Composable
private fun ExamCard(ctl: NotebookController) {
    val left by produceState(ctl.examEndsAt - System.currentTimeMillis(), ctl.examEndsAt) {
        while (true) {
            value = (ctl.examEndsAt - System.currentTimeMillis()).coerceAtLeast(0)
            delay(1000)
        }
    }
    val min = left / 60_000
    val sec = (left / 1000) % 60
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brand.Accent)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_timer), contentDescription = null, tint = Brand.OnInk, modifier = Modifier.size(22.dp))
            Text("Контрольная · осталось %d:%02d".format(min, sec), style = MaterialTheme.typography.titleMedium, color = Brand.OnInk)
        }
        Text(
            "Наставник молчит, пока не сдашь работу. Потом он проверит всё целиком и поставит оценку.",
            style = MaterialTheme.typography.bodySmall,
            color = Brand.OnInk.copy(alpha = 0.75f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Brand.OnInk)
                    .clickable { ctl.submitExam() }
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            ) { Text("Сдать работу", style = MaterialTheme.typography.labelLarge, color = Brand.Ink) }
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Brand.OnInk.copy(alpha = 0.14f))
                    .clickable { ctl.cancelExam() }
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            ) { Text("Прервать", style = MaterialTheme.typography.labelLarge, color = Brand.OnInk) }
        }
    }
}

@Composable
private fun ExamDialog(onStart: (Int) -> Unit, onDismiss: () -> Unit) {
    var minutes by remember { mutableStateOf(45) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Контрольная") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Реши задачи в тетради без подсказок. Когда время выйдет или ты нажмёшь «Сдать работу», наставник " +
                        "проверит всё целиком, отметит ошибки и поставит оценку от 2 до 5.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Segmented(listOf(20 to "20 мин", 45 to "45 мин", 60 to "60 мин", 90 to "90 мин"), minutes) { minutes = it }
            }
        },
        confirmButton = { InkButton("Начать", icon = R.drawable.ic_timer) { onStart(minutes) } },
        dismissButton = { SoftButton("Отмена", onClick = onDismiss) },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
    )
}

/** Переключатель в общем стиле: графитовый, когда включён. */
@Composable
fun AppSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedTrackColor = Brand.Accent,
            checkedThumbColor = Brand.OnInk,
            uncheckedTrackColor = Brand.Accent.copy(alpha = 0.12f),
            uncheckedThumbColor = Color.White,
            uncheckedBorderColor = Brand.Ink.copy(alpha = 0.2f),
        ),
    )
}
