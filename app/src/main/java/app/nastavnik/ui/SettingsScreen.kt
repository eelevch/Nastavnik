package app.nastavnik.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nastavnik.R
import app.nastavnik.ai.ModelPresets
import app.nastavnik.ai.Prompts
import app.nastavnik.ai.Strictness
import app.nastavnik.app
import app.nastavnik.data.BubbleSide
import app.nastavnik.data.SettingsRepo
import app.nastavnik.notes.NoteApps
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit, onAppearance: () -> Unit = {}, onOnboarding: () -> Unit = {}, onLks: () -> Unit = {}, go: (String) -> Unit = {}) {
    val context = LocalContext.current
    val repo = context.app.settings
    val s by remember { repo.flow() }.collectAsStateWithLifecycle(initialValue = repo.load())
    val tablet = remember(s.deviceRole) { context.app.isTablet() }

    ScreenScaffold(title = "Настройки", onBack = onBack) {
        InAppUpdateBanner()

        AppearanceEntry(onAppearance)

        if (tablet) {
            SectionCard("Нейросеть") {
                ProviderEditor(onChanged = {})
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                FieldLabel("Модель наставника")
                ModelPicker(s.provider, ModelPresets.mentor(s.provider), s.model) {
                    repo.setModel(it, s.provider)
                    if (s.cleanModel == s.model) repo.setCleanModel(it, s.provider)
                }
            }

            SectionCard("Наставник") {
                FieldLabel(
                    "Скорость ответа",
                    if (s.fastAnswers) "Быстро — модель меньше размышляет перед ответом."
                    else "Вдумчиво — больше времени на сложные задачи.",
                )
                Segmented(listOf(true to "Быстро", false to "Вдумчиво"), s.fastAnswers) { repo.setFastAnswers(it) }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                FieldLabel("Строгость", s.strictness.label)
                Segmented(
                    listOf(Strictness.QUESTIONS_ONLY to "Только вопросы", Strictness.HINTS to "Подсказки", Strictness.DETAILED to "Подробно"),
                    s.strictness,
                ) { repo.setStrictness(it) }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                FieldLabel("Следить за решением", "Проверять запись самому, когда перестанешь писать.")
                Segmented(listOf(0 to "Выкл.", 5 to "5 с", 10 to "10 с", 20 to "20 с"), s.watchPauseSec) {
                    repo.setWatchPauseSec(it)
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                FieldLabel("Голос озвучки", "Живая выразительная интонация наставника.")
                Segmented(
                    listOf("female" to "Женский", "male" to "Мужской", "system" to "Авто"),
                    s.ttsVoice,
                ) {
                    repo.setTtsVoice(it)
                    context.app.speaker.refreshVoice()
                }
            }

            UsageSection(s.model)
            CoordAccuracySection()
        }

        DeviceSection(tablet, showRole = false)
        GradesSettingsSection(onLks)
        SyncSection()

        MoreSettings(tablet) {
            if (tablet) {
                SectionCard("Плавающая кнопка и заметки") {
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
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    var fa by remember(s.noteFadeAlpha) { mutableFloatStateOf(s.noteFadeAlpha) }
                    FieldLabel("Прозрачность заметки у стилуса: ${(fa * 100).roundToInt()}%")
                    AppSlider(
                        value = fa,
                        onValueChange = { fa = it },
                        onValueChangeFinished = { repo.setNoteFadeAlpha(fa) },
                        valueRange = 0.15f..0.8f,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    val speaker = context.app.speaker
                    androidx.compose.runtime.LaunchedEffect(Unit) { speaker.prepare() }
                    val voices by speaker.voices.collectAsStateWithLifecycle()
                    var selectedVoice by remember { mutableStateOf(speaker.selectedVoice()) }
                    var neural by remember { mutableStateOf(speaker.neuralEnabled()) }
                    var neuralVoice by remember { mutableStateOf(speaker.neuralVoice()) }
                    val speechProblem by speaker.problem.collectAsStateWithLifecycle()
                    FieldLabel("Нейронный женский голос", "Живая интонация и смысловые паузы. Текст отправляется Gemini; нужен интернет и API-ключ, возможна оплата по тарифу Google.")
                    AppSwitch(neural) { neural = it; speaker.setNeural(it) }
                    if (neural) {
                        ApiKeyEditor(app.nastavnik.ai.AiProvider.GEMINI, onSaved = {})
                        speaker.neuralVoices.forEach { (id, label) ->
                            RadioRow(neuralVoice == id, label) {
                                neuralVoice = id
                                speaker.setNeuralVoice(id)
                                speaker.speak("Привет! Я помогу тебе разобраться. Давай подумаем: с чего начнём? Всё получится!")
                            }
                        }
                        SoftButton("Остановить прослушивание") { speaker.stop() }
                        speechProblem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    if (!neural) {
                        FieldLabel("Голос наставника", "Русские голоса установленного синтезатора. Сетевые голоса требуют интернета. Тембр и интонация зависят от движка.")
                        RadioRow(selectedVoice.isEmpty(), "Автоматически") {
                            selectedVoice = ""
                            speaker.selectVoice("")
                        }
                        voices.forEach { voice ->
                            RadioRow(selectedVoice == voice.name, voice.name + if (voice.isNetworkConnectionRequired) " · онлайн" else " · офлайн") {
                                selectedVoice = voice.name
                                speaker.selectVoice(voice.name)
                                speaker.speak("Привет! Давай разберёмся вместе. Что тебе хочется узнать?")
                            }
                        }
                    }
                    var speak by remember { mutableStateOf(repo.speakHints()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            FieldLabel("Читать подсказки вслух", "Озвучивать короткую подсказку сразу после ответа.")
                        }
                        AppSwitch(speak) { speak = it; repo.setSpeakHints(it) }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    FieldLabel("Что видит наставник", "«Весь конспект» отправляет с вопросом и пролистанные страницы тетради.")
                    Segmented(listOf(true to "Весь конспект", false to "Только экран"), s.seeNotebook) { repo.setSeeNotebook(it) }
                }

                SectionCard("Чистовик") {
                    FieldLabel("Модель для чистовика")
                    ModelPicker(s.provider, ModelPresets.mentor(s.provider), s.cleanModel) { repo.setCleanModel(it, s.provider) }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
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

            DeviceRoleSection(tablet)
            ScheduleExtraSection()
            BackupSection()
            UpdateSection()
            AboutSection(tablet, onOnboarding)
        }

        SystemPromptSection(s.customSystemPrompt) { repo.setCustomSystemPrompt(it) }
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

/** Режим устройства (вид приложения) — в «Дополнительно». */
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

/** Группа английского и фото преподавателей — в «Дополнительно». */
@Composable
private fun ScheduleExtraSection() {
    LanguageSection()
    TeacherPhotosSection()
}

/** «Дополнительно»: раскрывается плавно по нажатию. */
@Composable
private fun MoreSettings(tablet: Boolean, content: @Composable () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 90f else 0f, spring(dampingRatio = 0.75f, stiffness = 500f), label = "moreArrow")
    Row(
        Modifier
            .fillMaxWidth()
            .glass()
            .clickable { open = !open }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Дополнительно", style = MaterialTheme.typography.titleMedium)
            Text(
                if (tablet) "Кнопка, чистовик, расписание, резервная копия, обновления"
                else "Режим устройства, расписание, резервная копия, обновления",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            painterResource(R.drawable.ic_chevron),
            contentDescription = null,
            tint = Brand.InkSoft,
            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn },
        )
    }
    AnimatedVisibility(
        visible = open,
        enter = expandVertically(spring(dampingRatio = 0.82f, stiffness = 400f)) + fadeIn(tween(220, delayMillis = 40)),
        exit = shrinkVertically(spring(dampingRatio = 0.9f, stiffness = 450f)) + fadeOut(tween(160)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
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
