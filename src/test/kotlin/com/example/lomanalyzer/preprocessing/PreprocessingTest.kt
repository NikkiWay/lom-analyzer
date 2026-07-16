/*
 * НАЗНАЧЕНИЕ
 * Тесты препроцессинга текста (этап 3 пайплайна): очистка текста (HTML, URL,
 * упоминания, хэштеги, пробелы, обрезка), определение языка (fallback),
 * стемминг/лемматизация (fallback на Snowball), токенизация и фильтр стоп-слов.
 *
 * ЧТО ВНУТРИ
 * Класс PreprocessingTest, сгруппированный по компонентам:
 *  - TextCleaner: удаление HTML, замена URL/упоминаний маркерами, извлечение
 *    хэштегов, нормализация пробелов, обрезка до 15000 символов, комбинации;
 *  - LanguageDetectorProxy: русский → "ru", английский → "unknown" с флагом;
 *  - LemmatizerProxy: Snowball-стемминг, сохранение пунктуации, несколько слов;
 *  - Tokenizer: разбиение текста, смешанные языки;
 *  - StopWords: фильтрация частотных русских стоп-слов.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (assert*). Тестируемый fallback-стемминг — Lucene Snowball (русский),
 * поэтому результат стеммера ("чита" от "читал") — основа слова, не словарная форма.
 *
 * СВЯЗИ
 * TextCleaner, LanguageDetectorProxy, LemmatizerProxy, Tokenizer, StopWords.
 */
package com.example.lomanalyzer.preprocessing

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Тесты компонентов препроцессинга текста (очистка, язык, стемминг, токены). */
class PreprocessingTest {

    // --- TextCleaner ---

    /** TextCleaner удаляет HTML-теги, оставляя только текстовое содержимое. */
    @Test
    fun `TextCleaner removes HTML tags`() {
        // Теги p и b вырезаются, текст склеивается
        val result = TextCleaner.clean("<p>Hello <b>world</b></p>")
        assertEquals("Hello world", result.cleanText)
    }

    /** URL заменяется маркером [URL]; число найденных ссылок учитывается в urlsCount. */
    @Test
    fun `TextCleaner replaces URLs with marker`() {
        val result = TextCleaner.clean("Visit https://example.com for more info")
        assertEquals("Visit [URL] for more info", result.cleanText)
        // Ровно одна ссылка
        assertEquals(1, result.urlsCount)
    }

    /** Упоминание @user заменяется маркером [USER]; счётчик mentionsCount растёт. */
    @Test
    fun `TextCleaner replaces mentions with marker`() {
        val result = TextCleaner.clean("Hello @user123 how are you")
        assertEquals("Hello [USER] how are you", result.cleanText)
        assertEquals(1, result.mentionsCount)
    }

    /** Хэштеги извлекаются в список hashtags (без знака #) и считаются в hashtagsCount. */
    @Test
    fun `TextCleaner extracts hashtags`() {
        val result = TextCleaner.clean("Great #weather today #sunshine")
        // Два хэштега
        assertEquals(2, result.hashtagsCount)
        assertTrue(result.hashtags.contains("weather"))
        assertTrue(result.hashtags.contains("sunshine"))
    }

    /** Повторяющиеся пробелы/табы/переводы строк сжимаются в одиночные пробелы. */
    @Test
    fun `TextCleaner normalizes whitespace`() {
        val result = TextCleaner.clean("hello   \t\n  world")
        assertEquals("hello world", result.cleanText)
    }

    /**
     * Слишком длинный текст обрезается до предела 15000 символов; при этом
     * выставляется флаг truncated и заполняется причина truncationReason.
     */
    @Test
    fun `TextCleaner truncates at 15000 chars`() {
        // Вход в 16000 символов — превышает лимит
        val longText = "a".repeat(16000)
        val result = TextCleaner.clean(longText)
        // Обрезано ровно до 15000
        assertEquals(15000, result.cleanText.length)
        assertTrue(result.truncated)
        assertNotNull(result.truncationReason)
    }

    /** Короткий текст не обрезается: truncated=false, причина обрезки отсутствует. */
    @Test
    fun `TextCleaner does not truncate short text`() {
        val result = TextCleaner.clean("short text")
        assertFalse(result.truncated)
        assertNull(result.truncationReason)
    }

    /**
     * Комбинированный случай: одновременно HTML, URL и упоминание — все обработки
     * применяются вместе (теги вырезаны, ссылка → [URL], упоминание → [USER]).
     */
    @Test
    fun `TextCleaner handles combined HTML and URLs`() {
        val result = TextCleaner.clean("<div>Check <a href='x'>https://vk.com</a> @admin</div>")
        assertEquals("Check [URL] [USER]", result.cleanText)
    }

    // --- LanguageDetectorProxy ---

    /**
     * Fallback-детектор языка по токенам распознаёт русский текст как "ru"
     * с уверенностью не ниже 0.3 (порог для непустого русского ввода).
     */
    @Test
    fun `LanguageDetectorProxy detects Russian text`() {
        // Токенизируем заведомо русскую фразу
        val tokens = Tokenizer.tokenize("это был хороший день для всех людей")
        val detector = LanguageDetectorProxy()
        val result = detector.detectFallback(tokens)
        assertEquals("ru", result.language)
        assertTrue(result.confidence >= 0.3f)
    }

    /**
     * Английский текст не проходит фильтр русского языка: язык "unknown" и флаг
     * FILTERED_OUT_LANGUAGE (анализируются только русскоязычные тексты).
     */
    @Test
    fun `LanguageDetectorProxy returns unknown for English text`() {
        val tokens = Tokenizer.tokenize("this is a good day for all people")
        val detector = LanguageDetectorProxy()
        val result = detector.detectFallback(tokens)
        assertEquals("unknown", result.language)
        assertEquals("FILTERED_OUT_LANGUAGE", result.flag)
    }

    /** Пустой ввод не ломает детектор: язык определяется как "unknown". */
    @Test
    fun `LanguageDetectorProxy handles empty input`() {
        val detector = LanguageDetectorProxy()
        val result = detector.detectFallback(emptyList())
        assertEquals("unknown", result.language)
    }

    // --- LemmatizerProxy ---

    /**
     * Snowball-стемминг русского слова: "читал" → основа "чита"; в результате
     * сохраняется и исходная форма (original) для трассируемости.
     */
    @Test
    fun `LemmatizerProxy stems Russian word`() {
        val lemmatizer = LemmatizerProxy()
        val results = lemmatizer.stemFallback(listOf("читал"))
        assertEquals(1, results.size)
        // Стем (основа), а не словарная форма
        assertEquals("чита", results[0].lemma)
        assertEquals("читал", results[0].original)
    }

    /** Пунктуация при стемминге не теряется и не изменяется (запятая остаётся запятой). */
    @Test
    fun `LemmatizerProxy preserves punctuation`() {
        val lemmatizer = LemmatizerProxy()
        val results = lemmatizer.stemFallback(listOf("слово", ",", "другое"))
        assertEquals(3, results.size)
        // Запятая сохранена как отдельный токен
        assertEquals(",", results[1].lemma)
    }

    /**
     * Стемминг нескольких слов: каждое обрабатывается; для словоизменённых форм
     * основа короче исходного слова (Snowball отбрасывает окончания).
     */
    @Test
    fun `LemmatizerProxy stems multiple words`() {
        val lemmatizer = LemmatizerProxy()
        val results = lemmatizer.stemFallback(listOf("работающий", "программист"))
        assertEquals(2, results.size)
        // Snowball produces stems, not dictionary forms
        // Основа короче исходного слова
        assertTrue(results[0].lemma.length < results[0].original.length)
    }

    // --- Tokenizer ---

    /** Токенизатор разбивает русский текст на слова и знаки препинания по отдельности. */
    @Test
    fun `Tokenizer splits Russian text`() {
        val tokens = Tokenizer.tokenize("Привет, мир!")
        // Слова и пунктуация — отдельные токены
        assertEquals(listOf("Привет", ",", "мир", "!"), tokens)
    }

    /** Смешанный текст: латиница, кириллица и числа выделяются как отдельные токены. */
    @Test
    fun `Tokenizer handles mixed languages`() {
        val tokens = Tokenizer.tokenize("Hello мир 123")
        assertEquals(listOf("Hello", "мир", "123"), tokens)
    }

    // --- StopWords ---

    /**
     * Фильтр стоп-слов удаляет частотные служебные слова ("и", "он"), но
     * сохраняет значимые ("хороший"), снижая шум перед анализом.
     */
    @Test
    fun `StopWords filters common Russian words`() {
        val tokens = listOf("он", "хороший", "день", "и", "ночь")
        val filtered = StopWords.filter(tokens)
        // Служебные слова отброшены
        assertFalse(filtered.contains("и"))
        assertFalse(filtered.contains("он"))
        // Значимое слово сохранено
        assertTrue(filtered.contains("хороший"))
    }
}
