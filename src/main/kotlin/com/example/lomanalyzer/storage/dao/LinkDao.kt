/*
 * НАЗНАЧЕНИЕ
 * DAO (слой доступа к данным) для связей «многие-ко-многим» между сессией анализа и
 * сообществами/авторами (таблицы-связки session_communities и session_authors).
 * Привязывает к сессии выбранные сообщества (источники) и обнаруженных авторов.
 * Обмен между модулями — только через SQLite.
 *
 * ЧТО ВНУТРИ
 * Класс LinkDao: linkSessionCommunity/linkSessionAuthor (создание связей),
 * getCommunitiesForSession/getAuthorsForSession (выборки связей),
 * getCommunityVkIdsForSession (JOIN с Communities для извлечения внешних vkId).
 *
 * БИБЛИОТЕКИ
 * Exposed ORM — DSL запросов, в т. ч. innerJoin для соединения таблиц.
 *
 * СВЯЗИ
 * Таблицы SessionCommunities, SessionAuthors, Communities (storage/tables).
 */
package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * DAO связей сессии с сообществами и авторами. Каждый метод — отдельная транзакция.
 *
 * @param db подключение к БД (Exposed Database).
 */
class LinkDao(private val db: Database) {
    /**
     * Идемпотентный INSERT связи «сессия — сообщество».
     *
     * Существующая связь — не ошибка: стадия сбора может выполниться повторно
     * (возобновление с контрольной точки, повторный импорт того же файла), а сами
     * сообщества дедуплицируются по vkId и получают тот же id. Без проверки такой
     * повтор падал с нарушением первичного ключа (session_id, community_id).
     */
    fun linkSessionCommunity(sessionId: Int, communityId: Int) = transaction(db) {
        val existing = SessionCommunities.selectAll().where {
            (SessionCommunities.sessionId eq sessionId) and
                (SessionCommunities.communityId eq communityId)
        }.any()
        if (!existing) {
            SessionCommunities.insert {
                it[SessionCommunities.sessionId] = sessionId
                it[SessionCommunities.communityId] = communityId
            }
        }
    }

    /**
     * Идемпотентный INSERT связи «сессия — автор» (см. linkSessionCommunity).
     * @param role необязательная роль автора в рамках связи (или NULL).
     */
    fun linkSessionAuthor(sessionId: Int, authorId: Int, role: String? = null) = transaction(db) {
        val existing = SessionAuthors.selectAll().where {
            (SessionAuthors.sessionId eq sessionId) and (SessionAuthors.authorId eq authorId)
        }.any()
        if (!existing) {
            SessionAuthors.insert {
                it[SessionAuthors.sessionId] = sessionId
                it[SessionAuthors.authorId] = authorId
                it[SessionAuthors.role] = role
            }
        }
    }

    /**
     * SELECT всех связей сессии с сообществами.
     * @return список ResultRow таблицы-связки.
     */
    fun getCommunitiesForSession(sessionId: Int): List<ResultRow> = transaction(db) {
        SessionCommunities.selectAll()
            .where { SessionCommunities.sessionId eq sessionId }
            .toList()
    }

    /**
     * SELECT внешних vkId сообществ сессии. Соединяет таблицу-связку с Communities
     * (innerJoin) и проецирует только столбец vkId.
     * @return список внешних идентификаторов сообществ VK.
     */
    fun getCommunityVkIdsForSession(sessionId: Int): List<Int> = transaction(db) {
        // INNER JOIN session_communities с communities, выбор только vkId
        SessionCommunities.innerJoin(Communities)
            .selectAll()
            .where { SessionCommunities.sessionId eq sessionId }
            .map { it[Communities.vkId] }
    }

    /**
     * SELECT всех связей сессии с авторами.
     * @return список ResultRow таблицы-связки.
     */
    fun getAuthorsForSession(sessionId: Int): List<ResultRow> = transaction(db) {
        SessionAuthors.selectAll()
            .where { SessionAuthors.sessionId eq sessionId }
            .toList()
    }
}
