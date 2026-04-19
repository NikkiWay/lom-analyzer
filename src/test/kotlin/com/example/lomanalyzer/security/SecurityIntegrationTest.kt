package com.example.lomanalyzer.security

import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.RetentionManager
import com.example.lomanalyzer.storage.Migrations
import com.example.lomanalyzer.storage.dao.SessionDao
import com.example.lomanalyzer.storage.tables.AnalysisSessions
import com.example.lomanalyzer.vk.OAuthFlow
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit

class SecurityIntegrationTest {

    private lateinit var tempDb: Path
    private lateinit var db: Database

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        tempDb = Files.createTempFile("sec_test_", ".db")
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

    // --- PII masking ---

    @Test
    fun `masked log does not contain raw PII`() {
        val logLine = """{"vk_id": 123456789, "first_name": "Иван", "screen_name": "ivanov123"}"""
        val masked = PiiSafeFormatter.mask(logLine)
        assertFalse(masked.contains("123456789"))
        assertFalse(masked.contains("Иван"))
        assertFalse(masked.contains("ivanov123"))
    }

    // --- OAuth PKCE ---

    @Test
    fun `PKCE auth URL contains code_challenge`() {
        val vault = TokenVault(tempDir.resolve("vault.bin"))
        vault.initializeKey("test".toCharArray())
        val oauth = OAuthFlow(vault, Logger("test"), usePkce = true)

        val url = oauth.buildAuthUrl("12345")
        assertTrue(url.contains("code_challenge="))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("response_type=code"))
    }

    @Test
    fun `implicit auth URL uses response_type token`() {
        val vault = TokenVault(tempDir.resolve("vault.bin"))
        vault.initializeKey("test".toCharArray())
        val oauth = OAuthFlow(vault, Logger("test"), usePkce = false)

        val url = oauth.buildAuthUrl("12345")
        assertTrue(url.contains("response_type=token"))
        assertFalse(url.contains("code_challenge"))
    }

    @Test
    fun `code verifier and challenge are valid`() {
        val vault = TokenVault(tempDir.resolve("vault.bin"))
        vault.initializeKey("test".toCharArray())
        val oauth = OAuthFlow(vault, Logger("test"))

        val verifier = oauth.generateCodeVerifier()
        val challenge = oauth.generateCodeChallenge(verifier)
        assertTrue(verifier.length > 40)
        assertTrue(challenge.length > 20)
        assertNotEquals(verifier, challenge)
    }

    @Test
    fun `store and retrieve tokens with refresh`() {
        val vault = TokenVault(tempDir.resolve("vault.bin"))
        vault.initializeKey("test".toCharArray())
        val oauth = OAuthFlow(vault, Logger("test"))

        oauth.storeTokens("access_token_123", "refresh_token_456")
        assertEquals("access_token_123", oauth.getStoredToken())
        assertEquals("refresh_token_456", oauth.getStoredRefreshToken())
    }

    // --- Retention lifecycle ---

    @Test
    fun `soft-delete then restore then hard-delete`() {
        val sessionDao = SessionDao(db)
        val logger = Logger("test")
        val retention = RetentionManager(sessionDao, logger)

        val id = sessionDao.insert("Old Session", "test")

        // Soft-delete
        sessionDao.softDelete(id)
        var softDeleted = retention.getSoftDeletedSessions()
        assertEquals(1, softDeleted.size)
        assertEquals(id, softDeleted[0].id)

        // Restore
        retention.restoreSession(id)
        softDeleted = retention.getSoftDeletedSessions()
        assertTrue(softDeleted.isEmpty())
        assertNotNull(sessionDao.findById(id))

        // Soft-delete again then hard-delete
        sessionDao.softDelete(id)
        sessionDao.hardDelete(id)
        assertNull(sessionDao.findById(id))
    }

    @Test
    fun `findAll excludes soft-deleted sessions`() {
        val sessionDao = SessionDao(db)
        val id1 = sessionDao.insert("Active", "t")
        val id2 = sessionDao.insert("Deleted", "t")
        sessionDao.softDelete(id2)

        val active = sessionDao.findAll()
        assertEquals(1, active.size)
        assertEquals(id1, active[0][AnalysisSessions.id].value)
    }

    // --- Build verification ---

    @Test
    fun `prod config has DEBUG_INCLUDES_PII false`() {
        val prodConfig = java.io.File("src/main/resources/logback-prod.xml")
        if (prodConfig.exists()) {
            val content = prodConfig.readText()
            val hasTrueDebugPii = content.contains("\"true\"") &&
                content.contains("DEBUG_INCLUDES_PII")
            assertFalse(hasTrueDebugPii,
                "logback-prod.xml must have DEBUG_INCLUDES_PII=false")
        }
    }
}
