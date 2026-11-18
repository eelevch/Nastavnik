package app.nastavnik.data

import android.content.Context
import android.os.Build
import app.nastavnik.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Анонимная, бесплатная и безопасная телеметрия установок и дневной активности приложения
 * напрямую в Telegram-канал или чат разработчика через Telegram Bot API.
 *
 * Полная конфиденциальность:
 * НЕ собирает персональные данные (ПДн), имена, телефоны, конспекты, файлы или аккаунты.
 * Передаёт только технические метаданные устройства (модель, версия Android, разрешение экрана)
 * и анонимный случайный UUID установки для подсчёта уникальных пользователей.
 */
object TelegramMetrics {

    private const val PREFS_NAME = "nastavnik_telemetry"
    private const val KEY_INSTALL_ID = "install_id"
    private const val KEY_FIRST_REPORTED = "first_reported"
    private const val KEY_LAST_DAY = "last_day"

    /**
     * Токен бота Telegram и ID чата разработчика по умолчанию.
     * Чтобы подключить своего бота:
     * 1. Создайте бота в @BotFather в Telegram -> скопируйте токен.
     * 2. Создайте канал или группу, добавьте туда бота как администратора.
     * 3. Укажите токен и chat_id здесь или сохраните через SettingsRepo.
     */
    @Volatile
    var defaultBotToken: String = "8489576641:AAE3yqp-OkrKD7Y3kSoeYzFT6qohtteDKD0"

    @Volatile
    var defaultChatId: String = "2086098333"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    /**
     * Получить или сгенерировать постоянный анонимный идентификатор установки.
     */
    fun getInstallId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_INSTALL_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALL_ID, id).apply()
        }
        return id
    }

    /**
     * Фоновая проверка и тихая отправка события в Telegram (в CoroutineScope на Dispatchers.IO):
     * - при первом запуске: событие новой установки с характеристиками устройства;
     * - при последующих запусках: лаконичный пинг активности (не чаще 1 раза в сутки).
     */
    fun reportAppOpen(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val installId = getInstallId(context)

                // Проверяем токен и чат: берем из настроек или из значений по умолчанию
                val settingsToken = SettingsRepo(context).telegramBotToken().trim()
                val settingsChatId = SettingsRepo(context).telegramChatId().trim()
                val token = settingsToken.ifBlank { defaultBotToken.trim() }
                val chatId = settingsChatId.ifBlank { defaultChatId.trim() }

                if (token.isBlank() || chatId.isBlank()) {
                    // Телеметрия не настроена — тихо пропускаем
                    return@launch
                }

                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                val firstReported = prefs.getBoolean(KEY_FIRST_REPORTED, false)
                val lastDay = prefs.getString(KEY_LAST_DAY, "")

                val deviceName = escapeHtml("${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim())
                val osInfo = escapeHtml("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                val isTablet = DeviceProfile.isTablet(context)
                val role = if (isTablet) "Планшет" else "Телефон"
                val dm = context.resources.displayMetrics
                val screenInfo = "${dm.widthPixels}×${dm.heightPixels}, ${dm.densityDpi} dpi"
                val appVersion = escapeHtml("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                val safeInstallId = escapeHtml(installId)

                if (!firstReported) {
                    val message = buildString {
                        appendLine("🚀 <b>Новая установка #Наставник</b>")
                        appendLine("📱 <b>Устройство:</b> $deviceName ($osInfo)")
                        appendLine("💻 <b>Тип:</b> $role ($screenInfo)")
                        appendLine("🏷 <b>Версия:</b> $appVersion")
                        appendLine("🆔 <b>ID установки:</b> <code>$safeInstallId</code>")
                        appendLine("⏰ <b>Время:</b> $timeStr")
                    }
                    if (sendTelegramMessage(token, chatId, message)) {
                        prefs.edit()
                            .putBoolean(KEY_FIRST_REPORTED, true)
                            .putString(KEY_LAST_DAY, today)
                            .apply()
                    }
                } else if (lastDay != today) {
                    val message = buildString {
                        appendLine("⚡ <b>Активность #Наставник</b>")
                        appendLine("📱 <b>Устройство:</b> $deviceName ($role)")
                        appendLine("🏷 <b>Версия:</b> $appVersion")
                        appendLine("🆔 <b>ID установки:</b> <code>$safeInstallId</code>")
                        appendLine("⏰ <b>Время:</b> $timeStr")
                    }
                    if (sendTelegramMessage(token, chatId, message)) {
                        prefs.edit()
                            .putString(KEY_LAST_DAY, today)
                            .apply()
                    }
                }
            }
        }
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    /**
     * Прямая отправка сообщения через официальный Telegram Bot API.
     */
    private fun sendTelegramMessage(token: String, chatId: String, text: String): Boolean {
        return runCatching {
            val url = "https://api.telegram.org/bot$token/sendMessage"
            val form = FormBody.Builder()
                .add("chat_id", chatId)
                .add("text", text)
                .add("parse_mode", "HTML")
                .add("disable_web_page_preview", "true")
                .build()

            val request = Request.Builder()
                .url(url)
                .post(form)
                .build()

            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }.getOrDefault(false)
    }
}
