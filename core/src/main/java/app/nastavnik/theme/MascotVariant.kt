package app.nastavnik.theme

/**
 * Варианты оформления минималистичного маскота «Наставник» (модернизация базового и двух-орбитных силуэтов).
 */
enum class MascotVariant(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
) {
    CLASSIC(
        id = "CLASSIC",
        title = "Базовый Модерн",
        subtitle = "Сфера с мягким объёмом и отсветом, каллиграфическая 3D-орбита с сине-голубым градиентом",
        badge = "По умолчанию",
    ),
    DUAL_ORBIT(
        id = "DUAL_ORBIT",
        title = "Двойная гиро-орбита",
        subtitle = "Широкая градиентная и тонкая встречная орбиты с чистой посадкой ниже глаз и двойной кометой",
        badge = "Вариант 1",
    ),
    PARALLEL_ORBIT(
        id = "PARALLEL_ORBIT",
        title = "Двойной каскад",
        subtitle = "Два соосных параллельных 3D-кольца разной толщины с яркой искрой-спутником на внешнем контуре",
        badge = "Вариант 2",
    ),
    ECLIPSE_DUO(
        id = "ECLIPSE_DUO",
        title = "Затмение · 2 орбиты",
        subtitle = "Световой серп затмения по краю сферы и перелив индиго-лазурных двойных орбит",
        badge = "Вариант 3",
    );

    companion object {
        fun fromId(raw: String?): MascotVariant =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: CLASSIC
    }
}
