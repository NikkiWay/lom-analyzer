/*
 * НАЗНАЧЕНИЕ
 * Декларативное описание таблицы "bootstrap_interval" — доверительные интервалы
 * оценок, полученные бутстрапом (этап 6). Для каждой именованной оценки автора
 * хранится нижняя и верхняя граница CI, тип процедуры и число итераций.
 *
 * ЧТО ВНУТРИ
 * Один object-таблица BootstrapIntervals (Exposed ORM). Ссылается на сессию и
 * автора; одна строка — один интервал для одной оценки (score_name).
 *
 * МЕТОД
 * Бутстрап: одноуровневый B=1000 (по умолчанию) для большинства оценок;
 * двухуровневый 300x100 — только для Resp_a (отклик аудитории). См.
 * docs/formulas.md (Приложение Е.1–Е.3).
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — IntIdTable (суррогатный primary key "id"). Два foreign key.
 *
 * СВЯЗИ
 * session_id -> analysis_session.id; author_id -> author.id (Authors.kt).
 */
package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

/**
 * Таблица доверительных интервалов бутстрапа ("bootstrap_interval").
 *
 * IntIdTable (суррогатный primary key "id"). Хранит CI оценок автора в сессии.
 */
object BootstrapIntervals : IntIdTable("bootstrap_interval") {
    /** foreign key на analysis_session.id — сессия расчёта. */
    val sessionId = reference("session_id", AnalysisSessions)
    /** foreign key на author.id — автор, для оценки которого построен интервал. */
    val authorId = reference("author_id", Authors)
    /** Имя оценки, к которой относится интервал (например, одна из 11 метрик E.4). */
    val scoreName = text("score_name")
    /** Нижняя граница доверительного интервала. */
    val ciLo = float("ci_lo")
    /** Верхняя граница доверительного интервала. */
    val ciHi = float("ci_hi")
    /** Тип процедуры бутстрапа; по умолчанию "one_level" (для Resp_a — двухуровневый). */
    val procedureType = text("procedure_type").default("one_level")
    /** Число итераций бутстрапа; по умолчанию 1000 (B=1000 для одноуровневого). */
    val iterations = integer("iterations").default(1000)
    /** Момент расчёта интервала (Unix-время, мс). */
    val createdAt = long("created_at")
}
