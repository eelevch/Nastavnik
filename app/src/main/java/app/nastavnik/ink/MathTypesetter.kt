package app.nastavnik.ink

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Метрики шрифта для 2D-вёрстки формул на странице тетради (без жёсткой привязки к Android SDK). */
interface MathMetrics {
    fun width(text: String, size: Float): Float
    fun baselineOffset(size: Float): Float
}

/** Оценочные метрики шрифта для модульных тестов и фолбэков без Android Paint. */
object EstimateMetrics : MathMetrics {
    private const val NARROW_CHARS = "il1.,()|[]!'\u2032:;"

    override fun width(text: String, size: Float): Float {
        var total = 0f
        for (ch in text) {
            total += when {
                ch == ' ' -> size * 0.30f
                ch in NARROW_CHARS -> size * 0.32f
                else -> size * 0.56f
            }
        }
        return total
    }

    override fun baselineOffset(size: Float): Float = size * 0.93f
}

/** Готовый фрагмент 2D-вёрстки относительно базовой линии (x = 0, baselineY = 0). */
class MathLayout(
    val width: Float,
    val ascent: Float,
    val descent: Float,
    val texts: List<TextBox>,
    val strokes: List<Stroke>,
) {
    /** Смещает все тексты и штрихи на заданное смещение (dx, baselineY). */
    fun place(dx: Float, baselineY: Float): MathLayout {
        if (dx == 0f && baselineY == 0f) return this
        val movedTexts = texts.map { it.moved(dx, baselineY) }
        val movedStrokes = strokes.map { s ->
            val src = s.points
            val dst = FloatArray(src.size)
            var i = 0
            while (i + 2 < src.size) {
                dst[i] = src[i] + dx
                dst[i + 1] = src[i + 1] + baselineY
                dst[i + 2] = src[i + 2]
                i += 3
            }
            Stroke(s.tool, s.color, s.width, dst)
        }
        return MathLayout(width, ascent, descent, movedTexts, movedStrokes)
    }

    /** Пропорционально масштабирует раскладку вокруг базовой точки (0, 0). */
    fun scaled(factor: Float): MathLayout {
        if (factor >= 0.999f) return this
        val sTexts = texts.map { it.scaled(factor, factor, 0f, 0f) }
        val sStrokes = strokes.map { st ->
            val src = st.points
            val dst = FloatArray(src.size)
            var i = 0
            while (i + 2 < src.size) {
                dst[i] = src[i] * factor
                dst[i + 1] = src[i + 1] * factor
                dst[i + 2] = src[i + 2]
                i += 3
            }
            Stroke(st.tool, st.color, max(1.2f, st.width * factor), dst)
        }
        return MathLayout(width * factor, ascent * factor, descent * factor, sTexts, sStrokes)
    }
}

/**
 * 2D-верстальщик математических формул и смешанного текста с привязкой строк к клетке/линейке.
 * Строит настоящие дроби, корни, степени/индексы, суммы/интегралы с пределами, системы и матрицы
 * из обычных [TextBox] и ровных штрихов [Tool.SHAPE].
 */
object MathTypesetter {
    private const val PRESSURE = 0.6f

    private enum class OverKind { BAR, VEC, HAT }

    private sealed interface Node {
        data class Row(val items: List<Node>) : Node
        data class Sym(val text: String, val isOperator: Boolean = false, val unaryCapable: Boolean = false) : Node
        data class Frac(val num: Node, val den: Node) : Node
        data class Sqrt(val body: Node, val index: Node? = null) : Node
        data class Scripts(val base: Node, val sup: Node? = null, val sub: Node? = null) : Node
        data class BigOp(val symbol: String, val sup: Node? = null, val sub: Node? = null, val limitsAbove: Boolean) : Node
        data class Delim(val open: String, val body: Node, val close: String) : Node
        data class Over(val kind: OverKind, val body: Node) : Node
        data class Grid(val rows: List<List<Node>>, val leftBrace: String?, val rightBracket: String? = null) : Node
        data class TextRun(val text: String) : Node
    }

    private val BINARY_OPS = setOf(
        "=", "+", "-", "−", "×", "·", "÷", "±", "∓",
        "≤", "≥", "≠", "≈", "≡", "≃", "≅", "∼", "∝",
        "→", "←", "↦", "⇒", "⇐", "⇔",
        "∈", "∉", "⊂", "⊆", "⊃", "⊇", "∪", "∩",
        "<", ">", "∧", "∨", "⊥", "∥",
    )
    private val UNARY_CAPABLE_OPS = setOf("-", "−", "+", "±", "∓")
    private val BIG_LIMITS_CMDS = mapOf("sum" to "∑", "prod" to "∏", "lim" to "lim")
    private val BIG_INLINE_CMDS = mapOf("int" to "∫", "iint" to "∬", "oint" to "∮")

    private val CMD_SYMBOLS = mapOf(
        "qquad" to "  ", "quad" to " ",
        "R" to "ℝ", "N" to "ℕ", "Z" to "ℤ", "Q" to "ℚ", "C" to "ℂ",
        "Longleftrightarrow" to "⇔", "Leftrightarrow" to "⇔", "iff" to "⇔",
        "Longrightarrow" to "⇒", "Rightarrow" to "⇒", "implies" to "⇒",
        "Longleftarrow" to "⇐", "Leftarrow" to "⇐",
        "rightarrow" to "→", "to" to "→", "mapsto" to "↦", "leftarrow" to "←",
        "cdot" to "·", "times" to "×", "div" to "÷", "pm" to "±", "mp" to "∓",
        "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥", "neq" to "≠", "ne" to "≠",
        "approx" to "≈", "equiv" to "≡", "simeq" to "≃", "cong" to "≅", "sim" to "∼", "propto" to "∝",
        "infty" to "∞", "notin" to "∉", "in" to "∈",
        "subseteq" to "⊆", "supseteq" to "⊇", "subset" to "⊂", "supset" to "⊃",
        "cup" to "∪", "cap" to "∩", "emptyset" to "∅", "varnothing" to "∅",
        "forall" to "∀", "exists" to "∃", "nexists" to "∄",
        "land" to "∧", "lor" to "∨", "lnot" to "¬", "neg" to "¬",
        "therefore" to "∴", "because" to "∵",
        "partial" to "∂", "nabla" to "∇",
        "perp" to "⊥", "parallel" to "∥", "angle" to "∠", "triangle" to "△",
        "degree" to "°", "circ" to "°",
        "cdots" to "…", "ldots" to "…", "dots" to "…",
        "ast" to "*", "star" to "★",
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "varepsilon" to "ε", "epsilon" to "ε", "zeta" to "ζ", "eta" to "η",
        "theta" to "θ", "vartheta" to "θ", "iota" to "ι", "kappa" to "κ",
        "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
        "pi" to "π", "rho" to "ρ", "sigma" to "σ", "tau" to "τ",
        "upsilon" to "υ", "varphi" to "φ", "phi" to "φ", "chi" to "χ",
        "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
        "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        "arcsin" to "arcsin", "arccos" to "arccos", "arctan" to "arctan", "arcctg" to "arcctg",
        "sinh" to "sinh", "cosh" to "cosh", "tanh" to "tanh",
        "sin" to "sin", "cos" to "cos", "tan" to "tg", "ctg" to "ctg", "cot" to "ctg", "tg" to "tg",
        "log" to "log", "ln" to "ln", "lg" to "lg", "exp" to "exp",
        "max" to "max", "min" to "min", "sup" to "sup", "inf" to "inf",
        "det" to "det", "gcd" to "gcd", "mod" to "mod",
    )

    private val BLACKBOARD = mapOf('R' to "ℝ", 'N' to "ℕ", 'Z' to "ℤ", 'Q' to "ℚ", 'C' to "ℂ")

    /** Верстает отдельную LaTeX-формулу на базовой линии y = 0. */
    fun layoutFormula(latex: String, size: Float, color: Int, metrics: MathMetrics): MathLayout {
        val node = try {
            LatexParser(latex).parseRoot()
        } catch (_: Throwable) {
            Node.TextRun(latex.trim())
        }
        return layoutNode(node, size, size, color, metrics, nestedFrac = false)
    }

    private sealed interface Segment {
        data class Text(val raw: String) : Segment
        data class InlineMath(val tex: String) : Segment
        data class DisplayMath(val tex: String) : Segment
        data object NewLine : Segment
    }

    /**
     * Верстает смешанный текст (обычные слова + инлайн/выключные LaTeX-формулы) с переносом
     * в пределах [maxWidth] и привязкой базовых линий к сетке с шагом [lineStep].
     */
    fun layoutRichLines(
        text: String,
        maxWidth: Float,
        size: Float,
        color: Int,
        metrics: MathMetrics,
        lineStep: Float,
    ): MathLayout {
        val segments = tokenizeRich(text.replace("\r\n", "\n").replace('\r', '\n'))
        if (segments.isEmpty()) return MathLayout(0f, 0f, 0f, emptyList(), emptyList())

        data class LineLine(val layout: MathLayout, val centered: Boolean)
        val builtLines = mutableListOf<LineLine>()
        val curItems = mutableListOf<MathLayout>()
        var curW = 0f
        val spaceW = metrics.width(" ", size).coerceAtLeast(size * 0.28f)

        fun flushInlineLine() {
            if (curItems.isEmpty()) return
            val placedTexts = mutableListOf<TextBox>()
            val placedStrokes = mutableListOf<Stroke>()
            var x = 0f
            var maxAsc = metrics.baselineOffset(size) * 0.82f
            var maxDesc = size * 0.25f
            for (item in curItems) {
                val p = item.place(x, 0f)
                placedTexts += p.texts
                placedStrokes += p.strokes
                x += item.width
                maxAsc = max(maxAsc, item.ascent)
                maxDesc = max(maxDesc, item.descent)
            }
            builtLines += LineLine(MathLayout(x, maxAsc, maxDesc, placedTexts, placedStrokes), centered = false)
            curItems.clear()
            curW = 0f
        }

        fun pushToken(layout: MathLayout, joinTight: Boolean = false) {
            val neededGap = if (curItems.isEmpty() || joinTight) 0f else spaceW
            if (curItems.isNotEmpty() && curW + neededGap + layout.width > maxWidth) {
                flushInlineLine()
            }
            val finalLayout = if (layout.width > maxWidth) {
                layout.scaled((maxWidth / layout.width).coerceAtLeast(0.6f))
            } else {
                layout
            }
            if (curItems.isNotEmpty() && !joinTight) {
                curItems += MathLayout(spaceW, 0f, 0f, emptyList(), emptyList())
                curW += spaceW
            }
            curItems += finalLayout
            curW += finalLayout.width
        }

        for (seg in segments) {
            when (seg) {
                is Segment.NewLine -> {
                    if (curItems.isNotEmpty()) {
                        flushInlineLine()
                    } else if (builtLines.isNotEmpty()) {
                        builtLines += LineLine(
                            MathLayout(0f, size * 0.6f, size * 0.2f, emptyList(), emptyList()),
                            centered = false,
                        )
                    }
                }
                is Segment.DisplayMath -> {
                    flushInlineLine()
                    var f = layoutFormula(seg.tex, size, color, metrics)
                    if (f.width > maxWidth) {
                        f = f.scaled((maxWidth / f.width).coerceAtLeast(0.6f))
                    }
                    builtLines += LineLine(f, centered = true)
                }
                is Segment.InlineMath -> {
                    val f = layoutFormula(seg.tex, size, color, metrics)
                    // Если следующая за формулой пунктуация шла слитно, pushToken перенесёт целиком.
                    pushToken(f, joinTight = false)
                }
                is Segment.Text -> {
                    val clean = stripMarkdown(seg.raw)
                    val words = clean.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    val attachPunct = seg.raw.isNotEmpty() && seg.raw.first() in ",.;:!?"
                    words.forEachIndexed { idx, w ->
                        val item = makeTextLayout(w, size, color, metrics)
                        val tight = idx == 0 && attachPunct && curItems.isNotEmpty()
                        pushToken(item, joinTight = tight)
                    }
                }
            }
        }
        flushInlineLine()
        if (builtLines.isEmpty()) return MathLayout(0f, 0f, 0f, emptyList(), emptyList())

        val step = if (lineStep > 1f) lineStep else size * 1.35f
        val allTexts = mutableListOf<TextBox>()
        val allStrokes = mutableListOf<Stroke>()
        var totalW = 0f
        var prevBaseline = 0f
        var prevDescent = 0f
        var firstBaseline = 0f
        var lastBottom = 0f

        builtLines.forEachIndexed { idx, line ->
            val l = line.layout
            val baseline = if (idx == 0) {
                ceilToGrid(l.ascent, step).coerceAtLeast(l.ascent).also { firstBaseline = it }
            } else {
                val delta = ceilToGrid(prevDescent + 0.3f * size + l.ascent, step)
                    .coerceAtLeast(step)
                prevBaseline + delta
            }
            val dx = if (line.centered && l.width < maxWidth) ((maxWidth - l.width) * 0.5f) else 0f
            val placed = l.place(dx, baseline)
            allTexts += placed.texts
            allStrokes += placed.strokes
            totalW = max(totalW, dx + l.width)
            prevBaseline = baseline
            prevDescent = l.descent
            lastBottom = max(lastBottom, baseline + l.descent)
        }
        return MathLayout(totalW, 0f, lastBottom, allTexts, allStrokes)
    }

    private fun ceilToGrid(y: Float, step: Float): Float =
        if (step <= 1f) y else ceil((y - 0.01f) / step).coerceAtLeast(1f) * step

    private fun stripMarkdown(s: String): String {
        var res = s.replace("**", "").replace("__", "").replace("`", "")
        val bullet = Regex("""^\s*[-*]\s+""")
        if (bullet.containsMatchIn(res)) res = bullet.replaceFirst(res, "• ")
        val heading = Regex("""^\s{0,3}#{1,6}\s+""")
        if (heading.containsMatchIn(res)) res = heading.replaceFirst(res, "")
        return res
    }

    private val BARE_MATH_CMD_RE = Regex("""\\(?:frac|dfrac|tfrac|sqrt|sum|prod|int|iint|oint|lim|vec|overline|bar|hat)\b""")
    private val CYRILLIC_RE = Regex("""[А-Яа-яЁё]""")

    private fun tokenizeRich(raw: String): List<Segment> {
        // Если модель прислала чистую LaTeX-формулу без обрамления $...$, набираем её как формулу.
        val prepared = raw.lines().joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() &&
                !trimmed.contains('$') &&
                !trimmed.contains("\\(") &&
                !trimmed.contains("\\[") &&
                !trimmed.startsWith("\\begin{") &&
                !CYRILLIC_RE.containsMatchIn(trimmed) &&
                (BARE_MATH_CMD_RE.containsMatchIn(trimmed) || (trimmed.contains('^') && trimmed.contains('=')))
            ) {
                "${'$'}$trimmed${'$'}"
            } else {
                line
            }
        }
        val out = mutableListOf<Segment>()
        val textBuf = StringBuilder()
        fun flushText() {
            if (textBuf.isNotEmpty()) {
                out += Segment.Text(textBuf.toString())
                textBuf.setLength(0)
            }
        }
        var i = 0
        val n = prepared.length
        while (i < n) {
            if (prepared.startsWith("$$", i)) {
                val close = prepared.indexOf("$$", i + 2)
                val end = if (close >= 0) close else n
                flushText()
                val tex = prepared.substring(i + 2, end).trim()
                if (tex.isNotEmpty()) out += Segment.DisplayMath(tex)
                i = if (close >= 0) close + 2 else n
                continue
            }
            if (prepared.startsWith("\\[", i)) {
                val close = prepared.indexOf("\\]", i + 2)
                val end = if (close >= 0) close else n
                flushText()
                val tex = prepared.substring(i + 2, end).trim()
                if (tex.isNotEmpty()) out += Segment.DisplayMath(tex)
                i = if (close >= 0) close + 2 else n
                continue
            }
            if (prepared.startsWith("\\(", i)) {
                val close = prepared.indexOf("\\)", i + 2)
                val end = if (close >= 0) close else n
                flushText()
                val tex = prepared.substring(i + 2, end).trim()
                if (tex.isNotEmpty()) out += Segment.InlineMath(tex)
                i = if (close >= 0) close + 2 else n
                continue
            }
            if (prepared.startsWith("\\begin{", i)) {
                val closeBrace = prepared.indexOf('}', i + 7)
                if (closeBrace > i + 7) {
                    val env = prepared.substring(i + 7, closeBrace)
                    val endTag = "\\end{$env}"
                    val close = prepared.indexOf(endTag, closeBrace + 1)
                    val end = if (close >= 0) close + endTag.length else n
                    flushText()
                    out += Segment.DisplayMath(prepared.substring(i, end))
                    i = end
                    continue
                }
            }
            if (prepared[i] == '$') {
                val close = findSingleDollarEnd(prepared, i + 1)
                if (close > i) {
                    flushText()
                    val tex = prepared.substring(i + 1, close).trim()
                    if (tex.isNotEmpty()) out += Segment.InlineMath(tex)
                    i = close + 1
                    continue
                }
            }
            if (prepared[i] == '\n') {
                flushText()
                out += Segment.NewLine
                i++
                continue
            }
            textBuf.append(prepared[i])
            i++
        }
        flushText()
        return out
    }

    private fun findSingleDollarEnd(s: String, from: Int): Int {
        var i = from
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                i += 2
                continue
            }
            if (s[i] == '$') return i
            i++
        }
        return -1
    }

    // ---------------- LaTeX парсер ----------------

    private class LatexParser(private val src: String) {
        private var i = 0
        private val n = src.length

        fun parseRoot(): Node = parseUntil(stopAtBrace = false, stopAtRight = false, stopOnGridSep = false).node

        private data class ParseResult(val node: Node, val stoppedBy: String?)

        private fun parseUntil(
            stopAtBrace: Boolean,
            stopAtRight: Boolean,
            stopOnGridSep: Boolean,
        ): ParseResult {
            val items = mutableListOf<Node>()
            while (i < n) {
                skipSpaces()
                if (i >= n) break
                val ch = src[i]
                if (stopAtBrace && ch == '}') {
                    i++
                    return ParseResult(simplifyRow(items), "}")
                }
                if (stopOnGridSep && (ch == '&' || src.startsWith("\\\\", i))) {
                    val tok = if (ch == '&') { i++; "&" } else { i += 2; "\\\\" }
                    return ParseResult(simplifyRow(items), tok)
                }
                if (stopAtRight && src.startsWith("\\right", i)) {
                    return ParseResult(simplifyRow(items), "\\right")
                }
                if (src.startsWith("\\end{", i)) {
                    return ParseResult(simplifyRow(items), "\\end")
                }
                items += parseWithScripts(stopAtRight, stopOnGridSep)
            }
            return ParseResult(simplifyRow(items), null)
        }

        private fun parseWithScripts(stopAtRight: Boolean, stopOnGridSep: Boolean): Node {
            val atom = parseAtom()
            var sup: Node? = null
            var sub: Node? = null
            while (i < n) {
                skipSpaces()
                if (i >= n) break
                when (src[i]) {
                    '^' -> {
                        i++
                        sup = parseScriptArg()
                    }
                    '_' -> {
                        i++
                        sub = parseScriptArg()
                    }
                    else -> break
                }
            }
            if (atom is Node.BigOp) {
                return if (sup != null || sub != null) atom.copy(sup = sup ?: atom.sup, sub = sub ?: atom.sub) else atom
            }
            return if (sup != null || sub != null) Node.Scripts(atom, sup, sub) else atom
        }

        private fun parseScriptArg(): Node {
            skipSpaces()
            if (i >= n) return Node.TextRun("")
            return if (src[i] == '{') parseBracedGroup() else parseAtom()
        }

        private fun parseBracedGroup(): Node {
            skipSpaces()
            if (i >= n) return Node.TextRun("")
            if (src[i] != '{') return parseAtom()
            i++
            return parseUntil(stopAtBrace = true, stopAtRight = false, stopOnGridSep = false).node
        }

        private fun parseAtom(): Node {
            skipSpaces()
            if (i >= n) return Node.TextRun("")
            val ch = src[i]
            if (ch == '{') return parseBracedGroup()
            if (ch == '}') {
                i++
                return Node.TextRun("")
            }
            if (ch == '\\') return parseCommand()
            if (ch == '(' || ch == '[') {
                val closeCh = if (ch == '(') ')' else ']'
                val saved = i
                i++
                val inner = mutableListOf<Node>()
                var closed = false
                while (i < n) {
                    skipSpaces()
                    if (i >= n) break
                    if (src[i] == closeCh) {
                        i++
                        closed = true
                        break
                    }
                    if (src[i] == '}' || src.startsWith("\\end{", i) || src.startsWith("\\right", i)) break
                    inner += parseWithScripts(stopAtRight = false, stopOnGridSep = false)
                }
                if (closed) {
                    return Node.Delim(ch.toString(), simplifyRow(inner), closeCh.toString())
                }
                i = saved + 1
                return Node.Sym(ch.toString())
            }
            i++
            val s = if (ch == '-') "−" else ch.toString()
            return Node.Sym(s, isOperator = s in BINARY_OPS, unaryCapable = s in UNARY_CAPABLE_OPS)
        }

        private fun parseCommand(): Node {
            i++ // skip '\\'
            if (i >= n) return Node.TextRun("")
            val c = src[i]
            if (!c.isLetter()) {
                i++
                return when (c) {
                    ',' -> Node.TextRun(" ")
                    ';', ':' -> Node.TextRun(" ")
                    '!' -> Node.TextRun("")
                    '{', '}', '%', '&', '_', '$' -> Node.Sym(c.toString())
                    ' ' -> Node.TextRun(" ")
                    '|' -> Node.Sym("∥", isOperator = true)
                    else -> Node.TextRun(c.toString())
                }
            }
            val start = i
            while (i < n && src[i].isLetter()) i++
            val cmd = src.substring(start, i)
            return when (cmd) {
                "frac", "dfrac", "tfrac" -> {
                    val num = parseBracedGroup()
                    val den = parseBracedGroup()
                    Node.Frac(num, den)
                }
                "sqrt" -> {
                    skipSpaces()
                    val index = if (i < n && src[i] == '[') {
                        i++
                        val idxStr = StringBuilder()
                        while (i < n && src[i] != ']') idxStr.append(src[i++])
                        if (i < n && src[i] == ']') i++
                        LatexParser(idxStr.toString()).parseRoot()
                    } else null
                    val body = parseBracedGroup()
                    Node.Sqrt(body, index)
                }
                "overline", "bar" -> Node.Over(OverKind.BAR, parseBracedGroup())
                "vec" -> Node.Over(OverKind.VEC, parseBracedGroup())
                "hat" -> Node.Over(OverKind.HAT, parseBracedGroup())
                "text", "mathrm", "mathbf", "mathit", "mathcal", "boldsymbol", "textbf", "textit", "operatorname", "boxed" -> {
                    Node.TextRun(readRawBracedText())
                }
                "mathbb" -> {
                    val raw = readRawBracedText().trim()
                    val mapped = if (raw.length == 1) BLACKBOARD[raw[0]] ?: raw else raw
                    Node.Sym(mapped)
                }
                "left" -> {
                    val open = readDelimiterToken()
                    val bodyRes = parseUntil(stopAtBrace = false, stopAtRight = true, stopOnGridSep = false)
                    val close = if (i < n && src.startsWith("\\right", i)) {
                        i += 6
                        readDelimiterToken()
                    } else ""
                    Node.Delim(if (open == ".") "" else open, bodyRes.node, if (close == ".") "" else close)
                }
                "right" -> {
                    readDelimiterToken()
                    Node.TextRun("")
                }
                "begin" -> parseEnvironment(readRawBracedText().trim())
                "end" -> {
                    readRawBracedText()
                    Node.TextRun("")
                }
                else -> {
                    BIG_LIMITS_CMDS[cmd]?.let { sym ->
                        return Node.BigOp(sym, limitsAbove = true)
                    }
                    BIG_INLINE_CMDS[cmd]?.let { sym ->
                        return Node.BigOp(sym, limitsAbove = false)
                    }
                    val sym = CMD_SYMBOLS[cmd] ?: cmd
                    Node.Sym(sym, isOperator = sym in BINARY_OPS, unaryCapable = sym in UNARY_CAPABLE_OPS)
                }
            }
        }

        private fun parseEnvironment(env: String): Node {
            val endTag = "\\end{$env}"
            val rows = mutableListOf<MutableList<Node>>()
            var curRow = mutableListOf<Node>()
            while (i < n) {
                if (src.startsWith(endTag, i)) {
                    i += endTag.length
                    break
                }
                if (src.startsWith("\\end{", i)) {
                    val close = src.indexOf('}', i)
                    i = if (close >= 0) close + 1 else n
                    break
                }
                val cellRes = parseUntil(stopAtBrace = false, stopAtRight = false, stopOnGridSep = true)
                curRow += cellRes.node
                when (cellRes.stoppedBy) {
                    "&" -> Unit
                    "\\\\" -> {
                        rows += curRow
                        curRow = mutableListOf()
                    }
                    else -> {
                        if (src.startsWith(endTag, i)) i += endTag.length
                        break
                    }
                }
            }
            if (curRow.isNotEmpty()) rows += curRow
            if (rows.isEmpty()) return Node.TextRun("")
            val (lb, rb) = when (env) {
                "cases" -> "{" to null
                "pmatrix" -> "(" to ")"
                "bmatrix" -> "[" to "]"
                "vmatrix" -> "|" to "|"
                else -> null to null
            }
            return Node.Grid(rows, lb, rb)
        }

        private fun readDelimiterToken(): String {
            skipSpaces()
            if (i >= n) return ""
            if (src[i] == '\\') {
                i++
                if (i >= n) return ""
                val ch = src[i]
                if (!ch.isLetter()) {
                    i++
                    return if (ch == '|') "∥" else ch.toString()
                }
                val st = i
                while (i < n && src[i].isLetter()) i++
                val word = src.substring(st, i)
                return CMD_SYMBOLS[word] ?: word
            }
            return src[i++].toString()
        }

        private fun readRawBracedText(): String {
            skipSpaces()
            if (i >= n) return ""
            if (src[i] != '{') return src[i++].toString()
            i++
            val sb = StringBuilder()
            var depth = 1
            while (i < n && depth > 0) {
                val ch = src[i++]
                when {
                    ch == '{' -> { depth++; sb.append(ch) }
                    ch == '}' -> { depth--; if (depth > 0) sb.append(ch) }
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        private fun skipSpaces() {
            while (i < n && src[i].isWhitespace()) i++
        }

        private fun simplifyRow(items: List<Node>): Node =
            if (items.size == 1) items[0] else Node.Row(items)
    }

    // ---------------- Рекурсивный верстальщик ----------------

    private fun layoutNode(
        node: Node,
        size: Float,
        baseSize: Float,
        color: Int,
        metrics: MathMetrics,
        nestedFrac: Boolean,
    ): MathLayout = when (node) {
        is Node.TextRun -> makeTextLayout(node.text, size, color, metrics)
        is Node.Sym -> makeTextLayout(node.text, size, color, metrics)
        is Node.Row -> layoutRow(node.items, size, baseSize, color, metrics, nestedFrac)
        is Node.Frac -> layoutFrac(node, size, baseSize, color, metrics, nestedFrac)
        is Node.Sqrt -> layoutSqrt(node, size, baseSize, color, metrics, nestedFrac)
        is Node.Scripts -> layoutScripts(node, size, baseSize, color, metrics, nestedFrac)
        is Node.BigOp -> layoutBigOp(node, size, baseSize, color, metrics, nestedFrac)
        is Node.Delim -> layoutDelim(node, size, baseSize, color, metrics, nestedFrac)
        is Node.Over -> layoutOver(node, size, baseSize, color, metrics, nestedFrac)
        is Node.Grid -> layoutGrid(node, size, baseSize, color, metrics, nestedFrac)
    }

    private fun makeTextLayout(text: String, size: Float, color: Int, metrics: MathMetrics): MathLayout {
        if (text.isEmpty()) return MathLayout(0f, 0f, 0f, emptyList(), emptyList())
        val w = metrics.width(text, size)
        val baseOff = metrics.baselineOffset(size)
        val box = TextBox(0f, -baseOff, w + max(size * 0.3f, 8f), text, size, color)
        val asc = (baseOff * 0.84f).coerceAtLeast(size * 0.68f)
        val desc = size * 0.24f
        return MathLayout(w, asc, desc, listOf(box), emptyList())
    }

    private fun layoutRow(
        items: List<Node>,
        size: Float,
        baseSize: Float,
        color: Int,
        metrics: MathMetrics,
        nestedFrac: Boolean,
    ): MathLayout {
        if (items.isEmpty()) return MathLayout(0f, 0f, 0f, emptyList(), emptyList())
        val texts = mutableListOf<TextBox>()
        val strokes = mutableListOf<Stroke>()
        var x = 0f
        var asc = size * 0.65f
        var desc = size * 0.22f

        var pendingStartX = 0f
        var pendingText = StringBuilder()
        var prevWasOperator = true

        fun flushPending() {
            if (pendingText.isEmpty()) return
            val str = pendingText.toString()
            val w = metrics.width(str, size)
            val baseOff = metrics.baselineOffset(size)
            texts += TextBox(pendingStartX, -baseOff, w + max(size * 0.3f, 8f), str, size, color)
            asc = max(asc, baseOff * 0.84f)
            desc = max(desc, size * 0.24f)
            pendingText.setLength(0)
        }

        for (item in items) {
            if (item is Node.Sym && !item.isOperator) {
                if (pendingText.isEmpty()) pendingStartX = x
                pendingText.append(item.text)
                x += metrics.width(item.text, size)
                prevWasOperator = false
                continue
            }
            if (item is Node.Sym && item.isOperator) {
                val unary = item.unaryCapable && prevWasOperator
                if (!unary) {
                    flushPending()
                    val pad = size * 0.22f
                    x += pad
                    val l = makeTextLayout(item.text, size, color, metrics).place(x, 0f)
                    texts += l.texts
                    x += l.width + pad
                    asc = max(asc, l.ascent)
                    desc = max(desc, l.descent)
                } else {
                    flushPending()
                    val padL = size * 0.06f
                    x += padL
                    val l = makeTextLayout(item.text, size, color, metrics).place(x, 0f)
                    texts += l.texts
                    x += l.width + size * 0.03f
                    asc = max(asc, l.ascent)
                    desc = max(desc, l.descent)
                }
                prevWasOperator = true
                continue
            }
            if (item is Node.TextRun && item.text.isEmpty()) continue
            if (item is Node.TextRun) {
                if (pendingText.isEmpty()) pendingStartX = x
                pendingText.append(item.text)
                x += metrics.width(item.text, size)
                prevWasOperator = false
                continue
            }
            flushPending()
            val sub = layoutNode(item, size, baseSize, color, metrics, nestedFrac)
            val placed = sub.place(x, 0f)
            texts += placed.texts
            strokes += placed.strokes
            x += sub.width
            asc = max(asc, sub.ascent)
            desc = max(desc, sub.descent)
            prevWasOperator = false
        }
        flushPending()
        return MathLayout(x, asc, desc, texts, strokes)
    }

    private fun layoutFrac(
        node: Node.Frac,
        size: Float,
        baseSize: Float,
        color: Int,
        metrics: MathMetrics,
        nestedFrac: Boolean,
    ): MathLayout {
        val childSize = max(size * if (nestedFrac) 0.75f else 0.90f, baseSize * 0.5f)
        val num = layoutNode(node.num, childSize, baseSize, color, metrics, nestedFrac = true)
        val den = layoutNode(node.den, childSize, baseSize, color, metrics, nestedFrac = true)
        val axisY = -0.28f * size
        val gap = max(2.5f, size * 0.14f)
        val lineW = max(1.6f, size * 0.06f)
        val sidePad = size * 0.08f
        val contentW = max(num.width, den.width)
        val totalW = contentW + sidePad * 2f

        val numBaseline = axisY - gap - num.descent
        val denBaseline = axisY + gap + den.ascent
        val numPlaced = num.place((totalW - num.width) * 0.5f, numBaseline)
        val denPlaced = den.place((totalW - den.width) * 0.5f, denBaseline)
        val bar = shapeStroke(color, lineW, 0f, axisY, totalW, axisY)

        val asc = max(-axisY + lineW, num.ascent - numBaseline)
        val desc = max(axisY + lineW, denBaseline + den.descent)
        return MathLayout(
            width = totalW,
            ascent = asc,
            descent = desc,
            texts = numPlaced.texts + denPlaced.texts,
            strokes = numPlaced.strokes + denPlaced.strokes + bar,
        )
    }

    private fun layoutSqrt(
        node: Node.Sqrt,
        size: Float,
        baseSize: Float,
        color: Int,
        metrics: MathMetrics,
        nestedFrac: Boolean,
    ): MathLayout {
        val body = layoutNode(node.body, size, baseSize, color, metrics, nestedFrac)
        val idxLayout = node.index?.let {
            val s = max(size * 0.70f, baseSize * 0.5f)
            layoutNode(it, s, baseSize, color, metrics, nestedFrac = true)
        }
        val hookW = max(12f, size * 0.52f)
        val padTop = max(3f, size * 0.16f)
        val topY = -body.ascent - padTop
        val botY = max(body.descent * 0.85f, size * 0.22f)
        val lineW = max(1.6f, size * 0.06f)
        val idxOffsetX = if (idxLayout != null) max(0f, idxLayout.width - hookW * 0.25f) else 0f
        val hookX = idxOffsetX
        val bodyX = hookX + hookW
        val endX = bodyX + body.width + size * 0.10f

        val radicalPts = floatArrayOf(
            hookX, -0.10f * size,
            hookX + hookW * 0.18f, -0.22f * size,
            hookX + hookW * 0.48f, botY,
            hookX + hookW * 0.90f, topY,
            endX, topY,
        )
        val radicalStroke = shapeStroke(color, lineW, *radicalPts)
        val bodyPlaced = body.place(bodyX, 0f)
        val idxPlaced = idxLayout?.place(0f, -0.34f * size - idxLayout.descent)

        val texts = (idxPlaced?.texts ?: emptyList()) + bodyPlaced.texts
        val strokes = (idxPlaced?.strokes ?: emptyList()) + bodyPlaced.strokes + radicalStroke
        val asc = max(-topY + lineW, idxPlaced?.let { it.ascent - (-0.34f * size - it.descent) } ?: 0f)
        val desc = max(body.descent, botY + lineW)
        return MathLayout(endX + size * 0.04f, asc, desc, texts, strokes)
    }

    private fun layoutScripts(
        node: Node.Scripts,
        size: Float,
        baseSize: Float,
        color: Int,
        metrics: MathMetrics,
        nestedFrac: Boolean,
    ): MathLayout {
        val base = layoutNode(node.base, size, baseSize, color, metrics, nestedFrac)
        val rawScriptSize = if (size < baseSize * 0.85f) size * 0.78f else size * 0.70f
        val scriptSize = max(rawScriptSize, baseSize * 0.50f)
        val sup = node.sup?.let { layoutNode(it, scriptSize, baseSize, color, metrics, nestedFrac = true) }
        val sub = node.sub?.let { layoutNode(it, scriptSize, baseSize, color, metrics, nestedFrac = true) }

        val gapX = size * 0.05f
        val x = base.width + gapX
        var supPlaced: MathLayout? = null
        var subPlaced: MathLayout? = null
        var asc = base.ascent
        var desc = base.descent
        var maxRight = base.width

        if (sup != null) {
            val supBase = min(-size * 0.36f, -(base.ascent * 0.56f) - sup.descent * 0.25f)
            supPlaced = sup.place(x, supBase)
            asc = max(asc, sup.ascent - supBase)
            maxRight = max(maxRight, x + sup.width)
        }
        if (sub != null) {
            val subBase = max(size * 0.28f, base.descent + scriptSize * 0.12f)
            subPlaced = sub.place(x, subBase)
            desc = max(desc, subBase + sub.descent)
            maxRight = max(maxRight, x + sub.width)
        }
        return MathLayout(
            width = maxRight + size * 0.04f,
            ascent = asc,
            descent = desc,
            texts = base.texts + (supPlaced?.texts ?: emptyList()) + (subPlaced?.texts ?: emptyList()),
            strokes = base.strokes + (supPlaced?.strokes ?: emptyList()) + (subPlaced?.strokes ?: emptyList()),
        )
    }

    private fun layoutBigOp(
        node: Node.BigOp,
        size: Float,
        baseSize: Float,
        color: Int,
        metrics: MathMetrics,
        nestedFrac: Boolean,
    ): MathLayout {
        val symSize = if (node.symbol == "lim") size else size * 1.24f
        val symLayout = makeTextLayout(node.symbol, symSize, color, metrics)
        val limitSize = max(size * 0.68f, baseSize * 0.50f)
        val sup = node.sup?.let { layoutNode(it, limitSize, baseSize, color, metrics, nestedFrac = true) }
        val sub = node.sub?.let { layoutNode(it, limitSize, baseSize, color, metrics, nestedFrac = true) }

        if (!node.limitsAbove) {
            // Интегралы: пределы справа как индексы.
            val x = symLayout.width + size * 0.06f
            val supPlaced = sup?.place(x, -symLayout.ascent * 0.72f)
            val subPlaced = sub?.place(x, symLayout.descent + limitSize * 0.40f)
            val w = max(symLayout.width, x + max(sup?.width ?: 0f, sub?.width ?: 0f)) + size * 0.14f
            val asc = max(symLayout.ascent, supPlaced?.let { sup!!.ascent + symLayout.ascent * 0.72f } ?: 0f)
            val desc = max(symLayout.descent, subPlaced?.let { symLayout.descent + limitSize * 0.40f + sub!!.descent } ?: 0f)
            return MathLayout(
                width = w,
                ascent = asc,
                descent = desc,
                texts = symLayout.texts + (supPlaced?.texts ?: emptyList()) + (subPlaced?.texts ?: emptyList()),
                strokes = symLayout.strokes + (supPlaced?.strokes ?: emptyList()) + (subPlaced?.strokes ?: emptyList()),
            )
        }

        val gapY = max(2f, size * 0.12f)
        val totalW = maxOf(symLayout.width, sup?.width ?: 0f, sub?.width ?: 0f) + size * 0.14f
        val symPlaced = symLayout.place((totalW - symLayout.width) * 0.5f, 0f)
        var asc = symLayout.ascent
        var desc = symLayout.descent
        val texts = mutableListOf<TextBox>().apply { addAll(symPlaced.texts) }
        val strokes = mutableListOf<Stroke>().apply { addAll(symPlaced.strokes) }

        if (sup != null) {
            val supBaseline = -symLayout.ascent - gapY - sup.descent
            val sp = sup.place((totalW - sup.width) * 0.5f, supBaseline)
            texts += sp.texts; strokes += sp.strokes
            asc = max(asc, sup.ascent - supBaseline)
        }
        if (sub != null) {
            val subBaseline = symLayout.descent + gapY + sub.ascent
            val sb = sub.place((totalW - sub.width) * 0.5f, subBaseline)
            texts += sb.texts; strokes += sb.strokes
            desc = max(desc, subBaseline + sub.descent)
        }
        return MathLayout(totalW, asc, desc, texts, strokes)
    }

    private fun layoutDelim(
        node: Node.Delim,
        size: Float,
        baseSize: Float,
        color: Int,
        metrics: MathMetrics,
        nestedFrac: Boolean,
    ): MathLayout {
        val body = layoutNode(node.body, size, baseSize, color, metrics, nestedFrac)
        val totalH = body.ascent + body.descent
        if (totalH <= 1.25f * size) {
            val oL = makeTextLayout(node.open, size, color, metrics)
            val cL = makeTextLayout(node.close, size, color, metrics)
            val bP = body.place(oL.width, 0f)
            val cP = cL.place(oL.width + body.width, 0f)
            return MathLayout(
                width = oL.width + body.width + cL.width,
                ascent = maxOf(body.ascent, oL.ascent, cL.ascent),
                descent = maxOf(body.descent, oL.descent, cL.descent),
                texts = oL.texts + bP.texts + cP.texts,
                strokes = bP.strokes,
            )
        }

        val topY = -body.ascent - size * 0.06f
        val botY = body.descent + size * 0.06f
        val h = botY - topY
        val dw = if (node.open.isEmpty()) 0f else delimWidth(node.open, size, h)
        val cw = if (node.close.isEmpty()) 0f else delimWidth(node.close, size, h)
        val pad = size * 0.08f
        val lineW = max(1.6f, size * 0.06f)

        val strokes = mutableListOf<Stroke>()
        if (node.open.isNotEmpty()) {
            strokes += buildDelimStroke(node.open, 0f, topY, dw, botY, lineW, color, isOpen = true)
        }
        val bPlaced = body.place(dw + pad, 0f)
        strokes += bPlaced.strokes
        val closeX = dw + pad + body.width + pad
        if (node.close.isNotEmpty()) {
            strokes += buildDelimStroke(node.close, closeX, topY, cw, botY, lineW, color, isOpen = false)
        }
        return MathLayout(
            width = closeX + cw,
            ascent = max(body.ascent, -topY + lineW),
            descent = max(body.descent, botY + lineW),
            texts = bPlaced.texts,
            strokes = strokes,
        )
    }

    private fun layoutOver(
        node: Node.Over,
        size: Float,
        baseSize: Float,
        color: Int,
        metrics: MathMetrics,
        nestedFrac: Boolean,
    ): MathLayout {
        val body = layoutNode(node.body, size, baseSize, color, metrics, nestedFrac)
        val y = -body.ascent - size * 0.14f
        val lineW = max(1.5f, size * 0.055f)
        val w = max(body.width, size * 0.4f)
        val stroke = when (node.kind) {
            OverKind.BAR -> shapeStroke(color, lineW, 0f, y, w, y)
            OverKind.VEC -> shapeStroke(
                color, lineW,
                0f, y, w, y,
                w - size * 0.16f, y - size * 0.10f,
                w, y,
                w - size * 0.16f, y + size * 0.10f,
            )
            OverKind.HAT -> shapeStroke(
                color, lineW,
                w * 0.15f, y + size * 0.10f,
                w * 0.50f, y - size * 0.08f,
                w * 0.85f, y + size * 0.10f,
            )
        }
        return MathLayout(w, max(body.ascent, -y + size * 0.14f), body.descent, body.texts, body.strokes + stroke)
    }

    private fun layoutGrid(
        node: Node.Grid,
        size: Float,
        baseSize: Float,
        color: Int,
        metrics: MathMetrics,
        nestedFrac: Boolean,
    ): MathLayout {
        val cellSize = if (node.leftBrace == null && node.rightBracket == null) size
        else max(size * 0.90f, baseSize * 0.50f)
        val laid = node.rows.map { row ->
            row.map { cell -> layoutNode(cell, cellSize, baseSize, color, metrics, nestedFrac) }
        }
        val colCount = laid.maxOfOrNull { it.size } ?: 0
        if (colCount == 0) return MathLayout(0f, 0f, 0f, emptyList(), emptyList())

        val colWidths = FloatArray(colCount)
        for (row in laid) {
            row.forEachIndexed { c, cell -> colWidths[c] = max(colWidths[c], cell.width) }
        }
        val colGap = size * 0.45f
        val totalGridW = colWidths.sum() + colGap * max(0, colCount - 1)

        val rowGap = size * 0.26f
        val rowAsc = FloatArray(laid.size)
        val rowDesc = FloatArray(laid.size)
        var totalH = 0f
        laid.forEachIndexed { r, row ->
            rowAsc[r] = row.maxOfOrNull { it.ascent } ?: size * 0.65f
            rowDesc[r] = row.maxOfOrNull { it.descent } ?: size * 0.22f
            totalH += rowAsc[r] + rowDesc[r]
            if (r > 0) totalH += rowGap
        }

        // Центрируем таблицу/систему относительно математической оси (-0.28 * size).
        val axisY = -0.28f * size
        var yTop = axisY - totalH * 0.5f
        val texts = mutableListOf<TextBox>()
        val strokes = mutableListOf<Stroke>()

        laid.forEachIndexed { r, row ->
            val baseline = yTop + rowAsc[r]
            var x = 0f
            row.forEachIndexed { c, cell ->
                val p = cell.place(x, baseline)
                texts += p.texts
                strokes += p.strokes
                x += colWidths[c] + colGap
            }
            yTop += rowAsc[r] + rowDesc[r] + rowGap
        }

        val gridLayout = MathLayout(
            width = totalGridW,
            ascent = (totalH * 0.5f - axisY).coerceAtLeast(size * 0.7f),
            descent = (totalH * 0.5f + axisY).coerceAtLeast(size * 0.25f),
            texts = texts,
            strokes = strokes,
        )
        return if (node.leftBrace != null || node.rightBracket != null) {
            val dOpen = node.leftBrace.orEmpty()
            val dClose = node.rightBracket.orEmpty()
            val topY = -gridLayout.ascent - size * 0.04f
            val botY = gridLayout.descent + size * 0.04f
            val h = botY - topY
            val ow = if (dOpen.isEmpty()) 0f else delimWidth(dOpen, size, h)
            val cw = if (dClose.isEmpty()) 0f else delimWidth(dClose, size, h)
            val pad = size * 0.12f
            val lineW = max(1.6f, size * 0.06f)
            val dStrokes = mutableListOf<Stroke>()
            if (dOpen.isNotEmpty()) dStrokes += buildDelimStroke(dOpen, 0f, topY, ow, botY, lineW, color, true)
            val shifted = gridLayout.place(ow + pad, 0f)
            dStrokes += shifted.strokes
            val closeX = ow + pad + gridLayout.width + pad
            if (dClose.isNotEmpty()) dStrokes += buildDelimStroke(dClose, closeX, topY, cw, botY, lineW, color, false)
            MathLayout(closeX + cw, max(gridLayout.ascent, -topY + lineW), max(gridLayout.descent, botY + lineW), shifted.texts, dStrokes)
        } else {
            gridLayout
        }
    }

    private fun delimWidth(d: String, size: Float, height: Float): Float = when (d) {
        "|", "∥" -> max(6f, size * 0.24f)
        "{", "}" -> max(11f, min(size * 0.52f, height * 0.18f))
        else -> max(9f, min(size * 0.45f, height * 0.15f))
    }

    private fun buildDelimStroke(
        delim: String,
        x0: Float,
        topY: Float,
        w: Float,
        botY: Float,
        lineW: Float,
        color: Int,
        isOpen: Boolean,
    ): List<Stroke> {
        val midY = (topY + botY) * 0.5f
        return when (delim) {
            "[", "]" -> {
                val edgeX = if (isOpen) x0 + w * 0.2f else x0 + w * 0.8f
                val lipX = if (isOpen) x0 + w * 0.9f else x0 + w * 0.1f
                listOf(shapeStroke(color, lineW, lipX, topY, edgeX, topY, edgeX, botY, lipX, botY))
            }
            "|", "∥" -> {
                val cx = x0 + w * 0.5f
                listOf(shapeStroke(color, lineW, cx, topY, cx, botY))
            }
            "{", "}" -> {
                val outerX = if (isOpen) x0 + w * 0.10f else x0 + w * 0.90f
                val stemX = if (isOpen) x0 + w * 0.52f else x0 + w * 0.48f
                val tipX = if (isOpen) x0 + w * 0.92f else x0 + w * 0.08f
                val pts = mutableListOf<Float>()
                val steps = 10
                val r = min((botY - topY) * 0.18f, w * 0.42f)
                // Верхняя дуга к острому центру
                pts += tipX; pts += topY
                for (k in 1..steps) {
                    val t = k / steps.toFloat()
                    val ang = t * (PI.toFloat() * 0.5f)
                    val x = tipX + (stemX - tipX) * sin(ang)
                    val y = topY + r * (1f - cos(ang))
                    pts += x; pts += y
                }
                pts += stemX; pts += midY - r
                pts += outerX; pts += midY
                pts += stemX; pts += midY + r
                for (k in 0..steps) {
                    val t = k / steps.toFloat()
                    val ang = t * (PI.toFloat() * 0.5f)
                    val x = stemX + (tipX - stemX) * (1f - cos(ang))
                    val y = (botY - r) + r * sin(ang)
                    pts += x; pts += y
                }
                listOf(shapeStroke(color, lineW, *pts.toFloatArray()))
            }
            else -> { // "(" или ")"
                val tipX = if (isOpen) x0 + w * 0.88f else x0 + w * 0.12f
                val bellyX = if (isOpen) x0 + w * 0.14f else x0 + w * 0.86f
                val pts = FloatArray(17 * 2)
                for (k in 0..16) {
                    val t = k / 16f
                    val y = topY + (botY - topY) * t
                    val bow = sin(t * PI).toFloat()
                    val x = tipX + (bellyX - tipX) * bow
                    pts[k * 2] = x
                    pts[k * 2 + 1] = y
                }
                listOf(shapeStroke(color, lineW, *pts))
            }
        }
    }

    private fun shapeStroke(color: Int, width: Float, vararg xy: Float): Stroke =
        Stroke(Tool.SHAPE, color, width, Shapes.densify(xy, PRESSURE))
}
