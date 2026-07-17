/*
 * НАЗНАЧЕНИЕ
 * Единая цветовая шкала тональности для UI: перевод склонности автора или
 * аудитории (доля позитива минус доля негатива) в цвет зелёный → серый →
 * красный. Используется точками диаграммы рассеяния и кружками таблицы ЛОМ,
 * чтобы одна и та же склонность выглядела одинаково на обоих экранах.
 *
 * ЧТО ВНУТРИ
 * sentimentLeanColor — цвет по долям позитива и негатива; lerpColor —
 * линейная интерполяция цвета; SATURATION_EXPONENT — показатель кривой.
 *
 * МЕТОД
 * Насыщенность = |score| ^ (1/3), где score = positive − negative ∈ [-1, 1].
 * Показатель подобран по фактическому разбросу склонностей (см. ниже).
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop (androidx.compose.ui.graphics.Color).
 *
 * СВЯЗИ
 * ScatterPlot (точки), LomTable (ячейки позиции и отклика).
 */
package com.example.lomanalyzer.ui.components

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow

/** Цвет полюса поддержки — он же образец «Поддержка» в легенде. */
internal val SENTIMENT_POSITIVE = Color(0xFF2E7D32)

/** Цвет нейтральной середины шкалы — он же образец «Нейтрально» в легенде. */
internal val SENTIMENT_NEUTRAL = Color(0xFF9E9E9E)

/** Цвет полюса критики — он же образец «Критика» в легенде. */
internal val SENTIMENT_NEGATIVE = Color(0xFFC62828)

/**
 * Показатель степени кривой насыщенности (см. [sentimentLeanColor]).
 *
 * Модель тональности сдержанна к спокойной прозе, поэтому склонности лежат почти
 * целиком в нижней десятой части шкалы [0, 1]: на замерах по сессии из 86 авторов
 * |positive − negative| имеет медиану 0.065, p75 = 0.10, p90 = 0.18 и максимум 0.59.
 *
 * Кубический корень растягивает именно этот участок: медианная склонность даёт
 * насыщенность 0.40, заметный цвет получают 58 авторов из 86. Кривая монотонна на
 * всём диапазоне, поэтому верх шкалы сохраняет различимость: склонность 0.59
 * остаётся заметно сильнее 0.15.
 */
private const val SATURATION_EXPONENT = 1.0 / 3.0

/**
 * Цвет по склонности: зелёный (поддержка) → серый (нейтрально) → красный
 * (критика). Кривая монотонна, поэтому порядок сохраняется — более склонный
 * автор всегда окрашен насыщеннее. Нулевая склонность даёт ровно серый.
 *
 * @param positive доля позитива [0..1].
 * @param negative доля негатива [0..1].
 */
internal fun sentimentLeanColor(positive: Float, negative: Float): Color {
    // Тональный счёт, зажатый в [-1, 1]
    val score = (positive - negative).coerceIn(-1f, 1f)
    // Кубический корень: 0.02→0.27, 0.065→0.40, 0.18→0.56, 0.59→0.84
    val intensity = abs(score).toDouble().pow(SATURATION_EXPONENT).toFloat()

    // Положительный счёт — к зелёному, отрицательный — к красному (от нейтрального серого)
    return if (score >= 0) {
        lerpColor(SENTIMENT_NEUTRAL, SENTIMENT_POSITIVE, intensity)
    } else {
        lerpColor(SENTIMENT_NEUTRAL, SENTIMENT_NEGATIVE, intensity)
    }
}

/**
 * Линейная интерполяция между двумя цветами по компонентам RGB.
 *
 * @param from цвет при fraction = 0.
 * @param to цвет при fraction = 1.
 * @param fraction доля перехода, зажимается в [0..1].
 */
internal fun lerpColor(from: Color, to: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * f,
        green = from.green + (to.green - from.green) * f,
        blue = from.blue + (to.blue - from.blue) * f,
        alpha = 1f,
    )
}
