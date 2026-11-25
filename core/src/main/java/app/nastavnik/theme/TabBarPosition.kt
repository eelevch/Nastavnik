package app.nastavnik.theme

/**
 * Расположение и стиль панели вкладок в приложении.
 */
enum class TabBarPosition(val id: String, val label: String) {
    BOTTOM("BOTTOM", "Снизу"),
    BOTTOM_CAPSULE("BOTTOM_CAPSULE", "Капсула снизу"),
    LEFT("LEFT", "Сбоку слева"),
    RIGHT("RIGHT", "Сбоку справа"),
    BOTH("BOTH", "С двух сторон");

    /** Истинно для боковой вылазки с одного или обоих краёв экрана. */
    fun isSide(): Boolean = this == LEFT || this == RIGHT || this == BOTH

    /** Активен ли левый край экрана. */
    fun hasLeftEdge(): Boolean = this == LEFT || this == BOTH

    /** Активен ли правый край экрана. */
    fun hasRightEdge(): Boolean = this == RIGHT || this == BOTH

    companion object {
        val TABLET_OPTIONS = listOf(LEFT, RIGHT, BOTH)
        val PHONE_OPTIONS = listOf(BOTTOM, BOTTOM_CAPSULE, LEFT, RIGHT, BOTH)

        /** Безопасный разбор настройки планшета: допустимы LEFT, RIGHT или BOTH. */
        fun forTablet(raw: String?): TabBarPosition = when {
            raw.equals(RIGHT.id, ignoreCase = true) -> RIGHT
            raw.equals(BOTH.id, ignoreCase = true) -> BOTH
            else -> LEFT
        }

        /** Безопасный разбор настройки телефона: по умолчанию BOTTOM. */
        fun forPhone(raw: String?): TabBarPosition =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: BOTTOM
    }
}
