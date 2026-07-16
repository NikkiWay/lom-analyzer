/*
 * НАЗНАЧЕНИЕ
 * Декларативное описание таблицы "comment" — комментарии к постам, собранные на
 * фазе C трёхфазного сбора. Используются при расчёте отклика аудитории (ось 4):
 * объём, тональность и распределение реакций под постами авторов.
 *
 * ЧТО ВНУТРИ
 * Один object-таблица Comments (Exposed ORM). Ссылается на сессию и на пост;
 * имеет три индекса для типичных выборок (см. блок init).
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — IntIdTable (суррогатный primary key "id"). Индексы объявляются в
 * init { } через uniqueIndex/index.
 *
 * СВЯЗИ
 * session_id -> analysis_session.id; post_id -> post.id (Posts.kt).
 */
package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

/**
 * Таблица комментариев к постам ("comment").
 *
 * IntIdTable (суррогатный primary key "id"). Хранит текст и метрики комментария,
 * привязанные к посту и сессии.
 */
object Comments : IntIdTable("comment") {
    /** foreign key на analysis_session.id — сессия, в рамках которой собран комментарий. */
    val sessionId = reference("session_id", AnalysisSessions)
    /** foreign key на post.id — пост, к которому относится комментарий. */
    val postId = reference("post_id", Posts)
    /** Идентификатор комментария во ВКонтакте. */
    val vkId = integer("vk_id")
    /** Идентификатор автора комментария во ВКонтакте (from_id). */
    val fromId = integer("from_id")
    /** Текст комментария; может отсутствовать. */
    val text = text("text").nullable()
    /** Время публикации комментария (Unix-время, мс). */
    val publishedAt = long("published_at")
    /** Число лайков под комментарием; по умолчанию 0. */
    val likes = integer("likes").default(0)
    /** Момент создания записи в БД (Unix-время, мс). */
    val createdAt = long("created_at")

    init {
        // Уникальность комментария в пределах сессии (защита от повторной вставки при ресборе)
        uniqueIndex(sessionId, vkId)
        // Индекс для быстрой выборки всех комментариев конкретного поста в сессии
        index(false, sessionId, postId)
        // Индекс для быстрой выборки комментариев конкретного автора в сессии (отклик по автору)
        index(false, sessionId, fromId)
    }
}
