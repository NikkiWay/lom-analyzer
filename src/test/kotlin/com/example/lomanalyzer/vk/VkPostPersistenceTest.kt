/*
 * НАЗНАЧЕНИЕ
 * Тесты отображения ответа VK (VkPost) в строку таблицы post. Отображение было
 * выписано вручную в четырёх коллекторах и не покрывалось тестами: расхождение
 * между копиями (например, забытое поле в одном из окон сбора) не поймал бы
 * никто. Теперь отображение одно, и его поведение зафиксировано здесь.
 *
 * ЧТО ВНУТРИ
 * Класс VkPostPersistenceTest: проверки переноса всех полей, трактовки
 * отсутствующих счётчиков VK, различия null/0 для просмотров, признаков медиа и
 * репоста, а также идемпотентности повторной вставки.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5; Exposed ORM + JDBC SQLite (временная БД с миграциями Flyway).
 *
 * СВЯЗИ
 * PostDao.insertVkPost (vk/VkPostPersistence.kt), модели vk/models/VkModels.kt.
 */
package com.example.lomanalyzer.vk

import com.example.lomanalyzer.storage.Migrations
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.dao.SessionDao
import com.example.lomanalyzer.storage.tables.Posts
import com.example.lomanalyzer.vk.models.VkAttachment
import com.example.lomanalyzer.vk.models.VkComments
import com.example.lomanalyzer.vk.models.VkLikes
import com.example.lomanalyzer.vk.models.VkPost
import com.example.lomanalyzer.vk.models.VkReposts
import com.example.lomanalyzer.vk.models.VkViews
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class VkPostPersistenceTest {
    private lateinit var tempDb: Path
    private lateinit var db: Database
    private lateinit var postDao: PostDao
    private var sessionId = 0

    @BeforeEach
    fun setup() {
        tempDb = Files.createTempFile("vk_post_", ".db")
        Migrations.migrate(tempDb)
        db = Database.connect("jdbc:sqlite:${tempDb.toAbsolutePath()}", driver = "org.sqlite.JDBC")
        postDao = PostDao(db)
        sessionId = SessionDao(db).insert(name = "persist", topicQuery = "t", region = "synthetic")
    }

    @AfterEach
    fun cleanup() {
        Files.deleteIfExists(tempDb)
    }

    private fun readSingleRow() = transaction(db) { Posts.selectAll().single() }

    /** Полностью заполненный пост переносится в БД поле в поле. */
    @Test
    fun `maps every field of a fully populated post`() {
        val post = VkPost(
            id = 555,
            ownerId = -100,
            fromId = 777,
            date = 1_717_196_400L,
            text = "Загрязнение воздуха",
            likes = VkLikes(45),
            reposts = VkReposts(12),
            comments = VkComments(8),
            views = VkViews(9000),
            attachments = listOf(VkAttachment("photo")),
            copyHistory = listOf(VkPost(id = 1)),
        )

        postDao.insertVkPost(sessionId, post, "BASELINE")

        val row = readSingleRow()
        assertEquals(555, row[Posts.vkId])
        assertEquals(-100, row[Posts.ownerId])
        assertEquals(777, row[Posts.fromId])
        assertEquals(1_717_196_400L, row[Posts.publishedAt])
        assertEquals("Загрязнение воздуха", row[Posts.text])
        assertEquals("BASELINE", row[Posts.window])
        assertEquals("Загрязнение воздуха".length, row[Posts.ownTextLength])
        assertEquals(45, row[Posts.likes])
        assertEquals(12, row[Posts.reposts])
        assertEquals(8, row[Posts.comments])
        assertEquals(9000, row[Posts.views])
        assertTrue(row[Posts.containsMedia], "attachments present -> containsMedia")
        assertTrue(row[Posts.hasCopyHistory], "copy_history present -> hasCopyHistory")
    }

    /** Отсутствующие счётчики VK трактуются как ноль, а не как ошибка. */
    @Test
    fun `absent counters become zero`() {
        postDao.insertVkPost(sessionId, VkPost(id = 1, text = "x"), "CURRENT")

        val row = readSingleRow()
        assertEquals(0, row[Posts.likes])
        assertEquals(0, row[Posts.reposts])
        assertEquals(0, row[Posts.comments])
    }

    /**
     * Просмотры — единственный счётчик, где null сохраняется как null.
     * «VK не отдал просмотры» и «просмотров ноль» — разные случаи: охват Reach_a
     * при отсутствии данных подставляет размер аудитории, а при нуле — ноль.
     */
    @Test
    fun `missing views stay null rather than zero`() {
        postDao.insertVkPost(sessionId, VkPost(id = 1, views = null), "CURRENT")
        assertNull(readSingleRow()[Posts.views], "absent views must not collapse to 0")
    }

    /** Ноль просмотров — это именно ноль, а не отсутствие данных. */
    @Test
    fun `explicit zero views are preserved as zero`() {
        postDao.insertVkPost(sessionId, VkPost(id = 1, views = VkViews(0)), "CURRENT")
        assertEquals(0, readSingleRow()[Posts.views])
    }

    /** Пустые списки вложений и репостов не считаются наличием медиа/репоста. */
    @Test
    fun `empty attachment and copy history lists are not flags`() {
        postDao.insertVkPost(
            sessionId,
            VkPost(id = 1, attachments = emptyList(), copyHistory = emptyList()),
            "CURRENT",
        )

        val row = readSingleRow()
        assertFalse(row[Posts.containsMedia])
        assertFalse(row[Posts.hasCopyHistory])
    }

    /**
     * Один и тот же пост в разных окнах — две записи: фоновая и тематическая
     * активность считаются раздельно и по разным периодам.
     */
    @Test
    fun `same post in different windows is stored twice`() {
        val post = VkPost(id = 42, ownerId = -1, fromId = 5)
        postDao.insertVkPost(sessionId, post, "BASELINE")
        postDao.insertVkPost(sessionId, post, "CURRENT")

        val windows = transaction(db) { Posts.selectAll().map { it[Posts.window] } }
        assertEquals(listOf("BASELINE", "CURRENT"), windows.sorted())
    }

    /** Повторная вставка того же поста в то же окно не плодит дубликатов. */
    @Test
    fun `re-inserting the same post into the same window is idempotent`() {
        val post = VkPost(id = 42, ownerId = -1, fromId = 5)
        val first = postDao.insertVkPost(sessionId, post, "CURRENT")
        val second = postDao.insertVkPost(sessionId, post, "CURRENT")

        assertEquals(first, second, "must return the existing row id")
        assertEquals(1, transaction(db) { Posts.selectAll().count() })
    }
}
