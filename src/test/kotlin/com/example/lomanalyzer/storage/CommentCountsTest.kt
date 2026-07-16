/*
 * НАЗНАЧЕНИЕ
 * Тесты группированного подсчёта комментариев по постам сессии. Заменяет вызов
 * countByPost в цикле (по запросу на каждый пост, каждый — полное сканирование
 * таблицы comment), поэтому результат обязан совпадать с поштучным подсчётом.
 *
 * ЧТО ВНУТРИ
 * Класс CommentCountsTest: совпадение с поштучным countByPost, отсутствие постов
 * без комментариев в карте, изоляция сессий друг от друга.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5; Exposed ORM + JDBC SQLite (временная БД с миграциями Flyway).
 *
 * СВЯЗИ
 * CommentDao.countBySessionGroupedByPost / countByPost (storage/dao).
 */
package com.example.lomanalyzer.storage

import com.example.lomanalyzer.storage.dao.AuthorDao
import com.example.lomanalyzer.storage.dao.CommentDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.dao.SessionDao
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class CommentCountsTest {
    private lateinit var tempDb: Path
    private lateinit var db: Database
    private lateinit var postDao: PostDao
    private lateinit var commentDao: CommentDao
    private var sessionId = 0
    private var vkCommentId = 0

    @BeforeEach
    fun setup() {
        tempDb = Files.createTempFile("comment_counts_", ".db")
        Migrations.migrate(tempDb)
        db = Database.connect("jdbc:sqlite:${tempDb.toAbsolutePath()}", driver = "org.sqlite.JDBC")
        postDao = PostDao(db)
        commentDao = CommentDao(db)
        sessionId = SessionDao(db).insert(name = "counts", topicQuery = "t", region = "synthetic")
        AuthorDao(db).insert(vkId = 777, followersCount = 10)
    }

    @AfterEach
    fun cleanup() {
        Files.deleteIfExists(tempDb)
    }

    private fun newPost(session: Int, vkId: Int): Int = postDao.insert(
        sessionId = session, vkId = vkId, ownerId = -1, fromId = 777,
        publishedAt = 1_717_196_400L, text = "post $vkId", window = "CURRENT",
    )

    private fun addComments(session: Int, postId: Int, count: Int) {
        repeat(count) {
            commentDao.insert(
                sessionId = session, postId = postId, vkId = ++vkCommentId,
                fromId = 777, text = "c", publishedAt = 1_717_196_400L,
            )
        }
    }

    /**
     * Группированный подсчёт совпадает с поштучным для каждого поста — это и есть
     * условие, при котором замена N запросов одним ничего не меняет по существу.
     */
    @Test
    fun `grouped counts match per-post counts`() {
        val quiet = newPost(sessionId, 1)
        val busy = newPost(sessionId, 2)
        val single = newPost(sessionId, 3)
        addComments(sessionId, quiet, 2)
        addComments(sessionId, busy, 7)
        addComments(sessionId, single, 1)

        val grouped = commentDao.countBySessionGroupedByPost(sessionId)

        for (postId in listOf(quiet, busy, single)) {
            assertEquals(
                commentDao.countByPost(postId),
                grouped[postId],
                "grouped count must equal the per-post count for post $postId",
            )
        }
        assertEquals(mapOf(quiet to 2L, busy to 7L, single to 1L), grouped)
    }

    /** Пост без комментариев в карту не попадает — вызывающий трактует это как 0. */
    @Test
    fun `posts without comments are absent from the map`() {
        val withComments = newPost(sessionId, 1)
        val withoutComments = newPost(sessionId, 2)
        addComments(sessionId, withComments, 3)

        val grouped = commentDao.countBySessionGroupedByPost(sessionId)

        assertFalse(grouped.containsKey(withoutComments), "post with no comments must be absent")
        assertEquals(0L, grouped[withoutComments] ?: 0L)
        assertEquals(0L, commentDao.countByPost(withoutComments), "and it really has none")
    }

    /** Карта ограничена своей сессией: чужие комментарии в неё не попадают. */
    @Test
    fun `counts are scoped to the requested session`() {
        val otherSession = SessionDao(db).insert(name = "other", topicQuery = "t", region = "synthetic")
        val mine = newPost(sessionId, 1)
        val theirs = newPost(otherSession, 2)
        addComments(sessionId, mine, 4)
        addComments(otherSession, theirs, 9)

        val grouped = commentDao.countBySessionGroupedByPost(sessionId)

        assertEquals(mapOf(mine to 4L), grouped)
        assertFalse(grouped.containsKey(theirs), "another session's post must not appear")
    }

    /** Пустая сессия даёт пустую карту, а не ошибку. */
    @Test
    fun `empty session yields an empty map`() {
        assertEquals(emptyMap<Int, Long>(), commentDao.countBySessionGroupedByPost(sessionId))
    }
}
