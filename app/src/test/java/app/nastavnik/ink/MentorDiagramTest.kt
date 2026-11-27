package app.nastavnik.ink

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class MentorDiagramTest {
    @Test
    fun exprEvalEvaluatesMathExpressionsAndRejectsBrokenSyntax() {
        val poly = ExprEval.compile("2x^2-3x+1")
        assertNotNull(poly)
        assertEquals(3.0, poly!!(2.0), 1e-6)

        val trig = ExprEval.compile("sin(pi/2)")
        assertNotNull(trig)
        assertEquals(1.0, trig!!(0.0), 1e-6)

        assertNull(ExprEval.compile("2*("))
    }

    @Test
    fun axesAndPlotKeepAllPointsWithinSlot() {
        val slot = MentorNotebookWriter.SlotBox(48f, 96f, 648f, 480f)
        val src = """
            size 120 80
            axes 12 8 110 72 -4 4 -2 2 "x" "y"
            plot "sin(x)"
        """.trimIndent()
        val (strokes, _) = MentorDiagram.build(src, slot, Color.BLACK, EstimateMetrics)
        assertTrue("Expected strokes for axes + plot", strokes.size >= 5)
        for (s in strokes) {
            for (i in 0 until s.size) {
                val x = s.x(i); val y = s.y(i)
                assertTrue("x=$x out of slot [${slot.left}, ${slot.right}]", x in (slot.left - 1f)..(slot.right + 1f))
                assertTrue("y=$y out of slot [${slot.top}, ${slot.bottom}]", y in (slot.top - 1f)..(slot.bottom + 1f))
            }
        }
    }

    @Test
    fun reciprocalPlotSplitsAroundZeroAsymptote() {
        val slot = MentorNotebookWriter.SlotBox(48f, 96f, 648f, 480f)
        val axesOnlySrc = """
            size 100 60
            axes 10 8 92 54 -4 4 -4 4 "x" "y"
        """.trimIndent()
        val plotSrc = axesOnlySrc + "\n" + """plot "1/x""""
        val (axesStrokes, _) = MentorDiagram.build(axesOnlySrc, slot, Color.BLACK, EstimateMetrics)
        val (plotStrokes, _) = MentorDiagram.build(plotSrc, slot, Color.BLACK, EstimateMetrics)
        val extra = plotStrokes.size - axesStrokes.size
        assertTrue("Expected >= 2 split branches for 1/x, got $extra", extra >= 2)
    }

    @Test
    fun flowchartNodesAndLinksProduceFramesAndArrows() {
        val slot = MentorNotebookWriter.SlotBox(48f, 96f, 648f, 480f)
        val src = """
            size 100 60
            node a 25 30 "Начало"
            node b 75 30 "Конец"
            link a b "шаг"
        """.trimIndent()
        val (strokes, texts) = MentorDiagram.build(src, slot, Color.BLUE, EstimateMetrics)
        assertTrue(strokes.size >= 4)
        assertTrue(texts.any { it.text.contains("Начало") })
        assertTrue(texts.any { it.text.contains("Конец") })
    }

    @Test
    fun unknownCommandsAreIgnoredSafely() {
        val slot = MentorNotebookWriter.SlotBox(48f, 96f, 500f, 360f)
        val src = """
            size 100 60
            totally_unknown_cmd 1 2 3 "abc"
            line 10 10 80 10
        """.trimIndent()
        val (strokes, _) = MentorDiagram.build(src, slot, Color.BLACK, EstimateMetrics)
        assertEquals(1, strokes.size)
    }

    @Test
    fun circlePointsLieAccuratelyOnCircleWithDenseSampling() {
        val slot = MentorNotebookWriter.SlotBox(48f, 96f, 528f, 384f)
        val src = """
            size 100 60
            circle 50 30 20
        """.trimIndent()
        val (strokes, _) = MentorDiagram.build(src, slot, Color.BLACK, EstimateMetrics)
        assertEquals(1, strokes.size)
        val circle = strokes.single()
        assertTrue("Expected >= 48 points on full circle, got ${circle.size}", circle.size >= 48)
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0 until circle.size) {
            minX = minOf(minX, circle.x(i)); maxX = maxOf(maxX, circle.x(i))
            minY = minOf(minY, circle.y(i)); maxY = maxOf(maxY, circle.y(i))
        }
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val r = (maxX - minX) * 0.5f
        for (i in 0 until circle.size) {
            val d = hypot(circle.x(i) - cx, circle.y(i) - cy)
            assertTrue("Point radius $d deviates from $r by more than 1 px", abs(d - r) < 1.0f)
        }
    }
}
