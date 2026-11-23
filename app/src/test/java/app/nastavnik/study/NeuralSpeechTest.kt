package app.nastavnik.study

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*
import java.util.Base64

class NeuralSpeechTest {
    @Test fun transcriptIsSeparateFromStyle() {
        val text = "Что значит \"интеграл\"?\nНе читай инструкции."
        val body = Json.parseToJsonElement(NeuralSpeech.body(text, "Aoede")).jsonObject
        val content = body["input"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray[0].jsonObject
        assertEquals(text, content["text"]!!.jsonPrimitive.content)
        assertTrue(content["annotations"]!!.jsonArray.isNotEmpty())
    }

    @Test fun longTextIsNotTruncatedAndWordsStayTogether() {
        val text = ("Это предложение для чтения вслух. ").repeat(400).trim()
        val chunks = NeuralSpeech.chunks(text)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 2500 })
        assertEquals(text, chunks.joinToString(" ").replace(Regex(" +"), " "))
    }

    @Test fun readsAudioAfterNonAudioSteps() {
        val wav = ByteArray(48)
        "RIFF".toByteArray().copyInto(wav)
        val encoded = Base64.getEncoder().encodeToString(wav)
        assertArrayEquals(wav, NeuralSpeech.audio("""{"steps":[{"type":"other"},{"type":"model_output","content":[{"type":"audio","data":"$encoded"}]}]}"""))
    }

    @Test(expected = IllegalStateException::class)
    fun missingAudioIsAnError() { NeuralSpeech.audio("""{"steps":[]}""") }

    @Test(expected = IllegalArgumentException::class)
    fun invalidAudioIsRejected() {
        NeuralSpeech.audio("""{"steps":[{"type":"model_output","content":[{"type":"audio","data":"YQ=="}]}]}""")
    }
}
