/*
 * НАЗНАЧЕНИЕ
 * DAO (слой доступа к данным) для результатов этапа композитов и ролей (этап 7
 * алгоритма, подраздел 2.1.6). Работает сразу с тремя таблицами: composite_scores
 * (композитные оценки по осям «структура» и «тематика»), session_thresholds (пороги
 * θ_struct, θ_topic для квадрантной классификации), author_roles (итоговая роль
 * автора: базовая роль + атрибуты позиции и отклика + признак достаточности данных).
 * Композитные веса фиксированы 1/3,1/3,1/3 (OECD); расчёт — в analysis/composite.
 * Обмен между модулями — только через SQLite.
 *
 * ЧТО ВНУТРИ
 * Класс CompositeDao: upsertComposite, upsertThresholds, upsertRole (все три —
 * вставка-или-обновление по естественным ключам), и три выборки: findCompositesBySession,
 * findThresholds, findRolesBySession.
 *
 * МЕТОД
 * Квадрантная классификация: два композита сравниваются с порогами θ → базовая роль;
 * атрибуты позиции/отклика и индикатор достаточности (sufficiency) хранятся отдельно.
 *
 * БИБЛИОТЕКИ
 * Exposed ORM — DSL запросов. java.time.Instant — created_at в epoch millis.
 *
 * СВЯЗИ
 * Таблицы CompositeScores, SessionThresholds, AuthorRoles (storage/tables).
 */
package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.AuthorRoles
import com.example.lomanalyzer.storage.tables.CompositeScores
import com.example.lomanalyzer.storage.tables.SessionThresholds
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * DAO композитных оценок, порогов и ролей авторов. Каждый метод — отдельная транзакция.
 *
 * @param db подключение к БД (Exposed Database).
 */
class CompositeDao(private val db: Database) {

    /**
     * Upsert композитных оценок автора. Естественный ключ — (sessionId, authorId).
     * @param struct композит по оси структурного влияния.
     * @param topic композит по оси тематической активности.
     */
    fun upsertComposite(sessionId: Int, authorId: Int, struct: Float, topic: Float) = transaction(db) {
        // Ищем существующий композит по ключу сессия+автор
        val existing = CompositeScores.selectAll().where {
            (CompositeScores.sessionId eq sessionId) and (CompositeScores.authorId eq authorId)
        }.singleOrNull()
        if (existing != null) {
            // UPDATE: обновляем оба композита
            CompositeScores.update({
                (CompositeScores.sessionId eq sessionId) and (CompositeScores.authorId eq authorId)
            }) {
                it[structComposite] = struct
                it[topicComposite] = topic
            }
        } else {
            // INSERT: новой записи проставляем created_at
            CompositeScores.insert {
                it[CompositeScores.sessionId] = sessionId
                it[CompositeScores.authorId] = authorId
                it[structComposite] = struct
                it[topicComposite] = topic
                it[createdAt] = Instant.now().toEpochMilli()
            }
        }
    }

    /**
     * Upsert порогов классификации сессии. Естественный ключ — sessionId (один набор
     * порогов на сессию).
     * @param thetaStruct порог θ по оси структурного влияния.
     * @param thetaTopic порог θ по оси тематической активности.
     */
    fun upsertThresholds(sessionId: Int, thetaStruct: Float, thetaTopic: Float) = transaction(db) {
        // Ищем существующий набор порогов сессии
        val existing = SessionThresholds.selectAll().where {
            SessionThresholds.sessionId eq sessionId
        }.singleOrNull()
        if (existing != null) {
            // UPDATE: обновляем оба порога
            SessionThresholds.update({ SessionThresholds.sessionId eq sessionId }) {
                it[SessionThresholds.thetaStruct] = thetaStruct
                it[SessionThresholds.thetaTopic] = thetaTopic
            }
        } else {
            // INSERT: новой записи проставляем created_at
            SessionThresholds.insert {
                it[SessionThresholds.sessionId] = sessionId
                it[SessionThresholds.thetaStruct] = thetaStruct
                it[SessionThresholds.thetaTopic] = thetaTopic
                it[createdAt] = Instant.now().toEpochMilli()
            }
        }
    }

    /**
     * Upsert итоговой роли автора. Естественный ключ — (sessionId, authorId).
     * @param baseRole базовая роль из квадрантной классификации.
     * @param positionAttr атрибут позиции автора.
     * @param responseAttr атрибут отклика аудитории.
     * @param sufficiency индикатор достаточности данных (по умолчанию PRELIMINARY —
     *        предварительный результат, данных может быть недостаточно).
     */
    fun upsertRole(
        sessionId: Int, authorId: Int,
        baseRole: String, positionAttr: String, responseAttr: String,
        sufficiency: String = "PRELIMINARY",
    ) = transaction(db) {
        // Ищем существующую роль автора в сессии
        val existing = AuthorRoles.selectAll().where {
            (AuthorRoles.sessionId eq sessionId) and (AuthorRoles.authorId eq authorId)
        }.singleOrNull()
        if (existing != null) {
            // UPDATE: переписываем роль и атрибуты
            AuthorRoles.update({
                (AuthorRoles.sessionId eq sessionId) and (AuthorRoles.authorId eq authorId)
            }) {
                it[AuthorRoles.baseRole] = baseRole
                it[AuthorRoles.positionAttr] = positionAttr
                it[AuthorRoles.responseAttr] = responseAttr
                it[AuthorRoles.sufficiency] = sufficiency
            }
        } else {
            // INSERT: новой записи проставляем created_at
            AuthorRoles.insert {
                it[AuthorRoles.sessionId] = sessionId
                it[AuthorRoles.authorId] = authorId
                it[AuthorRoles.baseRole] = baseRole
                it[AuthorRoles.positionAttr] = positionAttr
                it[AuthorRoles.responseAttr] = responseAttr
                it[AuthorRoles.sufficiency] = sufficiency
                it[createdAt] = Instant.now().toEpochMilli()
            }
        }
    }

    /**
     * SELECT всех композитных оценок сессии.
     * @return список ResultRow.
     */
    fun findCompositesBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        CompositeScores.selectAll().where { CompositeScores.sessionId eq sessionId }.toList()
    }

    /**
     * SELECT набора порогов классификации сессии.
     * @return ResultRow или null, если пороги ещё не рассчитаны.
     */
    fun findThresholds(sessionId: Int): ResultRow? = transaction(db) {
        SessionThresholds.selectAll().where { SessionThresholds.sessionId eq sessionId }.singleOrNull()
    }

    /**
     * SELECT всех ролей авторов сессии.
     * @return список ResultRow.
     */
    fun findRolesBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        AuthorRoles.selectAll().where { AuthorRoles.sessionId eq sessionId }.toList()
    }
}
