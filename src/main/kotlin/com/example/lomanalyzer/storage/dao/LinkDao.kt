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
     * INSERT связи «сессия — сообщество» в таблицу-связку SessionCommunities.
     */
    fun linkSessionCommunity(sessionId: Int, communityId: Int) = transaction(db) {
        SessionCommunities.insert {
            it[SessionCommunities.sessionId] = sessionId
            it[SessionCommunities.communityId] = communityId
        }
    }

    /**
     * INSERT связи «сессия — автор» в таблицу-связку SessionAuthors.
     * @param role необязательная роль автора в рамках связи (или NULL).
     */
    fun linkSessionAuthor(sessionId: Int, authorId: Int, role: String? = null) = transaction(db) {
        SessionAuthors.insert {
            it[SessionAuthors.sessionId] = sessionId
            it[SessionAuthors.authorId] = authorId
            it[SessionAuthors.role] = role
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
