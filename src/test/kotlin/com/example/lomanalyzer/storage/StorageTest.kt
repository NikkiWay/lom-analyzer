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

class StorageTest {
    private lateinit var tempDb: Path
    private lateinit var db: Database

    @BeforeEach
    fun setup() {
        tempDb = Files.createTempFile("lom_test_", ".db")
        // Run Flyway migration
        Migrations.migrate(tempDb)
        // Connect Exposed
        db = Database.connect("jdbc:sqlite:${tempDb.toAbsolutePath()}", driver = "org.sqlite.JDBC")
        transaction(db) {
            connection.prepareStatement("PRAGMA foreign_keys=ON", false).executeUpdate()
        }
    }

    @AfterEach
    fun teardown() {
        Files.deleteIfExists(tempDb)
    }

    @Test
    fun `V1 schema creates all tables`() {
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
        val expected = listOf(
            "analysis_session", "anomaly_author_link", "anomaly_event", "anomaly_post_link",
            "audit_log", "author", "collection_checkpoint", "community",
            "dedup_group", "holiday_day_stats", "lom_score", "persona_aggregate",
            "persona_history_link",
            "post", "post_metrics_snapshot", "processed_text", "recovery_choice",
            "repost_relation", "risk_anomaly_link", "risk_signal",
            "sentiment_result", "session_author", "session_community",
            "session_metrics",
        )
        assertEquals(expected, tables)
    }

    @Test
    fun `insert and read AnalysisSession`() {
        val dao = SessionDao(db)
        val id = dao.insert(name = "Test Session", topicQuery = "ecology")
        val row = dao.findById(id)
        assertNotNull(row)
        assertEquals("Test Session", row!![AnalysisSessions.name])
        assertEquals("ecology", row[AnalysisSessions.topicQuery])
        assertEquals("CREATED", row[AnalysisSessions.status])
        assertEquals("FULL", row[AnalysisSessions.nlpMode])
    }

    @Test
    fun `insert and read Author`() {
        val dao = AuthorDao(db)
        val id = dao.insert(vkId = 123456, firstName = "Ivan", lastName = "Petrov", followersCount = 1500)
        val row = dao.findById(id)
        assertNotNull(row)
        assertEquals(123456, row!![Authors.vkId])
        assertEquals("Ivan", row[Authors.firstName])
        assertEquals(1500, row[Authors.followersCount])
        assertEquals(false, row[Authors.isClosed])
    }

    @Test
    fun `insert and read Post with session FK`() {
        val sessionDao = SessionDao(db)
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
        assertEquals("CURRENT", row!![Posts.window])
        assertEquals(17, row[Posts.ownTextLength])
        assertEquals(999, row[Posts.vkId])
    }

    @Test
    fun `required indexes exist`() {
        val indexes = transaction(db) {
            val result = mutableListOf<String>()
            exec("SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'idx_%' ORDER BY name") { rs ->
                while (rs.next()) {
                    result.add(rs.getString("name"))
                }
            }
            result
        }
        assertTrue(indexes.contains("idx_post_session_published"))
        assertTrue(indexes.contains("idx_post_session_from"))
        assertTrue(indexes.contains("idx_post_session_relevant"))
        assertTrue(indexes.contains("idx_post_session_window"))
        assertTrue(indexes.contains("idx_post_session_holiday"))
        assertTrue(indexes.contains("idx_lom_session_base"))
        assertTrue(indexes.contains("idx_lom_session_role"))
        assertTrue(indexes.contains("idx_anomaly_session_type_day"))
        assertTrue(indexes.contains("idx_checkpoint_session_ep"))
        assertTrue(indexes.contains("idx_audit_session_time"))
        assertTrue(indexes.contains("idx_session_deleted_at"))
        assertTrue(indexes.contains("idx_recovery_session_ts"))
        assertTrue(indexes.contains("idx_holiday_stats_session_date"))
    }

    @Test
    fun `session status update works`() {
        val dao = SessionDao(db)
        val id = dao.insert(name = "S2", topicQuery = "q")
        dao.updateStatus(id, "COLLECTING")
        val row = dao.findById(id)
        assertEquals("COLLECTING", row!![AnalysisSessions.status])
    }

    @Test
    fun `soft delete sets deleted_at`() {
        val dao = SessionDao(db)
        val id = dao.insert(name = "S3", topicQuery = "q")
        dao.softDelete(id)
        val row = dao.findById(id)
        assertNotNull(row!![AnalysisSessions.deletedAt])
        // findAll excludes soft-deleted
        assertTrue(dao.findAll().isEmpty())
    }
}
