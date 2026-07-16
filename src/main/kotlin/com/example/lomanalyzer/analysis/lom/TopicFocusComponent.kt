/*
 * НАЗНАЧЕНИЕ
 * Сырое значение тематической сфокусированности TopFocus_a (ось тематической
 * активности, этап 7, формула Е.4.2): какая доля публикаций автора посвящена
 * теме. Отвечает на вопрос «специализируется ли автор на теме или пишет о ней
 * мимоходом».
 *
 * ЧТО ВНУТРИ
 * Объект TopicFocusComponent:
 *   - computeLeaveOneOutPrior(...) — априорная доля темы по ОСТАЛЬНЫМ авторам;
 *   - computeRaw(...)              — сглаженная доля темы у автора.
 * Константа ALPHA = 5 — сила сглаживания.
 *
 * МЕТОД
 * Байесовское сглаживание долей. Наивная доля N_topic/N_all нестабильна при малом
 * числе постов (например, 1 из 1 = 1.0). Поэтому к ней подмешивается априор p_a:
 *   T_raw_a = (N_topic_a + alpha * p_a) / (N_all_a + alpha), ограничено сверху 1.0.
 * Априор p_a — leave-one-out: доля темы по всем авторам, КРОМЕ текущего
 * (исключение собственных постов убирает самоподтверждение/утечку).
 * alpha=5 задаёт «вес» априора в постах.
 *
 * БИБЛИОТЕКИ
 * kotlin.math.min. СВЯЗИ: результат идёт на z-нормализацию и в тематический композит.
 */
package com.example.lomanalyzer.analysis.lom

import kotlin.math.min

/**
 * Leave-one-out prior: p_a = sum_{a'!=a} N_topic_a' / sum_{a'!=a} N_all_a'.
 * T_raw_a = (N_topic_a + alpha * p_a) / (N_all_a + alpha), ограничено 1.0.
 * alpha = 5 (константа сглаживания).
 */
object TopicFocusComponent {
    /** Сила байесовского сглаживания (вес априора в «виртуальных» постах). */
    private const val ALPHA = 5.0

    /**
     * Априорная доля тематических постов по всем авторам, кроме текущего (leave-one-out).
     * @param totalTopicAll суммарное число тематических постов по всем авторам.
     * @param totalPostsAll суммарное число всех постов по всем авторам.
     * @param authorTopicCount тематические посты текущего автора (вычитаются).
     * @param authorPostCount все посты текущего автора (вычитаются).
     * @return доля темы среди остальных авторов; 0.5 как нейтральный fallback, если данных нет.
     */
    fun computeLeaveOneOutPrior(
        totalTopicAll: Int,
        totalPostsAll: Int,
        authorTopicCount: Int,
        authorPostCount: Int,
    ): Double {
        // Исключаем вклад самого автора, чтобы априор не «подтверждал» его же поведение
        val otherTopic = totalTopicAll - authorTopicCount
        val otherPosts = totalPostsAll - authorPostCount
        return if (otherPosts > 0) otherTopic.toDouble() / otherPosts else 0.5
    }

    /**
     * Сглаженная доля тематических постов у автора.
     * @param topicCount число тематических постов автора.
     * @param allCount число всех постов автора.
     * @param prior априор из computeLeaveOneOutPrior.
     * @return сглаженная доля в [0..1].
     */
    fun computeRaw(topicCount: Int, allCount: Int, prior: Double): Double {
        // Байесовское сглаживание: подмешиваем alpha*prior к наблюдаемым долям
        val raw = (topicCount + ALPHA * prior) / (allCount + ALPHA)
        return min(raw, 1.0)  // доля не может превышать 1.0
    }
}
