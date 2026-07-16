/*
 * НАЗНАЧЕНИЕ
 * Юнит-тесты этапа 7 (расчёт 11 оценок по 4 осям, Приложение Е.4): структурные,
 * тематические оценки, позиция автора и отклик аудитории.
 *
 * ЧТО ВНУТРИ
 * Класс ScoreCalculatorTest c группами тестов по объектам-калькуляторам:
 *   1) StructuralScores — Aud_a=ln(1+F) (Е.4.1), Age_a — нормировка возраста,
 *      ER_bg — фоновый engagement rate (Е.4.1);
 *   2) TopicScores — TopVol (объём), TopFocus (фокус на теме), Reach (охват) (Е.4.2);
 *   3) PositionScore — Pos_a, распределение тональности постов автора (Е.4.3);
 *   4) ResponseScores — ER_top (Е.4.4) и Resp_a, распределение тональности
 *      комментариев аудитории (Е.4.4).
 *
 * МЕТОД
 * Проверяются точные формулы Е.4: логарифм аудитории, линейная нормировка возраста,
 * engagement rate = средняя сумма реакций на подписчика, доли тональности (сумма=1).
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (@Test); часть тестов — однострочные функции через знак равенства;
 * kotlin.math.ln/abs для эталонных вычислений.
 *
 * СВЯЗИ
 * Тестируемые объекты из пакета analysis/scoring: StructuralScores, TopicScores,
 * PositionScore, ResponseScores; тип SentimentDistribution из пакета core.
 */
package com.example.lomanalyzer.analysis.scoring

import com.example.lomanalyzer.core.SentimentDistribution
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.ln

/**
 * Тесты 11 оценок по 4 осям влияния (структурная, тематическая, позиция, отклик) — формулы Е.4.
 */
class ScoreCalculatorTest {

    // ── Aud_a = ln(1 + F_a) (E.4.1) ──

    /** Aud_a при 0 подписчиков: ln(1+0)=0 (Е.4.1). */
    @Test fun `aud of 0 followers`() = assertEquals(0.0, StructuralScores.aud(0), 0.001)

    /** Aud_a при 1000 подписчиков равно ln(1001) — точная проверка формулы Aud_a=ln(1+F) (Е.4.1). */
    @Test fun `aud of 1000 followers`() = assertEquals(ln(1001.0), StructuralScores.aud(1000), 0.001)

    /** Монотонность Aud_a: логарифм растёт с числом подписчиков (больше аудитория → выше оценка). */
    @Test fun `aud is monotonically increasing`() {
        assertTrue(StructuralScores.aud(100) < StructuralScores.aud(1000))
        assertTrue(StructuralScores.aud(1000) < StructuralScores.aud(10000))
    }

    // ── Age_a (E.4.1) ──

    /**
     * Age_a — линейная нормировка возраста аккаунта на максимум по сессии (Е.4.1):
     * половина максимума → 0.5, максимум → 1.0, нулевой возраст → 0.0.
     */
    @Test fun `age normalization`() {
        assertEquals(0.5, StructuralScores.age(365, 730), 0.001)
        assertEquals(1.0, StructuralScores.age(730, 730), 0.001)
        assertEquals(0.0, StructuralScores.age(0, 730), 0.001)
    }

    /** Защита от деления на ноль: при нулевом максимуме возраста оценка = 0. */
    @Test fun `age with zero max returns 0`() = assertEquals(0.0, StructuralScores.age(100, 0))

    // ── ER_bg (E.4.1, Bonsón & Ratkai) ──

    /**
     * Формула фонового engagement rate ER_bg = (1/|B|)·Σ(L+C+R)/F (Е.4.1, Bonsón & Ratkai):
     * средняя по постам сумма реакций, делённая на число подписчиков.
     * Arrange: 3 поста с реакциями 10/20/30, 100 подписчиков → (60/3)/100 = 0.2.
     */
    @Test fun `er_bg formula`() {
        // 3 posts with reactions [10, 20, 30], followers = 100
        // ER = (1/3) * (10 + 20 + 30) / 100 = 0.2
        val er = StructuralScores.erBg(listOf(10, 20, 30), 100)
        assertEquals(0.2, er, 0.001)
    }

    /** Защита от деления на ноль: при нуле подписчиков ER_bg = 0. */
    @Test fun `er_bg with zero followers returns 0`() =
        assertEquals(0.0, StructuralScores.erBg(listOf(10, 20), 0))

    /** Нет постов — нечего усреднять: ER_bg = 0. */
    @Test fun `er_bg with empty posts returns 0`() =
        assertEquals(0.0, StructuralScores.erBg(emptyList(), 1000))

    // ── TopVol_a = |T_a| (E.4.2) ──

    /** TopVol_a = |T_a| — просто число тематических постов автора (Е.4.2), здесь тождественно 7. */
    @Test fun `topVol is identity`() = assertEquals(7, TopicScores.topVol(7))

    // ── TopFocus_a (E.4.2) ──

    /**
     * Формула фокуса на теме TopFocus_a = |T|/(|T|+|B^period|) (Е.4.2): доля тематических
     * постов среди всех постов за период. Arrange: 5 тематических + 15 фоновых = 5/20 = 0.25.
     */
    @Test fun `topFocus formula`() {
        // 5 topic + 15 non-topic = 5/20 = 0.25
        assertEquals(0.25, TopicScores.topFocus(5, 15), 0.001)
    }

    /** Если все посты тематические (фоновых 0), фокус = 1.0. */
    @Test fun `topFocus all topic is 1`() = assertEquals(1.0, TopicScores.topFocus(10, 0), 0.001)

    /** Защита от деления на ноль: при отсутствии постов фокус = 0. */
    @Test fun `topFocus no posts returns 0`() = assertEquals(0.0, TopicScores.topFocus(0, 0))

    // ── Reach_a (E.4.2) ──

    /** Reach_a = Σ V_i — суммарный охват (сумма просмотров тематических постов, Е.4.2): 100+200+300=600. */
    @Test fun `reach is sum of views`() {
        assertEquals(600.0, TopicScores.reach(listOf(100L, 200L, 300L)), 0.001)
    }

    // ── Pos_a (E.4.3) ──

    /**
     * Инвариант распределения позиции автора Pos_a=(p+,p0,p−) (Е.4.3): доли тональности
     * нормированы и в сумме дают 1.0 (это вероятностное распределение).
     */
    @Test fun `pos distribution sums to 1`() {
        val dist = PositionScore.pos(listOf("POSITIVE", "NEGATIVE", "NEUTRAL", "NEUTRAL"))
        assertEquals(1.0, dist.positive + dist.neutral + dist.negative, 0.001)
    }

    /**
     * Корректность долей Pos_a: на 4 постах (2 POSITIVE, 1 NEUTRAL, 1 NEGATIVE)
     * доли равны 0.5 / 0.25 / 0.25 соответственно.
     */
    @Test fun `pos correct proportions`() {
        val dist = PositionScore.pos(listOf("POSITIVE", "POSITIVE", "NEUTRAL", "NEGATIVE"))
        assertEquals(0.5, dist.positive, 0.001) // 2/4
        assertEquals(0.25, dist.neutral, 0.001) // 1/4
        assertEquals(0.25, dist.negative, 0.001) // 1/4
    }

    /** Граничный случай: при отсутствии постов позиция полностью нейтральна (p0=1.0). */
    @Test fun `pos empty is neutral`() {
        val dist = PositionScore.pos(emptyList())
        assertEquals(0.0, dist.positive, 0.001)
        assertEquals(1.0, dist.neutral, 0.001)
    }

    // ── ER_top (E.4.4) ──

    /**
     * ER_top использует ту же формулу engagement rate, что и ER_bg, но по тематическим
     * постам (Е.4.4): (60/3)/100 = 0.2. Проверяет идентичность расчёта.
     */
    @Test fun `er_top same formula as er_bg`() {
        val er = ResponseScores.erTop(listOf(10, 20, 30), 100)
        assertEquals(0.2, er, 0.001)
    }

    // ── Resp_a (E.4.4) ──

    /**
     * Инвариант распределения отклика аудитории Resp_a=(q+,q0,q−) (Е.4.4): доли тональности
     * комментариев в сумме дают 1.0.
     */
    @Test fun `resp distribution sums to 1`() {
        val dist = ResponseScores.resp(listOf("POSITIVE", "NEGATIVE", "NEUTRAL"))
        assertEquals(1.0, dist.positive + dist.neutral + dist.negative, 0.001)
    }

    /**
     * Корректность долей Resp_a: на 100 комментариях (60/30/10) доли равны 0.6 / 0.3 / 0.1.
     */
    @Test fun `resp correct proportions`() {
        val labels = List(60) { "POSITIVE" } + List(30) { "NEUTRAL" } + List(10) { "NEGATIVE" }
        val dist = ResponseScores.resp(labels)
        assertEquals(0.6, dist.positive, 0.001)
        assertEquals(0.3, dist.neutral, 0.001)
        assertEquals(0.1, dist.negative, 0.001)
    }
}
