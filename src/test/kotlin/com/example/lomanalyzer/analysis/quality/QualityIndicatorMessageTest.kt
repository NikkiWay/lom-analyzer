/*
 * НАЗНАЧЕНИЕ
 * Тесты формата сообщения индикатора качества. Значение проходит круг
 * «число → строка события → число», и на русской локали этот круг терял его:
 * запись давала «1,000», чтение ждало точку и молча подставляло 0.
 *
 * ЧТО ВНУТРИ
 * Класс QualityIndicatorMessageTest: независимость записи от локали ОС,
 * круговая сохранность значения, чтение записей со старым разделителем,
 * отказ на строках, не являющихся индикатором.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5; java.util.Locale — подмена локали по умолчанию на время проверки.
 *
 * СВЯЗИ
 * QualityIndicatorMessage (analysis/quality); пишет QualityCheckExecutor,
 * читает SessionQualityScreen.
 */
package com.example.lomanalyzer.analysis.quality

import com.example.lomanalyzer.core.QualityStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class QualityIndicatorMessageTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterEach
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    private fun indicator(value: Float, name: String = "Полнота сбора данных") = QualityIndicator(
        name = name,
        value = value,
        status = QualityStatus.PASSED,
        isPrimary = true,
        description = "описание",
    )

    /**
     * Ключевой тест: на русской локали разделитель дробной части — запятая, и
     * форматирование по умолчанию записывало «1,000». Именно из-за этого экран
     * качества показывал нули при верных статусах и цветах.
     */
    @Test
    fun `format does not depend on the OS locale`() {
        Locale.setDefault(Locale.forLanguageTag("ru-RU"))

        val message = QualityIndicatorMessage.format(indicator(1.0f))

        assertTrue(message.contains("1.000"), "разделитель обязан быть точкой, получено: $message")
        assertEquals("Полнота сбора данных: PASSED (1.000)", message)
    }

    /** Значение переживает круг «число → строка → число» на любой локали. */
    @Test
    fun `value survives a round trip under a comma locale`() {
        Locale.setDefault(Locale.forLanguageTag("ru-RU"))

        val parsed = QualityIndicatorMessage.parse(QualityIndicatorMessage.format(indicator(0.756f)))

        assertEquals(0.756f, parsed!!.value, 1e-6f)
        assertEquals(QualityStatus.PASSED, parsed.status)
        assertEquals("Полнота сбора данных", parsed.name)
    }

    /**
     * Записи, сделанные до перехода на Locale.ROOT, хранят значение с запятой.
     * Разбор обязан их понимать, иначе уже проведённые сессии так и остались бы
     * с нулями.
     */
    @Test
    fun `parse reads legacy comma separated values`() {
        val parsed = QualityIndicatorMessage.parse("Покрытие отклика комментариями: PASSED (0,756)")

        assertEquals(0.756f, parsed!!.value, 1e-6f)
        assertEquals("Покрытие отклика комментариями", parsed.name)
    }

    /** Значение вне диапазона [0..1] (например, ширина CI) не теряется. */
    @Test
    fun `parse keeps values above one`() {
        val parsed = QualityIndicatorMessage.parse("Средняя ширина CI по сессии: FAILED (237,117)")

        assertEquals(237.117f, parsed!!.value, 1e-3f)
        assertEquals(QualityStatus.FAILED, parsed.status)
    }

    /** Все статусы разбираются, а не только PASSED. */
    @Test
    fun `parse recognises every status`() {
        for (status in QualityStatus.entries) {
            val parsed = QualityIndicatorMessage.parse(
                QualityIndicatorMessage.format(indicator(0.5f).copy(status = status)),
            )
            assertEquals(status, parsed!!.status)
        }
    }

    /** Названия с двоеточием внутри не ломают разбор: имя берётся до первого. */
    @Test
    fun `parse handles names without a status as not an indicator`() {
        assertNull(QualityIndicatorMessage.parse("Просто сообщение без структуры"))
        assertNull(QualityIndicatorMessage.parse("Качество сессии: 8/10 пройдено"))
        assertNull(QualityIndicatorMessage.parse("Индикатор: НЕИЗВЕСТНЫЙ (0.500)"))
    }
}
