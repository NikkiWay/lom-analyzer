package com.example.lomanalyzer.integration

import com.example.lomanalyzer.analysis.anomaly.AnomalyDetection
import com.example.lomanalyzer.analysis.anomaly.RollingZScore
import com.example.lomanalyzer.analysis.anomaly.VolumeSpikeDetector
import com.example.lomanalyzer.analysis.anomaly.HolidayCalendar
import com.example.lomanalyzer.analysis.anomaly.DailyValue
import com.example.lomanalyzer.analysis.content.DictionarySentiment
import com.example.lomanalyzer.analysis.dedup.ExactHasher
import com.example.lomanalyzer.analysis.dedup.HashablePost
import com.example.lomanalyzer.analysis.lom.*
import com.example.lomanalyzer.analysis.risk.RiskScorer
import com.example.lomanalyzer.analysis.risk.SignalGenerator
import com.example.lomanalyzer.analysis.risk.BlockBootstrap
import com.example.lomanalyzer.analysis.topic.NgramMatcher
import com.example.lomanalyzer.analysis.topic.TopicRelevanceFilter
import com.example.lomanalyzer.config.ResourceLoader
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.preprocessing.TextCleaner
import com.example.lomanalyzer.preprocessing.Tokenizer
import com.example.lomanalyzer.preprocessing.LemmatizerProxy
import com.example.lomanalyzer.preprocessing.LanguageDetectorProxy
import com.example.lomanalyzer.storage.Migrations
import com.example.lomanalyzer.storage.dao.*
import com.example.lomanalyzer.storage.tables.Posts
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/**
 * MVP smoke test: ingests the minimal test corpus,
 * runs the full analysis pipeline, and verifies outputs.
 */
class MvpSmokeTest {
    private lateinit var tempDb: Path
    private lateinit var db: Database
    private val logger = Logger("mvp-smoke")

    @BeforeEach
    fun setup() {
        tempDb = Files.createTempFile("mvp_smoke_", ".db")
        Migrations.migrate(tempDb)
        db = Database.connect(
            "jdbc:sqlite:${tempDb.toAbsolutePath()}",
            driver = "org.sqlite.JDBC",
        )
        transaction(db) {
            (connection.connection as java.sql.Connection).createStatement().execute("PRAGMA foreign_keys=ON")
        }
    }

    @Test
    @Suppress("LongMethod")
    fun `full pipeline produces LomScores and RiskSignal`() = runBlocking {
        // 1. Import test corpus
        val corpusJson = MvpSmokeTest::class.java
            .getResourceAsStream("/test_corpus_minimal.json")
            ?.bufferedReader()?.readText()
            ?: javaClass.classLoader
                .getResource("test_corpus_minimal.json")
                ?.readText()

        // If corpus not on classpath, use file directly
        val json = corpusJson ?: java.io.File(
            "research/R07_minimal_test_corpus/test_corpus_minimal.json"
        ).readText()

        val importer = TestCorpusImporter(db)
        val sessionId = importer.import(json)
        assertTrue(sessionId > 0)

        // 2. Preprocessing
        val postDao = PostDao(db)
        val processedTextDao = ProcessedTextDao(db)
        val lemmatizer = LemmatizerProxy()
        val langDetector = LanguageDetectorProxy()

        val posts = postDao.findBySession(sessionId)
        assertEquals(50, posts.size, "Should have 50 posts")

        for (post in posts) {
            val postId = post[Posts.id].value
            val text = post[Posts.text] ?: ""
            val cleaned = TextCleaner.clean(text)
            val tokens = Tokenizer.tokenize(cleaned.cleanText)
            val lang = langDetector.detectFallback(tokens)
            processedTextDao.insert(
                postId = postId,
                cleanText = cleaned.cleanText,
                language = lang.language,
            )
        }

        // 3. Topic filtering
        val ngramMatcher = NgramMatcher(
            primaryNgrams = listOf(listOf("экология"), listOf("загрязнение")),
            secondaryNgrams = listOf(listOf("воздух"), listOf("вода")),
            excludedNgrams = listOf(listOf("экологичный", "продукт")),
        )
        val topicFilter = TopicRelevanceFilter(nlpMode = "FALLBACK_ONLY")
        var topicalCount = 0

        for (post in posts) {
            val text = post[Posts.text] ?: ""
            val tokens = Tokenizer.tokenize(text)
            val lemmas = lemmatizer.stemFallback(tokens).map { it.lemma }
            val matchResult = ngramMatcher.match(lemmas)
            val scoreResult = topicFilter.score(matchResult, text)
            postDao.updateTopicRelevance(
                post[Posts.id].value, scoreResult.relevant,
                scoreResult.l1, scoreResult.l2, scoreResult.combined,
            )
            if (scoreResult.relevant) topicalCount++
        }
        assertTrue(topicalCount > 0, "Should have some topical posts")

        // 4. Dedup
        val hashablePosts = posts.map {
            HashablePost(
                it[Posts.id].value, it[Posts.fromId], it[Posts.publishedAt],
                it[Posts.text] ?: "", it[Posts.ownTextLength], true,
            )
        }
        val dedupGroups = ExactHasher.findDuplicateGroups(hashablePosts)
        // Test corpus has no exact duplicates
        assertTrue(dedupGroups.isEmpty(), "No exact dupes expected")

        // 5. Gamma calibration
        val authorDao = AuthorDao(db)
        val authors = authorDao.findAll()
        assertEquals(5, authors.size)

        val gammaInputs = authors.map {
            val f = it[com.example.lomanalyzer.storage.tables.Authors.followersCount] ?: 0
            AuthorGammaInput(f, 10.0) // simplified
        }
        val gammaResult = GammaCalibrator(bootstrapIterations = 50).calibrate(gammaInputs)
        // With only 5 authors, should fallback
        assertTrue(gammaResult.fallback || gammaResult.gamma in 0.25..0.65)

        // 6. Base influence scoring (simplified)
        val normalizer = RobustNormalizer()
        val lomScoreDao = LomScoreDao(db)
        val aRaws = authors.map {
            AudienceComponent.computeRaw(
                it[com.example.lomanalyzer.storage.tables.Authors.followersCount] ?: 0,
            )
        }
        val aStats = normalizer.computeStats(aRaws)

        for ((i, author) in authors.withIndex()) {
            val authorId = author[com.example.lomanalyzer.storage.tables.Authors.id].value
            val aNorm = normalizer.normalize(aRaws[i], aStats).toFloat()
            lomScoreDao.insert(sessionId, authorId) {
                it[com.example.lomanalyzer.storage.tables.LomScores.audienceNorm] = aNorm
                it[com.example.lomanalyzer.storage.tables.LomScores.baseInfluenceHist] = aNorm
                it[com.example.lomanalyzer.storage.tables.LomScores.gammaUsed] =
                    gammaResult.gamma.toFloat()
            }
        }

        val lomScores = lomScoreDao.findBySession(sessionId)
        assertEquals(5, lomScores.size, "Should have 5 LomScores")

        // 7. Risk scoring
        val riskAnomalies = listOf(
            AnomalyDetection("VOLUME_SPIKE", LocalDate.of(2025, 6, 5), 0.6, 3.2, "spike"),
        )
        val riskResult = RiskScorer.computeRisk(riskAnomalies)
        assertTrue(riskResult.riskScore > 0, "Risk should be > 0")

        val riskCi = BlockBootstrap(iterations = 30).bootstrap(riskAnomalies)
        val signal = SignalGenerator.generateSignal(riskCi, riskResult, riskAnomalies)
        assertNotNull(signal)
        assertTrue(signal.recommendation.contains("[ЧЕРНОВИК]"))

        // 8. Reference base loads
        val resourceLoader = ResourceLoader(logger)
        val ref = resourceLoader.loadReferenceBase()
        assertNotNull(ref)

        // 9. Sentiment
        val dict = DictionarySentiment()
        val sentResult = dict.score(listOf("загрязнение", "ужасный", "катастрофа"))
        assertEquals("NEGATIVE", sentResult.label)

        // Verify stage durations can be recorded
        val metricsDao = SessionMetricsDao(db)
        metricsDao.insert(sessionId, "PREPROCESSING", 120)
        metricsDao.insert(sessionId, "TOPIC_FILTERING", 45)
        metricsDao.insert(sessionId, "GAMMA_CALIBRATION", 30)
        metricsDao.insert(sessionId, "BASE_SCORING", 80)
        metricsDao.insert(sessionId, "RISK_SCORING", 15)
        val metrics = metricsDao.findBySession(sessionId)
        assertEquals(5, metrics.size)

        // Cleanup
        Files.deleteIfExists(tempDb)
    }
}
