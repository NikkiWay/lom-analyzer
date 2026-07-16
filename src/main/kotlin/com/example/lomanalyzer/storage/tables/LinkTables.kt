/*
 * НАЗНАЧЕНИЕ
 * Декларативное описание таблиц-связок (many-to-many) между сессией и её
 * сущностями: session_community (сессия ↔ сообщества) и session_author
 * (сессия ↔ авторы). Реализуют принадлежность объектов к конкретной сессии
 * без дублирования самих объектов.
 *
 * ЧТО ВНУТРИ
 * Два object-таблицы: SessionCommunities и SessionAuthors (Exposed ORM). У обеих
 * составной primary key из двух foreign key — пара ключей уникальна.
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — обычная Table (без суррогатного id), составной primary key
 * через PrimaryKey(...). reference(...) задаёт foreign key.
 *
 * СВЯЗИ
 * session_community: session_id -> analysis_session.id, community_id -> community.id.
 * session_author: session_id -> analysis_session.id, author_id -> author.id.
 */
package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.sql.Table

/**
 * Таблица-связка "session_community" — какие сообщества входят в сессию.
 *
 * Обычная Table со составным primary key (session_id, community_id): одна и та
 * же пара не повторяется (сообщество привязано к сессии однократно).
 */
object SessionCommunities : Table("session_community") {
    /** foreign key на analysis_session.id. */
    val sessionId = reference("session_id", AnalysisSessions)
    /** foreign key на community.id. */
    val communityId = reference("community_id", Communities)
    /** Составной primary key — уникальность пары (сессия, сообщество). */
    override val primaryKey = PrimaryKey(sessionId, communityId)
}

/**
 * Таблица-связка "session_author" — какие авторы входят в сессию, с их ролью.
 *
 * Обычная Table со составным primary key (session_id, author_id): автор привязан
 * к сессии однократно.
 */
object SessionAuthors : Table("session_author") {
    /** foreign key на analysis_session.id. */
    val sessionId = reference("session_id", AnalysisSessions)
    /** foreign key на author.id. */
    val authorId = reference("author_id", Authors)
    /** Роль автора в рамках сессии (например, ядро/аудитория); может отсутствовать. */
    val role = text("role").nullable()
    /** Составной primary key — уникальность пары (сессия, автор). */
    override val primaryKey = PrimaryKey(sessionId, authorId)
}
