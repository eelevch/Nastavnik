package app.nastavnik.ink

import android.graphics.RectF
import app.nastavnik.ai.DrawingKind
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Помощник записи и рисования от имени наставника прямо на странице тетради:
 * преобразует LaTeX-формулы в чистый Unicode-вид для [TextBox], находит свободное место
 * без наезда на существующий почерк и формирует штрихи/фигуры с привязкой к сетке листа.
 */
object MentorNotebookWriter {
    /** Чисто-Kotlin прямоугольник в координатах листа (для тестирования и расчётов без Android RectF). */
    data class SlotBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val isEmpty: Boolean get() = left >= right || top >= bottom
        fun toRectF(): RectF = RectF(left, top, right, bottom)
    }

    private val fracRegex = Regex("""\\(?:d|t)?frac\{([^{}]+)\}\{([^{}]+)\}""")
    private val sqrtRegex = Regex("""\\sqrt\{([^{}]+)\}""")
    private val supBraceRegex = Regex("""\^\{([^{}]+)\}""")
    private val subBraceRegex = Regex("""_\{([^{}]+)\}""")
    private val supSingleRegex = Regex("""\^([0-9+\-=()])""")
    private val subSingleRegex = Regex("""_([0-9+\-=()a-zA-Z])""")
    private val cmdReplacements = listOf(
        "\\Rightarrow" to "⇒",
        "\\Leftrightarrow" to "⇔",
        "\\rightarrow" to "→",
        "\\leftarrow" to "←",
        "\\to" to "→",
        "\\infty" to "∞",
        "\\cdot" to "·",
        "\\times" to "×",
        "\\pm" to "±",
        "\\mp" to "∓",
        "\\le" to "≤",
        "\\leq" to "≤",
        "\\ge" to "≥",
        "\\geq" to "≥",
        "\\ne" to "≠",
        "\\neq" to "≠",
        "\\approx" to "≈",
        "\\equiv" to "≡",
        "\\in" to "∈",
        "\\notin" to "∉",
        "\\subset" to "⊂",
        "\\cup" to "∪",
        "\\cap" to "∩",
        "\\forall" to "∀",
        "\\exists" to "∃",
        "\\partial" to "∂",
        "\\nabla" to "∇",
        "\\int" to "∫",
        "\\sum" to "∑",
        "\\prod" to "∏",
        "\\lim" to "lim",
        "\\sin" to "sin",
        "\\cos" to "cos",
        "\\tan" to "tg",
        "\\ln" to "ln",
        "\\log" to "log",
        "\\alpha" to "α",
        "\\beta" to "β",
        "\\gamma" to "γ",
        "\\delta" to "δ",
        "\\Delta" to "Δ",
        "\\varepsilon" to "ε",
        "\\epsilon" to "ε",
        "\\theta" to "θ",
        "\\lambda" to "λ",
        "\\mu" to "μ",
        "\\pi" to "π",
        "\\rho" to "ρ",
        "\\sigma" to "σ",
        "\\varphi" to "φ",
        "\\phi" to "φ",
        "\\omega" to "ω",
        "\\Omega" to "Ω",
        "\\left" to "",
        "\\right" to "",
    )

    private val superscripts = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾', 'n' to 'ⁿ', 'i' to 'ⁱ',
    )
    private val subscripts = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
    )

    fun cleanMathForSheet(raw: String): String {
        var s = raw.trim()
            .replace("$$", "")
            .replace("$", "")
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
        for ((k, v) in cmdReplacements) s = s.replace(k, v)
        repeat(2) {
            s = fracRegex.replace(s) { m ->
                val a = m.groupValues[1].trim()
                val b = m.groupValues[2].trim()
                val wa = if (a.contains('+') || a.contains('-')) "($a)" else a
                val wb = if (b.contains('+') || b.contains('-') || b.contains('*') || b.contains('·')) "($b)" else b
                "$wa/$wb"
            }
            s = sqrtRegex.replace(s) { m ->
                val inner = m.groupValues[1].trim()
                if (inner.length == 1) "√$inner" else "√($inner)"
            }
        }
        s = supBraceRegex.replace(s) { m ->
            val v = m.groupValues[1]
            v.map { superscripts[it] ?: return@replace "^($v)" }.joinToString("")
        }
        s = supSingleRegex.replace(s) { m ->
            val ch = m.groupValues[1].first()
            superscripts[ch]?.toString() ?: "^$ch"
        }
        s = subBraceRegex.replace(s) { m ->
            val v = m.groupValues[1]
            v.map { subscripts[it] ?: return@replace "_$v" }.joinToString("")
        }
        s = subSingleRegex.replace(s) { m ->
            val ch = m.groupValues[1].first()
            subscripts[ch]?.toString() ?: "_$ch"
        }
        return s.lines().joinToString("\n") { it.trimEnd() }.trim()
    }

    internal fun computeWriteSlotBox(
        page: InkPage,
        anchor: SlotBox?,
        lineCountEstimate: Int,
        fontSize: Float = 22f,
    ): SlotBox {
        val left = ((anchor?.left ?: 84f).coerceIn(64f, PAGE_W * 0.42f))
        val width = (PAGE_W - left - 72f).coerceIn(320f, PAGE_W - 140f)
        val estH = max(54f, lineCountEstimate * fontSize * TextBox.LINE_K + 14f)

        val stepY = if (page.paper == Paper.LINED) PageDrawer.LINE_STEP else PageDrawer.CELL
        var y = if (anchor != null) {
            anchor.bottom + 14f
        } else {
            val bottom = maxExistingBottom(page)
            if (bottom <= 1f) 96f else bottom + 20f
        }
        y = ceilToGrid(y, stepY)

        for (attempt in 0 until 8) {
            val cand = SlotBox(left, y, left + width, y + estH)
            val blocker = overlappingBottom(page, cand) ?: break
            y = ceilToGrid(blocker + 12f, stepY)
        }
        val maxY = (page.height - estH - 40f).coerceAtLeast(80f)
        y = y.coerceIn(60f, maxY)
        return SlotBox(left, y, left + width, (y + estH).coerceAtMost(page.height - 24f))
    }

    /** Ищет аккуратный свободный прямоугольник на листе (не наезжая на существующие строки). */
    fun findWriteSlot(
        page: InkPage,
        anchorPageRect: RectF?,
        lineCountEstimate: Int,
        fontSize: Float = 22f,
    ): RectF = computeWriteSlotBox(page, anchorPageRect?.toSlotBox(), lineCountEstimate, fontSize).toRectF()

    internal fun buildWriteAdditionBoxes(
        page: InkPage,
        text: String,
        anchor: SlotBox?,
        inkColor: Int,
        fontSize: Float = 22f,
        metrics: MathMetrics = EstimateMetrics,
    ): Triple<List<Stroke>, List<TextBox>, SlotBox> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return Triple(emptyList(), emptyList(), SlotBox(0f, 0f, 0f, 0f))
        val stepY = if (page.paper == Paper.LINED) PageDrawer.LINE_STEP else PageDrawer.CELL
        val slotLeft = ((anchor?.left ?: 84f).coerceIn(64f, PAGE_W * 0.42f))
        val slotWidth = (PAGE_W - slotLeft - 72f).coerceIn(320f, PAGE_W - 140f)
        val layout = MathTypesetter.layoutRichLines(
            text = trimmed,
            maxWidth = (slotWidth - 18f).coerceAtLeast(160f),
            size = fontSize,
            color = inkColor,
            metrics = metrics,
            lineStep = stepY,
        )
        if (layout.texts.isEmpty() && layout.strokes.isEmpty()) {
            return Triple(emptyList(), emptyList(), SlotBox(0f, 0f, 0f, 0f))
        }
        val estLines = ceil(layout.descent / (fontSize * TextBox.LINE_K)).toInt().coerceAtLeast(1)
        val rawSlot = computeWriteSlotBox(page, anchor, estLines, fontSize)
        val needBottom = (rawSlot.top + layout.descent + 8f).coerceAtMost(page.height - 24f)
        val slot = rawSlot.copy(bottom = max(rawSlot.bottom, needBottom))

        val placed = layout.place(slot.left + 14f, slot.top)
        val barX = (slot.left - 2f).coerceAtLeast(24f)
        val y0 = slot.top + 4f
        val y1 = max(y0 + 18f, min(slot.bottom - 8f, slot.top + layout.descent))
        val marginBar = Stroke(
            Tool.SHAPE,
            inkColor,
            2.4f,
            Shapes.densify(floatArrayOf(barX, y0, barX, y1), 0.5f),
        )
        return Triple(listOf(marginBar) + placed.strokes, placed.texts, slot)
    }

    /** Создаёт 2D-раскладку текста/формул и ровную штрих-скобку слева в цвете ручки. */
    fun buildWriteAddition(
        page: InkPage,
        text: String,
        anchorPageRect: RectF?,
        inkColor: Int,
        fontSize: Float = 22f,
        metrics: MathMetrics = EstimateMetrics,
    ): Triple<List<Stroke>, List<TextBox>, RectF> {
        val (strokes, texts, slot) = buildWriteAdditionBoxes(page, text, anchorPageRect?.toSlotBox(), inkColor, fontSize, metrics)
        return Triple(strokes, texts, slot.toRectF())
    }

    internal fun buildDiagramAdditionBox(
        page: InkPage,
        src: String,
        anchor: SlotBox?,
        inkColor: Int,
        metrics: MathMetrics = EstimateMetrics,
    ): Triple<List<Stroke>, List<TextBox>, SlotBox> {
        val trimmed = src.trim()
        if (trimmed.isBlank()) return Triple(emptyList(), emptyList(), SlotBox(0f, 0f, 0f, 0f))
        val (locW, locH, title) = MentorDiagram.inspectHeader(trimmed)
        val targetW = min(PAGE_W * 0.78f, 640f)
        val aspect = (locH / locW.coerceAtLeast(1f)).coerceIn(0.35f, 1.3f)
        val extraH = if (!title.isNullOrBlank()) 40f else 0f
        val targetH = (targetW * aspect + extraH).coerceIn(180f, 620f)
        val stepY = if (page.paper == Paper.LINED) PageDrawer.LINE_STEP else PageDrawer.CELL
        val estLines = ceil(targetH / (22f * TextBox.LINE_K)).toInt().coerceAtLeast(5)
        val baseSlot = computeWriteSlotBox(page, anchor, estLines, 22f)
        val left = if (anchor != null) {
            ceilToGrid(anchor.left.coerceIn(64f, PAGE_W - targetW - 48f), PageDrawer.CELL)
        } else {
            ceilToGrid(((PAGE_W - targetW) * 0.5f), PageDrawer.CELL)
        }
        val top = ceilToGrid(baseSlot.top, stepY)
        val slot = SlotBox(
            left = left,
            top = top,
            right = (left + targetW).coerceAtMost(PAGE_W - 40f),
            bottom = (top + targetH).coerceAtMost(page.height - 24f),
        )
        val (strokes, texts) = MentorDiagram.build(trimmed, slot, inkColor, metrics)
        return Triple(strokes, texts, slot)
    }

    /** Строит чертёж, схему или график по DSL [MentorDiagram] в свободной зоне листа. */
    fun buildDiagramAddition(
        page: InkPage,
        src: String,
        anchorPageRect: RectF?,
        inkColor: Int,
        metrics: MathMetrics = EstimateMetrics,
    ): Triple<List<Stroke>, List<TextBox>, RectF> {
        val (strokes, texts, slot) = buildDiagramAdditionBox(page, src, anchorPageRect?.toSlotBox(), inkColor, metrics)
        return Triple(strokes, texts, slot.toRectF())
    }

    internal fun buildShapeAdditionBox(
        page: InkPage,
        kind: DrawingKind,
        anchor: SlotBox?,
        inkColor: Int,
        strokeWidth: Float = 3.2f,
    ): Pair<List<Stroke>, SlotBox> {
        val stepY = if (page.paper == Paper.LINED) PageDrawer.LINE_STEP else PageDrawer.CELL
        val rect = if (anchor != null && (kind == DrawingKind.ELLIPSE || kind == DrawingKind.RECT)) {
            findContentUnionBox(page, anchor, pad = 10f, hitMargin = 12f)
                ?: standardShapeBox(page, kind, anchor, stepY)
        } else {
            standardShapeBox(page, kind, anchor, stepY)
        }
        val shapeKind = when (kind) {
            DrawingKind.LINE -> ShapeKind.LINE
            DrawingKind.ARROW -> ShapeKind.ARROW
            DrawingKind.RECT -> ShapeKind.RECT
            DrawingKind.ELLIPSE -> ShapeKind.ELLIPSE
            DrawingKind.TRIANGLE -> ShapeKind.TRIANGLE
            DrawingKind.AXES -> ShapeKind.AXES
        }
        val x0 = if (kind == DrawingKind.AXES) rect.left + rect.width * 0.2f else rect.left
        val y0 = if (kind == DrawingKind.AXES) rect.bottom - rect.height * 0.2f else rect.top
        val strokes = Shapes.build(shapeKind, x0, y0, rect.right, rect.bottom, strokeWidth, inkColor)
        return strokes to rect
    }

    private fun standardShapeBox(page: InkPage, kind: DrawingKind, anchor: SlotBox?, stepY: Float): SlotBox {
        val (w, h) = when (kind) {
            DrawingKind.LINE, DrawingKind.ARROW -> 280f to 0f
            DrawingKind.RECT -> 320f to 200f
            DrawingKind.ELLIPSE -> 300f to 180f
            DrawingKind.TRIANGLE -> 300f to 260f
            DrawingKind.AXES -> 360f to 260f
        }
        val slot = computeWriteSlotBox(page, anchor, lineCountEstimate = max(2, ceil(h / 30f).toInt()), fontSize = 24f)
        val left = ceilToGrid((PAGE_W - w) * 0.5f, PageDrawer.CELL)
        val top = ceilToGrid(slot.top, PageDrawer.CELL.coerceAtLeast(stepY.coerceAtMost(PageDrawer.CELL)))
        val maxTop = (page.height - h - 40f).coerceAtLeast(60f)
        val finalTop = ceilToGrid(min(top, maxTop), PageDrawer.CELL)
        return SlotBox(left, finalTop, left + w, finalTop + h)
    }

    private fun findContentUnionBox(page: InkPage, anchor: SlotBox, pad: Float, hitMargin: Float): SlotBox? {
        val hit = SlotBox(anchor.left - hitMargin, anchor.top - hitMargin, anchor.right + hitMargin, anchor.bottom + hitMargin)
        var minL = Float.MAX_VALUE
        var minT = Float.MAX_VALUE
        var maxR = -Float.MAX_VALUE
        var maxB = -Float.MAX_VALUE
        var found = false
        fun includeIfHits(b: FloatArray) {
            if (b[2] >= hit.left && b[0] <= hit.right && b[3] >= hit.top && b[1] <= hit.bottom) {
                minL = min(minL, b[0])
                minT = min(minT, b[1])
                maxR = max(maxR, b[2])
                maxB = max(maxB, b[3])
                found = true
            }
        }
        for (s in page.strokes) includeIfHits(s.bounds())
        for (t in page.texts) includeIfHits(t.bounds())
        if (!found) return null
        return SlotBox(
            left = (minL - pad).coerceAtLeast(16f),
            top = (minT - pad).coerceAtLeast(16f),
            right = (maxR + pad).coerceAtMost(PAGE_W - 16f),
            bottom = (maxB + pad).coerceAtMost(page.height - 16f),
        )
    }

    /** Строит ровную геометрическую фигуру ([DrawingKind]) от имени наставника в свободной зоне листа. */
    fun buildShapeAddition(
        page: InkPage,
        kind: DrawingKind,
        anchorPageRect: RectF?,
        inkColor: Int,
        strokeWidth: Float = 3.2f,
    ): Pair<List<Stroke>, RectF> {
        val (strokes, box) = buildShapeAdditionBox(page, kind, anchorPageRect?.toSlotBox(), inkColor, strokeWidth)
        return strokes to box.toRectF()
    }

    /** Создаёт ровное выделение полупрозрачным маркером на указанной строке/области. */
    fun buildHighlightAddition(
        page: InkPage,
        anchorPageRect: RectF?,
        markerColor: Int,
    ): Pair<List<Stroke>, RectF> {
        val rect = anchorPageRect?.toSlotBox() ?: computeWriteSlotBox(page, null, 1, 22f)
        val cy = (rect.top + rect.bottom) * 0.5f
        val x0 = rect.left.coerceIn(16f, PAGE_W - 40f)
        val x1 = rect.right.coerceIn(x0 + 40f, PAGE_W - 16f)
        val stroke = Stroke(
            Tool.HIGHLIGHTER,
            markerColor,
            InkView.HIGHLIGHTER_W,
            floatArrayOf(x0, cy, 0.55f, x1, cy, 0.55f),
        )
        return listOf(stroke) to RectF(x0, cy - 16f, x1, cy + 16f)
    }

    private fun RectF.toSlotBox(): SlotBox? {
        if (left == 0f && top == 0f && right == 0f && bottom == 0f) return null
        return SlotBox(left, top, right, bottom)
    }

    private fun maxExistingBottom(page: InkPage): Float {
        var maxY = 0f
        for (s in page.strokes) {
            maxY = max(maxY, s.bounds()[3])
        }
        for (t in page.texts) {
            maxY = max(maxY, t.bounds()[3])
        }
        return maxY
    }

    private fun overlappingBottom(page: InkPage, cand: SlotBox): Float? {
        var bottom: Float? = null
        fun check(b: FloatArray) {
            if (b[2] >= cand.left && b[0] <= cand.right && b[3] >= cand.top && b[1] <= cand.bottom) {
                bottom = max(bottom ?: 0f, b[3])
            }
        }
        for (s in page.strokes) check(s.bounds())
        for (t in page.texts) check(t.bounds())
        return bottom
    }

    private fun ceilToGrid(y: Float, step: Float): Float {
        if (step <= 1f) return y
        return ceil(y / step) * step
    }
}
