package app.nastavnik.ink

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MathTypesetterTest {
    @Test
    fun fracPlacesNumeratorAboveBarAndDenominatorBelow() {
        val layout = MathTypesetter.layoutFormula("\\frac{a+b}{c}", 24f, Color.BLACK, EstimateMetrics)
        assertTrue("Expected fraction bar stroke", layout.strokes.isNotEmpty())
        val bar = layout.strokes.first()
        assertEquals(Tool.SHAPE, bar.tool)
        val barY = bar.points[1]
        val numTexts = layout.texts.filter { it.text != "c" }
        val denTexts = layout.texts.filter { it.text == "c" }
        assertTrue(numTexts.isNotEmpty())
        assertEquals(1, denTexts.size)
        val numCenterY = numTexts.first().y + EstimateMetrics.baselineOffset(numTexts.first().size) * 0.5f
        val denCenterY = denTexts.first().y + EstimateMetrics.baselineOffset(denTexts.first().size) * 0.5f
        assertTrue("Numerator should be above fraction bar ($numCenterY < $barY)", numCenterY < barY)
        assertTrue("Denominator should be below fraction bar ($denCenterY > $barY)", denCenterY > barY)
    }

    @Test
    fun superscriptUsesSmallerRaisedFont() {
        val layout = MathTypesetter.layoutFormula("x^{2}", 24f, Color.BLACK, EstimateMetrics)
        assertEquals(2, layout.texts.size)
        val base = layout.texts[0]
        val sup = layout.texts[1]
        assertEquals("x", base.text)
        assertEquals("2", sup.text)
        assertTrue("Superscript size (${sup.size}) must be < base size (${base.size})", sup.size < base.size)
        assertTrue("Superscript top (${sup.y}) must be higher than base (${base.y})", sup.y < base.y)
    }

    @Test
    fun sqrtDrawsRadicalStroke() {
        val layout = MathTypesetter.layoutFormula("\\sqrt{x+1}", 24f, Color.BLACK, EstimateMetrics)
        assertTrue("Expected radical stroke", layout.strokes.isNotEmpty())
        assertTrue("Expected radical polyline with >= 4 vertices", layout.strokes.first().size >= 4)
        assertTrue(layout.texts.any { it.text.contains("x") })
        assertTrue(layout.texts.any { it.text.contains("1") })
    }

    @Test
    fun sumPlacesLimitsAboveAndBelow() {
        val layout = MathTypesetter.layoutFormula("\\sum_{i=1}^{n} i", 24f, Color.BLACK, EstimateMetrics)
        val sumBox = layout.texts.first { it.text == "∑" }
        val upper = layout.texts.first { it.text == "n" }
        val lowerLeft = layout.texts.first { it.text == "i" && it.y > sumBox.y }
        assertTrue("Upper limit n (${upper.y}) should be above ∑ (${sumBox.y})", upper.y < sumBox.y)
        assertTrue("Lower limit (${lowerLeft.y}) should be below ∑ (${sumBox.y})", lowerLeft.y > sumBox.y)
    }

    @Test
    fun casesDrawsCurlyBraceAndTwoRows() {
        val layout = MathTypesetter.layoutFormula("\\begin{cases} x+y=1 \\\\ x-y=0 \\end{cases}", 24f, Color.BLACK, EstimateMetrics)
        assertTrue("Expected curly brace stroke", layout.strokes.isNotEmpty())
        val ys = layout.texts.map { it.y }.distinct().sorted()
        assertTrue("Expected at least two distinct rows, got $ys", ys.size >= 2)
        assertTrue("Second row must be strictly below first row", ys.last() - ys.first() >= 18f)
    }

    @Test
    fun malformedAndStreamingInputsDoNotCrash() {
        val cases = listOf("\\frac{1}{", "x^{", "}}", "\\sqrt{", "\\begin{cases} a=1")
        for (raw in cases) {
            val l = MathTypesetter.layoutFormula(raw, 22f, Color.BLACK, EstimateMetrics)
            assertTrue(l.width >= 0f)
        }
    }

    @Test
    fun richLinesWrapAndSnapBaselinesToGridStep() {
        val lineStep = PageDrawer.CELL
        val maxW = 240f
        val text = "Подставим найденное значение производной и упростим длинное выражение"
        val layout = MathTypesetter.layoutRichLines(text, maxW, 22f, Color.BLACK, EstimateMetrics, lineStep)
        assertTrue(layout.texts.size > 1)
        val baselines = layout.texts.map { it.y + EstimateMetrics.baselineOffset(it.size) }.distinct()
        assertTrue("Long text should wrap onto multiple lines", baselines.size >= 2)
        for (b in baselines) {
            val rem = b % lineStep
            assertTrue("Baseline $b must be a multiple of $lineStep (rem=$rem)", rem < 0.05f || abs(rem - lineStep) < 0.05f)
        }
        assertTrue("Total layout width (${layout.width}) should fit maxWidth ($maxW)", layout.width <= maxW + 1f)
    }
}
