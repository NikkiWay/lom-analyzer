/*
 * НАЗНАЧЕНИЕ
 * Переиспользуемый UI-компонент Compose Desktop — немодальный плавающий оверлей
 * в правом нижнем углу. Связан с фазой сбора данных C (per-author стены и
 * комментарии): показывает либо обратный отсчёт до старта фазы C, либо
 * предупреждение о том, что фаза C пропущена из-за flood control VK.
 *
 * ЧТО ВНУТРИ
 * @Composable CooldownOverlay — корневой контейнер, подписан на CooldownState.
 * Приватные @Composable CountdownCard (карточка таймера с прогресс-баром) и
 * PhaseSkippedCard (карточка предупреждения с кнопкой «Понятно»).
 *
 * МЕТОД
 * Состояние читается из CooldownState через collectAsState (StateFlow):
 * remainingSeconds/totalSeconds/label/subtitle/phaseSkippedMessage. Видимость
 * карточек управляется AnimatedVisibility (fade-in/out). Прогресс таймера =
 * 1 − remaining/total.
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop; AnimatedVisibility — анимация появления; collectAsState —
 * подписка на StateFlow; Koin (DI) — получение CooldownState.
 *
 * СВЯЗИ
 * CooldownState из orchestration; пропуск фазы C связан с VK flood control.
 */
package com.example.lomanalyzer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lomanalyzer.orchestration.CooldownState
import com.example.lomanalyzer.ui.theme.AppColors
import org.koin.java.KoinJavaComponent.get

/**
 * Non-modal floating overlay in the bottom-right corner.
 * Shows either:
 * 1. Countdown timer before Phase C starts
 * 2. Warning that Phase C was skipped due to flood control
 */
/**
 * Плавающий оверлей фазы C: таймер обратного отсчёта или предупреждение о пропуске.
 * Параметров не принимает — всё состояние берётся из общего CooldownState (DI).
 */
@Composable
@Suppress("FunctionNaming")
fun CooldownOverlay() {
    // Получаем общий объект состояния из Koin (запоминаем на время жизни композабла)
    val cooldownState = remember { get<CooldownState>(CooldownState::class.java) }
    // Подписываемся на реактивные поля состояния; recomposition при их изменении
    val remaining by cooldownState.remainingSeconds.collectAsState()
    val total by cooldownState.totalSeconds.collectAsState()
    val label by cooldownState.label.collectAsState()
    val subtitle by cooldownState.subtitle.collectAsState()
    val skippedMessage by cooldownState.phaseSkippedMessage.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        // ── Карточка обратного отсчёта (видна, пока остаются секунды) ──
        AnimatedVisibility(
            visible = remaining > 0,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            CountdownCard(remaining, total, label, subtitle)
        }

        // ── Карточка предупреждения о пропуске фазы C (видна при наличии сообщения) ──
        AnimatedVisibility(
            visible = skippedMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            skippedMessage?.let { msg ->
                // Кнопка закрытия сбрасывает сообщение в состоянии
                PhaseSkippedCard(msg) { cooldownState.dismissPhaseSkipped() }
            }
        }
    }
}

/**
 * Карточка обратного отсчёта до старта фазы C.
 *
 * @param remaining оставшиеся секунды.
 * @param total полная длительность отсчёта в секундах (для прогресс-бара).
 * @param label заголовок карточки (по умолчанию «Ожидание»).
 * @param subtitle пояснительная строка под заголовком (может быть пустой).
 */
@Composable
@Suppress("FunctionNaming")
private fun CountdownCard(remaining: Int, total: Int, label: String, subtitle: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = 8.dp,
        backgroundColor = AppColors.surface,
    ) {
        Column(
            modifier = Modifier.padding(20.dp).width(280.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.Timer,
                    contentDescription = null,
                    tint = AppColors.warning,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    label.ifEmpty { "Ожидание" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }

            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = AppColors.textSecondary,
                )
            }

            // Переводим оставшиеся секунды в формат мм:сс
            val minutes = remaining / 60
            val seconds = remaining % 60
            Text(
                "%d:%02d".format(minutes, seconds),
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = AppColors.primary,
            )

            // Доля пройденного времени: 1 − remaining/total (0 при неизвестной длительности)
            val fraction = if (total > 0) 1f - remaining.toFloat() / total else 0f
            LinearProgressIndicator(
                progress = fraction,
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = AppColors.primary,
                backgroundColor = AppColors.primaryLight,
            )
        }
    }
}

/**
 * Карточка-предупреждение о том, что фаза C пропущена (из-за flood control VK).
 *
 * @param message текст пояснения причины пропуска.
 * @param onDismiss обработчик кнопки «Понятно» (сбрасывает сообщение).
 */
@Composable
@Suppress("FunctionNaming")
private fun PhaseSkippedCard(message: String, onDismiss: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = 8.dp,
        backgroundColor = AppColors.warning.copy(alpha = 0.08f),
    ) {
        Column(
            modifier = Modifier.padding(20.dp).width(340.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = AppColors.warning,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    "Этап C пропущен",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = AppColors.warning,
                )
            }

            Text(
                message,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = AppColors.textSecondary,
            )

            Text(
                "Анализ продолжается с имеющимися данными.",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.textPrimary,
            )

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AppColors.warning,
                    contentColor = AppColors.surface,
                ),
                modifier = Modifier.align(Alignment.End).height(36.dp),
            ) {
                Text("Понятно", fontSize = 13.sp)
            }
        }
    }
}
