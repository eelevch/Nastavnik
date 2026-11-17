package app.nastavnik.study

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Читает подсказки вслух (системный синтез речи, русский язык). Формулы — словами, без разметки. */
class Speaker(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking = _isSpeaking.asStateFlow()

    private val _wordBeat = MutableStateFlow(0L)
    val wordBeat = _wordBeat.asStateFlow()

    /** Прочитать текст подсказки (Markdown + LaTeX). Новое чтение прерывает предыдущее. */
    fun speak(markdown: String) {
        val text = SpeakText.from(markdown)
        if (text.isBlank()) return
        val t = tts
        if (t == null) {
            pending = text
            initTts()
            return
        }
        if (ready) say(text) else pending = text
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                setupBestVoice()
                setupProgressListener()
                pending?.let { say(it) }
            }
            pending = null
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                _wordBeat.value = System.currentTimeMillis()
            }
        })
    }

    private fun setupBestVoice() {
        val t = tts ?: return
        val ruLocale = Locale.forLanguageTag("ru-RU")
        t.language = ruLocale

        runCatching {
            val voices = t.voices ?: return@runCatching
            val ruVoices = voices.filter { v ->
                val loc = v.locale
                loc.language.equals("ru", ignoreCase = true) || loc.toLanguageTag().startsWith("ru", ignoreCase = true)
            }
            if (ruVoices.isNotEmpty()) {
                // Ищем наиболее качественный и естественный русский голос (Google Neural / High Quality)
                val bestVoice = ruVoices.maxWithOrNull(
                    compareBy<Voice> { v ->
                        var score = 0
                        if (v.quality == Voice.QUALITY_VERY_HIGH) score += 300
                        else if (v.quality == Voice.QUALITY_HIGH) score += 200
                        else if (v.quality == Voice.QUALITY_NORMAL) score += 100

                        val name = v.name.lowercase()
                        if (name.contains("neural") || name.contains("wavenet")) score += 300
                        if (name.contains("network")) score += 180
                        if (name.contains("ru-ru-x-") || name.contains("ru-ru-")) score += 120
                        if (v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true) score -= 600
                        score
                    }
                )
                if (bestVoice != null && bestVoice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true) {
                    t.voice = bestVoice
                }
            }
        }

        // Приятная интонация и естественный темп для наставника
        t.setPitch(1.02f)
        t.setSpeechRate(1.04f)
    }

    private fun say(text: String) {
        _isSpeaking.value = true
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hint_${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun destroy() {
        tts?.shutdown()
        tts = null
        ready = false
        _isSpeaking.value = false
    }
}
