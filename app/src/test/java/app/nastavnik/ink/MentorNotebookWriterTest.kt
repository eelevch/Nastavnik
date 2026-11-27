package app.nastavnik.ink

import android.graphics.Color
import app.nastavnik.ai.DrawingKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MentorNotebookWriterTest {
    @Test
    fun cleanMathForSheetConvertsLatexToUnicode() {
        val raw = "${'$'}${'$'}\\frac{x^2 + 1}{2} \\Rightarrow \\sqrt{a_1} \\le \\infty${'$'}${'$'}"
        val cleaned = MentorNotebookWriter.cleanMathForSheet(raw)
        assertEquals("(x² + 1)/2 ⇒ √(a₁) ≤ ∞", cleaned)
    }

    @Test
    fun findWriteSlotAvoidsExistingStrokesAndTexts() {
        val page = InkPage(1L, Paper.GRID)
        page.texts += TextBox(70f, 100f, 600f, "Существующая строка", 24f, Color.BLACK)
        page.strokes += Stroke(Tool.PEN, Color.BLUE, 3f, floatArrayOf(70f, 220f, 0.5f, 400f, 250f, 0.5f))

        val slot = MentorNotebookWriter.computeWriteSlotBox(page, null, lineCountEstimate = 2, fontSize = 22f)
        assertTrue("Slot top (${slot.top}) should be below existing strokes bottom (253)", slot.top >= 253f)
        assertFalse(slot.isEmpty)
    }

    @Test
    fun buildWriteAndShapeAdditionsProduceValidPageObjects() {
        val page = InkPage(2L, Paper.GRID)
        val anchor = MentorNotebookWriter.SlotBox(80f, 140f, 450f, 180f)
        val (strokes, texts, slot) = MentorNotebookWriter.buildWriteAdditionBoxes(
            page = page,
            text = "\\frac{\\alpha}{2} + x^2 = 5",
            anchor = anchor,
            inkColor = Color.BLUE,
        )
        assertTrue("Expected straight margin bar + fraction bar strokes, got ${strokes.size}", strokes.size >= 2)
        val marginBar = strokes.first()
        assertEquals(Tool.SHAPE, marginBar.tool)
        val barX = marginBar.x(0)
        for (i in 0 until marginBar.size) {
            assertEquals("Margin bar must be vertical with equal X", barX, marginBar.x(i), 0.01f)
        }
        assertTrue("2D math layout should produce α, 2, x, 5 tokens", texts.any { it.text.contains("α") } && texts.any { it.text.contains("5") })
        assertTrue(slot.top >= 180f)

        val triangleAnchor = MentorNotebookWriter.SlotBox(37f, 200f, 74f, 610f)
        val (triStrokes, triRect) = MentorNotebookWriter.buildShapeAdditionBox(
            page = page,
            kind = DrawingKind.TRIANGLE,
            anchor = triangleAnchor,
            inkColor = Color.BLACK,
        )
        assertEquals(1, triStrokes.size)
        assertEquals(300f, triRect.width, 0.5f)
        assertEquals(260f, triRect.height, 0.5f)
        assertEquals(0f, triRect.left % PageDrawer.CELL, 0.1f)
        assertEquals(0f, triRect.top % PageDrawer.CELL, 0.1f)

        val circlePage = InkPage(3L, Paper.GRID)
        circlePage.strokes += Stroke(Tool.PEN, Color.BLUE, 3f, floatArrayOf(120f, 200f, 0.5f, 280f, 240f, 0.5f))
        val targetAnchor = MentorNotebookWriter.SlotBox(140f, 210f, 260f, 230f)
        val (_, wrappedRect) = MentorNotebookWriter.buildShapeAdditionBox(
            page = circlePage,
            kind = DrawingKind.ELLIPSE,
            anchor = targetAnchor,
            inkColor = Color.RED,
        )
        assertTrue("Wrapped ellipse left (${wrappedRect.left}) should pad around stroke left 120", wrappedRect.left <= 112f)
        assertTrue("Wrapped ellipse right (${wrappedRect.right}) should pad around stroke right 280", wrappedRect.right >= 288f)
        assertTrue("Wrapped ellipse top (${wrappedRect.top}) should pad around stroke top 200", wrappedRect.top <= 192f)
        assertTrue("Wrapped ellipse bottom (${wrappedRect.bottom}) should pad around stroke bottom 240", wrappedRect.bottom >= 248f)
    }
}
