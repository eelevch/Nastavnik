package app.nastavnik.ink

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.OverScroller
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Инструмент на листе. */
enum class EditTool { PEN, HIGHLIGHTER, ERASER, LASSO, SHAPE, TEXT }

/**
 * Лист тетради.
 *
 * Перо: ручка, маркер, ластик (целиком или частично), лассо (выделить, двигать, удалить, скопировать,
 * перекрасить), фигуры и текст. Обратный конец стилуса или кнопка на нём — ластик; перо, задержанное
 * на месте на секунду, — тоже ластик, пока его не отпустишь. Нарисуй линию, треугольник, прямоугольник
 * или овал и задержи перо — фигура станет ровной.
 *
 * Пальцы: один двигает лист (с инерцией), два — увеличивают; двойное касание — увеличить или по ширине;
 * касание двумя пальцами — «Отменить». Ладонь во время письма ничего не рисует.
 *
 * Готовая страница хранится картинкой размером с экран: пока пишешь, дорисовывается только новый штрих.
 */
@SuppressLint("ViewConstructor")
class InkView(context: Context) : View(context) {

    interface Listener {
        /** Страница изменилась: пора сохранить. */
        fun onPageEdited()

        /** Начали писать — например, чтобы отложить автопроверку. */
        fun onWritingStarted() {}

        fun onUndoStateChanged(canUndo: Boolean, canRedo: Boolean) {}

        /** Масштаб изменился (1 — лист по ширине экрана). */
        fun onZoomChanged(zoom: Float) {}

        /** Выделение лассо: рамка на экране (в пикселях вида) или null — выделения нет. */
        fun onSelectionChanged(screen: RectF?, count: Int) {}

        /** Инструмент «Текст»: коснулись листа ([existing] — коснулись уже напечатанного текста). */
        fun onTextRequest(x: Float, y: Float, existing: TextBox?) {}

        /** Короткая подсказка о том, что произошло («Ластик, пока держишь перо», «Фигура выровнена»). */
        fun onHint(text: String) {}

        /** Перо задержали — временно включился ластик (или выключился, когда перо подняли). */
        fun onTemporaryEraser(active: Boolean) {}
    }

    var listener: Listener? = null
    private val drawer = PageDrawer()
    private var density = resources.displayMetrics.density

    /** Привязать активный Activity-контекст для точного совпадения экранных координат и калибровки пера. */
    fun updateContext(actContext: Context) {
        val d = actContext.resources.displayMetrics.density
        if (d > 0f) density = d
    }

    var page: InkPage? = null
        private set
    private var bgBitmap: Bitmap? = null
    private var undo = UndoStack()

    // ---------------- инструмент ----------------

    var editTool: EditTool = EditTool.PEN
        set(value) {
            if (field == value) return
            if (field == EditTool.LASSO) clearSelection()
            field = value
            invalidate()
        }

    /** Цвет и толщина ручки, маркера и фигур (в единицах страницы). */
    var color: Int = 0xFF1F4FD1.toInt()
    var penWidth: Float = 3.2f
    var markerWidth: Float = HIGHLIGHTER_W

    /** Ластик стирает часть штриха (а не весь). */
    var partialEraser: Boolean = false
    var shapeKind: ShapeKind = ShapeKind.LINE
    var fingerDraws: Boolean = false

    /** Режим фиксации: перемещение листа ТОЛЬКО двумя пальцами, а зум строго зафиксирован (защита от случайных смещений). */
    var lockMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** Нарисованное от руки и задержанное перо выравнивается в фигуру. */
    var snapShapes: Boolean = true

    /** Перо, задержанное на месте, включает ластик. */
    var holdToErase: Boolean = true

    /** Тёмная тема — только тёмный «стол» вокруг листа. Сам лист всегда белый, чернила — как есть. */
    var dark: Boolean = false
        private set
    private var desk = DESK

    /** Тема приложения: цвет стола вокруг белого листа. */
    fun setTheme(dark: Boolean, deskColor: Int) {
        if (this.dark == dark && desk == deskColor) return
        this.dark = dark
        desk = deskColor
        invalidateCache()
    }

    /** Отметки наставника; новые проявляются плавно. */
    var marks: List<PageMark> = emptyList()
        set(value) {
            val appeared = value.isNotEmpty() && value != field
            field = value
            shownMarks = value
            if (appeared) animateMarks() else invalidate()
        }
    var selectedMark: Int? = null
        set(value) { field = value; invalidate() }
    private var marksAlpha = 1f

    private val pageMatrix = Matrix()
    private val inverse = Matrix()
    private var fitted = false
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val cursorFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cursorRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    // ---------------- страница ----------------

    /**
     * Показать страницу; у каждой страницы своя история «Отменить».
     * [direction] — куда листали (1 — вперёд, −1 — назад, 0 — без анимации).
     */
    fun setPage(p: InkPage, bg: Bitmap?, history: UndoStack, direction: Int = 0) {
        val old = if (direction != 0 && width > 0 && page != null) snapshot() else null
        cancelTurn()
        clearSelection()
        page = p
        bgBitmap = bg
        undo = history
        marks = emptyList()
        selectedMark = null
        current = null
        fitted = false
        frontPage = null
        frontValid = false
        inFlightStrokes.clear()
        renderGen.incrementAndGet()
        if (width > 0) fitWidth()
        invalidate()
        notifyUndo()
        if (old != null) animatePageTurn(old, direction)
    }

    fun undo() {
        val p = page ?: return
        clearSelection()
        if (undo.undo(p)) edited(full = true)
    }

    fun redo() {
        val p = page ?: return
        clearSelection()
        if (undo.redo(p)) edited(full = true)
    }

    /** Стереть всю страницу (можно отменить). */
    fun clearPage() {
        val p = page ?: return
        if (p.strokes.isEmpty() && p.texts.isEmpty()) return
        clearSelection()
        replace(p, emptyList(), emptyList())
    }

    /** Поменялась бумага или другое оформление страницы. */
    fun pageChanged() = invalidateCache()

    /** Любая сложная правка — одним шагом «Отменить». */
    private fun replace(p: InkPage, strokes: List<Stroke>, texts: List<TextBox>) {
        undo.push(InkEdit.Replace(p.strokes.toList(), p.texts.toList(), strokes, texts))
        p.set(strokes, texts)
        p.touch()
        edited(full = true)
    }

    private fun edited(full: Boolean) {
        notifyUndo()
        if (full) invalidateCache() else invalidate()
        listener?.onPageEdited()
    }

    private fun notifyUndo() = listener?.onUndoStateChanged(undo.canUndo, undo.canRedo)

    // ---------------- текст ----------------

    /** Напечатать текст (или заменить [old]; пустой текст удаляет). */
    fun putText(x: Float, y: Float, text: String, size: Float, color: Int, old: TextBox?) {
        val p = page ?: return
        val texts = p.texts.toMutableList()
        val at = old?.let { texts.indexOf(it) } ?: -1
        if (at >= 0) texts.removeAt(at)
        if (text.isNotBlank()) {
            val w = (PAGE_W - x - 24f).coerceIn(80f, PAGE_W * 0.9f)
            val box = TextBox(old?.x ?: x, old?.y ?: y, old?.width ?: w, text.trimEnd(), size, color)
            if (at >= 0) texts.add(at, box) else texts += box
        }
        replace(p, p.strokes.toList(), texts)
    }

    private fun textAt(x: Float, y: Float): TextBox? = page?.texts?.lastOrNull { t ->
        x >= t.x - 8 && x <= t.x + t.width && y >= t.y - 8 && y <= t.y + drawer.textHeight(t) + 8
    }

    // ---------------- выделение ----------------

    private var selStrokes: List<Stroke> = emptyList()
    private var selTexts: List<TextBox> = emptyList()
    private val selExclude = HashSet<Any>()
    private var moveDx = 0f
    private var moveDy = 0f
    private var resizeAnchorX = 0f
    private var resizeAnchorY = 0f
    private var resizeOrigW = 1f
    private var resizeOrigH = 1f
    private var resizeScale = 1f
    private val lassoPath = ArrayList<Float>()
    private var antsPhase = 0f
    private var antsAnim: ValueAnimator? = null
    private var selectionAppear = 1f

    val hasSelection: Boolean get() = selStrokes.isNotEmpty() || selTexts.isNotEmpty()

    fun clearSelection() {
        if (!hasSelection && lassoPath.isEmpty()) return
        selStrokes = emptyList()
        selTexts = emptyList()
        selExclude.clear()
        lassoPath.clear()
        moveDx = 0f
        moveDy = 0f
        resizeScale = 1f
        antsAnim?.cancel()
        antsAnim = null
        invalidateCache()
        debugSelectionCount = 0
        listener?.onSelectionChanged(null, 0)
    }

    private fun select(strokes: List<Stroke>, texts: List<TextBox>) {
        selStrokes = strokes
        selTexts = texts
        selExclude.clear()
        selExclude.addAll(strokes)
        selExclude.addAll(texts)
        invalidateCache()
        if (hasSelection) {
            startAnts()
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 260
                interpolator = OvershootInterpolator(1.6f)
                addUpdateListener { selectionAppear = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        reportSelection()
    }

    private fun startAnts() {
        if (antsAnim != null) return
        antsAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            interpolator = null
            addUpdateListener { antsPhase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun selectionBounds(): RectF? {
        val b = Selection.bounds(selStrokes, emptyList()) ?: if (selTexts.isEmpty()) return null else null
        var l = b?.get(0) ?: Float.MAX_VALUE
        var t = b?.get(1) ?: Float.MAX_VALUE
        var r = b?.get(2) ?: -Float.MAX_VALUE
        var bt = b?.get(3) ?: -Float.MAX_VALUE
        for (tx in selTexts) {
            l = min(l, tx.x); t = min(t, tx.y); r = max(r, tx.x + tx.width); bt = max(bt, tx.y + drawer.textHeight(tx))
        }
        if (l > r) return null
        return RectF(l - 6, t - 6, r + 6, bt + 6)
    }

    /** Снимок выделенной области для вопроса наставнику по конкретным формулам / записям. */
    fun selectionSnapshot(): Bitmap? {
        val b = selectionBounds() ?: return null
        val p = page ?: return null
        val k = 2f
        val w = (b.width() * k).toInt().coerceIn(32, 2048)
        val h = (b.height() * k).toInt().coerceIn(32, 2048)
        return try {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.scale(k, k)
            c.translate(-b.left, -b.top)
            drawer.drawPage(c, p, bgBitmap, b)
            bmp
        } catch (_: Throwable) {
            null
        }
    }

    private fun reportSelection() {
        debugSelectionCount = selStrokes.size + selTexts.size
        val b = selectionBounds()
        if (b == null) {
            listener?.onSelectionChanged(null, 0)
            return
        }
        if (mode == Mode.RESIZE && resizeScale != 1f) {
            val w = b.width() * resizeScale
            val h = b.height() * resizeScale
            b.right = b.left + w
            b.bottom = b.top + h
        } else {
            b.offset(moveDx, moveDy)
        }
        pageMatrix.mapRect(b)
        listener?.onSelectionChanged(b, selStrokes.size + selTexts.size)
    }

    fun deleteSelection() {
        val p = page ?: return
        if (!hasSelection) return
        val strokes = p.strokes.filter { it !in selExclude }
        val texts = p.texts.filter { it !in selExclude }
        fadeOut(selStrokes)
        clearSelection()
        replace(p, strokes, texts)
    }

    /** Копия выделенного рядом — и выделение переходит на копию. */
    fun duplicateSelection() {
        val p = page ?: return
        if (!hasSelection) return
        val ns = selStrokes.map { it.moved(24f, 24f) }
        val nt = selTexts.map { it.moved(24f, 24f) }
        replace(p, p.strokes + ns, p.texts + nt)
        select(ns, nt)
    }

    fun recolorSelection(c: Int) {
        val p = page ?: return
        if (!hasSelection) return
        val map = selStrokes.associateWith { if (it.tool == Tool.HIGHLIGHTER) it else it.recolored(c) }
        val tmap = selTexts.associateWith { TextBox(it.x, it.y, it.width, it.text, it.size, c) }
        replace(p, p.strokes.map { map[it] ?: it }, p.texts.map { tmap[it] ?: it })
        select(map.values.toList(), tmap.values.toList())
    }

    /** Выделенное — для «Копировать» (между страницами). */
    fun selectionCopy(): Pair<List<Stroke>, List<TextBox>> = selStrokes to selTexts

    /** Вставить скопированное и сразу выделить — можно передвинуть. */
    fun paste(strokes: List<Stroke>, texts: List<TextBox>) {
        val p = page ?: return
        if (strokes.isEmpty() && texts.isEmpty()) return
        editTool = EditTool.LASSO
        val ns = strokes.map { it.moved(16f, 16f) }
        val nt = texts.map { it.moved(16f, 16f) }
        replace(p, p.strokes + ns, p.texts + nt)
        select(ns, nt)
    }

    private fun commitMove() {
        val p = page ?: return
        if (moveDx == 0f && moveDy == 0f) return
        val dx = moveDx; val dy = moveDy
        moveDx = 0f; moveDy = 0f
        val sm = selStrokes.associateWith { it.moved(dx, dy) }
        val tm = selTexts.associateWith { it.moved(dx, dy) }
        replace(p, p.strokes.map { sm[it] ?: it }, p.texts.map { tm[it] ?: it })
        select(sm.values.toList(), tm.values.toList())
    }

    private fun insideSelection(px: Float, py: Float): Boolean {
        val b = selectionBounds() ?: return false
        b.inset(-12f, -12f)
        return b.contains(px, py)
    }

    /** Проверка попадания в угловой маркер изменения размера (правый нижний угол). */
    private fun nearResizeHandle(px: Float, py: Float): Boolean {
        val b = selectionBounds() ?: return false
        val r = 28f / scale
        return hypot(px - b.right, py - b.bottom) <= r
    }

    /** Пропорциональное изменение размера выделения (кнопки меню или жест). */
    fun scaleSelection(factor: Float) {
        val p = page ?: return
        if (!hasSelection) return
        val b = selectionBounds() ?: return
        val cx = b.centerX()
        val cy = b.centerY()
        val sm = selStrokes.associateWith { it.scaled(factor, factor, cx, cy) }
        val tm = selTexts.associateWith { it.scaled(factor, factor, cx, cy) }
        replace(p, p.strokes.map { sm[it] ?: it }, p.texts.map { tm[it] ?: it })
        select(sm.values.toList(), tm.values.toList())
        reportSelection()
        listener?.onHint("Размер: ${(factor * 100).roundToInt()}%")
        invalidate()
    }

    private fun commitResize() {
        val p = page ?: return
        val factor = resizeScale
        resizeScale = 1f
        if (abs(factor - 1f) < 0.02f) {
            reportSelection()
            invalidate()
            return
        }
        val ax = resizeAnchorX
        val ay = resizeAnchorY
        val sm = selStrokes.associateWith { it.scaled(factor, factor, ax, ay) }
        val tm = selTexts.associateWith { it.scaled(factor, factor, ax, ay) }
        replace(p, p.strokes.map { sm[it] ?: it }, p.texts.map { tm[it] ?: it })
        select(sm.values.toList(), tm.values.toList())
        reportSelection()
        listener?.onHint("Размер: ${(factor * 100).roundToInt()}%")
        invalidate()
    }

    // ---------------- масштаб ----------------

    private val values = FloatArray(9)
    private val scale: Float get() = values.also { pageMatrix.getValues(it) }[Matrix.MSCALE_X]

    private fun fitScale(): Float = ((width - 2 * MARGIN * density) / PAGE_W).coerceAtLeast(0.01f)

    /** Лист по ширине экрана, сверху небольшой отступ. */
    fun fitWidth() {
        val k = fitScale()
        pageMatrix.setScale(k, k)
        pageMatrix.postTranslate(MARGIN * density, MARGIN * density)
        fitted = true
        zoomChanged()
        invalidate()
    }

    /** Плавно вернуть лист по ширине экрана. */
    fun animateFitWidth() {
        val target = Matrix().apply {
            val k = fitScale()
            setScale(k, k)
            val ty = values.also { pageMatrix.getValues(it) }[Matrix.MTRANS_Y] / scale * k
            postTranslate(MARGIN * density, ty)
        }
        clamp(target)
        animateMatrix(target)
    }

    private var resizingUntil = 0L

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (!fitted) {
            fitWidth()
        } else {
            clampMatrix()
            resizingUntil = SystemClock.uptimeMillis() + 400L
            invalidate()
            postDelayed({
                if (SystemClock.uptimeMillis() >= resizingUntil) {
                    invalidate()
                }
            }, 420L)
        }
    }

    private fun zoomChanged() {
        listener?.onZoomChanged(scale / fitScale())
        if (hasSelection) reportSelection()
    }

    /** Плавно показать место на странице (например, отметку из заметки) в середине экрана. */
    fun reveal(r: RectF) {
        if (page == null) return
        val k = scale
        val target = Matrix(pageMatrix)
        val v = FloatArray(9).also { target.getValues(it) }
        val cx = r.centerX() * k + v[Matrix.MTRANS_X]
        val cy = r.centerY() * k + v[Matrix.MTRANS_Y]
        val dx = if (cx in 0f..width.toFloat()) 0f else width / 2f - cx
        val dy = height * 0.4f - cy
        target.postTranslate(dx, dy)
        clamp(target)
        animateMatrix(target)
    }

    private var matrixAnim: ValueAnimator? = null

    private fun animateMatrix(target: Matrix) {
        matrixAnim?.cancel()
        scroller.forceFinished(true)
        val from = FloatArray(9).also { pageMatrix.getValues(it) }
        val to = FloatArray(9).also { target.getValues(it) }
        val now = FloatArray(9)
        moving = true
        matrixAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 340
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener {
                val t = it.animatedValue as Float
                for (i in 0 until 9) now[i] = from[i] + (to[i] - from[i]) * t
                pageMatrix.setValues(now)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    moving = false
                    zoomChanged()
                    invalidate()
                }
            })
            start()
        }
    }

    private fun clamp(m: Matrix) {
        val p = page ?: return
        val v = FloatArray(9).also { m.getValues(it) }
        val k = v[Matrix.MSCALE_X]
        val pw = PAGE_W * k
        val ph = p.height * k
        val mg = MARGIN * density
        var tx = v[Matrix.MTRANS_X]
        var ty = v[Matrix.MTRANS_Y]
        tx = if (pw + 2 * mg <= width) (width - pw) / 2 else tx.coerceIn(width - pw - mg, mg)
        ty = if (ph + 2 * mg <= height) ty.coerceIn(mg, height - ph - mg) else ty.coerceIn(height - ph - mg * 4, mg)
        m.postTranslate(tx - v[Matrix.MTRANS_X], ty - v[Matrix.MTRANS_Y])
    }

    private fun clampMatrix() = clamp(pageMatrix)

    // ---------------- рисование ----------------

    // Двойной буфер растрового кэша в нативном 1:1 разрешении экрана + запас (overscan) вокруг
    // видимой области: отрисовка листа всегда за ~0.15 мс (120 FPS) без потери чёткости и без рывков.
    private fun newRenderExecutor() = Executors.newSingleThreadExecutor { r ->
        Thread(r, "InkPageCache").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY + 1
        }
    }
    private var renderExecutor = newRenderExecutor()
    private val bgDrawer = PageDrawer()
    @Volatile private var detached = false
    private val renderGen = AtomicLong(0L)
    private var renderInFlight = false
    private var renderPending = false
    private val inFlightStrokes = ArrayList<Stroke>()

    private var frontBmp: Bitmap? = null
    private var frontCanvas: Canvas? = null
    private val frontRect = RectF()
    private val frontSrc = Rect()
    private var frontW = 0
    private var frontH = 0
    private var frontScale = 0f
    private var frontPage: InkPage? = null
    private var frontValid = false
    private var spareBmp: Bitmap? = null

    private val cachePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fallbackPaperPaint = Paint().apply { color = PageDrawer.PAPER }
    private val tmpVisibleRect = RectF()
    private val tmpTargetRect = RectF()
    private val tmpRenderRect = RectF()
    private val isWeakDevice: Boolean by lazy { app.nastavnik.data.DeviceProfile.isWeakDevice(context) }

    /** Лист двигают или увеличивают. */
    private var moving = false

    private fun invalidateCache() {
        renderGen.incrementAndGet()
        frontValid = false
        inFlightStrokes.clear()
        val p = page
        if (p != null && width > 0 && height > 0 && !detached) {
            scheduleAsyncRender(p)
        }
        invalidate()
    }

    private fun visiblePageRect(p: InkPage, out: RectF) {
        if (width <= 0 || height <= 0) {
            out.set(0f, 0f, PAGE_W, min(p.height, PAGE_W * 1.5f))
            return
        }
        pageMatrix.invert(inverse)
        out.set(0f, 0f, width.toFloat(), height.toFloat())
        inverse.mapRect(out)
        out.left = out.left.coerceIn(0f, PAGE_W)
        out.top = out.top.coerceIn(0f, p.height)
        out.right = out.right.coerceIn(0f, PAGE_W)
        out.bottom = out.bottom.coerceIn(0f, p.height)
        if (out.width() < 1f || out.height() < 1f) {
            out.set(0f, 0f, PAGE_W, min(p.height, PAGE_W * 1.5f))
        }
    }

    private fun computeTargetCache(p: InkPage, outRect: RectF): Triple<Int, Int, Float> {
        visiblePageRect(p, tmpVisibleRect)
        val visW = tmpVisibleRect.width().coerceAtLeast(1f)
        val visH = tmpVisibleRect.height().coerceAtLeast(1f)
        val padX = max(visW * 0.55f, PAGE_W * 0.25f)
        val padY = max(visH * 0.65f, PAGE_W * 0.5f)
        outRect.set(
            (tmpVisibleRect.left - padX).coerceAtLeast(0f),
            (tmpVisibleRect.top - padY).coerceAtLeast(0f),
            (tmpVisibleRect.right + padX).coerceAtMost(PAGE_W),
            (tmpVisibleRect.bottom + padY).coerceAtMost(p.height),
        )

        val maxSide = if (isWeakDevice) {
            1920
        } else {
            maxOf(2880, minOf((maxOf(width, height) * 1.5f).toInt(), 3840))
        }
        val maxPixels = if (isWeakDevice) 1600L * 2048L else 2880L * 3200L

        var targetScale = scale.coerceAtLeast(fitScale() * 0.5f)
        targetScale = minOf(targetScale, maxSide / visW, maxSide / visH)

        // Ограничиваем стороны буфера без обрезания видимой области.
        val maxPageW = maxSide / targetScale
        if (outRect.width() > maxPageW) {
            val halfW = maxPageW * 0.5f
            val cx = tmpVisibleRect.centerX()
            var l = cx - halfW
            var r = cx + halfW
            if (l < 0f) { r = min(PAGE_W, r - l); l = 0f }
            if (r > PAGE_W) { l = max(0f, l - (r - PAGE_W)); r = PAGE_W }
            outRect.left = min(l, tmpVisibleRect.left)
            outRect.right = max(r, tmpVisibleRect.right)
        }
        val maxPageH = maxSide / targetScale
        if (outRect.height() > maxPageH) {
            val halfH = maxPageH * 0.5f
            val cy = tmpVisibleRect.centerY()
            var t = cy - halfH
            var b = cy + halfH
            if (t < 0f) { b = min(p.height, b - t); t = 0f }
            if (b > p.height) { t = max(0f, t - (b - p.height)); b = p.height }
            outRect.top = min(t, tmpVisibleRect.top)
            outRect.bottom = max(b, tmpVisibleRect.bottom)
        }

        // Ограничиваем суммарное число пикселей буфера: сначала поджимаем вертикальный, затем горизонтальный запас.
        var pxW = outRect.width() * targetScale
        var pxH = outRect.height() * targetScale
        if (pxW * pxH > maxPixels) {
            val allowedH = (maxPixels / pxW.coerceAtLeast(1f)) / targetScale
            if (allowedH >= visH) {
                val halfH = allowedH * 0.5f
                val cy = tmpVisibleRect.centerY()
                var t = cy - halfH
                var b = cy + halfH
                if (t < 0f) { b = min(p.height, b - t); t = 0f }
                if (b > p.height) { t = max(0f, t - (b - p.height)); b = p.height }
                outRect.top = min(t, tmpVisibleRect.top)
                outRect.bottom = max(b, tmpVisibleRect.bottom)
            } else {
                outRect.top = tmpVisibleRect.top
                outRect.bottom = tmpVisibleRect.bottom
            }
            pxH = outRect.height() * targetScale
            if (pxW * pxH > maxPixels) {
                val allowedW = (maxPixels / pxH.coerceAtLeast(1f)) / targetScale
                if (allowedW >= visW) {
                    val halfW = allowedW * 0.5f
                    val cx = tmpVisibleRect.centerX()
                    var l = cx - halfW
                    var r = cx + halfW
                    if (l < 0f) { r = min(PAGE_W, r - l); l = 0f }
                    if (r > PAGE_W) { l = max(0f, l - (r - PAGE_W)); r = PAGE_W }
                    outRect.left = min(l, tmpVisibleRect.left)
                    outRect.right = max(r, tmpVisibleRect.right)
                } else {
                    outRect.left = tmpVisibleRect.left
                    outRect.right = tmpVisibleRect.right
                }
                pxW = outRect.width() * targetScale
                pxH = outRect.height() * targetScale
                if (pxW * pxH > maxPixels) {
                    targetScale *= sqrt(maxPixels / (pxW * pxH).coerceAtLeast(1f))
                }
            }
        }

        val bw = (outRect.width() * targetScale).roundToInt().coerceIn(1, maxSide)
        val bh = (outRect.height() * targetScale).roundToInt().coerceIn(1, maxSide)
        return Triple(bw, bh, targetScale)
    }

    private fun renderPageIntoBitmap(
        targetBmp: Bitmap?,
        targetDrawer: PageDrawer,
        paper: Paper,
        pageHeight: Float,
        strokes: List<Stroke>,
        texts: List<TextBox>,
        exclude: Set<Any>,
        bg: Bitmap?,
        rect: RectF,
        bw: Int,
        bh: Int,
    ): Bitmap? {
        val cfg = if (isWeakDevice) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        val bmp = if (
            targetBmp != null &&
            !targetBmp.isRecycled &&
            targetBmp.config == cfg &&
            targetBmp.width >= bw &&
            targetBmp.height >= bh &&
            targetBmp.width <= bw * 2 &&
            targetBmp.height <= bh * 2
        ) {
            targetBmp
        } else {
            if (targetBmp != null && !targetBmp.isRecycled) targetBmp.recycle()
            try {
                Bitmap.createBitmap(bw, bh, cfg)
            } catch (_: OutOfMemoryError) {
                return null
            }
        }
        val c = Canvas(bmp)
        c.save()
        c.clipRect(0, 0, bw, bh)
        val sx = bw / rect.width().coerceAtLeast(1f)
        val sy = bh / rect.height().coerceAtLeast(1f)
        c.scale(sx, sy)
        c.translate(-rect.left, -rect.top)
        targetDrawer.drawPage(c, paper, pageHeight, strokes, texts, bg, rect, exclude)
        c.restore()
        return bmp
    }

    private fun needsViewportRefresh(p: InkPage): Boolean {
        if (!frontValid || frontBmp == null || frontBmp!!.isRecycled || frontPage !== p) return true
        visiblePageRect(p, tmpVisibleRect)
        val availX = (frontRect.width() - tmpVisibleRect.width()).coerceAtLeast(0f)
        val availY = (frontRect.height() - tmpVisibleRect.height()).coerceAtLeast(0f)
        val mx = min(tmpVisibleRect.width() * 0.15f, availX * 0.35f)
        val my = min(tmpVisibleRect.height() * 0.15f, availY * 0.35f)
        if ((frontRect.left > 0.5f && tmpVisibleRect.left < frontRect.left + mx) ||
            (frontRect.right < PAGE_W - 0.5f && tmpVisibleRect.right > frontRect.right - mx) ||
            (frontRect.top > 0.5f && tmpVisibleRect.top < frontRect.top + my) ||
            (frontRect.bottom < p.height - 0.5f && tmpVisibleRect.bottom > frontRect.bottom - my)
        ) {
            return true
        }
        val ratio = if (frontScale > 0f) scale / frontScale else 2f
        return if (moving) {
            ratio > 1.75f || ratio < 0.55f
        } else {
            ratio > 1.15f || ratio < 0.85f
        }
    }

    private fun scheduleAsyncRender(p: InkPage) {
        if (detached || width <= 0 || height <= 0) return
        if (renderInFlight) {
            renderPending = true
            return
        }
        val (bw, bh, targetScale) = computeTargetCache(p, tmpRenderRect)
        if (bw <= 0 || bh <= 0) return
        val targetRect = RectF(tmpRenderRect)
        val gen = renderGen.get()
        val strokesSnap = p.strokes.toList()
        val textsSnap = p.texts.toList()
        val excludeSnap = if (selExclude.isEmpty()) emptySet() else selExclude.toSet()
        val paperSnap = p.paper
        val heightSnap = p.height
        val bgSnap = bgBitmap

        val workBmp = spareBmp
        spareBmp = null
        renderInFlight = true
        renderPending = false
        inFlightStrokes.clear()

        renderExecutor.execute {
            if (detached) {
                if (workBmp != null && !workBmp.isRecycled) workBmp.recycle()
                return@execute
            }
            val resultBmp = renderPageIntoBitmap(
                targetBmp = workBmp,
                targetDrawer = bgDrawer,
                paper = paperSnap,
                pageHeight = heightSnap,
                strokes = strokesSnap,
                texts = textsSnap,
                exclude = excludeSnap,
                bg = bgSnap,
                rect = targetRect,
                bw = bw,
                bh = bh,
            )
            post {
                renderInFlight = false
                if (detached || page !== p || resultBmp == null) {
                    if (resultBmp != null && resultBmp !== frontBmp && !resultBmp.isRecycled) resultBmp.recycle()
                    return@post
                }
                if (gen != renderGen.get()) {
                    if (resultBmp !== frontBmp) {
                        if (spareBmp != null && spareBmp !== resultBmp && !spareBmp!!.isRecycled) spareBmp!!.recycle()
                        spareBmp = resultBmp
                    }
                    scheduleAsyncRender(p)
                    return@post
                }
                val oldFront = frontBmp
                frontBmp = resultBmp
                frontCanvas = Canvas(resultBmp)
                frontRect.set(targetRect)
                frontW = bw
                frontH = bh
                frontSrc.set(0, 0, bw, bh)
                frontScale = targetScale
                frontPage = p
                frontValid = true
                if (oldFront != null && oldFront !== resultBmp) {
                    if (spareBmp != null && spareBmp !== oldFront && !spareBmp!!.isRecycled) spareBmp!!.recycle()
                    spareBmp = oldFront
                }
                if (inFlightStrokes.isNotEmpty()) {
                    val fc = frontCanvas
                    if (fc != null) {
                        fc.save()
                        fc.clipRect(0, 0, frontW, frontH)
                        val sx = frontW / frontRect.width().coerceAtLeast(1f)
                        val sy = frontH / frontRect.height().coerceAtLeast(1f)
                        fc.scale(sx, sy)
                        fc.translate(-frontRect.left, -frontRect.top)
                        for (s in inFlightStrokes) drawer.drawStroke(fc, s)
                        fc.restore()
                    }
                    inFlightStrokes.clear()
                }
                invalidate()
                if (renderPending) {
                    renderPending = false
                    scheduleAsyncRender(p)
                }
            }
        }
    }

    /** Стол, тень, лист (1:1 кэш-буфер без потери чёткости), рамка листа — в текущем положении листа. */
    private fun drawScene(c: Canvas) {
        val p = page ?: return
        c.drawColor(desk)
        c.save()
        c.concat(pageMatrix)
        val k = scale
        shadow.color = if (dark) 0x40000000 else 0x1416161A
        c.drawRect(-2f / k, 2f / k, PAGE_W + 2f / k, p.height + 5f / k, shadow)
        c.drawRect(0f, 0f, PAGE_W, p.height, fallbackPaperPaint)

        if ((frontBmp == null || frontBmp!!.isRecycled || frontPage !== p) && width > 0 && height > 0) {
            val (bw, bh, targetScale) = computeTargetCache(p, tmpTargetRect)
            val oldSpare = spareBmp
            spareBmp = null
            val initBmp = renderPageIntoBitmap(
                targetBmp = oldSpare,
                targetDrawer = drawer,
                paper = p.paper,
                pageHeight = p.height,
                strokes = p.strokes,
                texts = p.texts,
                exclude = selExclude,
                bg = bgBitmap,
                rect = tmpTargetRect,
                bw = bw,
                bh = bh,
            )
            if (initBmp != null) {
                val oldFront = frontBmp
                if (oldFront != null && oldFront !== initBmp && !oldFront.isRecycled) {
                    spareBmp = oldFront
                }
                frontBmp = initBmp
                frontCanvas = Canvas(initBmp)
                frontRect.set(tmpTargetRect)
                frontW = bw
                frontH = bh
                frontSrc.set(0, 0, bw, bh)
                frontScale = targetScale
                frontPage = p
                frontValid = true
            }
        }
        val bmp = frontBmp
        if (bmp != null && !bmp.isRecycled && frontW > 0 && frontH > 0) {
            c.drawBitmap(bmp, frontSrc, frontRect, cachePaint)
        }

        edge.strokeWidth = 1f / k
        edge.color = if (dark) 0x26FFFFFF else 0x1F16161A
        c.drawRect(0f, 0f, PAGE_W, p.height, edge)
        c.restore()

        if (!renderInFlight && needsViewportRefresh(p)) {
            scheduleAsyncRender(p)
        }
    }

    /** Дорисовать один готовый штрих прямо в активный буфер без перерисовки всей страницы. */
    private fun drawIntoCache(s: Stroke) {
        val p = page
        val bmp = frontBmp
        val fc = frontCanvas
        if (p != null && bmp != null && !bmp.isRecycled && fc != null && frontPage === p && frontW > 0 && frontH > 0) {
            val b = s.bounds()
            val intersects = b[2] >= frontRect.left && b[0] <= frontRect.right && b[3] >= frontRect.top && b[1] <= frontRect.bottom
            if (intersects) {
                fc.save()
                fc.clipRect(0, 0, frontW, frontH)
                val sx = frontW / frontRect.width().coerceAtLeast(1f)
                val sy = frontH / frontRect.height().coerceAtLeast(1f)
                fc.scale(sx, sy)
                fc.translate(-frontRect.left, -frontRect.top)
                drawer.drawStroke(fc, s)
                fc.restore()
            }
            if (renderInFlight) inFlightStrokes += s
        } else {
            invalidateCache()
        }
    }

    private val lassoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val lassoFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF3A6FE0.toInt()
    }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }

    override fun onDraw(canvas: Canvas) {
        // Никогда не рисуем за пределами своего прямоугольника (иначе стол закрывает соседние панели).
        canvas.clipRect(0, 0, width, height)
        val p = page
        if (p == null) {
            canvas.drawColor(desk)
            return
        }
        val turn = turning
        if (turn != null) {
            drawTurn(canvas, turn)
            return
        }

        drawScene(canvas)
        canvas.save()
        canvas.concat(pageMatrix)
        val k = scale
        drawDying(canvas)
        drawSelection(canvas, k)
        current?.let { l ->
            if (l.tool == Tool.HIGHLIGHTER) {
                drawer.drawLiveHighlighter(canvas, l.livePath, l.color, l.width)
            } else {
                l.prepareTail()
                drawer.drawLivePen(
                    canvas, l.color, l.dotX, l.dotY, l.dotR, !l.hasMoved,
                    l.frozenBuckets, l.frozenSizes, l.tailBuckets, l.tailSizes, l.quantum,
                )
            }
        }
        morph?.let { drawer.drawStroke(canvas, it) }
        shapePreview.forEach { drawer.drawStroke(canvas, it) }
        if (lassoPath.size >= 4) drawLasso(canvas, k)
        if (marks.isNotEmpty()) {
            if (marksAlpha < 1f) {
                val save = canvas.saveLayerAlpha(null, (marksAlpha * 255).toInt())
                drawer.drawMarks(canvas, marks, selectedMark, k)
                canvas.restoreToCount(save)
            } else {
                drawer.drawMarks(canvas, marks, selectedMark, k)
            }
        }
        canvas.restore()
        drawCursor(canvas)
    }

    /** Для тестов: включён ли сейчас временный ластик. */
    val temporaryEraser: Boolean get() = tempEraser

    private fun drawSelection(c: Canvas, k: Float) {
        if (!hasSelection) return
        c.save()
        if (mode == Mode.RESIZE && resizeScale != 1f) {
            c.scale(resizeScale, resizeScale, resizeAnchorX, resizeAnchorY)
        } else {
            c.translate(moveDx, moveDy)
        }
        // Поднятое выделение: мягкий ореол, само содержимое — поверх.
        val glow = 0x335B8CFF
        for (s in selStrokes) drawer.drawGlow(c, s, glow, 7f / k)
        for (s in selStrokes) if (s.tool == Tool.HIGHLIGHTER) drawer.drawStroke(c, s)
        for (t in selTexts) drawer.drawText(c, t)
        for (s in selStrokes) if (s.tool != Tool.HIGHLIGHTER) drawer.drawStroke(c, s)
        c.restore()

        val b = selectionBounds() ?: return
        if (mode == Mode.RESIZE && resizeScale != 1f) {
            val w = b.width() * resizeScale
            val h = b.height() * resizeScale
            b.right = b.left + w
            b.bottom = b.top + h
        } else {
            b.offset(moveDx, moveDy)
        }
        val grow = (1 - selectionAppear) * 10f / k
        b.inset(-grow, -grow)
        selFrame.strokeWidth = 1.5f / k
        selFrame.color = 0xCC3A6FE0.toInt()
        selFrame.pathEffect = DashPathEffect(floatArrayOf(7f / k, 5f / k), -antsPhase * 12f / k)
        c.drawRoundRect(b, 8f / k, 8f / k, selFrame)

        // Маркер изменения размера (правый нижний угол)
        val handleR = 7f / k
        handleStroke.strokeWidth = 1.5f / k
        c.drawCircle(b.right, b.bottom, handleR, handlePaint)
        c.drawCircle(b.right, b.bottom, handleR, handleStroke)
        handleStroke.strokeWidth = 1.2f / k
        val d = 3f / k
        c.drawLine(b.right - d, b.bottom - d, b.right + d, b.bottom + d, handleStroke)
    }

    private fun drawLasso(c: Canvas, k: Float) {
        path.reset()
        path.moveTo(lassoPath[0], lassoPath[1])
        var i = 2
        while (i + 1 < lassoPath.size) {
            path.lineTo(lassoPath[i], lassoPath[i + 1])
            i += 2
        }
        lassoFill.color = 0x143A6FE0
        c.drawPath(path, lassoFill)
        lassoPaint.strokeWidth = 1.6f / k
        lassoPaint.color = 0xDD3A6FE0.toInt()
        lassoPaint.pathEffect = DashPathEffect(floatArrayOf(8f / k, 6f / k), 0f)
        c.drawPath(path, lassoPaint)
    }

    private fun animateMarks() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                marksAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // ---------------- стёртое тает ----------------

    private class Dying(val strokes: List<Stroke>, var alpha: Float = 1f)

    private val dying = ArrayList<Dying>()

    /** Стёртые штрихи не исчезают мгновенно, а тают — видно, что именно стёрлось. */
    private fun fadeOut(strokes: List<Stroke>) {
        if (strokes.isEmpty()) return
        val d = Dying(strokes)
        dying += d
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 240
            interpolator = DecelerateInterpolator()
            addUpdateListener { d.alpha = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    dying.remove(d)
                    invalidate()
                }
            })
            start()
        }
    }

    private fun drawDying(c: Canvas) {
        for (d in dying) {
            val save = c.saveLayerAlpha(null, (d.alpha * 255).toInt())
            for (s in d.strokes) drawer.drawStroke(c, s)
            c.restoreToCount(save)
        }
    }

    // ---------------- листание страниц ----------------

    private class Turn(val old: Bitmap, val direction: Int, var t: Float = 0f)

    private var turning: Turn? = null
    private var turnAnim: ValueAnimator? = null

    private fun cancelTurn() {
        turnAnim?.cancel()
        turnAnim = null
        turning?.old?.let { if (!it.isRecycled) it.recycle() }
        turning = null
    }

    private fun snapshot(): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val isWeak = app.nastavnik.data.DeviceProfile.isWeakDevice(context)
        val cfg = if (isWeak) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        return try {
            val b = Bitmap.createBitmap(width, height, cfg)
            val c = Canvas(b)
            drawScene(c)
            b
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun animatePageTurn(old: Bitmap, direction: Int) {
        cancelTurn()
        val turn = Turn(old, direction)
        turning = turn
        turnAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener {
                turn.t = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (turning === turn) {
                        turning = null
                        turnAnim = null
                    }
                    if (!old.isRecycled) old.recycle()
                    invalidate()
                }
            })
            start()
        }
    }

    private val turnPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Старая страница уходит в сторону, чуть уменьшаясь и тая; новая въезжает следом. */
    private fun drawTurn(canvas: Canvas, turn: Turn) {
        if (turn.old.isRecycled) return
        val shift = width * 0.22f
        canvas.drawColor(desk)
        canvas.save()
        val s = 1f - 0.05f * turn.t
        canvas.scale(s, s, width / 2f, height / 2f)
        turnPaint.alpha = ((1 - turn.t) * 255).toInt().coerceIn(0, 255)
        canvas.drawBitmap(turn.old, -turn.direction * shift * turn.t, 0f, turnPaint)
        canvas.restore()

        canvas.save()
        canvas.translate(turn.direction * shift * (1 - turn.t), 0f)
        drawScene(canvas)
        canvas.restore()
    }

    // ---------------- курсор пера ----------------

    private var hoverX = 0f
    private var hoverY = 0f
    private var hovering = false
    private var touchX = 0f
    private var touchY = 0f
    private var eraserRing = 0f
    private var ringAnim: ValueAnimator? = null

    private val erasing: Boolean get() = mode == Mode.ERASE

    /** Над листом видно, куда коснётся перо: точка цвета ручки или круг ластика. */
    private fun drawCursor(c: Canvas) {
        if (!hovering && !erasing) return
        val x = if (erasing) touchX else hoverX
        val y = if (erasing) touchY else hoverY
        if (editTool == EditTool.ERASER || erasing) {
            val r = ERASER_RADIUS * density * (if (tempEraser) eraserRing else 1f)
            cursorFill.color = 0x1816161A
            c.drawCircle(x, y, r, cursorFill)
            cursorRing.color = 0x8816161A.toInt()
            cursorRing.strokeWidth = 1.2f * density
            c.drawCircle(x, y, r, cursorRing)
        } else if (editTool == EditTool.PEN || editTool == EditTool.HIGHLIGHTER || editTool == EditTool.SHAPE) {
            val marker = editTool == EditTool.HIGHLIGHTER
            val r = if (marker) markerWidth * scale / 2 else maxOf(penWidth * scale / 2, 1.6f * density)
            cursorFill.color = drawer.displayColor(color)
            cursorFill.alpha = if (marker) 110 else 200
            c.drawCircle(x, y, r, cursorFill)
        }
    }

    override fun onHoverEvent(e: MotionEvent): Boolean {
        if (e.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS && e.getToolType(0) != MotionEvent.TOOL_TYPE_ERASER) return super.onHoverEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                hovering = true
                hoverX = e.x; hoverY = e.y
            }
            MotionEvent.ACTION_HOVER_EXIT -> hovering = false
        }
        invalidate()
        return true
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_BUTTON_PRESS || e.action == MotionEvent.ACTION_BUTTON_RELEASE) {
            val btn = e.actionButton
            if (btn == MotionEvent.BUTTON_STYLUS_PRIMARY ||
                btn == MotionEvent.BUTTON_STYLUS_SECONDARY ||
                btn == MotionEvent.BUTTON_SECONDARY ||
                btn == MotionEvent.BUTTON_BACK
            ) {
                // Поглощаем нажатия кнопок пера, чтобы система не превращала их в «Назад»
                return true
            }
        }
        return super.onGenericMotionEvent(e)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed && Build.VERSION.SDK_INT >= 29 && width > 0 && height > 0) {
            val edgeW = (32 * density).toInt()
            val leftRect = android.graphics.Rect(0, 0, edgeW, height)
            val rightRect = android.graphics.Rect((width - edgeW).coerceAtLeast(0), 0, width, height)
            systemGestureExclusionRects = listOf(leftRect, rightRect)
        }
    }

    // ---------------- ввод ----------------

    private enum class Mode { NONE, DRAW, ERASE, PAN, LASSO, MOVE, RESIZE, SHAPE, TEXT }

    private var mode = Mode.NONE
    private var activePointer = -1

    /** Текущий штрих: точки в единицах страницы; инкрементально строится за O(1) на кадр. */
    private inner class Live(val tool: Tool, val color: Int, val width: Float) {
        var rawPts = FloatArray(768)
        var rawSize = 0
        val livePath = Path()
        val quantum = maxOf(width * 0.04f, 0.04f)
        val frozenBuckets = arrayOfNulls<FloatArray>(64)
        val frozenSizes = IntArray(64)
        val tailBuckets = arrayOfNulls<FloatArray>(64)
        val tailSizes = IntArray(64)
        var hasMoved = false
        var dotX = 0f
        var dotY = 0f
        var dotR = 0f

        private val tiny = width * 0.02f
        private var frozenInit = false
        private var frozenIdx = 1
        private var frozenSx = 0f
        private var frozenSy = 0f
        private var frozenSp = 0f
        private var lastCurveX = 0f
        private var lastCurveY = 0f
        private var lastCurveP = 0f
        private var tailDirty = true

        fun add(x: Float, y: Float, p: Float) {
            if (rawSize + 3 > rawPts.size) {
                rawPts = rawPts.copyOf(rawPts.size * 2)
            }
            val isFirst = rawSize == 0
            rawPts[rawSize++] = x
            rawPts[rawSize++] = y
            rawPts[rawSize++] = p
            if (isFirst) {
                dotX = x
                dotY = y
                dotR = StrokeGeometry.widthAt(width, p, tool) * 0.5f
                if (tool == Tool.HIGHLIGHTER) {
                    livePath.moveTo(x, y)
                    livePath.lineTo(x + 0.01f, y)
                }
            } else {
                if (tool == Tool.HIGHLIGHTER) livePath.lineTo(x, y)
                if (!hasMoved && hypot(x - dotX, y - dotY) > tiny) hasMoved = true
            }
            tailDirty = true
        }

        fun toFloatArray(): FloatArray = rawPts.copyOf(rawSize)

        private fun pAt(idx: Int, n: Int): Float {
            val a = rawPts[max(idx - 1, 0) * 3 + 2]
            val b = rawPts[idx * 3 + 2]
            val c = rawPts[min(idx + 1, n - 1) * 3 + 2]
            return (a + 2f * b + c) * 0.25f
        }

        private fun pushSeg(
            buckets: Array<FloatArray?>,
            sizes: IntArray,
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
            p0: Float,
            p1: Float,
        ) {
            if (x0 == x1 && y0 == y1) return
            val w = StrokeGeometry.widthAt(width, (p0 + p1) * 0.5f, tool)
            val key = Math.round(w / quantum).coerceIn(0, 63)
            var b = buckets[key]
            val sz = sizes[key]
            if (b == null) {
                b = FloatArray(128)
                buckets[key] = b
            } else if (sz + 4 > b.size) {
                b = b.copyOf(b.size * 2)
                buckets[key] = b
            }
            b[sz] = x0; b[sz + 1] = y0; b[sz + 2] = x1; b[sz + 3] = y1
            sizes[key] = sz + 4
        }

        fun prepareTail() {
            if (!tailDirty || tool == Tool.HIGHLIGHTER) return
            tailDirty = false
            val rawN = rawSize / 3
            if (rawN == 0) return
            if (!hasMoved) {
                dotX = rawPts[0]
                dotY = rawPts[1]
                dotR = StrokeGeometry.widthAt(width, rawPts[2], tool) * 0.5f
                return
            }
            if (rawN >= 2 && !frozenInit) {
                frozenInit = true
                frozenSx = rawPts[0]
                frozenSy = rawPts[1]
                frozenSp = pAt(0, rawN)
                lastCurveX = frozenSx
                lastCurveY = frozenSy
                lastCurveP = frozenSp
            }
            while (frozenIdx <= rawN - 3) {
                val i = frozenIdx
                val cx = rawPts[i * 3]
                val cy = rawPts[i * 3 + 1]
                val nx0 = rawPts[(i + 1) * 3]
                val ny0 = rawPts[(i + 1) * 3 + 1]
                val ex = (cx + nx0) * 0.5f
                val ey = (cy + ny0) * 0.5f
                val ep = (pAt(i, rawN) + pAt(i + 1, rawN)) * 0.5f
                val len = hypot(cx - frozenSx, cy - frozenSy) + hypot(ex - cx, ey - cy)
                val k = (len / 1.6f).toInt().coerceIn(1, 12)
                for (j in 1..k) {
                    val t = j / k.toFloat()
                    val u = 1f - t
                    val px = u * u * frozenSx + 2f * u * t * cx + t * t * ex
                    val py = u * u * frozenSy + 2f * u * t * cy + t * t * ey
                    val pp = u * frozenSp + t * ep
                    pushSeg(frozenBuckets, frozenSizes, lastCurveX, lastCurveY, px, py, lastCurveP, pp)
                    lastCurveX = px; lastCurveY = py; lastCurveP = pp
                }
                frozenSx = ex; frozenSy = ey; frozenSp = ep
                frozenIdx++
            }
            tailSizes.fill(0)
            if (rawN == 2) {
                pushSeg(tailBuckets, tailSizes, rawPts[0], rawPts[1], rawPts[3], rawPts[4], pAt(0, 2), pAt(1, 2))
            } else if (frozenInit && rawN >= 3) {
                var sx = frozenSx; var sy = frozenSy; var sp = frozenSp
                var lx = lastCurveX; var ly = lastCurveY; var lp = lastCurveP
                for (i in frozenIdx until rawN - 1) {
                    val cx = rawPts[i * 3]
                    val cy = rawPts[i * 3 + 1]
                    val nx0 = rawPts[(i + 1) * 3]
                    val ny0 = rawPts[(i + 1) * 3 + 1]
                    val ex = (cx + nx0) * 0.5f
                    val ey = (cy + ny0) * 0.5f
                    val ep = (pAt(i, rawN) + pAt(i + 1, rawN)) * 0.5f
                    val len = hypot(cx - sx, cy - sy) + hypot(ex - cx, ey - cy)
                    val k = (len / 1.6f).toInt().coerceIn(1, 12)
                    for (j in 1..k) {
                        val t = j / k.toFloat()
                        val u = 1f - t
                        val px = u * u * sx + 2f * u * t * cx + t * t * ex
                        val py = u * u * sy + 2f * u * t * cy + t * t * ey
                        val pp = u * sp + t * ep
                        pushSeg(tailBuckets, tailSizes, lx, ly, px, py, lp, pp)
                        lx = px; ly = py; lp = pp
                    }
                    sx = ex; sy = ey; sp = ep
                }
                val endIdx = rawN - 1
                pushSeg(tailBuckets, tailSizes, lx, ly, rawPts[endIdx * 3], rawPts[endIdx * 3 + 1], lp, pAt(endIdx, rawN))
            }
        }

        fun pathLength(): Float {
            var d = 0f
            var i = 3
            while (i + 1 < rawSize) {
                d += hypot(rawPts[i] - rawPts[i - 3], rawPts[i + 1] - rawPts[i - 2])
                i += 3
            }
            return d
        }
    }

    private var current: Live? = null
    private var erasedAny = false
    private var eraseBefore: Pair<List<Stroke>, List<TextBox>>? = null
    private var lastX = 0f
    private var lastY = 0f
    private var lastSpan = 0f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private val pt = FloatArray(2)

    private var velocity: VelocityTracker? = null
    private val scroller = OverScroller(context)

    private var downAt = 0L
    private var downX = 0f
    private var downY = 0f
    private var lastTapAt = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var twoFingerAt = 0L
    private var twoFingerMoved = false
    private var maxPointers = 0

    // Начало перетаскивания (лассо, фигура) в единицах страницы.
    private var startPX = 0f
    private var startPY = 0f
    private var shapePreview: List<Stroke> = emptyList()

    /** Перо задержали на месте — временный ластик, пока перо на экране. */
    private var tempEraser = false
    private var penDownX = 0f
    private var penDownY = 0f
    private var holdCancelled = false
    private val holdEraser = Runnable {
        if (mode != Mode.DRAW || !holdToErase || holdCancelled) return@Runnable
        if (current == null) return@Runnable
        current = null
        tempEraser = true
        debugTempEraser = true
        beginErase()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        listener?.onTemporaryEraser(true)
        listener?.onHint("Ластик, пока держишь перо")
        ringAnim?.cancel()
        ringAnim = ValueAnimator.ofFloat(0.2f, 1f).apply {
            duration = 260
            interpolator = OvershootInterpolator(2f)
            addUpdateListener { eraserRing = it.animatedValue as Float; invalidate() }
            start()
        }
        page?.let { eraseAt(it, touchX, touchY) }
    }

    // Нарисовал и задержал перо — фигура выравнивается.
    private var holdX = 0f
    private var holdY = 0f
    private var snapped: Stroke? = null
    private var morph: Stroke? = null
    private val snapShape = Runnable {
        if (mode != Mode.DRAW || !snapShapes || snapped != null) return@Runnable
        val l = current ?: return@Runnable
        if (l.pathLength() < 30f) return@Runnable
        val r = Shapes.recognize(l.toFloatArray()) ?: return@Runnable
        if (l.tool == Tool.HIGHLIGHTER && r.kind != ShapeKind.LINE) return@Runnable
        val target = if (l.tool == Tool.HIGHLIGHTER) {
            Stroke(Tool.HIGHLIGHTER, l.color, l.width, floatArrayOf(r.xy[0], r.xy[1], 0.5f, r.xy[2], r.xy[3], 0.5f))
        } else {
            Shapes.strokeOf(r, l.width, l.color)
        }
        snapped = target
        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        listener?.onHint("${r.kind.label} — ровно")
        animateMorph(l, target)
    }

    /** Каракуля плавно перетекает в ровную фигуру. */
    private fun animateMorph(from: Live, to: Stroke) {
        val n = 96
        val a = Shapes.resample(FloatArray(from.rawSize / 3 * 2) { k -> from.rawPts[(k / 2) * 3 + k % 2] }, n)
        val b = Shapes.resample(FloatArray(to.size * 2) { k -> to.points[(k / 2) * 3 + k % 2] }, n)
        current = null
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener {
                val t = it.animatedValue as Float
                val pts = FloatArray(n * 3) { k ->
                    val i = k / 3
                    when (k % 3) {
                        0 -> a[i * 2] + (b[i * 2] - a[i * 2]) * t
                        1 -> a[i * 2 + 1] + (b[i * 2 + 1] - a[i * 2 + 1]) * t
                        else -> 0.5f
                    }
                }
                morph = if (t >= 1f) to else Stroke(if (to.tool == Tool.HIGHLIGHTER) Tool.HIGHLIGHTER else Tool.SHAPE, to.color, to.width, pts)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    morph = if (mode == Mode.DRAW) to else null
                    invalidate()
                }
            })
            start()
        }
    }

    private fun isPen(e: MotionEvent, i: Int): Boolean {
        val t = e.getToolType(i)
        return t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER
    }

    private fun toPage(x: Float, y: Float): FloatArray {
        pageMatrix.invert(inverse)
        pt[0] = x; pt[1] = y
        inverse.mapPoints(pt)
        return pt
    }

    private fun newLive(): Live = when (editTool) {
        EditTool.HIGHLIGHTER -> Live(Tool.HIGHLIGHTER, color, markerWidth)
        else -> Live(Tool.PEN, color, penWidth)
    }

    private fun addPoints(l: Live, e: MotionEvent, i: Int, pen: Boolean) {
        for (h in 0 until e.historySize) {
            val q = toPage(e.getHistoricalX(i, h), e.getHistoricalY(i, h))
            l.add(q[0], q[1], if (pen) e.getHistoricalPressure(i, h) else FINGER_PRESSURE)
        }
        val q = toPage(e.getX(i), e.getY(i))
        l.add(q[0], q[1], pressure(e, i, pen))
    }

    /** Что делает перо (или палец, если им можно писать) при касании. */
    private fun penMode(e: MotionEvent, i: Int, pen: Boolean): Mode {
        val eraserBtn = (e.buttonState and (
            MotionEvent.BUTTON_STYLUS_PRIMARY or
            MotionEvent.BUTTON_STYLUS_SECONDARY or
            MotionEvent.BUTTON_SECONDARY or
            MotionEvent.BUTTON_BACK
        )) != 0
        val eraserEnd = pen && (e.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER || eraserBtn)
        if (eraserEnd) return Mode.ERASE
        val q = toPage(e.getX(i), e.getY(i))
        return when (editTool) {
            EditTool.PEN, EditTool.HIGHLIGHTER -> Mode.DRAW
            EditTool.ERASER -> Mode.ERASE
            EditTool.LASSO -> when {
                hasSelection && nearResizeHandle(q[0], q[1]) -> Mode.RESIZE
                hasSelection && insideSelection(q[0], q[1]) -> Mode.MOVE
                else -> Mode.LASSO
            }
            EditTool.SHAPE -> Mode.SHAPE
            EditTool.TEXT -> Mode.TEXT
        }
    }

    /** Начать действие пером в точке (x, y) экрана. */
    private fun startPen(e: MotionEvent, i: Int, pen: Boolean) {
        mode = penMode(e, i, pen)
        touchX = e.getX(i); touchY = e.getY(i)
        val q = toPage(touchX, touchY)
        startPX = q[0]; startPY = q[1]
        when (mode) {
            Mode.DRAW -> {
                listener?.onWritingStarted()
                snapped = null
                morph = null
                current = newLive().also { it.add(q[0], q[1], pressure(e, i, pen)) }
                holdX = touchX; holdY = touchY
                penDownX = touchX; penDownY = touchY
                holdCancelled = false
                removeCallbacks(holdEraser)
                if (holdToErase && pen) postDelayed(holdEraser, HOLD_ERASE_MS)
            }
            Mode.ERASE -> {
                listener?.onWritingStarted()
                beginErase()
                page?.let { eraseAt(it, touchX, touchY) }
            }
            Mode.LASSO -> {
                clearSelection()
                lassoPath.clear()
                lassoPath += q[0]; lassoPath += q[1]
            }
            Mode.MOVE -> {
                moveDx = 0f; moveDy = 0f
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            Mode.RESIZE -> {
                val b = selectionBounds()
                if (b != null) {
                    resizeAnchorX = b.left
                    resizeAnchorY = b.top
                    resizeOrigW = max(10f, b.width())
                    resizeOrigH = max(10f, b.height())
                }
                resizeScale = 1f
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            Mode.SHAPE -> {
                listener?.onWritingStarted()
                shapePreview = emptyList()
            }
            else -> Unit
        }
        invalidate()
    }

    private fun beginErase() {
        val p = page ?: return
        mode = Mode.ERASE
        erasedAny = false
        eraseBefore = p.strokes.toList() to p.texts.toList()
    }

    private fun movePen(e: MotionEvent, i: Int, pen: Boolean, p: InkPage) {
        val x = e.getX(i); val y = e.getY(i)
        when (mode) {
            Mode.DRAW -> {
                if (snapped != null) return // фигура уже выровнена — ждём, когда поднимут перо
                val l = current ?: return
                addPoints(l, e, i, pen)
                // Сдвинул перо от места касания — это письмо, а не «подержать для ластика».
                if (!holdCancelled && hypot(x - penDownX, y - penDownY) > HOLD_SLOP * density) {
                    holdCancelled = true
                    removeCallbacks(holdEraser)
                }
                touchX = x; touchY = y
                // Перо стоит на месте — через миг пробуем выровнять фигуру.
                if (hypot(x - holdX, y - holdY) > SNAP_SLOP * density) {
                    holdX = x; holdY = y
                    removeCallbacks(snapShape)
                    if (snapShapes) postDelayed(snapShape, SNAP_MS)
                }
                invalidate()
            }
            Mode.ERASE -> {
                for (h in 0 until e.historySize) eraseAt(p, e.getHistoricalX(i, h), e.getHistoricalY(i, h))
                eraseAt(p, x, y)
                touchX = x; touchY = y
                invalidate()
            }
            Mode.LASSO -> {
                val q = toPage(x, y)
                lassoPath += q[0]; lassoPath += q[1]
                invalidate()
            }
            Mode.MOVE -> {
                val q = toPage(x, y)
                moveDx = q[0] - startPX
                moveDy = q[1] - startPY
                reportSelection()
                invalidate()
            }
            Mode.RESIZE -> {
                val q = toPage(x, y)
                val curW = q[0] - resizeAnchorX
                val curH = q[1] - resizeAnchorY
                val sx = curW / resizeOrigW
                val sy = curH / resizeOrigH
                resizeScale = ((sx + sy) / 2f).coerceIn(0.15f, 4.0f)
                reportSelection()
                invalidate()
            }
            Mode.SHAPE -> {
                val q = toPage(x, y)
                shapePreview = Shapes.build(shapeKind, startPX, startPY, q[0], q[1], penWidth, color)
                invalidate()
            }
            else -> Unit
        }
    }

    private fun endPen(e: MotionEvent, i: Int, pen: Boolean, p: InkPage) {
        removeCallbacks(holdEraser)
        removeCallbacks(snapShape)
        when (mode) {
            Mode.DRAW -> {
                val s = snapped
                if (s != null) {
                    snapped = null
                    morph = null
                    current = null
                    addStroke(p, s)
                } else {
                    current?.let { addPoints(it, e, i, pen) }
                    commitStroke(p)
                }
            }
            Mode.ERASE -> finishErase(p)
            Mode.LASSO -> {
                val poly = lassoPath.toFloatArray()
                lassoPath.clear()
                val reach = 28f * density / scale
                // Одиночный тап (касание по штриху или тексту) — выделяем сразу без обведения!
                if (poly.size <= 4) {
                    val tx = if (poly.isNotEmpty()) poly[0] else touchX
                    val ty = if (poly.size > 1) poly[1] else touchY
                    val hitStrokes = StrokeGeometry.hit(p.strokes, tx, ty, reach)
                    val hitText = textAt(tx, ty)
                    if (hitStrokes.isNotEmpty() || hitText != null) {
                        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        select(hitStrokes, listOfNotNull(hitText))
                    } else {
                        invalidate()
                    }
                } else {
                    var strokes = Selection.strokesIn(poly, p.strokes)
                    var texts = Selection.textsIn(poly, p.texts)
                    // Если строго внутри ничего не нашлось — проверяем любое касание или пересечение лассо
                    if (strokes.isEmpty() && texts.isEmpty()) {
                        strokes = Selection.strokesTouching(poly, p.strokes, reach)
                        texts = p.texts.filter { t ->
                            val b = t.bounds()
                            Selection.inside(poly, (b[0] + b[2]) / 2, (b[1] + b[3]) / 2) ||
                                (0 until poly.size / 2).any { j ->
                                    val px = poly[j * 2]; val py = poly[j * 2 + 1]
                                    px in b[0]..b[2] && py in b[1]..b[3]
                                }
                        }
                    }
                    if (strokes.isEmpty() && texts.isEmpty()) {
                        invalidate()
                        if (poly.size > 8) listener?.onHint("Ничего не выделено — коснись записи или обведи её")
                    } else {
                        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        select(strokes, texts)
                    }
                }
            }
            Mode.MOVE -> commitMove()
            Mode.RESIZE -> commitResize()
            Mode.SHAPE -> {
                val shapes = shapePreview
                shapePreview = emptyList()
                if (shapes.isNotEmpty() && hypot(touchX - e.getX(i), touchY - e.getY(i)) > 4 * density) {
                    replace(p, p.strokes + shapes, p.texts.toList())
                }
                invalidate()
            }
            Mode.TEXT -> {
                val q = toPage(e.getX(i), e.getY(i))
                if (hypot(e.getX(i) - touchX, e.getY(i) - touchY) < TOUCH_SLOP * 2 * density) {
                    listener?.onTextRequest(q[0], q[1], textAt(q[0], q[1]))
                }
            }
            else -> Unit
        }
        if (tempEraser) {
            tempEraser = false
            debugTempEraser = false
            listener?.onTemporaryEraser(false)
        }
        mode = Mode.NONE
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val p = page ?: return false
        if (e.actionMasked == MotionEvent.ACTION_DOWN) cancelTurn()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                matrixAnim?.cancel()
                scroller.forceFinished(true)
                activePointer = e.getPointerId(0)
                maxPointers = 1
                hovering = false
                val pen = isPen(e, 0)
                if (pen && Build.VERSION.SDK_INT >= 30) requestUnbufferedDispatch(e)
                lastX = e.x; lastY = e.y
                downAt = SystemClock.uptimeMillis(); downX = e.x; downY = e.y
                if (pen || fingerDraws) {
                    startPen(e, 0, pen)
                } else {
                    val q = toPage(e.x, e.y)
                    // Пальцем можно тянуть маркер изменения размера или перемещать выделение
                    mode = when {
                        hasSelection && nearResizeHandle(q[0], q[1]) -> {
                            val b = selectionBounds()
                            if (b != null) {
                                resizeAnchorX = b.left
                                resizeAnchorY = b.top
                                resizeOrigW = max(10f, b.width())
                                resizeOrigH = max(10f, b.height())
                            }
                            resizeScale = 1f
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            Mode.RESIZE
                        }
                        hasSelection && insideSelection(q[0], q[1]) -> {
                            startPX = q[0]; startPY = q[1]; moveDx = 0f; moveDy = 0f
                            Mode.MOVE
                        }
                        lockMode -> Mode.NONE
                        else -> Mode.PAN
                    }
                    velocity?.recycle()
                    velocity = VelocityTracker.obtain().also { it.addMovement(e) }
                }
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, e.pointerCount)
                val ai = e.actionIndex
                if (isPen(e, ai) && (mode == Mode.PAN || mode == Mode.NONE)) {
                    // Ладонь легла раньше пера: ладонь не двигает лист, работает перо.
                    endMoving()
                    activePointer = e.getPointerId(ai)
                    if (Build.VERSION.SDK_INT >= 30) requestUnbufferedDispatch(e)
                    startPen(e, ai, true)
                    return true
                }
                // Второй палец, когда пишут пальцем, — хотят двигать или увеличивать лист.
                if (!isPen(e, e.findPointerIndex(activePointer).coerceAtLeast(0)) && mode != Mode.PAN) {
                    removeCallbacks(holdEraser); removeCallbacks(snapShape)
                    when (mode) {
                        Mode.DRAW -> if ((current?.rawSize ?: 0) < 3 * 6) current = null else commitStroke(p)
                        Mode.ERASE -> finishErase(p)
                        Mode.LASSO -> lassoPath.clear()
                        Mode.MOVE -> { moveDx = 0f; moveDy = 0f; reportSelection() }
                        Mode.RESIZE -> { resizeScale = 1f; reportSelection() }
                        Mode.SHAPE -> shapePreview = emptyList()
                        else -> Unit
                    }
                    mode = Mode.PAN
                }
                if ((mode == Mode.NONE || mode == Mode.PAN) && e.pointerCount >= 2 && !isPen(e, ai)) {
                    mode = Mode.PAN
                    twoFingerAt = SystemClock.uptimeMillis()
                    twoFingerMoved = false
                    startTwoFinger(e)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (mode) {
                    Mode.PAN -> {
                        velocity?.addMovement(e)
                        if (e.pointerCount >= 2) {
                            if (!twoFingerMoved) {
                                val span = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
                                val shift = hypot((e.getX(0) + e.getX(1)) / 2 - lastFocusX, (e.getY(0) + e.getY(1)) / 2 - lastFocusY)
                                if (abs(span - lastSpan) > TOUCH_SLOP * density || shift > TOUCH_SLOP * density) twoFingerMoved = true
                            }
                            if (twoFingerMoved) {
                                moving = true
                                twoFinger(e)
                            }
                        } else if (!lockMode) {
                            if (!moving && hypot(e.x - downX, e.y - downY) < TOUCH_SLOP * density) return true
                            moving = true
                            pageMatrix.postTranslate(e.x - lastX, e.y - lastY)
                            lastX = e.x; lastY = e.y
                            clampMatrix()
                            if (hasSelection) reportSelection()
                        }
                        invalidate()
                    }
                    Mode.NONE -> Unit
                    else -> {
                        val i = e.findPointerIndex(activePointer).takeIf { it >= 0 } ?: return true
                        movePen(e, i, isPen(e, i), p)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> if (e.getPointerId(e.actionIndex) == activePointer && mode != Mode.PAN && mode != Mode.NONE) {
                // Перо подняли, а ладонь ещё лежит — действие закончено.
                endPen(e, e.actionIndex, isPen(e, e.actionIndex), p)
            } else if (mode == Mode.PAN) {
                val rest = (0 until e.pointerCount).firstOrNull { it != e.actionIndex } ?: 0
                lastX = e.getX(rest); lastY = e.getY(rest)
                downX = lastX; downY = lastY
                if (e.pointerCount - 1 >= 2) startTwoFinger(e)
                velocity?.clear()
            }
            MotionEvent.ACTION_UP -> {
                when (mode) {
                    Mode.PAN -> {
                        velocity?.addMovement(e)
                        if (moving) fling() else tapped(e)
                        mode = Mode.NONE
                    }
                    Mode.NONE -> Unit
                    else -> endPen(e, 0, isPen(e, 0), p)
                }
                velocity?.recycle()
                velocity = null
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(holdEraser); removeCallbacks(snapShape)
                current = null
                snapped = null
                morph = null
                shapePreview = emptyList()
                lassoPath.clear()
                if (mode == Mode.ERASE) finishErase(p)
                if (mode == Mode.MOVE) { moveDx = 0f; moveDy = 0f }
                if (mode == Mode.RESIZE) { resizeScale = 1f }
                mode = Mode.NONE
                endMoving()
                cancelTurn()
                invalidate()
            }
        }
        return true
    }

    /** Касание пальцем без движения: текст, снять выделение, двойное — масштаб. */
    private fun tapped(e: MotionEvent) {
        endMoving()
        if (lockMode) return
        val now = SystemClock.uptimeMillis()
        if (now - downAt > TAP_MS) return
        val q = toPage(e.x, e.y)
        if (editTool == EditTool.TEXT) {
            listener?.onTextRequest(q[0], q[1], textAt(q[0], q[1]))
            return
        }
        if (hasSelection && !insideSelection(q[0], q[1])) {
            clearSelection()
            return
        }
        if (now - lastTapAt < DOUBLE_TAP_MS && hypot(e.x - lastTapX, e.y - lastTapY) < 40 * density) {
            lastTapAt = 0
            if (scale > fitScale() * 1.15f) {
                animateFitWidth()
            } else {
                val target = Matrix(pageMatrix).apply { postScale(2.2f, 2.2f, e.x, e.y) }
                clamp(target)
                animateMatrix(target)
            }
        } else {
            lastTapAt = now
            lastTapX = e.x; lastTapY = e.y
        }
    }

    private fun fling() {
        if (lockMode) return endMoving()
        val vt = velocity ?: return endMoving()
        vt.computeCurrentVelocity(1000)
        val vx = vt.xVelocity
        val vy = vt.yVelocity

        if (hypot(vx, vy) < MIN_FLING * density) return endMoving()
        val v = FloatArray(9).also { pageMatrix.getValues(it) }
        val p = page ?: return endMoving()
        val k = v[Matrix.MSCALE_X]
        val mg = MARGIN * density
        val pw = PAGE_W * k
        val ph = p.height * k
        if (pw + 2 * mg <= width && ph + 2 * mg <= height) {
            return endMoving()
        }
        val minX = if (pw + 2 * mg <= width) (width - pw) / 2f else width - pw - mg
        val maxX = if (pw + 2 * mg <= width) (width - pw) / 2f else mg
        val minY = if (ph + 2 * mg <= height) (height - ph) / 2f else height - ph - mg * 4
        val maxY = if (ph + 2 * mg <= height) (height - ph) / 2f else mg
        scroller.fling(
            v[Matrix.MTRANS_X].toInt(), v[Matrix.MTRANS_Y].toInt(), vx.toInt(), vy.toInt(),
            minX.toInt(), maxX.toInt(), minY.toInt(), maxY.toInt(),
        )
        postOnAnimation(flingStep)
    }

    private val flingStep = object : Runnable {
        override fun run() {
            if (!scroller.computeScrollOffset()) {
                endMoving()
                return
            }
            val v = FloatArray(9).also { pageMatrix.getValues(it) }
            pageMatrix.postTranslate(scroller.currX - v[Matrix.MTRANS_X], scroller.currY - v[Matrix.MTRANS_Y])
            clampMatrix()
            if (hasSelection) reportSelection()
            invalidate()
            postOnAnimation(this)
        }
    }

    private fun endMoving() {
        if (!moving) return
        moving = false
        zoomChanged()
        invalidate()
    }

    private fun pressure(e: MotionEvent, i: Int, pen: Boolean) = if (pen) e.getPressure(i) else FINGER_PRESSURE

    private fun commitStroke(p: InkPage) {
        val l = current ?: return
        current = null
        if (l.rawSize == 0) return
        addStroke(p, Stroke(l.tool, l.color, l.width, StrokeGeometry.thin(l.toFloatArray(), 0.4f)))
    }

    private fun addStroke(p: InkPage, s: Stroke) {
        p.strokes += s
        p.touch()
        undo.push(InkEdit.Added(s))
        drawIntoCache(s)
        edited(full = false)
    }

    private fun eraseAt(p: InkPage, x: Float, y: Float) {
        val q = toPage(x, y)
        val r = ERASER_RADIUS * density / scale
        if (partialEraser && !tempEraser) {
            var changed = false
            val out = ArrayList<Stroke>(p.strokes.size)
            val gone = ArrayList<Stroke>()
            for (s in p.strokes) {
                val pieces = Selection.cut(s, q[0], q[1], r)
                if (pieces == null) out += s else { out += pieces; changed = true; if (pieces.isEmpty()) gone += s }
            }
            if (!changed) return
            p.strokes.clear(); p.strokes.addAll(out)
            fadeOut(gone)
        } else {
            val hit = StrokeGeometry.hit(p.strokes, q[0], q[1], r)
            if (hit.isEmpty()) return
            p.strokes.removeAll(hit.toSet())
            fadeOut(hit)
        }
        erasedAny = true
        p.touch()
        invalidateCache()
    }

    private fun finishErase(p: InkPage) {
        val before = eraseBefore
        eraseBefore = null
        if (!erasedAny || before == null) return
        erasedAny = false
        undo.push(InkEdit.Replace(before.first, before.second, p.strokes.toList(), p.texts.toList()))
        edited(full = true)
        invalidateCache()
    }

    private fun startTwoFinger(e: MotionEvent) {
        if (e.pointerCount < 2) return
        lastFocusX = (e.getX(0) + e.getX(1)) / 2
        lastFocusY = (e.getY(0) + e.getY(1)) / 2
        lastSpan = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
    }

    private fun twoFinger(e: MotionEvent) {
        val fx = (e.getX(0) + e.getX(1)) / 2
        val fy = (e.getY(0) + e.getY(1)) / 2
        val span = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
        if (!lockMode && lastSpan > 10f) {
            val k = scale
            val f = (span / lastSpan).coerceIn(0.5f, 2f)
            val nk = (k * f).coerceIn(fitScale() * MIN_ZOOM, fitScale() * MAX_ZOOM)
            pageMatrix.postScale(nk / k, nk / k, fx, fy)
        }
        pageMatrix.postTranslate(fx - lastFocusX, fy - lastFocusY)
        lastFocusX = fx; lastFocusY = fy; lastSpan = span
        clampMatrix()
        if (hasSelection) reportSelection()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        detached = false
        if (renderExecutor.isShutdown) renderExecutor = newRenderExecutor()
        frontValid = false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        detached = true
        renderInFlight = false
        renderPending = false
        renderGen.incrementAndGet()
        renderExecutor.shutdownNow()
        cancelTurn()
        if (frontBmp?.isRecycled == false) frontBmp?.recycle()
        if (spareBmp != null && spareBmp !== frontBmp && spareBmp?.isRecycled == false) spareBmp?.recycle()
        frontBmp = null
        frontCanvas = null
        spareBmp = null
        frontPage = null
        frontValid = false
        inFlightStrokes.clear()
        removeCallbacks(flingStep)
        removeCallbacks(holdEraser)
        removeCallbacks(snapShape)
        matrixAnim?.cancel()
        antsAnim?.cancel()
    }

    companion object {
        /** Отметки на открытом листе — по ним тесты проверяют ответ наставника в тетради. */
        @Volatile
        var shownMarks: List<PageMark> = emptyList()
            private set

        /** Включён ли временный ластик (перо задержали) — для тестов. */
        @Volatile
        var debugTempEraser = false
            private set

        /** Сколько выделено лассо — для тестов. */
        @Volatile
        var debugSelectionCount = 0
            private set

        /** Цвет «стола» вокруг листа. */
        const val DESK = 0xFFEDEAE4.toInt()
        const val DESK_DARK = 0xFF141417.toInt()
        private const val MARGIN = 16f
        private const val MIN_ZOOM = 0.6f
        private const val MAX_ZOOM = 6f
        private const val FINGER_PRESSURE = 0.55f
        private const val ERASER_RADIUS = 14f
        private const val TOUCH_SLOP = 6f
        private const val HOLD_SLOP = 5f
        private const val SNAP_SLOP = 3f
        private const val TAP_MS = 250L
        private const val DOUBLE_TAP_MS = 320L
        private const val MIN_FLING = 400f

        /** Сколько держать перо на месте, чтобы включился ластик. */
        const val HOLD_ERASE_MS = 900L

        /** Сколько задержать перо в конце, чтобы фигура выровнялась. */
        const val SNAP_MS = 550L
        const val HIGHLIGHTER_W = 20f
    }
}
