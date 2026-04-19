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

class PythonSidecarNlpService(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val secret: String,
    @Suppress("UnusedPrivateProperty") private val logger: Logger,
    maxConcurrency: Int = 4,
) : NlpService {

    private val semaphore = Semaphore(maxConcurrency)
    private val json = Json { ignoreUnknownKeys = true }

    private suspend inline fun <reified T> post(
        endpoint: String,
        body: String,
    ): T = semaphore.withPermit {
        val response: HttpResponse = httpClient.post("$baseUrl$endpoint") {
            header("X-Auth-Token", secret)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        json.decodeFromString(response.bodyAsText())
    }

    override suspend fun lemmatize(text: String): LemmatizeResult {
        val resp: LemmatizeResponse = post("/lemmatize", """{"text":"${escapeJson(text)}"}""")
        return LemmatizeResult(resp.lemmas)
    }

    override suspend fun detectLanguage(text: String): LanguageDetectResult {
        val resp: LangResponse = post("/language/detect", """{"text":"${escapeJson(text)}"}""")
        return LanguageDetectResult(resp.language, resp.confidence)
    }

    override suspend fun scoreSentiment(text: String, mode: String): SentimentScore {
        val resp: SentimentResponse = post(
            "/sentiment/dostoevsky",
            """{"text":"${escapeJson(text)}","mode":"$mode"}""",
        )
        return SentimentScore(resp.label, resp.score, "MODEL")
    }

    override suspend fun semanticSimilarity(a: String, b: String): SimilarityResult {
        val resp: SimResponse = post(
            "/semantic_similarity",
            """{"a":"${escapeJson(a)}","b":"${escapeJson(b)}"}""",
        )
        return SimilarityResult(resp.similarity)
    }

    override suspend fun embed(text: String): EmbeddingResult {
        val resp: EmbedResponse = post("/embed", """{"text":"${escapeJson(text)}"}""")
        return EmbeddingResult(resp.vector)
    }

    override suspend fun extractEntities(text: String): List<NerEntity> {
        val resp: NerResponse = post("/ner/natasha", """{"text":"${escapeJson(text)}"}""")
        return resp.entities.map { NerEntity(it.text, it.type, it.start, it.end) }
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    // Response DTOs
    @Serializable data class LemmatizeResponse(val lemmas: List<String>)
    @Serializable data class LangResponse(val language: String, val confidence: Float)
    @Serializable data class SentimentResponse(val label: String, val score: Float)
    @Serializable data class SimResponse(val similarity: Float)
    @Serializable data class EmbedResponse(val vector: List<Float>)
    @Serializable data class NerEntityDto(val text: String, val type: String, val start: Int, val end: Int)
    @Serializable data class NerResponse(val entities: List<NerEntityDto>)
}
