/*
 * НАЗНАЧЕНИЕ
 * DAO (слой доступа к данным) для журнала аудита (audit log) — записей о действиях
 * пользователя и системы. Реализует интерфейс AuditDao из модуля security, давая
 * подсистеме аудита возможность писать события, не зная деталей хранилища.
 * Относится к слою хранения (storage): по архитектуре модули обмениваются данными
 * ТОЛЬКО через SQLite, а DAO инкапсулирует все SQL-запросы к таблице audit_logs.
 *
 * ЧТО ВНУТРИ
 * Класс AuditLogDao: insert (запись события аудита из доменного объекта AuditEntry),
 * insertWithSession (запись события, привязанного к сессии анализа),
 * findBySession (выборка всех событий сессии по убыванию времени).
 *
 * БИБЛИОТЕКИ
 * Exposed ORM — типобезопасный DSL для SQL (insert, selectAll, where, orderBy).
 * kotlinx.serialization (Json) — сериализация map с деталями события в JSON-строку.
 * java.time.Instant — отметки времени в миллисекундах эпохи (epoch millis).
 *
 * СВЯЗИ
 * Таблица AuditLogs (storage/tables). Интерфейс AuditDao и модель AuditEntry (security).
 */
package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.security.AuditDao
import com.example.lomanalyzer.security.AuditEntry
import com.example.lomanalyzer.storage.tables.AuditLogs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * DAO журнала аудита. Все методы выполняются в отдельной Exposed transaction на
 * переданном подключении db. Реализует AuditDao, чтобы модуль security мог писать
 * события, не завися напрямую от Exposed.
 *
 * @param db подключение к БД (Exposed Database), на котором открываются транзакции.
 */
class AuditLogDao(private val db: Database) : AuditDao {
    /**
     * INSERT одной записи аудита из доменного объекта AuditEntry.
     * Детали (map) сериализуются в JSON; пустой map сохраняется как NULL.
     * Ничего не возвращает (контракт интерфейса AuditDao).
     */
    override fun insert(entry: AuditEntry) {
        transaction(db) {
            AuditLogs.insert {
                // Тип/название действия как есть из доменной модели
                it[action] = entry.action
                // Детали события: если map непустой — кодируем в JSON-строку, иначе NULL
                it[details] = if (entry.details.isNotEmpty()) Json.encodeToString(entry.details) else null
                // Время события переводим в epoch millis для хранения как INTEGER
                it[createdAt] = entry.timestamp.toEpochMilli()
            }
        }
    }

    /**
     * INSERT записи аудита, привязанной к конкретной сессии анализа.
     * @param sessionId идентификатор сессии анализа.
     * @param action название действия.
     * @param details произвольные детали в виде готовой строки (например, JSON) или NULL.
     * @return результат insert-выражения Exposed (InsertStatement).
     */
    fun insertWithSession(sessionId: Int, action: String, details: String? = null) = transaction(db) {
        AuditLogs.insert {
            it[AuditLogs.sessionId] = sessionId
            it[AuditLogs.action] = action
            it[AuditLogs.details] = details
            // Текущее время записи в epoch millis
            it[createdAt] = java.time.Instant.now().toEpochMilli()
        }
    }

    /**
     * SELECT всех записей аудита по сессии.
     * @return список ResultRow (сырые строки Exposed), отсортированных по времени
     *         создания по убыванию (DESC) — новые события сверху.
     */
    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        // WHERE session_id = ? ORDER BY created_at DESC
        AuditLogs.selectAll().where { AuditLogs.sessionId eq sessionId }
            .orderBy(AuditLogs.createdAt, SortOrder.DESC)
            .toList()
    }
}
