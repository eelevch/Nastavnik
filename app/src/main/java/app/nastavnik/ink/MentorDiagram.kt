package app.nastavnik.ink

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Безопасный рекурсивный вычислитель математических выражений от переменной `x`
 * для построения графиков наставником (`plot "..."`).
 */
object ExprEval {
    private sealed interface Token {
        data class Num(val v: Double) : Token
        data class Ident(val name: String) : Token
        data class Op(val c: Char) : Token
        data object LParen : Token
        data object RParen : Token
    }

    fun compile(src: String): ((Double) -> Double)? {
        val tokens = tokenize(src) ?: return null
        if (tokens.isEmpty()) return null
        val parser = Parser(tokens)
        val expr = parser.parseExpr() ?: return null
        if (!parser.atEnd()) return null
        return { x ->
            try {
                expr(x)
            } catch (_: Throwable) {
                Double.NaN
            }
        }
    }

    private fun tokenize(raw: String): List<Token>? {
        val src = raw.replace("−", "-").replace("·", "*").replace("π", "pi")
        val out = mutableListOf<Token>()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            if (c.isWhitespace()) { i++; continue }
            if (c.isDigit() || c == '.') {
                val st = i
                while (i < src.length && (src[i].isDigit() || src[i] == '.')) i++
                val v = src.substring(st, i).toDoubleOrNull() ?: return null
                out += Token.Num(v)
                if (i < src.length && (src[i].isLetter() || src[i] == '(')) {
                    out += Token.Op('*')
                }
                continue
            }
            if (c.isLetter()) {
                val st = i
                while (i < src.length && src[i].isLetter()) i++
                val word = src.substring(st, i).lowercase()
                // Поддержка записей вида "2x", "x(x-1)" и др.
                if (word == "x" || word == "e" || word == "pi") {
                    out += Token.Ident(word)
                    if (i < src.length && src[i] == '(') out += Token.Op('*')
                } else {
                    out += Token.Ident(word)
                }
                continue
            }
            when (c) {
                '(' -> out += Token.LParen
                ')' -> {
                    out += Token.RParen
                    if (i + 1 < src.length && (src[i + 1].isLetterOrDigit() || src[i + 1] == '(')) {
                        out += Token.Op('*')
                    }
                }
                '+', '-', '*', '/', '^' -> out += Token.Op(c)
                else -> return null
            }
            i++
        }
        return out
    }

    private class Parser(private val t: List<Token>) {
        private var p = 0

        fun atEnd() = p >= t.size

        fun parseExpr(): ((Double) -> Double)? {
            var left = parseTerm() ?: return null
            while (p < t.size) {
                val op = t[p] as? Token.Op ?: break
                if (op.c != '+' && op.c != '-') break
                p++
                val right = parseTerm() ?: return null
                val l = left
                left = if (op.c == '+') ({ x -> l(x) + right(x) }) else ({ x -> l(x) - right(x) })
            }
            return left
        }

        private fun parseTerm(): ((Double) -> Double)? {
            var left = parseUnary() ?: return null
            while (p < t.size) {
                val op = t[p] as? Token.Op ?: break
                if (op.c != '*' && op.c != '/') break
                p++
                val right = parseUnary() ?: return null
                val l = left
                left = if (op.c == '*') ({ x -> l(x) * right(x) }) else ({ x -> l(x) / right(x) })
            }
            return left
        }

        private fun parseUnary(): ((Double) -> Double)? {
            val op = t.getOrNull(p) as? Token.Op
            if (op != null && (op.c == '-' || op.c == '+')) {
                p++
                val inner = parseUnary() ?: return null
                return if (op.c == '-') ({ x -> -inner(x) }) else inner
            }
            return parsePower()
        }

        private fun parsePower(): ((Double) -> Double)? {
            val base = parsePrimary() ?: return null
            val op = t.getOrNull(p) as? Token.Op
            if (op != null && op.c == '^') {
                p++
                val expFn = parseUnary() ?: return null
                return { x -> base(x).pow(expFn(x)) }
            }
            return base
        }

        private fun parsePrimary(): ((Double) -> Double)? {
            val tok = t.getOrNull(p) ?: return null
            return when (tok) {
                is Token.Num -> { p++; { tok.v } }
                is Token.LParen -> {
                    p++
                    val e = parseExpr() ?: return null
                    if (t.getOrNull(p) !is Token.RParen) return null
                    p++
                    e
                }
                is Token.Ident -> {
                    p++
                    when (tok.name) {
                        "x" -> { x -> x }
                        "pi" -> { _ -> PI }
                        "e" -> { _ -> Math.E }
                        else -> {
                            val fn = unaryFunc(tok.name) ?: return null
                            if (t.getOrNull(p) !is Token.LParen) return null
                            p++
                            val arg = parseExpr() ?: return null
                            if (t.getOrNull(p) !is Token.RParen) return null
                            p++
                            { x -> fn(arg(x)) }
                        }
                    }
                }
                else -> null
            }
        }

        private fun unaryFunc(name: String): ((Double) -> Double)? = when (name) {
            "sin" -> ::sin
            "cos" -> ::cos
            "tan", "tg" -> ::tan
            "ctg", "cot" -> { v -> 1.0 / tan(v) }
            "sqrt" -> ::sqrt
            "abs" -> ::abs
            "ln" -> ::ln
            "log" -> ::log10
            "exp" -> ::exp
            "asin", "arcsin" -> ::asin
            "acos", "arccos" -> ::acos
            "atan", "arctg", "arctan" -> ::atan
            else -> null
        }
    }
}

/**
 * Конструктор ровных чертежей, схем, графов и графиков функций на странице тетради.
 * Парсит построчный DSL команды `@@ diagram`, аккуратно вписывает в клетку, округляет точки до 0.5 px
 * и использует плотное разбиение окружностей/дуг (>= 48 точек на полный оборот).
 */
object MentorDiagram {
    private const val PRESSURE = 0.6f
    private const val MAIN_W = 3.0f
    private const val AUX_W = 2.0f
    private const val MIN_CIRCLE_SEGMENTS = 72
    private const val MAX_ARC_STEP_DEG = 4.0

    private data class AxesSpec(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val xmin: Double,
        val xmax: Double,
        val ymin: Double,
        val ymax: Double,
    )

    private data class NodeRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val cx get() = (left + right) * 0.5f
        val cy get() = (top + bottom) * 0.5f
    }

    /** Извлекает заявленные локальные размеры `(W, H)` и необязательный `title` без полной сборки. */
    fun inspectHeader(src: String): Triple<Float, Float, String?> {
        var w = 100f
        var h = 60f
        var title: String? = null
        for (rawLine in src.lines()) {
            val tokens = splitCmd(rawLine)
            if (tokens.isEmpty()) continue
            when (tokens[0].lowercase()) {
                "size" -> if (tokens.size >= 3) {
                    w = tokens[1].toFloatOrNull()?.coerceIn(20f, 1000f) ?: w
                    h = tokens[2].toFloatOrNull()?.coerceIn(20f, 1000f) ?: h
                }
                "title" -> if (tokens.size >= 2) title = tokens[1]
            }
        }
        return Triple(w, h, title)
    }

    fun build(
        src: String,
        slot: MentorNotebookWriter.SlotBox,
        color: Int,
        metrics: MathMetrics = EstimateMetrics,
    ): Pair<List<Stroke>, List<TextBox>> {
        val (locW, locH, title) = inspectHeader(src)
        val titleSpace = if (!title.isNullOrBlank()) 44f else 0f
        val drawH = (slot.height - titleSpace).coerceAtLeast(48f)

        val ox = ceil(slot.left / PageDrawer.CELL) * PageDrawer.CELL
        val oy = ceil(slot.top / PageDrawer.CELL) * PageDrawer.CELL
        val availW = (slot.right - ox).coerceAtLeast(40f)
        val availH = (slot.top + drawH - oy).coerceAtLeast(40f)

        val rawScale = min(availW / locW, availH / locH)
        val scale = pickGridAlignedScale(rawScale, locW, locH, availW, availH)
        val drawnW = locW * scale
        val originX = roundHalf(ox + max(0f, (availW - drawnW) * 0.5f))
        val originY = roundHalf(oy)

        fun px(lx: Float): Float = originX + lx * scale
        fun py(ly: Float): Float = originY + ly * scale

        val strokes = mutableListOf<Stroke>()
        val texts = mutableListOf<TextBox>()
        val nodes = linkedMapOf<String, NodeRect>()
        var lastAxes: AxesSpec? = null

        fun addRichTextCentered(text: String, centerX: Float, centerY: Float, textSize: Float = 18f, maxW: Float = 260f): Pair<Float, Float> {
            val layout = MathTypesetter.layoutRichLines(text, maxW, textSize, color, metrics, step(textSize))
            val w = layout.width.coerceAtLeast(textSize)
            val h = layout.descent.coerceAtLeast(textSize * 1.1f)
            val placed = layout.place(roundHalf(centerX - w * 0.5f), roundHalf(centerY - h * 0.5f))
            texts += placed.texts
            strokes += placed.strokes
            return w to h
        }

        fun addRichTextAt(text: String, leftX: Float, topY: Float, textSize: Float = 18f, maxW: Float = 280f) {
            val layout = MathTypesetter.layoutRichLines(text, maxW, textSize, color, metrics, step(textSize))
            val placed = layout.place(roundHalf(leftX), roundHalf(topY))
            texts += placed.texts
            strokes += placed.strokes
        }

        for (rawLine in src.lines()) {
            val tokens = splitCmd(rawLine)
            if (tokens.isEmpty()) continue
            val cmd = tokens[0].lowercase()
            when (cmd) {
                "size", "title" -> Unit
                "line" -> if (tokens.size >= 5) {
                    val x1 = px(num(tokens, 1) ?: continue); val y1 = py(num(tokens, 2) ?: continue)
                    val x2 = px(num(tokens, 3) ?: continue); val y2 = py(num(tokens, 4) ?: continue)
                    val dashed = tokens.drop(5).any { it.equals("dashed", ignoreCase = true) }
                    if (dashed) strokes += dashedLine(color, AUX_W, x1, y1, x2, y2)
                    else strokes += segStroke(color, MAIN_W, x1, y1, x2, y2)
                }
                "arrow" -> if (tokens.size >= 5) {
                    val x1 = px(num(tokens, 1) ?: continue); val y1 = py(num(tokens, 2) ?: continue)
                    val x2 = px(num(tokens, 3) ?: continue); val y2 = py(num(tokens, 4) ?: continue)
                    val dashed = tokens.drop(5).any { it.equals("dashed", ignoreCase = true) }
                    if (dashed) strokes += dashedLine(color, AUX_W, x1, y1, x2, y2)
                    else strokes += segStroke(color, MAIN_W, x1, y1, x2, y2)
                    strokes += arrowHead(color, MAIN_W, x1, y1, x2, y2)
                }
                "polyline", "polygon" -> if (tokens.size >= 5) {
                    val pts = mutableListOf<Float>()
                    var k = 1
                    while (k + 1 < tokens.size) {
                        val lx = tokens[k].toFloatOrNull()
                        val ly = tokens[k + 1].toFloatOrNull()
                        if (lx != null && ly != null) {
                            pts += roundHalf(px(lx))
                            pts += roundHalf(py(ly))
                        }
                        k += 2
                    }
                    if (pts.size >= 4) {
                        if (cmd == "polygon") {
                            pts += pts[0]; pts += pts[1]
                        }
                        strokes += strokeXY(color, MAIN_W, pts.toFloatArray())
                    }
                }
                "rect" -> if (tokens.size >= 5) {
                    val x = px(num(tokens, 1) ?: continue); val y = py(num(tokens, 2) ?: continue)
                    val rw = (num(tokens, 3) ?: continue) * scale; val rh = (num(tokens, 4) ?: continue) * scale
                    val x2 = x + rw; val y2 = y + rh
                    strokes += segStroke(color, MAIN_W, x, y, x2, y, x2, y2, x, y2, x, y)
                    tokens.getOrNull(5)?.takeIf { it.isNotBlank() }?.let { lbl ->
                        addRichTextCentered(lbl, (x + x2) * 0.5f, (y + y2) * 0.5f, 18f, maxW = (rw - 12f).coerceAtLeast(60f))
                    }
                }
                "circle" -> if (tokens.size >= 4) {
                    val cx = px(num(tokens, 1) ?: continue); val cy = py(num(tokens, 2) ?: continue)
                    val r = (num(tokens, 3) ?: continue) * scale
                    strokes += arcPoints(color, MAIN_W, cx, cy, r, r, 0.0, 360.0)
                }
                "ellipse" -> if (tokens.size >= 5) {
                    val cx = px(num(tokens, 1) ?: continue); val cy = py(num(tokens, 2) ?: continue)
                    val rx = (num(tokens, 3) ?: continue) * scale; val ry = (num(tokens, 4) ?: continue) * scale
                    strokes += arcPoints(color, MAIN_W, cx, cy, rx, ry, 0.0, 360.0)
                }
                "arc" -> if (tokens.size >= 6) {
                    val cx = px(num(tokens, 1) ?: continue); val cy = py(num(tokens, 2) ?: continue)
                    val r = (num(tokens, 3) ?: continue) * scale
                    val a0 = (num(tokens, 4) ?: continue).toDouble()
                    val a1 = (num(tokens, 5) ?: continue).toDouble()
                    strokes += arcPoints(color, MAIN_W, cx, cy, r, r, a0, a1)
                }
                "point" -> if (tokens.size >= 3) {
                    val x = px(num(tokens, 1) ?: continue); val y = py(num(tokens, 2) ?: continue)
                    strokes += arcPoints(color, 3.6f, x, y, 3.5f, 3.5f, 0.0, 360.0)
                    tokens.getOrNull(3)?.takeIf { it.isNotBlank() }?.let { lbl ->
                        addRichTextAt(lbl, x + 7f, y - 22f, 18f)
                    }
                }
                "text" -> if (tokens.size >= 4) {
                    val x = px(num(tokens, 1) ?: continue); val y = py(num(tokens, 2) ?: continue)
                    addRichTextAt(tokens[3], x, y - 16f, 18f)
                }
                "dim" -> if (tokens.size >= 6) {
                    val x1 = px(num(tokens, 1) ?: continue); val y1 = py(num(tokens, 2) ?: continue)
                    val x2 = px(num(tokens, 3) ?: continue); val y2 = py(num(tokens, 4) ?: continue)
                    val lbl = tokens[5]
                    strokes += segStroke(color, AUX_W, x1, y1, x2, y2)
                    strokes += arrowHead(color, AUX_W, x1, y1, x2, y2)
                    strokes += arrowHead(color, AUX_W, x2, y2, x1, y1)
                    val len = hypot(x2 - x1, y2 - y1).coerceAtLeast(1f)
                    val nx = -(y2 - y1) / len; val ny = (x2 - x1) / len
                    val mx = (x1 + x2) * 0.5f + nx * 16f
                    val my = (y1 + y2) * 0.5f + ny * 16f
                    addRichTextCentered(lbl, mx, my, 17f)
                }
                "angle" -> if (tokens.size >= 7) {
                    val vx = px(num(tokens, 1) ?: continue); val vy = py(num(tokens, 2) ?: continue)
                    val ax = px(num(tokens, 3) ?: continue); val ay = py(num(tokens, 4) ?: continue)
                    val bx = px(num(tokens, 5) ?: continue); val by = py(num(tokens, 6) ?: continue)
                    val a1 = atan2((ay - vy).toDouble(), (ax - vx).toDouble()) * 180.0 / PI
                    var a2 = atan2((by - vy).toDouble(), (bx - vx).toDouble()) * 180.0 / PI
                    var diff = ((a2 - a1) % 360.0 + 360.0) % 360.0
                    if (diff > 180.0) {
                        a2 = a1 - (360.0 - diff)
                    } else {
                        a2 = a1 + diff
                    }
                    val legMin = min(hypot(ax - vx, ay - vy), hypot(bx - vx, by - vy))
                    val r = (legMin * 0.28f).coerceIn(14f, 36f)
                    strokes += arcPoints(color, AUX_W, vx, vy, r, r, a1, a2)
                    tokens.getOrNull(7)?.takeIf { it.isNotBlank() }?.let { lbl ->
                        val midRad = ((a1 + a2) * 0.5) * PI / 180.0
                        addRichTextCentered(
                            lbl,
                            vx + (cos(midRad) * (r + 14f)).toFloat(),
                            vy + (sin(midRad) * (r + 14f)).toFloat(),
                            17f,
                        )
                    }
                }
                "node" -> if (tokens.size >= 5) {
                    val id = tokens[1]
                    val cx = roundHalf(px(num(tokens, 2) ?: continue))
                    val cy = roundHalf(py(num(tokens, 3) ?: continue))
                    val lbl = tokens[4]
                    val layout = MathTypesetter.layoutRichLines(lbl, 220f, 18f, color, metrics, step(18f))
                    val boxW = max(92f, layout.width + 26f)
                    val boxH = max(44f, layout.descent + 18f)
                    val l = roundHalf(cx - boxW * 0.5f)
                    val r = roundHalf(cx + boxW * 0.5f)
                    val t = roundHalf(cy - boxH * 0.5f)
                    val b = roundHalf(cy + boxH * 0.5f)
                    strokes += roundedRect(color, MAIN_W, l, t, r, b, 10f)
                    val placed = layout.place(roundHalf(cx - layout.width * 0.5f), roundHalf(cy - layout.descent * 0.5f))
                    texts += placed.texts
                    strokes += placed.strokes
                    nodes[id] = NodeRect(l, t, r, b)
                }
                "link" -> if (tokens.size >= 3) {
                    val n1 = nodes[tokens[1]] ?: continue
                    val n2 = nodes[tokens[2]] ?: continue
                    val (x1, y1) = rectExitPoint(n1, n2.cx, n2.cy)
                    val (x2, y2) = rectExitPoint(n2, n1.cx, n1.cy)
                    strokes += segStroke(color, MAIN_W, x1, y1, x2, y2)
                    strokes += arrowHead(color, MAIN_W, x1, y1, x2, y2)
                    tokens.getOrNull(3)?.takeIf { it.isNotBlank() }?.let { lbl ->
                        addRichTextCentered(lbl, (x1 + x2) * 0.5f, (y1 + y2) * 0.5f - 14f, 16f)
                    }
                }
                "axes" -> if (tokens.size >= 9) {
                    val l = px(num(tokens, 1) ?: continue)
                    val t = py(num(tokens, 2) ?: continue)
                    val r = px(num(tokens, 3) ?: continue)
                    val b = py(num(tokens, 4) ?: continue)
                    val xmin = num(tokens, 5)?.toDouble() ?: continue
                    val xmax = num(tokens, 6)?.toDouble() ?: continue
                    val ymin = num(tokens, 7)?.toDouble() ?: continue
                    val ymax = num(tokens, 8)?.toDouble() ?: continue
                    if (xmax <= xmin || ymax <= ymin || r <= l || b <= t) continue
                    val spec = AxesSpec(l, t, r, b, xmin, xmax, ymin, ymax)
                    lastAxes = spec
                    val x0 = if (0.0 in xmin..xmax) mapX(spec, 0.0) else l
                    val y0 = if (0.0 in ymin..ymax) mapY(spec, 0.0) else b
                    // Ось X и ось Y со стрелками
                    strokes += segStroke(color, AUX_W, l, y0, r, y0)
                    strokes += arrowHead(color, AUX_W, l, y0, r, y0)
                    strokes += segStroke(color, AUX_W, x0, b, x0, t)
                    strokes += arrowHead(color, AUX_W, x0, b, x0, t)

                    // Круглые деления (1, 2, 5 * 10^k)
                    val xStep = niceTickStep(xmin, xmax, 5)
                    var tv = ceil(xmin / xStep) * xStep
                    while (tv <= xmax + xStep * 1e-6) {
                        if (abs(tv) > xStep * 0.15) {
                            val tx = mapX(spec, tv)
                            if (tx in (l + 8f)..(r - 14f)) {
                                strokes += segStroke(color, AUX_W, tx, y0 - 5f, tx, y0 + 5f)
                                addRichTextCentered(fmtTick(tv), tx, y0 + 16f, 14f, 56f)
                            }
                        }
                        tv += xStep
                    }
                    val yStep = niceTickStep(ymin, ymax, 5)
                    tv = ceil(ymin / yStep) * yStep
                    while (tv <= ymax + yStep * 1e-6) {
                        if (abs(tv) > yStep * 0.15) {
                            val ty = mapY(spec, tv)
                            if (ty in (t + 14f)..(b - 8f)) {
                                strokes += segStroke(color, AUX_W, x0 - 5f, ty, x0 + 5f, ty)
                                addRichTextCentered(fmtTick(tv), x0 - 18f, ty, 14f, 56f)
                            }
                        }
                        tv += yStep
                    }
                    val xLabel = tokens.getOrNull(9)?.takeIf { it.isNotBlank() } ?: "x"
                    val yLabel = tokens.getOrNull(10)?.takeIf { it.isNotBlank() } ?: "y"
                    addRichTextCentered(xLabel, r - 6f, y0 - 14f, 17f)
                    addRichTextCentered(yLabel, x0 + 14f, t + 6f, 17f)
                }
                "plot" -> if (tokens.size >= 2) {
                    val ax = lastAxes ?: continue
                    val fn = ExprEval.compile(tokens[1]) ?: continue
                    val extraNums = mutableListOf<Double>()
                    var dashed = false
                    for (t in tokens.drop(2)) {
                        val d = t.toDoubleOrNull()
                        if (d != null) extraNums += d
                        else if (t.equals("dashed", ignoreCase = true)) dashed = true
                    }
                    val pxMin = extraNums.getOrNull(0)?.coerceIn(ax.xmin, ax.xmax) ?: ax.xmin
                    val pxMax = extraNums.getOrNull(1)?.coerceIn(ax.xmin, ax.xmax) ?: ax.xmax
                    if (pxMax <= pxMin) continue
                    val ySpan = ax.ymax - ax.ymin
                    val yLo = ax.ymin - ySpan * 0.10
                    val yHi = ax.ymax + ySpan * 0.10

                    val samples = 220
                    var seg = mutableListOf<Float>()
                    var prevPy: Float? = null
                    fun flushPlotSeg() {
                        if (seg.size >= 4) {
                            if (dashed) {
                                val arr = seg.toFloatArray()
                                strokes += dashedPolyline(color, MAIN_W, arr)
                            } else {
                                strokes += strokeXY(color, MAIN_W, seg.toFloatArray())
                            }
                        }
                        seg = mutableListOf()
                        prevPy = null
                    }
                    for (k in 0..samples) {
                        val vx = pxMin + (pxMax - pxMin) * k / samples
                        val vy = fn(vx)
                        if (!vy.isFinite() || vy < yLo || vy > yHi) {
                            flushPlotSeg()
                            continue
                        }
                        val sx = mapX(ax, vx)
                        val sy = mapY(ax, vy.coerceIn(ax.ymin, ax.ymax)).coerceIn(ax.top, ax.bottom)
                        // Разрыв при резком скачке (асимптота внутри шага сетки)
                        if (prevPy != null && abs(sy - prevPy!!) > (ax.bottom - ax.top) * 0.65f) {
                            flushPlotSeg()
                        }
                        seg += roundHalf(sx); seg += roundHalf(sy)
                        prevPy = sy
                    }
                    flushPlotSeg()
                }
                "fpoint" -> if (tokens.size >= 3) {
                    val ax = lastAxes ?: continue
                    val vx = tokens[1].toDoubleOrNull() ?: continue
                    val vy = tokens[2].toDoubleOrNull() ?: continue
                    val sx = mapX(ax, vx).coerceIn(ax.left, ax.right)
                    val sy = mapY(ax, vy).coerceIn(ax.top, ax.bottom)
                    strokes += arcPoints(color, 3.6f, sx, sy, 3.5f, 3.5f, 0.0, 360.0)
                    tokens.getOrNull(3)?.takeIf { it.isNotBlank() }?.let { lbl ->
                        addRichTextAt(lbl, sx + 7f, sy - 22f, 17f)
                    }
                }
            }
        }

        if (!title.isNullOrBlank()) {
            addRichTextCentered(
                title,
                originX + drawnW * 0.5f,
                originY + locH * scale + 20f,
                18f,
                maxW = max(drawnW, 240f),
            )
        }
        return strokes to texts
    }

    private fun pickGridAlignedScale(rawScale: Float, locW: Float, locH: Float, availW: Float, availH: Float): Float {
        val halfCell = PageDrawer.CELL * 0.5f
        val maxK = floor(min(availW / locW, availH / locH) * 10f / halfCell).toInt()
        if (maxK >= 1) {
            val snapped = maxK * halfCell / 10f
            if (rawScale > 0f && abs(rawScale - snapped) / rawScale <= 0.15f) return snapped
        }
        return rawScale
    }

    private fun step(textSize: Float): Float = textSize * 1.32f

    private fun num(tokens: List<String>, idx: Int): Float? = tokens.getOrNull(idx)?.toFloatOrNull()

    private fun roundHalf(v: Float): Float = round(v * 2f) * 0.5f

    private fun mapX(ax: AxesSpec, v: Double): Float =
        roundHalf(ax.left + ((v - ax.xmin) / (ax.xmax - ax.xmin) * (ax.right - ax.left)).toFloat())

    private fun mapY(ax: AxesSpec, v: Double): Float =
        roundHalf(ax.bottom - ((v - ax.ymin) / (ax.ymax - ax.ymin) * (ax.bottom - ax.top)).toFloat())

    private fun niceTickStep(minV: Double, maxV: Double, targetTicks: Int): Double {
        val raw = (maxV - minV) / targetTicks.coerceAtLeast(2)
        if (raw <= 0.0) return 1.0
        val pow10 = 10.0.pow(floor(log10(raw)))
        val frac = raw / pow10
        val nice = when {
            frac <= 1.5 -> 1.0
            frac <= 3.5 -> 2.0
            frac <= 7.5 -> 5.0
            else -> 10.0
        }
        return nice * pow10
    }

    private fun fmtTick(v: Double): String {
        val r = v.roundToInt()
        return if (abs(v - r) < 1e-6) r.toString()
        else String.format(java.util.Locale.US, "%.1f", v).trimEnd('0').trimEnd('.')
    }

    private fun segStroke(color: Int, w: Float, vararg xy: Float): Stroke {
        val rounded = FloatArray(xy.size) { roundHalf(xy[it]) }
        return Stroke(Tool.SHAPE, color, w, Shapes.densify(rounded, PRESSURE))
    }

    private fun strokeXY(color: Int, w: Float, xy: FloatArray): Stroke {
        val rounded = FloatArray(xy.size) { roundHalf(xy[it]) }
        return Stroke(Tool.SHAPE, color, w, Shapes.densify(rounded, PRESSURE))
    }

    private fun arrowHead(color: Int, w: Float, x0: Float, y0: Float, x1: Float, y1: Float): Stroke {
        val a = atan2(y1 - y0, x1 - x0)
        val len = 14f.coerceAtMost(hypot(x1 - x0, y1 - y0) * 0.45f).coerceAtLeast(6f)
        val spread = (26.0 * PI / 180.0).toFloat()
        val ax = roundHalf(x1 - cos(a - spread) * len)
        val ay = roundHalf(y1 - sin(a - spread) * len)
        val bx = roundHalf(x1 - cos(a + spread) * len)
        val by = roundHalf(y1 - sin(a + spread) * len)
        return segStroke(color, w, ax, ay, x1, y1, bx, by)
    }

    private fun arcPoints(
        color: Int,
        w: Float,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        fromDeg: Double,
        toDeg: Double,
    ): Stroke {
        val span = abs(toDeg - fromDeg)
        val byStep = ceil(span / MAX_ARC_STEP_DEG).toInt().coerceAtLeast(8)
        val segments = if (span >= 359.0) max(MIN_CIRCLE_SEGMENTS, byStep) else byStep
        val xy = FloatArray((segments + 1) * 2)
        for (i in 0..segments) {
            val deg = fromDeg + (toDeg - fromDeg) * i / segments
            val rad = deg * PI / 180.0
            xy[i * 2] = roundHalf(cx + (cos(rad) * rx).toFloat())
            xy[i * 2 + 1] = roundHalf(cy + (sin(rad) * ry).toFloat())
        }
        return strokeXY(color, w, xy)
    }

    private fun roundedRect(color: Int, w: Float, l: Float, t: Float, r: Float, b: Float, rad: Float): Stroke {
        val cr = min(rad, min((r - l) * 0.35f, (b - t) * 0.35f))
        val pts = mutableListOf<Float>()
        fun corner(cx: Float, cy: Float, a0: Double, a1: Double) {
            for (k in 0..5) {
                val a = (a0 + (a1 - a0) * k / 5.0) * PI / 180.0
                pts += roundHalf(cx + (cos(a) * cr).toFloat())
                pts += roundHalf(cy + (sin(a) * cr).toFloat())
            }
        }
        corner(r - cr, t + cr, -90.0, 0.0)
        corner(r - cr, b - cr, 0.0, 90.0)
        corner(l + cr, b - cr, 90.0, 180.0)
        corner(l + cr, t + cr, 180.0, 270.0)
        pts += pts[0]; pts += pts[1]
        return strokeXY(color, w, pts.toFloatArray())
    }

    private fun rectExitPoint(box: NodeRect, targetX: Float, targetY: Float): Pair<Float, Float> {
        val dx = targetX - box.cx
        val dy = targetY - box.cy
        if (abs(dx) < 1e-3f && abs(dy) < 1e-3f) return box.cx to box.cy
        val hw = (box.right - box.left) * 0.5f
        val hh = (box.bottom - box.top) * 0.5f
        val tx = if (abs(dx) > 1e-3f) hw / abs(dx) else Float.MAX_VALUE
        val ty = if (abs(dy) > 1e-3f) hh / abs(dy) else Float.MAX_VALUE
        val t = min(tx, ty)
        return roundHalf(box.cx + dx * t) to roundHalf(box.cy + dy * t)
    }

    private fun dashedLine(color: Int, w: Float, x1: Float, y1: Float, x2: Float, y2: Float): List<Stroke> {
        val len = hypot(x2 - x1, y2 - y1)
        if (len < 4f) return listOf(segStroke(color, w, x1, y1, x2, y2))
        val ux = (x2 - x1) / len; val uy = (y2 - y1) / len
        val dash = 10f; val gap = 7f
        val out = mutableListOf<Stroke>()
        var d = 0f
        while (d < len) {
            val end = min(len, d + dash)
            out += segStroke(color, w, x1 + ux * d, y1 + uy * d, x1 + ux * end, y1 + uy * end)
            d += dash + gap
        }
        return out
    }

    private fun dashedPolyline(color: Int, w: Float, xy: FloatArray): List<Stroke> {
        val out = mutableListOf<Stroke>()
        var i = 0
        while (i + 3 < xy.size) {
            if (i % 4 == 0) {
                out += segStroke(color, w, xy[i], xy[i + 1], xy[i + 2], xy[i + 3])
            }
            i += 2
        }
        return out
    }

    private fun splitCmd(raw: String): List<String> {
        val s = raw.trim()
        if (s.isEmpty() || s.startsWith("#") || s.startsWith("//")) return emptyList()
        val out = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            while (i < s.length && s[i].isWhitespace()) i++
            if (i >= s.length) break
            if (s[i] == '"' || s[i] == '«') {
                val close = if (s[i] == '"') '"' else '»'
                i++
                val st = i
                while (i < s.length && s[i] != close) i++
                out += s.substring(st, i)
                if (i < s.length) i++
            } else {
                val st = i
                while (i < s.length && !s[i].isWhitespace()) i++
                out += s.substring(st, i)
            }
        }
        return out
    }
}
