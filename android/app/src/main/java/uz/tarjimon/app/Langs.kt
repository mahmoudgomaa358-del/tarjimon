package uz.tarjimon.app

data class Lang(
    val code: String,
    val name: String,
    val locale: String,
    val rtl: Boolean,
    val tap: String,
    val listen: String,
    val busy: String,
    val hint: String,
    val noVoice: String,
)

val LANGS = listOf(
    Lang("en", "English", "en-US", false, "Tap and speak", "Listening…", "Translating…",
        "Tap the button and speak English", "No English voice on this phone"),
    Lang("ar", "العربية", "ar-SA", true, "اضغط وتحدث", "أستمع…", "جارٍ الترجمة…",
        "اضغط على الزر وتحدث بالعربية", "لا يوجد صوت عربي على هذا الهاتف"),
    Lang("uz", "O‘zbekcha", "uz-UZ", false, "Bosing va gapiring", "Tinglayapman…", "Tarjima qilinmoqda…",
        "Tugmani bosing va o‘zbekcha gapiring", "O‘zbekcha ovoz ishlamadi"),
    Lang("ru", "Русский", "ru-RU", false, "Нажмите и говорите", "Слушаю…", "Перевожу…",
        "Нажмите кнопку и говорите по-русски", "На этом телефоне нет русского голоса"),
)

fun lang(code: String): Lang = LANGS.firstOrNull { it.code == code } ?: LANGS[0]
