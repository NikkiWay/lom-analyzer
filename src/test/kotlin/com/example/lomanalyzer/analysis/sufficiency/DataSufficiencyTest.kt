/*
 * НАЗНАЧЕНИЕ
 * Юнит-тесты этапа 10 (контроль качества): индикатор достаточности данных,
 * который классифицирует надёжность оценок автора как RELIABLE / PRELIMINARY /
 * UNRELIABLE.
 *
 * ЧТО ВНУТРИ
 * Класс DataSufficiencyTest: проверяет DataSufficiencyIndicator.evaluate по трём
 * входным критериям — число тематических постов, число комментариев и средняя
 * ширина доверительного интервала (avgCiWidth). Также проверяется работа
 * пользовательских порогов SufficiencyThresholds.
 *
 * МЕТОД
 * Решающее правило: RELIABLE — все критерии достаточны; UNRELIABLE — хотя бы один
 * ниже минимума или CI слишком широк; PRELIMINARY — промежуточные значения.
 * Отсутствие CI (null, нет данных бутстрапа) не понижает оценку.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (@Test, статические assert-методы).
 *
 * СВЯЗИ
 * Тестируемые типы: DataSufficiencyIndicator и SufficiencyThresholds (пакет
 * analysis/sufficiency), перечисление DataSufficiency (пакет core).
 */
package com.example.lomanalyzer.analysis.sufficiency

import com.example.lomanalyzer.core.DataSufficiency
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Тесты индикатора достаточности данных (этап 10): пороги по постам, комментариям и ширине CI.
 */
class DataSufficiencyTest {

    /**
     * RELIABLE: все три критерия достаточны (15 постов, 100 комментариев, узкий CI 0.10).
     * Базовый «зелёный» случай надёжной оценки.
     */
    @Test
    fun `reliable when all conditions met`() {
        val result = DataSufficiencyIndicator.evaluate(
            topicPostCount = 15, commentCount = 100, avgCiWidth = 0.10,
        )
        assertEquals(DataSufficiency.RELIABLE, result)
    }

    /**
     * UNRELIABLE из-за нехватки тематических постов (2 — ниже минимума), несмотря
     * на достаточные комментарии и узкий CI. Любой критерий ниже минимума делает оценку ненадёжной.
     */
    @Test
    fun `unreliable when topic posts below minimum`() {
        assertEquals(DataSufficiency.UNRELIABLE,
            DataSufficiencyIndicator.evaluate(topicPostCount = 2, commentCount = 100, avgCiWidth = 0.10))
    }

    /** UNRELIABLE из-за нехватки комментариев (5 — ниже минимума) при достаточных постах и CI. */
    @Test
    fun `unreliable when comments below minimum`() {
        assertEquals(DataSufficiency.UNRELIABLE,
            DataSufficiencyIndicator.evaluate(topicPostCount = 15, commentCount = 5, avgCiWidth = 0.10))
    }

    /** UNRELIABLE из-за слишком широкого CI (0.60): высокая неопределённость оценки. */
    @Test
    fun `unreliable when CI width too wide`() {
        assertEquals(DataSufficiency.UNRELIABLE,
            DataSufficiencyIndicator.evaluate(topicPostCount = 15, commentCount = 100, avgCiWidth = 0.60))
    }

    /**
     * PRELIMINARY: число постов в промежуточной зоне (5 — не меньше 3, но меньше 10),
     * при достаточных комментариях и узком CI. Оценка предварительная.
     */
    @Test
    fun `preliminary when between thresholds`() {
        // 5 posts (>= 3 but < 10), enough comments and CI
        assertEquals(DataSufficiency.PRELIMINARY,
            DataSufficiencyIndicator.evaluate(topicPostCount = 5, commentCount = 100, avgCiWidth = 0.10))
    }

    /** PRELIMINARY: посты и комментарии достаточны, но CI умеренной ширины (0.30) — пограничная неопределённость. */
    @Test
    fun `preliminary when CI is moderate`() {
        assertEquals(DataSufficiency.PRELIMINARY,
            DataSufficiencyIndicator.evaluate(topicPostCount = 15, commentCount = 100, avgCiWidth = 0.30))
    }

    /**
     * Отсутствие CI (null — бутстрап не выполнялся) не понижает оценку: при достаточных
     * постах и комментариях результат остаётся RELIABLE.
     */
    @Test
    fun `reliable when CI is null (no bootstrap data)`() {
        assertEquals(DataSufficiency.RELIABLE,
            DataSufficiencyIndicator.evaluate(topicPostCount = 15, commentCount = 100, avgCiWidth = null))
    }

    /**
     * Пользовательские пороги: с более строгими требованиями (минимум 20 постов и 100
     * комментариев для RELIABLE) входные 15 постов и 80 комментариев уже недотягивают
     * до надёжного уровня → PRELIMINARY.
     */
    @Test
    fun `custom thresholds work`() {
        val strict = SufficiencyThresholds(minTopicPostsReliable = 20, minCommentsReliable = 100)
        assertEquals(DataSufficiency.PRELIMINARY,
            DataSufficiencyIndicator.evaluate(topicPostCount = 15, commentCount = 80, avgCiWidth = 0.10, thresholds = strict))
    }
}
