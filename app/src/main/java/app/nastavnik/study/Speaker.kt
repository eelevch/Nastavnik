package app.nastavnik.study

import android.content.Context
import android.media.MediaPlayer
import android.media.AudioAttributes
import app.nastavnik.NastavnikApp
import app.nastavnik.ai.AiProvider
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Нейронная русская озвучка с выбором голоса; системный синтез доступен отдельно. */
class Speaker(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var playbackGeneration = 0L
    private var neuralJob: Job? = null
    private var player: MediaPlayer? = null
    private var audioFile: File? = null
    private val _problem = MutableStateFlow<String?>(null)
    val problem = _problem.asStateFlow()
    fun neuralEnabled() = prefs.getBoolean("neural", true)
    fun setNeural(enabled: Boolean) { stop(); prefs.edit().putBoolean("neural", enabled).apply() }
    fun neuralVoice() = prefs.getString("neural_voice", "Aoede") ?: "Aoede"
    fun setNeuralVoice(name: String) { stop(); prefs.edit().putString("neural_voice", name).apply() }
    val neuralVoices get() = NeuralSpeech.voices
    private var tts: TextToSpeech? = null
    private val prefs = context.applicationContext.getSharedPreferences("speaker", Context.MODE_PRIVATE)
    private val _voices = MutableStateFlow<List<Voice>>(emptyList())
    val voices = _voices.asStateFlow()
    fun prepare() { if (tts == null) initTts() }
    fun selectVoice(name: String) {
        prefs.edit().putString("voice", name).apply()
        setupBestVoice()
    }
    fun selectedVoice(): String = prefs.getString("voice", "").orEmpty()
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
        stop()
        _problem.value = null
        if (neuralEnabled()) {
            val key = (context.applicationContext as NastavnikApp).settings.apiKey(AiProvider.GEMINI)
            if (key.isBlank()) {
                _problem.value = "Для нейронного голоса добавь ключ Gemini в настройках голоса или выбери системную озвучку."
                android.widget.Toast.makeText(context, _problem.value, android.widget.Toast.LENGTH_LONG).show()
                return
            }
            _isSpeaking.value = true
            val generation = playbackGeneration
            neuralJob = scope.launch {
                try {
                    // Последовательные фрагменты позволяют читать длинные ответы целиком.
                    for (chunk in NeuralSpeech.chunks(text)) {
                        val bytes = withContext(Dispatchers.IO) { NeuralSpeech.generate(key, chunk, neuralVoice()) }
                        ensureActive()
                        val file = File.createTempFile("mentor-voice-", ".wav", context.cacheDir)
                        audioFile = file
                        withContext(Dispatchers.IO) { file.writeBytes(bytes) }
                        ensureActive()
                        suspendCancellableCoroutine<Unit> { continuation ->
                            val media = MediaPlayer()
                            player = media
                            media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                            media.setOnCompletionListener { if (continuation.isActive) continuation.resume(Unit) }
                            media.setOnErrorListener { _, _, _ ->
                                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Не удалось воспроизвести голос"))
                                true
                            }
                            media.setDataSource(file.absolutePath)
                            media.setOnPreparedListener { it.start() }
                            media.prepareAsync()
                        }
                        releaseAudio()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _problem.value = e.message ?: "Не удалось озвучить ответ"
                    android.widget.Toast.makeText(context, _problem.value, android.widget.Toast.LENGTH_LONG).show()
                } finally {
                    if (generation == playbackGeneration) {
                        releaseAudio()
                        _isSpeaking.value = false
                    }
                }
            }
            return
        }
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
            _voices.value = ruVoices.filter {
                it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
            }.sortedByDescending { it.quality }
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
                    t.voice = _voices.value.firstOrNull { it.name == selectedVoice() } ?: bestVoice
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

    private fun releaseAudio() {
        player?.release()
        player = null
        audioFile?.delete()
        audioFile = null
    }

    fun stop() {
        playbackGeneration++
        neuralJob?.cancel()
        neuralJob = null
        releaseAudio()
        pending = null
        tts?.stop()
        _isSpeaking.value = false
    }

    fun destroy() {
        stop()
        scope.cancel()
        tts?.shutdown()
        tts = null
        ready = false
        _isSpeaking.value = false
    }
}
