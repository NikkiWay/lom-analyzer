package com.example.lomanalyzer.orchestration

import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.storage.Migrations
import com.example.lomanalyzer.storage.dao.CheckpointDao
import com.example.lomanalyzer.storage.dao.SessionDao
import com.example.lomanalyzer.storage.tables.AnalysisSessions
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class OrchestrationTest {
    private lateinit var tempDb: Path
    private lateinit var db: Database
    private lateinit var logger: Logger

    @BeforeEach
    fun setup() {
        tempDb = Files.createTempFile("lom_orch_test_", ".db")
        Migrations.migrate(tempDb)
        db = Database.connect(
            "jdbc:sqlite:${tempDb.toAbsolutePath()}",
            driver = "org.sqlite.JDBC",
        )
        transaction(db) {
            (connection.connection as java.sql.Connection).createStatement().execute("PRAGMA foreign_keys=ON")
        }
        logger = Logger("test")
    }

    @AfterEach
    fun teardown() {
        Files.deleteIfExists(tempDb)
    }

    @Test
    fun `SessionManager createSession persists a row`() {
        val sessionDao = SessionDao(db)
        val manager = SessionManager(sessionDao, logger)

        val id = manager.createSession(
            SessionParams(name = "Test", topicQuery = "ecology")
        )
        assertTrue(id > 0)

        val row = manager.getSession(id)
        assertNotNull(row)
        assertEquals("Test", row!![AnalysisSessions.name])
        assertEquals("ecology", row[AnalysisSessions.topicQuery])
        assertEquals("CREATED", row[AnalysisSessions.status])
    }

    @Test
    fun `SessionManager updateStatus changes status`() {
        val sessionDao = SessionDao(db)
        val manager = SessionManager(sessionDao, logger)

        val id = manager.createSession(
            SessionParams(name = "S", topicQuery = "q")
        )
        manager.updateStatus(id, SessionStatus.ANALYZING)

        val row = manager.getSession(id)
        assertEquals(SessionStatus.ANALYZING, manager.getStatus(row!!))
    }

    @Test
    fun `CheckpointManager save and load roundtrip`() {
        val sessionDao = SessionDao(db)
        val checkpointDao = CheckpointDao(db)
        val sm = SessionManager(sessionDao, logger)
        val cm = CheckpointManager(checkpointDao, logger)

        val sessionId = sm.createSession(
            SessionParams(name = "CP Test", topicQuery = "topic")
        )
        cm.saveCheckpoint(sessionId, "COLLECT_BASELINE", "offset=100")

        val checkpoint = cm.loadLastCheckpoint(sessionId)
        assertNotNull(checkpoint)
        assertEquals(sessionId, checkpoint!!.sessionId)
        assertEquals("COLLECT_BASELINE", checkpoint.stage)
        assertEquals("offset=100", checkpoint.payload)
    }

    @Test
    fun `CheckpointManager loadLastCheckpoint returns null for no checkpoints`() {
        val checkpointDao = CheckpointDao(db)
        val cm = CheckpointManager(checkpointDao, logger)

        assertNull(cm.loadLastCheckpoint(9999))
    }

    @Test
    fun `ActiveSessionRegistry tryStartAnalysis returns false when busy`() {
        val registry = ActiveSessionRegistry()

        assertTrue(registry.tryStartAnalysis(1))
        assertTrue(registry.analysisInProgress)
        assertEquals(1, registry.currentSessionId())

        assertFalse(registry.tryStartAnalysis(2))
    }

    @Test
    fun `ActiveSessionRegistry endAnalysis allows new analysis`() {
        val registry = ActiveSessionRegistry()

        registry.tryStartAnalysis(1)
        registry.endAnalysis(1)

        assertFalse(registry.analysisInProgress)
        assertNull(registry.currentSessionId())
        assertTrue(registry.tryStartAnalysis(2))
    }

    @Test
    fun `CancellationController throws on checkCancelled`() {
        val controller = CancellationController()

        controller.cancel()
        assertTrue(controller.isCancelled())
        assertThrows(CancellationException::class.java) {
            controller.checkCancelled()
        }
    }

    @Test
    fun `CancellationController reset clears state`() {
        val controller = CancellationController()

        controller.cancel()
        controller.reset()
        assertFalse(controller.isCancelled())
        assertDoesNotThrow { controller.checkCancelled() }
    }
}
