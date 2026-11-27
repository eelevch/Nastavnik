package app.nastavnik.ai

/** Вид заметки на экране: от него зависят цвет рамки и подпись. */
enum class MarkKind(val label: String, val token: String) {
    ERROR("Ошибка", "error"),
    HINT("Подсказка", "hint"),
    OK("Верно", "ok"),
    THEORY("Теория", "theory"),
    NOTE("Наставник", "note");

    companion object {
        private val synonyms = mapOf(
            "error" to ERROR, "mistake" to ERROR, "err" to ERROR, "ошибка" to ERROR,
            "hint" to HINT, "next" to HINT, "step" to HINT, "подсказка" to HINT, "шаг" to HINT,
            "ok" to OK, "correct" to OK, "good" to OK, "верно" to OK,
            "theory" to THEORY, "теория" to THEORY,
            "note" to NOTE, "info" to NOTE, "заметка" to NOTE,
        )

        fun parse(word: String): MarkKind? = synonyms[word.lowercase()]
    }
}

/**
 * Как модель записывает координаты рамки.
 * Claude видит снимок в его настоящих пикселях; Gemini обучен на box_2d (y, x, нормировано на 1000);
 * у OpenAI-совместимых сервис может сам уменьшить картинку, поэтому для них тоже доли от 1000.
 */
enum class CoordMode { PIXELS_XY, NORM_XY, NORM_YX }

/** Рамка в долях снимка: 0…1 по ширине и высоте. */
data class NBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

enum class DrawingKind { ARROW, LINE, ELLIPSE, RECT, TRIANGLE, AXES }

data class Drawing(val kind: DrawingKind, val reverseX: Boolean = false, val reverseY: Boolean = false)

enum class MentorActionKind {
    ERASE,
    CLEAR_PAGE,
    ADD_PAGE,
    GOTO_PAGE,
    DUPLICATE_PAGE,
    DELETE_PAGE,
    SET_PAPER,
    RENAME_NOTEBOOK,
    SET_SUBJECT,
}

/**
 * Предлагаемое наставником изменение конспекта или тетради.
 * Выполняется только после явного подтверждения студента (или сразу, если отключено в настройках).
 */
data class MentorPageAction(
    val id: String,
    val kind: MentorActionKind,
    val text: String = "",
    val shape: DrawingKind? = null,
    val paper: String? = null,
    val targetPage: Int? = null,
    val applyAllPages: Boolean = false,
    val box: NBox? = null,
    val lines: IntRange? = null,
    val after: Int? = null,
) {
    fun summaryLabel(): String = when (kind) {
        MentorActionKind.ERASE -> "Стереть фрагмент на странице"
        MentorActionKind.CLEAR_PAGE -> "Очистить текущую страницу"
        MentorActionKind.ADD_PAGE -> "Добавить новую страницу" + (paper?.let { " (${paperLabel(it)})" } ?: "")
        MentorActionKind.GOTO_PAGE -> "Перейти на страницу ${targetPage ?: 1}"
        MentorActionKind.DUPLICATE_PAGE -> "Дублировать текущую страницу"
        MentorActionKind.DELETE_PAGE -> "Удалить текущую страницу"
        MentorActionKind.SET_PAPER -> "Сменить бумагу на ${paperLabel(paper)}" + (if (applyAllPages) " во всей тетради" else "")
        MentorActionKind.RENAME_NOTEBOOK -> "Переименовать тетрадь в «$text»"
        MentorActionKind.SET_SUBJECT -> "Задать предмет «$text»"
    }

    companion object {
        private fun paperLabel(paper: String?): String = when (paper?.uppercase()) {
            "GRID" -> "клетку"
            "LINED" -> "линейку"
            "DOTS" -> "точки"
            "BLANK" -> "чистый лист"
            else -> "выбранную"
        }
    }
}

/**
 * Заметка наставника. Место задаётся номером строки ([lines]), «под строкой» ([after]) и/или рамкой [box]
 * (в долях присланного снимка). Приложение потом переводит всё это в точную рамку по чернилам.
 */
data class Mark(
    val kind: MarkKind,
    val box: NBox?,
    /** Короткая подсказка — её видно сразу. */
    val text: String,
    val lines: IntRange? = null,
    val after: Int? = null,
    /** Полная подсказка — открывается кнопкой «Подробнее» (null — модель её не дала). */
    val detail: String? = null,
    val drawing: Drawing? = null,
    /** Исходные координаты, присланные моделью (до привязки к чернилам). */
    val rawModelBox: NBox? = null,
    /** Признак промаха: рамка модели не попала на чернила, включена подсветка строки. */
    val coordMiss: Boolean = false,
)

data class ScreenAnswer(
    val marks: List<Mark>,
    /** `[[ГОТОВО]]` — решение верное и доведено до ответа. */
    val done: Boolean,
    /** `[[ВЕРНО]]` — тихая проверка не нашла ошибок, показывать нечего. */
    val allGood: Boolean,
    /** `@@ тема …` — короткое название задачи для истории. */
    val topic: String? = null,
    /** Предложенные изменения конспекта/тетради (ждут разрешения студента). */
    val actions: List<MentorPageAction> = emptyList(),
)

/**
 * Разбирает ответ наставника вида
 * ```
 * @@ error стр 3
 * Проверь знак при раскрытии скобок.
 * @@ hint после 5
 * Что получится, если приравнять производную к нулю?
 * @@ error стр 4 : 410 520 980 590
 * ```
 * А также команды действий с тетрадью (`@@ write`, `@@ draw`, `@@ highlight`, `@@ erase`,
 * `@@ page_add`, `@@ page_goto`, `@@ page_paper`, `@@ notebook_rename`, `@@ notebook_subject` и т.д.).
 * Работает на недописанном (потоковом) тексте.
 */
object ScreenAnswerParser {
    const val ALL_GOOD_MARKER = "[[ВЕРНО]]"

    private val header = Regex("""^\s*@@\s*([\p{L}_]+)(.*)$""")
    private val drawingRe = Regex("""^\s*@@\s*(arrow|line|ellipse)\s*:?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val eraseActionRe = Regex("""^\s*@@\s*(?:erase|сотри|удали_фрагмент)\b(.*)$""", RegexOption.IGNORE_CASE)
    private val pageAddRe = Regex("""^\s*@@\s*(?:page_add|add_page|новая_страница)\b(.*)$""", RegexOption.IGNORE_CASE)
    private val pageGotoRe = Regex("""^\s*@@\s*(?:page_goto|goto_page|страница)\b(.*)$""", RegexOption.IGNORE_CASE)
    private val pageDupRe = Regex("""^\s*@@\s*(?:page_duplicate|duplicate_page)\b.*$""", RegexOption.IGNORE_CASE)
    private val pageDelRe = Regex("""^\s*@@\s*(?:page_delete|delete_page)\b.*$""", RegexOption.IGNORE_CASE)
    private val pageClearRe = Regex("""^\s*@@\s*(?:page_clear|clear_page|очистить_страницу)\b.*$""", RegexOption.IGNORE_CASE)
    private val pagePaperRe = Regex("""^\s*@@\s*(?:page_paper|paper|бумага)\b(.*)$""", RegexOption.IGNORE_CASE)
    private val nbRenameRe = Regex("""^\s*@@\s*(?:notebook_rename|rename_notebook|название_тетради)\s*[:\-—]?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val nbSubjectRe = Regex("""^\s*@@\s*(?:notebook_subject|subject_notebook|предмет_тетради)\s*[:\-—]?\s*(.*)$""", RegexOption.IGNORE_CASE)

    private val afterRe = Regex("""(?:после|под|after|below)\s*(?:стр\p{L}*\.?|line)?\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val linesRe = Regex("""(?:стр\p{L}*\.?|lines?|L)\s*(\d+)(?:\s*[-–—]\s*(\d+))?""", RegexOption.IGNORE_CASE)
    private val rangeAtStart = Regex("""^[\s:\-]*(\d+)\s*[-–—]\s*(\d+)(?![\d.,])""")
    // Дробная часть — только через точку: запятая в «(10,10,20,20)» разделяет числа.
    private val numberRe = Regex("""-?\d+(?:\.\d+)?""")
    private val topicRe = Regex("""^\s*@@\s*(?:тема|topic)(?!\p{L})\s*[:\-—]?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val detailRe = Regex("""^\s*@@\s*(?:подробнее|подробно|details?|more)\s*:?\s*$""", RegexOption.IGNORE_CASE)
    private val allGoodRegex = Regex("""\[\[\s*ВЕРНО\s*\]\]""", RegexOption.IGNORE_CASE)

    private class Spec(val lines: IntRange?, val after: Int?, val coords: List<Float>?)

    /** Что после вида заметки: номера строк, «после N», координаты. */
    private fun parseSpec(restIn: String): Spec? {
        var rest = restIn
        var after: Int? = null
        var lines: IntRange? = null
        afterRe.find(rest)?.let { m ->
            after = m.groupValues[1].toInt()
            rest = rest.removeRange(m.range)
        }
        (linesRe.find(rest) ?: rangeAtStart.find(rest))?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toIntOrNull() ?: a
            lines = minOf(a, b)..maxOf(a, b)
            rest = rest.removeRange(m.range)
        }
        val nums = numberRe.findAll(rest).map { it.value.toFloat() }.toMutableList()
        // «@@ error 3» или «@@ error 3 410 520 980 590» — первое число тогда номер строки.
        if (lines == null && after == null && (nums.size == 1 || nums.size == 5)) {
            lines = nums.first().toInt()..nums.first().toInt()
            nums.removeAt(0)
        }
        val coords = if (nums.size >= 4) nums.takeLast(4) else null
        // В строке-метке не должно быть ничего, кроме этого; иначе это просто текст, начинающийся с «@@».
        val leftover = numberRe.replace(rest, "").replace(Regex("""[\s:;,\[\]()|xх×=-]+"""), "")
        if (leftover.isNotEmpty() && leftover.length > 3) return null
        return Spec(lines, after, coords)
    }

    private fun parseDrawingKind(word: String): DrawingKind = when (word.lowercase()) {
        "arrow", "стрелка" -> DrawingKind.ARROW
        "ellipse", "овал" -> DrawingKind.ELLIPSE
        "rect", "прямоугольник" -> DrawingKind.RECT
        "triangle", "треугольник" -> DrawingKind.TRIANGLE
        "axes", "оси" -> DrawingKind.AXES
        else -> DrawingKind.LINE
    }

    private fun parsePaperToken(raw: String): String? {
        val low = raw.lowercase()
        return when {
            "grid" in low || "клетк" in low -> "GRID"
            "lined" in low || "линей" in low -> "LINED"
            "dots" in low || "точк" in low || "точеч" in low -> "DOTS"
            "blank" in low || "чист" in low || "бел" in low -> "BLANK"
            else -> null
        }
    }

    fun parse(raw: String, mode: CoordMode, imageW: Int, imageH: Int, streaming: Boolean = false): ScreenAnswer {
        val done = DoneMarker.process(raw, streaming)
        val allGood = allGoodRegex.containsMatchIn(done.text)
        var text = allGoodRegex.replace(done.text, "")
        // Строка-метка, после которой ещё не пришёл перевод строки, может быть недописанной.
        if (streaming && !raw.endsWith("\n")) text = hidePartialHeader(text)

        val marks = mutableListOf<Mark>()
        val actions = mutableListOf<MentorPageAction>()
        var kind: MarkKind? = null
        var box: NBox? = null
        var lines: IntRange? = null
        var after: Int? = null
        val body = StringBuilder()
        var topic: String? = null
        // Текст заметки до строки «@@ подробнее» — короткая подсказка, после — полная.
        val detail = StringBuilder()
        var inDetail = false
        var drawing: Drawing? = null

        fun flush() {
            val t = body.toString().trim()
            val d = detail.toString().trim().ifEmpty { null }
            if (kind != null || t.isNotEmpty() || d != null) {
                // Модель забыла короткую часть — показываем начало полной.
                val short = t.ifEmpty { d?.let(::firstSentence).orEmpty() }
                if (short.isNotEmpty() || streaming) marks += Mark(kind ?: MarkKind.NOTE, box, short, lines, after, d?.takeIf { it != short }, drawing)
            }
            body.setLength(0)
            detail.setLength(0)
            inDetail = false
        }

        for (line in text.lines()) {
            eraseActionRe.matchEntire(line)?.let { em ->
                flush()
                kind = null; box = null; lines = null; after = null; drawing = null
                val spec = parseSpec(em.groupValues[1])
                actions += MentorPageAction(
                    id = "act_${actions.size}",
                    kind = MentorActionKind.ERASE,
                    box = spec?.coords?.let { toBox(it, mode, imageW, imageH) },
                    lines = spec?.lines,
                    after = spec?.after,
                )
                continue
            }
            pageAddRe.matchEntire(line)?.let { pm ->
                flush()
                kind = null; box = null; lines = null; after = null; drawing = null
                actions += MentorPageAction(
                    id = "act_${actions.size}",
                    kind = MentorActionKind.ADD_PAGE,
                    paper = parsePaperToken(pm.groupValues[1]),
                )
                continue
            }
            pageGotoRe.matchEntire(line)?.let { gm ->
                flush()
                kind = null; box = null; lines = null; after = null; drawing = null
                val n = numberRe.find(gm.groupValues[1])?.value?.toFloatOrNull()?.toInt()?.coerceAtLeast(1)
                if (n != null) {
                    actions += MentorPageAction(
                        id = "act_${actions.size}",
                        kind = MentorActionKind.GOTO_PAGE,
                        targetPage = n,
                    )
                }
                continue
            }
            if (pageDupRe.matches(line)) {
                flush()
                kind = null; box = null; lines = null; after = null; drawing = null
                actions += MentorPageAction(id = "act_${actions.size}", kind = MentorActionKind.DUPLICATE_PAGE)
                continue
            }
            if (pageDelRe.matches(line)) {
                flush()
                kind = null; box = null; lines = null; after = null; drawing = null
                actions += MentorPageAction(id = "act_${actions.size}", kind = MentorActionKind.DELETE_PAGE)
                continue
            }
            if (pageClearRe.matches(line)) {
                flush()
                kind = null; box = null; lines = null; after = null; drawing = null
                actions += MentorPageAction(id = "act_${actions.size}", kind = MentorActionKind.CLEAR_PAGE)
                continue
            }
            pagePaperRe.matchEntire(line)?.let { ppm ->
                flush()
                kind = null; box = null; lines = null; after = null; drawing = null
                val arg = ppm.groupValues[1]
                val paper = parsePaperToken(arg)
                if (paper != null) {
                    val all = "all" in arg.lowercase() || "вс" in arg.lowercase()
                    actions += MentorPageAction(
                        id = "act_${actions.size}",
                        kind = MentorActionKind.SET_PAPER,
                        paper = paper,
                        applyAllPages = all,
                    )
                }
                continue
            }
            nbRenameRe.matchEntire(line)?.let { nm ->
                flush()
                kind = null; box = null; lines = null; after = null; drawing = null
                val newTitle = nm.groupValues[1].trim().trim('«', '»', '"').take(80)
                if (newTitle.isNotEmpty()) {
                    actions += MentorPageAction(
                        id = "act_${actions.size}",
                        kind = MentorActionKind.RENAME_NOTEBOOK,
                        text = newTitle,
                    )
                }
                continue
            }
            nbSubjectRe.matchEntire(line)?.let { sm ->
                flush()
                kind = null; box = null; lines = null; after = null; drawing = null
                val newSubj = sm.groupValues[1].trim().trim('«', '»', '"').take(60)
                if (newSubj.isNotEmpty()) {
                    actions += MentorPageAction(
                        id = "act_${actions.size}",
                        kind = MentorActionKind.SET_SUBJECT,
                        text = newSubj,
                    )
                }
                continue
            }

            val drawingHeader = drawingRe.matchEntire(line)
            if (drawingHeader != null) {
                val values = numberRe.findAll(drawingHeader.groupValues[2]).mapNotNull { it.value.toFloatOrNull() }.toList()
                val drawingBox = if (values.size == 4 && values.all { it.isFinite() }) toBox(values, mode, imageW, imageH) else null
                if (drawingBox != null) {
                    flush()
                    kind = MarkKind.HINT
                    box = drawingBox
                    lines = null
                    after = null
                    val xy = if (mode == CoordMode.NORM_YX) listOf(values[1], values[0], values[3], values[2]) else values
                    drawing = Drawing(DrawingKind.valueOf(drawingHeader.groupValues[1].uppercase()), xy[0] > xy[2], xy[1] > xy[3])
                    continue
                }
            }
            val t = topicRe.matchEntire(line)
            if (t != null) {
                flush()
                kind = null
                topic = t.groupValues[1].trim().trim('«', '»', '"', '.').take(80).ifBlank { null }
                continue
            }
            val m = header.matchEntire(line)
            val k = m?.let { MarkKind.parse(it.groupValues[1]) }
            val spec = if (k != null) parseSpec(m!!.groupValues[2]) else null
            if (k != null && spec != null) {
                flush()
                kind = k
                drawing = null
                box = spec.coords?.let { toBox(it, mode, imageW, imageH) }
                lines = spec.lines
                after = spec.after
            } else if (detailRe.matches(line)) {
                inDetail = true
            } else {
                (if (inDetail) detail else body).append(line).append('\n')
            }
        }
        flush()
        // Пустые заметки, кроме последней недописанной, не нужны.
        val cleaned = marks.filterIndexed { i, mk -> mk.text.isNotEmpty() || (streaming && i == marks.lastIndex) }
        return ScreenAnswer(cleaned, done.done, allGood && cleaned.none { it.kind == MarkKind.ERROR }, topic, actions)
    }

    /** Текст для истории и для следующих запросов: без координат, с подписями, короткая и полная части. */
    fun toPlain(answer: ScreenAnswer): String {
        val marksText = answer.marks.joinToString("\n\n") { m ->
            val text = if (m.detail != null) "${m.text}\n\n${m.detail}" else m.text
            if (m.kind == MarkKind.NOTE) text else "**${m.kind.label}.** $text"
        }
        if (marksText.isNotEmpty() || answer.actions.isEmpty()) return marksText
        return answer.actions.joinToString("\n") { "• ${it.summaryLabel()}" }
    }

    /** Первое предложение (или первая строка) — короткая подсказка, если модель её не выделила. */
    internal fun firstSentence(text: String): String {
        val line = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        // Точка внутри формулы ($…$) — не конец предложения.
        var inMath = false
        for (i in line.indices) {
            val c = line[i]
            if (c == '$') inMath = !inMath
            if (!inMath && (c == '.' || c == '?' || c == '!') && (i == line.lastIndex || line[i + 1] == ' ')) return line.substring(0, i + 1)
        }
        return line
    }

    /** Пока строка `@@ …` не дописана до конца, её не показываем. */
    private fun hidePartialHeader(text: String): String {
        val lastNl = text.lastIndexOf('\n')
        val last = text.substring(lastNl + 1)
        return if (last.trimStart().startsWith("@")) text.substring(0, (lastNl + 1).coerceAtLeast(0)) else text
    }

    internal fun toBox(v: List<Float>, mode: CoordMode, imageW: Int, imageH: Int): NBox? {
        if (imageW <= 0 || imageH <= 0) return null
        var (x0, y0, x1, y1) = when (mode) {
            CoordMode.NORM_YX -> listOf(v[1], v[0], v[3], v[2])
            else -> v
        }
        var effective = mode
        // Модель перепутала систему: прислала пиксели вместо долей от 1000.
        if (mode != CoordMode.PIXELS_XY && maxOf(x0, x1) > 1000.5f && maxOf(x0, x1) <= imageW + 1) {
            if (mode == CoordMode.NORM_YX) { x0 = v[0]; y0 = v[1]; x1 = v[2]; y1 = v[3] }
            effective = CoordMode.PIXELS_XY
        }
        val (sx, sy) = if (effective == CoordMode.PIXELS_XY) imageW.toFloat() to imageH.toFloat() else 1000f to 1000f
        var l = minOf(x0, x1) / sx
        var r = maxOf(x0, x1) / sx
        var t = minOf(y0, y1) / sy
        var b = maxOf(y0, y1) / sy
        if (r < 0f || b < 0f || l > 1f || t > 1f) return null
        l = l.coerceIn(0f, 1f); r = r.coerceIn(0f, 1f); t = t.coerceIn(0f, 1f); b = b.coerceIn(0f, 1f)
        // Слишком тонкую рамку (точку, линию) расширяем до видимой.
        val minW = 0.04f
        val minH = 0.025f
        if (r - l < minW) { val c = (l + r) / 2; l = (c - minW / 2).coerceAtLeast(0f); r = (l + minW).coerceAtMost(1f) }
        if (b - t < minH) { val c = (t + b) / 2; t = (c - minH / 2).coerceAtLeast(0f); b = (t + minH).coerceAtMost(1f) }
        return NBox(l, t, r, b)
    }
}
