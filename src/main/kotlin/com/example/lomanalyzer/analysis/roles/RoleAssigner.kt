/*
 * НАЗНАЧЕНИЕ
 * Квадрантная классификация авторов по ролям и расчёт двух качественных
 * атрибутов (этап 9 алгоритма, docs/algorithm.md; диплом 2.1.6). Превращает
 * числовые композиты и распределения тональности в интерпретируемые категории.
 *
 * ЧТО ВНУТРИ
 * object RoleAssigner:
 *  - assignRole(...)        — 4 базовые роли по квадранту (Struct vs θ, Topic vs θ);
 *  - authorPosition(...)    — атрибут «позиция автора» из Pos_a (argmax);
 *  - audienceResponse(...)  — атрибут «отклик аудитории» из Resp_a (правило 50%).
 *
 * МЕТОД
 * Базовая роль определяется знаком отклонения композитов от адаптивных порогов
 * θ_Struct, θ_Topic (медианы по сессии) → 4 квадранта:
 *   высокая структура + высокая тема  → авторитетный лидер;
 *   высокая структура + низкая тема    → «спящий гигант»;
 *   низкая структура  + высокая тема   → тематический активист;
 *   иначе                              → фоновый автор.
 * Позиция автора — доминирующая (argmax) категория Pos_a. Отклик аудитории —
 * категория, превысившая 50% в Resp_a, иначе MIXED (преимущественно нейтральный
 * отклик трактуется как «не критичный» ≈ одобрительный).
 *
 * ПРИНЦИПЫ
 * НЕТ автоматических рекомендаций, НЕТ кластеризации, НЕТ настраиваемых весов.
 *
 * БИБЛИОТЕКИ
 * Stdlib Kotlin; категории — enum'ы из core (BaseRole/AuthorPosition/AudienceResponse).
 *
 * СВЯЗИ
 * Вызывается из CompositeRolesExecutor; результаты сохраняются как роли/атрибуты.
 */
package com.example.lomanalyzer.analysis.roles

import com.example.lomanalyzer.core.AudienceResponse
import com.example.lomanalyzer.core.AuthorPosition
import com.example.lomanalyzer.core.BaseRole

/**
 * Квадрантная классификация ролей (диплом 2.1.6).
 *
 * 4 базовые роли определяются парой (Struct_a vs θ_Struct, Topic_a vs θ_Topic).
 * 2 атрибута: позиция автора (из Pos_a) и отклик аудитории (из Resp_a).
 *
 * НЕТ автоматических рекомендаций. НЕТ кластеризации. НЕТ настраиваемых весов.
 */
object RoleAssigner {

    /**
     * Назначает базовую роль по квадранту относительно адаптивных порогов.
     * @param structComposite Struct_a — структурный композит автора
     * @param topicComposite Topic_a — тематический композит автора
     * @param thetaStruct θ_Struct — порог по структурной оси (медиана)
     * @param thetaTopic θ_Topic — порог по тематической оси (медиана)
     * @return одна из 4 базовых ролей
     */
    fun assignRole(structComposite: Double, topicComposite: Double,
                   thetaStruct: Double, thetaTopic: Double): BaseRole =
        when {
            // Оба композита ≥ порогов → авторитетный лидер (сильная структура + активная тема)
            structComposite >= thetaStruct && topicComposite >= thetaTopic -> BaseRole.AUTHORITATIVE_LEADER
            // Сильная структура, но слабая тема → «спящий гигант» (большой вес, мало по теме)
            structComposite >= thetaStruct && topicComposite < thetaTopic -> BaseRole.SLEEPING_GIANT
            // Слабая структура, но активная тема → тематический активист
            structComposite < thetaStruct && topicComposite >= thetaTopic -> BaseRole.TOPIC_ACTIVIST
            // Оба ниже порога → фоновый автор
            else -> BaseRole.BACKGROUND_AUTHOR
        }

    /**
     * Атрибут «позиция автора» из Pos_a = (p+, p0, p-).
     * Доминирующая категория = argmax (с приоритетом «за» при равенстве).
     * @return SUPPORTIVE / CRITICAL / NEUTRAL
     */
    fun authorPosition(posPositive: Double, posNeutral: Double, posNegative: Double): AuthorPosition =
        when {
            // Позитив доминирует (≥ нейтраль и ≥ негатив) → поддерживающая позиция
            posPositive >= posNeutral && posPositive >= posNegative -> AuthorPosition.SUPPORTIVE
            // Иначе доминирует негатив → критическая позиция
            posNegative >= posPositive && posNegative >= posNeutral -> AuthorPosition.CRITICAL
            // Иначе преобладает нейтраль → нейтральная позиция
            else -> AuthorPosition.NEUTRAL
        }

    /**
     * Атрибут «отклик аудитории» из Resp_a = (q+, q0, q-).
     * «Смешанный» (MIXED), если ни одна категория не превышает 50%.
     * @return APPROVING / CRITICAL / MIXED
     */
    fun audienceResponse(respPositive: Double, respNeutral: Double, respNegative: Double): AudienceResponse =
        when {
            // Более половины комментариев позитивны → одобрительный отклик
            respPositive > 0.5 -> AudienceResponse.APPROVING
            // Более половины негативны → критический отклик
            respNegative > 0.5 -> AudienceResponse.CRITICAL
            // Преимущественно нейтральный отклик трактуем как «не критичный» ≈ одобрительный
            respNeutral > 0.5 -> AudienceResponse.APPROVING // predominantly neutral ≈ not critical
            // Ни одна категория не набрала большинства → смешанный отклик
            else -> AudienceResponse.MIXED
        }
}
