/*
 * НАЗНАЧЕНИЕ
 * Экран хода сбора и анализа данных. Показывает прогресс выполнения пайплайна в
 * реальном времени (этапы 2-10, docs/algorithm.md): текущий этап, процент
 * выполнения, число обработанных и оставшихся элементов, баннеры успеха/ошибки.
 * Позволяет отменить выполнение.
 *
 * ЧТО ВНУТРИ
 * CollectionScreen — корневой Composable экрана прогресса.
 * CircularProgressRing — кольцевой индикатор прогресса (Canvas) с пульсацией.
 * StatMiniCard — мини-карточка статистики (выполнено/осталось/всего).
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop (Material, Canvas, анимации) — UI и кастомная отрисовка кольца;
 * Koin — получение ProgressReporter, CancellationController, PipelineLauncher;
 * StateFlow + collectAsState — реактивная подписка на прогресс пайплайна.
 *
 * СВЯЗИ
 * ProgressReporter.progress — источник состояния прогресса (заполняется
 * оркестратором пайплайна); PipelineLauncher.cancel — отмена выполнения;
 * на этот экран переходят с SetupScreen после нажатия «Начать сбор».
 */
package com.example.lomanalyzer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lomanalyzer.orchestration.CancellationController
import com.example.lomanalyzer.orchestration.PipelineLauncher
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.ui.theme.AppColors
import com.example.lomanalyzer.ui.theme.ScreenHeader
import com.example.lomanalyzer.ui.theme.SectionCard
import org.koin.java.KoinJavaComponent.get

/**
 * Экран хода сбора и анализа данных: прогресс пайплайна в реальном времени.
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
fun CollectionScreen() {
    // Зависимости из Koin: репортер прогресса, контроллер отмены и лаунчер пайплайна
    val progressReporter = remember { get<ProgressReporter>(ProgressReporter::class.java) }
    val controller = remember { get<CancellationController>(CancellationController::class.java) }
    val launcher = remember { get<PipelineLauncher>(PipelineLauncher::class.java) }
    // Реактивная подписка на состояние прогресса; UI перерисовывается при каждом обновлении
    val progress by progressReporter.progress.collectAsState()

    // Производные флаги состояния выполнения
    val hasError = progress.error != null                                  // есть ли ошибка
    val isFinished = progress.finished                                     // завершено ли выполнение
    val isActive = progress.stage.isNotEmpty() && !isFinished             // идёт ли активная работа
    // Доля выполнения [0..1]: обработанные / всего (0, если общее число неизвестно)
    val fraction = if (progress.totalItems > 0) progress.completedItems.toFloat() / progress.totalItems else 0f

    // Подзаголовок-статус в зависимости от текущего состояния
    val subtitle = when {
        hasError -> "Ошибка: ${progress.error}"
        isFinished && !hasError -> "Анализ завершён"
        isActive -> "Этап: ${progress.stage} (${progress.completedItems}/${progress.totalItems})"
        else -> "Ожидание запуска"
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = "Сбор и анализ данных",
            subtitle = subtitle,
        )

        // ── Баннер ошибки: показывает этап и текст ошибки ──
        if (hasError) {
            Card(
                shape = RoundedCornerShape(10.dp),
                backgroundColor = AppColors.error.copy(alpha = 0.1f),
                elevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.Error, null, tint = AppColors.error, modifier = Modifier.size(24.dp))
                    Column {
                        Text(
                            "Ошибка на этапе: ${progress.stage}",
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.error,
                        )
                        Text(
                            progress.error ?: "",
                            fontSize = 13.sp,
                            color = AppColors.textSecondary,
                        )
                    }
                }
            }
        }

        // ── Баннер успеха: все этапы завершены без ошибок ──
        if (isFinished && !hasError) {
            Card(
                shape = RoundedCornerShape(10.dp),
                backgroundColor = AppColors.secondary.copy(alpha = 0.1f),
                elevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = AppColors.secondary, modifier = Modifier.size(24.dp))
                    Text(
                        "Все этапы успешно завершены",
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.secondary,
                    )
                }
            }
        }

        // ── Карточка прогресса: кольцо процента + текущий этап + линейный индикатор ──
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Кольцевой индикатор прогресса с процентом в центре
                Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressRing(fraction, isActive, hasError)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(fraction * 100).toInt()}%",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = if (hasError) AppColors.error else AppColors.textPrimary,
                        )
                        if (isActive) {
                            Text("готово", fontSize = 10.sp, color = AppColors.textTertiary)
                        }
                    }
                }

                // Блок статистики справа от кольца
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Текущий этап: иконка и цвет зависят от состояния (ошибка/готово/идёт/ожидание)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val icon = when {
                            hasError -> Icons.Filled.Error
                            isFinished -> Icons.Filled.CheckCircle
                            isActive -> Icons.Filled.Sync
                            else -> Icons.Outlined.HourglassEmpty
                        }
                        val tint = when {
                            hasError -> AppColors.error
                            isFinished -> AppColors.secondary
                            isActive -> AppColors.primary
                            else -> AppColors.textTertiary
                        }
                        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                        Text(
                            progress.stage.ifEmpty { "Ожидание" },
                            fontWeight = FontWeight.Medium,
                            color = if (isActive || isFinished) AppColors.textPrimary else AppColors.textTertiary,
                        )
                    }

                    if (progress.totalItems > 0) {
                        // Линейный индикатор прогресса под названием этапа
                        LinearProgressIndicator(
                            progress = fraction,
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = if (hasError) AppColors.error else AppColors.primary,
                            backgroundColor = AppColors.primaryLight,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${progress.completedItems} / ${progress.totalItems}",
                                fontSize = 12.sp,
                                color = AppColors.textSecondary,
                            )
                        }
                    }
                }
            }
        }

        // ── Сетка мини-карточек статистики: выполнено / осталось / всего ──
        if (progress.totalItems > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatMiniCard(
                    label = "Выполнено",
                    value = "${progress.completedItems}",
                    icon = Icons.Outlined.CheckCircle,
                    color = AppColors.secondary,
                    modifier = Modifier.weight(1f),
                )
                StatMiniCard(
                    label = "Осталось",
                    value = "${progress.totalItems - progress.completedItems}",
                    icon = Icons.Outlined.Pending,
                    color = AppColors.warning,
                    modifier = Modifier.weight(1f),
                )
                StatMiniCard(
                    label = "Всего",
                    value = "${progress.totalItems}",
                    icon = Icons.Outlined.List,
                    color = AppColors.info,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Управление: кнопка отмены (активна только во время выполнения) ──
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { launcher.cancel() },
                enabled = isActive,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AppColors.error,
                    contentColor = Color.White,
                ),
                modifier = Modifier.height(42.dp),
            ) {
                Icon(Icons.Filled.Stop, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Отменить")
            }
        }
    }
}

/**
 * Кольцевой индикатор прогресса, нарисованный на Canvas: фоновое кольцо плюс
 * дуга-прогресс. Во время активной работы фон пульсирует.
 * @param fraction доля выполнения [0..1].
 * @param isActive идёт ли активная работа (включает пульсацию).
 * @param hasError есть ли ошибка (меняет цвет дуги на красный).
 */
@Composable
@Suppress("FunctionNaming")
private fun CircularProgressRing(fraction: Float, isActive: Boolean, hasError: Boolean = false) {
    // Плавная анимация заполнения дуги при изменении доли выполнения
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
    )
    // Угол развёртки дуги: доля [0..1] -> градусы [0..360]
    val sweep = animatedFraction * 360f

    // Пульсация прозрачности фонового кольца для индикации активного состояния
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
    )

    val arcColor = if (hasError) AppColors.error else AppColors.primary

    Canvas(modifier = Modifier.size(100.dp)) {
        val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
        val padding = 4.dp.toPx()
        val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
        val topLeft = Offset(padding, padding)

        // Фоновое кольцо (полный круг 360°); пульсирует при активной работе
        drawArc(
            color = if (isActive) arcColor.copy(alpha = pulseAlpha) else AppColors.border,
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = arcSize, style = stroke,
        )
        // Дуга прогресса: рисуется поверх фона от верхней точки (-90°) по часовой стрелке
        if (sweep > 0f) {
            drawArc(
                color = arcColor,
                startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = topLeft, size = arcSize, style = stroke,
            )
        }
    }
}

/**
 * Мини-карточка статистики: иконка, числовое значение и подпись.
 * @param label подпись метрики (например, «Выполнено»).
 * @param value числовое значение в виде строки.
 * @param icon иконка карточки.
 * @param color акцентный цвет иконки и фона.
 * @param modifier модификатор компоновки (обычно weight для равной ширины).
 */
@Composable
@Suppress("FunctionNaming")
private fun StatMiniCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        elevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.12f)) {
                Icon(icon, null, tint = color, modifier = Modifier.padding(6.dp).size(18.dp))
            }
            Column {
                Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.textPrimary)
                Text(label, fontSize = 11.sp, color = AppColors.textTertiary)
            }
        }
    }
}
