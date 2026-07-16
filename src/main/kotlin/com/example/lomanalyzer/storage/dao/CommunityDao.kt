/*
 * НАЗНАЧЕНИЕ
 * DAO (слой доступа к данным) для сообществ VK (таблица communities). Сообщества —
 * источники постов на этапе сбора (фаза A — тематические посты). Хранит метаданные
 * групп/пабликов. Обмен между модулями — только через SQLite.
 *
 * ЧТО ВНУТРИ
 * Класс CommunityDao: insert (вставка и возврат id), findById/findByVkId (точечные
 * выборки), findAll (все неудалённые сообщества — soft-delete по deletedAt).
 *
 * БИБЛИОТЕКИ
 * Exposed ORM — DSL запросов. java.time.Instant — created_at/updated_at в epoch millis.
 *
 * СВЯЗИ
 * Таблица Communities (storage/tables). vkId — внешний идентификатор сообщества VK.
 */
package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.Communities
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * DAO сообществ VK. Каждый метод — отдельная транзакция.
 *
 * @param db подключение к БД (Exposed Database).
 */
class CommunityDao(private val db: Database) {
    /**
     * INSERT нового сообщества.
     * @param vkId внешний идентификатор сообщества во ВКонтакте.
     * @param membersCount число участников (может быть неизвестно — NULL).
     * @param isClosed признак закрытого сообщества.
     * @param communityType тип сообщества (группа/паблик/событие) или NULL.
     * @return сгенерированный id вставленной строки.
     */
    fun insert(
        vkId: Int,
        name: String,
        screenName: String? = null,
        membersCount: Int? = null,
        isClosed: Boolean = false,
        communityType: String? = null,
    ): Int = transaction(db) {
        // Единая отметка времени для created_at и updated_at при создании
        val now = Instant.now().toEpochMilli()
        Communities.insertAndGetId {
            it[Communities.vkId] = vkId
            it[Communities.name] = name
            it[Communities.screenName] = screenName
            it[Communities.membersCount] = membersCount
            it[Communities.isClosed] = isClosed
            it[Communities.communityType] = communityType
            it[createdAt] = now
            it[updatedAt] = now
        }.value
    }

    /**
     * SELECT сообщества по внутреннему первичному ключу id.
     * @return ResultRow или null.
     */
    fun findById(id: Int): ResultRow? = transaction(db) {
        Communities.selectAll().where { Communities.id eq id }.singleOrNull()
    }

    /**
     * SELECT сообщества по внешнему идентификатору VK.
     * @return ResultRow или null.
     */
    fun findByVkId(vkId: Int): ResultRow? = transaction(db) {
        Communities.selectAll().where { Communities.vkId eq vkId }.singleOrNull()
    }

    /**
     * SELECT всех «живых» сообществ (мягкое удаление: deletedAt IS NULL).
     * @return список ResultRow.
     */
    fun findAll(): List<ResultRow> = transaction(db) {
        Communities.selectAll().where { Communities.deletedAt.isNull() }.toList()
    }
}
