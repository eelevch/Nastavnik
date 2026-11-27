package app.nastavnik.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nastavnik.R
import app.nastavnik.app
import app.nastavnik.schedule.Lesson
import app.nastavnik.schedule.ScheduleNow
import app.nastavnik.study.ruPlural
import app.nastavnik.theme.TabBarPosition
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class PhoneTab(val title: String, val icon: Int) {
    TODAY("Сегодня", R.drawable.ic_home),
    SCHEDULE("Расписание", R.drawable.ic_calendar),
    GRADES("Оценки", R.drawable.ic_chart),
    STUDY("Учёба", R.drawable.ic_cards),
}

/**
 * Телефон: только то, что нужно в дороге и между парами — сегодня, расписание, карта кампуса и повторение.
 * Тетради для стилуса, наставник поверх записей и чистовики — на планшете.
 */
@Composable
fun PhoneShell(
    tab: PhoneTab,
    onTab: (PhoneTab) -> Unit,
    tabBarPosition: TabBarPosition = TabBarPosition.BOTTOM,
    sideCapsuleBias: Float = 0.78f,
    showSideCapsuleLabels: Boolean = true,
    resumeTick: Int,
    go: (String) -> Unit,
) {
    val context = LocalContext.current
    val isWeak = remember(context) { app.nastavnik.data.DeviceProfile.isWeakDevice(context) }
    val useRealBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isWeak && tabBarPosition == TabBarPosition.BOTTOM
    val contentLayer = rememberGraphicsLayer()
    var rootHeightPx by remember { mutableIntStateOf(0) }

    val bottomInset = when (tabBarPosition) {
        TabBarPosition.BOTTOM -> 84.dp
        TabBarPosition.BOTTOM_CAPSULE -> 88.dp
        TabBarPosition.LEFT, TabBarPosition.RIGHT, TabBarPosition.BOTH -> 24.dp
    }
    val capsuleItems = PhoneTab.entries.map { entry ->
        CapsuleTabItem(
            title = entry.title,
            icon = entry.icon,
            isSelected = entry == tab,
            onClick = { onTab(entry) },
        )
    }

    Box(Modifier.fillMaxSize().paperBackdrop()) {
        CompositionLocalProvider(LocalBottomBarInset provides bottomInset) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { rootHeightPx = it.height }
                    .then(
                        if (useRealBlur) {
                            Modifier.drawWithContent {
                                contentLayer.record {
                                    this@drawWithContent.drawContent()
                                }
                                drawLayer(contentLayer)
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        // Вкладки — плавный сдвиг и мягкое проявление без скачков и пауз.
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (fadeIn(tween(260, easing = Motion.Decelerate)) + slideInHorizontally(tween(300, easing = Motion.Emphasized)) { (it * 0.18f * dir).toInt() })
                            .togetherWith(fadeOut(tween(180, easing = Motion.Accelerate)) + slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { (-it * 0.18f * dir).toInt() })
                    },
                    label = "tab",
                    modifier = Modifier.fillMaxSize(),
                ) { t ->
                    when (t) {
                        PhoneTab.TODAY -> TodayTab(resumeTick, go, onTab = onTab)
                        PhoneTab.SCHEDULE -> ScheduleScreen(onBack = null)
                        PhoneTab.GRADES -> GradesScreen(onBack = null, onSettings = { go(MainActivity.SCREEN_SETTINGS) }, onLks = { go(MainActivity.SCREEN_LKS) })
                        PhoneTab.STUDY -> StudyTab(resumeTick, go)
                    }
                }
            }
        }
        when (tabBarPosition) {
            TabBarPosition.BOTTOM -> TabBar(
                tab = tab,
                onTab = onTab,
                modifier = Modifier.align(Alignment.BottomCenter),
                contentLayer = if (useRealBlur) contentLayer else null,
                rootHeightPx = rootHeightPx,
            )
            TabBarPosition.BOTTOM_CAPSULE -> PhoneBottomCapsule(
                items = capsuleItems,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            TabBarPosition.LEFT, TabBarPosition.RIGHT, TabBarPosition.BOTH -> EdgeBezelCapsule(
                items = capsuleItems,
                enableLeft = tabBarPosition.hasLeftEdge(),
                enableRight = tabBarPosition.hasRightEdge(),
                showLabels = showSideCapsuleLabels,
                verticalBias = sideCapsuleBias,
                modifier = Modifier.fillMaxSize(),
            )
        }

        GlobalMentorOverlay(
            bottomInset = bottomInset,
            onOpenSettings = { go(MainActivity.SCREEN_SETTINGS) },
        )
    }
}

/**
 * Нижняя панель без подложки сзади (как в Яндекс Музыке): значки и подписи парят поверх
 * глубокого прогрессивного размытия контента без сплошной карточки и пилюли.
 */
@Composable
private fun TabBar(
    tab: PhoneTab,
    onTab: (PhoneTab) -> Unit,
    modifier: Modifier,
    contentLayer: GraphicsLayer? = null,
    rootHeightPx: Int = 0,
) {
    TabRowBar(
        items = PhoneTab.entries.map { Triple(it.title, it.icon, it == tab) },
        onClick = { onTab(PhoneTab.entries[it]) },
        modifier = modifier,
        contentLayer = contentLayer,
        rootHeightPx = rootHeightPx,
    )
}

/** Панель вкладок без подложки с прогрессивным размытием; [onClick] — номер вкладки. */
@Composable
fun TabRowBar(
    items: List<Triple<String, Int, Boolean>>,
    onClick: (Int) -> Unit,
    modifier: Modifier,
    contentLayer: GraphicsLayer? = null,
    rootHeightPx: Int = 0,
) {
    val density = LocalDensity.current
    var barHeightPx by remember { mutableIntStateOf(0) }
    val fade = Brush.verticalGradient(
        0.00f to Color.Transparent,
        0.22f to Brand.Paper.copy(alpha = if (contentLayer != null) 0.22f else 0.34f),
        0.48f to Brand.Paper.copy(alpha = if (contentLayer != null) 0.56f else 0.74f),
        0.76f to Brand.Paper.copy(alpha = if (contentLayer != null) 0.84f else 0.94f),
        1.00f to Brand.Paper.copy(alpha = if (contentLayer != null) 0.95f else 0.99f),
    )
    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged { barHeightPx = it.height },
    ) {
        if (contentLayer != null && rootHeightPx > 0 && barHeightPx > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.36f to Color.Black,
                                1f to Color.Black,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clipToBounds()
                        .graphicsLayer {
                            val r = with(density) { 28.dp.toPx() }
                            renderEffect = BlurEffect(r, r, TileMode.Mirror)
                        }
                        .drawWithContent {
                            if (contentLayer.size.width > 0 && contentLayer.size.height > 0) {
                                translate(top = -(rootHeightPx - barHeightPx).toFloat()) {
                                    drawLayer(contentLayer)
                                }
                            }
                        },
                )
            }
        }
        Box(Modifier.matchParentSize().background(fade))
        Row(Modifier.fillMaxWidth().padding(top = 34.dp).navigationBarsPadding().height(64.dp)) {
            items.forEachIndexed { i, (title, icon, sel) ->
                val fg by animateColorAsState(
                    if (sel) Brand.Accent else Brand.InkSoft.copy(alpha = 0.68f),
                    tween(240, easing = Motion.Emphasized),
                    label = "tabColor",
                )
                val iconScale by animateFloatAsState(
                    if (sel) 1.08f else 1f,
                    spring(dampingRatio = 0.88f, stiffness = 500f),
                    label = "tabIconScale",
                )
                val source = remember { MutableInteractionSource() }
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .pressable(source)
                        .clickable(source, indication = null) { onClick(i) }
                        .semantics { contentDescription = title; selected = sel },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = fg,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** «Сегодня»: шапка с тем, что сейчас, и блоки из конструктора (кнопка «Изменить главную»). */
@Composable
private fun TodayTab(resumeTick: Int, go: (String) -> Unit, onTab: (PhoneTab) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val version by app.schedule.version.collectAsStateWithLifecycle()
    val now by produceState(LocalDateTime.now(), resumeTick) { while (true) { value = LocalDateTime.now(); delay(30_000) } }
    val lessons by produceState<List<Lesson>?>(null, version, resumeTick, now.toLocalDate()) { value = app.schedule.day(LocalDate.now()) }
    val sync by app.sync.status.collectAsStateWithLifecycle()
    val hasGroup = app.settings.scheduleGroup().isNotBlank()
    val nowMs = remember(resumeTick) { System.currentTimeMillis() }
    val dueCards by remember(nowMs) { app.db.cards().observeDueCount(nowMs) }.collectAsStateWithLifecycle(initialValue = 0)
    val journal by app.grades.journal.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { app.grades.refreshLks() }
    val avgGrade = journal?.averageMark
    var editing by remember { mutableStateOf(false) }

    ScreenScaffold(title = "", onBack = null) {
        HomeHeader(now, dayStatus(lessons, now, hasGroup)) {
            RoundIconButton(R.drawable.ic_pen, "Изменить главную") { editing = !editing }
            if (app.sync.configured) SyncDot(sync.running, sync.ok) { app.sync.soon(0) }
            RoundIconButton(R.drawable.ic_tune, "Настройки") { go(MainActivity.SCREEN_SETTINGS) }
        }
        InAppUpdateBanner()
        QuickActionsStrip(
            onSchedule = { onTab(PhoneTab.SCHEDULE) },
            onGrades = { onTab(PhoneTab.GRADES) },
            onReview = { go(MainActivity.SCREEN_REVIEW) },
            onNotebooks = { go(MainActivity.SCREEN_NOTEBOOKS) },
            dueCards = dueCards,
            avgGrade = avgGrade,
        )
        HomeBoard(
            tablet = false,
            editing = editing,
            onDoneEditing = { editing = false },
            resumeTick = resumeTick,
            go = go,
            onSchedule = { onTab(PhoneTab.SCHEDULE) },
        )
    }
}

/** Быстрые действия на телефоне: расписание, средний балл, карточки и конспекты. */
@Composable
private fun QuickActionsStrip(
    onSchedule: () -> Unit,
    onGrades: () -> Unit,
    onReview: () -> Unit,
    onNotebooks: () -> Unit,
    dueCards: Int,
    avgGrade: Double?,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickChip(icon = R.drawable.ic_calendar, title = "Пары", onClick = onSchedule)
        QuickChip(
            icon = R.drawable.ic_chart,
            title = if (avgGrade != null) "Балл %.2f".format(Locale.forLanguageTag("ru"), avgGrade) else "Оценки",
            badgeColor = avgGrade?.let { markColor("%.1f".format(Locale.US, it)) },
            onClick = onGrades,
        )
        QuickChip(
            icon = R.drawable.ic_cards,
            title = if (dueCards > 0) "Карточки ($dueCards)" else "Карточки",
            accent = dueCards > 0,
            onClick = onReview,
        )
        QuickChip(icon = R.drawable.ic_notebook, title = "Конспекты", onClick = onNotebooks)
    }
}

@Composable
private fun QuickChip(
    icon: Int,
    title: String,
    badgeColor: Color? = null,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressable(src)
            .clip(RoundedCornerShape(16.dp))
            .glass(RoundedCornerShape(16.dp), 6.dp)
            .clickable(src, indication = ripple(), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = badgeColor ?: if (accent) Brand.Accent else Brand.Ink,
            modifier = Modifier.size(16.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = badgeColor ?: if (accent) Brand.Accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** Число, которое «докручивается» до значения. */
@Composable
fun AnimatedCount(n: Int) {
    val shown by animateFloatAsState(n.toFloat(), tween(700), label = "count")
    Text(shown.toInt().toString(), style = MaterialTheme.typography.displaySmall)
}

/** Точка синхронизации: дышит, пока идёт; зелёная — всё хорошо, красная — ошибка. */
@Composable
private fun SyncDot(running: Boolean, ok: Boolean, onClick: () -> Unit) {
    val pulse by animateFloatAsState(if (running) 1.4f else 1f, spring(dampingRatio = 0.3f, stiffness = 120f), label = "syncPulse")
    Box(Modifier.size(44.dp).glass(CircleShape, 6.dp, strong = true).clickable(onClick = onClick).semantics { contentDescription = "Синхронизировать" }, contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_cloud), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        Box(
            Modifier.align(Alignment.TopEnd).padding(9.dp).size(8.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }
                .clip(CircleShape).background(if (running) Brand.Accent else if (ok) Brand.Ok else Brand.Error),
        )
    }
}

/** «Учёба»: повторение, прогресс, напоминания и конспекты с планшета. */
@Composable
private fun StudyTab(resumeTick: Int, go: (String) -> Unit) {
    val context = LocalContext.current
    val nowMs = remember(resumeTick) { System.currentTimeMillis() }
    val due by remember(nowMs) { context.app.db.cards().observeDueCount(nowMs) }.collectAsStateWithLifecycle(initialValue = 0)
    val total by remember { context.app.db.cards().observeCount() }.collectAsStateWithLifecycle(initialValue = 0)
    ScreenScaffold(title = "Учёба", onBack = null) {
        Column(
            Modifier.fillMaxWidth().enter().glass(RoundedCornerShape(26.dp), 12.dp).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Карточки", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AnimatedCount(due)
                    Text("на сегодня из $total", style = MaterialTheme.typography.bodyMedium, color = Brand.InkSoft)
                }
                InkButton(if (due > 0) "Повторить" else "Открыть", icon = R.drawable.ic_cards) { go(MainActivity.SCREEN_REVIEW) }
            }
        }
        // Сгруппированный список, как в настройках Android 16–17.
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val rows = listOf(
                Triple(R.drawable.ic_chart, "Успеваемость", "Оценки из личного кабинета") to MainActivity.SCREEN_GRADES,
                Triple(R.drawable.ic_chart, "Прогресс", "Активность и темы с ошибками") to MainActivity.SCREEN_PROGRESS,
                Triple(R.drawable.ic_notebook, "Конспекты", "Тетради с планшета — посмотреть") to MainActivity.SCREEN_NOTEBOOKS,
                Triple(R.drawable.ic_history, "Чистовики", "PDF аккуратных конспектов") to MainActivity.SCREEN_HISTORY,
            )
            rows.forEachIndexed { i, (spec, screen) ->
                // Плашка «проседает» под пальцем с пружинкой.
                val src = remember(screen) { MutableInteractionSource() }
                Row(
                    Modifier.fillMaxWidth().enter().pressable(src).glass(groupShape(i, rows.size), 8.dp)
                        .clickable(src, indication = null) { go(screen) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconBadge(spec.first, 40)
                    Column(Modifier.weight(1f)) {
                        Text(spec.second, style = MaterialTheme.typography.titleSmall)
                        Text(spec.third, style = MaterialTheme.typography.bodySmall, color = Brand.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = Brand.InkSoft, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

