/*
 * НАЗНАЧЕНИЕ
 * Определение языка текста на этапе препроцессинга (этап 5, docs/algorithm.md):
 * отсев нерусскоязычных публикаций. Двухрежимный (architecture.md, 2.2.7): при наличии
 * NLP-сервиса (Python sidecar) использует его, иначе — Kotlin-эвристику по частотным
 * русским леммам.
 *
 * ЧТО ВНУТРИ
 *  - LanguageResult — результат (язык, уверенность, флаг пригодности);
 *  - LanguageDetectorProxy — класс: suspend detect() (выбор режима) и detectFallback() (эвристика).
 *
 * МЕТОД (fallback)
 *  Доля словоформ, входящих в список частотных русских лемм: ≥0.5 -> "ru"/OK;
 *  ≥0.30 -> "ru"/LANGUAGE_UNCERTAIN; иначе -> unknown/FILTERED_OUT_LANGUAGE.
 *
 * БИБЛИОТЕКИ / СВЯЗИ
 *  NlpService — абстракция NLP; ресурс /lang_heuristic/ru_frequent_lemmas.txt — словарь
 *  частотных лемм. Вызывается из PreprocessingExecutor и LocalKotlinNlpService.
 */
package com.example.lomanalyzer.preprocessing

import com.example.lomanalyzer.nlp.NlpService

/**
 * Результат определения языка.
 *
 * @param language код языка ("ru" или "unknown").
 * @param confidence уверенность [0..1].
 * @param flag статус пригодности: OK, LANGUAGE_UNCERTAIN (русский, но неуверенно),
 *   FILTERED_OUT_LANGUAGE (отсеять как не-русский).
 */
data class LanguageResult(
    val language: String,
    val confidence: Float,
    val flag: String, // OK, LANGUAGE_UNCERTAIN, FILTERED_OUT_LANGUAGE
)

/**
 * Детектор языка с двумя режимами.
 * @param nlpService NLP-сервис для определения языка; если null — Kotlin-эвристика.
 */
class LanguageDetectorProxy(
    private val nlpService: NlpService? = null,
) {
    companion object {
        /** Нижний порог доли русских лемм, при котором язык считается русским (но неуверенно). */
        private const val RU_THRESHOLD = 0.30f
    }

    /** Множество частотных русских лемм для эвристики, лениво читается из ресурса. */
    private val frequentLemmas: Set<String> by lazy {
        val stream = LanguageDetectorProxy::class.java
            .getResourceAsStream("/lang_heuristic/ru_frequent_lemmas.txt")
        stream?.bufferedReader()?.readLines()
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotBlank() && !it.startsWith("#") }
            ?.toSet()
            ?: emptySet()
    }

    /**
     * Определяет язык. При наличии nlpService делегирует ему, иначе — эвристика по токенам.
     * @param text исходный текст (для NLP-сервиса), @param tokens токены (для fallback).
     */
    suspend fun detect(text: String, tokens: List<String>): LanguageResult {
        if (nlpService != null) {
            // Основной режим: язык от NLP-сервиса, флаг по уверенности
            val result = nlpService.detectLanguage(text)
            val flag = when {
                result.language == "ru" && result.confidence >= 0.5f -> "OK"
                result.language == "ru" -> "LANGUAGE_UNCERTAIN"
                else -> "FILTERED_OUT_LANGUAGE"
            }
            return LanguageResult(result.language, result.confidence, flag)
        }
        // Fallback: NLP недоступен
        return detectFallback(tokens)
    }

    /** Kotlin-эвристика определения русского языка по доле частотных русских лемм. */
    @Suppress("ReturnCount")
    fun detectFallback(tokens: List<String>): LanguageResult {
        // Нет токенов — отсеиваем
        if (tokens.isEmpty()) {
            return LanguageResult("unknown", 0f, "FILTERED_OUT_LANGUAGE")
        }

        // Берём только словоформы (начинаются с буквы), игнорируя числа/пунктуацию
        val wordTokens = tokens.filter { it.isNotEmpty() && it.first().isLetter() }
        if (wordTokens.isEmpty()) {
            return LanguageResult("unknown", 0f, "FILTERED_OUT_LANGUAGE")
        }

        // Доля слов, входящих в словарь частотных русских лемм
        val matchCount = wordTokens.count { it.lowercase() in frequentLemmas }
        val fraction = matchCount.toFloat() / wordTokens.size

        // Классификация по двум порогам
        return when {
            fraction >= 0.5f -> LanguageResult("ru", fraction, "OK")
            fraction >= RU_THRESHOLD -> LanguageResult("ru", fraction, "LANGUAGE_UNCERTAIN")
            else -> LanguageResult("unknown", fraction, "FILTERED_OUT_LANGUAGE")
        }
    }
}
