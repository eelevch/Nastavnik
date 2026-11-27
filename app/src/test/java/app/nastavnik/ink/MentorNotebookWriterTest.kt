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
        assertEquals(1, strokes.size)
        assertEquals(1, texts.size)
        assertTrue(texts[0].text.contains("α/2 + x² = 5"))
        assertTrue(slot.top >= 180f)

        val shapeAnchor = MentorNotebookWriter.SlotBox(100f, 300f, 420f, 520f)
        val (axesStrokes, axesRect) = MentorNotebookWriter.buildShapeAdditionBox(
            page = page,
            kind = DrawingKind.AXES,
            anchor = shapeAnchor,
            inkColor = Color.BLACK,
        )
        assertEquals(4, axesStrokes.size)
        assertFalse(axesRect.isEmpty)
    }
}
