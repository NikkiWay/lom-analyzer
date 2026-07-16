/*
 * НАЗНАЧЕНИЕ
 * Декларативное описание таблицы "author" — реестр авторов ВКонтакте
 * (кандидатов в лидеры общественного мнения, ЛОМ). Заполняется на фазе B
 * трёхфазного сбора (реестр авторов) и служит основой для всех оценок:
 * на author ссылаются lom_score, composite_score, bootstrap_interval,
 * author_role и таблица-связка session_author.
 *
 * ЧТО ВНУТРИ
 * Один object-таблица Authors (Exposed ORM) с идентификацией автора,
 * базовыми метриками аудитории и служебными флагами для статистики/контроля
 * качества (источник обнаружения, окно базовой линии, нестационарность).
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — IntIdTable даёт суррогатный primary key "id"; колонки
 * описываются декларативно. Физически таблица создаётся миграциями Flyway.
 *
 * СВЯЗИ
 * На author ссылаются lom_score, composite_score, session_threshold-зависимые
 * расчёты, bootstrap_interval, author_role, session_author (LinkTables.kt).
 */
package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

/**
 * Таблица авторов ВКонтакте ("author") — кандидатов в ЛОМ.
 *
 * Наследует IntIdTable: суррогатный primary key "id" (внутренний ключ БД),
 * отдельный от vkId (идентификатора во ВКонтакте).
 */
object Authors : IntIdTable("author") {
    /** Числовой идентификатор пользователя во ВКонтакте; uniqueIndex — один автор хранится единожды. */
    val vkId = integer("vk_id").uniqueIndex()
    /** Имя автора; может отсутствовать. */
    val firstName = text("first_name").nullable()
    /** Фамилия автора; может отсутствовать. */
    val lastName = text("last_name").nullable()
    /** Короткое имя (slug в URL профиля); может отсутствовать. */
    val screenName = text("screen_name").nullable()
    /** Число подписчиков (F_a) — входит в структурную ось влияния (Aud_a = log(1 + F_a)); null, если недоступно. */
    val followersCount = integer("followers_count").nullable()
    /** Признак закрытого профиля; по умолчанию false. */
    val isClosed = bool("is_closed").default(false)
    /** Метка отнесения к аудитории (а не к ядру ЛОМ) — служебный флаг классификации; может отсутствовать. */
    val audienceFlag = text("audience_flag").nullable()
    /** Момент первого появления автора в данных (Unix-время, мс); может отсутствовать. */
    val firstSeenAt = long("first_seen_at").nullable()
    /** Источник обнаружения автора: SEED (исходный список) либо производный; по умолчанию "SEED". */
    val discoverySource = text("discovery_source").default("SEED")
    /** Ширина окна базовой линии (фоновой активности) в днях; по умолчанию 60. */
    val baselineWindowDays = integer("baseline_window_days").default(60)
    /** Флаги аккаунта (JSON/строка): особенности профиля, влияющие на интерпретацию; может отсутствовать. */
    val accountFlags = text("account_flags").nullable()
    /** Признак возможной нестационарности активности автора (резкие изменения поведения); по умолчанию false. */
    val possiblyNonStationary = bool("possibly_non_stationary").default(false)
    /** Момент создания записи в БД (Unix-время, мс). */
    val createdAt = long("created_at")
    /** Момент последнего обновления записи в БД (Unix-время, мс). */
    val updatedAt = long("updated_at")
    /** Момент мягкого удаления (soft delete); null — запись активна. */
    val deletedAt = long("deleted_at").nullable()
}
