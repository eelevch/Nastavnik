package app.nastavnik.ui

import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nastavnik.BuildConfig
import app.nastavnik.NastavnikApp
import app.nastavnik.ai.DrawingKind
import app.nastavnik.ai.Mark
import app.nastavnik.ai.MarkKind
import app.nastavnik.ai.MentorActionKind
import app.nastavnik.ai.MentorPageAction
import app.nastavnik.ai.NBox
import app.nastavnik.ai.Prompts
import app.nastavnik.capture.ImageUtils
import app.nastavnik.data.DeviceProfile
import app.nastavnik.geom.IRect
import app.nastavnik.ink.AndroidMathMetrics
import app.nastavnik.ink.InkPage
import app.nastavnik.ink.InkView
import app.nastavnik.ink.MentorNotebookWriter
import app.nastavnik.ink.NotebookMeta
import app.nastavnik.ink.NotebookStore
import app.nastavnik.ink.SavedChatCodec
import app.nastavnik.ink.PageRenderer
import android.graphics.Bitmap
import app.nastavnik.ink.PAGE_W
import app.nastavnik.ink.PageMark
import app.nastavnik.ink.Paper
import app.nastavnik.ink.Tool
import app.nastavnik.ink.EditTool
import app.nastavnik.ink.ShapeKind
import app.nastavnik.ink.Stroke
import app.nastavnik.ink.TextBox
import app.nastavnik.ink.UndoStack
import app.nastavnik.session.AnswerUi
import app.nastavnik.session.AskKind
import app.nastavnik.session.MentorSession
import app.nastavnik.study.PracticePrompts
import app.nastavnik.session.ShotGeometry
import app.nastavnik.session.ShotResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Всё, что происходит в открытой тетради: страницы, сохранение, инструменты и наставник, который видит
 * текущую страницу (с номерами строк) и остальные страницы тетради как контекст.
 */
/**
 * На какой странице открыть тетрадь: тетрадь → страница. Действует 15 секунд после [set] — экран тетради при переходе
 * может собраться дважды, и оба раза должен открыться на этой странице.
 */
object NotebookStart {
    @Volatile private var target: Triple<Long, Long, Long>? = null

    fun set(notebookId: Long, pageId: Long) { target = Triple(notebookId, pageId, System.currentTimeMillis()) }

    fun take(notebookId: Long): Long? =
        target?.takeIf { it.first == notebookId && System.currentTimeMillis() - it.third < 15_000L }?.second
}

object NotebookVisionDemoStart {
    @Volatile private var pending: Triple<Long, String, Long>? = null

    fun put(notebookId: Long, mode: String) {
        pending = Triple(notebookId, mode, System.currentTimeMillis())
    }

    fun take(notebookId: Long): String? =
        pending?.takeIf { it.first == notebookId && System.currentTimeMillis() - it.third < 20_000L }
            ?.also { pending = null }
            ?.second
}

class NotebookController(val app: NastavnikApp, val notebookId: Long, private val scope: CoroutineScope) : InkView.Listener {
    private val nbs = app.notebooks
    val ink = InkView(app).also {
        it.listener = this
        it.contentDescription = "Лист тетради"
    }

    var meta by mutableStateOf<NotebookMeta?>(null)
        private set
    var pages by mutableStateOf<List<InkPage>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set

    // Инструменты (цвет ручки и «писать пальцем» запоминаются между тетрадями).
    var editTool by mutableStateOf(EditTool.PEN)
        private set
    var penColor by mutableIntStateOf(app.settings.penColor())
        private set
    var markerColor by mutableIntStateOf(MARKERS.first())
        private set
    var penWidth by mutableStateOf(3.2f)
        private set
    var markerWidth by mutableStateOf(InkView.HIGHLIGHTER_W)
        private set
    var partialEraser by mutableStateOf(false)
        private set
    var shapeKind by mutableStateOf(ShapeKind.LINE)
        private set
    var textSize by mutableStateOf(TEXT_SIZES[1])
        private set
    var fingerDraws by mutableStateOf(app.settings.fingerDraws())
        private set

    /** Где плашка инструментов: у правого края или у левого, и на какой высоте (0 — сверху, 1 — снизу). */
    var railRight by mutableStateOf(app.settings.railRight())
        private set
    var railY by mutableStateOf(app.settings.railY())
        private set

    /** Панель кистей перенесена наверх (где название, где много свободного места). */
    var toolBarTop by mutableStateOf(app.settings.toolBarTop())
        private set

    fun toggleToolBarPosition() {
        toolBarTop = !toolBarTop
        app.settings.setToolBarTop(toolBarTop)
        showHint(if (toolBarTop) "Кисти перенесены наверх" else "Кисти перенесены сбоку")
    }

    /** Режим фиксации: перемещение строго двумя пальцами, а зум закреплён. */
    var lockMode by mutableStateOf(false)
        private set

    fun toggleLockMode() {
        lockMode = !lockMode
        ink.lockMode = lockMode
        showHint(if (lockMode) "Фиксация листа: перемещение 2 пальцами, зум закреплён" else "Свободное перемещение и зум")
    }

    /** Полноэкранный режим чтения конспекта: без боковых плашек, плавающие элементы и кнопка наставника. */
    var fullScreen by mutableStateOf(false)
        private set

    fun toggleFullScreen() {
        fullScreen = !fullScreen
    }

    fun attachContext(context: android.content.Context) {
        ink.updateContext(context)
    }

    fun moveRail(right: Boolean, y: Float) {
        railRight = right
        railY = y
        app.settings.setRail(right, y)
    }

    /** Перо задержали — сейчас работает временный ластик (подсвечиваем его в палитре). */
    var tempEraser by mutableStateOf(false)
        private set

    /** Выделение лассо: рамка на экране и сколько выделено. */
    var selection by mutableStateOf<RectF?>(null)
        private set
    var selectionCount by mutableIntStateOf(0)
        private set

    /** Скопированное лассо (можно вставить на другую страницу). */
    private var clipboard: Pair<List<Stroke>, List<TextBox>>? = null
    var canPaste by mutableStateOf(false)
        private set

    /** Короткая подсказка поверх листа: что произошло или как пользоваться инструментом. */
    var hint by mutableStateOf<String?>(null)
        private set
    var hintSeq by mutableIntStateOf(0)
        private set

    /** Просят напечатать текст: место на странице и текст, который правят (или null — новый). */
    var textRequest by mutableStateOf<Triple<Float, Float, TextBox?>?>(null)
        private set

    /** Сам проверять страницу, когда студент перестал писать. */
    var autoCheck by mutableStateOf(app.settings.notebookAutoCheck())
        private set

    /** Контрольная: до какого момента идёт (0 — не идёт). Наставник молчит, пока её не сдадут. */
    var examEndsAt by mutableLongStateOf(0L)
        private set
    val examActive: Boolean get() = examEndsAt > 0L
    private var examJob: Job? = null

    private val undos = HashMap<Long, UndoStack>()
    private var saveJob: Job? = null
    private var autoJob: Job? = null
    private var lastAutoAt = 0L

    val mentor = MentorSession(app, scope, bindToSettings = false) { conv -> rememberConversation(conv) }

    data class MentorChatMessage(
        val id: Long = System.currentTimeMillis(),
        val isUser: Boolean,
        val text: String,
        val kind: AskKind = AskKind.USER,
        val answer: AnswerUi? = null,
        val timestamp: Long = System.currentTimeMillis(),
    )

    /** История сообщений чата наставника в рамках текущей сессии тетради. */
    val chatMessages = mutableStateListOf<MentorChatMessage>()

    /** Статус предложенных наставником действий на листе: "PENDING", "APPROVED", "DISMISSED". */
    val actionStates = mutableStateMapOf<Long, String>()

    /** Наставник прямо сейчас сам пишет или чертит на листе (после разрешения). */
    var mentorWriting by mutableStateOf(false)
        private set

    private var closed = false
    private var lastChatId = 0L

    @Synchronized
    private fun nextChatId(): Long {
        val next = maxOf(System.currentTimeMillis() * 10L, lastChatId + 1L)
        lastChatId = next
        return next
    }

    fun clearChat() {
        chatMessages.clear()
        actionStates.clear()
        mentor.newTask()
        ink.marks = emptyList()
        rejectedMarks.clear()
        app.getSharedPreferences("notebook_chats", 0).edit().remove(notebookId.toString()).apply()
        app.appScope.launch(Dispatchers.IO) {
            nbs.store.deleteChat(notebookId)
        }
    }

    fun saveChatToDisk() {
        val msgs = chatMessages.toList()
        val states = actionStates.toMap()
        app.appScope.launch(Dispatchers.IO) {
            if (msgs.isEmpty()) {
                nbs.store.deleteChat(notebookId)
            } else {
                val jsonStr = SavedChatCodec.encode(msgs, states)
                nbs.store.saveChat(notebookId, jsonStr)
            }
        }
    }

    init {
        scope.launch {
            mentor.state.collect { ui ->
                val ans = ui.answer ?: return@collect
                if (ans.kind == AskKind.WATCH) return@collect
                val idx = chatMessages.indexOfLast { !it.isUser && it.answer?.question == ans.question && (it.answer.seq == ans.seq || it.answer.streaming) }
                if (idx >= 0) {
                    chatMessages[idx] = chatMessages[idx].copy(answer = ans)
                } else if (ans.marks.isNotEmpty() || ans.actions.isNotEmpty() || ans.errorText != null) {
                    chatMessages.add(MentorChatMessage(id = nextChatId(), isUser = false, text = "", kind = ans.kind, answer = ans))
                }
                refreshMarks(ans)
                if (!ans.streaming) {
                    if (ans.actions.isNotEmpty() && actionStates[ans.seq] == null) {
                        if (!app.settings.mentorRequirePermission()) {
                            approveActions(ans)
                        } else {
                            actionStates[ans.seq] = "PENDING"
                        }
                    }
                    saveChatToDisk()
                }
            }
        }
    }

    /** К какой странице относится ответ наставника и во сколько раз снимок меньше страницы. */
    private var answerPageId = 0L
    private var answerScale = 1f

    val page: InkPage? get() = pages.getOrNull(index)

    suspend fun load() {
        val m = withContext(Dispatchers.IO) { nbs.store.meta(notebookId) } ?: return
        val ps = withContext(Dispatchers.IO) { nbs.store.loadPages(m) }
        meta = m
        pages = ps
        val convId = app.getSharedPreferences("notebook_chats", 0).getLong(notebookId.toString(), m.conversationId)
        if (convId > 0) mentor.useConversation(convId)
        answerPageId = 0L
        ink.marks = emptyList()
        rejectedMarks.clear()
        val savedRaw = withContext(Dispatchers.IO) { nbs.store.loadChat(notebookId) }
        chatMessages.clear()
        actionStates.clear()
        if (!savedRaw.isNullOrBlank()) {
            val (restored, restoredStates) = SavedChatCodec.decodeWithStates(savedRaw)
            val seenIds = HashSet<Long>(restored.size)
            for (msg in restored) {
                val uid = if (seenIds.add(msg.id)) msg.id else nextChatId()
                lastChatId = maxOf(lastChatId, uid)
                chatMessages.add(if (uid == msg.id) msg else msg.copy(id = uid))
            }
            actionStates.putAll(restoredStates)
            val lastBot = chatMessages.lastOrNull { !it.isUser && it.answer != null }
            lastBot?.answer?.let { ans -> refreshMarks(ans) }
        } else if (convId > 0) {
            var lastQuestion = ""
            val dbMsgs = withContext(Dispatchers.IO) {
                app.db.messages().forConversation(mentor.state.value.conversationId)
            }
            dbMsgs.forEach { message ->
                val user = message.role == "user"
                if (user) lastQuestion = message.text
                lastChatId = maxOf(lastChatId, message.id)
                chatMessages.add(
                    MentorChatMessage(
                        id = message.id,
                        isUser = user,
                        text = message.text,
                        timestamp = message.createdAt,
                        answer = if (user) null else AnswerUi(
                            seq = message.id,
                            kind = AskKind.USER,
                            question = lastQuestion,
                            streaming = false,
                            marks = listOf(Mark(MarkKind.NOTE, null, message.text)),
                        ),
                    )
                )
            }
        }
        mentor.extraSystem = PracticePrompts.variantForMentor(m.tasks)
        applyTool()
        // Открываем целевую страницу ДЗ, иначе — последнюю страницу, на которой вносились изменения.
        val start = NotebookStart.take(notebookId)?.let { id -> ps.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
            ?: m.lastEditedPageId.takeIf { it != 0L }?.let { id -> ps.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
            ?: 0
        showPage(start)
        loaded = true
        if (BuildConfig.DEBUG) {
            NotebookVisionDemoStart.take(notebookId)?.let { mode ->
                scope.launch { applyDebugVisionDemoWhenReady(mode) }
            }
        }
    }

    fun showPage(i: Int) {
        val p = pages.getOrNull(i) ?: return
        flushSave()
        // Листаем с анимацией: вперёд — новая страница въезжает справа.
        val direction = if (!loaded || i == index) 0 else if (i > index) 1 else -1
        index = i
        val stack = undos.getOrPut(p.id) { UndoStack() }
        scope.launch {
            val bg = nbs.background(notebookId, p)
            if (pages.getOrNull(index)?.id == p.id) {
                ink.setPage(p, bg, stack, direction)
                detectedPageLines = extractPageLineRects(p)
                refreshMarks()
            }
        }
    }

    // ---------------- страницы ----------------

    fun addPage(paper: Paper) {
        val m = meta ?: return
        val p = InkPage(nbs.store.newId(), paper)
        scope.launch {
            val nm = withContext(Dispatchers.IO) { nbs.store.addPage(m, p, index) }
            meta = nm
            pages = pages.toMutableList().apply { add(index + 1, p) }
            showPage(index + 1)
        }
    }

    fun addImages(uris: List<Uri>) = append { m -> nbs.appendImages(m, uris) }

    fun addPdf(uri: Uri) = append { m -> nbs.appendPdf(m, uri) }

    private fun append(block: suspend (NotebookMeta) -> NotebookMeta) {
        val m = meta ?: return
        scope.launch {
            flushSaveNow()
            val nm = block(m)
            val added = nm.pages.size - m.pages.size
            if (added <= 0) return@launch
            meta = nm
            pages = withContext(Dispatchers.IO) { nbs.store.loadPages(nm) }
            showPage(pages.size - added)
        }
    }

    /** Копия текущей страницы сразу после неё (вместе с подложкой). */
    fun duplicatePage() {
        val m = meta ?: return
        val p = page ?: return
        scope.launch {
            flushSaveNow()
            val copy = withContext(Dispatchers.IO) {
                val id = nbs.store.newId()
                val bg = p.background?.let { name ->
                    val newName = NotebookStore.backgroundName(id)
                    runCatching { nbs.store.background(m.id, name).copyTo(nbs.store.background(m.id, newName), overwrite = true) }
                        .map { newName }.getOrNull()
                }
                InkPage(id, p.paper, bg, p.height, p.strokes.toMutableList(), p.texts.toMutableList())
            }
            val nm = withContext(Dispatchers.IO) { nbs.store.addPage(m, copy, index) }
            meta = nm
            pages = pages.toMutableList().apply { add(index + 1, copy) }
            showPage(index + 1)
            showHint("Страница скопирована")
        }
    }

    /** Переставить страницу [from] на место [to]. */
    fun movePage(from: Int, to: Int) {
        val m = meta ?: return
        if (from !in pages.indices || to !in pages.indices || from == to) return
        val current = page?.id
        val list = pages.toMutableList().apply { add(to, removeAt(from)) }
        pages = list
        index = list.indexOfFirst { it.id == current }.coerceAtLeast(0)
        val nm = m.copy(pages = list.map { it.id }, updatedAt = System.currentTimeMillis())
        meta = nm
        scope.launch(Dispatchers.IO) { nbs.store.saveMeta(nm) }
    }

    /** Миниатюра страницы для обзора тетради. */
    suspend fun thumbnail(p: InkPage, widthPx: Int): Bitmap = withContext(Dispatchers.Default) {
        val isWeak = DeviceProfile.isWeakDevice(app)
        val cfg = if (isWeak) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        val bg = nbs.background(notebookId, p)
        PageRenderer.render(p, bg, widthPx, cfg)
    }

    fun deletePage() {
        val m = meta ?: return
        val p = page ?: return
        if (pages.size <= 1) return
        scope.launch {
            val nm = withContext(Dispatchers.IO) { nbs.store.removePage(m, p.id) }
            meta = nm
            pages = pages.filter { it.id != p.id }
            showPage(index.coerceAtMost(pages.lastIndex))
        }
    }

    fun setPaper(paper: Paper) {
        val p = page ?: return
        p.paper = paper
        p.touch()
        ink.pageChanged()
        onPageEdited()
    }

    /** Одна бумага на всю тетрадь: все страницы без подложки (фото и PDF не трогаем). */
    fun setPaperAll(paper: Paper) {
        val m = meta ?: return
        pages.filter { it.background == null }.forEach { it.paper = paper; it.touch() }
        ink.pageChanged()
        scope.launch {
            withContext(NonCancellable + Dispatchers.IO) {
                pages.forEach { nbs.store.savePage(m.id, it) }
                nbs.store.saveMeta(m.copy(paper = paper.name, updatedAt = System.currentTimeMillis()).also { meta = it })
            }
        }
    }

    /** Масштаб листа: 1 — по ширине экрана. */
    var zoom by mutableStateOf(1f)
        private set

    override fun onZoomChanged(zoom: Float) {
        this.zoom = zoom
    }

    fun fitWidth() = ink.animateFitWidth()

    fun rename(title: String) {
        val m = meta ?: return
        scope.launch { meta = withContext(Dispatchers.IO) { nbs.store.rename(m, title) } }
    }

    fun setSubject(subject: String) {
        val m = meta ?: return
        scope.launch { meta = withContext(Dispatchers.IO) { nbs.store.setSubject(m, subject) } }
    }

    fun clearPage() = ink.clearPage()
    fun undo() = ink.undo()
    fun redo() = ink.redo()

    // ---------------- инструменты ----------------

    fun pickTool(t: EditTool) {
        if (editTool == t) return
        editTool = t
        applyTool()
    }

    fun pickPen(color: Int) {
        penColor = color
        app.settings.setPenColor(color)
        if (editTool != EditTool.SHAPE && editTool != EditTool.TEXT) editTool = EditTool.PEN
        applyTool()
    }

    fun pickMarker(color: Int) {
        markerColor = color
        editTool = EditTool.HIGHLIGHTER
        applyTool()
    }

    fun changePenWidth(w: Float) {
        penWidth = w
        applyTool()
    }

    fun changeMarkerWidth(w: Float) {
        markerWidth = w
        applyTool()
    }

    fun choosePartialEraser(on: Boolean) {
        partialEraser = on
        applyTool()
    }

    fun pickShape(k: ShapeKind) {
        shapeKind = k
        editTool = EditTool.SHAPE
        applyTool()
    }

    fun pickTextSize(size: Float) {
        textSize = size
    }

    fun toggleFinger() {
        fingerDraws = !fingerDraws
        app.settings.setFingerDraws(fingerDraws)
        applyTool()
        showHint(if (fingerDraws) "Пальцем пишешь, двумя — двигаешь лист" else "Пальцем двигаешь лист, пишешь пером")
    }

    fun showHint(text: String) {
        hint = text
        hintSeq++
    }

    // Выделение.
    fun deleteSelection() = ink.deleteSelection()
    fun duplicateSelection() = ink.duplicateSelection()
    fun recolorSelection(c: Int) = ink.recolorSelection(c)
    fun scaleSelection(factor: Float) = ink.scaleSelection(factor)
    fun clearSelection() = ink.clearSelection()

    /** Задать вопрос наставнику конкретно по выделенной лассо области (штрихам, формулам, задачам). */
    fun askSelection(customQuestion: String? = null) {
        val bmp = ink.selectionSnapshot() ?: return
        if (app.settings.notReadyReason() != null) return
        val question = customQuestion ?: "На первом изображении — выделенный лассо фрагмент, на остальных — страницы всего конспекта. Объясни выделенный фрагмент относительно всего конспекта: как он связан с общим решением, всё ли верно и что делать дальше?"
        autoJob?.cancel()
        val now = System.currentTimeMillis()
        val userMsgId = nextChatId()
        val botMsgId = nextChatId()
        chatMessages.add(MentorChatMessage(id = userMsgId, isUser = true, text = customQuestion ?: "Объясни выделенное в контексте конспекта", kind = AskKind.USER, timestamp = now))
        chatMessages.add(
            MentorChatMessage(
                id = botMsgId,
                isUser = false,
                text = "",
                kind = AskKind.USER,
                answer = AnswerUi(seq = botMsgId, kind = AskKind.USER, question = question, streaming = true, status = "Изучаю выделенное в контексте конспекта…"),
                timestamp = now,
            )
        )
        saveChatToDisk()
        val p = page ?: return
        answerPageId = p.id
        answerScale = 1f
        showHint("Вопрос по выделенному отправлен")
        val stream = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val jpeg = stream.toByteArray()
        val w = bmp.width
        val h = bmp.height
        bmp.recycle()
        val allPages = pages.toList()
        val sel = selectedContextPages
        mentor.ask(question, AskKind.USER) {
            val geom = ShotGeometry(IRect(0, 0, w, h), w, h, 0, null, null)
            ShotGeometry.last = geom
            // Отправляем полную текущую страницу и остальные выбранные страницы конспекта для полного контекста
            val ctxPages = allPages
                .filter { it.id == p.id || sel == null || it.id in sel }
                .take(5)
                .map { contextImage(it) }
            ShotResult.Ok(jpeg, geom, null, ctxPages)
        }
    }

    fun copySelection() {
        val (s, t) = ink.selectionCopy()
        if (s.isEmpty() && t.isEmpty()) return
        clipboard = s to t
        canPaste = true
        showHint("Скопировано — можно вставить на любую страницу")
    }

    fun paste() {
        val c = clipboard ?: return
        ink.paste(c.first, c.second)
        editTool = EditTool.LASSO
    }

    // Текст.
    fun submitText(text: String) {
        val r = textRequest ?: return
        textRequest = null
        ink.putText(r.first, r.second, text, r.third?.size ?: textSize, r.third?.color ?: penColor, r.third)
    }

    fun cancelText() {
        textRequest = null
    }

    fun toggleAutoCheck() {
        autoCheck = !autoCheck
        app.settings.setNotebookAutoCheck(autoCheck)
        if (!autoCheck) {
            autoJob?.cancel()
            autoCheckProgress = 0f
        }
    }

    private fun applyTool() {
        ink.editTool = editTool
        ink.color = if (editTool == EditTool.HIGHLIGHTER) markerColor else penColor
        ink.penWidth = penWidth
        ink.markerWidth = markerWidth
        ink.partialEraser = partialEraser
        ink.shapeKind = shapeKind
        ink.fingerDraws = fingerDraws
    }

    override fun onSelectionChanged(screen: RectF?, count: Int) {
        selection = screen
        selectionCount = count
    }

    override fun onTextRequest(x: Float, y: Float, existing: TextBox?) {
        textRequest = Triple(x, y, existing)
    }

    override fun onHint(text: String) = showHint(text)

    override fun onTemporaryEraser(active: Boolean) {
        tempEraser = active
    }

    // ---------------- сохранение ----------------

    override fun onUndoStateChanged(canUndo: Boolean, canRedo: Boolean) {
        this.canUndo = canUndo
        this.canRedo = canRedo
    }

    /** Пишут прямо сейчас — палитра становится прозрачнее, а зрение наставника следит за пером. */
    var writing by mutableStateOf(false)
        private set
    var penViewX by mutableFloatStateOf(0f)
        private set
    var penViewY by mutableFloatStateOf(0f)
        private set
    var penNormX by mutableFloatStateOf(0.5f)
        private set
    var penNormY by mutableFloatStateOf(0.35f)
        private set
    var autoCheckProgress by mutableFloatStateOf(0f)
        private set
    var detectedPageLines by mutableStateOf<List<RectF>>(emptyList())
        private set
    var debugVisionAnswer by mutableStateOf<AnswerUi?>(null)
        private set
    var debugSilentOkAt by mutableLongStateOf(0L)
        private set
    var debugVisionFocusIdx by mutableStateOf<Int?>(null)
        private set
    var debugForcePanel by mutableStateOf<Boolean?>(null)
        private set
    private var writingJob: Job? = null

    suspend fun applyDebugVisionDemoWhenReady(mode: String) {
        if (!BuildConfig.DEBUG) return
        for (i in 0 until 80) {
            if (ink.width > 0 && ink.height > 0 && ink.page != null) break
            delay(50L)
        }
        delay(120L)
        applyDebugVisionDemo(mode)
    }

    fun applyDebugVisionDemo(mode: String) {
        if (!BuildConfig.DEBUG) return
        val p = page ?: return
        detectedPageLines = extractPageLineRects(p)
        writingJob?.cancel()
        autoJob?.cancel()
        writing = false
        autoCheckProgress = 0f
        debugVisionAnswer = null
        debugSilentOkAt = 0L
        debugVisionFocusIdx = null

        val lines = detectedPageLines
        val targetLine = lines.getOrNull(2) ?: lines.lastOrNull()
        val viewPt = FloatArray(2)
        if (targetLine != null && ink.mapPagePointToView(targetLine.right - 20f, targetLine.centerY(), viewPt)) {
            penViewX = viewPt[0]
            penViewY = viewPt[1]
            penNormX = ((targetLine.right - 20f) / PAGE_W).coerceIn(0f, 1f)
            penNormY = (targetLine.centerY() / p.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
        }

        when (mode.lowercase()) {
            "writing" -> {
                debugForcePanel = false
                writing = true
            }
            "autocheck" -> {
                debugForcePanel = false
                autoCheck = true
                autoCheckProgress = 0.62f
            }
            "reading" -> {
                debugForcePanel = false
                debugVisionAnswer = AnswerUi(
                    seq = 9001L,
                    kind = AskKind.QUICK,
                    question = "Проверь ход решения",
                    streaming = true,
                    status = "Смотрю на экран…",
                )
            }
            "analyzing", "line_focus" -> {
                val showPanel = mode.lowercase() == "analyzing"
                debugForcePanel = showPanel
                debugVisionFocusIdx = 1.coerceAtMost((lines.size - 1).coerceAtLeast(0))
                val ans = AnswerUi(
                    seq = 9002L,
                    kind = AskKind.QUICK,
                    question = "Проверь решение предела",
                    streaming = true,
                    status = "Думаю…",
                )
                debugVisionAnswer = ans
                if (showPanel) {
                    chatMessages.clear()
                    chatMessages.add(MentorChatMessage(id = 9001L, isUser = true, text = "Проверь решение предела"))
                    chatMessages.add(MentorChatMessage(id = 9002L, isUser = false, text = "", answer = ans))
                }
            }
        }
    }

    override fun onWritingStarted() {
        autoJob?.cancel()
        autoCheckProgress = 0f
        refreshWritingDecay()
    }

    override fun onPenMotion(viewX: Float, viewY: Float, pageNormX: Float, pageNormY: Float) {
        penViewX = viewX
        penViewY = viewY
        penNormX = pageNormX
        penNormY = pageNormY
        refreshWritingDecay()
    }

    private fun refreshWritingDecay() {
        writing = true
        writingJob?.cancel()
        writingJob = scope.launch {
            delay(1400)
            writing = false
        }
    }

    override fun onPageEdited() {
        page?.let { detectedPageLines = extractPageLineRects(it) }
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            flushSaveNow()
        }
        scheduleAutoCheck()
    }

    private fun flushSave() {
        if (saveJob?.isActive == true) {
            saveJob?.cancel()
            scope.launch { flushSaveNow() }
        }
    }

    private suspend fun flushSaveNow() {
        val m = meta ?: return
        val p = page ?: return
        withContext(NonCancellable + Dispatchers.IO) {
            nbs.store.savePage(m.id, p)
            val nm = m.copy(updatedAt = System.currentTimeMillis(), lastEditedPageId = p.id)
            nbs.store.saveMeta(nm)
            meta = nm
        }
    }

    /** Уходим из тетради: сохранить страницу и обновить превью. Если конспект пустой — удаляем его. */
    fun close() {
        if (closed) return
        closed = true
        autoJob?.cancel()
        autoCheckProgress = 0f
        examJob?.cancel()
        mentor.cancel()
        mentor.dismiss()
        answerPageId = 0L
        ink.marks = emptyList()
        rejectedMarks.clear()
        cachedMainShot = null
        contextCache.clear()
        cleanPageCache.clear()
        if (!loaded) return
        val m = meta ?: return
        // Пользовательское требование: если в тетради ничего не было сделано — удаляем её, не захламляя список
        val isEmpty = pages.all { it.strokes.isEmpty() && it.texts.isEmpty() && it.background == null }
        if (isEmpty && m.tasks.isEmpty() && chatMessages.isEmpty() && mentor.state.value.conversationId == 0L) {
            app.appScope.launch(Dispatchers.IO) {
                runCatching { nbs.store.delete(notebookId) }
            }
            return
        }
        val first = pages.firstOrNull() ?: return
        app.appScope.launch {
            flushSaveNow()
            saveChatToDisk()
            runCatching { nbs.saveThumb(m, first) }
        }
    }

    private fun rememberConversation(conv: Long) {
        app.getSharedPreferences("notebook_chats", 0).edit().putLong(notebookId.toString(), conv).commit()
        val current = meta ?: return
        val updated = current.copy(conversationId = conv)
        nbs.store.saveMeta(updated)
        meta = updated
    }

    /** null означает все страницы, включая добавленные позже. */
    var selectedContextPages by mutableStateOf<Set<Long>?>(null)
        private set

    fun selectAllContextPages() { selectedContextPages = null }
    fun selectNoContextPages() { selectedContextPages = emptySet() }
    fun selectContextPage(id: Long, selected: Boolean) {
        val ids = selectedContextPages ?: pages.map { it.id }.toSet()
        selectedContextPages = if (selected) ids + id else ids - id
    }

    // ---------------- наставник ----------------

    fun ask(question: String, kind: AskKind) {
        if (app.settings.notReadyReason() != null) return
        autoJob?.cancel()
        autoCheckProgress = 0f
        page?.let { detectedPageLines = extractPageLineRects(it) }
        if (kind != AskKind.WATCH) {
            val now = System.currentTimeMillis()
            val userMsgId = nextChatId()
            val botMsgId = nextChatId()
            chatMessages.add(MentorChatMessage(id = userMsgId, isUser = true, text = question, kind = kind, timestamp = now))
            chatMessages.add(
                MentorChatMessage(
                    id = botMsgId,
                    isUser = false,
                    text = "",
                    kind = kind,
                    answer = AnswerUi(seq = botMsgId, kind = kind, question = question, streaming = true, status = "Смотрю на экран…"),
                    timestamp = now,
                )
            )
            saveChatToDisk()
        }
        val pages = this.pages.size
        mentor.ask(question, kind) { shot(pages) }
    }

    // ---------------- действия наставника на листе (строго с разрешения) ----------------

    fun approveActions(answer: AnswerUi) {
        if (answer.actions.isEmpty()) return
        actionStates[answer.seq] = "APPROVED"
        saveChatToDisk()
        scope.launch {
            for (action in answer.actions) {
                executeMentorAction(action, answer.geometry)
            }
            showHint("Готово: наставник обновил конспект (можно отменить)")
        }
    }

    fun dismissActions(answer: AnswerUi) {
        if (answer.actions.isEmpty()) return
        actionStates[answer.seq] = "DISMISSED"
        saveChatToDisk()
    }

    /** Вручную перенести любую текстовую заметку/подсказку наставника прямо на лист тетради. */
    fun writeMarkToPage(mark: Mark, geometry: ShotGeometry?) {
        val text = listOfNotNull(mark.text.takeIf { it.isNotBlank() }, mark.detail?.takeIf { it.isNotBlank() })
            .joinToString("\n")
        if (text.isBlank()) return
        val action = MentorPageAction(
            id = "mark_${System.currentTimeMillis()}",
            kind = MentorActionKind.WRITE_TEXT,
            text = text,
            box = mark.box,
            lines = mark.lines,
            after = mark.after,
        )
        scope.launch {
            executeMentorAction(action, geometry)
            showHint("Заметка добавлена на лист")
        }
    }

    private suspend fun addPageAwait(paper: Paper) {
        val m = meta ?: return
        val p = InkPage(nbs.store.newId(), paper)
        val nm = withContext(Dispatchers.IO) { nbs.store.addPage(m, p, index) }
        meta = nm
        val nextIdx = index + 1
        pages = pages.toMutableList().apply { add(nextIdx.coerceIn(0, pages.size), p) }
        val bg = nbs.background(notebookId, p)
        index = nextIdx.coerceIn(0, pages.lastIndex)
        ink.setPage(p, bg, undos.getOrPut(p.id) { UndoStack() }, 1)
        detectedPageLines = extractPageLineRects(p)
    }

    private fun actionAnchorPageRect(action: MentorPageAction, geometry: ShotGeometry?, p: InkPage): RectF? {
        val g = geometry
        if (g != null && action.box != null) {
            val s = g.toScreen(action.box)
            return RectF(s.left * answerScale, s.top * answerScale, s.right * answerScale, s.bottom * answerScale)
        }
        val lines = detectedPageLines.ifEmpty { extractPageLineRects(p) }
        action.lines?.firstOrNull()?.let { num ->
            return lines.getOrNull(num - 1)
        }
        action.after?.let { num ->
            return lines.getOrNull(num - 1)
        }
        return null
    }

    private suspend fun animateMentorPenOver(pageRect: RectF, pageHeight: Float) {
        if (pageRect.isEmpty) return
        mentorWriting = true
        val viewPt = FloatArray(2)
        val steps = 8
        try {
            for (step in 0..steps) {
                val frac = step.toFloat() / steps
                val px = pageRect.left + pageRect.width() * (0.12f + 0.72f * frac)
                val py = pageRect.centerY()
                if (ink.mapPagePointToView(px, py, viewPt)) {
                    penViewX = viewPt[0]
                    penViewY = viewPt[1]
                    penNormX = (px / PAGE_W).coerceIn(0f, 1f)
                    penNormY = (py / pageHeight.coerceAtLeast(1f)).coerceIn(0f, 1f)
                }
                delay(42L)
            }
        } finally {
            mentorWriting = false
        }
    }

    private suspend fun executeMentorAction(action: MentorPageAction, geometry: ShotGeometry?) {
        val p = page ?: return
        val anchor = actionAnchorPageRect(action, geometry, p)
        when (action.kind) {
            MentorActionKind.WRITE_TEXT -> {
                val fontSize = if (p.paper == Paper.LINED) 26f else 24f
                val (strokes, texts, slot) = MentorNotebookWriter.buildWriteAddition(p, action.text, anchor, penColor, fontSize, AndroidMathMetrics)
                if (texts.isNotEmpty() || strokes.isNotEmpty()) {
                    animateMentorPenOver(slot, p.height)
                    ink.applyMentorAdditions(strokes, texts, slot)
                    page?.let { detectedPageLines = extractPageLineRects(it) }
                }
            }
            MentorActionKind.DIAGRAM -> {
                val (strokes, texts, rect) = MentorNotebookWriter.buildDiagramAddition(p, action.text, anchor, penColor, AndroidMathMetrics)
                if (strokes.isNotEmpty() || texts.isNotEmpty()) {
                    animateMentorPenOver(rect, p.height)
                    ink.applyMentorAdditions(strokes, texts, rect)
                    page?.let { detectedPageLines = extractPageLineRects(it) }
                }
            }
            MentorActionKind.DRAW_SHAPE -> {
                val dk = action.shape ?: DrawingKind.LINE
                val (strokes, rect) = MentorNotebookWriter.buildShapeAddition(p, dk, anchor, penColor, penWidth)
                if (strokes.isNotEmpty()) {
                    animateMentorPenOver(rect, p.height)
                    ink.applyMentorAdditions(strokes, emptyList(), rect)
                    page?.let { detectedPageLines = extractPageLineRects(it) }
                }
            }
            MentorActionKind.HIGHLIGHT -> {
                val (strokes, rect) = MentorNotebookWriter.buildHighlightAddition(p, anchor, markerColor)
                if (strokes.isNotEmpty()) {
                    animateMentorPenOver(rect, p.height)
                    ink.applyMentorAdditions(strokes, emptyList(), rect)
                }
            }
            MentorActionKind.ERASE -> {
                val region = anchor ?: return
                animateMentorPenOver(region, p.height)
                ink.eraseInPageRect(region)
                page?.let { detectedPageLines = extractPageLineRects(it) }
            }
            MentorActionKind.CLEAR_PAGE -> ink.clearPage()
            MentorActionKind.ADD_PAGE -> {
                val paper = action.paper?.let { runCatching { Paper.valueOf(it) }.getOrNull() } ?: (p.paper)
                addPageAwait(paper)
            }
            MentorActionKind.GOTO_PAGE -> {
                val targetIdx = ((action.targetPage ?: 1) - 1).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                showPage(targetIdx)
                delay(180L)
            }
            MentorActionKind.DUPLICATE_PAGE -> {
                duplicatePage()
                delay(180L)
            }
            MentorActionKind.DELETE_PAGE -> {
                deletePage()
                delay(180L)
            }
            MentorActionKind.SET_PAPER -> {
                val paper = action.paper?.let { runCatching { Paper.valueOf(it) }.getOrNull() } ?: return
                if (action.applyAllPages) setPaperAll(paper) else setPaper(paper)
            }
            MentorActionKind.RENAME_NOTEBOOK -> if (action.text.isNotBlank()) rename(action.text)
            MentorActionKind.SET_SUBJECT -> if (action.text.isNotBlank()) setSubject(action.text)
        }
    }

    // ---------------- контрольная ----------------

    /** Начать контрольную на [minutes] минут: подсказок нет, по времени работа сдаётся сама. */
    fun startExam(minutes: Int) {
        autoJob?.cancel()
        mentor.dismiss()
        examEndsAt = System.currentTimeMillis() + minutes * 60_000L
        examJob?.cancel()
        examJob = scope.launch {
            delay(minutes * 60_000L)
            submitExam()
        }
    }

    /** Сдать работу: наставник проверяет всё и ставит оценку. */
    fun submitExam() {
        examJob?.cancel()
        examEndsAt = 0L
        ask(Prompts.EXAM_PROMPT, AskKind.USER)
    }

    fun cancelExam() {
        examJob?.cancel()
        examEndsAt = 0L
    }

    /** Студент перестал писать — через паузу тихо проверяем страницу (только если включено и не идёт контрольная). */
    private fun scheduleAutoCheck() {
        autoJob?.cancel()
        autoCheckProgress = 0f
        if (!autoCheck || examActive || app.settings.notReadyReason() != null) return
        autoJob = scope.launch {
            val started = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - started
                if (elapsed >= AUTO_PAUSE_MS) break
                autoCheckProgress = (elapsed.toFloat() / AUTO_PAUSE_MS).coerceIn(0.02f, 1f)
                delay(80L)
            }
            autoCheckProgress = 0f
            if (System.currentTimeMillis() - lastAutoAt < AUTO_GAP_MS || mentor.isBusy) return@launch
            // Ответ на вопрос студента не перебиваем.
            val a = mentor.state.value.answer
            if (a != null && a.kind != AskKind.WATCH) return@launch
            lastAutoAt = System.currentTimeMillis()
            ask(Prompts.WATCH_PROMPT, AskKind.WATCH)
        }
    }

    /** Извлекает упорядоченные строки записей/текста в координатах страницы для подсветки чтения и анализа. */
    private fun extractPageLineRects(p: InkPage): List<RectF> {
        val items = ArrayList<RectF>(p.strokes.size + p.texts.size)
        for (s in p.strokes) {
            val b = s.bounds()
            items += RectF(b[0], b[1], b[2], b[3])
        }
        for (t in p.texts) {
            val b = t.bounds()
            items += RectF(b[0], b[1], b[2], b[3])
        }
        if (items.isEmpty()) return emptyList()
        items.sortBy { it.top }
        val lines = ArrayList<RectF>()
        for (r in items) {
            val prev = lines.lastOrNull()
            if (prev != null && r.top <= prev.bottom + 14f && r.bottom >= prev.top - 14f) {
                prev.left = minOf(prev.left, r.left)
                prev.top = minOf(prev.top, r.top)
                prev.right = maxOf(prev.right, r.right)
                prev.bottom = maxOf(prev.bottom, r.bottom)
            } else {
                lines += RectF(r)
            }
        }
        for (line in lines) {
            line.left = (line.left - 8f).coerceIn(0f, PAGE_W)
            line.top = (line.top - 6f).coerceIn(0f, p.height)
            line.right = (line.right + 8f).coerceIn(line.left + 8f, PAGE_W)
            line.bottom = (line.bottom + 6f).coerceIn(line.top + 8f, p.height)
        }
        return lines
    }

    private data class CachedContextPage(val pageId: Long, val version: Long, val jpeg: ByteArray)
    private val contextCache = LinkedHashMap<Long, CachedContextPage>(16, 0.75f, true)
    private var contextCacheBytes = 0

    private data class CachedMainShot(
        val pageId: Long,
        val version: Long,
        val jpeg: ByteArray,
        val geometry: ShotGeometry,
        val scale: Float,
    )
    private var cachedMainShot: CachedMainShot? = null

    private suspend fun contextImage(page: InkPage): ByteArray {
        val cached = contextCache[page.id]
        if (cached != null && cached.pageId == page.id && cached.version == page.version) return cached.jpeg
        val version = page.version
        val jpeg = nbs.renderJpeg(notebookId, page, ImageUtils.SMALL_PIXELS, 78)
        contextCache.remove(page.id)?.let { contextCacheBytes -= it.jpeg.size }
        if (version == page.version && jpeg.size <= CONTEXT_CACHE_BYTES) {
            while (contextCacheBytes + jpeg.size > CONTEXT_CACHE_BYTES && contextCache.isNotEmpty()) {
                val oldest = contextCache.entries.iterator()
                contextCacheBytes -= oldest.next().value.jpeg.size
                oldest.remove()
            }
            contextCache[page.id] = CachedContextPage(page.id, version, jpeg)
            contextCacheBytes += jpeg.size
        }
        return jpeg
    }

    /** Снимок текущей страницы и все остальные страницы тетради. Кэширует неизменённые листы. */
    private suspend fun shot(contextLimit: Int): ShotResult {
        val p = page ?: return ShotResult.Failed("Страница не открыта")
        flushSaveNow()
        answerPageId = p.id
        rejectedMarks.clear()
        val main = cachedMainShot
        val (jpeg, geometry) = if (main != null && main.pageId == p.id && main.version == p.version) {
            answerScale = main.scale
            ShotGeometry.last = main.geometry
            main.jpeg to main.geometry
        } else {
            val bmp = nbs.render(notebookId, p, ImageUtils.MAX_PIXELS)
            val prepared = try {
                withContext(Dispatchers.Default) { ImageUtils.prepare(bmp, Rect(0, 0, bmp.width, bmp.height), numberLines = true) }
            } finally {
                bmp.recycle()
            }
            val r = prepared.screenRect
            val scale = PAGE_W / r.width().coerceAtLeast(1)
            answerScale = scale
            val geom = ShotGeometry(IRect(r.left, r.top, r.right, r.bottom), prepared.width, prepared.height, prepared.gutter, prepared.lines, prepared.luma)
            ShotGeometry.last = geom
            geom.lines?.lines?.takeIf { it.isNotEmpty() }?.let { tl ->
                detectedPageLines = tl.map { l ->
                    RectF(l.left * scale, l.top * scale, l.right * scale, l.bottom * scale)
                }
            }
            cachedMainShot = CachedMainShot(p.id, p.version, prepared.jpeg, geom, scale)
            prepared.jpeg to geom
        }
        // Остальные страницы — по близости к текущей, в порядке тетради.
        val others = pages.withIndex().filter { it.index != index && (selectedContextPages == null || it.value.id in selectedContextPages!!) }
            .sortedBy { abs(it.index - index) }.take(contextLimit).sortedBy { it.index }
        val context = others.map { contextImage(it.value) }
        return ShotResult.Ok(jpeg, geometry, null, context)
    }

    var marksVisible by mutableStateOf(true)
        private set

    private val rejectedMarks = mutableSetOf<Int>()

    fun toggleMarks() {
        marksVisible = !marksVisible
        refreshMarks()
    }

    /** Студент нажал «Не здесь»: фиксируем промах для разметки и переключаем подсветку на строку. */
    fun onMarkNotHere(number: Int) {
        rejectedMarks.add(number)
        val a = mentor.state.value.answer ?: return
        val m = a.marks.getOrNull(number - 1) ?: return
        app.coordStats.recordUserMiss(
            model = app.settings.load().model,
            line = m.lines?.first,
            box = m.box?.let { "[${it.left}, ${it.top}, ${it.right}, ${it.bottom}]" },
            question = meta?.title,
        )
        refreshMarks()
    }

    /** Отметки ответа — на странице, к которой он относится. */
    fun refreshMarks(a: AnswerUi? = mentor.state.value.answer) {
        val p = page
        ink.marks = if (!marksVisible || a == null || p == null || p.id != answerPageId) {
            emptyList()
        } else {
            val g = a.geometry
            a.marks.mapIndexedNotNull { i, m ->
                val num = i + 1
                val isRejected = num in rejectedMarks
                val lineBox = if (isRejected) {
                    m.lines?.firstOrNull()?.let { lineNum ->
                        g?.lines?.line(lineNum)?.let { l ->
                            NBox(
                                l.left.toFloat() / g.imageW,
                                l.top.toFloat() / g.imageH,
                                l.right.toFloat() / g.imageW,
                                l.bottom.toFloat() / g.imageH,
                            )
                        }
                    }
                } else null
                val b = (if (isRejected) lineBox else m.box) ?: return@mapIndexedNotNull null
                val s = g?.toScreen(b) ?: return@mapIndexedNotNull null
                PageMark(num, m.kind, RectF(s.left * answerScale, s.top * answerScale, s.right * answerScale, s.bottom * answerScale), m.drawing)
            }
        }
    }

    /** Показать место заметки [number] на странице. */
    fun focusMark(number: Int) {
        ink.selectedMark = number
        ink.marks.firstOrNull { it.number == number }?.let { ink.reveal(it.rect) }
    }

    private data class CachedCleanPage(val version: Long, val jpeg: ByteArray)
    private val cleanPageCache = LinkedHashMap<Long, CachedCleanPage>()

    /** Все страницы тетради — картинками для чистовика и анализа. Неизменённые страницы не перерисовываются. */
    suspend fun pageImages(): List<ByteArray> {
        flushSaveNow()
        val currentIds = pages.map { it.id }.toSet()
        cleanPageCache.keys.retainAll(currentIds)
        return pages.map { p ->
            val cached = cleanPageCache[p.id]
            if (cached != null && cached.version == p.version) {
                cached.jpeg
            } else {
                val jpeg = nbs.renderJpeg(notebookId, p, ImageUtils.MAX_PIXELS, 85)
                cleanPageCache[p.id] = CachedCleanPage(p.version, jpeg)
                jpeg
            }
        }
    }

    companion object {
        /** Синяя ручка — как у большинства студентов, чёрная, красная для пометок, зелёная. */
        val PENS = listOf(
            0xFF1F4FD1.toInt(), 0xFF16161A.toInt(), 0xFFD93A3A.toInt(), 0xFF1F9D60.toInt(),
            0xFFE07B1F.toInt(), 0xFF7A4BD1.toInt(),
        )
        val MARKERS = listOf(0xFFFFE15A.toInt(), 0xFFFF9BCB.toInt(), 0xFF9CD8FF.toInt(), 0xFFA6F0A0.toInt())
        val WIDTHS = floatArrayOf(2.2f, 3.2f, 5f)

        /** Размер печатного текста: мелкий, обычный, заголовок (в единицах страницы). */
        val TEXT_SIZES = floatArrayOf(16f, 22f, 32f)
        private const val SAVE_DELAY_MS = 800L
        private const val AUTO_PAUSE_MS = 7_000L
        private const val AUTO_GAP_MS = 40_000L
        private const val CONTEXT_CACHE_BYTES = 24 * 1024 * 1024
    }
}
