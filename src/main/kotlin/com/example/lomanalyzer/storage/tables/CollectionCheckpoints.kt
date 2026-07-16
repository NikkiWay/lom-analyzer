/*
 * НАЗНАЧЕНИЕ
 * Декларативное описание таблицы "collection_checkpoint" — контрольные точки
 * сбора данных из VK API (этап 2). Позволяют возобновлять сбор после сбоя или
 * паузы: для каждого endpoint и владельца стены хранится текущий offset и число
 * собранных элементов.
 *
 * ЧТО ВНУТРИ
 * Один object-таблица CollectionCheckpoints (Exposed ORM). Привязана к сессии;
 * фиксирует прогресс по конкретному вызову VK API.
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — IntIdTable (суррогатный primary key "id"). foreign key на
 * analysis_session.
 *
 * СВЯЗИ
 * session_id -> analysis_session.id. Сами данные сбора кладутся в post/comment.
 */
package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

/**
 * Таблица контрольных точек сбора из VK API ("collection_checkpoint").
 *
 * IntIdTable (суррогатный primary key "id"). Хранит состояние возобновляемого
 * сбора по паре endpoint + владелец стены.
 */
object CollectionCheckpoints : IntIdTable("collection_checkpoint") {
    /** foreign key на analysis_session.id — сессия, к которой относится прогресс сбора. */
    val sessionId = reference("session_id", AnalysisSessions)
    /** Имя метода VK API, по которому идёт сбор (например, wall.get, newsfeed.search). */
    val endpoint = text("endpoint")
    /** Идентификатор владельца стены/источника во ВКонтакте (положительный — пользователь, отрицательный — сообщество). */
    val ownerId = integer("owner_id")
    /** Сохранённое значение offset/курсора пагинации VK API для продолжения сбора; может отсутствовать. */
    val offsetValue = text("offset_value").nullable()
    /** Сколько элементов уже собрано по этой контрольной точке; по умолчанию 0. */
    val itemsCollected = integer("items_collected").default(0)
    /** Статус сбора: по умолчанию "IN_PROGRESS"; меняется на завершённый/ошибочный. */
    val status = text("status").default("IN_PROGRESS")
    /** Момент создания записи (Unix-время, мс). */
    val createdAt = long("created_at")
    /** Момент последнего обновления прогресса (Unix-время, мс). */
    val updatedAt = long("updated_at")
}
