/*
 * НАЗНАЧЕНИЕ
 * Одноуровневый непараметрический бутстрап — этап 8 пайплайна (оценка
 * неопределённости, Приложение Е.3.1). Строит 95% доверительные интервалы
 * для оценок, вычисляемых по выборке постов одного автора: ER_bg, ER_top,
 * Reach и распределение тональности Pos_a.
 *
 * ЧТО ВНУТРИ
 * Объект OneLevelBootstrap:
 *   - bootstrap(values, statistic) — CI для скалярной статистики (среднее/сумма);
 *   - bootstrapDistribution(labels) — CI для долей (p+, p-) тональности Pos_a.
 * data-классы BootstrapCi (границы интервала + метаданные) и DistributionCi
 * (пара CI для положительной и отрицательной долей).
 *
 * МЕТОД
 * Непараметрический бутстрап: из исходной выборки размера n многократно (B=1000)
 * формируются ресэмплы того же размера n с возвращением, для каждого считается
 * статистика. По полученному распределению B значений берутся percentile-границы
 * 2.5% и 97.5% — это и есть 95% доверительный интервал (percentile-метод Е.3.3).
 * Percentile-метод выбран как непараметрический: не требует допущения о форме
 * распределения статистики и автоматически уважает её асимметрию.
 *
 * БИБЛИОТЕКИ / ФРЕЙМВОРКИ
 * kotlinx.coroutines — распараллеливание B итераций по ядрам CPU (Dispatchers.Default);
 * kotlin.random.Random — генератор ресэмплов (своё зерно на каждое ядро).
 *
 * СВЯЗИ
 * Вызывается из InferenceExecutor; квантили берутся из RobustStats.quantile.
 * Для кластерных данных (Resp_a) используется TwoLevelBootstrap.
 */
package com.example.lomanalyzer.analysis.inference

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.random.Random

/**
 * Одноуровневый непараметрический бутстрап (Приложение Е.3.1).
 *
 * Применяется к оценкам по выборке постов: ER_bg, ER_top, Reach, Pos.
 * B = 1000 итераций. Percentile-метод 2.5/97.5 для 95% CI.
 * Распараллелен по ядрам CPU через корутины.
 */
object OneLevelBootstrap {

    /** Число бутстрап-итераций (Е.3.1). */
    const val B = 1000
    /** Нижний percentile 95% интервала (2.5%). */
    private const val CI_LO_PERCENTILE = 0.025
    /** Верхний percentile 95% интервала (97.5%). */
    private const val CI_HI_PERCENTILE = 0.975

    /**
     * Бутстрап скалярной статистики, вычисляемой по списку значений-по-постам.
     *
     * @param values по одному значению на пост (например, реакции/F для ER, просмотры для Reach).
     * @param statistic функция, считающая оценку по ресэмплу (например, среднее или сумма).
     * @param iterations число бутстрап-итераций (по умолчанию B=1000).
     * @param rng источник случайности (для воспроизводимости тестов).
     * @return пара (ciLo, ciHi) или null, если выборка слишком мала (< 2).
     */
    suspend fun bootstrap(
        values: List<Double>,
        statistic: (List<Double>) -> Double,
        iterations: Int = B,
        rng: Random = Random.Default,
    ): BootstrapCi? = coroutineScope {
        if (values.size < 2) return@coroutineScope null  // на одной точке бутстрап бессмыслен
        val n = values.size

        // Делим B итераций на примерно равные порции по числу доступных ядер CPU
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val chunkSize = (iterations + cores - 1) / cores  // округление вверх, чтобы покрыть все итерации

        val deferreds = (0 until cores).map { coreIdx ->
            async(Dispatchers.Default) {
                // Своё зерно на каждое ядро: независимые потоки случайных чисел, но детерминированно от rng
                val localRng = Random(rng.nextLong() + coreIdx)
                val start = coreIdx * chunkSize
                val end = minOf(start + chunkSize, iterations)  // последняя порция может быть короче
                val localResults = DoubleArray(end - start)
                for (i in start until end) {
                    // Ресэмпл: n значений выбираются с возвращением из исходной выборки
                    val sample = List(n) { values[localRng.nextInt(n)] }
                    // Считаем целевую статистику на этом ресэмпле
                    localResults[i - start] = statistic(sample)
                }
                localResults.toList()
            }
        }

        // Собираем результаты со всех ядер в единое отсортированное бутстрап-распределение
        val allResults = deferreds.awaitAll().flatten().sorted()
        // Границы 95% CI — percentile-метод: 2.5- и 97.5-квантили распределения
        val lo = RobustStats.quantile(allResults, CI_LO_PERCENTILE)
        val hi = RobustStats.quantile(allResults, CI_HI_PERCENTILE)
        BootstrapCi(lo, hi, "one_level", iterations)
    }

    /**
     * Бутстрап распределения тональности Pos_a: ресэмплируем посты и заново
     * считаем доли (p+, p0, p-). Возвращает CI для положительной и отрицательной
     * компонент (нейтральная p0 = 1 - p+ - p- получается остатком).
     *
     * @param labels тональность каждого тематического поста автора ("POSITIVE"/"NEGATIVE"/...).
     * @return DistributionCi с CI долей p+ и p-, или null при размере выборки < 2.
     */
    suspend fun bootstrapDistribution(
        labels: List<String>,
        iterations: Int = B,
        rng: Random = Random.Default,
    ): DistributionCi? = coroutineScope {
        if (labels.size < 2) return@coroutineScope null
        val n = labels.size

        // Та же схема распараллеливания B итераций по ядрам, что и в bootstrap()
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val chunkSize = (iterations + cores - 1) / cores

        val deferreds = (0 until cores).map { coreIdx ->
            async(Dispatchers.Default) {
                val localRng = Random(rng.nextLong() + coreIdx)
                val start = coreIdx * chunkSize
                val end = minOf(start + chunkSize, iterations)
                val localPos = DoubleArray(end - start)
                val localNeg = DoubleArray(end - start)
                for (i in start until end) {
                    var pos = 0; var neg = 0
                    // Ресэмпл n меток с возвращением; считаем число положительных/отрицательных
                    repeat(n) {
                        when (labels[localRng.nextInt(n)].uppercase()) {
                            "POSITIVE" -> pos++
                            "NEGATIVE" -> neg++
                        }
                    }
                    // Доли в данном ресэмпле — оценки p+ и p- на этой итерации
                    localPos[i - start] = pos.toDouble() / n
                    localNeg[i - start] = neg.toDouble() / n
                }
                localPos.toList() to localNeg.toList()
            }
        }

        // Объединяем распределения долей со всех ядер и сортируем для взятия квантилей
        val results = deferreds.awaitAll()
        val allPos = results.flatMap { it.first }.sorted()
        val allNeg = results.flatMap { it.second }.sorted()

        DistributionCi(
            positiveCi = BootstrapCi(
                RobustStats.quantile(allPos, CI_LO_PERCENTILE),
                RobustStats.quantile(allPos, CI_HI_PERCENTILE),
                "one_level", iterations,
            ),
            negativeCi = BootstrapCi(
                RobustStats.quantile(allNeg, CI_LO_PERCENTILE),
                RobustStats.quantile(allNeg, CI_HI_PERCENTILE),
                "one_level", iterations,
            ),
        )
    }
}

/**
 * Доверительный интервал бутстрапа.
 * @param ciLo нижняя граница (percentile 2.5%).
 * @param ciHi верхняя граница (percentile 97.5%).
 * @param procedureType тип процедуры: "one_level" или "two_level" (для записи в БД).
 * @param iterations фактическое число итераций (метаданные качества).
 */
data class BootstrapCi(
    val ciLo: Double,
    val ciHi: Double,
    val procedureType: String,
    val iterations: Int,
)

/**
 * Пара доверительных интервалов для распределения тональности
 * (положительной и отрицательной долей) — для Pos_a и Resp_a.
 */
data class DistributionCi(
    val positiveCi: BootstrapCi,
    val negativeCi: BootstrapCi,
)
