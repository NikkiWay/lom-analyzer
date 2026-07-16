/*
 * НАЗНАЧЕНИЕ
 * Байесовская оценка качества тематической фильтрации (контроль качества к
 * этапу 6, docs/algorithm.md). По голосам аналитика на выборке постов вычисляет
 * апостериорные распределения precision и recall системного фильтра.
 *
 * ЧТО ВНУТРИ
 * - data class BetaPosterior: параметры и сводка Beta-апостериорного
 *   распределения (среднее, 95% интервал, alpha, beta).
 * - data class ValidationMetrics: precision и recall как BetaPosterior + число
 *   учтённых голосов.
 * - object BayesBetaValidator: расчёт метрик (computeMetrics) и одного
 *   Beta-апостериора (betaPosterior).
 *
 * МЕТОД (Beta-Bernoulli, сопряжённый байесовский вывод)
 * Априор Beta(1,1) (равномерный). По размеченным голосам считаются TP/FP/FN:
 *   precision ~ Beta(1+TP, 1+FP), recall ~ Beta(1+TP, 1+FN).
 * Среднее = alpha/(alpha+beta). 95% доверительный интервал — нормальная
 * аппроксимация (mean ± 1.96·sd), обрезанная в [0..1].
 *
 * БИБЛИОТЕКИ
 * kotlin.math.sqrt; внешних зависимостей нет.
 */
package com.example.lomanalyzer.analysis.topic

import kotlin.math.sqrt

/**
 * Сводка одного Beta-апостериорного распределения.
 *
 * @param mean апостериорное среднее (точечная оценка доли).
 * @param ci95Lo нижняя граница 95% интервала (обрезана снизу нулём).
 * @param ci95Hi верхняя граница 95% интервала (обрезана сверху единицей).
 * @param alpha параметр alpha распределения Beta (1 + число «успехов»).
 * @param beta параметр beta распределения Beta (1 + число «неудач»).
 */
data class BetaPosterior(
    val mean: Double,
    val ci95Lo: Double,
    val ci95Hi: Double,
    val alpha: Double,
    val beta: Double,
)

/**
 * Метрики качества фильтрации по голосам аналитика.
 *
 * @param precision апостериор точности (доля верных среди признанных релевантными).
 * @param recall апостериор полноты (доля найденных среди действительно релевантных).
 * @param totalVotes число учтённых (размеченных) голосов.
 */
data class ValidationMetrics(
    val precision: BetaPosterior,
    val recall: BetaPosterior,
    val totalVotes: Int,
)

/** Калькулятор байесовских метрик precision/recall тематического фильтра. */
object BayesBetaValidator {
    /**
     * Computes Bayes Beta posteriors for precision and recall.
     * Prior: Beta(1, 1) (uniform).
     *
     * Вычисляет байесовские Beta-апостериоры для precision и recall.
     * Априор: Beta(1, 1) (равномерный).
     *
     * @param votes list of (systemRelevant, analystVote) pairs
     *   where analystVote: true=relevant, false=not relevant, null=unsure (skipped)
     *   список пар (решение системы, голос аналитика); analystVote=null означает
     *   «не уверен» и из расчёта исключается.
     */
    fun computeMetrics(votes: List<Pair<Boolean, Boolean?>>): ValidationMetrics {
        // Берём только размеченные голоса (null — «не уверен» — отбрасываем)
        val resolved = votes.filter { it.second != null }

        // Подсчёт ячеек матрицы ошибок относительно решения системы:
        // TP — система права (релевантен и подтверждён), FP — ложное срабатывание,
        // FN — пропуск (система сочла нерелевантным, а аналитик — релевантным)
        var tp = 0; var fp = 0; var fn = 0
        for ((systemRelevant, analystVote) in resolved) {
            val actual = analystVote!!
            when {
                systemRelevant && actual -> tp++
                systemRelevant && !actual -> fp++
                !systemRelevant && actual -> fn++
            }
        }

        // Апостериоры с равномерным априором Beta(1,1): прибавляем 1 к каждому счётчику
        val precision = betaPosterior(1.0 + tp, 1.0 + fp)
        val recall = betaPosterior(1.0 + tp, 1.0 + fn)

        return ValidationMetrics(
            precision = precision,
            recall = recall,
            totalVotes = resolved.size,
        )
    }

    /**
     * Строит сводку Beta-апостериора по его параметрам alpha, beta.
     * @return [BetaPosterior] со средним и 95% интервалом (нормальная аппроксимация).
     */
    private fun betaPosterior(alpha: Double, beta: Double): BetaPosterior {
        // Среднее распределения Beta(alpha, beta)
        val mean = alpha / (alpha + beta)
        // Normal approximation for 95% CI
        // Дисперсия Beta = alpha*beta / ((alpha+beta)^2 * (alpha+beta+1))
        val variance = (alpha * beta) / ((alpha + beta).let { it * it * (it + 1) })
        val sd = sqrt(variance)
        return BetaPosterior(
            mean = mean,
            // 95% интервал по нормальной аппроксимации, обрезанный в [0..1]
            ci95Lo = (mean - 1.96 * sd).coerceAtLeast(0.0),
            ci95Hi = (mean + 1.96 * sd).coerceAtMost(1.0),
            alpha = alpha,
            beta = beta,
        )
    }
}
