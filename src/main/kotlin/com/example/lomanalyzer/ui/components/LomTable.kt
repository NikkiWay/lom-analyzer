/*
 * НАЗНАЧЕНИЕ
 * Переиспользуемый UI-компонент Compose Desktop — таблица ЛОМ (дашборд лидеров
 * мнений). Отображает по строкам авторов, по столбцам — 11 количественных оценок
 * 4 осей влияния (Приложение Е.4) плюс роль. Поддерживает сортировку по любому
 * столбцу и цветовую индикацию тональности позиции/отклика.
 *
 * ЧТО ВНУТРИ
 * @Composable LomTable — заголовок с сортировкой + прокручиваемые строки.
 * Приватные @Composable: CellW (текстовая ячейка), CellFW (числовая Float-ячейка),
 * CellSentiment (ячейка тональности с цветным кружком), CellRole (ячейка роли).
 * Функция lerpColor — линейная интерполяция цвета.
 *
 * МЕТОД
 * Сортировка: Comparator по выбранному столбцу (sortColumn) и направлению
 * (sortAsc), результат кэшируется через remember по (rows, sortColumn, sortAsc).
 * Для столбцов позиции/отклика сортировка по тональности (positive − negative);
 * для роли — по фиксированному порядку ролей. Цвет тональности: градиент
 * зелёный→серый→красный, интенсивность = sqrt|score| (усиливает малые отклонения,
 * как в ScatterPlot).
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop; LazyColumn — виртуализированный список строк; horizontalScroll
 * — горизонтальная прокрутка широкой таблицы; remember/mutableStateOf — состояние
 * сортировки. Палитра — AppColors.
 *
 * СВЯЗИ
 * Принимает List<LomTableRow>; роль рисуется через RoleCombinationBadge.
 */
package com.example.lomanalyzer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lomanalyzer.ui.theme.AppColors
import kotlin.math.abs

/**
 * Таблица ЛОМ с сортируемыми столбцами.
 *
 * @param rows строки таблицы (по одной на автора) с 11 оценками и ролью.
 * @param onRowClick колбэк клика по строке; передаёт authorId выбранного автора.
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
fun LomTable(
    rows: List<LomTableRow>,
    onRowClick: (Int) -> Unit = {},
) {
    // Состояние сортировки: ключ столбца и направление (по умолчанию — Aud, по убыванию)
    var sortColumn by remember { mutableStateOf("aud") }
    var sortAsc by remember { mutableStateOf(false) }

    // Пересортировываем только при изменении данных или параметров сортировки (кэш через remember)
    val sorted = remember(rows, sortColumn, sortAsc) {
        // Фиксированный порядок ролей для сортировки по столбцу «Роль»
        val roleOrder = mapOf(
            "AUTHORITATIVE" to 0, "ACTIVIST" to 1, "SLEEPING" to 2, "BACKGROUND_AUTHOR" to 3,
        )
        // Выбираем компаратор по активному столбцу
        val comparator: Comparator<LomTableRow> = when (sortColumn) {
            "authorName" -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.authorName }
            "aud" -> compareBy { it.aud }
            "age" -> compareBy { it.age }
            "erBg" -> compareBy { it.erBg }
            "topVol" -> compareBy { it.topVol }
            "topFocus" -> compareBy { it.topFocus }
            "reach" -> compareBy { it.reach }
            // Позиция/отклик сортируются по тональности: доля позитива минус доля негатива
            "pos" -> compareBy { (it.posPositive ?: 0f) - (it.posNegative ?: 0f) }
            "erTop" -> compareBy { it.erTop }
            "resp" -> compareBy { (it.respPositive ?: 0f) - (it.respNegative ?: 0f) }
            // Роль: по фиксированному порядку, при равенстве — по имени автора
            "role" -> compareBy<LomTableRow> { roleOrder[it.role] ?: 99 }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.authorName }
            else -> compareBy { it.aud }
        }
        // Применяем направление сортировки (asc/desc)
        if (sortAsc) rows.sortedWith(comparator) else rows.sortedWith(comparator.reversed())
    }

    // Заголовки столбцов: пара (подпись, ключ сортировки). Ключи совпадают с веткой when выше
    val headers = listOf(
        "Автор" to "authorName",
        "Аудитория (Aud)" to "aud",
        "Стаж (Age)" to "age",
        "Вовлеч. фон (ER_bg)" to "erBg",
        "Тем. посты (TopVol)" to "topVol",
        "Тем. фокус (TopFocus)" to "topFocus",
        "Охват (Reach)" to "reach",
        "Позиция (Pos)" to "pos",
        "Вовлеч. тема (ER_top)" to "erTop",
        "Отклик (Resp)" to "resp",
        "Роль" to "role",
    )

    // Корневая колонка с горизонтальной прокруткой (таблица шире экрана)
    Column(
        modifier = Modifier.fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState()),
    ) {
        // Строка заголовков с кликабельной сортировкой
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.surfaceVariant)
                .padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            for ((label, key) in headers) {
                // Столбец автора чуть шире остальных
                val colWidth = if (key == "authorName") 120.dp else 110.dp
                Row(
                    // Клик по заголовку: тот же столбец — переключаем направление, иначе выбираем столбец (desc)
                    modifier = Modifier.width(colWidth).clickable {
                        if (sortColumn == key) sortAsc = !sortAsc
                        else { sortColumn = key; sortAsc = false }
                    }.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        label,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = if (sortColumn == key) AppColors.primary else AppColors.textSecondary,
                        maxLines = 2,
                        lineHeight = 14.sp,
                    )
                    // У активного столбца показываем стрелку направления сортировки
                    if (sortColumn == key) {
                        Icon(
                            if (sortAsc) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            null,
                            modifier = Modifier.size(12.dp),
                            tint = AppColors.primary,
                        )
                    }
                }
            }
        }

        Divider(color = AppColors.divider)

        // Виртуализированный список строк таблицы
        LazyColumn {
            itemsIndexed(sorted) { index, row ->
                // Чередование фона строк (zebra) для читаемости
                val bgColor = if (index % 2 == 0) Color.Transparent else AppColors.surfaceVariant.copy(alpha = 0.5f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                        .clickable { onRowClick(row.authorId) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    // Ячейки строки в том же порядке, что и заголовки
                    CellW(row.authorName, 120.dp)
                    CellFW(row.aud)
                    CellFW(row.age)
                    CellFW(row.erBg)
                    CellW(row.topVol?.toString() ?: "—")
                    CellFW(row.topFocus)
                    CellFW(row.reach)
                    CellSentiment(row.posPositive, row.posNeutral, row.posNegative)
                    CellFW(row.erTop)
                    CellSentiment(row.respPositive, row.respNeutral, row.respNegative)
                    CellRole(row.role)
                }
            }
        }
    }
}

/** Стандартная ширина столбца таблицы. */
private val COL_W = 110.dp

/**
 * Текстовая ячейка таблицы.
 * @param text отображаемый текст.
 * @param width ширина ячейки (по умолчанию COL_W).
 */
@Composable
@Suppress("FunctionNaming")
private fun CellW(text: String, width: androidx.compose.ui.unit.Dp = COL_W) {
    Text(
        text,
        fontSize = 12.sp,
        color = AppColors.textPrimary,
        modifier = Modifier.width(width).padding(horizontal = 4.dp),
        maxLines = 1,
    )
}

/**
 * Числовая ячейка для Float-оценки: 3 знака после запятой или «—» при null.
 * @param value значение оценки или null, если оценка недоступна.
 */
@Composable
@Suppress("FunctionNaming")
private fun CellFW(value: Float?) {
    Text(
        value?.let { "%.3f".format(it) } ?: "—",
        fontSize = 12.sp,
        color = if (value != null) AppColors.textPrimary else AppColors.textTertiary,
        modifier = Modifier.width(COL_W).padding(horizontal = 4.dp),
    )
}

/**
 * Ячейка тональности: цветной кружок + подпись «поз / нейтр / нег» (в процентах).
 * Зелёный = позитив, серый = нейтрально, красный = негатив.
 * Интенсивность цвета = sqrt|score| — усиливает малые отклонения (как в ScatterPlot).
 *
 * @param pos доля позитива [0..1] или null.
 * @param neu доля нейтрального [0..1] или null.
 * @param neg доля негатива [0..1] или null.
 */
@Composable
@Suppress("FunctionNaming")
private fun CellSentiment(pos: Float?, neu: Float?, neg: Float?) {
    // Нет ни одной доли — показываем прочерк
    if (pos == null && neu == null && neg == null) {
        Text("—", fontSize = 12.sp, color = AppColors.textTertiary, modifier = Modifier.width(COL_W).padding(horizontal = 4.dp))
        return
    }
    // Тональный счёт: доля позитива минус доля негатива, зажат в [-1, 1]
    val p = pos ?: 0f; val n = neg ?: 0f
    val score = (p - n).coerceIn(-1f, 1f)
    // sqrt усиливает малые отклонения от нейтрали
    val intensity = kotlin.math.sqrt(abs(score))
    val green = Color(0xFF2E7D32)
    val gray = Color(0xFF9E9E9E)
    val red = Color(0xFFC62828)
    // Положительный счёт — к зелёному, отрицательный — к красному (от нейтрального серого)
    val color = if (score >= 0) lerpColor(gray, green, intensity) else lerpColor(gray, red, intensity)

    val u = neu ?: 0f
    // Подпись со всеми тремя долями в процентах: позитив / нейтрально / негатив
    val label = "%.0f / %.0f / %.0f".format(p * 100, u * 100, n * 100)

    Row(
        modifier = Modifier.width(COL_W).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Цветной кружок тональности
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, fontSize = 11.sp, color = AppColors.textPrimary)
    }
}

/**
 * Линейная интерполяция между двумя цветами по компонентам RGB.
 * @param fraction доля перехода [0..1]: 0 — from, 1 — to.
 */
private fun lerpColor(from: Color, to: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * f,
        green = from.green + (to.green - from.green) * f,
        blue = from.blue + (to.blue - from.blue) * f,
        alpha = 1f,
    )
}

/**
 * Ячейка роли: цветной бейдж роли или прочерк, если роль не назначена.
 * @param role имя роли (enum) или null.
 */
@Composable
@Suppress("FunctionNaming")
private fun CellRole(role: String?) {
    if (role != null) {
        // Делегируем отрисовку бейджа переиспользуемому компоненту
        Box(modifier = Modifier.width(COL_W).padding(horizontal = 4.dp)) {
            RoleCombinationBadge(role)
        }
    } else {
        Text("—", fontSize = 12.sp, color = AppColors.textTertiary, modifier = Modifier.width(COL_W).padding(horizontal = 4.dp))
    }
}
