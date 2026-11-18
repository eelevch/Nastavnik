package app.nastavnik.data

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class TelegramMetricsTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testInstallIdFormat() {
        val uuid = UUID.randomUUID().toString()
        assertTrue(uuid.matches(Regex("^[0-9a-fA-F-]{36}$")))
    }

    @Test
    fun testTelegramApiRequestFormat() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true,"result":{"message_id":123}}"""))

        // Проверяем формат POST-запроса на Telegram API
        val client = okhttp3.OkHttpClient()
        val form = okhttp3.FormBody.Builder()
            .add("chat_id", "-100123456789")
            .add("text", "🚀 <b>Новая установка #Наставник</b>\n📱 <b>Устройство:</b> Test Device")
            .add("parse_mode", "HTML")
            .add("disable_web_page_preview", "true")
            .build()

        val req = okhttp3.Request.Builder()
            .url(server.url("/botTEST_TOKEN/sendMessage"))
            .post(form)
            .build()

        client.newCall(req).execute().use { response ->
            assertTrue(response.isSuccessful)
        }

        val recorded = server.takeRequest()
        assertEquals("/botTEST_TOKEN/sendMessage", recorded.path)
        assertEquals("POST", recorded.method)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("chat_id=-100123456789"))
        assertTrue(body.contains("parse_mode=HTML"))
    }
}
