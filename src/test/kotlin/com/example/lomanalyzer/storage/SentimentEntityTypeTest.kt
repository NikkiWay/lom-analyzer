/*
 * НАЗНАЧЕНИЕ
 * Регрессионные тесты на разделение пространств идентификаторов в таблице
 * sentiment_result. До миграции V11 таблица имела ключ post_id с внешним ключом
 * на post(id), но код писал в неё и тональность комментариев: post и comment —
 * независимые автоинкрементные последовательности, поэтому первый же комментарий
 * (id=1) конфликтовал с тональностью поста id=1 и обрушивал стадию препроцессинга.
 *
 * ЧТО ВНУТРИ
 * Класс SentimentEntityTypeTest: временная БД с миграциями; проверки того, что
 * пост и комментарий с ОДИНАКОВЫМ id хранят независимые метки тональности и что
 * выборка карт по типу сущности не смешивает их между собой.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5, Exposed ORM + JDBC SQLite (временный файл БД, PRAGMA foreign_keys=ON).
 *
 * СВЯЗИ
 * SentimentResultDao, SentimentEntityType, миграция V11__sentiment_result_entity_type.sql.
 */
package com.example.lomanalyzer.storage

import com.example.lomanalyzer.storage.dao.AuthorDao
import com.example.lomanalyzer.storage.dao.CommentDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.dao.SentimentResultDao
import com.example.lomanalyzer.storage.dao.SessionDao
import com.example.lomanalyzer.storage.tables.SentimentEntityType
import com.example.lomanalyzer.storage.tables.SentimentResults
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.jetbrains.exposed.sql.selectAll
import java.nio.file.Files
import java.nio.file.Path

class SentimentEntityTypeTest {
    private lateinit var tempDb: Path
    private lateinit var db: Database
    private var sessionId = 0
    private var postId = 0
    private var commentId = 0

    @BeforeEach
    fun setup() {
        tempDb = Files.createTempFile("sentiment_entity_", ".db")
        Migrations.migrate(tempDb)
        db = Database.connect("jdbc:sqlite:${tempDb.toAbsolutePath()}", driver = "org.sqlite.JDBC")
        transaction(db) {
            (connection.connection as java.sql.Connection)
                .createStatement().execute("PRAGMA foreign_keys=ON")
        }

        val sessionDao = SessionDao(db)
        val authorDao = AuthorDao(db)
        val postDao = PostDao(db)
        val commentDao = CommentDao(db)

        sessionId = sessionDao.insert(name = "sentiment", topicQuery = "t", region = "synthetic")
        authorDao.insert(vkId = 777, followersCount = 10)
        postId = postDao.insert(
            sessionId = sessionId, vkId = 1, ownerId = 777, fromId = 777,
            publishedAt = 1_717_196_400L, text = "post text", window = "CURRENT",
        )
        commentId = commentDao.insert(
            sessionId = sessionId, postId = postId, vkId = 1, fromId = 777,
            text = "comment text", publishedAt = 1_717_196_400L,
        )
    }

    @AfterEach
    fun cleanup() {
        Files.deleteIfExists(tempDb)
    }

    /**
     * Пост и комментарий с совпадающими id хранят собственные метки тональности.
     * Без составного ключа (entity_type, entity_id) вставка второй строки нарушила
     * бы UNIQUE constraint и обрушила сессию на стадии препроцессинга.
     */
    @Test
    fun `post and comment sharing an id keep independent sentiment rows`() {
        val dao = SentimentResultDao(db)
        // Предусловие сценария: последовательности независимы и дают одинаковый id
        assertEquals(postId, commentId, "This regression only bites when the ids collide")

        dao.insert(SentimentEntityType.POST, postId, "NEGATIVE", 0.9f, "test_post")
        dao.insert(SentimentEntityType.COMMENT, commentId, "POSITIVE", 0.8f, "test_comment")

        val rows = transaction(db) { SentimentResults.selectAll().count() }
        assertEquals(2, rows, "Both rows must persist")

        val post = dao.findByEntity(SentimentEntityType.POST, postId)
        val comment = dao.findByEntity(SentimentEntityType.COMMENT, commentId)
        assertEquals("NEGATIVE", post?.get(SentimentResults.sentiment))
        assertEquals("POSITIVE", comment?.get(SentimentResults.sentiment))
    }

    /** Карта по типу сущности содержит только строки своего типа. */
    @Test
    fun `findAllAsMap is scoped to one entity type`() {
        val dao = SentimentResultDao(db)
        dao.insert(SentimentEntityType.POST, postId, "NEGATIVE", 0.9f, "test_post")
        dao.insert(SentimentEntityType.COMMENT, commentId, "POSITIVE", 0.8f, "test_comment")

        assertEquals(mapOf(postId to "NEGATIVE"), dao.findAllAsMap(SentimentEntityType.POST))
        assertEquals(mapOf(commentId to "POSITIVE"), dao.findAllAsMap(SentimentEntityType.COMMENT))
    }

    /** Выборка не находит запись чужого типа с тем же идентификатором. */
    @Test
    fun `lookup does not cross entity types`() {
        val dao = SentimentResultDao(db)
        dao.insert(SentimentEntityType.POST, postId, "NEGATIVE", 0.9f, "test_post")

        assertNull(dao.findByEntity(SentimentEntityType.COMMENT, commentId))
    }
}
