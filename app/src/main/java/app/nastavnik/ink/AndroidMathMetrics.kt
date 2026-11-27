package app.nastavnik.ink

import android.graphics.Paint
import android.text.TextPaint

/** Метрики текста на Android для точного 2D-выравнивания формул в [PageDrawer]. */
object AndroidMathMetrics : MathMetrics {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    override fun width(text: String, size: Float): Float {
        if (text.isEmpty()) return 0f
        paint.textSize = size
        val w = paint.measureText(text)
        return if (w > 0f) w else EstimateMetrics.width(text, size)
    }

    override fun baselineOffset(size: Float): Float {
        paint.textSize = size
        val top = -paint.fontMetrics.top
        return if (top > 0f) top else EstimateMetrics.baselineOffset(size)
    }
}
