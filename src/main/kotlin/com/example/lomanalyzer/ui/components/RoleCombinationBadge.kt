/*
 * НАЗНАЧЕНИЕ
 * Переиспользуемый UI-компонент Compose Desktop — цветной бейдж роли автора.
 * Преобразует машинное имя роли (enum) в русскую подпись и уникальную цветовую
 * пару (фон/текст). Роли — 4 базовых квадранта классификации ЛОМ (диплом 2.1.6).
 *
 * ЧТО ВНУТРИ
 * Приватная функция roleStyle — маппинг имени роли в (подпись, цвет фона,
 * цвет текста). @Composable RoleCombinationBadge — собственно бейдж-капсула.
 *
 * МЕТОД
 * Сопоставление по подстроке имени роли (без учёта регистра): AUTHORITATIVE —
 * «Авторитетный лидер», SLEEPING/GIANT — «Спящий гигант», TOPIC/ACTIVIST —
 * «Тематический активист», BACKGROUND — «Фоновый участник»; иначе — серый
 * fallback с исходной строкой.
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop; Surface — капсула с фоном. Цвета — литеральные ARGB
 * (согласованы с квадрантами ScatterPlot и RoleCombinationBadge).
 */
package com.example.lomanalyzer.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colored badge displaying the assigned role.
 * Simplified version for 4 base roles (diploma 2.1.6).
 */
/**
 * Сопоставляет имя роли (enum) с русской подписью и парой цветов (фон, текст).
 * @return Triple(подпись, цвет фона, цвет текста).
 */
private fun roleStyle(role: String): Triple<String, Color, Color> = when {
    role.contains("AUTHORITATIVE", true) ->
        Triple("Авторитетный лидер", Color(0xFFE8F5E9), Color(0xFF1B5E20))
    role.contains("SLEEPING", true) || role.contains("GIANT", true) ->
        Triple("Спящий гигант", Color(0xFFFFF3E0), Color(0xFFE65100))
    role.contains("TOPIC", true) || role.contains("ACTIVIST", true) ->
        Triple("Тематический активист", Color(0xFFE3F2FD), Color(0xFF0D47A1))
    role.contains("BACKGROUND", true) ->
        Triple("Фоновый участник", Color(0xFFF3E5F5), Color(0xFF6A1B9A))
    else ->
        Triple(role, Color(0xFFF5F5F5), Color(0xFF9E9E9E))
}

/**
 * Цветной бейдж назначенной роли автора.
 *
 * @param role машинное имя роли (enum); определяет подпись и цвета.
 * @param modifier внешний Modifier.
 */
@Composable
@Suppress("FunctionNaming")
fun RoleCombinationBadge(role: String, modifier: Modifier = Modifier) {
    // Разворачиваем стиль роли в подпись, цвет фона и цвет текста
    val (label, bg, fg) = roleStyle(role)

    Surface(shape = RoundedCornerShape(6.dp), color = bg, modifier = modifier) {
        Text(
            label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
