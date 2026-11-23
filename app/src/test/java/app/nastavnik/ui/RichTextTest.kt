package app.nastavnik.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextTest {

    private val mathColor = Color(0xFF1976D2)

    @Test
    fun rendersBoldItalicStrikeCodeAndLinksWithoutRawDelimiters() {
        val out = formatRichMathText(
            "Проверь **жирный**, __второй__, *курсив*, _особый_, ***вместе***, ~~старое~~, `x + 1` и [формулу](https://example.com).",
            mathColor = mathColor,
        )
        assertEquals(
            "Проверь жирный, второй, курсив, особый, вместе, старое, x + 1 и формулу.",
            out.text,
        )
        assertTrue(out.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(out.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(out.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough })
        assertTrue(out.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
        assertTrue(out.spanStyles.any { it.item.textDecoration == TextDecoration.Underline && it.item.color == mathColor })
    }

    @Test
    fun bulletListWithBoldDoesNotLeakAsterisks() {
        val out = formatRichMathText(
            "* **Шаг 1:** проверь \$x^2\$\n- **Шаг 2:** сравни с *нулем*",
            mathColor = mathColor,
        )
        assertEquals("• Шаг 1: проверь x²\n• Шаг 2: сравни с нулем", out.text)
        assertFalse(out.text.contains("*"))
        assertFalse(out.text.contains("$"))
        assertTrue(out.spanStyles.any { it.item.fontWeight == FontWeight.Bold && out.text.substring(it.start, it.end) == "Шаг 1:" })
        assertTrue(out.spanStyles.any { it.item.color == mathColor && out.text.substring(it.start, it.end) == "x²" })
    }

    @Test
    fun nestedFormattingAndMathInsideBoldWorkCleanly() {
        val out = formatRichMathText(
            "**Проверь *знак* в \$x^2 + a_{1}\$** и **итог *тут***",
            mathColor = mathColor,
        )
        assertFalse(out.text.contains("*"))
        assertFalse(out.text.contains("$"))
        assertEquals("Проверь знак в x² + a₁ и итог тут", out.text)
        assertTrue(out.spanStyles.any { it.item.fontStyle == FontStyle.Italic && out.text.substring(it.start, it.end) == "знак" })
        assertTrue(out.spanStyles.any { it.item.fontStyle == FontStyle.Italic && out.text.substring(it.start, it.end) == "тут" })
        assertTrue(out.spanStyles.any { it.item.color == mathColor })
    }

    @Test
    fun preservesMultiplicationAndUnderscoreIdentifiers() {
        val out = formatRichMathText("Вычисли 2 * 3 + 4 * 5 для x_1 и my_var_name.", mathColor)
        assertEquals("Вычисли 2 * 3 + 4 * 5 для x_1 и my_var_name.", out.text)
        assertFalse(out.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun headingsBlockquotesAndDisplayLatexCleanlyRendered() {
        val out = formatRichMathText(
            "### Вывод\n> Важно учесть **знак**\n\\[ \\frac{a + b}{c} \\]",
            mathColor = mathColor,
        )
        assertFalse(out.text.contains("#"))
        assertFalse(out.text.contains("\\["))
        assertFalse(out.text.contains("\\frac"))
        assertTrue(out.text.contains("Вывод"))
        assertTrue(out.text.contains("▎ Важно учесть знак"))
        assertTrue(out.text.contains("((a + b) / c)") || out.text.contains("(a + b / c)"))
    }

    @Test
    fun streamingUnclosedMarkersNeverFlashRawSymbols() {
        val unclosedBold = formatRichMathText("Проверь **знак", mathColor = mathColor, streaming = true)
        assertEquals("Проверь знак", unclosedBold.text)
        assertTrue(unclosedBold.spanStyles.any { it.item.fontWeight == FontWeight.Bold && unclosedBold.text.substring(it.start, it.end) == "знак" })

        val danglingTail = formatRichMathText("Проверь **", mathColor = mathColor, streaming = true)
        assertEquals("Проверь ", danglingTail.text)

        val unclosedBoldAtRest = formatRichMathText("Обрати внимание на **коэффициент", mathColor = mathColor, streaming = false)
        assertEquals("Обрати внимание на коэффициент", unclosedBoldAtRest.text)
        assertTrue(unclosedBoldAtRest.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun cleanMathHandlesAdvancedLatexConstructs() {
        val expr = cleanMath("""\left(\frac{a+b}{2}\right) \implies x^{2n} \in \mathbb{R}, \sin(\alpha)""")
        assertEquals("((a+b / 2)) ⇒ x²ⁿ ∈ ℝ, sin(α)", expr)
        assertEquals("∫ √(x + 1)", cleanMath("""\int \sqrt{x + 1}"""))
    }
}
