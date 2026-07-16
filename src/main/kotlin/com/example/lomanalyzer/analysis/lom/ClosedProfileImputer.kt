/*
 * НАЗНАЧЕНИЕ
 * Импутация (восполнение) числа подписчиков F для авторов с закрытым профилем
 * или неизвестным счётчиком. Часть этапа 7 (вычисление оценок): без F нельзя
 * посчитать структурные метрики (Aud, ER), а закрытые профили VK не отдают F.
 *
 * ЧТО ВНУТРИ
 * Объект ClosedProfileImputer (computeQ25 — нижний квартиль по открытым профилям;
 * impute — выбор фактического или импутированного F) и data class ImputeResult.
 *
 * МЕТОД
 * Консервативная импутация: F = Q25(F_open) — первый квартиль распределения
 * подписчиков среди ОТКРЫТЫХ авторов. Берётся именно нижний квартиль, чтобы не
 * завышать влияние автора с неизвестной аудиторией. Импутированные значения
 * помечаются флагом ESTIMATED_CONSERVATIVE и исключаются из статистик нормализации
 * (медиана/IQR), чтобы не искажать пороги.
 *
 * БИБЛИОТЕКИ
 * Только stdlib Kotlin.
 *
 * СВЯЗИ
 * Результат используется AudienceComponent/EngagementDensityComponent и
 * далее z-нормализацией композитов.
 */
package com.example.lomanalyzer.analysis.lom

/**
 * Если Author.isClosed или followersCount неизвестен, подставляет F = Q25(F_open).
 * Флаг audienceFlag = ESTIMATED_CONSERVATIVE.
 * Импутированные значения исключаются из статистик нормализации.
 */
object ClosedProfileImputer {
    /**
     * Считает Q25 (первый квартиль) числа подписчиков по открытым профилям.
     * Используется как консервативная подстановка для закрытых/неизвестных.
     * @param openFollowerCounts список F всех открытых авторов.
     * @return значение нижнего квартиля; для пустого списка 0.
     */
    fun computeQ25(openFollowerCounts: List<Int>): Int {
        if (openFollowerCounts.isEmpty()) return 0
        val sorted = openFollowerCounts.sorted()                       // квартиль — по упорядоченной выборке
        val idx = (sorted.size * 0.25).toInt().coerceIn(0, sorted.size - 1)  // индекс ~25-го перцентиля
        return sorted[idx]
    }

    /**
     * Выбирает фактическое F или импутирует его консервативной оценкой Q25.
     * @param followersCount фактическое число подписчиков (может быть null).
     * @param isClosed закрыт ли профиль автора.
     * @param q25 заранее посчитанный Q25(F_open) для подстановки.
     * @return ImputeResult с итоговым F, флагом и признаком импутации.
     */
    fun impute(
        followersCount: Int?,
        isClosed: Boolean,
        q25: Int,
    ): ImputeResult {
        // Закрытый профиль или отсутствующий/некорректный счётчик -> консервативная импутация
        if (isClosed || followersCount == null || followersCount <= 0) {
            return ImputeResult(
                followers = q25,
                audienceFlag = "ESTIMATED_CONSERVATIVE",
                imputed = true,
            )
        }
        // Иначе используем фактическое F без пометки
        return ImputeResult(
            followers = followersCount,
            audienceFlag = "NORMAL",
            imputed = false,
        )
    }
}

/**
 * Результат импутации подписчиков.
 * @param followers итоговое число подписчиков (фактическое либо Q25).
 * @param audienceFlag метка качества: "NORMAL" или "ESTIMATED_CONSERVATIVE".
 * @param imputed true, если значение было импутировано (исключается из нормализации).
 */
data class ImputeResult(
    val followers: Int,
    val audienceFlag: String,
    val imputed: Boolean,
)
