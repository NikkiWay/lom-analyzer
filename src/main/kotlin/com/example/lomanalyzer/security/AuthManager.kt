/*
 * НАЗНАЧЕНИЕ
 * Менеджер авторизации в VK: связывает OAuth-поток (VK ID, OAuth 2.1 + PKCE)
 * с зашифрованным хранилищем токена (TokenVault) и хранением метаданных сессии.
 * Отвечает за вход, выход и определение текущего состояния «залогинен или нет».
 * Часть модуля безопасности (см. docs/architecture.md, авторизация и сбор VK).
 *
 * ЧТО ВНУТРИ
 * Класс AuthManager: запуск входа через VK ID (startVkIdLogin), завершение входа
 * по redirect-URL (completeVkIdLogin), вход по готовому токену (loginWithToken),
 * выход (logout), чтение/сохранение/удаление информации о сессии.
 * Вложенный data class SessionInfo — метаданные сессии (id пользователя, имя,
 * id VK-приложения). Sealed class VkIdLoginResult — результат входа (Success/Error).
 *
 * МЕТОД
 * VK ID OAuth 2.1 с PKCE: приложение открывает страницу авторизации в браузере,
 * пользователь логинится, VK возвращает redirect-URL с кодом, который
 * обменивается на токены (OAuthFlow.exchangeCodeForTokens). Сам токен доступа
 * шифруется и хранится в TokenVault, метаданные сессии — в JSON-файле.
 *
 * БИБЛИОТЕКИ
 * kotlinx.serialization (Json) — сериализация SessionInfo в JSON; java.awt.Desktop
 * — открытие URL авторизации в системном браузере; java.nio.file — файл сессии.
 *
 * СВЯЗИ
 * OAuthFlow (обмен кодов на токены, хранение токена), TokenVault (наличие токена),
 * Logger/AppEvent (события VK_LOGIN/VK_LOGOUT).
 */
package com.example.lomanalyzer.security

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.vk.OAuthFlow
import com.example.lomanalyzer.vk.VkIdAuthException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Управляет авторизацией пользователя в VK и состоянием сессии.
 *
 * @param oAuthFlow реализация OAuth-потока VK ID (построение URL, обмен кодов, хранение токена).
 * @param tokenVault зашифрованное хранилище токена (для проверки наличия токена).
 * @param logger структурированный логгер для событий входа/выхода.
 * @param sessionInfoFile путь к JSON-файлу с метаданными сессии.
 */
class AuthManager(
    private val oAuthFlow: OAuthFlow,
    private val tokenVault: TokenVault,
    private val logger: Logger,
    private val sessionInfoFile: Path,
) {
    /**
     * Метаданные текущей сессии VK (сохраняются в JSON, не содержат токена).
     *
     * @property vkUserId числовой id пользователя VK.
     * @property firstName имя пользователя.
     * @property lastName фамилия пользователя.
     * @property vkAppId id VK-приложения, через которое выполнен вход.
     */
    @Serializable
    data class SessionInfo(
        val vkUserId: Long = 0,
        val firstName: String = "",
        val lastName: String = "",
        val vkAppId: String = "",
    ) {
        /** Отображаемое имя «Имя Фамилия»; если пусто — заглушка «VK User». */
        val displayName: String
            get() = "$firstName $lastName".trim().ifEmpty { "VK User" }
    }

    /** Результат входа через VK ID: успех с id пользователя либо ошибка с сообщением. */
    sealed class VkIdLoginResult {
        data class Success(val userId: Long) : VkIdLoginResult()
        data class Error(val message: String) : VkIdLoginResult()
    }

    /** Конфигурация JSON: красивый вывод и игнорирование неизвестных полей. */
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    /** Кэш метаданных сессии в памяти, чтобы не читать файл повторно. */
    private var cachedInfo: SessionInfo? = null

    /** Залогинен ли пользователь — определяется по наличию токена в хранилище. */
    fun isLoggedIn(): Boolean = tokenVault.hasToken()

    /** Возвращает действующий токен доступа из OAuth-потока (или null). */
    fun getAccessToken(): String? = oAuthFlow.getStoredToken()

    // ── VK ID OAuth 2.1 + PKCE ──

    /**
     * Начинает вход через VK ID: строит URL авторизации (с PKCE) и открывает его
     * в системном браузере. Возвращает этот URL (на случай ручного копирования).
     */
    fun startVkIdLogin(clientId: String): String {
        val authUrl = oAuthFlow.buildVkIdAuthUrl(clientId)
        openInBrowser(authUrl)
        return authUrl
    }

    /**
     * Завершает вход через VK ID по redirect-URL: извлекает код авторизации,
     * обменивает его на токены, сохраняет метаданные сессии и логирует событие.
     *
     * @param redirectUrl URL, на который VK вернул пользователя после авторизации.
     * @return Success с id пользователя либо Error с текстом ошибки.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun completeVkIdLogin(clientId: String, redirectUrl: String): VkIdLoginResult {
        return try {
            // Разбираем redirect-URL и извлекаем код авторизации + device id
            val result = oAuthFlow.parseVkIdRedirect(redirectUrl)
                ?: return VkIdLoginResult.Error("Не удалось извлечь код авторизации из URL")

            // Обмениваем код на токены (этап обмена в OAuth 2.1 + PKCE)
            val tokenResponse = oAuthFlow.exchangeCodeForTokens(
                code = result.code,
                clientId = clientId,
                redirectUri = "https://oauth.vk.com/blank.html",
                callbackDeviceId = result.deviceId,
            )

            // Сохраняем метаданные сессии (сам токен уже сохранён в OAuthFlow/Vault)
            val info = SessionInfo(
                vkUserId = tokenResponse.userId,
                vkAppId = clientId,
            )
            saveSessionInfo(info)
            logger.event(AppEvent.VK_LOGIN, mapOf("method" to "vk_id"))
            VkIdLoginResult.Success(tokenResponse.userId)
        } catch (e: VkIdAuthException) {
            // Известная ошибка авторизации VK ID — отдаём её сообщение
            VkIdLoginResult.Error(e.message ?: "Ошибка авторизации VK ID")
        } catch (e: Exception) {
            // Любая иная ошибка (сеть, разбор ответа и т. п.)
            VkIdLoginResult.Error("Ошибка: ${e.message}")
        }
    }

    // ── Other methods ──

    /** Вход по уже имеющемуся токену доступа: сохраняет токен и метаданные сессии. */
    fun loginWithToken(accessToken: String, info: SessionInfo) {
        oAuthFlow.storeTokens(accessToken)
        saveSessionInfo(info)
        logger.event(AppEvent.VK_LOGIN, mapOf("method" to "token"))
    }

    // ── Session ──

    /** Выход: удаляет токен из хранилища, стирает файл сессии и кэш, логирует событие. */
    fun logout() {
        tokenVault.removeToken()
        deleteSessionInfo()
        cachedInfo = null
        logger.event(AppEvent.VK_LOGOUT)
    }

    /**
     * Возвращает метаданные текущей сессии: сначала из кэша, затем из JSON-файла.
     * При ошибке чтения/разбора возвращает null.
     */
    fun getSessionInfo(): SessionInfo? {
        cachedInfo?.let { return it }
        if (!Files.exists(sessionInfoFile)) return null
        return try {
            val text = Files.readString(sessionInfoFile)
            json.decodeFromString<SessionInfo>(text).also { cachedInfo = it }
        } catch (_: Exception) {
            null
        }
    }

    /** Сохраняет метаданные сессии в JSON-файл и обновляет кэш. */
    private fun saveSessionInfo(info: SessionInfo) {
        cachedInfo = info
        Files.createDirectories(sessionInfoFile.parent)
        Files.writeString(sessionInfoFile, json.encodeToString(info))
    }

    /** Удаляет JSON-файл сессии, если он существует. */
    private fun deleteSessionInfo() {
        Files.deleteIfExists(sessionInfoFile)
    }

    /** Открывает URL в системном браузере; при ошибке молча игнорирует (пользователь скопирует вручную). */
    private fun openInBrowser(url: String) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
        } catch (_: Exception) { /* user can copy manually */ }
    }
}
