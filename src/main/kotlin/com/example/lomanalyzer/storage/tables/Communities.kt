/*
 * НАЗНАЧЕНИЕ
 * Декларативное описание таблицы "community" — справочник сообществ (пабликов,
 * групп) ВКонтакте, попавших в анализ. Используется на этапе 2 (сбор данных)
 * для хранения метаданных площадок, со стен которых собираются посты.
 *
 * ЧТО ВНУТРИ
 * Один object-таблица Communities (Exposed ORM). Связана с сессиями анализа
 * через таблицу-связку session_community (см. LinkTables.kt). На авторов и
 * посты напрямую не ссылается.
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — IntIdTable добавляет суррогатный целочисленный primary key "id"
 * с автоинкрементом; остальные колонки описываются декларативно (text, integer,
 * bool, long, nullable, uniqueIndex, default). Таблица создаётся миграциями
 * Flyway (V1–V10), Exposed-описание используется для типобезопасных запросов.
 *
 * СВЯЗИ
 * session_community (LinkTables.kt) связывает community с analysis_session.
 */
package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

/**
 * Таблица сообществ ВКонтакте ("community").
 *
 * Хранит идентификацию и метаданные паблика/группы. Наследует от IntIdTable —
 * значит, имеет суррогатный primary key "id" (внутренний идентификатор в БД),
 * отдельный от vkId (идентификатора во ВКонтакте).
 */
object Communities : IntIdTable("community") {
    /** Числовой идентификатор сообщества во ВКонтакте; uniqueIndex — каждое сообщество хранится один раз. */
    val vkId = integer("vk_id").uniqueIndex()
    /** Отображаемое название сообщества. */
    val name = text("name")
    /** Короткое имя (буквенный slug в URL вида vk.com/screen_name); может отсутствовать. */
    val screenName = text("screen_name").nullable()
    /** Число подписчиков сообщества на момент сбора; null, если недоступно. */
    val membersCount = integer("members_count").nullable()
    /** Признак закрытого сообщества (контент скрыт от не-участников); по умолчанию false. */
    val isClosed = bool("is_closed").default(false)
    /** Тип сообщества из VK API (group, page, event и т. п.); может отсутствовать. */
    val communityType = text("community_type").nullable()
    /** Момент создания записи в БД (Unix-время, мс). */
    val createdAt = long("created_at")
    /** Момент последнего обновления записи в БД (Unix-время, мс). */
    val updatedAt = long("updated_at")
    /** Момент мягкого удаления (soft delete); null — запись активна. */
    val deletedAt = long("deleted_at").nullable()
}
