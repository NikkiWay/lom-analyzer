package com.example.lomanalyzer.orchestration

import com.example.lomanalyzer.export.JsonExporter
import com.example.lomanalyzer.export.JsonAnomaly
import com.example.lomanalyzer.export.JsonLomScore
import com.example.lomanalyzer.export.JsonRiskSignal
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.persona.PersonaHistoryManager
import com.example.lomanalyzer.storage.Migrations
import com.example.lomanalyzer.storage.dao.SessionDao
import com.example.lomanalyzer.storage.tables.AnalysisSessions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class UxExtensionTest {
    private lateinit var tempDb: Path
    private lateinit var db: Database
    private val logger = Logger("test")

    @BeforeEach
    fun setup() {
        tempDb = Files.createTempFile("ux_test_", ".db")
        Migrations.migrate(tempDb)
        db = Database.connect("jdbc:sqlite:${tempDb.toAbsolutePath()}", "org.sqlite.JDBC")
        transaction(db) {
            (connection.connection as java.sql.Connection).createStatement().execute("PRAGMA foreign_keys=ON")
        }
    }

    @AfterEach
    fun teardown() {
        Files.deleteIfExists(tempDb)
    }

    // --- ActiveSessionRegistry state machine ---

    @Test
    fun `registry transitions IDLE to ANALYZING to RECOVERY`() {
        val registry = ActiveSessionRegistry()
        assertEquals(RegistryState.IDLE, registry.getCurrentState())

        assertTrue(registry.tryStartAnalysis(1, "Session 1"))
        assertEquals(RegistryState.ANALYZING, registry.getCurrentState())
        assertEquals(1, registry.currentSessionId())

        registry.transitionToRecovery()
        assertEquals(RegistryState.RECOVERY_AWAITING, registry.getCurrentState())
        assertEquals(1, registry.currentSessionId())

        // Cannot start another session during recovery
        assertFalse(registry.tryStartAnalysis(2))
    }

    @Test
    fun `PAUSED_PENDING_RECOVERY blocks new sessions`() {
        val registry = ActiveSessionRegistry()
        registry.tryStartAnalysis(1)
        registry.transitionToRecovery()

        // Another session cannot start
        assertFalse(registry.tryStartAnalysis(2))
        assertTrue(registry.analysisInProgress)
    }

    @Test
    fun `forceReset clears recovery state`() {
        val registry = ActiveSessionRegistry()
        registry.tryStartAnalysis(1)
        registry.transitionToRecovery()

        registry.forceReset()
        assertEquals(RegistryState.IDLE, registry.getCurrentState())
        assertTrue(registry.tryStartAnalysis(2))
    }

    // --- Recovery timeout ---

    @Test
    fun `timeout cancels session after delay`() = runBlocking {
        val sessionDao = SessionDao(db)
        val sm = SessionManager(sessionDao, logger)
        val registry = ActiveSessionRegistry()

        val sessionId = sm.createSession(SessionParams("Test", "q"))
        registry.tryStartAnalysis(sessionId)
        registry.transitionToRecovery()

        val watcher = RecoveryTimeoutWatcher(
            registry, sm, logger, timeoutMs = 100, // 100ms for test
        )
        watcher.startWatching(sessionId, CoroutineScope(Dispatchers.Default))

        kotlinx.coroutines.delay(200) // Wait for timeout

        assertEquals(RegistryState.IDLE, registry.getCurrentState())
        val row = sessionDao.findById(sessionId)!!
        assertEquals("CANCELLED", row[AnalysisSessions.status])
    }

    // --- Fork session ---

    @Test
    fun `forkSession creates new session linked to source`() {
        val sessionDao = SessionDao(db)
        val sm = SessionManager(sessionDao, logger)

        val sourceId = sm.createSession(SessionParams("Original", "ecology"))
        val forkId = sm.forkSession(sourceId, SessionParams("Fork", "ecology v2"))

        assertTrue(forkId > sourceId)
        val fork = sessionDao.findById(forkId)!!
        assertEquals("Fork", fork[AnalysisSessions.name])
        assertEquals(sourceId, fork[AnalysisSessions.sessionFamilyId]?.value)
    }

    // --- PersonaHistoryManager ---

    @Test
    fun `persona link persists across sessions`() {
        // Create the table manually since V2 migration handles it
        transaction(db) {
            exec("""CREATE TABLE IF NOT EXISTS persona_history_link (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_session_id INTEGER NOT NULL,
                target_session_id INTEGER NOT NULL,
                author_id INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )""")
        }

        val manager = PersonaHistoryManager(db)
        manager.linkPersonas(1, 2, 42)

        val links = manager.findLinks(1)
        assertEquals(1, links.size)
    }

    // --- JsonExporter ---

    @Test
    fun `JSON export produces valid JSON with schema`() {
        val exporter = JsonExporter(logger)
        val tempFile = File.createTempFile("export_", ".json")
        try {
            exporter.export(
                sessionId = 1,
                sessionName = "Test",
                lomScores = listOf(
                    JsonLomScore("hash1", 0.65f, 0.45f, "TOPIC_DRIVER", 0.7f),
                ),
                anomalies = listOf(
                    JsonAnomaly("VOLUME_SPIKE", "2025-06-15", 0.6f),
                ),
                riskSignals = listOf(
                    JsonRiskSignal(0.55f, "MEDIUM", "[draft]"),
                ),
                outputFile = tempFile,
            )

            val content = tempFile.readText()
            assertTrue(content.isNotEmpty(), "Export file should not be empty")
            assertTrue(content.contains("sessionId"), "Should contain sessionId: $content")
            assertTrue(content.contains("TOPIC_DRIVER"), "Should contain role")
            assertTrue(content.contains("VOLUME_SPIKE"), "Should contain anomaly type")
            // Verify valid JSON
            Json.parseToJsonElement(content)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `JSON export hides session name in safe mode`() {
        val exporter = JsonExporter(logger)
        val tempFile = File.createTempFile("export_safe_", ".json")
        try {
            exporter.export(
                sessionId = 1,
                sessionName = "Secret Session",
                lomScores = emptyList(),
                anomalies = emptyList(),
                riskSignals = emptyList(),
                outputFile = tempFile,
                rawMode = false,
            )
            val content = tempFile.readText()
            assertFalse(content.contains("Secret Session"))
            assertTrue(content.contains("Session #1"))
        } finally {
            tempFile.delete()
        }
    }
}
