/*
 * НАЗНАЧЕНИЕ
 * DAO (слой доступа к данным) для результатов анализа тональности (таблица
 * sentiment_result). Хранит метку тональности постов И комментариев, метод, признак
 * учёта отрицания и сведения о бутстрапе сентимента (согласованность и варианты).
 * Тональность нужна для оценок позиции автора (ось 3) и отклика аудитории (ось 4).
 * Обмен между модулями — только через SQLite.
 *
 * ЧТО ВНУТРИ
 * Класс SentimentResultDao: insert (вставка результата сентимента),
 * findByEntity (точечная выборка), findAllAsMap (массовая загрузка карты
 * id -> метка тональности в пределах одного типа сущности).
 *
 * МЕТОД
 * Каждая строка адресуется парой (entityType, entityId). Посты и комментарии
 * нумеруются независимо, поэтому тип сущности обязателен во всех операциях:
 * без него id=1 поста и id=1 комментария неразличимы.
 *
 * БИБЛИОТЕКИ
 * Exposed ORM — DSL запросов (insert, selectAll, associate).
 *
 * СВЯЗИ
 * Таблица SentimentResults (storage/tables) и enum SentimentEntityType.
 */
package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.SentimentEntityType
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
     * @param entityType тип сущности: POST либо COMMENT.
     * @param entityId id поста либо комментария (в зависимости от entityType).
     * @param sentiment метка тональности (positive/neutral/negative).
     * @param score числовая оценка тональности или NULL.
     * @param method способ получения тональности.
     * @param negationApplied признак учёта отрицаний при разборе.
     * @param bootstrapAgreement доля согласия вариантов бутстрапа сентимента или NULL.
     * @param bootstrapVariants сериализованные варианты бутстрапа или NULL.
     */
    fun insert(
        entityType: SentimentEntityType,
        entityId: Int,
        sentiment: String,
        score: Float? = null,
        method: String,
        negationApplied: Boolean = false,
        bootstrapAgreement: Float? = null,
        bootstrapVariants: String? = null,
    ) = transaction(db) {
        SentimentResults.insert {
            it[SentimentResults.entityType] = entityType.name
            it[SentimentResults.entityId] = entityId
            it[SentimentResults.sentiment] = sentiment
            it[SentimentResults.score] = score
            it[SentimentResults.method] = method
            it[SentimentResults.negationApplied] = negationApplied
            it[SentimentResults.bootstrapAgreement] = bootstrapAgreement
            it[SentimentResults.bootstrapVariants] = bootstrapVariants
        }
    }

    /**
     * SELECT результата тональности по типу и id сущности.
     * @return ResultRow или null.
     */
    fun findByEntity(entityType: SentimentEntityType, entityId: Int): ResultRow? = transaction(db) {
        SentimentResults.selectAll().where {
            (SentimentResults.entityType eq entityType.name) and (SentimentResults.entityId eq entityId)
        }.singleOrNull()
    }

    /**
     * Load sentiment results for one entity type as a map of id -> sentiment label.
     *
     * Массовая загрузка результатов тональности одним запросом в карту
     * id -> метка тональности. Выборка всегда ограничена одним типом сущности:
     * посты и комментарии нумеруются независимо, поэтому общая карта по обоим
     * типам молча теряла бы строки при совпадении идентификаторов.
     * @param entityType тип сущности, по которому строится карта.
     * @return Map id сущности -> метка тональности.
     */
    fun findAllAsMap(entityType: SentimentEntityType): Map<Int, String> = transaction(db) {
        SentimentResults.selectAll()
            .where { SentimentResults.entityType eq entityType.name }
            .associate { it[SentimentResults.entityId] to it[SentimentResults.sentiment] }
    }
}
