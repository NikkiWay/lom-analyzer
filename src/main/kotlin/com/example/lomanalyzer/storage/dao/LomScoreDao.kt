/*
 * НАЗНАЧЕНИЕ
 * DAO (слой доступа к данным) для 11 количественных оценок автора в сессии
 * (таблица lom_scores, Приложение Е.4 диплома). Это центральная таблица результатов
 * этапа 5 алгоритма (11 оценок): аудитория, активность, охват, позиция (доли
 * positive/neutral/negative), отклик (Resp_*), вовлечённость (ER) и т. п., а также
 * технические счётчики (число фоновых/тематических постов, комментариев, подписчиков).
 * Обмен между модулями — только через SQLite.
 *
 * ЧТО ВНУТРИ
 * Класс LomScoreDao: upsert (вставка-или-частичное-обновление по ключу сессия+автор;
 * не-null аргументы перезаписывают соответствующие поля, null — не трогают),
 * findBySession и findBySessionAndAuthor (выборки).
 *
 * МЕТОД
 * Оси и формулы — см. analysis/scoring и docs/formulas.md (Е.4). Здесь только хранение.
 *
 * БИБЛИОТЕКИ
 * Exposed ORM — DSL запросов. java.time.Instant — created_at в epoch millis.
 *
 * СВЯЗИ
 * Таблица LomScores (storage/tables). Источник для бутстрапа и композитов.
 */
package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.LomScores
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * DAO for the 11 quantitative scores per author per session (diploma E.4).
 *
 * DAO 11 количественных оценок автора в сессии (Приложение Е.4). Каждый метод —
 * отдельная транзакция.
 *
 * @param db подключение к БД (Exposed Database).
 */
class LomScoreDao(private val db: Database) {

    /**
     * Upsert строки оценок автора. Естественный ключ — (sessionId, authorId).
     * Особенность частичного обновления: при UPDATE поля-оценки с типом Float?/Int?
     * перезаписываются только если переданное значение не null; счётчики
     * bgPostCount/topicPostCount/commentCount пишутся всегда. Это позволяет
     * заполнять разные группы оценок разными проходами пайплайна, не затирая ранее
     * вычисленные.
     * @return id существующей (при UPDATE) или вновь созданной (при INSERT) строки.
     */
    fun upsert(
        sessionId: Int,
        authorId: Int,
        aud: Float? = null,
        age: Float? = null,
        erBg: Float? = null,
        topVol: Int? = null,
        topFocus: Float? = null,
        reach: Float? = null,
        posPositive: Float? = null,
        posNeutral: Float? = null,
        posNegative: Float? = null,
        erTop: Float? = null,
        respPositive: Float? = null,
        respNeutral: Float? = null,
        respNegative: Float? = null,
        bgPostCount: Int = 0,
        topicPostCount: Int = 0,
        commentCount: Int = 0,
        followersCount: Int? = null,
    ): Int = transaction(db) {
        // Ищем существующую строку оценок по ключу сессия+автор
        val existing = LomScores.selectAll().where {
            (LomScores.sessionId eq sessionId) and (LomScores.authorId eq authorId)
        }.singleOrNull()

        if (existing != null) {
            // UPDATE: частичное обновление — пишем только переданные (не-null) оценки
            LomScores.update({
                (LomScores.sessionId eq sessionId) and (LomScores.authorId eq authorId)
            }) {
                if (aud != null) it[LomScores.aud] = aud
                if (age != null) it[LomScores.age] = age
                if (erBg != null) it[LomScores.erBg] = erBg
                if (topVol != null) it[LomScores.topVol] = topVol
                if (topFocus != null) it[LomScores.topFocus] = topFocus
                if (reach != null) it[LomScores.reach] = reach
                if (posPositive != null) it[LomScores.posPositive] = posPositive
                if (posNeutral != null) it[LomScores.posNeutral] = posNeutral
                if (posNegative != null) it[LomScores.posNegative] = posNegative
                if (erTop != null) it[LomScores.erTop] = erTop
                if (respPositive != null) it[LomScores.respPositive] = respPositive
                if (respNeutral != null) it[LomScores.respNeutral] = respNeutral
                if (respNegative != null) it[LomScores.respNegative] = respNegative
                it[LomScores.bgPostCount] = bgPostCount
                it[LomScores.topicPostCount] = topicPostCount
                it[LomScores.commentCount] = commentCount
                if (followersCount != null) it[LomScores.followersCount] = followersCount
            }
            return@transaction existing[LomScores.id].value
        }

        // INSERT: строки нет — создаём новую со всеми переданными значениями
        LomScores.insertAndGetId {
            it[LomScores.sessionId] = sessionId
            it[LomScores.authorId] = authorId
            it[LomScores.aud] = aud
            it[LomScores.age] = age
            it[LomScores.erBg] = erBg
            it[LomScores.topVol] = topVol
            it[LomScores.topFocus] = topFocus
            it[LomScores.reach] = reach
            it[LomScores.posPositive] = posPositive
            it[LomScores.posNeutral] = posNeutral
            it[LomScores.posNegative] = posNegative
            it[LomScores.erTop] = erTop
            it[LomScores.respPositive] = respPositive
            it[LomScores.respNeutral] = respNeutral
            it[LomScores.respNegative] = respNegative
            it[LomScores.bgPostCount] = bgPostCount
            it[LomScores.topicPostCount] = topicPostCount
            it[LomScores.commentCount] = commentCount
            it[LomScores.followersCount] = followersCount
            it[createdAt] = Instant.now().toEpochMilli()
        }.value
    }

    /**
     * SELECT строк оценок всех авторов сессии.
     * @return список ResultRow.
     */
    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        LomScores.selectAll().where { LomScores.sessionId eq sessionId }.toList()
    }

    /**
     * SELECT строки оценок одного автора в сессии.
     * @return ResultRow или null, если оценки ещё не вычислены.
     */
    fun findBySessionAndAuthor(sessionId: Int, authorId: Int): ResultRow? = transaction(db) {
        LomScores.selectAll().where {
            (LomScores.sessionId eq sessionId) and (LomScores.authorId eq authorId)
        }.singleOrNull()
    }
}
