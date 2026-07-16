/*
 * НАЗНАЧЕНИЕ
 * Журнал аудита значимых действий приложения (вход/выход, операции с данными
 * и т. п.) для прослеживаемости. Часть модуля безопасности и наблюдаемости
 * (observability).
 *
 * ЧТО ВНУТРИ
 * data class AuditEntry — одна запись аудита (время, действие, детали).
 * class AuditLog — приёмник событий: дублирует запись в структурированный
 * Logger и (опционально) в БД через AuditDao.
 * interface AuditDao — контракт сохранения записей аудита в базу.
 *
 * БИБЛИОТЕКИ
 * java.time.Instant — метка времени события; собственный Logger проекта.
 *
 * СВЯЗИ
 * Logger (observability), реализация AuditDao на стороне storage/dao.
 */
package com.example.lomanalyzer.security

import com.example.lomanalyzer.observability.Logger
import java.time.Instant

/**
 * Запись журнала аудита.
 *
 * @property timestamp момент события.
 * @property action код/имя действия (например, "VK_LOGIN").
 * @property details дополнительные пары ключ-значение, описывающие событие.
 */
data class AuditEntry(
    val timestamp: Instant,
    val action: String,
    val details: Map<String, String> = emptyMap(),
)

/**
 * Пишет события аудита одновременно в структурированный логгер и в базу данных.
 *
 * @param logger логгер для немедленной записи в общий журнал.
 * @param dao опциональный DAO для сохранения записей в БД (может быть null).
 */
class AuditLog(
    private val logger: Logger,
    private val dao: AuditDao? = null,
) {
    /**
     * Фиксирует действие: формирует AuditEntry с текущим временем, сохраняет его
     * в БД (если задан dao) и дублирует в общий лог.
     */
    fun record(action: String, details: Map<String, String> = emptyMap()) {
        val entry = AuditEntry(
            timestamp = Instant.now(),
            action = action,
            details = details,
        )
        // Сохранение в БД выполняется только если DAO предоставлен
        dao?.insert(entry)
        // Дублируем в структурированный лог; значения приводим к Any? для API логгера
        logger.info("AUDIT: $action", details.mapValues { it.value as Any? })
    }
}

/** Контракт DAO для сохранения записей аудита в базу данных. */
interface AuditDao {
    fun insert(entry: AuditEntry)
}
