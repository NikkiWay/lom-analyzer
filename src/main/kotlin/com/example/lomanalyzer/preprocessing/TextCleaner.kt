/*
 * НАЗНАЧЕНИЕ
 * Очистка «сырого» текста публикации/комментария на этапе препроцессинга (этап 5,
 * docs/algorithm.md): удаление HTML, нормализация ссылок/упоминаний/эмодзи, сбор
 * статистики (хэштеги, ссылки, упоминания) и усечение слишком длинных текстов.
 *
 * ЧТО ВНУТРИ
 *  - CleanResult — результат очистки (чистый текст, хэштеги, эмодзи, флаг усечения, счётчики);
 *  - object TextCleaner — singleton с набором Regex и методом clean().
 *
 * МЕТОД
 *  Последовательная замена по регулярным выражениям: вырезаются HTML-теги, ссылки
 *  заменяются плейсхолдером [URL], VK-упоминания [id..|Name] и @-упоминания -> [USER],
 *  эмодзи удаляются (но предварительно сохраняются в отдельное поле), множественные
 *  пробелы схлопываются. Тексты длиннее MAX_LENGTH (15000) усекаются.
 *
 * СВЯЗИ
 *  Результат (cleanText) идёт в Tokenizer; счётчики используются как признаки.
 */
package com.example.lomanalyzer.preprocessing

/**
 * Результат очистки текста.
 *
 * @param cleanText очищенный текст (с плейсхолдерами [URL]/[USER]).
 * @param hashtags извлечённые хэштеги (без символа #).
 * @param emojis собранные эмодзи (отдельно от текста).
 * @param truncated был ли текст усечён по длине.
 * @param truncationReason причина усечения (если было).
 * @param urlsCount число ссылок в исходном тексте.
 * @param mentionsCount число упоминаний (@ и VK-формата).
 * @param hashtagsCount число хэштегов.
 */
data class CleanResult(
    val cleanText: String,
    val hashtags: List<String>,
    val emojis: String,
    val truncated: Boolean,
    val truncationReason: String? = null,
    val urlsCount: Int = 0,
    val mentionsCount: Int = 0,
    val hashtagsCount: Int = 0,
)

/** Singleton-очиститель текста. */
object TextCleaner {
    /** Максимальная длина чистого текста; превышение -> усечение. */
    private const val MAX_LENGTH = 15_000

    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val URL_REGEX = Regex("https?://\\S+")
    private val VK_MENTION_REGEX = Regex("\\[id\\d+\\|[^]]*]")
    private val MENTION_REGEX = Regex("@[\\w.]+")
    private val HASHTAG_REGEX = Regex("#[\\wА-яёЁ]+")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    // Unicode-диапазоны эмодзи: смайлы, символы, дингбаты, доп. символы, флаги, модификаторы
    private val EMOJI_REGEX = Regex("[\\x{1F600}-\\x{1F64F}\\x{1F300}-\\x{1F5FF}\\x{1F680}-\\x{1F6FF}" +
        "\\x{1F1E0}-\\x{1F1FF}\\x{2600}-\\x{26FF}\\x{2700}-\\x{27BF}" +
        "\\x{FE00}-\\x{FE0F}\\x{1F900}-\\x{1F9FF}\\x{200D}\\x{20E3}]+")

    /**
     * Очищает исходный текст и собирает статистику.
     * @param rawText «сырой» текст из VK.
     * @return CleanResult с чистым текстом и метаданными.
     */
    fun clean(rawText: String): CleanResult {
        // Сначала извлекаем хэштеги (до очистки), убирая символ #
        val hashtags = HASHTAG_REGEX.findAll(rawText)
            .map { it.value.removePrefix("#") }
            .toList()

        // Эмодзи сохраняем в отдельное поле (диплом, этап обработки: эмодзи хранятся отдельно)
        val emojis = EMOJI_REGEX.findAll(rawText).joinToString("") { it.value }

        // Счётчики ссылок и упоминаний (обоих форматов) до замен
        val urlsCount = URL_REGEX.findAll(rawText).count()
        val mentionsCount = MENTION_REGEX.findAll(rawText).count() +
            VK_MENTION_REGEX.findAll(rawText).count()

        // Последовательная нормализация текста
        var text = rawText
        text = HTML_TAG_REGEX.replace(text, "")          // убираем HTML-теги
        text = URL_REGEX.replace(text, "[URL]")          // ссылки -> плейсхолдер
        text = VK_MENTION_REGEX.replace(text, "[USER]")  // [id123|Name] -> [USER]
        text = MENTION_REGEX.replace(text, "[USER]")     // @nick -> [USER]
        text = EMOJI_REGEX.replace(text, " ")            // эмодзи из чистого текста удаляем
        text = MULTI_SPACE_REGEX.replace(text, " ").trim() // схлопываем пробелы

        // Усечение слишком длинного текста с фиксацией причины
        var truncated = false
        var reason: String? = null
        if (text.length > MAX_LENGTH) {
            text = text.substring(0, MAX_LENGTH)
            truncated = true
            reason = "TEXT_EXCEEDS_${MAX_LENGTH}_CHARS"
        }

        return CleanResult(
            cleanText = text,
            hashtags = hashtags,
            emojis = emojis,
            truncated = truncated,
            truncationReason = reason,
            urlsCount = urlsCount,
            mentionsCount = mentionsCount,
            hashtagsCount = hashtags.size,
        )
    }
}
