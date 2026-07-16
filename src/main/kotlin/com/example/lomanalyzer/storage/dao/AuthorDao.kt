/*
 * НАЗНАЧЕНИЕ
 * DAO (слой доступа к данным) для авторов (таблица authors) — пользователей VK,
 * выступающих кандидатами в лидеры общественного мнения (ЛОМ). Закрывает запись и
 * чтение реестра авторов на этапе сбора данных (фаза B — реестр авторов).
 * По архитектуре весь обмен между модулями идёт через SQLite; DAO прячет SQL.
 *
 * ЧТО ВНУТРИ
 * Класс AuthorDao: insert (добавить автора и вернуть его id), findById/findByVkId
 * (точечные выборки), findAll (все неудалённые авторы — soft-delete по deletedAt),
 * update (частичное обновление произвольных полей через переданный блок).
 *
 * БИБЛИОТЕКИ
 * Exposed ORM — DSL запросов (insertAndGetId, selectAll, where, update).
 * java.time.Instant — отметки created_at/updated_at в epoch millis.
 *
 * СВЯЗИ
 * Таблица Authors (storage/tables). Внешний vkId — идентификатор пользователя VK.
 */
package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.Authors
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * DAO для реестра авторов. Каждый метод открывает отдельную Exposed transaction
 * на подключении db.
 *
 * @param db подключение к БД (Exposed Database).
 */
class AuthorDao(private val db: Database) {
    /**
     * INSERT нового автора.
     * @param vkId идентификатор пользователя во ВКонтакте.
     * @param followersCount число подписчиков (может быть неизвестно — NULL).
     * @param isClosed признак закрытого профиля.
     * @param discoverySource как автор был обнаружен (по умолчанию SEED — стартовый).
     * @return сгенерированный первичный ключ (id) вставленной строки.
     */
    fun insert(
        vkId: Int,
        firstName: String? = null,
        lastName: String? = null,
        screenName: String? = null,
        followersCount: Int? = null,
        isClosed: Boolean = false,
        discoverySource: String = "SEED",
    ): Int = transaction(db) {
        // Единая отметка времени для created_at и updated_at при создании
        val now = Instant.now().toEpochMilli()
        // insertAndGetId возвращает EntityID; .value извлекает числовой id
        Authors.insertAndGetId {
            it[Authors.vkId] = vkId
            it[Authors.firstName] = firstName
            it[Authors.lastName] = lastName
            it[Authors.screenName] = screenName
            it[Authors.followersCount] = followersCount
            it[Authors.isClosed] = isClosed
            it[Authors.discoverySource] = discoverySource
            it[createdAt] = now
            it[updatedAt] = now
        }.value
    }

    /**
     * SELECT автора по внутреннему первичному ключу id.
     * @return ResultRow или null, если автора нет.
     */
    fun findById(id: Int): ResultRow? = transaction(db) {
        Authors.selectAll().where { Authors.id eq id }.singleOrNull()
    }

    /**
     * SELECT автора по внешнему идентификатору VK.
     * @return ResultRow или null, если автор с таким vkId не сохранён.
     */
    fun findByVkId(vkId: Int): ResultRow? = transaction(db) {
        Authors.selectAll().where { Authors.vkId eq vkId }.singleOrNull()
    }

    /**
     * SELECT всех «живых» авторов (мягкое удаление: deletedAt IS NULL).
     * @return список ResultRow всех неудалённых авторов.
     */
    fun findAll(): List<ResultRow> = transaction(db) {
        Authors.selectAll().where { Authors.deletedAt.isNull() }.toList()
    }

    /**
     * UPDATE автора по id. Поля задаёт вызывающий через лямбду block (Exposed
     * UpdateStatement); поле updatedAt проставляется автоматически текущим временем.
     * @param id первичный ключ обновляемого автора.
     * @param block настройка обновляемых столбцов в контексте UpdateStatement.
     */
    fun update(id: Int, block: UpdateStatement.() -> Unit) = transaction(db) {
        Authors.update({ Authors.id eq id }) {
            // Применяем пользовательские присваивания полей
            block(it)
            // Всегда обновляем отметку времени изменения
            it[updatedAt] = Instant.now().toEpochMilli()
        }
    }
}
