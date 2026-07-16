/*
 * НАЗНАЧЕНИЕ
 * Модуль робастной регрессии. ВАЖНО: в текущей версии в основных расчётах НЕ
 * используется — оставлен как справочный код и обозначен как направление развития
 * (см. Заключение диплома, «Направления развития»). Реальная робастная статистика
 * пайплайна живёт в analysis/inference/RobustStats.kt.
 *
 * ЧТО ВНУТРИ
 * data class RegressionResult (наклон + свободный член) и объект RobustRegression:
 *   - theilSen(x,y)            — оценка Тейла-Сена (медианный наклон по парам);
 *   - huber(values,k)          — M-оценка Хьюбера (упрощённый IRLS);
 *   - madR2(...)               — робастный аналог R² на основе MAD;
 *   - bootstrapTheilSenCI(...) — бутстрап-CI для наклона (5/95 перцентили);
 *   - permutationTestSlope(...)— перестановочный тест значимости наклона;
 *   - median(...)              — внутренняя медиана.
 *
 * МЕТОД
 * Тейл-Сен: наклон = медиана наклонов по всем парам точек (i,j) — устойчив к
 * выбросам, выдерживает до ~29% загрязнения. При n>500 берётся подвыборка пар для
 * ограничения O(n²). R²_MAD = 1 - (MAD_остатков / MAD_общая)² — робастная доля
 * объяснённой вариации. Значимость наклона — перестановочный тест (p-value как
 * доля перестановок с |наклоном| не меньше наблюдаемого).
 *
 * БИБЛИОТЕКИ
 * kotlin.math.abs/sqrt, kotlin.random.Random (зерно 42 — воспроизводимость).
 *
 * ПРИМЕЧАНИЕ
 * Параметры здесь отличаются от боевого RobustStats (tol 1e-4 vs 1e-6, 20 итераций
 * vs 100) — это намеренно упрощённый справочный вариант, не путать.
 */
package com.example.lomanalyzer.analysis.lom

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * @deprecated Не используется в основных расчётах текущей версии.
 * Регрессия Тейла-Сена упоминается только как направление развития.
 * Код M-оценки Хьюбера может быть полезен как образец для analysis/inference/RobustStats.
 * См. Заключение диплома, «Направления развития».
 *
 * Результат регрессии: наклон (slope) и свободный член (intercept).
 */
@Deprecated("Not used in main calculations; kept as reference for future development")
data class RegressionResult(
    val slope: Double,
    val intercept: Double,
)

/**
 * Модуль робастной регрессии по v6 §14.1.2.
 * Предоставляет оценку Тейла-Сена, M-оценку Хьюбера, R² на основе MAD,
 * бутстрап-CI и перестановочный тест значимости наклона.
 */
object RobustRegression {

    /**
     * Оценка Тейла-Сена: медиана наклонов по всем парам (i,j), где x_i != x_j.
     * При n > 500 — подвыборка O(n*log n) пар для ограничения вычислений.
     * @return RegressionResult с робастными наклоном и свободным членом.
     */
    @Suppress("NestedBlockDepth")
    fun theilSen(x: List<Double>, y: List<Double>): RegressionResult {
        require(x.size == y.size && x.size >= 2)  // нужны парные ряды длиной >= 2
        val n = x.size
        val slopes = mutableListOf<Double>()

        if (n <= 500) {
            // Точный режим: перебираем все O(n²) пары точек
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    if (x[i] != x[j]) {  // пропускаем пары с равным x (наклон не определён)
                        slopes.add((y[j] - y[i]) / (x[j] - x[i]))  // наклон отрезка между точками
                    }
                }
            }
        } else {
            // Приближённый режим: n*20 случайных пар (детерминированное зерно 42)
            val rng = Random(42)
            val sampleSize = n * 20
            repeat(sampleSize) {
                val i = rng.nextInt(n)
                var j = rng.nextInt(n)
                while (j == i) j = rng.nextInt(n)  // гарантируем разные индексы
                if (x[i] != x[j]) {
                    slopes.add((y[j] - y[i]) / (x[j] - x[i]))
                }
            }
        }

        if (slopes.isEmpty()) return RegressionResult(0.0, 0.0)  // нет валидных пар

        val slope = median(slopes)  // робастный наклон = медиана всех попарных наклонов
        // Свободный член = медиана остатков (y_i - slope*x_i)
        val residuals = (0 until n).map { y[it] - slope * x[it] }
        val intercept = median(residuals)

        return RegressionResult(slope, intercept)
    }

    /**
     * M-оценка Хьюбера по v6 §12.4 (упрощённый справочный вариант).
     * Итеративное перевзвешивание; сходится при |mu_new - mu_old| < 1e-4; максимум 20 итераций.
     * @param k настроечная константа Хьюбера (по умолчанию 1.345).
     * @return робастная оценка центра выборки.
     */
    @Suppress("ReturnCount")
    fun huber(values: List<Double>, k: Double = 1.345): Double {
        if (values.isEmpty()) return 0.0
        if (values.size == 1) return values[0]

        var mu = median(values)  // стартовое приближение — медиана
        repeat(20) {
            var sumW = 0.0
            var sumWx = 0.0
            for (v in values) {
                val r = abs(v - mu)                            // абсолютный остаток (без деления на масштаб)
                val w = if (r > 0) minOf(1.0, k / r) else 1.0  // вес Хьюбера: ядро -> 1, хвост -> k/r
                sumW += w
                sumWx += w * v
            }
            val muNew = if (sumW > 0) sumWx / sumW else mu  // взвешенное среднее
            if (abs(muNew - mu) < 1e-4) return muNew         // критерий сходимости (грубее боевого 1e-6)
            mu = muNew
        }
        return mu
    }

    /**
     * Робастный R² на основе MAD по v6 §14.1.2:
     * R²_MAD = 1 - (MAD_остатков / MAD_общая)².
     * @return доля объяснённой вариации в [0..1].
     */
    fun madR2(
        x: List<Double>,
        y: List<Double>,
        slope: Double,
        intercept: Double,
    ): Double {
        // Абсолютные остатки регрессии и абсолютные отклонения y от его медианы
        val residuals = x.indices.map { abs(y[it] - intercept - slope * x[it]) }
        val yMedian = median(y)
        val totalDevs = y.map { abs(it - yMedian) }

        val madRes = median(residuals)  // робастный разброс остатков
        val madTot = median(totalDevs)  // робастный общий разброс y

        if (madTot <= 0) return 0.0          // вырожденный случай (нет разброса в y)
        val ratio = madRes / madTot
        return (1.0 - ratio * ratio).coerceIn(0.0, 1.0)  // 1 - доля необъяснённого разброса
    }

    /**
     * Бутстрап-CI для наклона Тейла-Сена: ресэмпл пар с возвращением,
     * перцентили 5/95.
     * @return пара (нижняя, верхняя) границ доверительного интервала наклона.
     */
    fun bootstrapTheilSenCI(
        x: List<Double>,
        y: List<Double>,
        iterations: Int = 1000,
    ): Pair<Double, Double> {
        val n = x.size
        val rng = Random(42)
        val slopes = mutableListOf<Double>()

        repeat(iterations) {
            // Ресэмпл пар (x,y) с возвращением по общим индексам — сохраняет связь x<->y
            val indices = (0 until n).map { rng.nextInt(n) }
            val bx = indices.map { x[it] }
            val by = indices.map { y[it] }
            val result = theilSen(bx, by)
            slopes.add(result.slope)
        }

        slopes.sort()
        // Границы CI как 5- и 95-перцентили бутстрап-распределения наклонов
        val lo = slopes[(slopes.size * 0.05).toInt()]
        val hi = slopes[(slopes.size * 0.95).toInt().coerceAtMost(slopes.size - 1)]
        return lo to hi
    }

    /**
     * Перестановочный тест значимости наклона.
     * Перемешиваем y, заново считаем наклон Тейла-Сена; p-value = доля перестановок,
     * где |наклон| >= наблюдаемого |наклона| (нулевая гипотеза: связи x->y нет).
     * @return p-value в [0..1]: малое значение -> наклон статистически значим.
     */
    fun permutationTestSlope(
        x: List<Double>,
        y: List<Double>,
        iterations: Int = 500,
    ): Double {
        val observed = theilSen(x, y).slope  // наблюдаемый наклон на исходных данных
        val absObserved = abs(observed)
        val rng = Random(42)
        var exceedCount = 0

        repeat(iterations) {
            // Разрушаем связь x<->y случайной перестановкой y (эмуляция нулевой гипотезы)
            val permY = y.toMutableList()
            permY.shuffle(rng)
            val permSlope = abs(theilSen(x, permY).slope)
            if (permSlope >= absObserved) exceedCount++  // счётчик «не хуже наблюдаемого»
        }

        return exceedCount.toDouble() / iterations  // доля превышений = эмпирическое p-value
    }

    /** Внутренняя медиана (дубликат RobustStats.median для автономности модуля). */
    internal fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0  // чётное n: среднее двух центральных
        } else {
            sorted[mid]                            // нечётное n: центральный элемент
        }
    }
}
