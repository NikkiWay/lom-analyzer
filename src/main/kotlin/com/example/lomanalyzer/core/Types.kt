/*
 * НАЗНАЧЕНИЕ
 * Общие доменные типы (модели предметной области), которые используются всеми
 * четырьмя модулями приложения (UI, сбор данных, NLP, аналитическое ядро).
 * Файл описывает «словарь» проекта: распределение тональности, индикатор
 * достаточности данных, базовые роли ЛОМ, атрибуты позиции автора и отклика
 * аудитории, статус индикатора качества. Эти типы — часть результатов этапов
 * 7–10 алгоритма (см. docs/algorithm.md) и фигурируют в подразделах диплома
 * 2.1.5 (достаточность), 2.1.6 (роли) и 2.2.8 (качество).
 *
 * ЧТО ВНУТРИ
 *  - SentimentDistribution — data class с тремя долями тональности и вычислением
 *    доминирующей категории;
 *  - SentimentCategory — enum трёх категорий тональности;
 *  - DataSufficiency — enum индикатора достаточности данных по автору;
 *  - BaseRole — enum четырёх базовых ролей квадрантной классификации;
 *  - AuthorPosition — enum атрибута позиции автора;
 *  - AudienceResponse — enum атрибута отклика аудитории;
 *  - QualityStatus — enum статуса индикатора качества сессии.
 *
 * СВЯЗИ
 * Модули НЕ вызывают друг друга напрямую — они обмениваются данными только через
 * локальную базу SQLite (режим WAL), а эти типы задают общую форму данных
 * (см. docs/architecture.md, «Изоляция модулей»). Сборка зависимостей — через
 * Koin (di/AppModule.kt).
 *
 * БИБЛИОТЕКИ
 * Только stdlib Kotlin — внешних зависимостей в файле нет.
 */
package com.example.lomanalyzer.core

/**
 * Core domain types shared across all modules.
 * Modules communicate only through these types and the local SQLite database.
 *
 * Общие доменные типы, разделяемые всеми модулями. Модули общаются между собой
 * исключительно через эти типы и локальную базу SQLite (принцип изоляции модулей).
 *
 * @see Подраздел_2_2_3_архитектура.md — module isolation principle
 */

/**
 * Распределение тональности текста: доли (положительная, нейтральная,
 * отрицательная), в сумме дающие 1.0.
 *
 * Sentiment distribution: (positive, neutral, negative), sum = 1.0
 *
 * @param positive доля положительной тональности [0..1].
 * @param neutral доля нейтральной тональности [0..1].
 * @param negative доля отрицательной тональности [0..1].
 */
data class SentimentDistribution(
    val positive: Double,
    val neutral: Double,
    val negative: Double,
) {
    init {
        // Доли тональности не могут быть отрицательными — инвариант проверяется при создании
        require(positive >= 0 && neutral >= 0 && negative >= 0)
    }

    /**
     * Доминирующая (преобладающая) категория тональности.
     *
     * Dominant category
     *
     * Вычисляется как категория с наибольшей долей; при равенстве приоритет
     * отдаётся положительной, затем отрицательной, иначе — нейтральной.
     */
    val dominant: SentimentCategory
        get() = when {
            // positive выигрывает, если он не меньше и нейтральной, и отрицательной долей
            positive >= neutral && positive >= negative -> SentimentCategory.POSITIVE
            // negative выигрывает, если он не меньше и нейтральной, и положительной долей
            negative >= neutral && negative >= positive -> SentimentCategory.NEGATIVE
            // во всех остальных случаях преобладает нейтральная тональность
            else -> SentimentCategory.NEUTRAL
        }
}

/** Три категории тональности: положительная, нейтральная, отрицательная. */
enum class SentimentCategory { POSITIVE, NEUTRAL, NEGATIVE }

/**
 * Индикатор достаточности данных по отдельному автору (диплом 2.1.5).
 * Показывает, насколько надёжны рассчитанные для автора оценки и доверительные
 * интервалы (CI — confidence interval), исходя из объёма собранных данных.
 *
 * Data sufficiency indicator per author (diploma 2.1.5)
 */
enum class DataSufficiency {
    RELIABLE,       // надёжно: |T_a| >= 10 И сумма |C_i| >= 50 И средняя ширина CI <= 0.20
    PRELIMINARY,    // предварительно: промежуточное состояние между reliable и unreliable
    UNRELIABLE,     // ненадёжно: |T_a| < 3 ИЛИ сумма |C_i| < 10 ИЛИ ширина CI > 0.50
}

/**
 * Базовая роль автора по квадрантной классификации (диплом 2.1.6).
 * Назначается на этапе 9 алгоритма по положению автора в квадрантах композитных осей.
 *
 * Base role assigned by quadrant classification (diploma 2.1.6)
 *
 * @param russianName человекочитаемое название роли на русском для вывода в UI и экспорта.
 */
enum class BaseRole(val russianName: String) {
    AUTHORITATIVE_LEADER("Авторитетный лидер тематической дискуссии"),
    SLEEPING_GIANT("Спящий гигант"),
    TOPIC_ACTIVIST("Тематический активист"),
    BACKGROUND_AUTHOR("Фоновый автор"),
}

/**
 * Атрибут позиции автора, выводимый из распределения Pos_a (тональность собственных постов).
 *
 * Author position attribute derived from Pos_a distribution
 *
 * @param russianName название атрибута на русском для отображения.
 */
enum class AuthorPosition(val russianName: String) {
    SUPPORTIVE("поддерживающая"),
    NEUTRAL("нейтральная"),
    CRITICAL("критическая"),
}

/**
 * Атрибут отклика аудитории, выводимый из распределения Resp_a (тональность комментариев к автору).
 *
 * Audience response attribute derived from Resp_a distribution
 *
 * @param russianName название атрибута на русском для отображения.
 */
enum class AudienceResponse(val russianName: String) {
    APPROVING("преимущественно одобрительный"),
    MIXED("смешанный"),
    CRITICAL("преимущественно критический"),
}

/**
 * Статус отдельного индикатора качества сессии (диплом, таблица 2.5).
 * Используется на этапе 10 при оценке качества данных и результатов.
 *
 * Quality indicator status (diploma table 2.5)
 */
enum class QualityStatus {
    PASSED,
    BORDERLINE,
    FAILED,
}
