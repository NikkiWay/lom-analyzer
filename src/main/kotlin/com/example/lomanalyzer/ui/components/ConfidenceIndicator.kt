/*
 * НАЗНАЧЕНИЕ
 * Переиспользуемый UI-компонент Compose Desktop — компактный бейдж уровня
 * доверия (confidence). Показывает числовое значение доверия к оценке и его
 * качественную категорию (Высокая/Средняя/Низкая) цветной точкой и подписью.
 *
 * ЧТО ВНУТРИ
 * Единственная @Composable-функция ConfidenceIndicator: Surface-капсула с
 * цветным кружком-индикатором, числом доверия и русской меткой уровня.
 *
 * МЕТОД
 * Категория и цвет выбираются по порогам доверия: ≥0.60 — «Высокая» (success),
 * ≥0.25 — «Средняя» (warning), иначе — «Низкая» (error).
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop; Surface/Row — компоновка капсулы. Палитра — AppColors.
 */
package com.example.lomanalyzer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lomanalyzer.ui.theme.AppColors

/**
 * Бейдж уровня доверия к оценке.
 *
 * @param confidence значение доверия [0..1]; определяет цвет, метку и число.
 * @param modifier внешний Modifier.
 */
@Composable
@Suppress("FunctionNaming")
fun ConfidenceIndicator(confidence: Float, modifier: Modifier = Modifier) {
    // По порогам доверия выбираем тройку: цвет точки/текста, цвет фона капсулы, русская метка
    val (color, bgColor, label) = when {
        confidence >= 0.60f -> Triple(AppColors.success, AppColors.successLight, "Высокая")
        confidence >= 0.25f -> Triple(AppColors.warning, AppColors.warningLight, "Средняя")
        else -> Triple(AppColors.error, AppColors.errorLight, "Низкая")
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Цветной кружок-индикатор уровня (8.dp)
            Surface(shape = CircleShape, color = color, modifier = Modifier.size(8.dp)) {}
            // Числовое значение доверия с точностью 2 знака
            Text(
                "%.2f".format(confidence),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
            // Качественная метка уровня (Высокая/Средняя/Низкая)
            Text(
                label,
                fontSize = 11.sp,
                color = color.copy(alpha = 0.8f),
            )
        }
    }
}
