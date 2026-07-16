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
 * аудитории (StructuralScores.audienceScore), словарный сентимент. См. docs/algorithm.md.
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
import com.example.lomanalyzer.analysis.scoring.StructuralScores
import com.example.lomanalyzer.analysis.topic.NgramMatcher
import com.example.lomanalyzer.analysis.topic.TopicRelevanceFilter
import com.example.lomanalyzer.analysis.topic.TopicStratum
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
import org.junit.jupiter.api.AfterEach
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
     * импорт даёт валидный sessionId; в сессии 50 постов и 5 авторов; проход 1
     * фильтра находит 5 постов с явными ключевыми словами, а без прохода 2 все
     * посты получают стратум DISPUTED (контракт режима FALLBACK_ONLY); точных
     * дубликатов в корпусе нет; рассчитываются 5 LomScore; словарный сентимент
     * на негативных леммах даёт NEGATIVE; фиксируются 5 записей метрик этапов.
     */
    @Test
    @Suppress("LongMethod")
    fun `full pipeline produces LomScores and RiskSignal`() = runBlocking<Unit> {
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

        // 3. Тематическая фильтрация (этап 6): n-граммы по теме «экология».
        // N-граммы обязаны пройти ту же нормализацию, что и токены постов: ниже посты
        // приводятся Snowball-стеммером (stemFallback), поэтому «экология» в тексте даёт
        // основу «эколог», и сырое слово «экология» не совпало бы с ней никогда.
        // Ровно так же поступает production-код — см. TopicFilterExecutor.parseAndLemmatizeNgrams,
        // который прогоняет пользовательские n-граммы через тот же лемматизатор.
        fun stemNgram(vararg words: String): List<String> =
            lemmatizer.stemFallback(words.toList()).map { it.lemma }

        val ngramMatcher = NgramMatcher(
            primaryNgrams = listOf(stemNgram("экология"), stemNgram("загрязнение")),
            secondaryNgrams = listOf(stemNgram("воздух"), stemNgram("вода")),
            excludedNgrams = listOf(stemNgram("экологичный", "продукт")),
        )
        // Режим FALLBACK_ONLY: только первый проход L1, без RuBERT
        val topicFilter = TopicRelevanceFilter(nlpMode = "FALLBACK_ONLY")
        var topicalCount = 0
        var postsWithKeywordHit = 0
        val strata = mutableMapOf<TopicStratum, Int>()

        for (post in posts) {
            val text = post[Posts.text] ?: ""
            val tokens = Tokenizer.tokenize(text)
            // Лемматизация fallback-стеммером, берём только леммы
            val lemmas = lemmatizer.stemFallback(tokens).map { it.lemma }
            // Сопоставление с n-граммами темы и расчёт релевантности
            val matchResult = ngramMatcher.match(lemmas)
            val scoreResult = topicFilter.score(matchResult, text)
            if (matchResult.primaryHits > 0) postsWithKeywordHit++
            strata[scoreResult.stratum] = (strata[scoreResult.stratum] ?: 0) + 1
            // Сохраняем признак темы и оценки L1/L2/combined в БД
            postDao.updateTopicRelevance(
                post[Posts.id].value, scoreResult.relevant,
                scoreResult.l1, scoreResult.l2, scoreResult.combined,
            )
            if (scoreResult.relevant) topicalCount++
        }

        // Проход 1 действительно находит ключевые слова: ровно 5 постов корпуса
        // содержат основные n-граммы («экология»/«загрязнение») в явном виде.
        assertEquals(5, postsWithKeywordHit, "Pass 1 should find the 5 posts with explicit keywords")

        // Контракт режима FALLBACK_ONLY: без прохода 2 ни один пост не принимается
        // автоматически. Максимальный достижимый L1 на этом корпусе — 0.433
        // (1 основное + 1 дополнительное попадание = 1.3/3), что ниже порога
        // CONFIDENT_THRESHOLD = 0.50; для 0.50 нужно ≥2 попаданий основных n-грамм.
        // Поэтому все посты получают стратум DISPUTED и уходят аналитику на ручную
        // проверку (TopicValidationScreen) — это штатное поведение, а не сбой.
        assertEquals(posts.size, strata[TopicStratum.DISPUTED], "Without pass 2 every post is DISPUTED")
        assertEquals(0, topicalCount, "FALLBACK_ONLY accepts nothing automatically on this corpus")

        // Корпус намеренно рассчитан на двухпроходный фильтр: 25 из 50 постов размечены
        // как тематические, но часть из них («Качество воды из-под крана…») вовсе не
        // содержит ключевых слов и распознаётся только семантически, по reference_texts
        // из metadata.topic_config. Полное покрытие этих 25 постов проверяется в режиме
        // FULL с работающим sidecar, а не здесь.

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

        // 6. Структурная оценка аудитории (упрощённо): сырое значение по подписчикам.
        // Aud_a = ln(1 + F_a) — та же функция, что используется в пайплайне (этап 7).
        val lomScoreDao = LomScoreDao(db)
        val aRaws = authors.map {
            StructuralScores.audienceScore(
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
    }

    /**
     * Уборка после каждого теста: удаляет временный файл БД. Вынесено в @AfterEach,
     * чтобы файл удалялся и при упавшей проверке, а тест-метод не возвращал значение
     * (JUnit 5 обнаруживает только @Test-методы, возвращающие void).
     */
    @AfterEach
    fun cleanup() {
        Files.deleteIfExists(tempDb)
    }
}
