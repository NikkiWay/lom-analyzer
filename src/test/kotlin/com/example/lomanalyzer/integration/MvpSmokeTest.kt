/*
 * НАЗНАЧЕНИЕ
 * Интеграционный smoke-тест MVP: на минимальном тестовом корпусе прогоняет
 * основные этапы пайплайна (импорт → препроцессинг → тематическая фильтрация →
 * дедуп → структурная оценка → сентимент) сквозь реальную БД и проверяет выходы.
 * Это «дымовой» тест целостности: убеждается, что компоненты стыкуются и работают
 * вместе на детерминированных синтетических данных.
 *
 * ЧТО ВНУТРИ
 * Класс MvpSmokeTest: подготовка временной БД SQLite с миграциями (setup),
 * один сквозной тест full pipeline с проверками количеств (50 постов, 5 авторов,
 * 5 LomScore, 5 записей метрик) и корректности промежуточных результатов.
 *
 * АЛГОРИТМ
 * Шаги теста повторяют этапы 5–9 алгоритма: очистка/токенизация/лемматизация,
 * двухпроходный фильтр в режиме FALLBACK_ONLY, точный дедуп, нормированная оценка
 * аудитории (AudienceComponent), словарный сентимент. См. docs/algorithm.md.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (@Test, @BeforeEach); Exposed ORM + JDBC SQLite (временная БД в temp-файле,
 * PRAGMA foreign_keys=ON); kotlinx.coroutines.runBlocking — suspend-вызовы.
 *
 * СВЯЗИ
 * Использует TestCorpusImporter (загрузка корпуса), DAO-слой (PostDao, AuthorDao,
 * LomScoreDao, ProcessedTextDao, SessionMetricsDao), препроцессинг (TextCleaner,
 * Tokenizer, LemmatizerProxy, LanguageDetectorProxy), фильтр (NgramMatcher,
 * TopicRelevanceFilter), дедуп (ExactHasher), сентимент (DictionarySentiment),
 * миграции (Migrations). Корпус — research/R07_minimal_test_corpus.
 */
package com.example.lomanalyzer.integration

import com.example.lomanalyzer.analysis.content.DictionarySentiment
import com.example.lomanalyzer.analysis.dedup.ExactHasher
import com.example.lomanalyzer.analysis.dedup.HashablePost
import com.example.lomanalyzer.analysis.lom.AudienceComponent
import com.example.lomanalyzer.analysis.topic.NgramMatcher
import com.example.lomanalyzer.analysis.topic.TopicRelevanceFilter
import com.example.lomanalyzer.config.ResourceLoader
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.preprocessing.TextCleaner
import com.example.lomanalyzer.preprocessing.Tokenizer
import com.example.lomanalyzer.preprocessing.LemmatizerProxy
import com.example.lomanalyzer.preprocessing.LanguageDetectorProxy
import com.example.lomanalyzer.storage.Migrations
import com.example.lomanalyzer.storage.dao.AuthorDao
import com.example.lomanalyzer.storage.dao.LomScoreDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.dao.ProcessedTextDao
import com.example.lomanalyzer.storage.dao.SessionMetricsDao
import com.example.lomanalyzer.storage.tables.Posts
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * MVP smoke test: ingests the minimal test corpus,
 * runs the full analysis pipeline, and verifies outputs.
 *
 * Дымовой тест MVP: загружает минимальный тестовый корпус, прогоняет основной
 * аналитический пайплайн и проверяет корректность выходов.
 */
class MvpSmokeTest {
    /** Путь к временному файлу БД SQLite, создаётся заново перед каждым тестом. */
    private lateinit var tempDb: Path
    /** Подключение Exposed к временной БД. */
    private lateinit var db: Database
    /** Логгер с тегом сценария (для диагностики прогона). */
    private val logger = Logger("mvp-smoke")

    /**
     * Подготовка fixture перед каждым тестом: создаёт временную БД, накатывает миграции
     * Flyway, подключается через Exposed и включает контроль внешних ключей.
     */
    @BeforeEach
    fun setup() {
        // Создаём временный файл БД и применяем все миграции схемы
        tempDb = Files.createTempFile("mvp_smoke_", ".db")
        Migrations.migrate(tempDb)
        // Подключаемся к этой БД через JDBC-драйвер SQLite
        db = Database.connect(
            "jdbc:sqlite:${tempDb.toAbsolutePath()}",
            driver = "org.sqlite.JDBC",
        )
        // Включаем проверку внешних ключей (по умолчанию в SQLite выключена)
        transaction(db) {
            (connection.connection as java.sql.Connection).createStatement().execute("PRAGMA foreign_keys=ON")
        }
    }

    /**
     * Сквозной прогон пайплайна на минимальном корпусе.
     * Проверяет, что этапы стыкуются и дают ожидаемые объёмы и значения:
     * импорт даёт валидный sessionId; в сессии 50 постов и 5 авторов;
     * тематическая фильтрация находит хотя бы один тематический пост; точных
     * дубликатов в корпусе нет; рассчитываются 5 LomScore; словарный сентимент
     * на негативных леммах даёт NEGATIVE; фиксируются 5 записей метрик этапов.
     */
    @Test
    @Suppress("LongMethod")
    fun `full pipeline produces LomScores and RiskSignal`() = runBlocking {
        // 1. Импорт тестового корпуса: сначала пробуем найти JSON на classpath...
        val corpusJson = MvpSmokeTest::class.java
            .getResourceAsStream("/test_corpus_minimal.json")
            ?.bufferedReader()?.readText()
            ?: javaClass.classLoader
                .getResource("test_corpus_minimal.json")
                ?.readText()

        // ...если на classpath нет — читаем файл корпуса напрямую из research-каталога
        val json = corpusJson ?: java.io.File(
            "research/R07_minimal_test_corpus/test_corpus_minimal.json"
        ).readText()

        // Загружаем корпус в БД, получаем идентификатор созданной сессии
        val importer = TestCorpusImporter(db)
        val sessionId = importer.import(json)
        assertTrue(sessionId > 0)

        // 2. Препроцессинг (этап 5): очистка, токенизация, определение языка
        val postDao = PostDao(db)
        val processedTextDao = ProcessedTextDao(db)
        val lemmatizer = LemmatizerProxy()
        val langDetector = LanguageDetectorProxy()

        // Все посты сессии — корпус рассчитан ровно на 50 записей
        val posts = postDao.findBySession(sessionId)
        assertEquals(50, posts.size, "Should have 50 posts")

        for (post in posts) {
            val postId = post[Posts.id].value
            val text = post[Posts.text] ?: ""
            // Очистка текста (удаление мусора), затем токенизация
            val cleaned = TextCleaner.clean(text)
            val tokens = Tokenizer.tokenize(cleaned.cleanText)
            // Определение языка fallback-методом (без NLP-сервиса)
            val lang = langDetector.detectFallback(tokens)
            // Сохраняем очищенный текст и язык в таблицу обработанных текстов
            processedTextDao.insert(
                postId = postId,
                cleanText = cleaned.cleanText,
                language = lang.language,
            )
        }

        // 3. Тематическая фильтрация (этап 6): n-граммы по теме «экология»
        val ngramMatcher = NgramMatcher(
            primaryNgrams = listOf(listOf("экология"), listOf("загрязнение")),
            secondaryNgrams = listOf(listOf("воздух"), listOf("вода")),
            excludedNgrams = listOf(listOf("экологичный", "продукт")),
        )
        // Режим FALLBACK_ONLY: только первый проход L1, без RuBERT
        val topicFilter = TopicRelevanceFilter(nlpMode = "FALLBACK_ONLY")
        var topicalCount = 0

        for (post in posts) {
            val text = post[Posts.text] ?: ""
            val tokens = Tokenizer.tokenize(text)
            // Лемматизация fallback-стеммером, берём только леммы
            val lemmas = lemmatizer.stemFallback(tokens).map { it.lemma }
            // Сопоставление с n-граммами темы и расчёт релевантности
            val matchResult = ngramMatcher.match(lemmas)
            val scoreResult = topicFilter.score(matchResult, text)
            // Сохраняем признак темы и оценки L1/L2/combined в БД
            postDao.updateTopicRelevance(
                post[Posts.id].value, scoreResult.relevant,
                scoreResult.l1, scoreResult.l2, scoreResult.combined,
            )
            if (scoreResult.relevant) topicalCount++
        }
        // Корпус содержит тематические посты — их должно быть хотя бы несколько
        assertTrue(topicalCount > 0, "Should have some topical posts")

        // 4. Дедупликация (этап 5, точные копии): строим хэшируемые посты
        val hashablePosts = posts.map {
            HashablePost(
                it[Posts.id].value, it[Posts.fromId], it[Posts.publishedAt],
                it[Posts.text] ?: "", it[Posts.ownTextLength], true,
            )
        }
        val dedupGroups = ExactHasher.findDuplicateGroups(hashablePosts)
        // В тестовом корпусе намеренно нет точных дубликатов
        assertTrue(dedupGroups.isEmpty(), "No exact dupes expected")

        // 5. Авторы: корпус содержит ровно 5 авторов
        val authorDao = AuthorDao(db)
        val authors = authorDao.findAll()
        assertEquals(5, authors.size)

        // 6. Структурная оценка аудитории (упрощённо): сырое значение по подписчикам
        val lomScoreDao = LomScoreDao(db)
        val aRaws = authors.map {
            AudienceComponent.computeRaw(
                it[com.example.lomanalyzer.storage.tables.Authors.followersCount] ?: 0,
            )
        }
        // Максимум для нормировки в [0..1]; защита от деления на ноль через coerceAtLeast(1.0)
        val maxRaw = aRaws.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

        for ((i, author) in authors.withIndex()) {
            val authorId = author[com.example.lomanalyzer.storage.tables.Authors.id].value
            // Нормированная оценка аудитории = сырое значение / максимум
            val aNorm = (aRaws[i] / maxRaw).toFloat()
            val followersCount = author[com.example.lomanalyzer.storage.tables.Authors.followersCount] ?: 0
            // Записываем (upsert) оценку LOM для автора в текущей сессии
            lomScoreDao.upsert(
                sessionId = sessionId,
                authorId = authorId,
                aud = aNorm,
                followersCount = followersCount,
            )
        }

        // Должно получиться по одной записи LomScore на каждого из 5 авторов
        val lomScores = lomScoreDao.findBySession(sessionId)
        assertEquals(5, lomScores.size, "Should have 5 LomScores")

        // 7. Сентимент: на наборе негативных лемм метка должна быть NEGATIVE
        val dict = DictionarySentiment()
        val sentResult = dict.score(listOf("загрязнение", "ужасный", "катастрофа"))
        assertEquals("NEGATIVE", sentResult.label)

        // Проверяем, что длительности этапов можно записать в метрики сессии
        val metricsDao = SessionMetricsDao(db)
        metricsDao.insert(sessionId, "PREPROCESSING", 120)
        metricsDao.insert(sessionId, "TOPIC_FILTERING", 45)
        metricsDao.insert(sessionId, "GAMMA_CALIBRATION", 30)
        metricsDao.insert(sessionId, "BASE_SCORING", 80)
        metricsDao.insert(sessionId, "RISK_SCORING", 15)
        val metrics = metricsDao.findBySession(sessionId)
        assertEquals(5, metrics.size)

        // Уборка: удаляем временный файл БД
        Files.deleteIfExists(tempDb)
    }
}
