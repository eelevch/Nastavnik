package app.nastavnik.study

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Бесплатный нейронный синтез речи (Microsoft Edge Neural TTS — ru-RU-SvetlanaNeural / DmitryNeural).
 * Не требует API-ключа, звучит естественно и выразительно, с живой русской интонацией.
 */
internal object EdgeNeuralSpeech {
    private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val CHROMIUM_FULL_VERSION = "130.0.2849.68"
    private const val SEC_MS_GEC_VERSION = "1-$CHROMIUM_FULL_VERSION"
    private const val WIN_EPOCH = 11644473600L

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    private fun generateSecMsGec(): String {
        var ticks = (System.currentTimeMillis() / 1000L) + WIN_EPOCH
        ticks -= ticks % 300L
        val ticks100ns = ticks * 10_000_000L
        val strToHash = "${ticks100ns}$TRUSTED_CLIENT_TOKEN"
        val digest = MessageDigest.getInstance("SHA-256").digest(strToHash.toByteArray(Charsets.US_ASCII))
        return digest.joinToString("") { "%02X".format(it) }
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    suspend fun generate(text: String, female: Boolean = true): ByteArray {
        val voice = if (female) "ru-RU-SvetlanaNeural" else "ru-RU-DmitryNeural"
        val pitch = if (female) "+3Hz" else "-2Hz"
        val rate = "+4%"
        val reqId = UUID.randomUUID().toString().replace("-", "")
        val url = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
            "?TrustedClientToken=$TRUSTED_CLIENT_TOKEN" +
            "&Sec-MS-GEC=${generateSecMsGec()}" +
            "&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION" +
            "&ConnectionId=$reqId"

        val request = Request.Builder()
            .url(url)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0")
            .build()

        return suspendCancellableCoroutine { cont ->
            val audioOut = ByteArrayOutputStream()
            val ws = http.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val configMsg = "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
                    webSocket.send(configMsg)

                    val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='ru-RU'>" +
                        "<voice name='$voice'><prosody pitch='$pitch' rate='$rate'>${escapeXml(text)}</prosody></voice></speak>"
                    val ssmlMsg = "X-RequestId:$reqId\r\n" +
                        "Content-Type:application/ssml+xml\r\n" +
                        "Path:ssml\r\n\r\n$ssml"
                    webSocket.send(ssmlMsg)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("Path:turn.end")) {
                        webSocket.close(1000, "done")
                        val bytes = audioOut.toByteArray()
                        if (cont.isActive) {
                            if (bytes.isNotEmpty()) cont.resume(bytes)
                            else cont.resumeWithException(IllegalStateException("Пустой аудиопоток"))
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val arr = bytes.toByteArray()
                    if (arr.size < 2) return
                    val headerLen = ((arr[0].toInt() and 0xFF) shl 8) or (arr[1].toInt() and 0xFF)
                    val offset = 2 + headerLen
                    if (offset < arr.size) {
                        audioOut.write(arr, offset, arr.size - offset)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (cont.isActive) cont.resumeWithException(t)
                }
            })
            cont.invokeOnCancellation { ws.cancel() }
        }
    }
}
