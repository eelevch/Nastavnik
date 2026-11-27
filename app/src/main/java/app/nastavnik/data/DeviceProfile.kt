package app.nastavnik.data

import android.app.ActivityManager
import android.content.Context
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Профиль устройства: оптимизация ресурсов для слабых телефонов/планшетов
 * и подбор оптимального разрешения экрана для планшетов.
 */
object DeviceProfile {

    @Volatile
    private var cachedIsWeak: Boolean? = null

    /**
     * Слабое устройство: мало оперативной памяти (isLowRamDevice или <= 3.5 ГБ),
     * старый/бюджетный процессор (<= 4 ядер).
     * Результат кэшируется, чтобы исключить повторные IPC binder-вызовы к ActivityManagerService во время отрисовки.
     */
    fun isWeakDevice(context: Context): Boolean {
        cachedIsWeak?.let { return it }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am?.isLowRamDevice == true) {
            cachedIsWeak = true
            return true
        }
        val mem = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mem)
        val totalGb = mem.totalMem / (1024.0 * 1024.0 * 1024.0)
        val weak = (totalGb > 0 && totalGb <= 3.5) || Runtime.getRuntime().availableProcessors() <= 4
        cachedIsWeak = weak
        return weak
    }

    /**
     * Планшет ли это (по наименьшей ширине экрана >= 600 dp).
     */
    fun isTablet(context: Context): Boolean {
        return context.resources.configuration.smallestScreenWidthDp >= 600
    }

    /**
     * Запуск внутри Android-эмулятора (защита от проблем с RGB_565/gfxstream на хосте).
     */
    fun isEmulator(): Boolean {
        val hw = android.os.Build.HARDWARE.lowercase()
        val fp = android.os.Build.FINGERPRINT.lowercase()
        val prod = android.os.Build.PRODUCT.lowercase()
        return "ranchu" in hw || "goldfish" in hw || fp.startsWith("generic") || "sdk_gphone" in prod || "emulator" in prod
    }

    data class Resolution(
        val width: Int,
        val height: Int,
        val dpi: Int,
        val scale: Float,
    )

    /**
     * Оптимальное разрешение для планшета:
     * На экранах высокого разрешения (2.5K / 3K: 2560x1600, 2880x1800 и т.д.)
     * снижает нагрузку на видеопамять в 3-4 раза, сохраняя максимальную четкость
     * формул и рукописного текста для нейросети (предел 1500 px / 1.15 Мп).
     */
    fun optimalTabletResolution(
        realW: Int,
        realH: Int,
        realDpi: Int,
        isWeak: Boolean = false,
    ): Resolution {
        val maxSide = max(realW, realH)
        // Для планшета целевая длинная сторона: 1440-1500 px (или 1200 px на слабых устройствах).
        val targetMax = if (isWeak) 1200 else 1500
        if (maxSide <= targetMax) {
            return Resolution(realW, realH, realDpi, 1.0f)
        }
        val scale = targetMax.toFloat() / maxSide.toFloat()
        // Делаем чётными (требование энкодеров и ImageReader)
        val optW = ((realW * scale).roundToInt() / 2) * 2
        val optH = ((realH * scale).roundToInt() / 2) * 2
        val optDpi = (realDpi * scale).roundToInt().coerceAtLeast(120)
        return Resolution(optW, optH, optDpi, scale)
    }

    /**
     * Оптимальный лимит памяти для кэша подложек страниц тетрадей (в байтах).
     */
    fun maxBackgroundCacheBytes(): Int {
        val maxHeap = Runtime.getRuntime().maxMemory()
        // 1/8 кучи, но не менее 16 МБ и не более 48 МБ (на слабых устройствах не тратит лишнюю память).
        return (maxHeap / 8).coerceIn(16L * 1024 * 1024, 48L * 1024 * 1024).toInt()
    }

    /**
     * Оптимальный размер декодирования подложек тетрадей (maxSide).
     */
    fun optimalImageSide(isWeak: Boolean): Int {
        return if (isWeak) 1400 else 1800
    }
}
