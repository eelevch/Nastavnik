package app.nastavnik.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import app.nastavnik.BuildConfig
import app.nastavnik.R
import app.nastavnik.data.AppSettings
import app.nastavnik.data.TelegramMetrics
import app.nastavnik.theme.TabBarPosition
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.remember
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import app.nastavnik.app
import app.nastavnik.study.StudyWidget
import app.nastavnik.study.LessonQuiet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.nastavnik.overlay.BubbleService

class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf(SCREEN_HOME)

    /** Экран открыла кнопка поверх приложения для записей — закрыв его, возвращаемся туда, где писали. */
    private var fromOverlay = false

    /** Открытая тетрадь (для экрана [SCREEN_NOTEBOOK]). */
    private var notebookId by mutableLongStateOf(0L)

    /** Пока из «Поделиться» готовятся страницы — показываем, что идёт работа. */
    private var importing by mutableStateOf(false)

    /** Прислали PDF — спросить, это ДЗ или в тетрадь. */
    private var askShared by mutableStateOf<List<Uri>?>(null)

    /** PDF с заданиями из «Поделиться» — для экрана «Домашка». */
    private var sharedHomework by mutableStateOf<Uri?>(null)

    /** Телефон: открытая вкладка. */
    private var phoneTab by mutableStateOf(PhoneTab.TODAY)

    /** Планшет: открытая вкладка капсульной панели. */
    private var tabletTab by mutableStateOf(TabletTab.HOME)

    /** Растёт при каждом onResume — экраны перечитывают состояние разрешений. */
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableHighRefreshRate()
        // Фон всегда светлый — значки строки состояния тёмные и при тёмной теме системы.
        val bars = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        notebookId = savedInstanceState?.getLong(KEY_NOTEBOOK) ?: 0L
        savedInstanceState?.getString(KEY_TAB)?.let { t -> PhoneTab.entries.firstOrNull { it.name == t }?.let { phoneTab = it } }
        savedInstanceState?.getString(KEY_TABLET_TAB)?.let { t -> TabletTab.entries.firstOrNull { it.name == t }?.let { tabletTab = it } }
        screen = savedInstanceState?.getString(KEY_SCREEN)
            ?: screenFrom(intent)
            // Первая настройка — про кнопку поверх записей; телефону она не нужна.
            ?: if (app.settings.load().onboardingDone || !app.isTablet()) SCREEN_HOME else SCREEN_ONBOARDING
        if (savedInstanceState == null) receiveShared(intent)

        setContent {
            val prefs by remember { app.settings.flow() }.collectAsStateWithLifecycle(initialValue = null)
            // Оформление входит в настройки: поменяли — поток выдаёт новые настройки, и экран сразу перекрашивается.
            val st = prefs ?: app.settings.load()
            val appearance = appearanceOf(st)
            val dark = appearance.resolve(isSystemInDarkTheme()).dark
            LaunchedEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            val tablet = remember(st.deviceRole) { app.isTablet() }
            NastavnikTheme(appearance) {
                val go: (String) -> Unit = { target ->
                    fromOverlay = false
                    // На телефоне расписание и успеваемость — вкладки главного экрана.
                    when {
                        !tablet && target == SCREEN_SCHEDULE -> { phoneTab = PhoneTab.SCHEDULE; screen = SCREEN_HOME }
                        !tablet && target == SCREEN_GRADES -> { phoneTab = PhoneTab.GRADES; screen = SCREEN_HOME }
                        tablet && target == SCREEN_SCHEDULE -> { tabletTab = TabletTab.SCHEDULE; screen = SCREEN_HOME }
                        tablet && target == SCREEN_GRADES -> { tabletTab = TabletTab.GRADES; screen = SCREEN_HOME }
                        else -> screen = target
                    }
                }
                val home = {
                    screen = SCREEN_HOME
                    if (fromOverlay) moveTaskToBack(true)
                    fromOverlay = false
                }
                val openNotebook: (Long) -> Unit = { id ->
                    notebookId = id
                    // Телефон — только просмотр: пишут стилусом на планшете.
                    go(if (tablet) SCREEN_NOTEBOOK else SCREEN_VIEWER)
                }
                BackHandler(enabled = true) {
                    when {
                        screen != SCREEN_HOME -> {
                            when (screen) {
                                SCREEN_CLEAN_TEST -> go(SCREEN_CLEAN)
                                SCREEN_APPEARANCE -> go(SCREEN_SETTINGS)
                                SCREEN_VIEWER -> go(SCREEN_NOTEBOOKS)
                                SCREEN_NOTEBOOK -> go(SCREEN_NOTEBOOKS)
                                SCREEN_LKS -> go(SCREEN_GRADES)
                                SCREEN_LOCK_SETUP -> go(SCREEN_SETTINGS)
                                else -> home()
                            }
                        }
                        !tablet && phoneTab != PhoneTab.TODAY -> phoneTab = PhoneTab.TODAY
                        tablet && tabletTab != TabletTab.HOME -> tabletTab = TabletTab.HOME
                        else -> {
                            if (fromOverlay) moveTaskToBack(true) else finish()
                        }
                    }
                }

                if (importing) {
                    ImportingScreen()
                    return@NastavnikTheme
                }
                // Экраны сменяются мягко: для тетради — чистый GPU fade без масштабирования AndroidView,
                // для остальных — мягкое «сквозное проявление» без рывков.
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        if (targetState == SCREEN_NOTEBOOK || initialState == SCREEN_NOTEBOOK) {
                            fadeIn(tween(190, easing = Motion.Decelerate))
                                .togetherWith(fadeOut(tween(130, easing = Motion.Accelerate)))
                        } else {
                            (fadeIn(tween(220, easing = Motion.Decelerate)) + scaleIn(tween(220, easing = Motion.Decelerate), initialScale = 0.985f))
                                .togetherWith(fadeOut(tween(140, easing = Motion.Accelerate)))
                        }
                    },
                    label = "screen",
                ) { target ->
                    when (target) {
                        SCREEN_NOTEBOOKS -> NotebooksScreen(resumeTick, onBack = home, onOpen = openNotebook, readOnly = !tablet)
                        SCREEN_VIEWER -> NotebookViewer(notebookId, onBack = { go(SCREEN_NOTEBOOKS) })
                        SCREEN_SCHEDULE -> ScheduleScreen(onBack = home)
                        SCREEN_GRADES -> GradesScreen(onBack = home, onSettings = { go(SCREEN_SETTINGS) }, onLks = { go(SCREEN_LKS) })
                        SCREEN_LKS -> LksScreen(onBack = { go(SCREEN_GRADES) }, onDone = { go(SCREEN_GRADES) })
                        SCREEN_NOTEBOOK -> if (notebookId != 0L) {
                            NotebookScreen(
                                notebookId,
                                onBack = { go(SCREEN_NOTEBOOKS) },
                                onCleanReady = { go(SCREEN_CLEAN) },
                                onSettings = { go(SCREEN_SETTINGS) },
                            )
                        } else {
                            NotebooksScreen(resumeTick, onBack = home, onOpen = openNotebook)
                        }
                        SCREEN_TRAINING, SCREEN_EXAMS -> TrainingScreen(
                            initialTab = if (target == SCREEN_EXAMS) TrainTab.EXAMS else TrainTab.VARIANT,
                            onBack = home,
                            onOpenNotebook = openNotebook,
                            onReview = { go(SCREEN_REVIEW) },
                            onSettings = { go(SCREEN_SETTINGS) },
                        )
                        SCREEN_ONBOARDING -> OnboardingScreen(resumeTick, onDone = { screen = SCREEN_HOME; moveTaskToBack(true) }, onSkip = { screen = SCREEN_HOME })
                        SCREEN_SETTINGS -> SettingsScreen(onBack = home, onAppearance = { go(SCREEN_APPEARANCE) }, onOnboarding = { go(SCREEN_ONBOARDING) }, onLks = { go(SCREEN_LKS) }, go = go)
                        SCREEN_EVENING -> EveningScreen(resumeTick, onBack = home, go = go)
                        SCREEN_LOCK_SETUP -> StudyLockSetupScreen(onBack = { go(SCREEN_SETTINGS) }, onDone = { go(SCREEN_EVENING) })
                        SCREEN_REMINDERS -> RemindersScreen(onBack = home)
                        SCREEN_HOMEWORK -> HomeworkScreen(
                            resumeTick, onBack = home, openNotebook = openNotebook,
                            sharedFile = sharedHomework, onSharedUsed = { sharedHomework = null },
                        )
                        SCREEN_APPEARANCE -> AppearanceScreen(onBack = { go(SCREEN_SETTINGS) })
                        SCREEN_HISTORY -> HistoryScreen(onBack = home, onCleanReady = { go(SCREEN_CLEAN) })
                        SCREEN_SEARCH -> SearchScreen(
                            onBack = home,
                            onOpenNotebook = openNotebook,
                            onHistory = { go(SCREEN_HISTORY) },
                        )
                        SCREEN_CLEAN_TEST -> CleanTestScreen(onBack = { go(SCREEN_CLEAN) })
                        SCREEN_REVIEW -> ReviewScreen(onBack = home, onExample = { go(SCREEN_CLEAN_TEST) })
                        SCREEN_PROGRESS -> ProgressScreen(onBack = home, onReview = { go(SCREEN_REVIEW) })
                        SCREEN_CLEAN -> CleanScreen(
                            // Чистовик открыла кнопка наставника — закрыв его, возвращаемся к записям.
                            onClose = home,
                            onSettings = { go(SCREEN_SETTINGS) },
                            onExample = { go(SCREEN_CLEAN_TEST) },
                        )
                        else -> if (tablet) TabletShell(
                            tab = tabletTab,
                            onTab = { tabletTab = it },
                            tabBarPosition = TabBarPosition.forTablet(st.tabletTabBar),
                            sideCapsuleBias = st.sideCapsuleBias,
                            resumeTick = resumeTick,
                            go = go,
                            openNotebook = openNotebook,
                        ) else PhoneShell(
                            tab = phoneTab,
                            onTab = { phoneTab = it },
                            tabBarPosition = TabBarPosition.forPhone(st.phoneTabBar),
                            sideCapsuleBias = st.sideCapsuleBias,
                            resumeTick = resumeTick,
                            go = go,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        screenFrom(intent)?.let { screen = it }
        receiveShared(intent)
    }

    /**
     * Фото или PDF из любого приложения («Поделиться», «Открыть с помощью») — новая тетрадь:
     * поверх можно писать, а наставник видит все страницы.
     */
    private fun receiveShared(intent: Intent) {
        val uris = sharedUris(intent)
        if (uris.isEmpty()) return
        val type = intent.type ?: uris.firstOrNull()?.let { contentResolver.getType(it) }.orEmpty()
        // PDF — может быть домашкой: спросим.
        if (type.contains("pdf")) { importShared(uris, "pdf"); return }
        importShared(uris, type)
    }

    private fun importShared(uris: List<Uri>, type: String) {
        importing = true
        lifecycleScope.launch {
            val meta = runCatching {
                val nbs = app.notebooks
                if (type.contains("pdf")) nbs.importPdf(uris.first()) else nbs.importImages(uris)
            }.getOrNull()
            importing = false
            if (meta != null) {
                fromOverlay = false
                notebookId = meta.id
                screen = if (app.isTablet()) SCREEN_NOTEBOOK else SCREEN_VIEWER
            }
        }
    }

    private fun sharedUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        Intent.ACTION_VIEW -> listOfNotNull(intent.data)
        else -> emptyList()
    }

    /**
     * Какой экран открыть по интенту. Экран, открытый кнопкой поверх записей, по «Назад» возвращает туда.
     * Ярлык «Запустить наставника» включает его и сразу уходит в фон — к записям.
     */
    private fun screenFrom(intent: Intent): String? {
        if (BuildConfig.DEBUG) {
            intent.getStringExtra("debug_role")?.let { app.settings.setDeviceRole(it) }
            intent.getStringExtra("debug_theme")?.let { app.settings.setThemeMode(it) }
            intent.getStringExtra("debug_tablet_tab_bar")?.let {
                app.settings.setTabletTabBar(TabBarPosition.forTablet(it))
            }
            intent.getStringExtra("debug_phone_tab_bar")?.let {
                app.settings.setPhoneTabBar(TabBarPosition.forPhone(it))
            }
            if (intent.getBooleanExtra("debug_skip_onboarding", false)) {
                app.settings.setOnboardingDone(true)
            }
        }
        val s = intent.getStringExtra(EXTRA_SCREEN) ?: return null
        fromOverlay = intent.getBooleanExtra(EXTRA_FROM_OVERLAY, false)
        // Расписание и успеваемость — вкладки оболочки.
        if (!app.isTablet()) when (s) {
            SCREEN_GRADES -> { phoneTab = PhoneTab.GRADES; return SCREEN_HOME }
            SCREEN_SCHEDULE -> { phoneTab = PhoneTab.SCHEDULE; return SCREEN_HOME }
        } else when (s) {
            SCREEN_GRADES -> { tabletTab = TabletTab.GRADES; return SCREEN_HOME }
            SCREEN_SCHEDULE -> { tabletTab = TabletTab.SCHEDULE; return SCREEN_HOME }
        }
        intent.getLongExtra(EXTRA_NOTEBOOK, 0L).takeIf { it != 0L }?.let { notebookId = it }
        if (s == ACTION_START_MENTOR) {
            if (Permissions.overlay(this)) {
                BubbleService.start(this)
                moveTaskToBack(true)
            }
            return SCREEN_HOME
        }
        return s
    }

    override fun onResume() {
        super.onResume()
        enableHighRefreshRate()
        resumeTick++
        app.sync.onForeground()
        // Оценки, виджет и телеметрия — в фоне, чтобы первый кадр при возврате открывался плавно.
        app.appScope.launch(Dispatchers.IO) {
            runCatching { StudyWidget.refresh(this@MainActivity) }
            runCatching { app.grades.refresh(); app.grades.refreshLks() }
            runCatching { LessonQuiet.update(this@MainActivity) }
            runCatching { TelegramMetrics.reportAppOpen(this@MainActivity) }
        }
        app.checkUpdateInBackground()
    }

    /**
     * Включение максимальной частоты экрана (120/144 Гц) для плавной анимации и мгновенного отклика пера.
     */
    private fun enableHighRefreshRate() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            runCatching {
                val display = display
                val modes = display?.supportedModes ?: emptyArray()
                val maxMode = modes.maxByOrNull { it.refreshRate }
                if (maxMode != null && maxMode.refreshRate > 60f) {
                    val lp = window.attributes
                    lp.preferredDisplayModeId = maxMode.modeId
                    window.attributes = lp
                }
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            runCatching {
                @Suppress("DEPRECATION")
                val modes = window.windowManager.defaultDisplay.supportedModes
                val maxMode = modes.maxByOrNull { it.refreshRate }
                if (maxMode != null && maxMode.refreshRate > 60f) {
                    val lp = window.attributes
                    lp.preferredDisplayModeId = maxMode.modeId
                    window.attributes = lp
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Ушли из приложения — отправляем сделанное на другие устройства.
        app.sync.soon(300)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SCREEN, screen)
        outState.putLong(KEY_NOTEBOOK, notebookId)
        outState.putString(KEY_TAB, phoneTab.name)
        outState.putString(KEY_TABLET_TAB, tabletTab.name)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            val fromStylus = event.isFromSource(android.view.InputDevice.SOURCE_STYLUS) ||
                (event.source and android.view.InputDevice.SOURCE_BLUETOOTH_STYLUS != 0)
            if (fromStylus) {
                // Нажатие кнопки пера / жест стилуса не должны закрывать тетрадь или выходить из приложения.
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_HOME = "home"
        const val SCREEN_SETTINGS = "settings"
        const val SCREEN_HISTORY = "history"
        const val SCREEN_ONBOARDING = "onboarding"
        const val SCREEN_CLEAN_TEST = "clean_test"
        const val SCREEN_CLEAN = "clean"
        const val SCREEN_REVIEW = "review"
        const val SCREEN_PROGRESS = "progress"
        const val SCREEN_NOTEBOOKS = "notebooks"
        const val SCREEN_TRAINING = "training"
        const val SCREEN_SEARCH = "search"
        const val SCREEN_APPEARANCE = "appearance"
        const val SCREEN_EXAMS = "exams"
        const val SCREEN_NOTEBOOK = "notebook"
        const val SCREEN_SCHEDULE = "schedule"
        const val SCREEN_VIEWER = "viewer"
        const val SCREEN_GRADES = "grades"
        const val SCREEN_LKS = "lks"
        const val SCREEN_EVENING = "evening"
        const val SCREEN_HOMEWORK = "homework"
        const val SCREEN_LOCK_SETUP = "lock_setup"
        const val SCREEN_REMINDERS = "reminders"
        const val EXTRA_NOTEBOOK = "notebook_id"
        const val EXTRA_FROM_OVERLAY = "from_overlay"

        /** Ярлык на значке приложения: включить наставника. */
        const val ACTION_START_MENTOR = "start"
        private const val KEY_SCREEN = "screen"
        private const val KEY_NOTEBOOK = "notebook"
        private const val KEY_TAB = "phone_tab"
        private const val KEY_TABLET_TAB = "tablet_tab"
    }
}

/** Пока из присланных фото или PDF готовятся страницы. */
@Composable
private fun ImportingScreen() {
    Box(Modifier.fillMaxSize().paperBackdrop(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = Brand.Accent, trackColor = Brand.Accent.copy(alpha = 0.12f))
            Text("Готовлю страницы тетради…", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Оформление из настроек — одно для всех экранов. */
internal fun appearanceOf(st: AppSettings) = Appearance(
    mode = runCatching { ThemeMode.valueOf(st.themeMode) }.getOrDefault(ThemeMode.LIGHT),
    light = st.lightTheme,
    dark = st.darkTheme,
    accent = st.accent,
    backdrop = runCatching { Backdrop.valueOf(st.backdrop) }.getOrDefault(Backdrop.PLAIN),
    textScale = st.textScale,
)
