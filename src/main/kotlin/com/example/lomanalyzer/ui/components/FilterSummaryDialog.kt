/*
 * НАЗНАЧЕНИЕ
 * Переиспользуемый UI-компонент Compose Desktop — диалог сводки качества
 * тематической фильтрации (диплом 2.2.6). Показывается между этапами 6 и 7
 * пайплайна и даёт аналитику возможность оценить результат двухпроходного
 * тематического фильтра (L1 ключевые слова, L2 RuBERT) и вручную разметить
 * спорные публикации перед расчётом оценок.
 *
 * ЧТО ВНУТРИ
 * @Composable FilterSummaryDialog — модальный AlertDialog со сводкой (три доли)
 * и списком спорных постов. Приватные @Composable: ProportionCard (карточка
 * одной доли), DisputedPostCard (карточка спорного поста с голосованием),
 * VoteButton (кнопка-переключатель тематичности).
 *
 * МЕТОД
 * Три доли по результату фильтрации: уверенно прошли (L1), подтверждены вторым
 * проходом (L2), спорные. По спорным аналитик голосует «тематическая / не
 * тематическая / сброс», вызывая onVote — ручная корректировка фильтра.
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop; AlertDialog — модальное окно; LazyColumn — список спорных
 * постов; remember/mutableStateOf — локальное состояние голоса по посту.
 *
 * СВЯЗИ
 * Принимает FilterQualitySummary и ValidationPost из analysis.topic; колбэки
 * управляют продолжением/перезапуском пайплайна.
 */
package com.example.lomanalyzer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lomanalyzer.analysis.topic.FilterQualitySummary
import com.example.lomanalyzer.analysis.topic.ValidationPost
import com.example.lomanalyzer.ui.theme.AppColors

/**
 * Диалог сводки качества тематической фильтрации (диплом 2.2.6).
 * Появляется между этапами 6 и 7: три доли + список спорных публикаций.
 *
 * @param summary агрегированная сводка фильтрации (доли и спорные посты).
 * @param onContinue продолжить пайплайн с текущим результатом фильтрации.
 * @param onRerunFilter перезапустить только тематическую фильтрацию.
 * @param onRestartSession начать сессию анализа заново.
 * @param onVote ручная разметка спорного поста: id и голос
 *   (true — тематический, false — нет, null — сброс).
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
fun FilterSummaryDialog(
    summary: FilterQualitySummary,
    onContinue: () -> Unit,
    onRerunFilter: () -> Unit,
    onRestartSession: () -> Unit,
    onVote: (postId: Int, vote: Boolean?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Сводка качества тематической фильтрации", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.widthIn(min = 500.dp, max = 700.dp).heightIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Три доли результата фильтрации: уверенные (L1), второй проход (L2), спорные
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProportionCard("Уверенно прошли", summary.confidentCount, summary.confidentRatio,
                        Color(0xFF2E7D32), Modifier.weight(1f))
                    ProportionCard("Второй проход", summary.pass2ConfirmedCount, summary.pass2Ratio,
                        Color(0xFF1565C0), Modifier.weight(1f))
                    ProportionCard("Спорные", summary.disputedCount, summary.disputedRatio,
                        Color(0xFFE65100), Modifier.weight(1f))
                }

                // Итоговые счётчики: всего публикаций и сколько исключено фильтром
                Text("Всего публикаций: ${summary.totalPosts}, исключено: ${summary.excludedCount}",
                    fontSize = 12.sp, color = AppColors.textSecondary)

                Divider()

                // Спорные публикации для ручной проверки (если есть)
                if (summary.disputedPosts.isNotEmpty()) {
                    Text("Спорные публикации (${summary.disputedPosts.size})",
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        itemsIndexed(summary.disputedPosts) { _, post ->
                            DisputedPostCard(post, onVote)
                        }
                    }
                } else {
                    Text("Нет спорных публикаций", fontSize = 13.sp, color = AppColors.textTertiary)
                }
            }
        },
        confirmButton = {
            // Основное действие — продолжить пайплайн с текущим результатом
            Button(onClick = onContinue, colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.primary)) {
                Text("Продолжить", color = Color.White)
            }
        },
        dismissButton = {
            // Альтернативы: перезапуск только фильтрации или всей сессии
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRerunFilter) { Text("Перезапустить фильтрацию") }
                OutlinedButton(onClick = onRestartSession) { Text("Начать заново") }
            }
        },
    )
}

/**
 * Карточка одной доли сводки: крупный процент, подпись и абсолютное число.
 *
 * @param label подпись доли (например, «Спорные»).
 * @param count абсолютное число публикаций в этой группе.
 * @param ratio доля [0..1]; показывается как процент.
 * @param color акцентный цвет группы.
 * @param modifier внешний Modifier (обычно weight для равной ширины).
 */
@Composable
@Suppress("FunctionNaming")
private fun ProportionCard(label: String, count: Int, ratio: Float, color: Color, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.08f), modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Крупный процент доли
            Text("%.0f%%".format(ratio * 100), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 11.sp, color = color.copy(alpha = 0.8f))
            // Абсолютное число публикаций в группе
            Text("$count", fontSize = 12.sp, color = AppColors.textSecondary)
        }
    }
}

/**
 * Карточка одного спорного поста с возможностью ручной разметки.
 *
 * @param post спорная публикация (сообщество, score фильтра, текст, текущий голос).
 * @param onVote колбэк разметки: id поста и голос (true/false/null).
 */
@Composable
@Suppress("FunctionNaming")
private fun DisputedPostCard(post: ValidationPost, onVote: (Int, Boolean?) -> Unit) {
    // Локальное состояние голоса аналитика по этому посту; пересоздаётся при смене поста (key = post.id)
    var currentVote by remember(post.id) { mutableStateOf(post.analystVote) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = AppColors.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Шапка: имя сообщества слева, score фильтра справа
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(post.communityName, fontSize = 11.sp, color = AppColors.textTertiary, maxLines = 1)
                Text("score: %.2f".format(post.score), fontSize = 11.sp, color = AppColors.textSecondary)
            }
            // Превью текста поста: до 200 символов с многоточием
            Text(
                post.text.take(200).let { if (post.text.length > 200) "$it..." else it },
                fontSize = 12.sp, color = AppColors.textPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            // Кнопки голосования: при нажатии обновляем локальный голос и сообщаем наружу через onVote
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoteButton("Тематическая", currentVote == true, Color(0xFF2E7D32)) {
                    currentVote = true; onVote(post.id, true)
                }
                VoteButton("Не тематическая", currentVote == false, Color(0xFFC62828)) {
                    currentVote = false; onVote(post.id, false)
                }
                // Кнопка сброса голоса показывается только если голос уже выставлен
                if (currentVote != null) {
                    TextButton(onClick = { currentVote = null; onVote(post.id, null) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("Сброс", fontSize = 11.sp, color = AppColors.textTertiary)
                    }
                }
            }
        }
    }
}

/**
 * Кнопка-переключатель голоса по тематичности поста.
 *
 * @param label подпись кнопки.
 * @param selected выбран ли этот вариант (подсвечивается фоном и цветом текста).
 * @param color акцентный цвет варианта.
 * @param onClick обработчик нажатия.
 */
@Composable
@Suppress("FunctionNaming")
private fun VoteButton(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            backgroundColor = if (selected) color.copy(alpha = 0.12f) else Color.Transparent,
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Text(label, fontSize = 11.sp, color = if (selected) color else AppColors.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
