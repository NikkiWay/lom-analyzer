/*
 * НАЗНАЧЕНИЕ
 * Кэширующий декоратор NlpService (диплом 2.2.7): сохраняет результаты лемматизации и
 * sentiment в таблице nlp_result по хэшу текста, чтобы при повторных прогонах с теми же
 * данными не вызывать NLP заново (что особенно дорого для Python sidecar/ML-моделей).
 *
 * ЧТО ВНУТРИ
 *  CachingNlpService — обёртка вокруг любого NlpService (паттерн «декоратор» через
 *  делегирование `by delegate`). Переопределяет lemmatize/scoreSentiment и пакетные
 *  методы, добавляя проверку/запись кэша; приватный sha256() — ключ кэша.
 *
 * МЕТОД
 *  Ключ кэша = SHA-256(text) + версия модели (modelVersion). Несовпадение версии модели
 *  = промах кэша (результаты разных режимов/моделей не смешиваются). В батч-методах
 *  сначала отбираются некэшированные тексты, обрабатываются одним вызовом, затем пишутся.
 *
 * БИБЛИОТЕКИ / СВЯЗИ
 *  java.security.MessageDigest (SHA-256), kotlinx.serialization (леммы <-> JSON),
 *  NlpResultDao/NlpResults (Exposed, таблица nlp_result). Подключается NlpServiceSelector.
 */
package com.example.lomanalyzer.nlp

import org.jetbrains.exposed.sql.ResultRow

import com.example.lomanalyzer.core.SentimentDistribution
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.storage.dao.NlpResultDao
import com.example.lomanalyzer.storage.tables.NlpResults
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Кэширующий декоратор для NlpService (диплом 2.2.7).
 * Хранит результаты лемматизации и sentiment в таблице nlp_result по хэшу текста.
 * При повторных прогонах с теми же данными NLP не вызывается заново.
 *
 * @param delegate оборачиваемая реализация NLP (sidecar или Kotlin).
 * @param nlpResultDao DAO кэша результатов.
 * @param modelVersion версия модели — часть ключа кэша (изоляция режимов/моделей).
 * @param logger логгер.
 */
class CachingNlpService(
    private val delegate: NlpService,
    private val nlpResultDao: NlpResultDao,
    private val modelVersion: String,
    private val logger: Logger,
) : NlpService by delegate {

    private val json = Json { ignoreUnknownKeys = true }

    /** Лемматизация с кэшем: при попадании возвращает сохранённые леммы, иначе вызывает delegate и кэширует. */
    override suspend fun lemmatize(text: String): LemmatizeResult {
        val hash = sha256(text)
        // Проверяем кэш по хэшу и версии модели
        val cached = nlpResultDao.findByHash(hash, modelVersion)
        if (cached != null) {
            val lemmasJson = cached[NlpResults.lemmasJson]
            if (lemmasJson != null) {
                return LemmatizeResult(json.decodeFromString(lemmasJson))
            }
        }
        // Промах кэша: считаем и сохраняем
        val result = delegate.lemmatize(text)
        nlpResultDao.insertLemmas(hash, json.encodeToString(result.lemmas), modelVersion)
        return result
    }

    /** Sentiment с кэшем: при попадании возвращает сохранённую оценку, иначе вызывает delegate и кэширует. */
    override suspend fun scoreSentiment(text: String, mode: String): SentimentScore {
        val hash = sha256(text)
        val cached = nlpResultDao.findByHash(hash, modelVersion)
        // Используем кэш, только если все поля sentiment заполнены
        cached?.let { scoreFromCache(it) }?.let { return it }

        val result = delegate.scoreSentiment(text, mode)
        nlpResultDao.insertSentiment(
            hash, result.label, result.score, result.method, modelVersion,
            probabilities = result.probabilities,
        )
        return result
    }

    /** Пакетная лемматизация с кэшем: некэшированные тексты обрабатываются одним батч-вызовом. */
    override suspend fun batchLemmatize(texts: List<String>): List<List<String>> {
        val results = mutableListOf<List<String>>()
        val uncachedIndices = mutableListOf<Int>()
        val uncachedTexts = mutableListOf<String>()

        // Кэш поднимаем одним запросом на весь батч: поштучный findByHash открывал
        // отдельную транзакцию на каждый текст, и «батч» из 50 текстов стоил
        // 50 обращений к БД ещё до единственного вызова модели.
        val hashes = texts.map { sha256(it) }
        val cachedByHash = nlpResultDao.findByHashes(hashes, modelVersion)

        // Раскладываем результаты, сохраняя исходный порядок текстов
        for ((i, text) in texts.withIndex()) {
            val cached = cachedByHash[hashes[i]]
            val lemmasJson = cached?.get(NlpResults.lemmasJson)
            if (lemmasJson != null) {
                results.add(json.decodeFromString(lemmasJson))
            } else {
                results.add(emptyList()) // заглушка, заполнится после батч-обработки
                uncachedIndices.add(i)
                uncachedTexts.add(text)
            }
        }

        // Батч-обработка только некэшированных текстов и запись их в кэш
        if (uncachedTexts.isNotEmpty()) {
            val batchResults = delegate.batchLemmatize(uncachedTexts)
            for ((j, idx) in uncachedIndices.withIndex()) {
                val lemmas = batchResults[j]
                results[idx] = lemmas
                nlpResultDao.insertLemmas(sha256(texts[idx]), json.encodeToString(lemmas), modelVersion)
            }
        }
        return results
    }

    /** Пакетный sentiment постов с кэшем (режим "dostoevsky"). */
    override suspend fun batchSentimentForPosts(texts: List<String>): List<SentimentDistribution> =
        batchSentiment(texts, "dostoevsky").map { sentimentDistribution(it) }

    /** Пакетный sentiment комментариев с кэшем (режим "dostoevsky_short"). */
    override suspend fun batchSentimentForComments(texts: List<String>): List<SentimentDistribution> =
        batchSentiment(texts, "dostoevsky_short").map { sentimentDistribution(it) }

    /**
     * Пакетный sentiment с кэшем: чтение кэша одним запросом, добор некэшированных
     * одним обращением к модели, запись результатов.
     */
    override suspend fun batchSentiment(texts: List<String>, mode: String): List<SentimentScore> {
        // Массив результатов фиксированного размера, чтобы сохранить исходный порядок
        val results = arrayOfNulls<SentimentScore>(texts.size)
        val uncachedIndices = mutableListOf<Int>()
        val uncachedTexts = mutableListOf<String>()

        // Кэш поднимаем одним запросом на весь батч (см. batchLemmatize)
        val hashes = texts.map { sha256(it) }
        val cachedByHash = nlpResultDao.findByHashes(hashes, modelVersion)

        // Сначала пытаемся взять из кэша
        for ((i, text) in texts.withIndex()) {
            val fromCache = cachedByHash[hashes[i]]?.let { scoreFromCache(it) }
            if (fromCache != null) {
                results[i] = fromCache
            } else {
                uncachedIndices.add(i)
                uncachedTexts.add(text)
            }
        }

        if (uncachedTexts.isNotEmpty()) {
            // Некэшированные уходят в модель ОДНИМ пакетным вызовом: поштучный
            // scoreSentiment сводил бы пакетный режим к N обращениям к sidecar.
            val computed = delegate.batchSentiment(uncachedTexts, mode)
            for ((j, idx) in uncachedIndices.withIndex()) {
                val score = computed[j]
                results[idx] = score
                nlpResultDao.insertSentiment(
                    sha256(texts[idx]), score.label, score.score, score.method, modelVersion,
                    probabilities = score.probabilities,
                )
            }
        }

        // К этому моменту все ячейки заполнены — безопасно приводим к не-nullable
        @Suppress("UNCHECKED_CAST")
        return (results as Array<SentimentScore>).toList()
    }

    /**
     * Собирает SentimentScore из строки кэша, включая распределение вероятностей.
     * Распределение восстанавливается, только если оно там есть: у словарного
     * fallback колонки пустые, и score остаётся без вероятностей.
     */
    private fun scoreFromCache(row: ResultRow): SentimentScore? {
        val sentiment = row[NlpResults.sentiment]
        val score = row[NlpResults.score]
        val method = row[NlpResults.method]
        // Строка кэша годится, только если заполнены все поля тональности:
        // тот же ключ используют и записи лемматизации, у которых их нет.
        if (sentiment == null || score == null || method == null) return null

        val neutral = row[NlpResults.probNeutral]
        val probabilities = neutral?.let {
            SentimentDistribution(
                positive = (row[NlpResults.probPositive] ?: 0f).toDouble(),
                neutral = it.toDouble(),
                negative = (row[NlpResults.probNegative] ?: 0f).toDouble(),
            )
        }
        return SentimentScore(sentiment, score, method, probabilities)
    }

    /** Вычисляет SHA-256 текста в виде hex-строки — ключ кэша. */
    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
