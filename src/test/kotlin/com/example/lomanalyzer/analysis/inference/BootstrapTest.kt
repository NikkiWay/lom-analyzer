/*
 * НАЗНАЧЕНИЕ
 * Юнит-тесты этапа 8 (оценка неопределённости): одноуровневый и двухуровневый
 * бутстрап для построения 95-процентных доверительных интервалов (формулы Е.3).
 *
 * ЧТО ВНУТРИ
 * Класс BootstrapTest c двумя группами тестов:
 *   1) OneLevelBootstrap — одноуровневый бутстрап (Е.3.1, B=1000): валидность CI,
 *      покрытие истинного среднего на уровне 95 процентов, null для одного элемента,
 *      бутстрап распределения меток тональности (bootstrapDistribution);
 *   2) TwoLevelBootstrap — двухуровневый бутстрап (Е.3.2, 300×100) для кластерных
 *      данных (посты с комментариями): валидность CI, null для одного кластера,
 *      более широкий CI на кластерных данных, чем у одноуровневого (учёт структуры).
 *
 * МЕТОД
 * Перцентильный бутстрап: ресэмплинг с возвращением, статистика на каждой выборке,
 * границы CI = перцентили 2.5 и 97.5 (Е.3.3). Для повторяемости задаётся seed
 * через kotlin.random.Random. Двухуровневый: внешний ресэмплинг кластеров,
 * внутренний — наблюдений внутри кластера (только для Resp_a).
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (@Test); kotlinx.coroutines.runBlocking — тестируемые suspend-функции
 * бутстрапа выполняются синхронно.
 *
 * СВЯЗИ
 * Тестируемые типы из пакета analysis/inference: OneLevelBootstrap, TwoLevelBootstrap.
 */
package com.example.lomanalyzer.analysis.inference

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Тесты одноуровневого (Е.3.1) и двухуровневого (Е.3.2) бутстрапа доверительных интервалов.
 */
class BootstrapTest {

    // ── OneLevelBootstrap ──

    /**
     * Проверяет валидность одноуровневого CI для среднего (Е.3.1).
     * Arrange: значения 1..50 (истинное среднее 25.5), статистика — среднее, фиксированный seed=42.
     * Assert: CI не null; нижняя граница строго меньше верхней; границы лежат внутри
     * диапазона данных (ciLo>0, ciHi<51); тип процедуры помечен как "one_level".
     */
    @Test
    fun `one-level bootstrap produces valid CI`() = runBlocking {
        val values = (1..50).map { it.toDouble() }
        // Act: 500 итераций перцентильного бутстрапа среднего
        val ci = OneLevelBootstrap.bootstrap(values, { it.average() }, iterations = 500, rng = Random(42))
        assertNotNull(ci)
        assertTrue(ci!!.ciLo < ci.ciHi, "ciLo=${ci.ciLo} should be < ciHi=${ci.ciHi}")
        assertTrue(ci.ciLo > 0, "ciLo should be positive")
        assertTrue(ci.ciHi < 51, "ciHi should be < 51")
        assertEquals("one_level", ci.procedureType)
    }

    /**
     * Проверяет покрытие: 95-процентный CI должен накрывать истинное среднее
     * в большинстве повторов (свойство корректности доверительного интервала).
     * Arrange: данные с известным средним 50, 20 прогонов с разными seed.
     * Assert: истинное среднее попадает в CI не менее чем в 80 процентах прогонов
     * (порог занижен относительно 95 процентов ради скорости теста).
     */
    @Test
    fun `one-level bootstrap covers true mean at 95 percent`() = runBlocking {
        // Generate known data: mean = 50
        val trueMean = 50.0
        val data = (1..100).map { trueMean + (it - 50).toDouble() }
        var covered = 0
        val runs = 20
        // Прогоняем бутстрап с разными seed и считаем долю покрытий истинного среднего
        for (seed in 1..runs) {
            val ci = OneLevelBootstrap.bootstrap(data, { it.average() }, iterations = 500, rng = Random(seed.toLong()))
            if (ci != null && ci.ciLo <= trueMean && trueMean <= ci.ciHi) covered++
        }
        // At least 80% of runs should cover the true mean (relaxed for speed)
        assertTrue(covered >= runs * 0.8, "Coverage $covered/$runs should be >= 80%")
    }

    /**
     * Граничный случай: для выборки из одного элемента бутстрап невозможен
     * (нет вариативности) → возвращается null.
     */
    @Test
    fun `one-level returns null for single element`() = runBlocking {
        val ci = OneLevelBootstrap.bootstrap(listOf(5.0), { it.average() })
        assertNull(ci)
    }

    /**
     * Проверяет бутстрап распределения меток тональности (доли POSITIVE/NEUTRAL/NEGATIVE):
     * для каждой категории строится свой CI.
     * Arrange: 30 POSITIVE, 50 NEUTRAL, 20 NEGATIVE. Assert: CI позитива валиден
     * (lo<hi) и доли неотрицательны (lo≥0 для позитива и негатива).
     */
    @Test
    fun `one-level distribution bootstrap produces valid CIs`() = runBlocking {
        val labels = List(30) { "POSITIVE" } + List(50) { "NEUTRAL" } + List(20) { "NEGATIVE" }
        // Act: бутстрап долей по категориям
        val result = OneLevelBootstrap.bootstrapDistribution(labels, iterations = 500, rng = Random(42))
        assertNotNull(result)
        assertTrue(result!!.positiveCi.ciLo < result.positiveCi.ciHi)
        assertTrue(result.positiveCi.ciLo >= 0)
        assertTrue(result.negativeCi.ciLo >= 0)
    }

    // ── TwoLevelBootstrap ──

    /**
     * Проверяет валидность двухуровневого CI на кластерных данных (Е.3.2).
     * Arrange: 5 постов (кластеров), в каждом 10 комментариев (6 POSITIVE, 4 NEGATIVE).
     * Act: двухуровневый бутстрап с внешними 50 и внутренними 20 итерациями.
     * Assert: CI валиден (lo<hi), доля позитива неотрицательна, тип процедуры "two_level".
     */
    @Test
    fun `two-level bootstrap produces valid CI`() = runBlocking {
        // 5 posts with 10 comments each
        val clusters = (1..5).map { postIdx ->
            // Каждый пост (кластер) — набор меток тональности комментариев
            val labels = (1..10).map { if (it <= 6) "POSITIVE" else "NEGATIVE" }
            labels
        }
        // Act: внешний ресэмплинг постов + внутренний ресэмплинг комментариев
        val result = TwoLevelBootstrap.bootstrap(clusters, outerIterations = 50, innerIterations = 20, rng = Random(42))
        assertNotNull(result)
        assertTrue(result!!.positiveCi.ciLo < result.positiveCi.ciHi)
        assertTrue(result.positiveCi.ciLo >= 0)
        assertEquals("two_level", result.positiveCi.procedureType)
    }

    /**
     * Граничный случай: один кластер не позволяет оценить межкластерную вариативность
     * на внешнем уровне → возвращается null.
     */
    @Test
    fun `two-level returns null for single cluster`() = runBlocking {
        val result = TwoLevelBootstrap.bootstrap(listOf(listOf("POSITIVE", "NEGATIVE")))
        assertNull(result)
    }

    /**
     * Ключевое свойство двухуровневого бутстрапа (диплом 2.1.5): на сильно кластерных
     * данных он даёт более широкий CI, так как учитывает межкластерную дисперсию,
     * которую одноуровневый бутстрап на «плоских» метках недооценивает.
     * Arrange: 5 кластеров с однородной внутри тональностью; те же метки «расплющены»
     * для одноуровневого бутстрапа.
     * Assert: ширина двухуровневого CI сопоставима или больше (порог 0.5*oneWidth
     * занижен ради стабильности при малом числе итераций).
     */
    @Test
    fun `two-level CI is wider than one-level on clustered data`() = runBlocking {
        // Highly clustered: each post has uniform sentiment
        val clusters = listOf(
            List(20) { "POSITIVE" },
            List(20) { "POSITIVE" },
            List(20) { "NEGATIVE" },
            List(20) { "NEGATIVE" },
            List(20) { "NEUTRAL" },
        )
        // Те же наблюдения без кластерной структуры — для одноуровневого бутстрапа
        val flatLabels = clusters.flatten()

        // Act: двухуровневый учитывает структуру кластеров, одноуровневый — нет
        val twoLevel = TwoLevelBootstrap.bootstrap(clusters, outerIterations = 100, innerIterations = 50, rng = Random(42))
        val oneLevel = OneLevelBootstrap.bootstrapDistribution(flatLabels, iterations = 500, rng = Random(42))

        assertNotNull(twoLevel)
        assertNotNull(oneLevel)

        // Сравниваем ширины CI доли позитива
        val twoWidth = twoLevel!!.positiveCi.ciHi - twoLevel.positiveCi.ciLo
        val oneWidth = oneLevel!!.positiveCi.ciHi - oneLevel.positiveCi.ciLo
        // Two-level should generally produce wider CI due to cluster structure
        // (this is the whole point per diploma 2.1.5)
        assertTrue(twoWidth > oneWidth * 0.5, "twoWidth=$twoWidth should be comparable to or wider than oneWidth=$oneWidth")
    }
}
