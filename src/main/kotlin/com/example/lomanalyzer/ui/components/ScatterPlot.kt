/*
 * НАЗНАЧЕНИЕ
 * Переиспользуемый UI-компонент Compose Desktop — квадрантный точечный график
 * (scatter plot) для классификации ЛОМ по 4 базовым ролям (диплом 2.1.6,
 * docs/algorithm.md). По осям — два композита (структурное влияние X,
 * тематическая активность Y); пороги (theta_S, theta_T) делят плоскость на 4
 * квадранта-роли. Каждый автор — точка: цвет = его позиция, размер = тематический ER.
 *
 * ЧТО ВНУТРИ
 * @Composable ScatterPlot — легенда + Canvas. Приватные хелперы: positionColor
 * (цвет по тональности позиции), lerpColor (интерполяция цвета), niceMax/niceTicks/
 * fmtTick (красивые границы и засечки осей), DrawScope.drawScatterContent (вся
 * низкоуровневая отрисовка: квадранты, сетка, оси, пороговые линии, подписи, точки).
 *
 * МЕТОД
 * Цвет точки: градиент зелёный→серый→красный по score = posPositive − posNegative,
 * интенсивность = sqrt|score| (усиливает малые отклонения). Размер точки растёт с
 * тематическим ER. Подписи имён расставляются жадно без наложений, приоритет — по
 * сумме композитов. Квадранты: активист, авторитетный лидер, фоновый участник,
 * спящий гигант.
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop; Canvas + DrawScope — ручная отрисовка графика; TextMeasurer/
 * drawText — измерение и вывод текста на Canvas; withTransform/rotate — поворот
 * подписи оси Y; clipRect — обрезка области данных. Палитра — AppColors.
 *
 * СВЯЗИ
 * Принимает List<ScatterPoint> и пороги композитов.
 */
package com.example.lomanalyzer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lomanalyzer.ui.theme.AppColors
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Описание квадранта-роли: короткая метка, полное описание и акцентный цвет. */
// Определения квадрантов для 4 базовых ролей (диплом 2.1.6)
private data class Quadrant(val label: String, val description: String, val color: Color)

/** Авторитетный лидер: высокое структурное влияние + высокая тематическая активность. */
private val Q_AUTH = Quadrant("ЛИДЕР", "Авторитетный лидер", Color(0xFF1B5E20))

/** Тематический активист: низкое структурное влияние + высокая тематическая активность. */
private val Q_ACTIVIST = Quadrant("АКТИВИСТ", "Тематический активист", Color(0xFF0D47A1))

/** Спящий гигант: высокое структурное влияние + низкая тематическая активность. */
private val Q_GIANT = Quadrant("ГИГАНТ", "Спящий гигант", Color(0xFFE65100))

/** Фоновый участник: низкое влияние + низкая тематическая активность. */
private val Q_BG = Quadrant("ФОН", "Фоновый участник", Color(0xFF6A1B9A))

/**
 * Цвет точки по позиции автора: зелёный (поддержка) → серый (нейтрально) →
 * красный (критика). score = posPositive − posNegative, диапазон [-1, 1].
 * Кривая sqrt усиливает малые отклонения от нейтрали: уже при score ±0.05 цвет
 * сдвигается примерно на 22%, а не остаётся почти незаметным.
 *
 * @param posPositive доля позитивной позиции автора.
 * @param posNegative доля негативной позиции автора.
 */
private fun positionColor(posPositive: Float, posNegative: Float): Color {
    // Тональный счёт позиции, зажатый в [-1, 1]
    val score = (posPositive - posNegative).coerceIn(-1f, 1f)
    val green = Color(0xFF2E7D32)
    val gray = Color(0xFF9E9E9E)
    val red = Color(0xFFC62828)
    // sqrt усиливает малые значения: sqrt(0.05)=0.22, sqrt(0.1)=0.32, sqrt(0.3)=0.55
    val intensity = kotlin.math.sqrt(abs(score))

    // Положительный счёт — к зелёному, отрицательный — к красному (от нейтрального серого)
    return if (score >= 0) {
        lerpColor(gray, green, intensity)
    } else {
        lerpColor(gray, red, intensity)
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
 * Квадрантный точечный график классификации ЛОМ.
 *
 * @param points точки-авторы с координатами, позицией (цвет) и ER (размер).
 * @param tauStruct порог по оси X (структурное влияние, theta_S) — граница квадрантов.
 * @param tauTopic порог по оси Y (тематическая активность, theta_T) — граница квадрантов.
 * @param modifier внешний Modifier (по умолчанию — ширина на всю, высота 280..480 dp).
 */
@Composable
@Suppress("FunctionNaming")
fun ScatterPlot(
    points: List<ScatterPoint>,
    tauStruct: Float,
    tauTopic: Float,
    modifier: Modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 480.dp),
) {
    // Пустые данные — показываем заглушку вместо графика
    if (points.isEmpty()) {
        Surface(shape = RoundedCornerShape(8.dp), color = AppColors.surfaceVariant, modifier = modifier) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Данные появятся после завершения анализа", fontSize = 12.sp, color = AppColors.textTertiary)
            }
        }
        return
    }

    // Измеритель текста для подписей на Canvas (засечки, оси, имена, метки квадрантов)
    val textMeasurer = rememberTextMeasurer()
    // Стили текста кэшируем через remember, чтобы не пересоздавать при каждой recomposition
    val tickStyle = remember { TextStyle(fontSize = 10.sp, color = Color(0xFF757575)) }
    val axisLabelStyle = remember { TextStyle(fontSize = 11.sp, color = Color(0xFF616161), fontWeight = FontWeight.Medium) }
    val quadrantLabelStyle = remember { TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
    val quadrantDescStyle = remember { TextStyle(fontSize = 10.sp) }
    val threshStyle = remember { TextStyle(fontSize = 10.sp) }
    val nameStyle = remember { TextStyle(fontSize = 9.sp, color = Color(0xFF424242)) }

    Column(modifier = modifier) {
        // Легенда: цвет точки = тональность позиции автора (поддержка/нейтрально/критика)
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(10.dp).background(Color(0xFF2E7D32), CircleShape))
                Text("Поддержка", fontSize = 10.sp, color = AppColors.textSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(10.dp).background(Color(0xFF9E9E9E), CircleShape))
                Text("Нейтрально", fontSize = 10.sp, color = AppColors.textSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(10.dp).background(Color(0xFFC62828), CircleShape))
                Text("Критика", fontSize = 10.sp, color = AppColors.textSecondary)
            }
            Spacer(Modifier.weight(1f))
            // Счётчик авторов в правой части легенды
            Text("${points.size} авторов", fontSize = 11.sp, color = AppColors.textTertiary, fontWeight = FontWeight.Medium)
        }

        // Сам график рисуется вручную на Canvas; вся отрисовка вынесена в drawScatterContent
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            drawScatterContent(points, tauStruct, tauTopic, textMeasurer,
                tickStyle, axisLabelStyle, quadrantLabelStyle, quadrantDescStyle, threshStyle, nameStyle)
        }
    }
}

// ── Вспомогательные функции отрисовки ──

/**
 * Подбирает «красивую» верхнюю границу оси по максимуму значений
 * (округляет вверх до 1/1.5/2/3/5/7.5 × 10^n).
 */
private fun niceMax(values: List<Float>): Float {
    if (values.isEmpty()) return 1f
    val raw = values.max().coerceAtLeast(0.01f)
    val magnitude = 10.0.pow(floor(log10(raw.toDouble()))).toFloat()
    val normalized = raw / magnitude
    val nice = when {
        normalized <= 1.0f -> 1.0f; normalized <= 1.5f -> 1.5f; normalized <= 2.0f -> 2.0f
        normalized <= 3.0f -> 3.0f; normalized <= 5.0f -> 5.0f; normalized <= 7.5f -> 7.5f
        else -> 10.0f
    }
    return nice * magnitude
}

/**
 * Строит список «красивых» засечек оси от 0 до max с шагом, кратным 1/2/2.5/5/10.
 * @param desiredCount желаемое примерное число интервалов.
 */
private fun niceTicks(max: Float, desiredCount: Int = 5): List<Float> {
    if (max <= 0f) return listOf(0f)
    val rawStep = max / desiredCount
    val mag = 10.0.pow(floor(log10(rawStep.toDouble()))).toFloat()
    val ns = rawStep / mag
    val step = (when { ns <= 1f -> 1f; ns <= 2f -> 2f; ns <= 2.5f -> 2.5f; ns <= 5f -> 5f; else -> 10f }) * mag
    val ticks = mutableListOf<Float>()
    var v = 0f
    while (v <= max + step * 0.01f) { ticks.add(v); v += step }
    return ticks
}

/** Форматирует значение засечки: целое без дробей или 2–3 знака с обрезкой нулей. */
private fun fmtTick(v: Float): String = when {
    v == 0f -> "0"
    v >= 1f && v == v.toInt().toFloat() -> v.toInt().toString()
    v >= 0.1f -> "%.2f".format(v).trimEnd('0').trimEnd('.')
    else -> "%.3f".format(v).trimEnd('0').trimEnd('.')
}

/**
 * Низкоуровневая отрисовка всего содержимого графика на Canvas (DrawScope):
 * фоны квадрантов, сетка, оси, пороговые линии, подписи осей/порогов/квадрантов
 * и сами точки с подписями имён.
 *
 * @receiver DrawScope текущего Canvas.
 * @param points точки-авторы.
 * @param tauS порог по X (структурное влияние).
 * @param tauT порог по Y (тематическая активность).
 * @param tm измеритель текста для подписей.
 * @param tickSt стиль засечек; axisSt стиль подписей осей; qLabelSt/qDescSt стили
 *   меток квадрантов; thrSt стиль подписей порогов; nameSt стиль имён авторов.
 */
@Suppress("LongParameterList", "LongMethod")
private fun DrawScope.drawScatterContent(
    points: List<ScatterPoint>, tauS: Float, tauT: Float,
    tm: androidx.compose.ui.text.TextMeasurer,
    tickSt: TextStyle, axisSt: TextStyle, qLabelSt: TextStyle,
    qDescSt: TextStyle, thrSt: TextStyle, nameSt: TextStyle,
) {
    // Отступы области рисования: слева (под подписи оси Y), снизу, сверху, справа
    val pL = 62f; val pB = 52f; val pT = 16f; val pR = 16f
    // Размеры области данных за вычетом отступов
    val w = size.width - pL - pR; val h = size.height - pT - pB
    if (w <= 0f || h <= 0f) return

    // Диапазоны данных по осям; включаем пороги, чтобы линии порогов всегда попадали в кадр
    val allX = points.map { it.structComposite } + tauS
    val allY = points.map { it.topicComposite } + tauT
    val minX = allX.min(); val maxXr = allX.max()
    val rangeX = (maxXr - minX).coerceAtLeast(0.01f)
    val minY = allY.min(); val maxYr = allY.max()
    val rangeY = (maxYr - minY).coerceAtLeast(0.01f)
    // Добавляем по 10% запаса с каждой стороны, чтобы точки не липли к краям
    val xLo = minX - rangeX * 0.1f; val xHi = maxXr + rangeX * 0.1f
    val yLo = minY - rangeY * 0.1f; val yHi = maxYr + rangeY * 0.1f
    val xRange = xHi - xLo; val yRange = yHi - yLo

    // Перевод значения данных в пиксельную координату X внутри области графика
    fun mapX(v: Float) = pL + ((v - xLo) / xRange).coerceIn(0f, 1f) * w
    // Перевод значения в пиксельную координату Y (инвертируем: ось Y экрана растёт вниз)
    fun mapY(v: Float) = pT + h * (1f - ((v - yLo) / yRange).coerceIn(0f, 1f))

    // Пиксельные позиции пороговых линий — границы квадрантов
    val tauSX = mapX(tauS); val tauTY = mapY(tauT)

    // Фоновые заливки квадрантов (слабая прозрачность qA)
    val qA = 0.05f
    drawRect(Q_ACTIVIST.color.copy(alpha = qA), Offset(pL, pT), androidx.compose.ui.geometry.Size(tauSX - pL, tauTY - pT))
    drawRect(Q_AUTH.color.copy(alpha = qA), Offset(tauSX, pT), androidx.compose.ui.geometry.Size(pL + w - tauSX, tauTY - pT))
    drawRect(Q_BG.color.copy(alpha = qA), Offset(pL, tauTY), androidx.compose.ui.geometry.Size(tauSX - pL, pT + h - tauTY))
    drawRect(Q_GIANT.color.copy(alpha = qA), Offset(tauSX, tauTY), androidx.compose.ui.geometry.Size(pL + w - tauSX, pT + h - tauTY))

    // Вспомогательная сетка (по 5 линий на ось)
    val gridC = Color(0xFFE8E8E8)
    val nLines = 5
    for (i in 1..nLines) {
        val fx = i.toFloat() / (nLines + 1)
        drawLine(gridC, Offset(pL + fx * w, pT), Offset(pL + fx * w, pT + h), 0.5f)
        drawLine(gridC, Offset(pL, pT + fx * h), Offset(pL + w, pT + fx * h), 0.5f)
    }

    // Оси координат (нижняя и левая границы области)
    val axC = Color(0xFFBDBDBD)
    drawLine(axC, Offset(pL, pT + h), Offset(pL + w, pT + h), 1.5f)
    drawLine(axC, Offset(pL, pT), Offset(pL, pT + h), 1.5f)

    // Пороговые линии (theta_S, theta_T) пунктиром — границы квадрантов
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
    drawLine(Color(0xFF1565C0).copy(alpha = 0.5f), Offset(tauSX, pT), Offset(tauSX, pT + h), 1.5f, pathEffect = dash)
    drawLine(Color(0xFFE65100).copy(alpha = 0.5f), Offset(pL, tauTY), Offset(pL + w, tauTY), 1.5f, pathEffect = dash)

    // Подписи осей: X — по центру под графиком, Y — повёрнута на -90° вдоль левого края
    val xLbl = "Структурное влияние (Struct) \u2192"
    val xLay = tm.measure(xLbl, axisSt)
    drawText(xLay, topLeft = Offset(pL + w / 2 - xLay.size.width / 2f, pT + h + 28))
    val yLbl = "\u2192 Тематическая активность (Topic)"
    val yLay = tm.measure(yLbl, axisSt)
    // \u041F\u043E\u0432\u043E\u0440\u043E\u0442 \u0441\u0438\u0441\u0442\u0435\u043C\u044B \u043A\u043E\u043E\u0440\u0434\u0438\u043D\u0430\u0442 \u043D\u0430 -90\u00B0 \u0434\u043B\u044F \u0432\u0435\u0440\u0442\u0438\u043A\u0430\u043B\u044C\u043D\u043E\u0439 \u043F\u043E\u0434\u043F\u0438\u0441\u0438 \u043E\u0441\u0438 Y
    withTransform({ rotate(-90f, Offset(12f, pT + h / 2)) }) {
        drawText(yLay, topLeft = Offset(12f - yLay.size.width / 2f, pT + h / 2 - yLay.size.height / 2f))
    }

    // \u041F\u043E\u0434\u043F\u0438\u0441\u0438 \u043F\u043E\u0440\u043E\u0433\u043E\u0432 theta_S \u0438 theta_T \u0440\u044F\u0434\u043E\u043C \u0441 \u043F\u0443\u043D\u043A\u0442\u0438\u0440\u043D\u044B\u043C\u0438 \u043B\u0438\u043D\u0438\u044F\u043C\u0438 (\u0441\u0438\u043C\u0432\u043E\u043B \u03B8 \u2014 \u03B8)
    val tsLbl = "\u03B8_S=%.2f".format(tauS)
    val tsLay = tm.measure(tsLbl, thrSt.copy(color = Color(0xFF1565C0).copy(alpha = 0.7f)))
    drawText(tsLay, topLeft = Offset(tauSX + 4, pT + 2))
    val ttLbl = "\u03B8_T=%.2f".format(tauT)
    val ttLay = tm.measure(ttLbl, thrSt.copy(color = Color(0xFFE65100).copy(alpha = 0.7f)))
    drawText(ttLay, topLeft = Offset(pL + w - ttLay.size.width - 4, tauTY - ttLay.size.height - 2))

    // Метки квадрантов (название роли + описание) по центру каждого квадранта
    val qAlpha = 0.45f
    // Локальный хелпер: рисует две строки метки квадранта с центрированием по (cx, cy)
    fun drawQL(q: Quadrant, cx: Float, cy: Float) {
        val ll = tm.measure(q.label, qLabelSt.copy(color = q.color.copy(alpha = qAlpha)))
        val dl = tm.measure(q.description, qDescSt.copy(color = q.color.copy(alpha = qAlpha * 0.7f)))
        drawText(ll, topLeft = Offset(cx - ll.size.width / 2f, cy - ll.size.height))
        drawText(dl, topLeft = Offset(cx - dl.size.width / 2f, cy + 2))
    }
    drawQL(Q_ACTIVIST, (pL + tauSX) / 2, (pT + tauTY) / 2)
    drawQL(Q_AUTH, (tauSX + pL + w) / 2, (pT + tauTY) / 2)
    drawQL(Q_BG, (pL + tauSX) / 2, (tauTY + pT + h) / 2)
    drawQL(Q_GIANT, (tauSX + pL + w) / 2, (tauTY + pT + h) / 2)

    // Точки данных — цвет = градиент позиции, размер = тематический ER.
    // clipRect ограничивает отрисовку областью графика (точки у края не вылезают на оси)
    clipRect(pL, pT, pL + w, pT + h) {
        // Гало: полупрозрачный ореол вокруг точки (радиус + 3) для лучшей читаемости
        points.forEach { p ->
            val x = mapX(p.structComposite); val y = mapY(p.topicComposite)
            val color = positionColor(p.posPositive, p.posNegative)
            // Радиус растёт с тематическим ER (нормируем по 0.3), зажат в [5, 14] px
            val radius = (5f + p.erTop.coerceIn(0f, 0.3f) / 0.3f * 7f).coerceIn(5f, 14f)
            drawCircle(color.copy(alpha = 0.15f), radius + 3f, Offset(x, y))
        }
        // Сами точки авторов поверх гало
        points.forEach { p ->
            val x = mapX(p.structComposite); val y = mapY(p.topicComposite)
            val color = positionColor(p.posPositive, p.posNegative)
            val radius = (5f + p.erTop.coerceIn(0f, 0.3f) / 0.3f * 7f).coerceIn(5f, 14f)
            drawCircle(color.copy(alpha = 0.85f), radius, Offset(x, y))
        }
        // Подписи имён — выводим максимум без наложений.
        // Приоритет — по сумме композитов (самых значимых авторов подписываем первыми).
        val sorted = points.sortedByDescending { it.structComposite + it.topicComposite }
        // Прямоугольники уже размещённых подписей для проверки пересечений: [x1, y1, x2, y2]
        val usedRects = mutableListOf<FloatArray>() // [x1, y1, x2, y2]
        for (p in sorted) {
            val x = mapX(p.structComposite); val y = mapY(p.topicComposite)
            val radius = (5f + p.erTop.coerceIn(0f, 0.3f) / 0.3f * 7f).coerceIn(5f, 14f)
            // Имя обрезаем до 18 символов; ставим справа от точки
            val nl = tm.measure(p.authorName.take(18), nameSt)
            val lx = x + radius + 3; val ly = y - nl.size.height / 2f
            val lx2 = lx + nl.size.width; val ly2 = ly + nl.size.height
            // Пропускаем подпись, если она выходит за границы графика или налезает на уже нарисованную
            val overlaps = lx2 > pL + w || lx < pL ||
                usedRects.any { r -> lx < r[2] + 2 && lx2 > r[0] - 2 && ly < r[3] + 1 && ly2 > r[1] - 1 }
            if (!overlaps) {
                drawText(nl, topLeft = Offset(lx, ly))
                usedRects.add(floatArrayOf(lx, ly, lx2, ly2))
            }
        }
    }
}
