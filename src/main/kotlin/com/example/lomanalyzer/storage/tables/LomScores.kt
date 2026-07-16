/*
 * НАЗНАЧЕНИЕ
 * Декларативное описание таблицы "lom_score" — 11 количественных оценок автора
 * по 4 осям влияния (структурная, тематическая активность, позиция автора,
 * отклик аудитории) плюс служебные счётчики. Это основной результат этапа 5
 * (оценки) и вход для бутстрапа (этап 6) и композитов/ролей (этап 7).
 *
 * ЧТО ВНУТРИ
 * Один object-таблица LomScores (Exposed ORM). Одна строка — набор оценок одного
 * автора в одной сессии. Колонки сгруппированы по 4 осям + служебные поля.
 *
 * МЕТОД
 * Формулы — Приложение Е.4 диплома (например, Aud_a = log(1 + F_a)). Многие поля
 * nullable, так как часть оценок может быть не вычислена при нехватке данных.
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — IntIdTable (суррогатный primary key "id"). Два foreign key.
 *
 * СВЯЗИ
 * session_id -> analysis_session.id; author_id -> author.id. Производные:
 * bootstrap_interval (CI оценок), composite_score (свёртка осей).
 */
package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

/**
 * 11 quantitative scores across 4 axes (diploma Appendix E.4).
 * Plus service fields for bootstrap (stage 6) and sufficiency indicator (stage 8).
 *
 * Таблица 11 оценок автора по 4 осям (Приложение Е.4) со служебными полями для
 * бутстрапа (этап 6) и индикатора достаточности данных (этап 8).
 * IntIdTable — суррогатный primary key "id".
 */
object LomScores : IntIdTable("lom_score") {
    /** foreign key на analysis_session.id — сессия расчёта оценок. */
    val sessionId = reference("session_id", AnalysisSessions)
    /** foreign key на author.id — автор, для которого посчитаны оценки. */
    val authorId = reference("author_id", Authors)

    // Ось 1: структурное влияние (Axis 1: Structural influence)
    /** Aud_a = log(1 + F_a): логарифмическая нормировка размера аудитории; может отсутствовать. */
    val aud = float("aud").nullable()                // Aud_a = log(1 + F_a)
    /** Age_a: нормированный возраст аккаунта автора; может отсутствовать. */
    val age = float("age").nullable()                // Age_a = normalized account age
    /** ER_a^bg: фоновый engagement rate (вовлечённость вне темы); может отсутствовать. */
    val erBg = float("er_bg").nullable()             // ER_a^bg = background engagement rate

    // Ось 2: тематическая активность (Axis 2: Topic activity)
    /** TopVol_a = |T_a|: число тематических постов автора; может отсутствовать. */
    val topVol = integer("top_vol").nullable()        // TopVol_a = |T_a|
    /** TopFocus_a: фокус на теме (доля тематической активности); может отсутствовать. */
    val topFocus = float("top_focus").nullable()      // TopFocus_a
    /** Reach_a: тематический охват автора; может отсутствовать. */
    val reach = float("reach").nullable()             // Reach_a

    // Ось 3: позиция автора — распределение {+, 0, -} (Axis 3: Author position)
    /** Доля положительной позиции автора по теме [0..1]; может отсутствовать. */
    val posPositive = float("pos_positive").nullable()
    /** Доля нейтральной позиции автора по теме [0..1]; может отсутствовать. */
    val posNeutral = float("pos_neutral").nullable()
    /** Доля отрицательной позиции автора по теме [0..1]; может отсутствовать. */
    val posNegative = float("pos_negative").nullable()

    // Ось 4: отклик аудитории (Axis 4: Audience response)
    /** ER_a^top: тематический engagement rate (вовлечённость на тематических постах); может отсутствовать. */
    val erTop = float("er_top").nullable()            // ER_a^top
    /** Доля положительного отклика аудитории [0..1]; может отсутствовать. */
    val respPositive = float("resp_positive").nullable()
    /** Доля нейтрального отклика аудитории [0..1]; может отсутствовать. */
    val respNeutral = float("resp_neutral").nullable()
    /** Доля отрицательного отклика аудитории [0..1]; может отсутствовать. */
    val respNegative = float("resp_negative").nullable()

    // Служебные поля (Service fields)
    /** Число фоновых постов автора (вне темы); по умолчанию 0. */
    val bgPostCount = integer("bg_post_count").default(0)
    /** Число тематических постов автора; по умолчанию 0. */
    val topicPostCount = integer("topic_post_count").default(0)
    /** Число комментариев под постами автора (для отклика); по умолчанию 0. */
    val commentCount = integer("comment_count").default(0)
    /** Снимок числа подписчиков (F_a) на момент расчёта; может отсутствовать. */
    val followersCount = integer("followers_count").nullable()

    /** Момент расчёта набора оценок (Unix-время, мс). */
    val createdAt = long("created_at")
}
