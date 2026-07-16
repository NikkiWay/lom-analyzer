/*
 * НАЗНАЧЕНИЕ
 * Тесты мягкого голосования по осям 3 и 4: Pos_a и Resp_a как среднее
 * распределений вероятностей вместо долей жёстких меток.
 *
 * ЧТО ВНУТРИ
 * Класс SoftVotingTest: сохранение перевеса у сдержанных текстов, различение
 * текстов с одинаковой меткой но разной склонностью, единичная сумма компонент,
 * согласованность с расчётом по меткам на уверенных предсказаниях.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5.
 *
 * СВЯЗИ
 * PositionScore.authorPositionFromProbabilities,
 * ResponseScores.audienceResponseFromProbabilities, core.SentimentDistribution.
 */
package com.example.lomanalyzer.analysis.scoring

import com.example.lomanalyzer.core.SentimentDistribution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SoftVotingTest {

    /**
     * Ключевой случай, ради которого метод и менялся: у автора один тематический
     * пост, модель дала neutral 0.80 при positive 0.15.
     *
     * По меткам Pos_a вырождался ровно в (0, 1, 0) — склонность к позитиву
     * исчезала, и точка на диаграмме была чисто серой. Усреднение вероятностей
     * её сохраняет.
     */
    @Test
    fun `keeps the lean of a single restrained post`() {
        val post = SentimentDistribution(positive = 0.15, neutral = 0.80, negative = 0.05)

        val byProbabilities = PositionScore.authorPositionFromProbabilities(listOf(post))
        val byLabels = PositionScore.authorPositionDistribution(listOf("NEUTRAL"))

        assertEquals(0.15, byProbabilities.positive, 1e-9)
        assertEquals(0.05, byProbabilities.negative, 1e-9)
        assertEquals(0.0, byLabels.positive, 1e-9, "по меткам склонность теряется полностью")
        assertTrue(
            byProbabilities.positive - byProbabilities.negative > 0.09,
            "перевес к позитиву должен быть заметен раскраске",
        )
    }

    /**
     * Два текста с одинаковой меткой «neutral», но противоположными склонностями,
     * по меткам неразличимы, а по вероятностям — противоположны.
     */
    @Test
    fun `distinguishes opposite leans behind the same label`() {
        val leansPositive = SentimentDistribution(0.15, 0.80, 0.05)
        val leansNegative = SentimentDistribution(0.05, 0.80, 0.15)

        val positiveLean = PositionScore.authorPositionFromProbabilities(listOf(leansPositive))
        val negativeLean = PositionScore.authorPositionFromProbabilities(listOf(leansNegative))

        assertTrue(positiveLean.positive > positiveLean.negative)
        assertTrue(negativeLean.negative > negativeLean.positive)
        // По меткам оба дали бы один и тот же результат
        assertEquals(
            PositionScore.authorPositionDistribution(listOf("NEUTRAL")),
            PositionScore.authorPositionDistribution(listOf("NEUTRAL")),
        )
    }

    /** Сумма компонент остаётся равной 1 — инвариант распределения. */
    @Test
    fun `components still sum to one`() {
        val posts = listOf(
            SentimentDistribution(0.15, 0.80, 0.05),
            SentimentDistribution(0.60, 0.30, 0.10),
            SentimentDistribution(0.02, 0.10, 0.88),
        )

        val result = PositionScore.authorPositionFromProbabilities(posts)

        assertEquals(1.0, result.positive + result.neutral + result.negative, 1e-9)
    }

    /** Усреднение по нескольким постам — среднее покомпонентно. */
    @Test
    fun `averages component-wise across posts`() {
        val posts = listOf(
            SentimentDistribution(0.20, 0.70, 0.10),
            SentimentDistribution(0.40, 0.50, 0.10),
        )

        val result = PositionScore.authorPositionFromProbabilities(posts)

        assertEquals(0.30, result.positive, 1e-9)
        assertEquals(0.60, result.neutral, 1e-9)
        assertEquals(0.10, result.negative, 1e-9)
    }

    /**
     * На уверенных предсказаниях мягкое голосование сходится к расчёту по меткам:
     * смена метода не переворачивает выводы там, где модель уверена.
     */
    @Test
    fun `agrees with label shares when the model is confident`() {
        val confident = listOf(
            SentimentDistribution(0.99, 0.01, 0.0),
            SentimentDistribution(0.99, 0.01, 0.0),
            SentimentDistribution(0.0, 0.01, 0.99),
        )

        val byProbabilities = PositionScore.authorPositionFromProbabilities(confident)
        val byLabels = PositionScore.authorPositionDistribution(listOf("POSITIVE", "POSITIVE", "NEGATIVE"))

        assertEquals(byLabels.positive, byProbabilities.positive, 0.02)
        assertEquals(byLabels.negative, byProbabilities.negative, 0.02)
    }

    /** Нет постов — позиция не определена, считается нейтральной. */
    @Test
    fun `no posts yields a neutral position`() {
        val result = PositionScore.authorPositionFromProbabilities(emptyList())

        assertEquals(SentimentDistribution(0.0, 1.0, 0.0), result)
    }

    /** Отклик аудитории усредняется так же, как позиция автора. */
    @Test
    fun `audience response averages the same way`() {
        val comments = listOf(
            SentimentDistribution(0.10, 0.85, 0.05),
            SentimentDistribution(0.30, 0.65, 0.05),
        )

        val result = ResponseScores.audienceResponseFromProbabilities(comments)

        assertEquals(0.20, result.positive, 1e-9)
        assertEquals(0.75, result.neutral, 1e-9)
        assertEquals(1.0, result.positive + result.neutral + result.negative, 1e-9)
    }
}
