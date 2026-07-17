/*
 * НАЗНАЧЕНИЕ
 * Формат сообщения индикатора качества и его разбор. Индикаторы сохраняются как
 * события сессии (тип QUALITY_INDICATOR) одной строкой, а экран качества читает
 * их обратно — то есть число проходит круг «значение → текст → значение».
 *
 * ЧТО ВНУТРИ
 * object QualityIndicatorMessage: format (значение → строка), parse (строка →
 * значение); data class ParsedIndicator — результат разбора.
 *
 * МЕТОД
 * Формат: «Название: СТАТУС (0.123)». Число пишется и читается в Locale.ROOT.
 * Запись и чтение живут рядом намеренно: раньше запись форматировала число по
 * локали ОС («1,000»), а чтение ждало точку и молча получало 0 — экран показывал
 * нули при верных статусах и цветах. Пока две стороны формата лежали в разных
 * модулях, ничто и не могло этого поймать.
 *
 * СВЯЗИ
 * Пишет QualityCheckExecutor (этап 10), читает SessionQualityScreen.
 */
package com.example.lomanalyzer.analysis.quality

import com.example.lomanalyzer.core.QualityStatus
import java.util.Locale

/**
 * Разобранный индикатор.
 *
 * @param name название индикатора.
 * @param status статус относительно порогов.
 * @param value числовое значение.
 */
data class ParsedIndicator(
    val name: String,
    val status: QualityStatus,
    val value: Float,
)

/** Формат строки индикатора качества: «Название: СТАТУС (0.123)». */
object QualityIndicatorMessage {

    /** Сколько знаков после запятой сохраняется в сообщении. */
    private const val VALUE_DECIMALS = 3

    /**
     * Собирает сообщение индикатора.
     *
     * Locale.ROOT обязателен: разделитель дробной части по умолчанию берётся из
     * локали ОС, и на русской системе значение записывалось бы как «1,000».
     */
    fun format(indicator: QualityIndicator): String =
        "%s: %s (%.${VALUE_DECIMALS}f)".format(
            Locale.ROOT, indicator.name, indicator.status.name, indicator.value,
        )

    /**
     * Разбирает сообщение обратно; null — строка не является индикатором.
     *
     * Запятая принимается наравне с точкой: сессии, записанные до перехода на
     * Locale.ROOT, сохранили значение в формате локали ОС, и без этого их
     * индикаторы так и показывались бы нулями.
     */
    @Suppress("ReturnCount")
    fun parse(message: String): ParsedIndicator? {
        val colonIdx = message.indexOf(':')
        if (colonIdx < 0) return null
        val name = message.substring(0, colonIdx).trim()
        val rest = message.substring(colonIdx + 1).trim()

        val statusName = rest.substringBefore(" (").trim()
        val status = QualityStatus.entries.firstOrNull { it.name == statusName } ?: return null

        // Значение — в скобках; отсутствие скобок означает, что это не индикатор
        if (!rest.contains('(') || !rest.contains(')')) return null
        val value = rest.substringAfter('(').substringBefore(')')
            .replace(',', '.')
            .toFloatOrNull() ?: return null

        return ParsedIndicator(name = name, status = status, value = value)
    }
}
