package app.nastavnik.theme

/**
 * Расположение и стиль панели вкладок в приложении.
 */
enum class TabBarPosition(val id: String, val label: String) {
    BOTTOM("BOTTOM", "Снизу"),
    BOTTOM_CAPSULE("BOTTOM_CAPSULE", "Капсула снизу"),
    LEFT("LEFT", "Сбоку слева"),
    RIGHT("RIGHT", "Сбоку справа");

    /** Истинно для вертикальной боковой капсульной панели. */
    fun isSide(): Boolean = this == LEFT || this == RIGHT

    companion object {
        val TABLET_OPTIONS = listOf(LEFT, RIGHT)
        val PHONE_OPTIONS = listOf(BOTTOM, BOTTOM_CAPSULE, LEFT, RIGHT)

        /** Безопасный разбор настройки планшета: допустимы только LEFT или RIGHT. */
        fun forTablet(raw: String?): TabBarPosition =
            if (raw.equals(RIGHT.id, ignoreCase = true)) RIGHT else LEFT

        /** Безопасный разбор настройки телефона: по умолчанию BOTTOM. */
        fun forPhone(raw: String?): TabBarPosition =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: BOTTOM
    }
}
