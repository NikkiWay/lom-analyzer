/*
 * НАЗНАЧЕНИЕ
 * Тесты кэширующего декоратора NLP. Проверяют пакетные пути: число обращений
 * к БД на батч, единственный вызов модели за некэшированными текстами и
 * сохранность порядка результатов.
 *
 * ЧТО ВНУТРИ
 * Класс CachingNlpServiceTest: число обращений к кэшу на батч, обращение к
 * модели только за некэшированными текстами, сохранение порядка результатов,
 * работа кэша при повторном прогоне.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5; kotlinx.coroutines.runBlocking; Exposed ORM + JDBC SQLite (временная
 * БД с миграциями); MockK (spyk) — подсчёт вызовов реального DAO, класс которого
 * финальный. Делегат — учётная заглушка вместо реальной модели.
 *
 * СВЯЗИ
 * CachingNlpService (nlp/), NlpResultDao (storage/dao), NlpService (nlp/).
 */
package com.example.lomanalyzer.nlp

import com.example.lomanalyzer.core.SentimentDistribution
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.storage.Migrations
import com.example.lomanalyzer.storage.dao.NlpResultDao
import io.mockk.clearMocks
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class CachingNlpServiceTest {
    private lateinit var tempDb: Path
    private lateinit var db: Database
    private lateinit var dao: NlpResultDao
    private lateinit var delegate: CountingNlpService
    private lateinit var caching: CachingNlpService

    /** Заглушка модели: считает вызовы и запоминает, что у неё спрашивали. */
    private class CountingNlpService : NlpService {
        var batchLemmatizeCalls = 0
        var batchSentimentCalls = 0
        var scoreSentimentCalls = 0
        val lemmatizeRequested = mutableListOf<String>()
        val sentimentRequested = mutableListOf<String>()

        override suspend fun lemmatize(text: String) = LemmatizeResult(listOf(text.lowercase()))
        override suspend fun detectLanguage(text: String) = LanguageDetectResult("ru", 1.0f)
        override suspend fun scoreSentiment(text: String, mode: String): SentimentScore {
            scoreSentimentCalls++
            return SentimentScore("POSITIVE", 0.9f, "stub")
        }
        override suspend fun semanticSimilarity(a: String, b: String) = SimilarityResult(0.5f)
        override suspend fun embed(text: String) = EmbeddingResult(listOf(0f))
        override suspend fun extractEntities(text: String): List<NerEntity> = emptyList()

        override suspend fun batchSentiment(texts: List<String>, mode: String): List<SentimentScore> {
            batchSentimentCalls++
            sentimentRequested.addAll(texts)
            return texts.map {
                SentimentScore(
                    "NEGATIVE", 0.7f, "stub_batch",
                    SentimentDistribution(positive = 0.1, neutral = 0.2, negative = 0.7),
                )
            }
        }

        override suspend fun batchLemmatize(texts: List<String>): List<List<String>> {
            batchLemmatizeCalls++
            lemmatizeRequested.addAll(texts)
            return texts.map { listOf("lemma_of_$it") }
        }

        override suspend fun batchSentimentForPosts(texts: List<String>): List<SentimentDistribution> =
            texts.map { SentimentDistribution(1.0, 0.0, 0.0) }

        override suspend fun batchSentimentForComments(texts: List<String>): List<SentimentDistribution> =
            texts.map { SentimentDistribution(1.0, 0.0, 0.0) }
    }

    @BeforeEach
    fun setup() {
        tempDb = Files.createTempFile("nlp_cache_", ".db")
        Migrations.migrate(tempDb)
        db = Database.connect("jdbc:sqlite:${tempDb.toAbsolutePath()}", driver = "org.sqlite.JDBC")
        // spyk: NlpResultDao — финальный класс, но поведение нужно настоящее (реальная БД),
        // а вызовы — подсчитанные
        dao = spyk(NlpResultDao(db))
        delegate = CountingNlpService()
        caching = CachingNlpService(
            delegate = delegate,
            nlpResultDao = dao,
            modelVersion = "test-v1",
            logger = Logger("nlp-cache-test"),
        )
    }

    @AfterEach
    fun cleanup() {
        Files.deleteIfExists(tempDb)
    }

    /**
     * Чтение кэша для всего батча — ОДИН запрос, а не по одному на текст.
     * Поштучный findByHash открывает отдельную транзакцию на каждый текст, и
     * батч из 10 текстов стоил бы 10 обращений к БД ещё до вызова модели.
     */
    @Test
    fun `batch reads the cache with a single bulk lookup`() = runBlocking {
        val texts = (1..10).map { "текст $it" }

        caching.batchLemmatize(texts)

        verify(exactly = 1) { dao.findByHashes(any(), any()) }
    }

    /**
     * Полностью закэшированный батч не делает поштучных обращений вовсе: чтение —
     * один запрос, записи нет.
     *
     * На холодном батче поштучные findByHash всё же случаются, но не при чтении:
     * их делает insertLemmas, будучи upsert'ом — он проверяет наличие строки,
     * чтобы выбрать между вставкой и обновлением. Это фаза записи новых
     * результатов, и к оптимизации чтения она отношения не имеет.
     */
    @Test
    fun `fully cached batch performs no per-text lookups`() = runBlocking {
        val texts = (1..10).map { "текст $it" }
        caching.batchLemmatize(texts) // прогрев: тексты попадают в кэш
        clearMocks(dao, answers = false)

        caching.batchLemmatize(texts)

        verify(exactly = 1) { dao.findByHashes(any(), any()) }
        verify(exactly = 0) { dao.findByHash(any(), any()) }
    }

    /** Повторный батч берётся из кэша и не доходит до модели. */
    @Test
    fun `second batch is served from cache without calling the model`() = runBlocking {
        val texts = listOf("экология", "загрязнение")

        val first = caching.batchLemmatize(texts)
        val second = caching.batchLemmatize(texts)

        assertEquals(1, delegate.batchLemmatizeCalls, "cached batch must not reach the model")
        assertEquals(first, second, "cached result must equal the computed one")
    }

    /**
     * При частичном попадании модель спрашивают только о некэшированных текстах,
     * а порядок результатов соответствует порядку входа.
     */
    @Test
    fun `partial hit asks the model only for uncached texts and keeps order`() = runBlocking {
        caching.batchLemmatize(listOf("первый"))
        delegate.lemmatizeRequested.clear()

        val result = caching.batchLemmatize(listOf("первый", "второй", "третий"))

        assertEquals(
            listOf("второй", "третий"),
            delegate.lemmatizeRequested,
            "only uncached texts go to the model",
        )
        assertEquals(
            listOf(listOf("lemma_of_первый"), listOf("lemma_of_второй"), listOf("lemma_of_третий")),
            result,
            "results must stay aligned with the input order",
        )
    }

    /** Повторяющиеся тексты внутри батча не ломают раскладку результатов. */
    @Test
    fun `duplicate texts in one batch resolve to the same result`() = runBlocking {
        val result = caching.batchLemmatize(listOf("повтор", "другой", "повтор"))

        assertEquals(3, result.size)
        assertEquals(result[0], result[2], "identical texts must map to identical results")
        assertTrue(result[1] != result[0])
    }

    /** Пустой батч не обращается к модели. */
    @Test
    fun `empty batch does not call the model`() = runBlocking {
        val result = caching.batchLemmatize(emptyList())

        assertEquals(emptyList<List<String>>(), result)
        assertEquals(0, delegate.batchLemmatizeCalls)
    }

    /**
     * Пакетная тональность доходит до модели ОДНИМ вызовом.
     *
     * Поштучный scoreSentiment внутри пакетного метода свёл бы режим к N
     * обращениям к sidecar — ровно к тому, что он призван устранить.
     */
    @Test
    fun `batch sentiment reaches the model in one call`() = runBlocking {
        val texts = (1..10).map { "текст $it" }

        val result = caching.batchSentiment(texts)

        assertEquals(1, delegate.batchSentimentCalls, "batch must be one call to the model")
        assertEquals(0, delegate.scoreSentimentCalls, "no per-text calls in a batch path")
        assertEquals(10, result.size)
        assertEquals("NEGATIVE", result[0].label, "result must come from the batch path")
    }

    /** Повторная пакетная тональность берётся из кэша, модель не вызывается. */
    @Test
    fun `second batch sentiment is served from cache`() = runBlocking {
        val texts = listOf("экология", "загрязнение")

        val first = caching.batchSentiment(texts)
        val second = caching.batchSentiment(texts)

        assertEquals(1, delegate.batchSentimentCalls, "cached batch must not reach the model")
        assertEquals(first.map { it.label }, second.map { it.label })
    }

    /**
     * Распределение вероятностей переживает круг через кэш.
     *
     * Кэш обязан хранить вероятности наравне с меткой: иначе попадание вернуло бы
     * результат без распределения, и повторный прогон той же сессии считал бы оси
     * по долям меток.
     */
    @Test
    fun `probability distribution survives a cache round-trip`() = runBlocking {
        val texts = listOf("экология", "загрязнение")

        val computed = caching.batchSentiment(texts)
        val fromCache = caching.batchSentiment(texts)

        assertEquals(1, delegate.batchSentimentCalls, "второй батч обязан прийти из кэша")

        val fresh = computed[0].probabilities!!
        val cached = fromCache[0].probabilities!!
        assertEquals(0.7, fresh.negative, 1e-6, "распределение должно доходить от модели")
        // Сравниваем с допуском: в БД вероятности лежат как REAL (Float), поэтому
        // круг Double -> Float -> Double даёт 0.1 -> 0.10000000149. Для вероятностей,
        // округляемых моделью до 4 знаков, точности float с запасом.
        assertEquals(fresh.positive, cached.positive, 1e-6, "позитив обязан пережить кэш")
        assertEquals(fresh.neutral, cached.neutral, 1e-6, "нейтраль обязана пережить кэш")
        assertEquals(fresh.negative, cached.negative, 1e-6, "негатив обязан пережить кэш")
    }

    /** При частичном попадании в модель уходят только некэшированные тексты, порядок сохраняется. */
    @Test
    fun `partial sentiment hit asks the model only for uncached texts`() = runBlocking {
        caching.batchSentiment(listOf("первый"))
        delegate.sentimentRequested.clear()

        val result = caching.batchSentiment(listOf("первый", "второй", "третий"))

        assertEquals(listOf("второй", "третий"), delegate.sentimentRequested)
        assertEquals(3, result.size)
        assertTrue(result.all { it.label == "NEGATIVE" }, "cached and fresh must agree")
    }
}
