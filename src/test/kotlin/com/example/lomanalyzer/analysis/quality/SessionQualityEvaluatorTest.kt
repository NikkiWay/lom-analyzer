/*
 * НАЗНАЧЕНИЕ
 * Тесты индикаторов качества сессии, отвечающих за молчаливую деградацию:
 * долю неизмеренной тональности и доступность семантического прохода фильтра.
 * В обоих случаях сессия завершается успешно, и статус COMPLETED не отличает
 * такой прогон от полноценного.
 *
 * ЧТО ВНУТРИ
 * Класс SessionQualityEvaluatorTest: пороги доли подставленных нейтралей,
 * двоичный индикатор прохода 2, их влияние на общий статус сессии.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5.
 *
 * СВЯЗИ
 * SessionQualityEvaluator, QualityInput (analysis/quality), core.QualityStatus.
 */
package com.example.lomanalyzer.analysis.quality

import com.example.lomanalyzer.core.QualityStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionQualityEvaluatorTest {

    private val evaluator = SessionQualityEvaluator()

    /** Вход, у которого все прочие индикаторы заведомо проходят. */
    private fun healthyInput(
        sentimentFallbackRatio: Float = 0f,
        semanticPassAvailable: Boolean = true,
    ) = QualityInput(
        collectionCompleteness = 1.0f,
        topicFilteringQuality = 1.0f,
        commentCoverage = 1.0f,
        reliableRatio = 1.0f,
        unreliableRatio = 0.0f,
        sentimentFallbackRatio = sentimentFallbackRatio,
        semanticPassAvailable = semanticPassAvailable,
    )

    private fun indicator(input: QualityInput, name: String) =
        evaluator.evaluate(input).indicators.firstOrNull { it.name == name }

    /** Измеренная моделью тональность — индикатор пройден. */
    @Test
    fun `no fabricated neutrals passes`() {
        val found = indicator(healthyInput(sentimentFallbackRatio = 0f), "Доля неизмеренной тональности")

        assertNotNull(found)
        assertEquals(QualityStatus.PASSED, found!!.status)
    }

    /**
     * Порог провала — 10%: доля подставленных нейтралей выше означает, что оси
     * позиции и отклика построены преимущественно на неизмеренных значениях.
     */
    @Test
    fun `high share of fabricated neutrals fails`() {
        val borderline = indicator(healthyInput(sentimentFallbackRatio = 0.08f), "Доля неизмеренной тональности")
        val failed = indicator(healthyInput(sentimentFallbackRatio = 0.81f), "Доля неизмеренной тональности")

        assertEquals(QualityStatus.BORDERLINE, borderline!!.status)
        assertEquals(QualityStatus.FAILED, failed!!.status, "доля 81% обязана проваливать сессию")
    }

    /** Работающий проход 2 — индикатор пройден. */
    @Test
    fun `available semantic pass passes`() {
        val found = indicator(healthyInput(semanticPassAvailable = true), "Семантический проход фильтра")

        assertEquals(QualityStatus.PASSED, found!!.status)
        assertEquals(1f, found.value)
    }

    /**
     * Отключённый проход 2 проваливает индикатор: решение по пограничным постам
     * принимается по одним ключевым словам, и состав тематической выборки — а с ним
     * и все оценки — получен иначе.
     */
    @Test
    fun `disabled semantic pass fails`() {
        val found = indicator(healthyInput(semanticPassAvailable = false), "Семантический проход фильтра")

        assertEquals(QualityStatus.FAILED, found!!.status)
        assertEquals(0f, found.value)
    }

    /**
     * Ключевое свойство: сессия с отключённым проходом 2 не может числиться
     * качественной, хотя сам этап отрабатывает без ошибки.
     */
    @Test
    fun `degraded session cannot be reported as healthy`() {
        val healthy = evaluator.evaluate(healthyInput())
        val degraded = evaluator.evaluate(healthyInput(semanticPassAvailable = false))

        assertEquals(QualityStatus.PASSED, healthy.overallStatus)
        assertEquals(QualityStatus.FAILED, degraded.overallStatus)
        assertTrue(!degraded.allPrimaryPassed)
    }

    /** Оба индикатора — основные: они влияют на общий статус, а не только на отчёт. */
    @Test
    fun `degradation indicators are primary`() {
        val input = healthyInput()

        assertTrue(indicator(input, "Доля неизмеренной тональности")!!.isPrimary)
        assertTrue(indicator(input, "Семантический проход фильтра")!!.isPrimary)
    }

    /** По умолчанию деградации нет: вход без явных значений описывает здоровый прогон. */
    @Test
    fun `defaults assume no degradation`() {
        val defaults = QualityInput()

        assertEquals(0f, defaults.sentimentFallbackRatio)
        assertTrue(defaults.semanticPassAvailable)
    }
}
