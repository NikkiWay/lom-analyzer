/*
 * НАЗНАЧЕНИЕ
 * Переиспользуемый UI-компонент Compose Desktop — горизонтальный индикатор
 * точечной оценки с доверительным интервалом (CI bar). Визуализирует результат
 * бутстрапа: точечную оценку метрики и её доверительный интервал [ciLo, ciHi]
 * (см. этап 6 пайплайна — бутстрап, docs/algorithm.md).
 *
 * ЧТО ВНУТРИ
 * Единственная @Composable-функция CiBar: полоса с подсвеченным диапазоном CI и
 * маркером точечной оценки, плюс текстовая подпись «значение [lo, hi]».
 *
 * МЕТОД
 * Значения нормированы в [0..1] и переводятся в ширину/смещение через
 * BoxWithConstraints (totalWidth). Диапазон CI рисуется полупрозрачной заливкой
 * шириной (hi − lo), маркер оценки — узкой полоской по позиции value. Переходы
 * анимируются animateFloatAsState (отдельно для value, lo, hi).
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop; BoxWithConstraints — доступ к доступной ширине для расчёта
 * геометрии; animateFloatAsState/tween — анимация. Палитра — AppColors.
 */
package com.example.lomanalyzer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lomanalyzer.ui.theme.AppColors

/**
 * Полоса «точечная оценка + доверительный интервал».
 *
 * @param value точечная оценка метрики [0..1].
 * @param ciLo нижняя граница доверительного интервала; null — CI отсутствует
 *   (тогда совпадает со значением).
 * @param ciHi верхняя граница доверительного интервала; null — CI отсутствует.
 * @param modifier внешний Modifier.
 * @param barColor цвет маркера и заливки CI (по умолчанию — primary темы).
 * @param showLabel показывать ли текстовую подпись «значение [lo; hi]» под полосой.
 *   Отключают, когда точечная оценка и границы уже подписаны рядом.
 */
@Composable
@Suppress("FunctionNaming")
fun CiBar(
    value: Float,
    ciLo: Float?,
    ciHi: Float?,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colors.primary,
    showLabel: Boolean = true,
) {
    // Границы CI и значение зажимаем в [0..1]; при отсутствии CI берём само значение
    val lo = (ciLo ?: value).coerceIn(0f, 1f)
    val hi = (ciHi ?: value).coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)

    // Анимируем все три величины, чтобы полоса плавно перестраивалась при смене данных
    val animV by animateFloatAsState(v, tween(500))
    val animLo by animateFloatAsState(lo, tween(500))
    val animHi by animateFloatAsState(hi, tween(500))

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // BoxWithConstraints даёт доступную ширину (maxWidth) для расчёта геометрии полосы
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(AppColors.surfaceVariant),
        ) {
            val totalWidth = maxWidth
            // Полупрозрачная заливка диапазона CI: ширина = (hi − lo), смещение = lo
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(totalWidth * (animHi - animLo).coerceAtLeast(0f))
                    .offset(x = totalWidth * animLo)
                    .clip(RoundedCornerShape(7.dp))
                    .background(barColor.copy(alpha = 0.2f)),
            )
            // Маркер точечной оценки: узкая полоска по позиции value (центрируем сдвигом на 2.dp)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .offset(x = totalWidth * animV - 2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor),
            )
        }
        // Текстовая подпись: точечная оценка и границы CI с точностью 3 знака (разделитель — «;»)
        if (showLabel) {
            Text(
                "%.3f [%.3f; %.3f]".format(value, ciLo ?: value, ciHi ?: value),
                fontSize = 10.sp,
                color = AppColors.textTertiary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
