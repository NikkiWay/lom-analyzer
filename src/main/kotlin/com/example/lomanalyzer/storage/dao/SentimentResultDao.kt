/*
 * НАЗНАЧЕНИЕ
 * DAO (слой доступа к данным) для результатов анализа тональности (таблица
 * sentiment_results). Хранит метку тональности постов/комментариев, метод, признак
 * учёта отрицания и сведения о бутстрапе сентимента (согласованность и варианты).
 * Тональность нужна для оценок позиции автора и отклика аудитории.
 * Обмен между модулями — только через SQLite.
 *
 * ЧТО ВНУТРИ
 * Класс SentimentResultDao: insert (вставка результата сентимента),
 * findByPostId (точечная выборка), findAllAsMap (быстрая массовая загрузка карты
 * postId/commentId -> метка тональности).
 *
 * БИБЛИОТЕКИ
 * Exposed ORM — DSL запросов (insert, selectAll, associate).
 *
 * СВЯЗИ
 * Таблица SentimentResults (storage/tables). postId — id поста/комментария.
 */
package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.SentimentResults
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * DAO результатов тональности. Каждый метод — отдельная транзакция.
 *
 * @param db подключение к БД (Exposed Database).
 */
class SentimentResultDao(private val db: Database) {
    /**
     * INSERT результата анализа тональности.
     * @param postId id поста/комментария, к которому относится результат.
     * @param sentiment метка тональности (positive/neutral/negative).
     * @param score числовая оценка тональности или NULL.
     * @param method способ получения тональности.
     * @param negationApplied признак учёта отрицаний при разборе.
     * @param bootstrapAgreement доля согласия вариантов бутстрапа сентимента или NULL.
     * @param bootstrapVariants сериализованные варианты бутстрапа или NULL.
     */
    fun insert(
        postId: Int,
        sentiment: String,
        score: Float? = null,
        method: String,
        negationApplied: Boolean = false,
        bootstrapAgreement: Float? = null,
        bootstrapVariants: String? = null,
    ) = transaction(db) {
        SentimentResults.insert {
            it[SentimentResults.postId] = postId
            it[SentimentResults.sentiment] = sentiment
            it[SentimentResults.score] = score
            it[SentimentResults.method] = method
            it[SentimentResults.negationApplied] = negationApplied
            it[SentimentResults.bootstrapAgreement] = bootstrapAgreement
            it[SentimentResults.bootstrapVariants] = bootstrapVariants
        }
    }

    /**
     * SELECT результата тональности по id поста/комментария.
     * @return ResultRow или null.
     */
    fun findByPostId(postId: Int): ResultRow? = transaction(db) {
        SentimentResults.selectAll().where { SentimentResults.postId eq postId }.singleOrNull()
    }

    /**
     * Load all sentiment results as a map of postId/commentId -> sentiment label. Fast bulk read.
     *
     * Массовая загрузка всех результатов тональности одним запросом в карту
     * postId/commentId -> метка тональности (ускоряет доступ без частых одиночных
     * выборок). Мапит каждую ResultRow в пару ключ-значение через associate.
     * @return Map id -> метка тональности.
     */
    fun findAllAsMap(): Map<Int, String> = transaction(db) {
        // SELECT всех строк; associate строит Map из (postId -> sentiment)
        SentimentResults.selectAll().associate {
            it[SentimentResults.postId].value to it[SentimentResults.sentiment]
        }
    }
}
