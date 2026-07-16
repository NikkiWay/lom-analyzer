/*
 * НАЗНАЧЕНИЕ
 * Словарный (lexicon-based) анализ тональности (sentiment) — это fallback-режим
 * NLP-модуля (см. docs/architecture.md, раздел 2.2.7), который применяется, когда
 * Python FastAPI sidecar (dostoevsky/RuBERT) недоступен. Работает в Kotlin без
 * внешних моделей: оценивает тональность текста по заранее подготовленному словарю
 * позитивных/негативных лемм и эвристикам сигнальных слов.
 *
 * ЧТО ВНУТРИ
 *  - SentimentResult — результат оценки (метка, числовой score, метод, флаг учёта отрицаний);
 *  - SentilexData — структура словаря RuSentiLex (positive/negative списки), читается из ресурса;
 *  - DictionarySentiment — основной класс: метод score() и эвристический fallback scoreBySignals().
 *
 * МЕТОД
 *  1. Основной путь — подсчёт позитивных/негативных лемм по словарю с учётом
 *     инверсии отрицаний (NegationHandler), затем нормализация:
 *     rawScore = (nPos − nNeg) / (nPos + nNeg + 1) ∈ (−1..1).
 *  2. Если словарных совпадений нет — эвристика по сигнальным словам, восклицательным/
 *     вопросительным знакам, риторическим «?!», капсу (даёт более слабый score).
 *  Метки: POSITIVE / NEGATIVE / NEUTRAL; при |score| < порога — NEUTRAL/LOW_CONFIDENCE.
 *
 * БИБЛИОТЕКИ
 *  - kotlinx.serialization (+ Json) — разбор словаря sentilex_base.json из ресурсов;
 *  - kotlin.math.abs — для модуля score.
 *
 * СВЯЗИ
 *  - Используется LocalKotlinNlpService (Kotlin-fallback слоя nlp/);
 *  - Опирается на NegationHandler (учёт отрицаний) из этого же пакета.
 */
package com.example.lomanalyzer.analysis.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Результат оценки тональности одного текста.
 *
 * @param label метка тональности: "POSITIVE", "NEGATIVE" или "NEUTRAL".
 * @param score числовая оценка в диапазоне [-1..1]: >0 — позитив, <0 — негатив, 0 — нейтрально.
 * @param method способ получения оценки: "DICTIONARY", "SIGNAL", "LOW_CONFIDENCE" или "NO_LEXICON_MATCH".
 * @param negationApplied true, если при подсчёте сработала инверсия отрицаний (NegationHandler).
 */
data class SentimentResult(
    val label: String,
    val score: Float,
    val method: String,
    val negationApplied: Boolean,
)

/**
 * Структура словаря тональности (RuSentiLex), десериализуемая из JSON-ресурса.
 *
 * @param version версия словаря.
 * @param positive список лемм с позитивной окраской.
 * @param negative список лемм с негативной окраской.
 */
@Serializable
data class SentilexData(
    val version: String = "",
    val positive: List<String> = emptyList(),
    val negative: List<String> = emptyList(),
)

/**
 * Словарный анализатор тональности (fallback-режим NLP).
 * Оценивает тональность текста по словарю лемм и эвристикам, без ML-моделей.
 */
class DictionarySentiment {
    companion object {
        /** Порог уверенности: при |score| ниже него оценка считается нейтральной. */
        private const val LOW_CONFIDENCE_THRESHOLD = 0.15f

        // Сигнальные слова негативной позиции (fallback, когда словарь не дал совпадений)
        private val NEGATIVE_SIGNALS = setOf(
            "против", "зачем", "почему", "куда", "хватит", "доколе", "сколько_можно",
            "надоело", "устали", "достало", "увы", "жаль", "к_сожалению",
        )
        // Сигнальные слова позитивной/поддерживающей позиции
        private val POSITIVE_SIGNALS = setOf(
            "за", "поддерживаю", "согласен", "согласна", "одобряю", "верно",
            "правильно", "наконец", "давно_пора", "молодцы", "респект",
        )
    }

    /** Словарь тональности, лениво загружаемый из ресурса sentilex_base.json (пустой, если ресурс не найден). */
    private val sentilex: SentilexData by lazy {
        val json = Json { ignoreUnknownKeys = true }
        // Читаем JSON-словарь из classpath-ресурса
        val stream = DictionarySentiment::class.java
            .getResourceAsStream("/resources/sentilex_base.json")
        if (stream != null) {
            json.decodeFromString<SentilexData>(stream.bufferedReader().readText())
        } else {
            // Ресурс отсутствует — работаем с пустым словарём (останется только эвристика по сигналам)
            SentilexData()
        }
    }

    /** Множество позитивных лемм в нижнем регистре (для быстрой проверки вхождения). */
    private val posSet by lazy { sentilex.positive.map { it.lowercase() }.toSet() }
    /** Множество негативных лемм в нижнем регистре. */
    private val negSet by lazy { sentilex.negative.map { it.lowercase() }.toSet() }

    /**
     * Оценивает тональность по списку лемм текста.
     *
     * @param lemmas леммы (нормализованные слова) текста.
     * @param negationHandler обработчик отрицаний (по умолчанию со стандартными негаторами).
     * @return SentimentResult с меткой, числовым score и методом оценки.
     */
    @Suppress("ReturnCount")
    fun score(
        lemmas: List<String>,
        negationHandler: NegationHandler = NegationHandler(),
    ): SentimentResult {
        // Пустой вход — нейтрально, словарных совпадений нет
        if (lemmas.isEmpty()) {
            return SentimentResult("NEUTRAL", 0f, "NO_LEXICON_MATCH", false)
        }

        // Считаем позитивные/негативные леммы с учётом инверсии отрицаний (не хороший -> негатив)
        val adjusted = negationHandler.applyNegation(lemmas, posSet, negSet)
        val nPos = adjusted.positiveCount
        val nNeg = adjusted.negativeCount

        // Основной путь: оценка по словарю, если есть хотя бы одно совпадение
        if (nPos > 0 || nNeg > 0) {
            // Нормализованный баланс тональности в (-1..1); +1 в знаменателе сглаживает малые счётчики
            val rawScore = (nPos - nNeg).toFloat() / (nPos + nNeg + 1)
            val label = when {
                // Слишком слабый сигнал -> считаем нейтральным
                kotlin.math.abs(rawScore) < LOW_CONFIDENCE_THRESHOLD -> "NEUTRAL"
                rawScore > 0 -> "POSITIVE"
                else -> "NEGATIVE"
            }
            // Метод оценки: при слабом сигнале помечаем как LOW_CONFIDENCE, иначе DICTIONARY
            val method = if (kotlin.math.abs(rawScore) < LOW_CONFIDENCE_THRESHOLD) {
                "LOW_CONFIDENCE"
            } else {
                "DICTIONARY"
            }
            return SentimentResult(label, rawScore, method, adjusted.negationApplied)
        }

        // Совпадений по словарю нет — переходим к эвристике сигнальных слов и пунктуации
        return scoreBySignals(lemmas)
    }

    /**
     * Эвристический fallback определения позиции по сигнальным словам, знакам пунктуации
     * и интенсификаторам (капс, восклицания). Даёт более слабую оценку (±0.2..0.4), метод "SIGNAL".
     */
    private fun scoreBySignals(lemmas: List<String>): SentimentResult {
        val lowerLemmas = lemmas.map { it.lowercase() }

        var posSignals = 0
        var negSignals = 0

        // Считаем вхождения позитивных и негативных сигнальных слов
        for (w in lowerLemmas) {
            if (w in POSITIVE_SIGNALS) posSignals++
            if (w in NEGATIVE_SIGNALS) negSignals++
        }

        // Восклицательные знаки — признак эмоциональной интенсивности
        val exclamations = lemmas.count { it.contains("!") }
        // Вопросительные знаки в утвердительном контексте часто означают критику
        val questions = lemmas.count { it.contains("?") }

        // "?!" или "!?" — риторическое возмущение, усиливает негативный сигнал
        val rhetoricalCount = lemmas.count { it.contains("?!") || it.contains("!?") }
        negSignals += rhetoricalCount

        // Токены в КАПСЕ указывают на сильную позицию (длина >2, целиком в верхнем регистре, есть буквы)
        val capsTokens = lemmas.count { it.length > 2 && it == it.uppercase() && it.any { c -> c.isLetter() } }
        // Суммарная эмоциональная интенсивность, ограниченная сверху значением 3
        val emotionalIntensity = (exclamations + capsTokens).coerceAtMost(3)

        // Совсем нет сигналов и эмоций — нейтрально
        if (posSignals == 0 && negSignals == 0 && emotionalIntensity == 0) {
            return SentimentResult("NEUTRAL", 0f, "NO_LEXICON_MATCH", false)
        }

        // Баланс сигнальных слов: >0 — к позитиву, <0 — к негативу
        val signalBalance = (posSignals - negSignals).toFloat()
        val rawScore = when {
            // Перевес позитивных сигналов — слабый позитив с надбавкой за интенсивность
            signalBalance > 0 -> 0.2f + (emotionalIntensity * 0.05f)
            // Перевес негативных сигналов — слабый негатив
            signalBalance < 0 -> -(0.2f + (emotionalIntensity * 0.05f))
            // Сигналов поровну, но больше вопросов чем восклицаний — трактуем как критику (негатив)
            emotionalIntensity > 0 && questions > exclamations -> -(0.15f + emotionalIntensity * 0.05f)
            // Иначе эмоциональность без вопросов — слабый позитив
            emotionalIntensity > 0 -> 0.15f + emotionalIntensity * 0.05f
            else -> 0f
        }.coerceIn(-1f, 1f)

        // Слишком слабая итоговая оценка — нейтрально
        if (kotlin.math.abs(rawScore) < LOW_CONFIDENCE_THRESHOLD) {
            return SentimentResult("NEUTRAL", 0f, "NO_LEXICON_MATCH", false)
        }

        val label = if (rawScore > 0) "POSITIVE" else "NEGATIVE"
        return SentimentResult(label, rawScore, "SIGNAL", false)
    }
}
