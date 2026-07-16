/*
 * НАЗНАЧЕНИЕ
 * Тесты слоя хранения: схема БД (Flyway-миграции), DAO для сессий, авторов и
 * постов, наличие обязательных индексов, обновление статуса и soft-delete сессий.
 * Подтверждают, что миграции создают корректную схему и DAO читают/пишут данные.
 *
 * ЧТО ВНУТРИ
 * Класс StorageTest на временной SQLite-БД (миграции Flyway, foreign_keys=ON):
 *  - V1 schema: ровно ожидаемый набор таблиц;
 *  - вставка/чтение AnalysisSession (значения по умолчанию: status=CREATED, nlpMode=FULL);
 *  - вставка/чтение Author;
 *  - вставка/чтение Post со ссылкой на сессию (FK);
 *  - наличие обязательных индексов idx_*;
 *  - обновление статуса сессии;
 *  - soft-delete: проставляется deleted_at, findAll исключает запись.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (@BeforeEach/@AfterEach, assert*). Exposed ORM + SQLite JDBC.
 * Flyway (Migrations.migrate) — применение схемы. Прямой SQL к sqlite_master —
 * проверка наличия таблиц и индексов.
 *
 * СВЯЗИ
 * Migrations, SessionDao, AuthorDao, PostDao, таблицы AnalysisSessions, Authors, Posts.
 */
package com.example.lomanalyzer.storage

import com.example.lomanalyzer.storage.dao.AuthorDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.dao.SessionDao
import com.example.lomanalyzer.storage.tables.AnalysisSessions
import com.example.lomanalyzer.storage.tables.Authors
import com.example.lomanalyzer.storage.tables.Posts
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Тесты схемы БД, индексов и DAO (сессии, авторы, посты). */
class StorageTest {
    /** Временный файл SQLite-БД. */
    private lateinit var tempDb: Path
    /** Подключение Exposed к временной БД. */
    private lateinit var db: Database

    /** Arrange: временная БД, миграции Flyway, подключение Exposed, foreign_keys=ON. */
    @BeforeEach
    fun setup() {
        tempDb = Files.createTempFile("lom_test_", ".db")
        // Run Flyway migration
        // Применяем миграции Flyway — создаём схему БД
        Migrations.migrate(tempDb)
        // Connect Exposed
        // Подключаем Exposed к созданной БД
        db = Database.connect("jdbc:sqlite:${tempDb.toAbsolutePath()}", driver = "org.sqlite.JDBC")
        transaction(db) {
            (connection.connection as java.sql.Connection).createStatement().execute("PRAGMA foreign_keys=ON")
        }
    }

    /** Teardown: удаление временной БД. */
    @AfterEach
    fun teardown() {
        Files.deleteIfExists(tempDb)
    }

    /**
     * Проверка полноты схемы: после миграций в БД присутствует ровно ожидаемый
     * набор пользовательских таблиц (служебные flyway_ и sqlite_ исключены).
     */
    @Test
    fun `migrated schema contains exactly the expected tables`() {
        // Читаем имена пользовательских таблиц из системного каталога SQLite
        val tables = transaction(db) {
            val result = mutableListOf<String>()
            val sql = """SELECT name FROM sqlite_master WHERE type='table'
                AND name NOT LIKE 'flyway%' AND name NOT LIKE 'sqlite%' ORDER BY name"""
            exec(sql) { rs ->
                while (rs.next()) {
                    result.add(rs.getString("name"))
                }
            }
            result
        }
        // Полный ожидаемый список таблиц схемы (в алфавитном порядке)
        val expected = listOf(
            "analysis_session", "author", "author_role",
            "bootstrap_interval", "collection_checkpoint", "comment", "community",
            "composite_score", "dedup_group", "lom_score", "nlp_result",
            "pipeline_checkpoint", "post", "processed_text",
            "sentiment_result", "session_author", "session_community",
            "session_event", "session_metrics", "session_quality_indicator",
            "session_threshold",
        )
        assertEquals(expected, tables)
    }

    /**
     * SessionDao.insert/findById: запись читается, сохранены имя и тема, а также
     * значения по умолчанию из схемы (status=CREATED, nlpMode=FULL).
     */
    @Test
    fun `insert and read AnalysisSession`() {
        val dao = SessionDao(db)
        val id = dao.insert(name = "Test Session", topicQuery = "ecology")
        val row = dao.findById(id)
        assertNotNull(row)
        assertEquals("Test Session", row!![AnalysisSessions.name])
        assertEquals("ecology", row[AnalysisSessions.topicQuery])
        // Значения по умолчанию из схемы
        assertEquals("CREATED", row[AnalysisSessions.status])
        assertEquals("FULL", row[AnalysisSessions.nlpMode])
    }

    /**
     * AuthorDao.insert/findById: автор читается с сохранёнными полями (vkId, имя,
     * число подписчиков); isClosed по умолчанию false.
     */
    @Test
    fun `insert and read Author`() {
        val dao = AuthorDao(db)
        val id = dao.insert(vkId = 123456, firstName = "Ivan", lastName = "Petrov", followersCount = 1500)
        val row = dao.findById(id)
        assertNotNull(row)
        assertEquals(123456, row!![Authors.vkId])
        assertEquals("Ivan", row[Authors.firstName])
        assertEquals(1500, row[Authors.followersCount])
        // По умолчанию профиль не закрыт
        assertEquals(false, row[Authors.isClosed])
    }

    /**
     * PostDao.insert/findById с внешним ключом на сессию: пост привязан к ранее
     * созданной сессии, сохранены поля окна (window), длины собственного текста и vkId.
     */
    @Test
    fun `insert and read Post with session FK`() {
        val sessionDao = SessionDao(db)
        // Сначала создаём сессию — пост ссылается на неё по FK
        val sessionId = sessionDao.insert(name = "S1", topicQuery = "politics")

        val postDao = PostDao(db)
        val postId = postDao.insert(
            sessionId = sessionId,
            vkId = 999,
            ownerId = -100,
            fromId = 123,
            publishedAt = 1717196400000L,
            text = "Test post content",
            window = "CURRENT",
            ownTextLength = 17,
        )
        val row = postDao.findById(postId)
        assertNotNull(row)
        // Временное окно поста (текущее/фоновое)
        assertEquals("CURRENT", row!![Posts.window])
        assertEquals(17, row[Posts.ownTextLength])
        assertEquals(999, row[Posts.vkId])
    }

    /**
     * Проверка наличия обязательных индексов idx_*: они критичны для
     * производительности запросов пайплайна (выборки постов/комментов/скоров по сессии).
     */
    @Test
    fun `required indexes exist`() {
        // Читаем имена индексов idx_* из системного каталога
        val indexes = transaction(db) {
            val result = mutableListOf<String>()
            exec("SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'idx_%' ORDER BY name") { rs ->
                while (rs.next()) {
                    result.add(rs.getString("name"))
                }
            }
            result
        }
        // Каждый обязательный индекс должен присутствовать
        assertTrue(indexes.contains("idx_post_session_published"))
        assertTrue(indexes.contains("idx_post_session_from"))
        assertTrue(indexes.contains("idx_post_session_relevant"))
        assertTrue(indexes.contains("idx_post_session_window"))
        assertTrue(indexes.contains("idx_post_session_holiday"))
        assertTrue(indexes.contains("idx_lom_score_session"))
        assertTrue(indexes.contains("idx_checkpoint_session_ep"))
        assertTrue(indexes.contains("idx_session_deleted_at"))
        assertTrue(indexes.contains("idx_comment_session_post"))
        assertTrue(indexes.contains("idx_comment_session_from"))
        assertTrue(indexes.contains("idx_session_event_session"))
        assertTrue(indexes.contains("idx_checkpoint_session"))
    }

    /** SessionDao.updateStatus меняет статус сессии; чтение подтверждает COLLECTING. */
    @Test
    fun `session status update works`() {
        val dao = SessionDao(db)
        val id = dao.insert(name = "S2", topicQuery = "q")
        dao.updateStatus(id, "COLLECTING")
        val row = dao.findById(id)
        assertEquals("COLLECTING", row!![AnalysisSessions.status])
    }

    /**
     * softDelete проставляет метку deleted_at (запись физически остаётся), а
     * findAll исключает мягко удалённые сессии (список становится пустым).
     */
    @Test
    fun `soft delete sets deleted_at`() {
        val dao = SessionDao(db)
        val id = dao.insert(name = "S3", topicQuery = "q")
        dao.softDelete(id)
        val row = dao.findById(id)
        // Метка удаления проставлена
        assertNotNull(row!![AnalysisSessions.deletedAt])
        // findAll excludes soft-deleted
        // Активный список не содержит мягко удалённую сессию
        assertTrue(dao.findAll().isEmpty())
    }
}
