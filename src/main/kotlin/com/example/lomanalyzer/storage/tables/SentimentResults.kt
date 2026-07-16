/*
 * НАЗНАЧЕНИЕ
 * Декларативное описание таблицы "sentiment_result" — тональность (сентимент)
 * поста ИЛИ комментария: метка {+, 0, -}, числовая оценка и метод вычисления.
 * Заполняется на этапе препроцессинга и используется при расчёте позиции автора
 * (ось 3, по его собственным постам) и отклика аудитории (ось 4, по комментариям).
 *
 * ЧТО ВНУТРИ
 * Один object-таблица SentimentResults (Exposed ORM) с составным первичным ключом
 * (entity_type, entity_id). Хранит также служебные поля бутстрапа сентимента
 * (согласованность вариантов).
 *
 * МЕТОД
 * Тональность определяется NLP-моделью (Python sidecar: rubert-tiny2-russian-sentiment)
 * либо словарным fallback (DictionarySentiment). Бутстрап-поля фиксируют
 * устойчивость метки при ресемплинге.
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — обычная Table с составным естественным ключом.
 *
 * СВЯЗИ
 * entity_id -> post.id (Posts.kt) при entity_type = POST либо comment.id
 * (Comments.kt) при entity_type = COMMENT. Соседняя таблица processed_text
 * держит леммы. См. миграцию V11__sentiment_result_entity_type.sql.
 */
package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.sql.Table

/**
 * Тип сущности, к которой относится оценка тональности.
 *
 * Посты и комментарии нумеруются независимыми автоинкрементными
 * последовательностями, поэтому их идентификаторы пересекаются и сами по себе
 * не различают сущность — тип обязателен как часть ключа.
 */
enum class SentimentEntityType {
    /** Оценка относится к посту (post.id). */
    POST,

    /** Оценка относится к комментарию (comment.id). */
    COMMENT,
}

/**
 * Таблица тональности постов и комментариев ("sentiment_result").
 *
 * Составной первичный ключ (entity_type, entity_id): одна оценка тональности на
 * каждую сущность. Внешнего ключа нет намеренно — entity_id адресует две разные
 * таблицы в зависимости от entity_type (см. V11__sentiment_result_entity_type.sql).
 */
object SentimentResults : Table("sentiment_result") {
    /** Тип сущности: POST либо COMMENT — определяет, куда указывает entityId. */
    val entityType = text("entity_type")
    /** Идентификатор post.id либо comment.id — в зависимости от entityType. */
    val entityId = integer("entity_id")
    /** Метка тональности: положительная "+", нейтральная "0" или отрицательная "-". */
    val sentiment = text("sentiment")
    /** Числовая оценка уверенности/полярности модели; может отсутствовать. */
    val score = float("score").nullable()
    /** Метод определения тональности (например, модель sidecar или словарный fallback). */
    val method = text("method")
    /** Признак, что применялась обработка отрицаний (negation handling); по умолчанию false. */
    val negationApplied = bool("negation_applied").default(false)
    /** Доля согласованных меток при бутстрапе сентимента [0..1] — устойчивость результата; может отсутствовать. */
    val bootstrapAgreement = float("bootstrap_agreement").nullable()
    /** Распределение меток по бутстрап-вариантам (JSON); может отсутствовать. */
    val bootstrapVariants = text("bootstrap_variants").nullable()

    // Распределение вероятностей по классам, как его вернула модель. NULL, если
    // источник распределения не даёт (словарный fallback) — см. V13.
    /** Вероятность позитивного класса [0..1]; NULL для словарного fallback. */
    val probPositive = float("prob_positive").nullable()
    /** Вероятность нейтрального класса [0..1]; NULL для словарного fallback. */
    val probNeutral = float("prob_neutral").nullable()
    /** Вероятность негативного класса [0..1]; NULL для словарного fallback. */
    val probNegative = float("prob_negative").nullable()

    /** Составной первичный ключ — пара (тип сущности, её идентификатор). */
    override val primaryKey = PrimaryKey(entityType, entityId)
}
