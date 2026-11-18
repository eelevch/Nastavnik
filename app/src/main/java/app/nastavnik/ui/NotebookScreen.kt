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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.roundToInt
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun NotebookScreen(notebookId: Long, onBack: () -> Unit, onCleanReady: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ctl = remember(notebookId) { NotebookController(context.app, notebookId, scope) }
    var tips by remember { mutableStateOf(false) }
    LaunchedEffect(notebookId) {
        ctl.load()
        // Первый раз — коротко, как устроен лист.
        if (!context.app.settings.editorTipsShown()) tips = true
        // Прогреваем движок WebView (им рисуются формулы в подсказках): первый запуск долгий,
        // пусть он случится сейчас, а не в момент, когда пришёл ответ наставника.
        delay(600)
        runCatching { android.webkit.WebView(context).destroy() }
    }
    DisposableEffect(ctl) { onDispose { ctl.close() } }
    val close = {
        ctl.close()
        onBack()
    }
    BackHandler(onBack = close)

    val state by ctl.mentor.state.collectAsState()
    LaunchedEffect(state.answer, ctl.index) { ctl.refreshMarks(state.answer) }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val wide = screenWidthDp >= 840
    var panel by rememberSaveable { mutableStateOf(wide) }
    var panelWidthDp by rememberSaveable { mutableFloatStateOf(390f) }
    val minPanelWidthDp = 260f
    val maxPanelWidthDp = (screenWidthDp - 380f).coerceAtLeast(minPanelWidthDp).coerceAtMost(screenWidthDp * 0.7f)
    val effectivePanelWidth = panelWidthDp.coerceIn(minPanelWidthDp, maxPanelWidthDp)
    var pagesOpen by rememberSaveable { mutableStateOf(false) }
    val importers = rememberImporters(onImages = { ctl.addImages(it) }, onPdf = { ctl.addPdf(it) })
        // Лист всегда белый, как бумага; от темы зависит только стол вокруг него.
    val dark = Brand.dark
    val desk = Brand.Desk.toArgb()
    SideEffect { ctl.ink.setTheme(dark, desk) }
    // Ответ на явный вопрос открывает панель; фоновая проверка не закрывает лист.
    LaunchedEffect(state.answer?.seq) { if (state.answer != null && state.answer?.kind != app.nastavnik.session.AskKind.WATCH) panel = true }

    if (tips) EditorTips { tips = false; context.app.settings.setEditorTipsShown() }
    if (pagesOpen) PagesOverview(ctl, onAdd = { ctl.addPage(ctl.page?.paper ?: Paper.GRID) }) { pagesOpen = false }
    TextDialog(ctl)

    val isFullScreen = ctl.fullScreen
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(desk))
            .systemBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (!isFullScreen) {
                NotebookTopBar(ctl, panel, onBack = close, onTogglePanel = { panel = !panel }, onPages = { pagesOpen = true }, importers = importers)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(
                            start = if (isFullScreen) 0.dp else 12.dp,
                            end = if (isFullScreen || (panel && wide)) 0.dp else 12.dp,
                            bottom = if (isFullScreen) 0.dp else 12.dp,
                        )
                        .clip(if (isFullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(24.dp)),
                ) {
                    AndroidView(
                        factory = { ctx ->
                            ctl.attachContext(ctx)
                            ctl.ink
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    SelectionMenu(ctl)
                    EditorHint(ctl, Modifier.align(Alignment.TopCenter).padding(top = if (isFullScreen) 72.dp else 14.dp))
                    // Лист увеличен — маленькая плашка с масштабом; нажатие возвращает по ширине.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = abs(ctl.zoom - 1f) > 0.04f,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
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

                    // В полноэкранном режиме: плавающие не прибитые к краям стеклянные островки управления
                    if (isFullScreen) {
                        // Плавающий левый верхний островок навигации
                        Row(
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 16.dp, top = 16.dp)
                                .glass(CircleShape, 10.dp, strong = true)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            BarIcon(R.drawable.ic_shrink, "Свернуть полноэкранный режим") { ctl.toggleFullScreen() }
                            BarIcon(R.drawable.ic_chevron_left, "Предыдущая страница", enabled = ctl.index > 0) { ctl.showPage(ctl.index - 1) }
                            Text("${ctl.index + 1} / ${ctl.pages.size.coerceAtLeast(1)}", style = MaterialTheme.typography.labelMedium)
                            BarIcon(R.drawable.ic_chevron, "Следующая страница", enabled = ctl.index < ctl.pages.lastIndex) { ctl.showPage(ctl.index + 1) }
                            BarIcon(R.drawable.ic_plus, "Новая страница") { ctl.addPage(ctl.page?.paper ?: Paper.GRID) }
                        }

                        // Плавающий правый верхний островок: фиксация + КНОПКА НАСТАВНИКА
                        Row(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 16.dp, top = 16.dp)
                                .glass(CircleShape, 10.dp, strong = true)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            BarButton(
                                if (ctl.lockMode) R.drawable.ic_lock else R.drawable.ic_unlock,
                                if (ctl.lockMode) "Фиксация листа включена" else "Фиксация листа",
                                active = ctl.lockMode,
                                onClick = { ctl.toggleLockMode() },
                            )
                            BarButton(
                                R.drawable.ic_sparkle,
                                if (panel) "Скрыть наставника" else "Наставник",
                                active = panel,
                                onClick = { panel = !panel },
                            )
                        }
                    }

                    // Страница задачи ДЗ — плашка с проверкой.
                    if (!isFullScreen) {
                        HomeworkBar(ctl, Modifier.align(Alignment.BottomCenter).padding(start = 72.dp, end = 16.dp, bottom = 56.dp))
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = !ctl.loaded,
                        modifier = Modifier.align(Alignment.Center),
                        exit = fadeOut(),
                    ) { CircularProgressIndicator(color = Brand.Accent) }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = panel && wide,
                    enter = slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth } + fadeIn(tween(180)),
                    exit = slideOutHorizontally(animationSpec = tween(180, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth } + fadeOut(tween(140)),
                ) {
                    Row(
                        Modifier
                            .fillMaxHeight()
                            .requiredWidth(effectivePanelWidth.dp + 16.dp)
                    ) {
                        PanelSplitter(
                            onDragDeltaDp = { deltaDp ->
                                panelWidthDp = (panelWidthDp - deltaDp).coerceIn(minPanelWidthDp, maxPanelWidthDp)
                            },
                        )
                        MentorPanel(
                            ctl = ctl,
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
        androidx.compose.animation.AnimatedVisibility(
            visible = panel && !wide,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(240, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(240, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)),
        ) {
            MentorPanel(
                ctl = ctl,
                onClose = { panel = false },
                onCleanReady = onCleanReady,
                onSettings = onSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height((LocalConfiguration.current.screenHeightDp * 0.65f).dp)
                    .padding(12.dp)
                    .imePadding(),
            )
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
    onBack: () -> Unit,
    onTogglePanel: () -> Unit,
    onPages: () -> Unit,
    importers: Importers,
) {
    var renaming by remember { mutableStateOf(false) }
    var choosingSubject by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var paperDialog by remember { mutableStateOf(false) }
    var examDialog by remember { mutableStateOf(false) }
    var deletePageConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var existingSubjects by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            existingSubjects = context.app.notebooks.store.list().map { it.subject.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        }
    }
    if (paperDialog) PaperDialog(ctl) { paperDialog = false }
    if (examDialog) ExamDialog(onStart = { m -> examDialog = false; ctl.startExam(m) }, onDismiss = { examDialog = false })
    val meta = ctl.meta
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BarButton(R.drawable.ic_arrow_back, "Назад", onClick = onBack)
        Text(
            meta?.title ?: "Тетрадь",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 140.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { renaming = true }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
        val subj = meta?.subject?.trim().orEmpty()
        if (subj.isNotEmpty()) {
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
        // Кисти всегда сверху — расширенный центрированный ряд инструментов по центру
        Spacer(Modifier.weight(1f))
        TopToolBar(ctl)
        Spacer(Modifier.weight(1f))
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
        // Полноэкранный режим просмотра конспекта без лишних панелей
        BarButton(R.drawable.ic_expand, "На весь экран", onClick = { ctl.toggleFullScreen() })
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
                MenuItem("Полноэкранный режим", R.drawable.ic_expand) { moreMenu = false; ctl.toggleFullScreen() }
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
        val mentorAction = if (panel) "Скрыть наставника" else "Показать наставника"
        BarButton(R.drawable.ic_sparkle, mentorAction, active = panel, onClick = onTogglePanel)
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
private class Ask(val action: QuickAction, val icon: Int, val title: String, val hint: String)

private val ASKS = listOf(
    Ask(QuickAction.NEXT_STEP, R.drawable.ic_bulb, "Подсказка", "подскажет шаг"),
    Ask(QuickAction.FIND_ERROR, R.drawable.ic_search, "Где ошибка?", "проверит записи"),
    Ask(QuickAction.CHECK_ANSWER, R.drawable.ic_check, "Проверить ответ", "верно ли всё"),
    Ask(QuickAction.THEORY, R.drawable.ic_book, "Теория", "формулы и правила"),
    Ask(QuickAction.SHOW_STEP, R.drawable.ic_chevron, "Показать шаг", "ход решения"),
    Ask(QuickAction.SIMILAR, R.drawable.ic_refresh, "Похожая задача", "для тренировки"),
)

/**
 * Панель наставника в виде непрерывного чата.
 * Сохраняет всю историю вопросов и ответов сессии: при скрытии и повторном открытии чат не сбрасывается.
 * Вверху — кнопка с крестиком (закрыть окно) и очистка чата.
 * Внизу — всегда доступна кнопка «Подсказка» (и другие быстрые действия) и строка ввода для следующего вопроса.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MentorPanel(
    ctl: NotebookController,
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
    var listening by remember { mutableStateOf(false) }
    var voiceRms by remember { mutableFloatStateOf(0f) }
    var partialVoiceText by remember { mutableStateOf("") }
    var speechProblem by remember { mutableStateOf<String?>(null) }
    var selectPages by remember { mutableStateOf(false) }

    val busy = ctl.mentor.isBusy

    fun ask(text: String, kind: AskKind) {
        val reason = context.app.settings.notReadyReason()
        if (reason != null) {
            problem = "$reason. Открой настройки → «Нейросеть»."
            return
        }
        problem = null
        speechProblem = null
        ctl.ask(text, kind)
    }

    val speech = remember {
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
                    ask(query, AskKind.USER)
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
    DisposableEffect(Unit) {
        onDispose {
            speech.destroy()
            speaker.stop()
        }
    }

    Box(modifier.glass(RoundedCornerShape(28.dp), 14.dp)) {
        Column(Modifier.fillMaxSize()) {
            // Верхняя шапка окна наставника: заголовок, глазик, новый чат и крестик закрытия
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconBadge(R.drawable.ic_sparkle, 26)
                Column(Modifier.weight(1f)) {
                    Text("Наставник", style = MaterialTheme.typography.titleMedium)
                    if (ctl.chatMessages.isNotEmpty()) {
                        val count = ctl.chatMessages.count { it.isUser }
                        Text(
                            "$count ${if (count == 1) "вопрос" else if (count in 2..4) "вопроса" else "вопросов"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                    items(ctl.chatMessages, key = { it.id }) { msg ->
                        if (msg.isUser) {
                            UserChatBubble(msg)
                        } else {
                            AssistantChatBubble(
                                ctl = ctl,
                                msg = msg,
                                onSettings = onSettings,
                                onCleanReady = onCleanReady,
                                onRetry = { ask(msg.answer?.question.orEmpty(), msg.kind) },
                            )
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
                        onClick = {
                            if (isSpeaking) speaker.stop()
                            speech.toggle()
                        },
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
            QuickAction.FIND_ERROR.prompt -> "Где ошибка?"
            QuickAction.CHECK_ANSWER.prompt -> "Проверить"
            QuickAction.THEORY.prompt -> "Теория"
            QuickAction.SHOW_STEP.prompt -> "Покажи шаг"
            QuickAction.SIMILAR.prompt -> "Похожая задача"
            else -> msg.text
        }
        val icon = when (msg.text) {
            QuickAction.NEXT_STEP.prompt -> R.drawable.ic_bulb
            QuickAction.FIND_ERROR.prompt -> R.drawable.ic_search
            QuickAction.CHECK_ANSWER.prompt -> R.drawable.ic_check
            QuickAction.THEORY.prompt -> R.drawable.ic_book
            QuickAction.SHOW_STEP.prompt -> R.drawable.ic_chevron
            QuickAction.SIMILAR.prompt -> R.drawable.ic_refresh
            else -> null
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                .background(Brand.Accent)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
private fun AssistantChatBubble(
    ctl: NotebookController,
    msg: NotebookController.MentorChatMessage,
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
                ThinkingRow(msg.id, "Думаю…")
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
                if (answer.streaming && answer.marks.isEmpty()) {
                    ThinkingRow(answer.seq, answer.status ?: "Думаю…")
                }
                answer.marks.forEachIndexed { i, m ->
                    NoteCard(
                        number = i + 1,
                        m = m,
                        topic = ctl.meta?.title.orEmpty(),
                        onLocate = { ctl.focusMark(i + 1) },
                        onNotHere = { ctl.onMarkNotHere(i + 1) },
                    )
                }
                if (answer.streaming && answer.marks.isNotEmpty()) {
                    ThinkingRow(answer.seq, "Дописываю…")
                }
                if (answer.done && !answer.streaming) {
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
                                    .background(Brand.Glass.copy(alpha = 0.8f))
                                    .border(0.8.dp, Brand.Line, CircleShape)
                                    .clickable {
                                        context.app.speaker.speak(answer.marks.joinToString(". ") { it.text })
                                    }
                                    .semantics { contentDescription = "Прочитать ответ вслух" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_volume),
                                    contentDescription = null,
                                    tint = Brand.Ink,
                                    modifier = Modifier.size(19.dp),
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
                highlight = true,
                enabled = enabled,
                onClick = { onAsk(QuickAction.NEXT_STEP.prompt, AskKind.QUICK) },
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
private fun QuickChip(
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
private fun VoiceWaveButton(
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
private fun CompactInputField(
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
            Text(a.title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            Text(a.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Наставник думает: три «дышащие» точки и секунды — видно, что работа идёт. */
@Composable
private fun ThinkingRow(seq: Long, status: String?) {
    val start = remember(seq) { System.currentTimeMillis() }
    val seconds by produceState(0, seq) {
        while (true) {
            value = ((System.currentTimeMillis() - start) / 1000).toInt()
            delay(250)
        }
    }
    val pulse = rememberInfiniteTransition(label = "thinking")
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp), 4.dp, strong = true)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) { i ->
                val a by pulse.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(520, delayMillis = i * 160, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "dot$i",
                )
                Box(
                    Modifier
                        .size(8.dp)
                        .graphicsLayer { alpha = a; scaleX = 0.7f + 0.3f * a; scaleY = 0.7f + 0.3f * a }
                        .clip(CircleShape)
                        .background(Brand.Ink),
                )
            }
        }
        Text(status ?: "Думаю…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (seconds > 0) Text("$seconds с", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}



/** Заметка наставника в панели: номер как на листе, вид, короткая подсказка и «Подробнее». */
@Composable
private fun NoteCard(number: Int, m: Mark, topic: String, onLocate: () -> Unit, onNotHere: (() -> Unit)? = null) {
    val context = LocalContext.current
    val speak: (String) -> Unit = context.app.speaker::speak
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable(number, m.text) { mutableStateOf(false) }
    var notHereReported by rememberSaveable(number) { mutableStateOf(false) }
    // «В карточки»: null — не нажимали, иначе итог (добавлено или почему нет).
    var carded by remember(m.text) { mutableStateOf<String?>(null) }
    var carding by remember(m.text) { mutableStateOf(false) }
    val kindColor = Color(PageDrawer.kindColor(m.kind))
    // Заметка въезжает снизу и проявляется — видно, что пришло новое.
    val appear = remember(number) { Animatable(0f) }
    LaunchedEffect(number) { appear.animateTo(1f, tween(360, easing = FastOutSlowInEasing)) }
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                translationY = (1 - appear.value) * 18.dp.toPx()
            }
            .animateContentSize(tween(220))
            .glass(RoundedCornerShape(20.dp), 4.dp, strong = true)
            .clickable(enabled = m.box != null, onClick = onLocate)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(kindColor), contentAlignment = Alignment.Center) {
                Text(number.toString(), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            Text(NoteColors.title(m.kind), style = MaterialTheme.typography.labelLarge, color = kindColor)
            Spacer(Modifier.weight(1f))
            if (m.box != null) {
                if (notHereReported) {
                    Text("✓ промах", style = MaterialTheme.typography.labelSmall, color = Brand.Ok)
                } else if (onNotHere != null) {
                    Text(
                        "Не здесь",
                        style = MaterialTheme.typography.labelSmall,
                        color = Brand.Error,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brand.ErrorSoft)
                            .clickable {
                                notHereReported = true
                                onNotHere()
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    "к записям ↗",
                    style = MaterialTheme.typography.labelSmall,
                    color = Brand.Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brand.Accent.copy(alpha = 0.12f))
                        .clickable(onClick = onLocate)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { speak(if (expanded && m.detail != null) "${m.text}. ${m.detail}" else m.text) }
                    .semantics { contentDescription = "Прочитать вслух" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_volume), contentDescription = null, tint = Brand.InkSoft, modifier = Modifier.size(16.dp))
            }
        }
        RichText(m.text, sizePx = 15)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (m.detail != null) LinkText(if (expanded) "Свернуть ↑" else "Подробнее ↓") { expanded = !expanded }
            Spacer(Modifier.weight(1f))
            when {
                carding -> Text("Сохраняю…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                carded != null -> Text(carded!!, style = MaterialTheme.typography.labelMedium, color = if (carded!!.startsWith("✓")) Brand.Ok else Brand.Error)
                else -> LinkText("В карточки +") {
                    carding = true
                    scope.launch {
                        val hint = if (m.detail != null) "${m.text}\n\n${m.detail}" else m.text
                        carded = when (val r = context.app.trainer.cardFromHint(hint, topic)) {
                            is TrainResult.Ok -> "✓ В повторении"
                            is TrainResult.Failed -> r.message
                        }
                        carding = false
                    }
                }
            }
        }
        if (m.detail != null && expanded) RichText(m.detail, sizePx = 15)
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
