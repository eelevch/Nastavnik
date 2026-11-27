package app.nastavnik.ink

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import app.nastavnik.ai.Drawing
import app.nastavnik.ai.DrawingKind
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Геометрия рисунка в координатах текущей поверхности. */
object MentorDrawing {
    fun draw(canvas: Canvas, rect: RectF, drawing: Drawing, paint: Paint, unit: Float) {
        when (drawing.kind) {
            DrawingKind.ELLIPSE -> canvas.drawOval(rect, paint)
            DrawingKind.RECT -> canvas.drawRect(rect, paint)
            DrawingKind.TRIANGLE -> {
                val cx = rect.centerX()
                canvas.drawLine(cx, rect.top, rect.right, rect.bottom, paint)
                canvas.drawLine(rect.right, rect.bottom, rect.left, rect.bottom, paint)
                canvas.drawLine(rect.left, rect.bottom, cx, rect.top, paint)
            }
            DrawingKind.AXES -> {
                val ox = rect.left + rect.width() * 0.16f
                val oy = rect.bottom - rect.height() * 0.16f
                canvas.drawLine(rect.left, oy, rect.right, oy, paint)
                drawArrowHead(canvas, rect.left, oy, rect.right, oy, paint, 10f * unit)
                canvas.drawLine(ox, rect.bottom, ox, rect.top, paint)
                drawArrowHead(canvas, ox, rect.bottom, ox, rect.top, paint, 10f * unit)
            }
            DrawingKind.LINE, DrawingKind.ARROW -> {
                val x1 = if (drawing.reverseX) rect.right else rect.left
                val x2 = if (drawing.reverseX) rect.left else rect.right
                val y1 = if (drawing.reverseY) rect.bottom else rect.top
                val y2 = if (drawing.reverseY) rect.top else rect.bottom
                canvas.drawLine(x1, y1, x2, y2, paint)
                if (drawing.kind == DrawingKind.ARROW) {
                    drawArrowHead(canvas, x1, y1, x2, y2, paint, 12f * unit)
                }
            }
        }
    }

    private fun drawArrowHead(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint, length: Float) {
        val angle = atan2(y2 - y1, x2 - x1)
        val spread = (PI / 5.7).toFloat()
        for (side in listOf(-spread, spread)) {
            canvas.drawLine(x2, y2, x2 - length * cos(angle + side), y2 - length * sin(angle + side), paint)
        }
    }
}
