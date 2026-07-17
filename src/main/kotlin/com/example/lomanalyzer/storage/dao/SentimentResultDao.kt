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

import com.example.lomanalyzer.core.SentimentDistribution
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
     * @param probabilities распределение вероятностей по классам или NULL, если
     *   источник его не даёт (словарный fallback).
     */
    fun insert(
        entityType: SentimentEntityType,
        entityId: Int,
        sentiment: String,
        score: Float? = null,
        method: String,
        negationApplied: Boolean = false,
        probabilities: SentimentDistribution? = null,
    ) = transaction(db) {
        SentimentResults.insert {
            it[SentimentResults.entityType] = entityType.name
            it[SentimentResults.entityId] = entityId
            it[SentimentResults.sentiment] = sentiment
            it[SentimentResults.score] = score
            it[SentimentResults.method] = method
            it[SentimentResults.negationApplied] = negationApplied
            it[SentimentResults.probPositive] = probabilities?.positive?.toFloat()
            it[SentimentResults.probNeutral] = probabilities?.neutral?.toFloat()
            it[SentimentResults.probNegative] = probabilities?.negative?.toFloat()
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

    /**
     * Массовая загрузка методов расчёта одного типа сущности.
     *
     * Нужна проверке качества: по методу видно, измерена ли тональность моделью
     * или подставлена после сбоя (fallback_error). Без этого сессия, где модель
     * отвалилась на большинстве текстов, выглядит так же, как посчитанная целиком.
     *
     * @param entityType тип сущности, по которому строится карта.
     * @return Map id сущности -> метод расчёта.
     */
    fun findMethodsAsMap(entityType: SentimentEntityType): Map<Int, String> = transaction(db) {
        SentimentResults.selectAll()
            .where { SentimentResults.entityType eq entityType.name }
            .associate { it[SentimentResults.entityId] to it[SentimentResults.method] }
    }

    /**
     * Массовая загрузка распределений вероятностей одного типа сущности.
     *
     * В карту попадают только строки, у которых распределение есть: его даёт
     * модель sidecar, а словарный fallback — нет (там колонки NULL, см. V13).
     * Отсутствие ключа означает «вероятностей нет», и вызывающий возвращается к
     * расчёту по долям меток.
     *
     * @param entityType тип сущности, по которому строится карта.
     * @return Map id сущности -> распределение вероятностей.
     */
    fun findProbabilitiesAsMap(entityType: SentimentEntityType): Map<Int, SentimentDistribution> =
        transaction(db) {
            SentimentResults.selectAll()
                .where {
                    (SentimentResults.entityType eq entityType.name) and
                        (SentimentResults.probNeutral.isNotNull())
                }
                .associate { row ->
                    row[SentimentResults.entityId] to SentimentDistribution(
                        positive = (row[SentimentResults.probPositive] ?: 0f).toDouble(),
                        neutral = (row[SentimentResults.probNeutral] ?: 0f).toDouble(),
                        negative = (row[SentimentResults.probNegative] ?: 0f).toDouble(),
                    )
                }
        }
}
