/*
 * НАЗНАЧЕНИЕ
 * Сырое значение плотности вовлечённости (engagement density) по фоновым постам —
 * структурная ось влияния, этап 7. Отражает «качество» отклика на пост с поправкой
 * на размер аудитории (большой охват сам по себе даёт больше реакций).
 *
 * ЧТО ВНУТРИ
 * data class PostReactions (лайки/репосты/комментарии одного поста) и объект
 * EngagementDensityComponent:
 *   - weightedReaction(post) — взвешенная сумма реакций;
 *   - meanReaction(posts)    — средняя взвешенная реакция r_bar по постам;
 *   - computeRaw(...)        — итоговая сырая оценка с гамма-поправкой на F.
 *
 * МЕТОД
 * weightedReaction = likes + 2*reposts + 1.5*comments — веса отражают «стоимость»
 * действия (репост дороже лайка, комментарий — между ними).
 * E_raw = ln(1 + r_bar) - gamma * ln(1 + F): лог-сжатие реакций и подписчиков,
 * вычитание gamma*ln(1+F) гасит зависимость реакций от размера аудитории
 * (gamma — коэффициент частичной поправки, калибруется отдельно).
 *
 * БИБЛИОТЕКИ
 * kotlin.math.ln/max. СВЯЗИ: r_bar считается по дедуплицированным постам;
 * результат идёт на z-нормализацию и в структурный композит.
 */
package com.example.lomanalyzer.analysis.lom

import kotlin.math.ln
import kotlin.math.max

/**
 * Реакции на один пост.
 * @param likes число лайков.
 * @param reposts число репостов.
 * @param comments число комментариев.
 */
data class PostReactions(
    val likes: Int,
    val reposts: Int,
    val comments: Int,
)

/**
 * E_raw = ln(1 + r_bar) - gamma * ln(1 + F),
 * где r_bar — средняя взвешенная реакция по всем фоновым постам после схлопывания
 * дубликатов. weightedReaction = likes + 2*reposts + 1.5*comments.
 */
object EngagementDensityComponent {
    /**
     * Взвешенная реакция на пост (репост весит 2.0, комментарий 1.5, лайк 1.0).
     * @return скалярная «сила» отклика на пост.
     */
    fun weightedReaction(post: PostReactions): Double =
        post.likes + 2.0 * post.reposts + 1.5 * post.comments

    /**
     * Средняя взвешенная реакция r_bar по списку постов.
     * @return среднее weightedReaction; 0.0 для пустого списка.
     */
    fun meanReaction(posts: List<PostReactions>): Double {
        if (posts.isEmpty()) return 0.0
        val total = posts.sumOf { weightedReaction(it) }  // суммарная взвешенная реакция
        return total / max(posts.size, 1)                  // делим на число постов (max страхует от деления на 0)
    }

    /**
     * Итоговая сырая оценка плотности вовлечённости.
     * @param rBar средняя взвешенная реакция.
     * @param followers число подписчиков F.
     * @param gamma коэффициент поправки на размер аудитории.
     * @return ln(1+r_bar) - gamma*ln(1+F).
     */
    fun computeRaw(rBar: Double, followers: Int, gamma: Double): Double =
        // Лог-сжатие реакций минус гамма-поправка на лог-сжатый размер аудитории
        ln(1.0 + rBar) - gamma * ln(1.0 + followers)
}
