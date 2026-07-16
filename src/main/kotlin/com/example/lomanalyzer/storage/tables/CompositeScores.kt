/*
 * НАЗНАЧЕНИЕ
 * Декларативное описание трёх таблиц этапа 7 (композиты, пороги и роли):
 *   - composite_score   — свёрнутые композитные оценки автора по осям;
 *   - session_threshold — пороги классификации (theta) на уровне сессии;
 *   - author_role       — итоговая квадрантная роль автора и атрибуты.
 *
 * ЧТО ВНУТРИ
 * Три object-таблицы (Exposed ORM): CompositeScores, SessionThresholds,
 * AuthorRoles. Все привязаны к сессии; первая и третья — ещё и к автору.
 *
 * МЕТОД
 * Композитные веса 1/3,1/3,1/3 (OECD Handbook), робастная z-нормализация.
 * Квадрантная классификация: сравнение композитов с порогами theta_struct /
 * theta_topic; 4 базовые роли + атрибуты позиции и отклика (см. docs/algorithm.md,
 * раздел 2.1.6).
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — все три IntIdTable (суррогатный primary key "id"). foreign key
 * на analysis_session (и author). У session_threshold session_id с uniqueIndex —
 * один набор порогов на сессию.
 *
 * СВЯЗИ
 * session_id -> analysis_session.id; author_id -> author.id. Вход: lom_score.
 */
package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

/**
 * Таблица композитных оценок автора ("composite_score").
 *
 * IntIdTable (суррогатный primary key "id"). Хранит свёртку осей в композиты,
 * по которым затем определяется квадрантная роль.
 */
object CompositeScores : IntIdTable("composite_score") {
    /** foreign key на analysis_session.id — сессия расчёта. */
    val sessionId = reference("session_id", AnalysisSessions)
    /** foreign key на author.id — автор. */
    val authorId = reference("author_id", Authors)
    /** Структурный композит (свёртка оси структурного влияния). */
    val structComposite = float("struct_composite")
    /** Тематический композит (свёртка оси тематической активности). */
    val topicComposite = float("topic_composite")
    /** Момент расчёта композитов (Unix-время, мс). */
    val createdAt = long("created_at")
}

/**
 * Таблица порогов классификации сессии ("session_threshold").
 *
 * IntIdTable (суррогатный primary key "id"). Один набор порогов на сессию
 * (session_id с uniqueIndex). Пороги делят пространство композитов на квадранты.
 */
object SessionThresholds : IntIdTable("session_threshold") {
    /** foreign key на analysis_session.id с uniqueIndex — ровно один набор порогов на сессию. */
    val sessionId = reference("session_id", AnalysisSessions).uniqueIndex()
    /** Порог theta по структурному композиту (граница квадранта). */
    val thetaStruct = float("theta_struct")
    /** Порог theta по тематическому композиту (граница квадранта). */
    val thetaTopic = float("theta_topic")
    /** Момент расчёта порогов (Unix-время, мс). */
    val createdAt = long("created_at")
}

/**
 * Таблица итоговых ролей авторов ("author_role").
 *
 * IntIdTable (суррогатный primary key "id"). Хранит результат квадрантной
 * классификации: базовую роль, атрибуты позиции/отклика и метку достаточности.
 */
object AuthorRoles : IntIdTable("author_role") {
    /** foreign key на analysis_session.id — сессия. */
    val sessionId = reference("session_id", AnalysisSessions)
    /** foreign key на author.id — автор. */
    val authorId = reference("author_id", Authors)
    /** Базовая роль автора (одна из 4 квадрантных). */
    val baseRole = text("base_role")
    /** Атрибут позиции автора по теме (на основе оси 3). */
    val positionAttr = text("position_attr")
    /** Атрибут отклика аудитории (на основе оси 4). */
    val responseAttr = text("response_attr")
    /** Метка достаточности данных для роли; по умолчанию "PRELIMINARY" (предварительная). */
    val sufficiency = text("sufficiency").default("PRELIMINARY")
    /** Момент назначения роли (Unix-время, мс). */
    val createdAt = long("created_at")
}
