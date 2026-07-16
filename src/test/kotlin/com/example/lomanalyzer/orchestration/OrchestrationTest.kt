/*
 * НАЗНАЧЕНИЕ
 * Тесты оркестрации пайплайна: управление сессиями анализа, чекпоинты для
 * возобновления, реестр активной сессии (один анализ за раз) и контроллер отмены.
 * Относится к слою orchestration (запуск и сопровождение 10 этапов анализа).
 *
 * ЧТО ВНУТРИ
 * Класс OrchestrationTest на временной SQLite-БД (Flyway-миграции):
 *  - SessionManager: создание сессии и смена статуса;
 *  - CheckpointManager: сохранение/загрузка чекпоинта, null при их отсутствии;
 *  - ActiveSessionRegistry: запрет параллельного анализа и его повторное разрешение;
 *  - CancellationController: бросок CancellationException и сброс состояния.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (@BeforeEach/@AfterEach/@Test, assert*). Exposed ORM + SQLite JDBC —
 * реальная БД во временном файле. Flyway (Migrations.migrate) — создание схемы.
 *
 * СВЯЗИ
 * SessionManager, CheckpointManager, ActiveSessionRegistry, CancellationController,
 * DAO (SessionDao, CheckpointDao), таблица AnalysisSessions, Migrations.
 */
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

/** Тесты менеджеров сессий, чекпоинтов, реестра и контроллера отмены. */
class OrchestrationTest {
    /** Путь к временному файлу SQLite-БД для каждого теста. */
    private lateinit var tempDb: Path
    /** Подключение Exposed к временной БД. */
    private lateinit var db: Database
    /** Логгер-заглушка для конструкторов менеджеров. */
    private lateinit var logger: Logger

    /**
     * Arrange для каждого теста: создаём временную БД, прогоняем миграции Flyway,
     * подключаем Exposed и включаем внешние ключи (PRAGMA foreign_keys=ON).
     */
    @BeforeEach
    fun setup() {
        // Временный файл БД — изолирует каждый тест
        tempDb = Files.createTempFile("lom_orch_test_", ".db")
        // Применяем миграции V1..V10 — создаём всю схему
        Migrations.migrate(tempDb)
        db = Database.connect(
            "jdbc:sqlite:${tempDb.toAbsolutePath()}",
            driver = "org.sqlite.JDBC",
        )
        // Включаем проверку внешних ключей (SQLite по умолчанию off)
        transaction(db) {
            (connection.connection as java.sql.Connection).createStatement().execute("PRAGMA foreign_keys=ON")
        }
        logger = Logger("test")
    }

    /** Teardown: удаляем временный файл БД после теста. */
    @AfterEach
    fun teardown() {
        Files.deleteIfExists(tempDb)
    }

    /**
     * SessionManager.createSession должен записать строку сессии в БД с переданными
     * параметрами и начальным статусом CREATED. Assert: id>0 и поля строки совпадают.
     */
    @Test
    fun `SessionManager createSession persists a row`() {
        val sessionDao = SessionDao(db)
        val manager = SessionManager(sessionDao, logger)

        // Создаём сессию с именем и темой
        val id = manager.createSession(
            SessionParams(name = "Test", topicQuery = "ecology")
        )
        // Валидный автоинкрементный id
        assertTrue(id > 0)

        // Читаем строку обратно и сверяем сохранённые поля
        val row = manager.getSession(id)
        assertNotNull(row)
        assertEquals("Test", row!![AnalysisSessions.name])
        assertEquals("ecology", row[AnalysisSessions.topicQuery])
        // Начальный статус новой сессии
        assertEquals("CREATED", row[AnalysisSessions.status])
    }

    /**
     * SessionManager.updateStatus меняет статус сессии в БД; getStatus читает его
     * обратно как enum. Act: перевод в ANALYZING. Assert: статус прочитан как ANALYZING.
     */
    @Test
    fun `SessionManager updateStatus changes status`() {
        val sessionDao = SessionDao(db)
        val manager = SessionManager(sessionDao, logger)

        val id = manager.createSession(
            SessionParams(name = "S", topicQuery = "q")
        )
        // Меняем статус сессии
        manager.updateStatus(id, SessionStatus.ANALYZING)

        // Перечитываем и убеждаемся, что статус обновился
        val row = manager.getSession(id)
        assertEquals(SessionStatus.ANALYZING, manager.getStatus(row!!))
    }

    /**
     * CheckpointManager: сохранённый чекпоинт читается обратно без потерь
     * (roundtrip). Assert: sessionId, stage и payload совпадают с записанными.
     */
    @Test
    fun `CheckpointManager save and load roundtrip`() {
        val sessionDao = SessionDao(db)
        val checkpointDao = CheckpointDao(db)
        val sm = SessionManager(sessionDao, logger)
        val cm = CheckpointManager(checkpointDao, logger)

        // Чекпоинт привязан к сессии (FK), поэтому сначала создаём сессию
        val sessionId = sm.createSession(
            SessionParams(name = "CP Test", topicQuery = "topic")
        )
        // Сохраняем чекпоинт этапа COLLECT_BASELINE с произвольным payload
        cm.saveCheckpoint(sessionId, "COLLECT_BASELINE", "offset=100")

        // Загружаем последний чекпоинт и сверяем все поля
        val checkpoint = cm.loadLastCheckpoint(sessionId)
        assertNotNull(checkpoint)
        assertEquals(sessionId, checkpoint!!.sessionId)
        assertEquals("COLLECT_BASELINE", checkpoint.stage)
        assertEquals("offset=100", checkpoint.payload)
    }

    /**
     * Граничный случай: для сессии без чекпоинтов loadLastCheckpoint возвращает null
     * (несуществующий id 9999), а не падает.
     */
    @Test
    fun `CheckpointManager loadLastCheckpoint returns null for no checkpoints`() {
        val checkpointDao = CheckpointDao(db)
        val cm = CheckpointManager(checkpointDao, logger)

        // Нет ни одного чекпоинта → null
        assertNull(cm.loadLastCheckpoint(9999))
    }

    /**
     * ActiveSessionRegistry гарантирует одновременно только один анализ:
     * первый tryStartAnalysis успешен, второй (пока первый активен) — false.
     */
    @Test
    fun `ActiveSessionRegistry tryStartAnalysis returns false when busy`() {
        val registry = ActiveSessionRegistry()

        // Первый запуск разрешён
        assertTrue(registry.tryStartAnalysis(1))
        assertTrue(registry.analysisInProgress)
        assertEquals(1, registry.currentSessionId())

        // Пока сессия 1 активна, запуск сессии 2 запрещён
        assertFalse(registry.tryStartAnalysis(2))
    }

    /**
     * После endAnalysis реестр освобождается: in-progress снимается, текущая сессия
     * сбрасывается в null и новый анализ снова разрешён.
     */
    @Test
    fun `ActiveSessionRegistry endAnalysis allows new analysis`() {
        val registry = ActiveSessionRegistry()

        // Запускаем и сразу завершаем анализ сессии 1
        registry.tryStartAnalysis(1)
        registry.endAnalysis(1)

        // Реестр снова свободен
        assertFalse(registry.analysisInProgress)
        assertNull(registry.currentSessionId())
        // Новый анализ разрешён
        assertTrue(registry.tryStartAnalysis(2))
    }

    /**
     * CancellationController: после cancel() флаг isCancelled=true, а checkCancelled()
     * бросает CancellationException (механизм кооперативной отмены пайплайна).
     */
    @Test
    fun `CancellationController throws on checkCancelled`() {
        val controller = CancellationController()

        // Запрашиваем отмену
        controller.cancel()
        assertTrue(controller.isCancelled())
        // Проверка отмены должна выбросить исключение
        assertThrows(CancellationException::class.java) {
            controller.checkCancelled()
        }
    }

    /**
     * reset() возвращает контроллер в исходное состояние: isCancelled=false и
     * checkCancelled() больше не бросает исключение (повторное использование).
     */
    @Test
    fun `CancellationController reset clears state`() {
        val controller = CancellationController()

        // Отменяем, затем сбрасываем состояние
        controller.cancel()
        controller.reset()
        // После сброса отмены нет
        assertFalse(controller.isCancelled())
        assertDoesNotThrow { controller.checkCancelled() }
    }
}
