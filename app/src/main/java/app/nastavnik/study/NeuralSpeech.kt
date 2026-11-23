package app.nastavnik.study

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Gemini TTS: стиль задаётся отдельно от дословно читаемого текста. */
internal object NeuralSpeech {
    val voices = listOf("Aoede" to "Аоэда · лёгкий", "Kore" to "Кора · уверенный",
        "Leda" to "Леда · молодой", "Sulafat" to "Сулафат · тёплый")
    private val http = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build()
    fun chunks(text: String): List<String> {
        val result = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.isNotEmpty()) {
            val limit = minOf(2500, remaining.length)
            val boundary = if (limit == remaining.length) limit else
                (remaining.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'), limit - 1)
                    .takeIf { it >= 1000 }?.plus(1)
                    ?: remaining.lastIndexOf(' ', limit - 1).takeIf { it > 0 } ?: limit)
            result += remaining.take(boundary)
            remaining = remaining.drop(boundary).trimStart()
        }
        return result
    }
    fun body(text: String, voice: String): String = buildJsonObject {
        put("model", "gemini-3.8-flash-tts")
        putJsonArray("input") { addJsonObject {
            put("type", "user_input")
            putJsonArray("content") { addJsonObject {
                put("type", "text"); put("text", text)
                putJsonArray("annotations") { addJsonObject {
                    put("type", "speech_metadata")
                    put("style", "Speak Russian as a warm, friendly female tutor. Natural expressive intonation, thoughtful pauses, clear pronunciation, gentle enthusiasm. Vary emphasis with meaning, raise intonation for questions. Never sing or exaggerate.")
                } }
            } }
        } }
        putJsonObject("response_format") { put("type", "audio"); put("mime_type", "audio/wav") }
        putJsonObject("generation_config") { putJsonArray("speech_config") { addJsonObject { put("voice", voice) } } }
    }.toString()

    fun audio(json: String): ByteArray {
        val root = Json.parseToJsonElement(json).jsonObject
        val audio = root["steps"]?.jsonArray.orEmpty()
            .filter { it.jsonObject["type"]?.jsonPrimitive?.content == "model_output" }
            .flatMap { it.jsonObject["content"]?.jsonArray.orEmpty() }
            .lastOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "audio" }
            ?.jsonObject?.get("data")?.jsonPrimitive?.content
            ?: error("Сервис не вернул звук")
        return Base64.getDecoder().decode(audio).also {
            require(it.size > 44 && String(it, 0, 4, Charsets.US_ASCII) == "RIFF") { "Некорректный аудиоответ" }
        }
    }

    suspend fun generate(key: String, text: String, voice: String): ByteArray {
        val request = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/interactions")
            .header("x-goog-api-key", key)
            .post(body(text, voice).toRequestBody("application/json".toMediaType())).build()
        val json = suspendCancellableCoroutine<String> { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val result = runCatching {
                            check(it.isSuccessful) { when (it.code) {
                                401, 403 -> "Проверь ключ Gemini и доступ к озвучке"
                                429 -> "Лимит озвучки Gemini исчерпан"
                                else -> "Сервис озвучки недоступен (HTTP ${it.code})"
                            } }
                            it.body?.string() ?: error("Пустой ответ озвучки")
                        }
                        if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
                    }
                }
            })
        }
        return audio(json)
    }
}
