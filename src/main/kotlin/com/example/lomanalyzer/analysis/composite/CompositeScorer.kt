/*
 * НАЗНАЧЕНИЕ
 * Сборка композитных оценок двух главных осей и расчёт адаптивных порогов для
 * последующей классификации ролей (этап 9 алгоритма, docs/algorithm.md;
 * формула Приложения Е.4.6, раздел 2.1.6).
 *
 * ЧТО ВНУТРИ
 * object CompositeScorer:
 *  - structuralComposite(...) — Struct_a (структурный композит);
 *  - topicComposite(...)      — Topic_a (тематический композит);
 *  - adaptiveThresholds(...)  — пороги θ_Struct, θ_Topic (медианы композитов).
 *
 * МЕТОД
 * Struct_a = (1/3)(z(Aud) + z(ER_bg) + z(Age))
 * Topic_a  = (1/3)(z(TopVol) + z(TopFocus) + z(Reach))
 * Равные веса 1/3 — стандартный дефолт OECD Handbook [114]; НАСТРАИВАЕМЫХ весов
 * нет (методологическое требование диплома). Адаптивные пороги — это медианы
 * композитов по сессии, поэтому ровно половина авторов попадает в «высокую»
 * категорию, половина — в «низкую».
 *
 * БИБЛИОТЕКИ
 * Stdlib Kotlin; медиана для порогов — из analysis.inference.RobustStats.
 *
 * СВЯЗИ
 * Вход — z-значения из RobustZScore. Выход (композиты и пороги) использует
 * CompositeRolesExecutor и далее RoleAssigner для квадрантной классификации.
 */
package com.example.lomanalyzer.analysis.composite

import com.example.lomanalyzer.analysis.inference.RobustStats

/**
 * Композитные оценки двух главных осей (диплом Е.4.6, раздел 2.1.6).
 *
 * Struct_a = (1/3)(z(Aud) + z(ER_bg) + z(Age))
 * Topic_a  = (1/3)(z(TopVol) + z(TopFocus) + z(Reach))
 *
 * Равные веса 1/3 — стандартный дефолт OECD Handbook [114].
 * НИКАКИХ настраиваемых весов — методологическое требование диплома.
 */
object CompositeScorer {

    /** Равный вес каждой из трёх компонент композита: 1/3 (OECD Handbook [114]). */
    private const val WEIGHT = 1.0 / 3.0

    /**
     * Struct_a = (1/3)(z(Aud) + z(ER_bg) + z(Age)) — структурный композит (ось 1).
     * @param zAud z-нормированная оценка размера аудитории
     * @param zErBg z-нормированный фоновый engagement rate
     * @param zAge z-нормированный возраст аккаунта
     * @return взвешенная (равными весами) сумма z-компонент
     */
    fun structuralComposite(zAud: Double, zErBg: Double, zAge: Double): Double =
        // Среднее трёх z-оценок структурной оси (вес 1/3 у каждой)
        WEIGHT * zAud + WEIGHT * zErBg + WEIGHT * zAge

    /**
     * Topic_a = (1/3)(z(TopVol) + z(TopFocus) + z(Reach)) — тематический композит (ось 2).
     * @param zTopVol z-нормированный объём тематических постов
     * @param zTopFocus z-нормированная сосредоточенность на теме
     * @param zReach z-нормированный охват
     * @return взвешенная (равными весами) сумма z-компонент
     */
    fun topicComposite(zTopVol: Double, zTopFocus: Double, zReach: Double): Double =
        // Среднее трёх z-оценок тематической оси (вес 1/3 у каждой)
        WEIGHT * zTopVol + WEIGHT * zTopFocus + WEIGHT * zReach

    /**
     * Адаптивные пороги = медианы композитов по сессии (θ_Struct, θ_Topic).
     * При таком выборе порога половина авторов всегда оказывается в «высокой»
     * категории, половина — в «низкой».
     * @param structComposites все значения Struct_a по авторам сессии
     * @param topicComposites все значения Topic_a по авторам сессии
     * @return пара (θ_Struct, θ_Topic)
     */
    fun adaptiveThresholds(
        structComposites: List<Double>,
        topicComposites: List<Double>,
    ): Pair<Double, Double> {
        // Порог по структурной оси — медиана структурных композитов
        val thetaStruct = RobustStats.median(structComposites)
        // Порог по тематической оси — медиана тематических композитов
        val thetaTopic = RobustStats.median(topicComposites)
        return thetaStruct to thetaTopic
    }
}
