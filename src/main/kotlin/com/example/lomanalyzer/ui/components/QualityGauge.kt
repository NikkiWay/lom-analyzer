/*
 * НАЗНАЧЕНИЕ
 * Переиспользуемый UI-компонент Compose Desktop — мини-индикатор качества
 * (дуговой gauge). Показывает одну метрику качества/достаточности данных в виде
 * полукруглой шкалы с числом в центре. Используется на экране качества (этап 8
 * пайплайна — оценка достаточности данных, см. docs/algorithm.md).
 *
 * ЧТО ВНУТРИ
 * Единственная @Composable-функция QualityGauge: карточка с дуговым индикатором,
 * числовым значением в процентах, подписью и опциональной отметкой GATE
 * (порог-«ворота», блокирующий вывод при недостаточном качестве).
 *
 * МЕТОД
 * Цвет шкалы выбирается по порогам значения: ≥0.7 — success (зелёный),
 * ≥0.4 — warning (жёлтый), иначе — error (красный). Значение анимируется через
 * animateFloatAsState. Дуга рисуется на Canvas двумя слоями: фон (полная дуга)
 * и активная часть (sweepAngle, пропорциональный значению).
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop — декларативный UI; Canvas + drawArc — низкоуровневая
 * отрисовка дуги; animateFloatAsState/tween — плавная анимация значения.
 * Палитра — AppColors из ui.theme.
 */
package com.example.lomanalyzer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lomanalyzer.ui.theme.AppColors

/**
 * Дуговой индикатор одной метрики качества.
 *
 * @param label подпись метрики под дугой (может занимать до 2 строк).
 * @param value значение метрики в диапазоне [0..1] (доля); рисуется как процент.
 * @param isGate если true — показывается отметка GATE: эта метрика является
 *   «воротами» (gate), способными заблокировать публикацию результата.
 * @param modifier внешний Modifier для размещения карточки.
 */
@Composable
@Suppress("FunctionNaming")
fun QualityGauge(label: String, value: Float, isGate: Boolean = false, modifier: Modifier = Modifier) {
    // Цвет шкалы по порогам: зелёный/жёлтый/красный — визуальный светофор качества
    val color = when {
        value >= 0.7f -> AppColors.success
        value >= 0.4f -> AppColors.warning
        else -> AppColors.error
    }
    // Плавная анимация заполнения дуги от 0 до текущего значения (600 мс)
    val animatedValue by animateFloatAsState(value.coerceIn(0f, 1f), tween(600))

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        elevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Мини-дуга (arc gauge) в центре карточки
            Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(52.dp)) {
                    // Толщина линии дуги со скруглёнными концами
                    val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    val padding = 4.dp.toPx()
                    // Размер и положение дуги с учётом внутреннего отступа
                    val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
                    val topLeft = Offset(padding, padding)

                    // Фоновая дуга: 270° от 135° (нижний разрыв снизу), серый цвет
                    drawArc(
                        color = Color(0xFFE2E8F0),
                        startAngle = 135f, sweepAngle = 270f, useCenter = false,
                        topLeft = topLeft, size = arcSize, style = stroke,
                    )
                    // Активная дуга: длина пропорциональна значению (270° * value)
                    drawArc(
                        color = color,
                        startAngle = 135f, sweepAngle = 270f * animatedValue, useCenter = false,
                        topLeft = topLeft, size = arcSize, style = stroke,
                    )
                }
                // Число в центре дуги — значение в процентах без дробной части
                Text(
                    "%.0f".format(value * 100),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }

            // Подпись метрики под дугой
            Text(
                label,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = AppColors.textSecondary,
                lineHeight = 12.sp,
            )
            // Отметка GATE — метрика-«ворота», блокирующая публикацию при низком качестве
            if (isGate) {
                Text(
                    "GATE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.warning,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}
