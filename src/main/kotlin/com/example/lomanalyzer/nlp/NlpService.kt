/*
 * НАЗНАЧЕНИЕ
 * Контракт NLP-модуля (диплом 2.2.7, см. docs/architecture.md): единый интерфейс
 * обработки естественного языка с двумя режимами реализации — основной (Python FastAPI
 * sidecar: pymorphy3, dostoevsky/RuBERT-tiny2, natasha) и fallback (Kotlin: Snowball
 * stemmer + словарный sentiment). Код пайплайна работает с этим интерфейсом, не зная,
 * какая реализация подключена.
 *
 * ЧТО ВНУТРИ
 *  - DTO результатов: LemmatizeResult, LanguageDetectResult, SentimentScore,
 *    SimilarityResult, EmbeddingResult, NerEntity;
 *  - interface NlpService — одиночные методы (lemmatize, detectLanguage, scoreSentiment,
 *    semanticSimilarity, embed, extractEntities) и пакетные (batch*) с дефолтной реализацией;
 *  - функция sentimentDistribution() — конвертация метки sentiment в распределение {+,0,-}.
 *
 * МЕТОД
 *  Пакетные методы по умолчанию делегируют одиночным (реализации могут переопределить их
 *  для настоящей батч-обработки и амортизации IPC к sidecar). dostoevsky возвращает 4
 *  категории (positive/negative/neutral/speech); по диплому speech объединяется с neutral.
 *
 * СВЯЗИ
 *  Реализуется PythonSidecarNlpService и LocalKotlinNlpService; декорируется
 *  CachingNlpService; выбирается NlpServiceSelector. SentimentDistribution — из core.
 */
package com.example.lomanalyzer.nlp

import com.example.lomanalyzer.core.SentimentDistribution

/** Результат лемматизации: список лемм текста. */
data class LemmatizeResult(val lemmas: List<String>)
/** Результат определения языка: код языка и уверенность [0..1]. */
data class LanguageDetectResult(val language: String, val confidence: Float)
/** Оценка тональности: метка, числовой score и метод получения. */
/**
 * Оценка тональности одного текста.
 *
 * @property label метка-победитель (positive/neutral/negative).
 * @property score уверенность в метке-победителе.
 * @property method источник оценки (модель sidecar либо словарный fallback).
 * @property probabilities распределение вероятностей по трём классам, если
 *   источник его даёт. Модель считает распределение всегда, и метка-победитель
 *   теряет силу склонности: у сдержанного текста бывает neutral 0.80 при
 *   positive 0.15, и по одной метке этот перевес уже не восстановить. Оси
 *   позиции автора (Pos_a) и отклика аудитории (Resp_a) усредняют именно
 *   вероятности. Словарный fallback распределения не даёт — там null.
 */
data class SentimentScore(
    val label: String,
    val score: Float,
    val method: String,
    val probabilities: SentimentDistribution? = null,
)
/** Семантическая близость двух текстов [0..1]. */
data class SimilarityResult(val similarity: Float)
/** Векторное представление (embedding) текста. */
data class EmbeddingResult(val vector: List<Float>)
/** Именованная сущность (NER): текст, тип и границы в исходной строке. */
data class NerEntity(val text: String, val type: String, val start: Int, val end: Int)

/**
 * Контракт NLP-модуля (диплом 2.2.7).
 * Два режима: основной (Python sidecar) и fallback (Kotlin).
 *
 * Одиночные методы (обратная совместимость) + пакетные (амортизация IPC-накладных расходов).
 */
interface NlpService {
    // ── Методы для одного текста ──
    suspend fun lemmatize(text: String): LemmatizeResult
    suspend fun detectLanguage(text: String): LanguageDetectResult
    suspend fun scoreSentiment(text: String, mode: String = "dostoevsky"): SentimentScore
    suspend fun semanticSimilarity(a: String, b: String): SimilarityResult
    suspend fun embed(text: String): EmbeddingResult
    suspend fun extractEntities(text: String): List<NerEntity>

    // ── Пакетные методы (диплом 2.2.7: критично для амортизации IPC-накладных расходов) ──

    /** Лемматизация батча текстов. Реализация по умолчанию делегирует одиночному методу. */
    suspend fun batchLemmatize(texts: List<String>): List<List<String>> =
        texts.map { lemmatize(it).lemmas }

    /**
     * Тональность батча текстов: метка и оценка на каждый текст.
     *
     * Объявлен в интерфейсе, а не только в реализации sidecar, чтобы вызов
     * проходил через декораторы (CachingNlpService) — иначе добраться до
     * пакетного режима можно было бы лишь приведением типа к конкретной
     * реализации, а на обёртке такое приведение не срабатывает.
     *
     * Реализация по умолчанию делегирует одиночному методу.
     */
    suspend fun batchSentiment(texts: List<String>, mode: String = "dostoevsky"): List<SentimentScore> =
        texts.map { scoreSentiment(it, mode) }

    /** Тональность батча постов: распределение {positive, neutral, negative}. */
    suspend fun batchSentimentForPosts(texts: List<String>): List<SentimentDistribution> =
        texts.map { sentimentDistribution(scoreSentiment(it)) }

    /**
     * Тональность батча комментариев.
     * Отдельный метод, т. к. комментарии короче и отличаются по стилю (диплом 2.2.7).
     */
    suspend fun batchSentimentForComments(texts: List<String>): List<SentimentDistribution> =
        texts.map { sentimentDistribution(scoreSentiment(it, "dostoevsky_short")) }
}

/**
 * Преобразует меточную оценку SentimentScore в распределение SentimentDistribution {+, 0, -}.
 * Dostoevsky возвращает 4 категории: positive, negative, neutral, speech.
 * По диплому: speech объединяется с neutral.
 */
fun sentimentDistribution(score: SentimentScore): SentimentDistribution {
    val s = score.score
    return when (score.label.uppercase()) {
        // Позитив: интенсивность s распределяет массу между positive и neutral
        "POSITIVE" -> SentimentDistribution(
            positive = (0.5 + s * 0.5).coerceIn(0.0, 1.0),
            neutral = (0.5 - s * 0.5).coerceIn(0.0, 1.0),
            negative = 0.0,
        )
        // Негатив: масса распределяется между neutral и negative
        "NEGATIVE" -> SentimentDistribution(
            positive = 0.0,
            neutral = (0.5 + s * 0.5).coerceIn(0.0, 1.0),
            negative = (0.5 - s * 0.5).coerceIn(0.0, 1.0),
        )
        // speech/skip объединяем с нейтральным (вся масса в neutral)
        "SPEECH", "SKIP" -> SentimentDistribution(positive = 0.0, neutral = 1.0, negative = 0.0)
        else -> SentimentDistribution( // NEUTRAL или неизвестная метка
            positive = 0.0,
            neutral = 1.0,
            negative = 0.0,
        )
    }
}
