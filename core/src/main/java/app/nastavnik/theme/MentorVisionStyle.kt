package app.nastavnik.theme

/**
 * Визуализация того, как наставник следит за письмом, читает и анализирует страницу.
 */
enum class MentorVisionStyle(
    val id: String,
    val title: String,
    val subtitle: String,
) {
    AURORA_SCAN(
        id = "AURORA_SCAN",
        title = "Световой луч",
        subtitle = "Плавная волна чтения страницы, подсветка распознанных строк и световой след у кромки за пером.",
    ),
    LINE_FOCUS(
        id = "LINE_FOCUS",
        title = "Фокус строк",
        subtitle = "Чёткие стеклянные контуры вокруг формул и строк с переключением фокуса при анализе.",
    ),
    SUBTLE(
        id = "SUBTLE",
        title = "Деликатный",
        subtitle = "Только тонкий световой импульс на поле листа на высоте пера и мягкое дыхание рамки.",
    ),
    OFF(
        id = "OFF",
        title = "Выкл.",
        subtitle = "Без световых эффектов поверх страницы (остаётся только живой взгляд сферы).",
    );

    companion object {
        fun fromId(raw: String?): MentorVisionStyle {
            val clean = raw?.trim()
            if (clean.isNullOrEmpty()) return AURORA_SCAN
            return entries.firstOrNull { it.id.equals(clean, ignoreCase = true) } ?: AURORA_SCAN
        }
    }
}
