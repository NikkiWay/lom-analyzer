/*
 * НАЗНАЧЕНИЕ
 * DAO (слой доступа к данным) для контрольных точек сбора (таблица
 * collection_checkpoints). Позволяет возобновлять долгий сбор данных из VK API
 * после прерывания: хранит прогресс по каждому endpoint и владельцу (ownerId) —
 * текущий offset/курсор, число собранных элементов и статус. Относится к этапу
 * сбора данных. Обмен между модулями — только через SQLite.
 *
 * ЧТО ВНУТРИ
 * Класс CheckpointDao: insert (создать контрольную точку и вернуть id),
 * updateProgress (обновить прогресс/курсор/статус), findBySession (все точки сессии).
 *
 * БИБЛИОТЕКИ
 * Exposed ORM — DSL запросов. java.time.Instant — created_at/updated_at в epoch millis.
 *
 * СВЯЗИ
 * Таблица CollectionCheckpoints (storage/tables). Используется сборщиками vk/.
 */
package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.CollectionCheckpoints
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * DAO контрольных точек сбора. Каждый метод — отдельная транзакция.
 *
 * @param db подключение к БД (Exposed Database).
 */
class CheckpointDao(private val db: Database) {
    /**
     * INSERT новой контрольной точки сбора.
     * @param endpoint имя метода VK API, по которому ведётся сбор.
     * @param ownerId идентификатор владельца (сообщества/пользователя), чьи данные собираются.
     * @return сгенерированный id контрольной точки.
     */
    fun insert(
        sessionId: Int,
        endpoint: String,
        ownerId: Int,
    ): Int = transaction(db) {
        // Единая отметка времени для created_at и updated_at при создании
        val now = Instant.now().toEpochMilli()
        CollectionCheckpoints.insertAndGetId {
            it[CollectionCheckpoints.sessionId] = sessionId
            it[CollectionCheckpoints.endpoint] = endpoint
            it[CollectionCheckpoints.ownerId] = ownerId
            it[createdAt] = now
            it[updatedAt] = now
        }.value
    }

    /**
     * UPDATE прогресса контрольной точки по id.
     * @param offset текущий курсор/смещение пагинации VK (или NULL, если сбор завершён).
     * @param itemsCollected накопленное число собранных элементов.
     * @param status статус сбора (например, IN_PROGRESS/DONE).
     */
    fun updateProgress(id: Int, offset: String?, itemsCollected: Int, status: String) = transaction(db) {
        CollectionCheckpoints.update({ CollectionCheckpoints.id eq id }) {
            // Сохраняем курсор пагинации (offsetValue) для возобновления сбора
            it[offsetValue] = offset
            it[CollectionCheckpoints.itemsCollected] = itemsCollected
            it[CollectionCheckpoints.status] = status
            it[updatedAt] = Instant.now().toEpochMilli()
        }
    }

    /**
     * SELECT всех контрольных точек сессии.
     * @return список ResultRow.
     */
    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        CollectionCheckpoints.selectAll()
            .where { CollectionCheckpoints.sessionId eq sessionId }
            .toList()
    }
}
