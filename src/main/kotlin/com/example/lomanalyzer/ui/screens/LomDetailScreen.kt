/*
 * НАЗНАЧЕНИЕ
 * Детальная карточка одного автора (открывается кликом по строке дашборда).
 * Показывает назначенную роль, атрибуты позиции и отклика аудитории, индикатор
 * достаточности данных, все оценки по 4 осям влияния (Приложение Е.4) и список
 * тематических публикаций автора с тональностью.
 *
 * ЧТО ВНУТРИ
 * LomDetailScreen — корневой Composable карточки.
 * DetailHeader — заголовок секции с иконкой.
 * ScoreRow — строка «название оценки -> значение».
 * PostCard — карточка одной публикации (дата, метрики, тональность, текст).
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop (Material) — UI; Koin — получение навигатора, держателя сессии и
 * DAO (LomScoreDao/AuthorDao/CompositeDao/PostDao/SentimentResultDao); StateFlow +
 * collectAsState — слежение за выбранным автором, сессией и версией данных; Exposed
 * ResultRow — строки результатов запросов.
 *
 * СВЯЗИ
 * Берёт id автора из AppNavigator.selectedAuthorId; роли/позиция/отклик — из
 * AuthorRoles через CompositeDao; оценки — из LomScores; посты — из PostDao
 * (только тематически релевантные); тональность поста — из SentimentResultDao.
 * Кнопка «Назад» возвращает на дашборд.
 */
package com.example.lomanalyzer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lomanalyzer.orchestration.ActiveSessionHolder
import com.example.lomanalyzer.storage.dao.*
import com.example.lomanalyzer.storage.tables.SentimentEntityType
import com.example.lomanalyzer.storage.tables.*
import com.example.lomanalyzer.ui.components.*
import com.example.lomanalyzer.ui.navigation.AppNavigator
import com.example.lomanalyzer.ui.theme.AppColors
import com.example.lomanalyzer.ui.theme.SectionCard
import org.koin.java.KoinJavaComponent.get
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.ResultRow

/**
 * Согласованный снимок данных по одному автору, прочитанный за один заход в БД.
 *
 * @param author строка профиля автора или null, если автор не выбран/не найден.
 * @param lom строка с 11 оценками для пары (сессия, автор).
 * @param ciMap доверительные интервалы бутстрапа: имя оценки -> CI.
 * @param roleRow строка роли и атрибутов автора в рамках сессии.
 * @param posts тематически релевантные публикации автора.
 * @param sentimentByPostId метки тональности постов: post.id -> метка.
 */
private data class AuthorDetailSnapshot(
    val author: ResultRow? = null,
    val lom: ResultRow? = null,
    val ciMap: Map<String, CiInfo> = emptyMap(),
    val roleRow: ResultRow? = null,
    val posts: List<ResultRow> = emptyList(),
    val sentimentByPostId: Map<Int, String> = emptyMap(),
)

/**
 * Детальная карточка автора: роль, атрибуты, оценки по 4 осям и публикации.
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
fun LomDetailScreen() {
    // Зависимости из Koin: навигатор, держатель сессии и DAO результатов/постов/тональности
    val navigator = remember { get<AppNavigator>(AppNavigator::class.java) }
    val sessionHolder = remember { get<ActiveSessionHolder>(ActiveSessionHolder::class.java) }
    val lomScoreDao = remember { get<LomScoreDao>(LomScoreDao::class.java) }
    val authorDao = remember { get<AuthorDao>(AuthorDao::class.java) }
    val compositeDao = remember { get<CompositeDao>(CompositeDao::class.java) }
    val postDao = remember { get<PostDao>(PostDao::class.java) }
    val sentimentDao = remember { get<SentimentResultDao>(SentimentResultDao::class.java) }
    val bootstrapDao = remember { get<BootstrapIntervalDao>(BootstrapIntervalDao::class.java) }

    // Реактивное состояние: какой автор выбран, какая сессия активна, версия данных
    val authorId by navigator.selectedAuthorId.collectAsState()
    val sessionId by sessionHolder.sessionId.collectAsState()
    val dataVersion by sessionHolder.dataVersion.collectAsState()

    // Все данные экрана читаются одним заходом в Dispatchers.IO: DAO выполняют
    // блокирующие JDBC-транзакции, которые нельзя вызывать в потоке composition.
    // Перезапуск — при смене сессии, автора или версии данных.
    val detail by produceState(AuthorDetailSnapshot(), sessionId, authorId, dataVersion) {
        val sid = sessionId
        val aid = authorId
        if (sid == null || aid == null) {
            value = AuthorDetailSnapshot()
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            val authorRow = authorDao.findById(aid)
            val postRows = authorRow?.get(Authors.vkId)?.let { vkId ->
                postDao.findBySessionAndFromId(sid, vkId).filter { it[Posts.isTopicRelevant] == true }
            } ?: emptyList()

            AuthorDetailSnapshot(
                author = authorRow,
                // 11 оценок (LomScores) для пары (сессия, автор)
                lom = lomScoreDao.findBySessionAndAuthor(sid, aid),
                // Доверительные интервалы бутстрапа: карта «имя оценки -> CI». Заполнены
                // только оценки, считавшиеся по выборке (ER_bg, ER_top, Reach, доли тональности).
                ciMap = bootstrapDao.findBySessionAndAuthor(sid, aid).associate { row ->
                    row[BootstrapIntervals.scoreName] to CiInfo(
                        lo = row[BootstrapIntervals.ciLo],
                        hi = row[BootstrapIntervals.ciHi],
                        procedure = row[BootstrapIntervals.procedureType],
                        iterations = row[BootstrapIntervals.iterations],
                    )
                },
                // Строка роли/атрибутов автора (AuthorRoles) в рамках сессии
                roleRow = compositeDao.findRolesBySession(sid)
                    .firstOrNull { it[AuthorRoles.authorId].value == aid },
                posts = postRows,
                // Тональность показываемых публикаций — одной картой. Точечный запрос
                // на каждый пост в цикле отрисовки давал отдельное обращение к БД (N+1).
                sentimentByPostId = sentimentDao.findAllAsMap(SentimentEntityType.POST),
            )
        }
    }

    val author = detail.author
    val lom = detail.lom
    val ciMap = detail.ciMap
    val roleRow = detail.roleRow
    val posts = detail.posts

    // Отображаемое имя автора (имя + фамилия) с запасным вариантом по id
    val authorName = author?.let {
        listOfNotNull(it[Authors.firstName], it[Authors.lastName]).joinToString(" ")
    }?.ifBlank { "Author #$authorId" } ?: "Author #${authorId ?: "-"}"

    // Роль и атрибуты переводим из enum-кодов БД в русские названия (с откатом на код, если перевод не найден)
    val role = roleRow?.get(AuthorRoles.baseRole)?.let { roleName ->
        com.example.lomanalyzer.core.BaseRole.entries.firstOrNull { it.name == roleName }?.russianName ?: roleName
    } ?: "—"
    val position = roleRow?.get(AuthorRoles.positionAttr)?.let { attr ->
        com.example.lomanalyzer.core.AuthorPosition.entries.firstOrNull { it.name == attr }?.russianName ?: attr
    } ?: "—"
    val response = roleRow?.get(AuthorRoles.responseAttr)?.let { attr ->
        com.example.lomanalyzer.core.AudienceResponse.entries.firstOrNull { it.name == attr }?.russianName ?: attr
    } ?: "—"
    // Достаточность данных (надёжность оценки): RELIABLE / PRELIMINARY / UNRELIABLE
    val sufficiency = roleRow?.get(AuthorRoles.sufficiency) ?: "—"

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Шапка карточки: кнопка «Назад» (на дашборд) и имя автора
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = { navigator.back() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = AppColors.textSecondary)
            }
            Column {
                Text(authorName, style = MaterialTheme.typography.h5)
                Text("Карточка автора", style = MaterialTheme.typography.body2, color = AppColors.textSecondary)
            }
        }

        // Роль + атрибуты (позиция автора и отклик аудитории) в три колонки
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(modifier = Modifier.weight(1f)) {
                DetailHeader(Icons.Outlined.Badge, "Роль")
                RoleCombinationBadge(role)
            }
            SectionCard(modifier = Modifier.weight(1f)) {
                DetailHeader(Icons.Outlined.RecordVoiceOver, "Позиция")
                Text(position, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
            SectionCard(modifier = Modifier.weight(1f)) {
                DetailHeader(Icons.Outlined.Forum, "Отклик аудитории")
                Text(response, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
        }

        // Индикатор достаточности данных: цвет и подпись по уровню надёжности оценки
        SectionCard {
            DetailHeader(Icons.Outlined.Verified, "Достаточность данных")
            val suffColor = when (sufficiency) {
                "RELIABLE" -> Color(0xFF2E7D32)
                "PRELIMINARY" -> Color(0xFFE65100)
                else -> Color(0xFFC62828)
            }
            val suffLabel = when (sufficiency) {
                "RELIABLE" -> "Оценка надёжна"
                "PRELIMINARY" -> "Оценка предварительна"
                "UNRELIABLE" -> "Оценка ненадёжна"
                else -> sufficiency
            }
            Text(suffLabel, color = suffColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

        // Оценки по 4 осям влияния (по одной секции на ось; значения из LomScores)
        SectionCard {
            DetailHeader(Icons.Outlined.Analytics, "Ось 1: Структурное влияние")
            ScoreRow("Aud (аудитория)", lom?.get(LomScores.aud))
            ScoreRow("Age (длительность)", lom?.get(LomScores.age))
            ScoreRow("ER_bg (фоновый ER)", lom?.get(LomScores.erBg), ci = ciMap["er_bg"], fractionScale = true)
        }
        SectionCard {
            DetailHeader(Icons.Outlined.Topic, "Ось 2: Тематическая активность")
            ScoreRow("TopVol (публикаций)", lom?.get(LomScores.topVol)?.toFloat())
            ScoreRow("TopFocus (доля темы)", lom?.get(LomScores.topFocus))
            ScoreRow("Reach (охват)", lom?.get(LomScores.reach), ci = ciMap["reach"])
        }
        SectionCard {
            DetailHeader(Icons.Outlined.ThumbsUpDown, "Ось 3: Позиция автора")
            ScoreRow("Поддержка", lom?.get(LomScores.posPositive), ci = ciMap["pos_positive"], fractionScale = true)
            ScoreRow("Нейтрально", lom?.get(LomScores.posNeutral))
            ScoreRow("Критика", lom?.get(LomScores.posNegative), ci = ciMap["pos_negative"], fractionScale = true)
        }
        SectionCard {
            DetailHeader(Icons.Outlined.ChatBubbleOutline, "Ось 4: Отклик аудитории")
            ScoreRow("ER_top (тематич. ER)", lom?.get(LomScores.erTop), ci = ciMap["er_top"], fractionScale = true)
            ScoreRow("Одобрение", lom?.get(LomScores.respPositive), ci = ciMap["resp_positive"], fractionScale = true)
            ScoreRow("Нейтрально", lom?.get(LomScores.respNeutral))
            ScoreRow("Критика", lom?.get(LomScores.respNegative), ci = ciMap["resp_negative"], fractionScale = true)
        }

        // Публикации автора (тематические). Показываем не более 50, для каждой подтягиваем тональность
        SectionCard {
            DetailHeader(Icons.Outlined.Description, "Публикации (${posts.size})")
            if (posts.isEmpty()) {
                Text("Нет публикаций", fontSize = 13.sp, color = AppColors.textTertiary, modifier = Modifier.padding(vertical = 8.dp))
            } else {
                val dtFmt = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()) }
                posts.take(50).forEach { post ->
                    // Тональность поста берём из уже загруженной карты (без запроса к БД при отрисовке)
                    val sentimentLabel = detail.sentimentByPostId[post[Posts.id].value]
                    PostCard(post, dtFmt, sentimentLabel)
                }
                // Если публикаций больше 50, показываем подпись с остатком
                if (posts.size > 50) {
                    Text("... и ещё ${posts.size - 50}", fontSize = 12.sp, color = AppColors.textTertiary)
                }
            }
        }
    }
}

/**
 * Заголовок секции детальной карточки: иконка + текст.
 * @param icon иконка секции.
 * @param title текст заголовка.
 */
@Composable
@Suppress("FunctionNaming")
private fun DetailHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 4.dp)) {
        Icon(icon, null, tint = AppColors.primary, modifier = Modifier.size(18.dp))
        Text(title, style = MaterialTheme.typography.subtitle1)
    }
}

/**
 * Доверительный интервал бутстрапа для одной оценки (для отображения в карточке).
 * @param lo нижняя граница CI.
 * @param hi верхняя граница CI.
 * @param procedure тип процедуры бутстрапа ("one_level" / "two_level").
 * @param iterations число итераций бутстрапа.
 */
private data class CiInfo(
    val lo: Float,
    val hi: Float,
    val procedure: String,
    val iterations: Int,
)

/**
 * Строка одной оценки: подпись слева, числовое значение справа (или «—», если null).
 * Если для оценки рассчитан доверительный интервал (ci != null), справа появляется
 * стрелка-раскрытие; по клику под строкой показывается 95% ДИ бутстрапа.
 * @param label название оценки.
 * @param value значение оценки (форматируется до 4 знаков); null — нет данных.
 * @param ci доверительный интервал бутстрапа; null — CI не рассчитывался (точечная оценка).
 * @param fractionScale значение лежит в [0..1] — тогда CI рисуется полосой CiBar;
 *   иначе (например, Reach) CI показывается только текстом.
 */
@Composable
@Suppress("FunctionNaming")
private fun ScoreRow(
    label: String,
    value: Float?,
    ci: CiInfo? = null,
    fractionScale: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .then(if (ci != null) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 13.sp, color = AppColors.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value?.let { "%.4f".format(it) } ?: "—", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                // Стрелка-раскрытие показываем только если для оценки есть доверительный интервал
                if (ci != null) {
                    Icon(
                        if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (expanded) "Скрыть доверительный интервал" else "Показать доверительный интервал",
                        tint = AppColors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        // Раскрывающийся блок с доверительным интервалом
        if (ci != null) {
            AnimatedVisibility(visible = expanded) {
                CiDetail(value ?: 0f, ci, fractionScale)
            }
        }
    }
}

/**
 * Раскрывающийся блок с доверительным интервалом оценки: визуальная полоса (для
 * долей в [0..1]) или текстовый интервал (для величин вне [0..1]), плюс подпись
 * с методом бутстрапа.
 * @param value точечная оценка (для позиции маркера на полосе).
 * @param ci доверительный интервал.
 * @param fractionScale значение в [0..1] — рисуем полосу CiBar.
 */
@Composable
@Suppress("FunctionNaming")
private fun CiDetail(value: Float, ci: CiInfo, fractionScale: Boolean) {
    // Вырожденный интервал: границы совпали — бутстрап не выявил разброса
    // (например, все наблюдения одной тональности → доля всегда одинакова).
    val degenerate = (ci.hi - ci.lo) < 1e-6f
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // Полосу рисуем только для долей в [0..1]; подпись на ней отключаем — границы дублирует строка ниже
        if (fractionScale) {
            CiBar(value = value, ciLo = ci.lo, ciHi = ci.hi, showLabel = false)
        }
        // Границы интервала: 4 знака (как и значение оценки), разделитель — «;»
        Text(
            "95% ДИ: " + "[%.4f; %.4f]".format(ci.lo, ci.hi),
            fontSize = 11.sp,
            color = AppColors.textTertiary,
            fontWeight = FontWeight.Medium,
        )
        val method = if (ci.procedure == "two_level") {
            "двухуровневый бутстрап (300×100)"
        } else {
            "одноуровневый бутстрап (B=${ci.iterations})"
        }
        // Для вырожденного интервала поясняем, почему он стянут в точку
        val note = if (degenerate) {
            "Интервал вырожден: во всех бутстрап-выборках значение совпало (нет разброса) · $method"
        } else {
            "Метод: $method"
        }
        Text(note, fontSize = 10.sp, color = AppColors.textTertiary)
    }
}

/**
 * Карточка одной публикации: дата, метрики (лайки/репосты/комментарии), метка
 * тональности (если не нейтральная) и обрезанный текст.
 * @param post строка результата запроса по таблице Posts.
 * @param dtFmt форматтер даты публикации.
 * @param sentiment строка результата тональности (может быть null).
 */
@Composable
@Suppress("FunctionNaming")
private fun PostCard(post: org.jetbrains.exposed.sql.ResultRow, dtFmt: DateTimeFormatter, sentLabel: String?) {
    // Текст (обрезан до 300 символов) и дата публикации
    val text = post[Posts.text]?.take(300) ?: ""
    val date = dtFmt.format(Instant.ofEpochMilli(post[Posts.publishedAt]))

    Surface(shape = RoundedCornerShape(8.dp), color = AppColors.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(date, fontSize = 11.sp, color = AppColors.textTertiary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${post[Posts.likes]}", fontSize = 11.sp, color = AppColors.textSecondary)
                    Text("${post[Posts.reposts]}", fontSize = 11.sp, color = AppColors.textSecondary)
                    Text("${post[Posts.comments]}", fontSize = 11.sp, color = AppColors.textSecondary)
                }
            }
            // Метку тональности показываем только для не нейтральных постов (зелёный — позитив, красный — негатив)
            if (sentLabel != null && sentLabel != "NEUTRAL") {
                val c = if (sentLabel == "POSITIVE") Color(0xFF2E7D32) else Color(0xFFC62828)
                Text(sentLabel, fontSize = 10.sp, color = c, fontWeight = FontWeight.Medium)
            }
            if (text.isNotBlank()) {
                Text(if (text.length >= 300) "$text..." else text, fontSize = 12.sp, color = AppColors.textPrimary)
            }
        }
    }
}
