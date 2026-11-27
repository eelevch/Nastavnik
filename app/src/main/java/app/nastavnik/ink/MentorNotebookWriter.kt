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
    ): Triple<List<Stroke>, List<TextBox>, SlotBox> {
        val formatted = cleanMathForSheet(text)
        if (formatted.isBlank()) return Triple(emptyList(), emptyList(), SlotBox(0f, 0f, 0f, 0f))
        val estLines = formatted.split('\n').sumOf { line ->
            ceil(line.length / 36.0).toInt().coerceAtLeast(1)
        }.coerceAtLeast(1)
        val slot = computeWriteSlotBox(page, anchor, estLines, fontSize)
        val textX = slot.left + 14f
        val textW = (slot.width - 18f).coerceAtLeast(160f)
        val box = TextBox(textX, slot.top, textW, formatted, fontSize, inkColor)

        val barX = (slot.left - 2f).coerceAtLeast(24f)
        val y0 = slot.top + 4f
        val y1 = min(slot.bottom - 8f, slot.top + estLines * fontSize * TextBox.LINE_K)
        val marginBar = Stroke(
            Tool.PEN,
            inkColor,
            2.6f,
            floatArrayOf(
                barX, y0, 0.55f,
                barX + 0.8f, (y0 + y1) * 0.5f, 0.65f,
                barX, max(y0 + 18f, y1), 0.5f,
            ),
        )
        return Triple(listOf(marginBar), listOf(box), slot)
    }

    /** Создаёт отформатированный [TextBox] и лёгкую штрих-скобку слева в цвете ручки. */
    fun buildWriteAddition(
        page: InkPage,
        text: String,
        anchorPageRect: RectF?,
        inkColor: Int,
        fontSize: Float = 22f,
    ): Triple<List<Stroke>, List<TextBox>, RectF> {
        val (strokes, texts, slot) = buildWriteAdditionBoxes(page, text, anchorPageRect?.toSlotBox(), inkColor, fontSize)
        return Triple(strokes, texts, slot.toRectF())
    }

    internal fun buildShapeAdditionBox(
        page: InkPage,
        kind: DrawingKind,
        anchor: SlotBox?,
        inkColor: Int,
        strokeWidth: Float = 3.2f,
    ): Pair<List<Stroke>, SlotBox> {
        val rect = if (anchor != null && anchor.width > 36f && anchor.height > 28f) {
            anchor
        } else {
            val slot = computeWriteSlotBox(page, null, 6, 26f)
            val cx = PAGE_W * 0.5f
            val w = 320f
            val h = 210f
            SlotBox(cx - w * 0.5f, slot.top, cx + w * 0.5f, (slot.top + h).coerceAtMost(page.height - 40f))
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
