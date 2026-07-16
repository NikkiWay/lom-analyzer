/*
 * НАЗНАЧЕНИЕ
 * Тесты UX-расширений оркестрации: конечный автомат реестра активной сессии
 * (включая режим восстановления после сбоя), форк сессии (повторный анализ на
 * основе исходной) и экспорт результатов в JSON (этап 9 — экспорт).
 *
 * ЧТО ВНУТРИ
 * Класс UxExtensionTest на временной SQLite-БД:
 *  - переходы состояний ActiveSessionRegistry: IDLE → ANALYZING → RECOVERY_AWAITING;
 *  - блокировка новых сессий в состоянии ожидания восстановления;
 *  - forceReset возвращает реестр в IDLE;
 *  - forkSession создаёт новую сессию, связанную с исходной (sessionFamilyId);
 *  - JsonExporter: валидный JSON со схемой и сокрытие имени сессии в safe-режиме.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (assert*, @BeforeEach/@AfterEach). Exposed + SQLite + Flyway — реальная БД.
 * kotlinx.serialization.json (Json.parseToJsonElement) — проверка валидности JSON.
 *
 * СВЯЗИ
 * ActiveSessionRegistry, RegistryState, SessionManager, SessionDao, JsonExporter,
 * JsonAuthorScores, таблица AnalysisSessions.
 */
package com.example.lomanalyzer.orchestration

import com.example.lomanalyzer.export.JsonExporter
import com.example.lomanalyzer.export.JsonAuthorScores
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.storage.Migrations
import com.example.lomanalyzer.storage.dao.SessionDao
import com.example.lomanalyzer.storage.tables.AnalysisSessions
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

/** Тесты состояния реестра, форка сессий и JSON-экспорта. */
class UxExtensionTest {
    /** Временный файл SQLite-БД для теста. */
    private lateinit var tempDb: Path
    /** Подключение Exposed к временной БД. */
    private lateinit var db: Database
    /** Логгер-заглушка. */
    private val logger = Logger("test")

    /** Arrange: временная БД, миграции Flyway, подключение Exposed, foreign_keys=ON. */
    @BeforeEach
    fun setup() {
        tempDb = Files.createTempFile("ux_test_", ".db")
        Migrations.migrate(tempDb)
        db = Database.connect("jdbc:sqlite:${tempDb.toAbsolutePath()}", "org.sqlite.JDBC")
        transaction(db) {
            (connection.connection as java.sql.Connection).createStatement().execute("PRAGMA foreign_keys=ON")
        }
    }

    /** Teardown: удаление временной БД. */
    @AfterEach
    fun teardown() {
        Files.deleteIfExists(tempDb)
    }

    // --- ActiveSessionRegistry state machine ---

    /**
     * Проверка автомата состояний: пустой реестр в IDLE; после старта — ANALYZING
     * с текущей сессией; transitionToRecovery → RECOVERY_AWAITING (сессия сохраняется);
     * в состоянии восстановления новый запуск запрещён.
     */
    @Test
    fun `registry transitions IDLE to ANALYZING to RECOVERY`() {
        val registry = ActiveSessionRegistry()
        // Исходное состояние — простой
        assertEquals(RegistryState.IDLE, registry.getCurrentState())

        // Старт анализа сессии 1 → ANALYZING
        assertTrue(registry.tryStartAnalysis(1, "Session 1"))
        assertEquals(RegistryState.ANALYZING, registry.getCurrentState())
        assertEquals(1, registry.currentSessionId())

        // Перевод в режим ожидания восстановления (после сбоя)
        registry.transitionToRecovery()
        assertEquals(RegistryState.RECOVERY_AWAITING, registry.getCurrentState())
        // Привязка к сессии 1 сохраняется
        assertEquals(1, registry.currentSessionId())

        // Cannot start another session during recovery
        // Во время восстановления нельзя начать другую сессию
        assertFalse(registry.tryStartAnalysis(2))
    }

    /**
     * Инвариант: состояние ожидания восстановления блокирует запуск новых сессий,
     * при этом analysisInProgress остаётся true (анализ формально не завершён).
     */
    @Test
    fun `PAUSED_PENDING_RECOVERY blocks new sessions`() {
        val registry = ActiveSessionRegistry()
        registry.tryStartAnalysis(1)
        registry.transitionToRecovery()

        // Another session cannot start
        // Новая сессия не может стартовать в режиме восстановления
        assertFalse(registry.tryStartAnalysis(2))
        assertTrue(registry.analysisInProgress)
    }

    /**
     * forceReset аварийно сбрасывает реестр из состояния восстановления обратно
     * в IDLE, после чего запуск новой сессии снова разрешён.
     */
    @Test
    fun `forceReset clears recovery state`() {
        val registry = ActiveSessionRegistry()
        registry.tryStartAnalysis(1)
        registry.transitionToRecovery()

        // Принудительный сброс из RECOVERY_AWAITING
        registry.forceReset()
        assertEquals(RegistryState.IDLE, registry.getCurrentState())
        assertTrue(registry.tryStartAnalysis(2))
    }

    // --- Fork session ---

    /**
     * forkSession создаёт новую сессию-форк, связанную с исходной через
     * sessionFamilyId. Assert: forkId > sourceId, имя форка сохранено, поле
     * sessionFamilyId указывает на исходную сессию.
     */
    @Test
    fun `forkSession creates new session linked to source`() {
        val sessionDao = SessionDao(db)
        val sm = SessionManager(sessionDao, logger)

        // Исходная сессия и форк от неё
        val sourceId = sm.createSession(SessionParams("Original", "ecology"))
        val forkId = sm.forkSession(sourceId, SessionParams("Fork", "ecology v2"))

        // Форк создан позже → больший id
        assertTrue(forkId > sourceId)
        val fork = sessionDao.findById(forkId)!!
        assertEquals("Fork", fork[AnalysisSessions.name])
        // Связь форка с исходной сессией
        assertEquals(sourceId, fork[AnalysisSessions.sessionFamilyId]?.value)
    }

    // --- JsonExporter ---

    /**
     * JsonExporter.export создаёт непустой валидный JSON, содержащий ключ schema-
     * полей (sessionId) и значения скоров (роль TOPIC_DRIVER). Валидность
     * подтверждается успешным разбором через Json.parseToJsonElement.
     */
    @Test
    fun `JSON export produces valid JSON with schema`() {
        val exporter = JsonExporter(logger)
        // Временный выходной файл (удаляется в finally)
        val tempFile = File.createTempFile("export_", ".json")
        try {
            // Экспортируем одну строку скоров автора
            exporter.export(
                sessionId = 1,
                sessionName = "Test",
                scores = listOf(
                    JsonAuthorScores(
                        authorHash = "hash1",
                        aud = 0.65f,
                        erBg = 0.45f,
                        role = "TOPIC_DRIVER",
                    ),
                ),
                outputFile = tempFile,
            )

            // Читаем результат и проверяем содержимое
            val content = tempFile.readText()
            assertTrue(content.isNotEmpty(), "Export file should not be empty")
            assertTrue(content.contains("sessionId"), "Should contain sessionId: $content")
            assertTrue(content.contains("TOPIC_DRIVER"), "Should contain role")
            // Verify valid JSON
            // Подтверждаем, что вывод — синтаксически корректный JSON
            Json.parseToJsonElement(content)
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Safe-режим экспорта (rawMode=false) скрывает реальное имя сессии: в файле
     * не должно быть "Secret Session", вместо него — обезличенное "Session #1".
     */
    @Test
    fun `JSON export hides session name in safe mode`() {
        val exporter = JsonExporter(logger)
        val tempFile = File.createTempFile("export_safe_", ".json")
        try {
            // Экспорт без сырых данных (safe), список скоров пуст
            exporter.export(
                sessionId = 1,
                sessionName = "Secret Session",
                scores = emptyList(),
                outputFile = tempFile,
                rawMode = false,
            )
            val content = tempFile.readText()
            // Имя сессии скрыто
            assertFalse(content.contains("Secret Session"))
            // Используется обезличенный идентификатор
            assertTrue(content.contains("Session #1"))
        } finally {
            tempFile.delete()
        }
    }
}
