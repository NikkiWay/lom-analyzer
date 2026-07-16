/*
 * НАЗНАЧЕНИЕ
 * Декларативное описание таблицы "dedup_group" — результаты дедупликации постов
 * (этап 5): пары "канонический пост — его near-дубликат" с мерой сходства.
 * Позволяет исключать повторы при подсчёте объёма и оригинальности контента.
 *
 * ЧТО ВНУТРИ
 * Один object-таблица DedupGroups (Exposed ORM). Каждая строка — связь двух
 * постов (canonical и duplicate) в рамках одной сессии, с числом сходства и
 * методом сравнения.
 *
 * МЕТОД
 * Сходство считает NormalizedLevenshtein (нормализованное расстояние Левенштейна
 * по леммам, порог 0.90, временное окно). См. docs/formulas.md, этап 5.
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — IntIdTable (суррогатный primary key "id"). Три foreign key:
 * на session, на канонический post и на дублирующий post.
 *
 * СВЯЗИ
 * session_id -> analysis_session.id; canonical_post_id и duplicate_post_id ->
 * post.id (Posts.kt).
 */
package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

/**
 * Таблица групп дубликатов постов ("dedup_group").
 *
 * IntIdTable (суррогатный primary key "id"). Хранит пары "канонический пост —
 * near-дубликат" внутри сессии с количественной мерой сходства.
 */
object DedupGroups : IntIdTable("dedup_group") {
    /** foreign key на analysis_session.id — сессия, в рамках которой проведена дедупликация. */
    val sessionId = reference("session_id", AnalysisSessions)
    /** foreign key на post.id — канонический (оставляемый) пост группы. */
    val canonicalPostId = reference("canonical_post_id", Posts)
    /** foreign key на post.id — пост, признанный near-дубликатом канонического. */
    val duplicatePostId = reference("duplicate_post_id", Posts)
    /** Мера сходства пары [0..1]: 1.0 — идентичны, ≥ порога (0.90) — near-дубликаты. */
    val similarity = float("similarity")
    /** Метод сравнения (например, NormalizedLevenshtein по леммам). */
    val method = text("method")
}
