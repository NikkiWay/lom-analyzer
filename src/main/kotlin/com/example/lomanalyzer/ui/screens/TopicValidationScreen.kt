/*
 * НАЗНАЧЕНИЕ
 * Презентационный экран ручной валидации тематической фильтрации аналитиком
 * (этап 6 пайплайна, docs/algorithm.md). Показывает посты сессии, сгруппированные
 * по источнику (сообщество/стена) и автору, и даёт аналитику оценить каждый пост
 * (релевантен теме / нет). По голосам аналитика считаются метрики precision и
 * recall системного тематического фильтра. Сам экран не работает с БД — все
 * действия отдаются наверх через колбэки (см. TopicValidationWrapper в MainContent.kt).
 *
 * ЧТО ВНУТРИ
 * TopicValidationScreen — корневой Composable экрана.
 * FilterMode — enum фильтра постов (все/оценено/не оценено/релевантные и т.п.).
 * SourceGroup, AuthorGroup — модели иерархической группировки постов.
 * StatsBar/StatPill/MetricPill — панель метрик (precision/recall с CI).
 * FilterChip — чип фильтра; SourceHeader/AuthorHeader — раскрываемые заголовки групп.
 * PostVoteCard/VoteChip — карточка поста с кнопками голосования.
 * buildSourceGroups — построение иерархии источник -> автор -> посты.
 * stratumColor/formatDate/authorExpandKey — вспомогательные функции.
 *
 * МЕТОД
 * Метрики качества фильтра считаются байесовским валидатором на бета-распределении
 * (BayesBetaValidator): precision ~ Beta(1+TP, 1+FP), recall ~ Beta(1+TP, 1+FN),
 * с 95% доверительными интервалами (см. ValidationMetrics).
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop (Material, LazyColumn, анимации) — UI и список с раскрытием
 * групп; remember/mutableStateMapOf — локальное состояние голосов и раскрытий.
 *
 * СВЯЗИ
 * Получает готовый список ValidationPost и колбэки от обёртки в MainContent.kt,
 * которая и пишет результаты в БД через PostDao.
 */
package com.example.lomanalyzer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lomanalyzer.analysis.topic.BayesBetaValidator
import com.example.lomanalyzer.analysis.topic.ValidationMetrics
import com.example.lomanalyzer.analysis.topic.ValidationPost
import com.example.lomanalyzer.ui.theme.AppColors
import com.example.lomanalyzer.ui.theme.SectionCard
import com.example.lomanalyzer.ui.theme.StatusBadge
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Режимы фильтрации списка постов по статусу голоса аналитика. */
private enum class FilterMode(val label: String) {
    ALL("Все"),
    UNVOTED("Не оценено"),
    VOTED("Оценено"),
    RELEVANT("Релевантные"),
    NOT_RELEVANT("Нерелевантные"),
}

/**
 * Группа постов одного источника (сообщество или личная стена).
 * @property ownerVkId vkId владельца источника (отрицательный — сообщество).
 * @property name отображаемое имя источника.
 * @property authors авторы постов внутри источника.
 */
private data class SourceGroup(
    val ownerVkId: Int,
    val name: String,
    val authors: List<AuthorGroup>,
) {
    /** Суммарное число постов во всех авторах источника. */
    val totalPosts: Int get() = authors.sumOf { it.posts.size }
    /** Суммарное число оценённых аналитиком постов во всех авторах источника. */
    val votedPosts: Int get() = authors.sumOf { it.votedCount }
}

/**
 * Группа постов одного автора внутри источника.
 * @property fromVkId vkId автора.
 * @property name имя автора.
 * @property posts посты автора (отсортированы по дате).
 * @property votedCount число постов автора, получивших голос аналитика.
 */
private data class AuthorGroup(
    val fromVkId: Int,
    val name: String,
    val posts: List<ValidationPost>,
    val votedCount: Int,
)

/** Формат даты публикации поста (московское время). */
private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    .withZone(ZoneId.of("Europe/Moscow"))

/**
 * Экран ручной валидации тематической фильтрации (этап 6).
 * @param allPosts все посты сессии для проверки.
 * @param currentThreshold текущий порог тематической релевантности.
 * @param onThresholdChanged колбэк изменения порога.
 * @param onStopPhraseAdded колбэк добавления стоп-фразы.
 * @param onVote колбэк голоса аналитика по посту (id, релевантен/нет/сброс).
 * @param onValidationComplete колбэк завершения валидации.
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
fun TopicValidationScreen(
    allPosts: List<ValidationPost>,
    currentThreshold: Float,
    onThresholdChanged: (Float) -> Unit,
    onStopPhraseAdded: (String) -> Unit,
    onVote: (postId: Int, vote: Boolean?) -> Unit,
    onValidationComplete: () -> Unit,
) {
    // Локальное состояние экрана
    val votes = remember { mutableStateMapOf<Int, Boolean?>() }        // голоса аналитика: id поста -> релевантен/нет/null
    var searchQuery by remember { mutableStateOf("") }                 // строка поиска по тексту/автору/сообществу
    var filterMode by remember { mutableStateOf(FilterMode.ALL) }      // активный фильтр статуса
    var threshold by remember { mutableStateOf(currentThreshold) }     // текущее положение слайдера порога
    var stopPhrase by remember { mutableStateOf("") }                  // вводимая стоп-фраза
    val expandedSources = remember { mutableStateMapOf<Int, Boolean>() }  // раскрытие групп-источников
    val expandedAuthors = remember { mutableStateMapOf<Long, Boolean>() } // раскрытие групп-авторов

    // Один раз при входе подгружаем сохранённые голоса аналитика из данных постов
    LaunchedEffect(Unit) {
        allPosts.forEach { post ->
            post.analystVote?.let { votes[post.id] = it }
        }
    }

    // Фильтрация постов по поиску и режиму фильтра; пересчитывается при изменении входов или голосов
    val filteredPosts = remember(allPosts, searchQuery, filterMode, votes.toMap()) {
        allPosts.filter { post ->
            val vote = votes[post.id]
            val matchesFilter = when (filterMode) {
                FilterMode.ALL -> true
                FilterMode.UNVOTED -> vote == null
                FilterMode.VOTED -> vote != null
                FilterMode.RELEVANT -> vote == true
                FilterMode.NOT_RELEVANT -> vote == false
            }
            val matchesSearch = searchQuery.isBlank() ||
                post.text.contains(searchQuery, ignoreCase = true) ||
                post.authorName.contains(searchQuery, ignoreCase = true) ||
                post.communityName.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    // Иерархическая группировка отфильтрованных постов: источник -> автор -> посты
    val sourceGroups = remember(filteredPosts, votes.toMap()) {
        buildSourceGroups(filteredPosts, votes)
    }

    // Метрики качества фильтра по голосам аналитика (precision/recall с CI); null, если голосов ещё нет
    val metrics: ValidationMetrics? = remember(votes.toMap()) {
        val voted = allPosts.filter { votes[it.id] != null }
        if (voted.isEmpty()) null
        else {
            // Пары «решение системы -> решение аналитика» подаются в байесовский валидатор
            val voteList = voted.map { it.systemRelevant to votes[it.id] }
            BayesBetaValidator.computeMetrics(voteList)
        }
    }

    // Общее число оценённых постов (для шапки и нижней панели)
    val totalVoted = votes.values.count { it != null }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // -- Header --
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.FactCheck, null, tint = AppColors.primary, modifier = Modifier.size(24.dp))
            Text("Валидация темы", style = MaterialTheme.typography.h6)
            Spacer(Modifier.weight(1f))
            StatusBadge(
                "${totalVoted} / ${allPosts.size} оценено",
                color = if (totalVoted > 0) AppColors.secondary else AppColors.textTertiary,
                backgroundColor = if (totalVoted > 0) AppColors.secondary.copy(alpha = 0.1f) else AppColors.surfaceVariant,
            )
        }

        // -- Stats bar --
        StatsBar(metrics, allPosts.size, totalVoted)

        // -- Управление: слайдер порога релевантности + поле стоп-фразы --
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Tune, null, tint = AppColors.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Порог: %.2f".format(threshold), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(12.dp))
                    // Слайдер порога: при изменении сразу прокидываем новое значение наверх (пересчёт релевантности)
                    Slider(
                        value = threshold,
                        onValueChange = { threshold = it; onThresholdChanged(it) },
                        valueRange = 0.1f..0.9f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = AppColors.primary,
                            activeTrackColor = AppColors.primary,
                        ),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = stopPhrase,
                        onValueChange = { stopPhrase = it },
                        label = { Text("Стоп-фраза") },
                        leadingIcon = { Icon(Icons.Outlined.Block, null, Modifier.size(18.dp)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    // Кнопка добавления стоп-фразы: прокидывает фразу наверх и очищает поле
                    Button(
                        onClick = {
                            if (stopPhrase.isNotBlank()) {
                                onStopPhraseAdded(stopPhrase)
                                stopPhrase = ""
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    }
                }
            }
        }

        // -- Filter bar --
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск по тексту, автору, сообществу...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f).height(44.dp),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
            )
            FilterMode.entries.forEach { mode ->
                FilterChip(mode, filterMode == mode) { filterMode = mode }
            }
        }

        // -- Основной контент: ленивый список постов, сгруппированных по источникам и авторам --
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (sourceGroups.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (allPosts.isEmpty()) "Нет данных для валидации"
                            else "Нет постов, соответствующих фильтру",
                            color = AppColors.textTertiary,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            // Для каждого источника рисуем заголовок; вложенные авторы и посты — только при раскрытии
            sourceGroups.forEach { source ->
                val sourceExpanded = expandedSources[source.ownerVkId] == true

                item(key = "source_${source.ownerVkId}") {
                    SourceHeader(
                        name = source.name,
                        totalPosts = source.totalPosts,
                        votedPosts = source.votedPosts,
                        expanded = sourceExpanded,
                        onClick = {
                            expandedSources[source.ownerVkId] = !sourceExpanded
                        },
                    )
                }

                if (sourceExpanded) {
                    source.authors.forEach { author ->
                        val authorKey = authorExpandKey(source.ownerVkId, author.fromVkId)
                        val authorExpanded = expandedAuthors[authorKey] == true

                        item(key = "author_${source.ownerVkId}_${author.fromVkId}") {
                            AuthorHeader(
                                name = author.name,
                                totalPosts = author.posts.size,
                                votedPosts = author.votedCount,
                                expanded = authorExpanded,
                                onClick = {
                                    expandedAuthors[authorKey] = !authorExpanded
                                },
                            )
                        }

                        if (authorExpanded) {
                            // Карточки постов автора с кнопками голосования
                            items(
                                items = author.posts,
                                key = { "post_${it.id}" },
                            ) { post ->
                                PostVoteCard(
                                    post = post,
                                    vote = votes[post.id],
                                    // Голос обновляет локальное состояние и сразу прокидывается наверх для сохранения
                                    onVote = { vote ->
                                        votes[post.id] = vote
                                        onVote(post.id, vote)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // -- Нижняя панель: счётчик оценённых постов и кнопка завершения валидации --
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (totalVoted > 0) {
                Text(
                    "Оценено $totalVoted из ${allPosts.size} постов",
                    fontSize = 12.sp,
                    color = AppColors.textTertiary,
                )
            } else {
                Spacer(Modifier)
            }
            Button(
                onClick = onValidationComplete,
                shape = RoundedCornerShape(10.dp),
                elevation = ButtonDefaults.elevation(defaultElevation = 2.dp),
            ) {
                Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Завершить валидацию")
            }
        }
    }
}

/**
 * Панель статистики над списком: всего постов, оценено, а также precision/recall с
 * доверительными интервалами (если есть голоса).
 * @param metrics метрики качества фильтра (null — голосов ещё нет).
 * @param totalPosts всего постов в сессии.
 * @param totalVoted число оценённых постов.
 */
@Composable
@Suppress("FunctionNaming")
private fun StatsBar(metrics: ValidationMetrics?, totalPosts: Int, totalVoted: Int) {
    Card(
        shape = RoundedCornerShape(10.dp),
        elevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            StatPill("Всего постов", totalPosts.toString())
            StatPill("Оценено", totalVoted.toString())
            if (metrics != null) {
                MetricPill("Precision", metrics.precision.mean.toFloat(), metrics.precision.ci95Lo.toFloat(), metrics.precision.ci95Hi.toFloat())
                MetricPill("Recall", metrics.recall.mean.toFloat(), metrics.recall.ci95Lo.toFloat(), metrics.recall.ci95Hi.toFloat())
            }
        }
    }
}

/** Простая «пилюля» статистики: подпись сверху, числовое значение снизу. */
@Composable
@Suppress("FunctionNaming")
private fun StatPill(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = AppColors.textTertiary)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/**
 * «Пилюля» метрики с доверительным интервалом: подпись, среднее и диапазон [lo, hi].
 * @param label название метрики (Precision/Recall).
 * @param mean точечная оценка.
 * @param lo нижняя граница 95% CI.
 * @param hi верхняя граница 95% CI.
 */
@Composable
@Suppress("FunctionNaming")
private fun MetricPill(label: String, mean: Float, lo: Float, hi: Float) {
    Column {
        Text(label, fontSize = 11.sp, color = AppColors.textTertiary)
        Text("%.2f".format(mean), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("[%.2f, %.2f]".format(lo, hi), fontSize = 10.sp, color = AppColors.textTertiary)
    }
}

/**
 * Чип фильтра статуса постов с подсветкой выбранного.
 * @param mode режим фильтра.
 * @param selected выбран ли этот режим.
 * @param onClick колбэк выбора режима.
 */
@Composable
@Suppress("FunctionNaming")
private fun FilterChip(mode: FilterMode, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) AppColors.primary.copy(alpha = 0.12f) else AppColors.surfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
    ) {
        Text(
            mode.label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) AppColors.primary else AppColors.textSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/**
 * Раскрываемый заголовок группы-источника (сообщество/стена): стрелка раскрытия,
 * имя, счётчик постов и бейдж числа оценённых.
 * @param name имя источника.
 * @param totalPosts всего постов в источнике.
 * @param votedPosts оценено постов.
 * @param expanded раскрыта ли группа.
 * @param onClick колбэк переключения раскрытия.
 */
@Composable
@Suppress("FunctionNaming")
private fun SourceHeader(
    name: String,
    totalPosts: Int,
    votedPosts: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        elevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = AppColors.primary,
                modifier = Modifier.size(20.dp),
            )
            Icon(Icons.Outlined.Groups, null, tint = AppColors.textSecondary, modifier = Modifier.size(18.dp))
            Text(
                name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$totalPosts постов",
                fontSize = 12.sp,
                color = AppColors.textTertiary,
            )
            if (votedPosts > 0) {
                StatusBadge(
                    "$votedPosts оценено",
                    color = AppColors.secondary,
                    backgroundColor = AppColors.secondary.copy(alpha = 0.1f),
                )
            }
        }
    }
}

/**
 * Раскрываемый заголовок группы-автора внутри источника (с отступом).
 * @param name имя автора.
 * @param totalPosts всего постов автора.
 * @param votedPosts оценено постов автора.
 * @param expanded раскрыта ли группа.
 * @param onClick колбэк переключения раскрытия.
 */
@Composable
@Suppress("FunctionNaming")
private fun AuthorHeader(
    name: String,
    totalPosts: Int,
    votedPosts: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = AppColors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Icon(Icons.Outlined.Person, null, tint = AppColors.textSecondary, modifier = Modifier.size(16.dp))
        Text(
            name,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "$totalPosts",
            fontSize = 11.sp,
            color = AppColors.textTertiary,
        )
        if (votedPosts > 0) {
            Text(
                "($votedPosts оц.)",
                fontSize = 11.sp,
                color = AppColors.secondary,
            )
        }
    }
}

/**
 * Карточка одного поста с метаданными (дата, страта, оценка, окно) и кнопками
 * голосования аналитика.
 * @param post пост для оценки.
 * @param vote текущий голос аналитика (true/false/null).
 * @param onVote колбэк смены голоса.
 */
@Composable
@Suppress("FunctionNaming")
private fun PostVoteCard(post: ValidationPost, vote: Boolean?, onVote: (Boolean?) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
        shape = RoundedCornerShape(10.dp),
        elevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Строка метаданных: дата, страта, оценка тематичности, метка окна и признак системной релевантности
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (post.publishedAt > 0) {
                    Text(
                        formatDate(post.publishedAt),
                        fontSize = 11.sp,
                        color = AppColors.textTertiary,
                    )
                }
                StatusBadge(
                    post.stratum,
                    color = stratumColor(post.stratum),
                    backgroundColor = stratumColor(post.stratum).copy(alpha = 0.1f),
                )
                Text("Score: %.3f".format(post.score), fontSize = 11.sp, color = AppColors.textTertiary)
                if (post.window == "BASELINE") {
                    StatusBadge(
                        "baseline",
                        color = AppColors.textTertiary,
                        backgroundColor = AppColors.surfaceVariant,
                    )
                }
                if (post.systemRelevant) {
                    Icon(Icons.Filled.CheckCircle, null, tint = AppColors.secondary.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                }
            }

            // Текст поста (обрезается до 400 символов)
            Text(
                post.text.take(400).let { if (post.text.length > 400) "$it..." else it },
                style = MaterialTheme.typography.body2,
                color = AppColors.textPrimary,
                lineHeight = 18.sp,
            )

            // Кнопки голосования: релевантен / не релевантен / пропустить (сброс голоса)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoteChip("Релевантен", vote == true, AppColors.secondary) { onVote(true) }
                VoteChip("Не релевантен", vote == false, AppColors.error) { onVote(false) }
                VoteChip("Пропустить", vote == null && post.analystVote != null, AppColors.textTertiary) { onVote(null) }
            }
        }
    }
}

/**
 * Чип-кнопка одного варианта голоса с подсветкой выбранного.
 * @param label подпись варианта.
 * @param selected выбран ли этот вариант.
 * @param color акцентный цвет варианта.
 * @param onClick колбэк выбора.
 */
@Composable
@Suppress("FunctionNaming")
private fun VoteChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) color.copy(alpha = 0.12f) else Color.Transparent,
    ) {
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text(
                label,
                fontSize = 12.sp,
                color = if (selected) color else AppColors.textTertiary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/** Цвет бейджа страты поста по уровню оценки тематичности (от уверенно-тематичного до около-нуля). */
private fun stratumColor(stratum: String): Color = when (stratum) {
    "HIGH_CONF" -> Color(0xFF2E7D32)
    "BORDERLINE_POS" -> Color(0xFFED6C02)
    "BORDERLINE_NEG" -> Color(0xFFC77700)
    "NEAR_ZERO" -> Color(0xFF9E9E9E)
    else -> Color(0xFF757575)
}

/** Форматирует дату публикации (epoch-секунды) в строку; при ошибке возвращает пустую строку. */
private fun formatDate(epochSeconds: Long): String = try {
    dateFormatter.format(Instant.ofEpochSecond(epochSeconds))
} catch (_: Exception) {
    ""
}

/**
 * Составной ключ раскрытия группы-автора: упаковывает (ownerVkId, fromVkId) в Long,
 * сдвигая ownerVkId в старшие 32 бита и кладя fromVkId в младшие. Нужен, чтобы один
 * и тот же автор в разных источниках раскрывался независимо.
 */
private fun authorExpandKey(ownerVkId: Int, fromVkId: Int): Long =
    ownerVkId.toLong().shl(32) or fromVkId.toLong().and(0xFFFFFFFFL)

/**
 * Строит иерархию группировки постов: источник (по ownerVkId) -> автор (по fromVkId)
 * -> посты автора. Внутри автора посты сортируются по убыванию даты, авторы — по
 * числу постов, источники — по суммарному числу постов.
 * @param posts отфильтрованные посты.
 * @param votes карта голосов (для подсчёта оценённых постов в каждой группе).
 */
private fun buildSourceGroups(
    posts: List<ValidationPost>,
    votes: Map<Int, Boolean?>,
): List<SourceGroup> {
    // Группируем посты по владельцу-источнику
    val byOwner = posts.groupBy { it.ownerVkId }
    return byOwner.map { (ownerVkId, ownerPosts) ->
        val communityName = ownerPosts.first().communityName.ifBlank { "ID $ownerVkId" }
        // Внутри источника группируем по автору
        val byAuthor = ownerPosts.groupBy { it.fromVkId }
        val authorGroups = byAuthor.map { (fromVkId, authorPosts) ->
            val authorName = authorPosts.first().authorName.ifBlank { "ID $fromVkId" }
            // Посты автора — от новых к старым
            val sorted = authorPosts.sortedByDescending { it.publishedAt }
            AuthorGroup(
                fromVkId = fromVkId,
                name = authorName,
                posts = sorted,
                votedCount = sorted.count { votes[it.id] != null },
            )
        }.sortedByDescending { it.posts.size } // авторы с большим числом постов выше
        SourceGroup(
            ownerVkId = ownerVkId,
            name = communityName,
            authors = authorGroups,
        )
    }.sortedByDescending { it.totalPosts } // источники с большим числом постов выше
}
