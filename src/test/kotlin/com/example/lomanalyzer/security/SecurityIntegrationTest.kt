/*
 * НАЗНАЧЕНИЕ
 * Интеграционные тесты безопасности: OAuth-поток VK с PKCE (защищённый код-флоу
 * и implicit-флоу), хранение access/refresh-токенов в зашифрованном TokenVault,
 * soft-delete сессий в БД и проверка прод-конфигурации логирования (PII выключен).
 *
 * ЧТО ВНУТРИ
 * Класс SecurityIntegrationTest на временной SQLite-БД и временном каталоге vault:
 *  - VK ID auth URL содержит PKCE (code_challenge, method S256, response_type=code);
 *  - implicit auth URL использует response_type=token и не содержит PKCE;
 *  - code_verifier и code_challenge валидны и различаются;
 *  - сохранение и чтение пары токенов (access + refresh) через OAuthFlow/TokenVault;
 *  - findAll исключает soft-deleted сессии;
 *  - logback-prod.xml не включает DEBUG_INCLUDES_PII=true.
 *
 * МЕТОД
 * PKCE (RFC 7636): verifier — случайная строка, challenge = BASE64URL(SHA-256(verifier)),
 * method S256. Защищает обмен кода авторизации от перехвата.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (@TempDir, @BeforeEach/@AfterEach, assert*). Exposed + SQLite + Flyway.
 *
 * СВЯЗИ
 * OAuthFlow, TokenVault, SessionDao, таблица AnalysisSessions, Migrations,
 * ресурс src/main/resources/logback-prod.xml.
 */
package com.example.lomanalyzer.security

import com.example.lomanalyzer.observability.Logger
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

/** Интеграционные тесты OAuth/PKCE, vault токенов, soft-delete и прод-конфига. */
class SecurityIntegrationTest {

    /** Временный файл SQLite-БД. */
    private lateinit var tempDb: Path
    /** Подключение Exposed к временной БД. */
    private lateinit var db: Database

    /** Временный каталог JUnit для файла vault (изолирован и авто-удаляется). */
    @TempDir
    lateinit var tempDir: Path

    /** Arrange: временная БД с миграциями, подключение Exposed, foreign_keys=ON. */
    @BeforeEach
    fun setup() {
        tempDb = Files.createTempFile("sec_test_", ".db")
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

    // --- OAuth PKCE ---

    /**
     * URL авторизации VK ID должен использовать защищённый код-флоу с PKCE:
     * содержать code_challenge, code_challenge_method=S256, response_type=code и
     * указывать домен id.vk.com.
     */
    @Test
    fun `VK ID auth URL contains PKCE code_challenge`() {
        // Vault нужен OAuthFlow для хранения секретов; инициализируем ключ паролем
        val vault = TokenVault(tempDir.resolve("vault.bin"))
        vault.initializeKey("test".toCharArray())
        val oauth = OAuthFlow(vault, Logger("test"))

        // Строим URL авторизации VK ID
        val url = oauth.buildVkIdAuthUrl("12345", "http://localhost:8080/callback")
        // Признаки PKCE-флоу
        assertTrue(url.contains("code_challenge="))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("id.vk.com"))
    }

    /**
     * Implicit-флоу (устаревший) использует response_type=token, не содержит PKCE
     * (code_challenge) и идёт через домен oauth.vk.com.
     */
    @Test
    fun `implicit auth URL uses response_type token`() {
        val vault = TokenVault(tempDir.resolve("vault.bin"))
        vault.initializeKey("test".toCharArray())
        val oauth = OAuthFlow(vault, Logger("test"))

        val url = oauth.buildImplicitAuthUrl("12345")
        assertTrue(url.contains("response_type=token"))
        // В implicit нет PKCE
        assertFalse(url.contains("code_challenge"))
        assertTrue(url.contains("oauth.vk.com"))
    }

    /**
     * PKCE-пара корректна: verifier достаточно длинный (>40), challenge получен из
     * него (>20) и отличается от verifier (challenge = хэш verifier, а не сам verifier).
     */
    @Test
    fun `code verifier and challenge are valid`() {
        val vault = TokenVault(tempDir.resolve("vault.bin"))
        vault.initializeKey("test".toCharArray())
        val oauth = OAuthFlow(vault, Logger("test"))

        // Генерируем verifier и производный от него challenge
        val verifier = oauth.generateCodeVerifier()
        val challenge = oauth.generateCodeChallenge(verifier)
        assertTrue(verifier.length > 40)
        assertTrue(challenge.length > 20)
        // challenge — это хэш verifier, значения не совпадают
        assertNotEquals(verifier, challenge)
    }

    /**
     * Сохранение и чтение токенов: storeTokens кладёт access+refresh в зашифрованный
     * vault, getStoredToken/getStoredRefreshToken возвращают их без искажений.
     */
    @Test
    fun `store and retrieve tokens with refresh`() {
        val vault = TokenVault(tempDir.resolve("vault.bin"))
        vault.initializeKey("test".toCharArray())
        val oauth = OAuthFlow(vault, Logger("test"))

        // Сохраняем пару токенов и читаем обратно
        oauth.storeTokens("access_token_123", "refresh_token_456")
        assertEquals("access_token_123", oauth.getStoredToken())
        assertEquals("refresh_token_456", oauth.getStoredRefreshToken())
    }

    /**
     * findAll возвращает только активные сессии: помеченная softDelete сессия
     * исключается из выборки (остаётся в БД, но скрыта).
     */
    @Test
    fun `findAll excludes soft-deleted sessions`() {
        val sessionDao = SessionDao(db)
        // Две сессии, вторую мягко удаляем
        val id1 = sessionDao.insert("Active", "t")
        val id2 = sessionDao.insert("Deleted", "t")
        sessionDao.softDelete(id2)

        // Видна только активная сессия
        val active = sessionDao.findAll()
        assertEquals(1, active.size)
        assertEquals(id1, active[0][AnalysisSessions.id].value)
    }

    // --- Build verification ---

    /**
     * Проверка прод-конфигурации логирования: если logback-prod.xml существует,
     * в нём не должно быть одновременно DEBUG_INCLUDES_PII и значения "true"
     * (в проде PII в логи не пишется).
     */
    @Test
    fun `prod config has DEBUG_INCLUDES_PII false`() {
        val prodConfig = java.io.File("src/main/resources/logback-prod.xml")
        // Проверяем только если файл присутствует в сборке
        if (prodConfig.exists()) {
            val content = prodConfig.readText()
            // Признак опасной комбинации: включённый вывод PII в проде
            val hasTrueDebugPii = content.contains("\"true\"") &&
                content.contains("DEBUG_INCLUDES_PII")
            assertFalse(hasTrueDebugPii,
                "logback-prod.xml must have DEBUG_INCLUDES_PII=false")
        }
    }
}
