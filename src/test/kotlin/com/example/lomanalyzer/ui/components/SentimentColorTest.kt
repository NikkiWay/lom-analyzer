/*
 * НАЗНАЧЕНИЕ
 * Тесты общей цветовой шкалы тональности: нейтральная середина, направление
 * градиента, монотонность и раскрытие малых склонностей.
 *
 * ЧТО ВНУТРИ
 * Класс SentimentColorTest: проверки sentimentLeanColor и lerpColor.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5; androidx.compose.ui.graphics.Color — value-класс, для проверок
 * достаточно чистого JVM без запуска Compose.
 *
 * СВЯЗИ
 * ui/components/SentimentColor.kt; используется ScatterPlot и LomTable.
 */
package com.example.lomanalyzer.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class SentimentColorTest {

    /** Насыщенность как расстояние от нейтрального серого — мера «заметности» цвета. */
    private fun saturation(color: Color): Float {
        val gray = SENTIMENT_NEUTRAL
        return abs(color.red - gray.red) + abs(color.green - gray.green) + abs(color.blue - gray.blue)
    }

    /** Равные доли позитива и негатива дают ровно нейтральный серый. */
    @Test
    fun `balanced lean is exactly neutral gray`() {
        assertEquals(SENTIMENT_NEUTRAL, sentimentLeanColor(0.3f, 0.3f))
        assertEquals(SENTIMENT_NEUTRAL, sentimentLeanColor(0f, 0f))
    }

    /** Перевес к позитиву уходит в зелёную половину шкалы, к негативу — в красную. */
    @Test
    fun `lean direction picks the matching pole`() {
        val positive = sentimentLeanColor(0.6f, 0.1f)
        val negative = sentimentLeanColor(0.1f, 0.6f)

        assertTrue(positive.green > positive.red, "перевес к позитиву обязан быть зеленее")
        assertTrue(negative.red > negative.green, "перевес к негативу обязан быть краснее")
    }

    /** Полные полюса совпадают с образцами легенды. */
    @Test
    fun `full lean reaches the legend swatches`() {
        assertEquals(SENTIMENT_POSITIVE, sentimentLeanColor(1f, 0f))
        assertEquals(SENTIMENT_NEGATIVE, sentimentLeanColor(0f, 1f))
    }

    /**
     * Кривая монотонна: более склонный автор всегда окрашен насыщеннее.
     * Это то, чего лишена линейная шкала с отсечкой — она схлопывает верх диапазона.
     */
    @Test
    fun `saturation grows monotonically with the lean`() {
        val leans = listOf(0.02f, 0.065f, 0.18f, 0.4f, 0.59f, 1.0f)

        val saturations = leans.map { saturation(sentimentLeanColor(it, 0f)) }

        for (i in 1 until saturations.size) {
            assertTrue(
                saturations[i] > saturations[i - 1],
                "склонность ${leans[i]} обязана быть насыщеннее, чем ${leans[i - 1]}",
            )
        }
    }

    /**
     * Ключевое свойство кривой: типичная склонность (медиана 0.065 на реальной
     * сессии) даёт заметный цвет, а не почти-серый. Линейная насыщенность дала бы
     * здесь 6.5% — визуально неотличимо от нейтрали.
     */
    @Test
    fun `typical small lean is visibly tinted`() {
        val median = sentimentLeanColor(0.065f, 0f)

        val relative = saturation(median) / saturation(SENTIMENT_POSITIVE)
        assertTrue(relative > 0.3f, "медианная склонность должна быть заметна, получено $relative")
    }

    /** Доли за пределами шкалы не ломают цвет: счёт зажимается в [-1, 1]. */
    @Test
    fun `out of range shares are clamped`() {
        assertEquals(SENTIMENT_POSITIVE, sentimentLeanColor(2f, 0f))
        assertEquals(SENTIMENT_NEGATIVE, sentimentLeanColor(0f, 2f))
    }

    /** Интерполяция: границы дают исходные цвета, доля зажимается. */
    @Test
    fun `lerp returns endpoints at the bounds`() {
        val from = Color(0xFF000000)
        val to = Color(0xFFFFFFFF)

        assertEquals(from, lerpColor(from, to, 0f))
        assertEquals(to, lerpColor(from, to, 1f))
        assertEquals(from, lerpColor(from, to, -5f), "доля ниже нуля зажимается")
        assertEquals(to, lerpColor(from, to, 5f), "доля выше единицы зажимается")
    }
}
