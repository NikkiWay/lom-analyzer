/*
 * НАЗНАЧЕНИЕ
 * Экспорт результатов сессии в JSON (этап 9 архитектуры — экспорт). Как и
 * CsvExporter, поддерживает приватность: в обезличенном режиме имя сессии и
 * идентификаторы авторов скрываются/хешируются.
 *
 * ЧТО ВНУТРИ
 *  - @Serializable data class JsonSessionExport — корневой объект экспорта
 *    (версия схемы, id/имя сессии, режим приватности, список оценок);
 *  - @Serializable data class JsonAuthorScores — оценки по одному автору;
 *  - class JsonExporter — export(), createSalt(), hashAuthorId().
 *
 * МЕТОД
 * Сериализация выполняется kotlinx.serialization (Json с prettyPrint). Соль для
 * хеширования генерируется криптостойким SecureRandom, хеширование id — PiiHasher.
 *
 * СВЯЗИ
 * Регистрируется как single в Koin (di/AppModule.kt). Логирует событие EXPORT_CSV
 * (с пометкой format=JSON) через Logger. Хеширование ПДн — security/PiiHasher.
 *
 * БИБЛИОТЕКИ
 * kotlinx.serialization — JSON serialization; java.io.File — запись;
 * java.security.SecureRandom — генерация соли.
 */
package com.example.lomanalyzer.export

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.security.PiiHasher
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom

/**
 * Корневой объект JSON-экспорта сессии.
 *
 * @param schemaVersion версия схемы экспорта (для обратной совместимости).
 * @param sessionId идентификатор сессии.
 * @param sessionName имя сессии (в обезличенном режиме заменяется на "Session #id").
 * @param privacyMode режим приватности: "RAW" или "HASHED".
 * @param scores список оценок по авторам.
 */
@Serializable
data class JsonSessionExport(
    val schemaVersion: String = "2.0",
    val sessionId: Int,
    val sessionName: String,
    val privacyMode: String,
    val scores: List<JsonAuthorScores>,
)

/**
 * Оценки по одному автору для JSON-экспорта (11 оценок по 4 осям + роль и достаточность).
 * Все числовые поля nullable со значением по умолчанию null — если оценка не рассчитана.
 *
 * @param authorHash обезличенный (солёный) идентификатор автора.
 * @param aud размер аудитории (Aud).
 * @param age возраст/стаж аккаунта (Age).
 * @param erBg вовлечённость в фоновом периоде (ER_bg).
 * @param topVol объём тематических постов (Top_vol).
 * @param topFocus тематическая сфокусированность (Top_focus).
 * @param reach охват распространения (Reach).
 * @param posPositive доля положительной позиции автора (Pos_a).
 * @param posNegative доля отрицательной позиции автора (Pos_a).
 * @param erTop вовлечённость в тематических постах (ER_top).
 * @param respPositive доля положительного отклика аудитории (Resp_a).
 * @param respNegative доля отрицательного отклика аудитории (Resp_a).
 * @param role назначенная базовая роль.
 * @param sufficiency индикатор достаточности данных.
 */
@Serializable
data class JsonAuthorScores(
    val authorHash: String,
    val aud: Float? = null,
    val age: Float? = null,
    val erBg: Float? = null,
    val topVol: Int? = null,
    val topFocus: Float? = null,
    val reach: Float? = null,
    val posPositive: Float? = null,
    val posNegative: Float? = null,
    val erTop: Float? = null,
    val respPositive: Float? = null,
    val respNegative: Float? = null,
    val role: String? = null,
    val sufficiency: String? = null,
)

/**
 * Экспортёр результатов сессии в JSON.
 *
 * @param logger логгер для фиксации событий экспорта.
 */
class JsonExporter(private val logger: Logger) {
    /** Настроенный JSON-сериализатор с человекочитаемым форматированием. */
    private val json = Json { prettyPrint = true }

    /**
     * Экспортирует оценки сессии в JSON-файл с учётом режима приватности.
     *
     * @param sessionId идентификатор сессии.
     * @param sessionName имя сессии (используется только в сыром режиме).
     * @param scores оценки по авторам (ожидается, что id уже хешированы для HASHED-режима).
     * @param outputFile файл назначения.
     * @param rawMode сырой режим (с реальным именем сессии).
     */
    fun export(
        sessionId: Int,
        sessionName: String,
        scores: List<JsonAuthorScores>,
        outputFile: File,
        rawMode: Boolean = false,
    ) {
        // Метка режима приватности для записи в файл
        val privacyMode = if (rawMode) "RAW" else "HASHED"
        val export = JsonSessionExport(
            sessionId = sessionId,
            // В обезличенном режиме скрываем реальное имя сессии
            sessionName = if (rawMode) sessionName else "Session #$sessionId",
            privacyMode = privacyMode,
            scores = scores,
        )
        // Сериализуем объект в JSON и пишем в файл одной операцией
        outputFile.writeText(json.encodeToString(export))
        // Логируем экспорт (формат JSON, режим приватности, имя файла)
        logger.event(AppEvent.EXPORT_CSV, mapOf("format" to "JSON", "mode" to privacyMode, "file" to outputFile.name))
    }

    /** Генерирует криптостойкую 16-байтовую соль для хеширования идентификаторов. */
    fun createSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    /**
     * Возвращает солёный хеш VK id автора (для обезличивания перед экспортом).
     *
     * @param vkId VK id автора.
     * @param salt соль.
     */
    fun hashAuthorId(vkId: Int, salt: ByteArray): String = PiiHasher.hash(vkId.toString(), salt)
}
