/*
 * НАЗНАЧЕНИЕ
 * Двухуровневый непараметрический бутстрап для кластерных (иерархических)
 * данных — этап 8 пайплайна, Приложение Е.3.2. Применяется ИСКЛЮЧИТЕЛЬНО к
 * оценке отклика аудитории Resp_a (тональность комментариев).
 *
 * ЧТО ВНУТРИ
 * Объект TwoLevelBootstrap с единственным методом bootstrap(clusters) и
 * параметрами B_outer=300, B_inner=100. Возвращает DistributionCi (CI долей
 * p+ и p- отклика).
 *
 * АЛГОРИТМ / ЗАЧЕМ ДВА УРОВНЯ
 * Комментарии имеют кластерную структуру: они сгруппированы по постам, и внутри
 * одного поста коррелированы (общая тема/аудитория задаёт «эффект кластера»).
 * Если «схлопнуть» все комментарии в один плоский пул и применить обычный
 * одноуровневый бутстрап, то межкластерная дисперсия игнорируется и
 * неопределённость СИСТЕМАТИЧЕСКИ занижается (интервал слишком узкий).
 * Двухуровневая схема воспроизводит обе ступени случайности:
 *   - внешний уровень (B_outer): ресэмпл n постов с возвращением — учитывает
 *     вариативность между постами;
 *   - внутренний уровень (B_inner): для каждого выбранного поста ресэмпл его
 *     комментариев с возвращением — учитывает вариативность внутри поста.
 * Итого 300×100 = 30 000 реплик; границы 95% CI берутся percentile-методом
 * (2.5/97.5).
 *
 * БИБЛИОТЕКИ / ФРЕЙМВОРКИ
 * kotlinx.coroutines — распараллеливание внешних итераций по ядрам CPU;
 * kotlin.random.Random — ресэмплинг (своё зерно на ядро).
 *
 * СВЯЗИ
 * Вызывается из InferenceExecutor (только для Resp_a); квантили — RobustStats.
 * Для некластерных оценок используется OneLevelBootstrap.
 */
package com.example.lomanalyzer.analysis.inference

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.random.Random

/**
 * Двухуровневый непараметрический бутстрап для кластерных данных (Приложение Е.3.2).
 *
 * Применяется ТОЛЬКО к Resp_a — тональность комментариев имеет кластерную
 * структуру (комментарии сгруппированы по постам). Прямой одноуровневый бутстрап
 * на плоском пуле комментариев систематически занижает неопределённость.
 *
 * B_outer = 300, B_inner = 100 → 30 000 итераций суммарно.
 * Percentile-метод 2.5/97.5 для 95% CI.
 *
 * @see Подраздел_2_1_5_бутстрап.md — обоснование двухуровневой схемы
 */
object TwoLevelBootstrap {

    /** Число внешних итераций (ресэмпл постов), Е.3.2. */
    const val B_OUTER = 300
    /** Число внутренних итераций (ресэмпл комментариев внутри поста), Е.3.2. */
    const val B_INNER = 100
    /** Нижний percentile 95% интервала (2.5%). */
    private const val CI_LO_PERCENTILE = 0.025
    /** Верхний percentile 95% интервала (97.5%). */
    private const val CI_HI_PERCENTILE = 0.975

    /**
     * Двухуровневый бутстрап для распределения Resp_a.
     *
     * @param clusters список списков меток комментариев, по одному на тематический пост:
     *   clusters[i] = метки тональности комментариев под постом i.
     * @param outerIterations число внешних итераций (по умолчанию B_OUTER=300).
     * @param innerIterations число внутренних итераций (по умолчанию B_INNER=100).
     * @param rng источник случайности.
     * @return CI для положительной и отрицательной компонент Resp_a, или null
     *   при недостатке данных (< 2 непустых кластеров).
     */
    suspend fun bootstrap(
        clusters: List<List<String>>,
        outerIterations: Int = B_OUTER,
        innerIterations: Int = B_INNER,
        rng: Random = Random.Default,
    ): DistributionCi? = coroutineScope {
        // Нужно минимум 2 поста с комментариями, иначе межкластерная оценка невозможна
        val nonEmpty = clusters.filter { it.isNotEmpty() }
        if (nonEmpty.size < 2) return@coroutineScope null
        val n = nonEmpty.size  // число кластеров (постов с комментариями)

        // Распараллеливаем именно ВНЕШНИЕ итерации по ядрам CPU
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val chunkSize = (outerIterations + cores - 1) / cores

        val deferreds = (0 until cores).map { coreIdx ->
            async(Dispatchers.Default) {
                val localRng = Random(rng.nextLong() + coreIdx)  // независимое зерно на ядро
                val start = coreIdx * chunkSize
                val end = minOf(start + chunkSize, outerIterations)
                val localPos = mutableListOf<Double>()
                val localNeg = mutableListOf<Double>()

                for (outer in start until end) {
                    // ВНЕШНИЙ уровень: ресэмпл n постов с возвращением (вариативность между постами)
                    val sampledPosts = List(n) { nonEmpty[localRng.nextInt(n)] }

                    // ВНУТРЕННИЙ уровень: для каждого выбранного поста ресэмплируем его комментарии
                    repeat(innerIterations) {
                        var totalPos = 0
                        var totalNeg = 0
                        var totalCount = 0

                        for (postComments in sampledPosts) {
                            val m = postComments.size
                            // Ресэмпл m комментариев данного поста с возвращением
                            repeat(m) {
                                val label = postComments[localRng.nextInt(m)]
                                when (label.uppercase()) {
                                    "POSITIVE" -> totalPos++
                                    "NEGATIVE" -> totalNeg++
                                }
                                totalCount++
                            }
                        }

                        // Доли тональности по всему набору ресэмплированных комментариев этой реплики
                        if (totalCount > 0) {
                            localPos.add(totalPos.toDouble() / totalCount)
                            localNeg.add(totalNeg.toDouble() / totalCount)
                        }
                    }
                }

                localPos to localNeg
            }
        }

        // Объединяем 30 000 реплик со всех ядер в распределения долей и сортируем для квантилей
        val results = deferreds.awaitAll()
        val allPos = results.flatMap { it.first }.sorted()
        val allNeg = results.flatMap { it.second }.sorted()

        if (allPos.isEmpty()) return@coroutineScope null  // защита от полностью пустого результата

        val totalIter = outerIterations * innerIterations  // суммарно реплик (300×100), пишется в метаданные
        // Границы 95% CI — percentile-метод 2.5/97.5 по бутстрап-распределению
        DistributionCi(
            positiveCi = BootstrapCi(
                RobustStats.quantile(allPos, CI_LO_PERCENTILE),
                RobustStats.quantile(allPos, CI_HI_PERCENTILE),
                "two_level", totalIter,
            ),
            negativeCi = BootstrapCi(
                RobustStats.quantile(allNeg, CI_LO_PERCENTILE),
                RobustStats.quantile(allNeg, CI_HI_PERCENTILE),
                "two_level", totalIter,
            ),
        )
    }
}
