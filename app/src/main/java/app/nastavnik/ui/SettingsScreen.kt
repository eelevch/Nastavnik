package app.nastavnik.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nastavnik.BuildConfig
import app.nastavnik.R
import app.nastavnik.ai.ModelPresets
import app.nastavnik.ai.Prompts
import app.nastavnik.ai.Strictness
import app.nastavnik.app
import app.nastavnik.data.AppSettings
import app.nastavnik.data.BubbleSide
import app.nastavnik.data.SettingsRepo
import app.nastavnik.notes.NoteApps
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class SettingsSubPage(val title: String) {
    ROOT("Настройки"),
    MENTOR("Поведение наставника"),
    AI("Нейросеть и ключ"),
    STUDY("Учёба и расписание"),
    GRADES("Успеваемость"),
    DATA("Данные и облако"),
    ABOUT("Система и обновления"),
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAppearance: () -> Unit = {},
    onOnboarding: () -> Unit = {},
    onLks: () -> Unit = {},
    go: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val repo = context.app.settings
    val s by remember { repo.flow() }.collectAsStateWithLifecycle(initialValue = repo.load())
    val tablet = remember(s.deviceRole) { context.app.isTablet() }
    var subPage by rememberSaveable { mutableStateOf(SettingsSubPage.ROOT) }

    BackHandler(enabled = subPage != SettingsSubPage.ROOT) {
        subPage = SettingsSubPage.ROOT
    }

    ScreenScaffold(
        title = subPage.title,
        onBack = {
            if (subPage == SettingsSubPage.ROOT) onBack() else subPage = SettingsSubPage.ROOT
        },
    ) {
        AnimatedContent(
            targetState = subPage,
            transitionSpec = {
                val forward = targetState != SettingsSubPage.ROOT
                (fadeIn(tween(200)) + slideInHorizontally(tween(220)) { if (forward) it / 8 else -it / 8 })
                    .togetherWith(fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { if (forward) -it / 10 else it / 10 })
            },
            label = "settingsSubPage",
        ) { page ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (page) {
                    SettingsSubPage.ROOT -> SettingsRootHub(
                        tablet = tablet,
                        s = s,
                        repo = repo,
                        onAppearance = onAppearance,
                        onNavigate = { subPage = it },
                    )
                    SettingsSubPage.MENTOR -> MentorSettingsPage(tablet, s, repo)
                    SettingsSubPage.AI -> AiSettingsPage(s, repo)
                    SettingsSubPage.STUDY -> StudySettingsPage(tablet, s, repo)
                    SettingsSubPage.GRADES -> GradesSettingsSection(onLks)
                    SettingsSubPage.DATA -> {
                        SyncSection()
                        BackupSection()
                    }
                    SettingsSubPage.ABOUT -> {
                        UpdateSection()
                        DeviceRoleSection(tablet)
                        AboutSection(tablet, onOnboarding)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRootHub(
    tablet: Boolean,
    s: AppSettings,
    repo: SettingsRepo,
    onAppearance: () -> Unit,
    onNavigate: (SettingsSubPage) -> Unit,
) {
    InAppUpdateBanner()

    val aiProblem = repo.notReadyReason(s)
    if (aiProblem != null) {
        Banner("$aiProblem — наставник ждёт подключения.", "Подключить") {
            onNavigate(SettingsSubPage.AI)
        }
    }

    SectionCard("Наставник и внешний вид") {
        SettingsHubRow(
            title = "Оформление и сфера",
            subtitle = "${Brand.current.name} · цвет сферы, зрение и панель вкладок",
            leading = { MascotIcon(sizeDp = 28.dp) },
            onClick = onAppearance,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsHubRow(
            icon = R.drawable.ic_sparkle,
            title = "Поведение и голос наставника",
            subtitle = "${s.strictness.label} · " +
                (if (s.mentorRequirePermission) "Действия по разрешению" else "Авто-действия") +
                if (repo.speakHints()) " · Озвучка вкл." else "",
            onClick = { onNavigate(SettingsSubPage.MENTOR) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val modelShort = s.model.substringAfterLast('/')
        SettingsHubRow(
            icon = R.drawable.ic_bulb,
            title = "Нейросеть и ключ API",
            subtitle = "${s.provider.shortTitle} · $modelShort · " +
                if (aiProblem == null) "подключено" else "требуется настройка",
            statusOk = aiProblem == null,
            onClick = { onNavigate(SettingsSubPage.AI) },
        )
    }

    SectionCard("Учёба") {
        val alertText = if (s.reminderHour >= 0) " · карточки в ${s.reminderHour}:00" else ""
        val lessonMin = repo.lessonAlertMin()
        val lessonText = if (lessonMin > 0) "Напоминания за $lessonMin мин" else "Без напоминаний о парах"
        SettingsHubRow(
            icon = R.drawable.ic_calendar,
            title = "Пары, расписание и тишина",
            subtitle = lessonText + (if (repo.quietLessons()) " · Тихо на парах" else "") + alertText,
            onClick = { onNavigate(SettingsSubPage.STUDY) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsHubRow(
            icon = R.drawable.ic_chart,
            title = "Успеваемость (ЛКС Сириус)",
            subtitle = if (repo.lksConfigured) "ЛКС подключён · автообновление" else "Войдите в ЛКС для оценок и среднего балла",
            statusOk = repo.lksConfigured,
            onClick = { onNavigate(SettingsSubPage.GRADES) },
        )
    }

    SectionCard("Данные и система") {
        SettingsHubRow(
            icon = R.drawable.ic_cloud,
            title = "Синхронизация и резервная копия",
            subtitle = if (repo.syncConfigured) "Облако подключено · ZIP-экспорт" else "Только на устройстве · ZIP-архив",
            statusOk = if (repo.syncConfigured) true else null,
            onClick = { onNavigate(SettingsSubPage.DATA) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val deviceLabel = if (tablet) "Планшет" else "Телефон"
        SettingsHubRow(
            icon = R.drawable.ic_tune,
            title = "Система и обновления",
            subtitle = "Версия ${BuildConfig.VERSION_NAME} · $deviceLabel",
            onClick = { onNavigate(SettingsSubPage.ABOUT) },
        )
    }
}

@Composable
private fun SettingsHubRow(
    title: String,
    subtitle: String,
    icon: Int? = null,
    statusOk: Boolean? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (leading != null) {
            Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { leading() }
        } else if (icon != null) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brand.Ink.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = Brand.Ink, modifier = Modifier.size(19.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (statusOk != null) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (statusOk) Brand.Ok else Brand.Error),
                    )
                }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = Brand.InkSoft, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun MentorSettingsPage(tablet: Boolean, s: AppSettings, repo: SettingsRepo) {
    val context = LocalContext.current
    SectionCard("Обучение и конспект") {
        FieldLabel("Строгость", s.strictness.label)
        Segmented(
            listOf(Strictness.QUESTIONS_ONLY to "Вопросы", Strictness.HINTS to "Подсказки", Strictness.DETAILED to "Подробно"),
            s.strictness,
        ) { repo.setStrictness(it) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FieldLabel(
            "Скорость ответа",
            if (s.fastAnswers) "Быстро — модель меньше размышляет перед ответом."
            else "Вдумчиво — больше времени на сложные задачи.",
        )
        Segmented(listOf(true to "Быстро", false to "Вдумчиво"), s.fastAnswers) { repo.setFastAnswers(it) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FieldLabel(
                    "Спрашивать разрешение перед записью на лист",
                    "Наставник сначала покажет список изменений (запись, рисунки, страницы) и выполнит после подтверждения.",
                )
            }
            AppSwitch(s.mentorRequirePermission) { repo.setMentorRequirePermission(it) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FieldLabel(
                    "Шар наставника на главных экранах",
                    "Быстрый вопрос текстом или голосом в углу без открытия отдельного окна.",
                )
            }
            AppSwitch(s.showAmbientMentor) { repo.setShowAmbientMentor(it) }
        }
        if (tablet) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FieldLabel("Проверять, когда остановился писать", "Фоновая проверка после паузы.")
            Segmented(listOf(0 to "Выкл.", 5 to "5 с", 10 to "10 с", 20 to "20 с"), s.watchPauseSec) {
                repo.setWatchPauseSec(it)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FieldLabel("Контекст тетради", "«Весь конспект» отправляет с вопросом и другие страницы.")
            Segmented(listOf(true to "Весь конспект", false to "Только лист"), s.seeNotebook) { repo.setSeeNotebook(it) }
        }
    }

    SectionCard("Голос и озвучка") {
        var speak by remember(s) { mutableStateOf(repo.speakHints()) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FieldLabel("Читать подсказки вслух", "Автоматически озвучивать ответ сразу после появления.")
            }
            AppSwitch(speak) { speak = it; repo.setSpeakHints(it) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FieldLabel("Тембр голоса")
        Segmented(
            listOf("female" to "Женский", "male" to "Мужской", "system" to "Авто"),
            s.ttsVoice,
        ) {
            repo.setTtsVoice(it)
            context.app.speaker.refreshVoice()
        }
        CollapsibleBlock("Расширенные настройки голоса") {
            val speaker = context.app.speaker
            LaunchedEffect(Unit) { speaker.prepare() }
            val voices by speaker.voices.collectAsStateWithLifecycle()
            var selectedVoice by remember { mutableStateOf(speaker.selectedVoice()) }
            var neural by remember { mutableStateOf(speaker.neuralEnabled()) }
            var neuralVoice by remember { mutableStateOf(speaker.neuralVoice()) }
            val speechProblem by speaker.problem.collectAsStateWithLifecycle()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("Нейронный голос Gemini", "Живая интонация; нужен ключ Gemini и интернет.")
                }
                AppSwitch(neural) { neural = it; speaker.setNeural(it) }
            }
            if (neural) {
                ApiKeyEditor(app.nastavnik.ai.AiProvider.GEMINI, onSaved = {})
                speaker.neuralVoices.forEach { (id, label) ->
                    RadioRow(neuralVoice == id, label) {
                        neuralVoice = id
                        speaker.setNeuralVoice(id)
                        speaker.speak("Привет! Я помогу разобраться с задачей.")
                    }
                }
                SoftButton("Остановить") { speaker.stop() }
                speechProblem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } else {
                RadioRow(selectedVoice.isEmpty(), "Системный автоматически") {
                    selectedVoice = ""
                    speaker.selectVoice("")
                }
                voices.forEach { voice ->
                    RadioRow(selectedVoice == voice.name, voice.name + if (voice.isNetworkConnectionRequired) " · онлайн" else " · офлайн") {
                        selectedVoice = voice.name
                        speaker.selectVoice(voice.name)
                        speaker.speak("Привет! Давай разберёмся вместе.")
                    }
                }
            }
        }
    }

    if (tablet) {
        CollapsibleBlock("Плавающая кнопка поверх других приложений") {
            SectionCard(null) {
                var alpha by remember(s.bubbleAlpha) { mutableFloatStateOf(s.bubbleAlpha) }
                FieldLabel("Непрозрачность кнопки: ${(alpha * 100).roundToInt()}%")
                AppSlider(
                    value = alpha,
                    onValueChange = { alpha = it },
                    onValueChangeFinished = { repo.setBubbleAlpha(alpha) },
                    valueRange = SettingsRepo.MIN_ALPHA..SettingsRepo.MAX_ALPHA,
                )
                var size by remember(s.bubbleSizeDp) { mutableFloatStateOf(s.bubbleSizeDp.toFloat()) }
                FieldLabel("Размер кнопки: ${size.roundToInt()} dp")
                AppSlider(
                    value = size,
                    onValueChange = { size = it },
                    onValueChangeFinished = { repo.setBubbleSize(size.roundToInt()) },
                    valueRange = 40f..72f,
                    steps = 7,
                )
                FieldLabel("Сторона экрана")
                Segmented(listOf(BubbleSide.LEFT to "Слева", BubbleSide.RIGHT to "Справа"), s.bubbleSide) { repo.setBubbleSide(it) }
                var fa by remember(s.noteFadeAlpha) { mutableFloatStateOf(s.noteFadeAlpha) }
                FieldLabel("Прозрачность заметки у стилуса: ${(fa * 100).roundToInt()}%")
                AppSlider(
                    value = fa,
                    onValueChange = { fa = it },
                    onValueChangeFinished = { repo.setNoteFadeAlpha(fa) },
                    valueRange = 0.15f..0.8f,
                )
            }
        }
    }
}

@Composable
private fun AiSettingsPage(s: AppSettings, repo: SettingsRepo) {
    val aiProblem = repo.notReadyReason(s)
    var editingProvider by rememberSaveable { mutableStateOf(aiProblem != null) }

    SectionCard("Подключение нейросети") {
        if (aiProblem == null && !editingProvider) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusPill("${s.provider.shortTitle} · подключено", ok = true)
                LinkText("Изменить") { editingProvider = true }
            }
        } else {
            ProviderEditor(onChanged = {})
            if (aiProblem == null) {
                LinkText("Свернуть настройки ключа") { editingProvider = false }
            }
        }
    }

    SectionCard("Модели") {
        FieldLabel("Модель наставника")
        ModelPicker(s.provider, ModelPresets.mentor(s.provider), s.model) {
            repo.setModel(it, s.provider)
            if (s.cleanModel == s.model) repo.setCleanModel(it, s.provider)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FieldLabel("Модель для чистовика")
        ModelPicker(s.provider, ModelPresets.mentor(s.provider), s.cleanModel) {
            repo.setCleanModel(it, s.provider)
        }
    }

    UsageSection(s.model)

    CollapsibleBlock("Для продвинутых (системный промт и координаты)") {
        SystemPromptSection(s.customSystemPrompt) { repo.setCustomSystemPrompt(it) }
        CoordAccuracySection()
    }
}

@Composable
private fun StudySettingsPage(tablet: Boolean, s: AppSettings, repo: SettingsRepo) {
    val context = LocalContext.current
    DeviceSection(tablet, showRole = false)
    LanguageSection()
    TeacherPhotosSection()
    if (tablet) {
        SectionCard("Экспорт чистовика (PDF)") {
            FieldLabel("Куда отправлять PDF")
            val targets = remember { NoteApps.pdfTargets(context) }
            RadioRow(s.sendTarget == SettingsRepo.TARGET_CHOOSER, "Каждый раз спрашивать") {
                repo.setSendTarget(SettingsRepo.TARGET_CHOOSER)
            }
            targets.forEach { t ->
                RadioRow(s.sendTarget == t.encode(), t.label) { repo.setSendTarget(t.encode()) }
            }
        }
    }
}

@Composable
private fun CollapsibleBlock(title: String, content: @Composable () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 90f else 0f, spring(dampingRatio = 0.75f, stiffness = 500f), label = "colArrow")
    Row(
        Modifier
            .fillMaxWidth()
            .glass()
            .clickable { open = !open }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(
            painterResource(R.drawable.ic_chevron),
            contentDescription = null,
            tint = Brand.InkSoft,
            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn },
        )
    }
    AnimatedVisibility(
        visible = open,
        enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 420f)) + fadeIn(tween(180)),
        exit = shrinkVertically(spring(dampingRatio = 0.9f, stiffness = 450f)) + fadeOut(tween(140)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun SystemPromptSection(current: String, onSave: (String) -> Unit) {
    val context = LocalContext.current
    val settingsRepo = context.app.settings
    val scope = rememberCoroutineScope()
    val isCustom = current.isNotBlank()
    var editing by remember { mutableStateOf(false) }
    var clearOnChange by remember { mutableStateOf(settingsRepo.clearChatOnPromptChange()) }
    var confirmClear by remember { mutableStateOf(false) }
    var clearedHint by remember { mutableStateOf(false) }

    fun savePrompt(next: String) {
        val normalized = next.trim()
        val changed = normalized != current.trim()
        onSave(normalized)
        if (changed && clearOnChange) {
            scope.launch {
                context.app.clearChatHistory()
                clearedHint = true
            }
        }
    }

    SectionCard("Системный промт") {
        FieldLabel(
            "Промт наставника",
            if (isCustom) "Используется свой промт (${current.length} симв.)."
            else "По умолчанию — задаёт стиль, строгость и правила ответов наставника.",
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SoftButton(if (isCustom) "Изменить промт" else "Настроить промт") {
                clearedHint = false
                editing = true
            }
            if (isCustom) {
                LinkText("Сбросить") { savePrompt("") }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FieldLabel(
                    "Очищать историю чатов при смене промта",
                    "Удаляет прошлые диалоги, чтобы наставник сразу отвечал по новым правилам.",
                )
            }
            AppSwitch(clearOnChange) {
                clearOnChange = it
                settingsRepo.setClearChatOnPromptChange(it)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SoftButton("Очистить историю чатов", icon = R.drawable.ic_trash) { confirmClear = true }
            if (clearedHint) {
                Text("История очищена", style = MaterialTheme.typography.labelMedium, color = Brand.Ok)
            }
        }
    }
    if (editing) {
        var draft by remember(current) { mutableStateOf(current.ifBlank { Prompts.DEFAULT_MENTOR_TEMPLATE }) }
        AlertDialog(
            onDismissRequest = { editing = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 680.dp),
            title = { Text("Системный промт") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Можно использовать подстановки {STRICTNESS}, {COORDS}, {COORDS_RULE}. Пустой текст вернёт стандартный промт.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        minLines = 8,
                        maxLines = 16,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 420.dp),
                    )
                    LinkText("Вернуть стандартный") { draft = Prompts.DEFAULT_MENTOR_TEMPLATE }
                }
            },
            confirmButton = {
                InkButton("Сохранить") {
                    val trimmed = draft.trim()
                    if (trimmed.isEmpty() || trimmed == Prompts.DEFAULT_MENTOR_TEMPLATE.trim()) {
                        savePrompt("")
                    } else {
                        savePrompt(trimmed)
                    }
                    editing = false
                }
            },
            dismissButton = { SoftButton("Отмена") { editing = false } },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить историю чатов?") },
            text = {
                Text(
                    "Все прошлые диалоги с наставником будут удалены. Конспекты и тетради останутся.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                InkButton("Очистить", danger = true) {
                    confirmClear = false
                    scope.launch {
                        context.app.clearChatHistory()
                        clearedHint = true
                    }
                }
            },
            dismissButton = { SoftButton("Отмена") { confirmClear = false } },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
        )
    }
}

@Composable
private fun DeviceRoleSection(tablet: Boolean) {
    val repo = LocalContext.current.app.settings
    var role by remember { mutableStateOf(repo.deviceRole()) }
    SectionCard("Режим устройства") {
        FieldLabel(
            "Вид приложения",
            if (tablet) "Планшет: тетради со стилусом, наставник и чистовики." else "Телефон: расписание, оценки и карточки.",
        )
        Segmented(listOf("auto" to "Авто", "phone" to "Телефон", "tablet" to "Планшет"), role) {
            role = it
            repo.setDeviceRole(it)
        }
    }
}

@Composable
private fun AboutSection(tablet: Boolean, onOnboarding: () -> Unit) {
    SectionCard("О приложении") {
        if (tablet) LinkText("Пройти первую настройку заново", onClick = onOnboarding)
        Text(
            "Приложение неофициальное и не связано с Университетом «Сириус».",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
