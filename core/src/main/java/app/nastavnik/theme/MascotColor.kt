package app.nastavnik.theme

/**
 * Предустановленные объёмные 3D-палитры маскота-наставника.
 */
enum class MascotColor(
    val id: String,
    val title: String,
    val coreArgb: Long,
    val midArgb: Long,
    val rimArgb: Long,
    val haloArgb: Long,
    val sparkArgb: Long,
) {
    SUNSET(
        id = "SUNSET",
        title = "Персик",
        coreArgb = 0xFFE93882,
        midArgb = 0xFFFF6D84,
        rimArgb = 0xFFFFBE76,
        haloArgb = 0xFFFF6D84,
        sparkArgb = 0xFFFFE6B8,
    ),
    VIOLET(
        id = "VIOLET",
        title = "Индиго",
        coreArgb = 0xFF4B21B3,
        midArgb = 0xFF7B5CFF,
        rimArgb = 0xFFB8A2FF,
        haloArgb = 0xFF7B5CFF,
        sparkArgb = 0xFFE4DCFF,
    ),
    EMERALD(
        id = "EMERALD",
        title = "Изумруд",
        coreArgb = 0xFF0A7558,
        midArgb = 0xFF22B88A,
        rimArgb = 0xFF88F0CB,
        haloArgb = 0xFF22B88A,
        sparkArgb = 0xFFD4FCEF,
    ),
    OCEAN(
        id = "OCEAN",
        title = "Океан",
        coreArgb = 0xFF1A4BD6,
        midArgb = 0xFF3B82F6,
        rimArgb = 0xFF9BC9FF,
        haloArgb = 0xFF3B82F6,
        sparkArgb = 0xFFDBEEFF,
    ),
    ORCHID(
        id = "ORCHID",
        title = "Орхидея",
        coreArgb = 0xFFD92378,
        midArgb = 0xFFF471B5,
        rimArgb = 0xFFFFD4EA,
        haloArgb = 0xFFF471B5,
        sparkArgb = 0xFFFFEDF7,
    ),
    AMBER(
        id = "AMBER",
        title = "Янтарь",
        coreArgb = 0xFFD94E1F,
        midArgb = 0xFFF9803A,
        rimArgb = 0xFFFEE08B,
        haloArgb = 0xFFF9803A,
        sparkArgb = 0xFFFFF4CF,
    ),
    OBSIDIAN(
        id = "OBSIDIAN",
        title = "Обсидиан",
        coreArgb = 0xFF0D0F14,
        midArgb = 0xFF1C202B,
        rimArgb = 0xFF384052,
        haloArgb = 0xFF64748B,
        sparkArgb = 0xFF94A3B8,
    ),
    THEME(
        id = "THEME",
        title = "В цвет темы",
        coreArgb = 0xFF1D4ED8,
        midArgb = 0xFF3B82F6,
        rimArgb = 0xFF93C5FD,
        haloArgb = 0xFF3B82F6,
        sparkArgb = 0xFFDBEAFE,
    );

    data class MascotStops(
        val coreArgb: Int,
        val midArgb: Int,
        val rimArgb: Int,
        val haloArgb: Int,
        val sparkArgb: Int,
    )

    fun resolveStops(themeAccentArgb: Int): MascotStops {
        if (this != THEME) {
            return MascotStops(
                coreArgb = coreArgb.toInt(),
                midArgb = midArgb.toInt(),
                rimArgb = rimArgb.toInt(),
                haloArgb = haloArgb.toInt(),
                sparkArgb = sparkArgb.toInt(),
            )
        }
        val mid = themeAccentArgb or 0xFF000000.toInt()
        val core = blendOpaque(mid, 0xFF0A0C18.toInt(), 0.36f)
        val rim = blendOpaque(mid, 0xFFFFF8F2.toInt(), 0.54f)
        val spark = blendOpaque(mid, 0xFFFFFFFF.toInt(), 0.75f)
        return MascotStops(
            coreArgb = core,
            midArgb = mid,
            rimArgb = rim,
            haloArgb = mid,
            sparkArgb = spark,
        )
    }

    companion object {
        fun fromId(raw: String?): MascotColor {
            val clean = raw?.trim()
            if (clean.isNullOrEmpty()) return SUNSET
            return entries.firstOrNull { it.id.equals(clean, ignoreCase = true) } ?: SUNSET
        }

        private fun blendOpaque(fromArgb: Int, toArgb: Int, fraction: Float): Int {
            val t = fraction.coerceIn(0f, 1f)
            val r0 = (fromArgb ushr 16) and 0xFF
            val g0 = (fromArgb ushr 8) and 0xFF
            val b0 = fromArgb and 0xFF
            val r1 = (toArgb ushr 16) and 0xFF
            val g1 = (toArgb ushr 8) and 0xFF
            val b1 = toArgb and 0xFF
            val r = (r0 + ((r1 - r0) * t)).toInt().coerceIn(0, 255)
            val g = (g0 + ((g1 - g0) * t)).toInt().coerceIn(0, 255)
            val b = (b0 + ((b1 - b0) * t)).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
