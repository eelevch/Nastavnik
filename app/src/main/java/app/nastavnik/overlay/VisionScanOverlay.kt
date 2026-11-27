package app.nastavnik.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import app.nastavnik.geom.IRect
import app.nastavnik.session.ShotGeometry
import app.nastavnik.theme.MascotColor
import app.nastavnik.theme.MentorVisionStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Неперехватывающий оверлей поверх сторонних приложений для заметок, показывающий,
 * как наставник считывает страницу и подсвечивает найденные строки/формулы во время анализа.
 */
class VisionScanOverlay(
    private val context: Context,
    private val wm: WindowManager,
) {
    private var view: ScanView? = null
    private var params: WindowManager.LayoutParams? = null
    private var showing = false
    private var captureHidden = false

    fun show(
        geometry: ShotGeometry,
        mascotColor: MascotColor,
        themeAccent: Int,
        style: MentorVisionStyle,
    ) {
        if (style == MentorVisionStyle.OFF) {
            dismiss()
            return
        }
        val lineRects = geometry.lines?.lines.orEmpty().map { line ->
            val sx = geometry.screen.width.toFloat() / geometry.imageW.coerceAtLeast(1)
            val sy = geometry.screen.height.toFloat() / geometry.imageH.coerceAtLeast(1)
            RectF(
                line.left * sx,
                line.top * sy,
                line.right * sx,
                line.bottom * sy,
            )
        }
        val area = geometry.screen
        val currentView = view
        if (currentView != null && showing) {
            currentView.updateData(lineRects, mascotColor, themeAccent, style)
            return
        }
        val v = ScanView(context, lineRects, mascotColor, themeAccent, style)
        val p = OverlayWindows.absParams(
            x = area.left,
            y = area.top,
            width = area.width.coerceAtLeast(1),
            height = area.height.coerceAtLeast(1),
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        ).apply {
            alpha = if (captureHidden) 0f else OverlayWindows.MAX_PASS_THROUGH_ALPHA
        }
        if (runCatching { wm.addView(v, p) }.isSuccess) {
            view = v
            params = p
            showing = true
        }
    }

    fun setCaptureHidden(hidden: Boolean) {
        captureHidden = hidden
        val v = view ?: return
        val p = params ?: return
        p.alpha = if (hidden) 0f else OverlayWindows.MAX_PASS_THROUGH_ALPHA
        runCatching { wm.updateViewLayout(v, p) }
    }

    fun dismiss() {
        val v = view ?: return
        view = null
        params = null
        showing = false
        runCatching { wm.removeView(v) }
    }

    @SuppressLint("ViewConstructor")
    private class ScanView(
        context: Context,
        private var lines: List<RectF>,
        private var mascotColor: MascotColor,
        private var themeAccent: Int,
        private var style: MentorVisionStyle,
    ) : View(context) {

        private val density = resources.displayMetrics.density
        private var timeSec = 0f
        private var anim: ValueAnimator? = null

        private var stops = mascotColor.resolveStops(themeAccent)
        private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * density
            strokeCap = Paint.Cap.ROUND
        }
        private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val beamLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * density
            strokeCap = Paint.Cap.ROUND
        }
        private val lineFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val lineStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * density
        }
        private val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * density
            strokeCap = Paint.Cap.ROUND
            pathEffect = DashPathEffect(floatArrayOf(6f * density, 5f * density), 0f)
        }
        private val tracePath = Path()
        private val box = RectF()
        private var beamW = -1

        init {
            applyPalette()
        }

        fun updateData(
            lines: List<RectF>,
            mascotColor: MascotColor,
            themeAccent: Int,
            style: MentorVisionStyle,
        ) {
            this.lines = lines
            this.mascotColor = mascotColor
            this.themeAccent = themeAccent
            this.style = style
            applyPalette()
            invalidate()
        }

        private fun applyPalette() {
            stops = mascotColor.resolveStops(themeAccent)
            bracketPaint.color = withAlpha(stops.midArgb, 0.72f)
            beamLinePaint.color = withAlpha(stops.sparkArgb, 0.92f)
            tracePaint.color = withAlpha(stops.sparkArgb, 0.65f)
            beamW = -1
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            anim?.cancel()
            anim = ValueAnimator.ofFloat(0f, 3600f).apply {
                duration = 3_600_000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    timeSec = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onDetachedFromWindow() {
            anim?.cancel()
            anim = null
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 2f || h <= 2f) return

            // 1. 4 угловых маркера чтения экрана.
            val m = 10f * density
            val arm = 24f * density
            bracketPaint.alpha = ((0.45f + 0.30f * sin(timeSec * 3.8f)) * 255f).toInt().coerceIn(40, 220)
            canvas.drawLine(m, m + arm, m, m, bracketPaint)
            canvas.drawLine(m, m, m + arm, m, bracketPaint)
            canvas.drawLine(w - m - arm, m, w - m, m, bracketPaint)
            canvas.drawLine(w - m, m, w - m, m + arm, bracketPaint)
            canvas.drawLine(m, h - m - arm, m, h - m, bracketPaint)
            canvas.drawLine(m, h - m, m + arm, h - m, bracketPaint)
            canvas.drawLine(w - m - arm, h - m, w - m, h - m, bracketPaint)
            canvas.drawLine(w - m, h - m, w - m, h - m - arm, bracketPaint)

            // 2. Световой луч сканирования.
            val cycle = (timeSec / 1.35f) % 1f
            val sweep = (1f - cos(cycle * PI.toFloat())) * 0.5f
            val scanY = m + sweep * (h - 2f * m)

            if (style == MentorVisionStyle.AURORA_SCAN) {
                val beamH = 92f * density
                val top = (scanY - beamH).coerceAtLeast(0f)
                beamPaint.shader = LinearGradient(
                    0f,
                    top,
                    0f,
                    scanY,
                    intArrayOf(Color.TRANSPARENT, withAlpha(stops.haloArgb, 0.18f), withAlpha(stops.sparkArgb, 0.34f)),
                    floatArrayOf(0f, 0.72f, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRect(m, top, w - m, scanY, beamPaint)
                canvas.drawLine(m, scanY, w - m, scanY, beamLinePaint)
            } else if (style == MentorVisionStyle.SUBTLE) {
                val barW = w * 0.42f
                val bx = m + (w - 2f * m - barW) * sweep
                canvas.drawLine(bx, m, bx + barW, m, beamLinePaint)
            }

            // 3. Распознанные строки и плавный фокус анализа.
            if (style != MentorVisionStyle.SUBTLE && lines.isNotEmpty()) {
                val activeIdx = ((timeSec / 0.9f).toInt()) % lines.size
                val r = 10f * density
                lines.forEachIndexed { idx, src ->
                    val isFocus = timeSec > 1.25f && idx == activeIdx
                    val dist = (1f - abs(src.centerY() - scanY) / (h * 0.25f)).coerceIn(0.22f, 1f)
                    val a = if (isFocus) 1f else dist
                    lineFillPaint.color = withAlpha(stops.haloArgb, if (isFocus) 0.16f else 0.08f * a)
                    lineStrokePaint.color = withAlpha(stops.midArgb, if (isFocus) 0.85f else 0.40f * a)
                    box.set(src)
                    box.inset(-6f * density, -4f * density)
                    canvas.drawRoundRect(box, r, r, lineFillPaint)
                    canvas.drawRoundRect(box, r, r, lineStrokePaint)
                }

                if (timeSec > 1.25f && lines.size > 1) {
                    val prev = lines[(activeIdx - 1 + lines.size) % lines.size]
                    val curr = lines[activeIdx]
                    val ctrlX = minOf(prev.left, curr.left) - 24f * density
                    tracePath.reset()
                    tracePath.moveTo(prev.left, prev.centerY())
                    tracePath.cubicTo(ctrlX, prev.centerY(), ctrlX, curr.centerY(), curr.left, curr.centerY())
                    canvas.drawPath(tracePath, tracePaint)
                }
            }
        }

        private fun withAlpha(argb: Int, alpha: Float): Int {
            val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
            return (argb and 0x00FFFFFF) or (a shl 24)
        }
    }
}
