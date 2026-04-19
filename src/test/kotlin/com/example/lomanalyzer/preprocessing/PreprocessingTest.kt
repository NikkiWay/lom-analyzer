package com.example.lomanalyzer.preprocessing

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PreprocessingTest {

    // --- TextCleaner ---

    @Test
    fun `TextCleaner removes HTML tags`() {
        val result = TextCleaner.clean("<p>Hello <b>world</b></p>")
        assertEquals("Hello world", result.cleanText)
    }

    @Test
    fun `TextCleaner replaces URLs with marker`() {
        val result = TextCleaner.clean("Visit https://example.com for more info")
        assertEquals("Visit [URL] for more info", result.cleanText)
        assertEquals(1, result.urlsCount)
    }

    @Test
    fun `TextCleaner replaces mentions with marker`() {
        val result = TextCleaner.clean("Hello @user123 how are you")
        assertEquals("Hello [USER] how are you", result.cleanText)
        assertEquals(1, result.mentionsCount)
    }

    @Test
    fun `TextCleaner extracts hashtags`() {
        val result = TextCleaner.clean("Great #weather today #sunshine")
        assertEquals(2, result.hashtagsCount)
        assertTrue(result.hashtags.contains("weather"))
        assertTrue(result.hashtags.contains("sunshine"))
    }

    @Test
    fun `TextCleaner normalizes whitespace`() {
        val result = TextCleaner.clean("hello   \t\n  world")
        assertEquals("hello world", result.cleanText)
    }

    @Test
    fun `TextCleaner truncates at 15000 chars`() {
        val longText = "a".repeat(16000)
        val result = TextCleaner.clean(longText)
        assertEquals(15000, result.cleanText.length)
        assertTrue(result.truncated)
        assertNotNull(result.truncationReason)
    }

    @Test
    fun `TextCleaner does not truncate short text`() {
        val result = TextCleaner.clean("short text")
        assertFalse(result.truncated)
        assertNull(result.truncationReason)
    }

    @Test
    fun `TextCleaner handles combined HTML and URLs`() {
        val result = TextCleaner.clean("<div>Check <a href='x'>https://vk.com</a> @admin</div>")
        assertEquals("Check [URL] [USER]", result.cleanText)
    }

    // --- LanguageDetectorProxy ---

    @Test
    fun `LanguageDetectorProxy detects Russian text`() {
        val tokens = Tokenizer.tokenize("это был хороший день для всех людей")
        val detector = LanguageDetectorProxy()
        val result = detector.detectFallback(tokens)
        assertEquals("ru", result.language)
        assertTrue(result.confidence >= 0.3f)
    }

    @Test
    fun `LanguageDetectorProxy returns unknown for English text`() {
        val tokens = Tokenizer.tokenize("this is a good day for all people")
        val detector = LanguageDetectorProxy()
        val result = detector.detectFallback(tokens)
        assertEquals("unknown", result.language)
        assertEquals("FILTERED_OUT_LANGUAGE", result.flag)
    }

    @Test
    fun `LanguageDetectorProxy handles empty input`() {
        val detector = LanguageDetectorProxy()
        val result = detector.detectFallback(emptyList())
        assertEquals("unknown", result.language)
    }

    // --- LemmatizerProxy ---

    @Test
    fun `LemmatizerProxy stems Russian word`() {
        val lemmatizer = LemmatizerProxy()
        val results = lemmatizer.stemFallback(listOf("читал"))
        assertEquals(1, results.size)
        assertEquals("чита", results[0].lemma)
        assertEquals("читал", results[0].original)
    }

    @Test
    fun `LemmatizerProxy preserves punctuation`() {
        val lemmatizer = LemmatizerProxy()
        val results = lemmatizer.stemFallback(listOf("слово", ",", "другое"))
        assertEquals(3, results.size)
        assertEquals(",", results[1].lemma)
    }

    @Test
    fun `LemmatizerProxy stems multiple words`() {
        val lemmatizer = LemmatizerProxy()
        val results = lemmatizer.stemFallback(listOf("работающий", "программист"))
        assertEquals(2, results.size)
        // Snowball produces stems, not dictionary forms
        assertTrue(results[0].lemma.length < results[0].original.length)
    }

    // --- Tokenizer ---

    @Test
    fun `Tokenizer splits Russian text`() {
        val tokens = Tokenizer.tokenize("Привет, мир!")
        assertEquals(listOf("Привет", ",", "мир", "!"), tokens)
    }

    @Test
    fun `Tokenizer handles mixed languages`() {
        val tokens = Tokenizer.tokenize("Hello мир 123")
        assertEquals(listOf("Hello", "мир", "123"), tokens)
    }

    // --- StopWords ---

    @Test
    fun `StopWords filters common Russian words`() {
        val tokens = listOf("он", "хороший", "день", "и", "ночь")
        val filtered = StopWords.filter(tokens)
        assertFalse(filtered.contains("и"))
        assertFalse(filtered.contains("он"))
        assertTrue(filtered.contains("хороший"))
    }
}
