/*
 * НАЗНАЧЕНИЕ
 * Тесты импорта датасета из JSON — альтернативы сбору через VK API. Проверяют
 * полноту импорта, привязку комментариев к постам, дедупликацию по vkId и
 * публикацию прогресса со счётчиками.
 *
 * ЧТО ВНУТРИ
 * Класс JsonDataImporterTest: импорт полного датасета, пропуск комментариев без
 * поста, идемпотентность повторного импорта, наличие счётчиков в прогрессе.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5; Exposed ORM + JDBC SQLite (временная БД с миграциями Flyway).
 *
 * СВЯЗИ
 * JsonDataImporter, ProgressReporter (orchestration), DAO-слой (storage/dao).
 */
package com.example.lomanalyzer.import

import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.storage.Migrations
import com.example.lomanalyzer.storage.dao.AuthorDao
import com.example.lomanalyzer.storage.dao.CommentDao
import com.example.lomanalyzer.storage.dao.CommunityDao
import com.example.lomanalyzer.storage.dao.LinkDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.dao.SessionDao
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class JsonDataImporterTest {
    private lateinit var tempDb: Path
    private lateinit var tempJson: Path
    private lateinit var db: Database
    private lateinit var importer: JsonDataImporter
    private lateinit var progressReporter: ProgressReporter
    private lateinit var commentDao: CommentDao
    private var sessionId = 0

    /** Датасет: 1 сообщество, 2 автора, 2 поста и 3 комментария, один из которых «сирота». */
    private val datasetJson = """
        {
          "communities": [
            {"vkId": 100, "name": "Тест", "screenName": "test", "membersCount": 500, "isClosed": false, "type": "group"}
          ],
          "authors": [
            {"vkId": 11, "firstName": "Иван", "lastName": "Петров", "screenName": "ivan", "followersCount": 300, "isClosed": false},
            {"vkId": 22, "firstName": "Анна", "lastName": "Сидорова", "screenName": "anna", "followersCount": 900, "isClosed": false}
          ],
          "posts": [
            {"vkId": 1, "ownerId": -100, "fromId": 11, "date": 1717196400, "text": "Первый пост", "likes": 5, "reposts": 1, "comments": 2, "views": 90, "containsMedia": false, "hasCopyHistory": false, "window": "CURRENT"},
            {"vkId": 2, "ownerId": -100, "fromId": 22, "date": 1717196500, "text": "Второй пост", "likes": 7, "reposts": 0, "comments": 0, "views": 40, "containsMedia": false, "hasCopyHistory": false, "window": "BASELINE"}
          ],
          "comments": [
            {"vkId": 501, "postVkId": 1, "postOwnerId": -100, "fromId": 22, "date": 1717196600, "text": "Согласен", "likes": 1},
            {"vkId": 502, "postVkId": 1, "postOwnerId": -100, "fromId": 11, "date": 1717196700, "text": "И я", "likes": 0},
            {"vkId": 503, "postVkId": 999, "postOwnerId": -100, "fromId": 11, "date": 1717196800, "text": "Комментарий к несуществующему посту", "likes": 0}
          ]
        }
    """.trimIndent()

    @BeforeEach
    fun setup() {
        tempDb = Files.createTempFile("import_", ".db")
        Migrations.migrate(tempDb)
        db = Database.connect("jdbc:sqlite:${tempDb.toAbsolutePath()}", driver = "org.sqlite.JDBC")

        tempJson = Files.createTempFile("dataset_", ".json")
        tempJson.toFile().writeText(datasetJson, Charsets.UTF_8)

        progressReporter = ProgressReporter()
        commentDao = CommentDao(db)
        importer = JsonDataImporter(
            communityDao = CommunityDao(db),
            authorDao = AuthorDao(db),
            postDao = PostDao(db),
            commentDao = commentDao,
            linkDao = LinkDao(db),
            progressReporter = progressReporter,
            logger = Logger("import-test"),
        )
        sessionId = SessionDao(db).insert(name = "import", topicQuery = "t", region = "synthetic")
    }

    @AfterEach
    fun cleanup() {
        Files.deleteIfExists(tempDb)
        Files.deleteIfExists(tempJson)
    }

    /** Все сущности датасета попадают в БД. */
    @Test
    fun `imports every entity of the dataset`() {
        val result = importer.import(sessionId, tempJson.toString())

        assertEquals(1, result.communities)
        assertEquals(2, result.authors)
        assertEquals(2, result.posts)
        assertEquals(2, result.comments, "the orphan comment must not be imported")
    }

    /** Комментарии привязываются к своему посту по паре (vkId, ownerId). */
    @Test
    fun `links comments to their post`() {
        importer.import(sessionId, tempJson.toString())

        val counts = commentDao.countBySessionGroupedByPost(sessionId)
        assertEquals(1, counts.size, "both comments belong to the same post")
        assertEquals(2L, counts.values.first())
    }

    /**
     * Комментарий, ссылающийся на отсутствующий пост, пропускается, а импорт
     * продолжается — датасет с битой ссылкой не должен ронять сбор.
     */
    @Test
    fun `skips a comment whose post is missing`() {
        val result = importer.import(sessionId, tempJson.toString())

        assertEquals(2, result.comments)
        assertEquals(2L, commentDao.countBySession(sessionId))
    }

    /**
     * Прогресс публикуется СО СЧЁТЧИКАМИ.
     *
     * Экран сбора считает долю как completedItems/totalItems: без счётчиков
     * индикатор простаивает на нуле всю загрузку.
     */
    @Test
    fun `reports progress with item counts`() {
        importer.import(sessionId, tempJson.toString())

        val progress = progressReporter.progress.value
        assertTrue(progress.totalItems > 0, "totalItems must be set, otherwise the ring stays at 0%")
        assertTrue(progress.completedItems > 0, "completedItems must be set")
        assertTrue(progress.stage.isNotBlank(), "the stage must be named")
    }

    /** Повторный импорт того же файла не задваивает сущности (дедуп по vkId). */
    @Test
    fun `re-importing the same dataset does not duplicate entities`() {
        importer.import(sessionId, tempJson.toString())
        val second = importer.import(sessionId, tempJson.toString())

        assertEquals(1, second.communities)
        assertEquals(2, second.authors)
        assertEquals(2L, commentDao.countBySession(sessionId), "comments must not double")
    }
}
