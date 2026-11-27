package app.nastavnik.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import app.nastavnik.data.DeviceProfile
import app.nastavnik.theme.MascotColor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Круглая плавающая кнопка-сфера наставника. Реагирует только на палец: касание стилусом или ластиком
 * не нажимает кнопку, а сообщает [Listener.onStylus], чтобы окно временно стало «прозрачным» для касаний.
 */
@SuppressLint("ViewConstructor")
class BubbleView(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        fun onTap()
        fun onLongPress()
        fun onDragStart()
        fun onDrag(dx: Int, dy: Int)
        fun onDragEnd()
        fun onStylus()
    }

    private val density = resources.displayMetrics.density
    private val isWeakDevice = DeviceProfile.isWeakDevice(context)

    private var stops = MascotColor.SUNSET.resolveStops(0xFF2563EB.toInt())

    var mascotColor: MascotColor = MascotColor.SUNSET
        set(value) {
            if (field == value) return
            field = value
            stops = value.resolveStops(themeAccentArgb)
            rebuildShaders()
            invalidate()
        }

    var themeAccentArgb: Int = 0xFF2563EB.toInt()
        set(value) {
            if (field == value) return
            field = value
            if (mascotColor == MascotColor.THEME) {
                stops = mascotColor.resolveStops(value)
                rebuildShaders()
                invalidate()
            }
        }

    private val spherePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        setShadowLayer(6f * density, 0f, 2.5f * density, 0x380A0C14)
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.1f * density
        color = Color.argb(65, 255, 255, 255)
    }
    private val eyeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 0, 0, 0)
    }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val thinkArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.6f * density
        strokeCap = Paint.Cap.ROUND
        color = stops.sparkArgb
    }
    private val arcRect = RectF()
    private val eyeRect = RectF()
    private val shaderMatrix = Matrix()
    private var sphereGradient: RadialGradient? = null

    private var animTimeSec = 0f
    private var lifeAnimator: ValueAnimator? = null

    /** Пока нейросеть думает, вокруг сферы вращается светящаяся дуга, а внутреннее ядро ускоряется. */
    var busy: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            syncAnimatorState()
            invalidate()
        }

    /** Включена фоновая проверка — маленькая зелёная точка на кнопке. */
    var watching: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4ADE80.toInt() }
    private val dotBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val flashRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.6f * density
    }
    private var flashUntil = 0L

    /** Короткая вспышка кольца одним цветом: например, зелёным — «проверил, всё верно». */
    fun flash(color: Int, ms: Long = 1600) {
        flashRing.color = color
        flashUntil = System.currentTimeMillis() + ms
        invalidate()
        postDelayed({ invalidate() }, ms + 20)
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var longPressed = false
    private var tracking = false
    private val longPressRunnable = Runnable {
        if (tracking && !dragging) {
            longPressed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener.onLongPress()
        }
    }

    init {
        contentDescription = "Наставник"
        isHapticFeedbackEnabled = true
        // Тень у круга рисуется программно.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun rebuildShaders() {
        val pad = 5f * density
        val r = (width.coerceAtMost(height) / 2f - pad).coerceAtLeast(1f)
        sphereGradient = RadialGradient(
            0f,
            0f,
            r * 1.24f,
            intArrayOf(stops.coreArgb, stops.midArgb, stops.rimArgb, stops.rimArgb),
            floatArrayOf(0f, 0.50f, 0.86f, 1f),
            Shader.TileMode.CLAMP,
        )
        thinkArcPaint.color = stops.sparkArgb
    }

    private fun syncAnimatorState() {
        val shouldAnimate = isAttachedToWindow &&
            windowVisibility == VISIBLE &&
            visibility == VISIBLE &&
            (!isWeakDevice || busy)
        if (shouldAnimate) {
            if (lifeAnimator == null) {
                lifeAnimator = ValueAnimator.ofFloat(0f, 3600f).apply {
                    duration = 3_600_000L
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    addUpdateListener {
                        animTimeSec = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            }
        } else {
            lifeAnimator?.cancel()
            lifeAnimator = null
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rebuildShaders()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncAnimatorState()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        syncAnimatorState()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        syncAnimatorState()
    }

    override fun onDetachedFromWindow() {
        lifeAnimator?.cancel()
        lifeAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val pad = 5 * density
        val r = width.coerceAtMost(height) / 2f - pad
        if (r <= 1f) return
        val cx = width / 2f
        val cy = height / 2f
        val t = animTimeSec

        // 1. Вектор взгляда: саккады в покое, орбитальное слежение в размышлении.
        val saccadeX = bubbleSaccadeX(t) * r * 0.11f
        val saccadeY = bubbleSaccadeY(t) * r * 0.045f
        val orbitRad = t * (2f * PI.toFloat() / 1.45f)
        val thinkX = cos(orbitRad) * r * 0.14f
        val thinkY = sin(orbitRad * 2f) * r * 0.045f - r * 0.02f
        val lookX = if (busy) thinkX else saccadeX
        val lookY = if (busy) thinkY else saccadeY
        val normLookX = (lookX / (r * 0.14f)).coerceIn(-1f, 1f)

        // 2. Живой радиальный градиент сферы без аллокаций в кадре.
        val driftSpeed = if (busy) 2.6f else 1.15f
        val driftX = sin(t * driftSpeed) * r * (if (busy) 0.18f else 0.08f)
        val driftY = cos(t * driftSpeed * 0.85f) * r * (if (busy) 0.18f else 0.08f)
        sphereGradient?.let { grad ->
            shaderMatrix.setTranslate(cx + lookX * 0.72f + driftX, cy + lookY * 0.72f + driftY)
            grad.setLocalMatrix(shaderMatrix)
            spherePaint.shader = grad
        }
        canvas.drawCircle(cx, cy, r, spherePaint)
        canvas.drawCircle(cx, cy, r - rimPaint.strokeWidth * 0.5f, rimPaint)

        // 3. Индикатор вспышки или орбитального размышления.
        if (System.currentTimeMillis() < flashUntil) {
            canvas.drawCircle(cx, cy, r - flashRing.strokeWidth / 2f, flashRing)
        } else if (busy) {
            val ar = r - thinkArcPaint.strokeWidth / 2f
            arcRect.set(cx - ar, cy - ar, cx + ar, cy + ar)
            val spin = (t * 235f) % 360f
            canvas.drawArc(arcRect, spin - 90f, 78f, false, thinkArcPaint)
        }

        // 4. Белые вертикальные капсулы глаз с морганием и 3D-перспективой.
        val eyeY = cy - r * 0.02f
        val eyeOffX = r * 0.26f
        val eyeW = r * 0.285f
        val eyeH = r * 0.45f
        val blink = bubbleBlinkFactor(t) * (if (busy) 0.86f else 1f)
        val shadowOffset = (r * 0.03f).coerceAtLeast(0.6f)
        for (i in 0..1) {
            val sign = if (i == 0) -1f else 1f
            val perspective = 1f - sign * normLookX * 0.12f
            val w = eyeW * perspective
            val h = (eyeH * blink).coerceAtLeast(eyeH * 0.15f)
            val ex = cx + sign * eyeOffX * (1f - abs(normLookX) * 0.04f) + lookX
            val ey = eyeY + lookY
            val rx = w * 0.5f

            eyeRect.set(ex - w * 0.5f, ey - h * 0.5f + shadowOffset, ex + w * 0.5f, ey + h * 0.5f + shadowOffset)
            canvas.drawRoundRect(eyeRect, rx, rx, eyeShadowPaint)

            eyeRect.set(ex - w * 0.5f, ey - h * 0.5f, ex + w * 0.5f, ey + h * 0.5f)
            canvas.drawRoundRect(eyeRect, rx, rx, eyePaint)
        }

        // 5. Зелёная точка фонового слежения.
        if (watching) {
            val dr = 4.5f * density
            val dx = cx + r * 0.68f
            val dy = cy - r * 0.68f
            canvas.drawCircle(dx, dy, dr + 1.5f * density, dotBorder)
            canvas.drawCircle(dx, dy, dr, dot)
        }
    }

    private fun isPen(event: MotionEvent): Boolean {
        val tool = event.getToolType(event.actionIndex.coerceAtLeast(0))
        return tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        // Стилус завис над кнопкой — значит, сейчас будут писать: пропускаем касания сквозь окно.
        if (isPen(event) && (event.actionMasked == MotionEvent.ACTION_HOVER_ENTER || event.actionMasked == MotionEvent.ACTION_HOVER_MOVE)) {
            listener.onStylus()
            return true
        }
        return super.onHoverEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isPen(event)) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) listener.onStylus()
            cancelTracking()
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
                longPressed = false
                tracking = true
                postDelayed(longPressRunnable, longPressTimeout)
                isPressed = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && !longPressed && hypot(dx, dy) > touchSlop) {
                    dragging = true
                    removeCallbacks(longPressRunnable)
                    listener.onDragStart()
                }
                if (dragging) listener.onDrag(dx.toInt(), dy.toInt())
            }
            MotionEvent.ACTION_UP -> {
                if (!tracking) return false
                removeCallbacks(longPressRunnable)
                isPressed = false
                tracking = false
                when {
                    dragging -> listener.onDragEnd()
                    !longPressed && abs(event.rawX - downX) <= touchSlop -> {
                        performClick()
                        listener.onTap()
                    }
                }
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) listener.onDragEnd()
                cancelTracking()
            }
            MotionEvent.ACTION_POINTER_DOWN -> Unit
        }
        return true
    }

    private fun cancelTracking() {
        removeCallbacks(longPressRunnable)
        tracking = false
        dragging = false
        isPressed = false
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

private fun bubbleSmoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun bubbleSaccadeX(timeSec: Float): Float {
    val cycle = 7.6f
    val p = (timeSec % cycle) / cycle
    return when {
        p < 0.25f -> 0f
        p < 0.34f -> bubbleSmoothstep(0.25f, 0.34f, p) * 0.75f
        p < 0.48f -> 0.75f
        p < 0.57f -> 0.75f - bubbleSmoothstep(0.48f, 0.57f, p) * 1.45f
        p < 0.70f -> -0.70f
        p < 0.80f -> -0.70f + bubbleSmoothstep(0.70f, 0.80f, p) * 0.70f
        else -> 0f
    }
}

private fun bubbleSaccadeY(timeSec: Float): Float {
    val cycle = 7.6f
    val p = (timeSec % cycle) / cycle
    return when {
        p < 0.48f -> 0f
        p < 0.57f -> -bubbleSmoothstep(0.48f, 0.57f, p) * 0.5f
        p < 0.70f -> -0.5f
        p < 0.80f -> -0.5f + bubbleSmoothstep(0.70f, 0.80f, p) * 0.5f
        else -> 0f
    }
}

private fun bubbleBlinkFactor(timeSec: Float): Float {
    val cycleLen = 3.8f
    val cycleIndex = (timeSec / cycleLen).toInt()
    val local = timeSec - cycleIndex * cycleLen
    val isDouble = (cycleIndex % 3) == 2

    fun single(t: Float, center: Float): Float {
        val dt = abs(t - center)
        if (dt >= 0.095f) return 1f
        val k = bubbleSmoothstep(0f, 0.095f, dt)
        return 0.14f + 0.86f * k
    }

    var open = single(local, 3.40f)
    if (isDouble) {
        open = minOf(open, single(local, 3.63f))
    }
    return open
}
