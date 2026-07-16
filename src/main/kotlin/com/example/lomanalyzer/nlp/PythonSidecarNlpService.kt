/*
 * НАЗНАЧЕНИЕ
 * Реализация NlpService поверх Python FastAPI sidecar — основной (FULL) режим NLP-модуля
 * (диплом 2.2.7, architecture.md). Через HTTP обращается к локальному Python-сервису,
 * где работают pymorphy3 (lemmatization), dostoevsky/RuBERT-tiny2 (sentiment, embeddings,
 * семантическая близость) и natasha (NER).
 *
 * ЧТО ВНУТРИ
 *  PythonSidecarNlpService — HTTP-клиент к sidecar: одиночные методы (lemmatize,
 *  detectLanguage, scoreSentiment, semanticSimilarity, embed, extractEntities), пакетные
 *  (batchLemmatize, batchSentiment), приватные post()/escapeJson() и набор @Serializable DTO.
 *
 * МЕТОД / ДЕТАЛИ
 *  Каждый запрос — POST с заголовком авторизации X-Auth-Token (секрет sidecar) и JSON-телом.
 *  Параллелизм ограничен Semaphore(maxConcurrency), чтобы не перегружать sidecar.
 *  JSON тела формируется вручную с экранированием (escapeJson) во избежание двойного
 *  экранирования. Ответы десериализуются kotlinx.serialization (ignoreUnknownKeys).
 *
 * БИБЛИОТЕКИ
 *  Ktor client (HTTP), kotlinx.coroutines Semaphore (ограничение конкурентности),
 *  kotlinx.serialization (разбор JSON-ответов).
 *
 * СВЯЗИ
 *  Создаётся NlpServiceSelector в режиме FULL; обычно оборачивается CachingNlpService.
 */
package com.example.lomanalyzer.nlp

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * NLP-сервис, работающий через Python FastAPI sidecar по HTTP.
 *
 * @param httpClient Ktor-клиент для запросов.
 * @param baseUrl базовый URL sidecar.
 * @param secret секрет для заголовка X-Auth-Token.
 * @param logger логгер.
 * @param maxConcurrency максимум одновременных запросов к sidecar.
 */
class PythonSidecarNlpService(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val secret: String,
    @Suppress("UnusedPrivateProperty") private val logger: Logger,
    maxConcurrency: Int = 4,
) : NlpService {

    /** Ограничитель параллельных запросов к sidecar. */
    private val semaphore = Semaphore(maxConcurrency)
    private val json = Json { ignoreUnknownKeys = true }

    /** Общий POST-запрос к sidecar: добавляет авторизацию, шлёт JSON-тело и десериализует ответ в T. */
    private suspend inline fun <reified T> post(
        endpoint: String,
        body: String,
    ): T = semaphore.withPermit {
        // withPermit удерживает слот семафора на время запроса (ограничение конкурентности)
        val response: HttpResponse = httpClient.post("$baseUrl$endpoint") {
            header("X-Auth-Token", secret)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        json.decodeFromString(response.bodyAsText())
    }

    /** Лемматизация текста через endpoint /lemmatize (pymorphy3). */
    override suspend fun lemmatize(text: String): LemmatizeResult {
        val resp: LemmatizeResponse = post("/lemmatize", """{"text":"${escapeJson(text)}"}""")
        return LemmatizeResult(resp.lemmas)
    }

    /** Определение языка через /language/detect. */
    override suspend fun detectLanguage(text: String): LanguageDetectResult {
        val resp: LangResponse = post("/language/detect", """{"text":"${escapeJson(text)}"}""")
        return LanguageDetectResult(resp.language, resp.confidence)
    }

    /** Тональность через /sentiment/dostoevsky; mode выбирает вариант модели. */
    override suspend fun scoreSentiment(text: String, mode: String): SentimentScore {
        val resp: SentimentResponse = post(
            "/sentiment/dostoevsky",
            """{"text":"${escapeJson(text)}","mode":"$mode"}""",
        )
        return SentimentScore(resp.label, resp.score, "MODEL")
    }

    /** Семантическая близость двух текстов через /semantic_similarity (embeddings RuBERT). */
    override suspend fun semanticSimilarity(a: String, b: String): SimilarityResult {
        val resp: SimResponse = post(
            "/semantic_similarity",
            """{"a":"${escapeJson(a)}","b":"${escapeJson(b)}"}""",
        )
        return SimilarityResult(resp.similarity)
    }

    /** Векторное представление текста через /embed (RuBERT-tiny2). */
    override suspend fun embed(text: String): EmbeddingResult {
        val resp: EmbedResponse = post("/embed", """{"text":"${escapeJson(text)}"}""")
        return EmbeddingResult(resp.vector)
    }

    /** Извлечение именованных сущностей через /ner/natasha. */
    override suspend fun extractEntities(text: String): List<NerEntity> {
        val resp: NerResponse = post("/ner/natasha", """{"text":"${escapeJson(text)}"}""")
        return resp.entities.map { NerEntity(it.text, it.type, it.start, it.end) }
    }

    /** Пакетная лемматизация через /batch/lemmatize (один HTTP-вызов на батч). */
    override suspend fun batchLemmatize(texts: List<String>): List<List<String>> {
        val body = json.encodeToString(BatchRequest.serializer(), BatchRequest(texts.map { escapeJson(it) }))
        // JSON формируем вручную, чтобы избежать двойного экранирования
        val textsJson = texts.joinToString(",") { "\"${escapeJson(it)}\"" }
        val resp: BatchLemmatizeResponse = post("/batch/lemmatize", """{"texts":[$textsJson]}""")
        return resp.results
    }

    /** Пакетный sentiment через /batch/sentiment (используется в PreprocessingExecutor). */
    suspend fun batchSentiment(texts: List<String>): List<SentimentScore> {
        val textsJson = texts.joinToString(",") { "\"${escapeJson(it)}\"" }
        val resp: BatchSentimentResponse = post("/batch/sentiment", """{"texts":[$textsJson]}""")
        return resp.results.map { SentimentScore(it.label, it.score, "MODEL") }
    }

    /** Экранирует спецсимволы для безопасной вставки строки в JSON-тело запроса. */
    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")

    // DTO ответов sidecar (десериализуются kotlinx.serialization)
    @Serializable data class LemmatizeResponse(val lemmas: List<String>)
    @Serializable data class LangResponse(val language: String, val confidence: Float)
    @Serializable data class SentimentResponse(val label: String, val score: Float)
    @Serializable data class SimResponse(val similarity: Float)
    @Serializable data class EmbedResponse(val vector: List<Float>)
    @Serializable data class NerEntityDto(val text: String, val type: String, val start: Int, val end: Int)
    @Serializable data class NerResponse(val entities: List<NerEntityDto>)
    @Serializable data class BatchRequest(val texts: List<String>)
    @Serializable data class BatchLemmatizeResponse(val results: List<List<String>>)
    @Serializable data class BatchSentimentResponse(val results: List<SentimentResponse>)
}
