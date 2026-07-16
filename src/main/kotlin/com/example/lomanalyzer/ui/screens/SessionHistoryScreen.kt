/*
 * НАЗНАЧЕНИЕ
 * Экран истории сессий анализа. Выводит список ранее созданных сессий с их
 * статусом и оценкой качества; позволяет выбрать завершённую сессию для просмотра
 * результатов (делает её активной и переходит на дашборд) или удалить её в корзину.
 *
 * ЧТО ВНУТРИ
 * SessionSummary — модель краткой сводки по сессии для списка.
 * SessionHistoryScreen — корневой Composable экрана.
 * loadSessions — загрузка и маппинг сессий из БД в SessionSummary.
 * SessionCard — карточка одной сессии (статус, имя, тема, дата, качество, действия).
 * StatusBadge — значок статуса сессии.
 * qualityColor — цвет индикатора качества по значению.
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop (Material, LazyColumn, AlertDialog) — UI и диалог подтверждения;
 * Koin — получение SessionManager, SessionDao, ActiveSessionHolder, AppNavigator;
 * StateFlow + collectAsState — слежение за активной сессией; LaunchedEffect —
 * однократная загрузка списка.
 *
 * СВЯЗИ
 * SessionManager.listSessions/SessionDao — источник сессий и мягкое удаление;
 * ActiveSessionHolder.set делает выбранную сессию активной; AppNavigator переводит
 * на LOM_DASHBOARD для просмотра результатов.
 */
package com.example.lomanalyzer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lomanalyzer.orchestration.ActiveSessionHolder
import com.example.lomanalyzer.orchestration.SessionManager
import com.example.lomanalyzer.orchestration.SessionStatus
import com.example.lomanalyzer.storage.dao.SessionDao
import com.example.lomanalyzer.storage.tables.AnalysisSessions
import com.example.lomanalyzer.ui.navigation.AppNavigator
import com.example.lomanalyzer.ui.navigation.NavRoute
import com.example.lomanalyzer.ui.theme.AppColors
import com.example.lomanalyzer.ui.theme.EmptyStateMessage
import com.example.lomanalyzer.ui.theme.ScreenHeader
import org.koin.java.KoinJavaComponent.get
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * Краткая сводка по сессии для отображения в списке истории.
 * @property id внутренний id сессии.
 * @property name название сессии.
 * @property topicQuery тема исследования.
 * @property status текущий статус выполнения.
 * @property qualityScore интегральная оценка качества (null, если не рассчитана).
 * @property createdAt время создания (epoch-мс).
 * @property updatedAt время последнего обновления (epoch-мс).
 */
private data class SessionSummary(
    val id: Int,
    val name: String,
    val topicQuery: String,
    val status: SessionStatus,
    val qualityScore: Float?,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Экран истории сессий: список сессий с выбором активной и удалением в корзину.
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
fun SessionHistoryScreen() {
    // Зависимости из Koin: менеджер/DAO сессий, держатель активной сессии и навигатор
    val sessionManager = remember { get<SessionManager>(SessionManager::class.java) }
    val sessionDao = remember { get<SessionDao>(SessionDao::class.java) }
    val sessionHolder = remember { get<ActiveSessionHolder>(ActiveSessionHolder::class.java) }
    val navigator = remember { get<AppNavigator>(AppNavigator::class.java) }

    // Активная сессия (для пометки «активная»), список сессий и id сессии для подтверждения удаления
    val activeSessionId by sessionHolder.sessionId.collectAsState()
    var sessions by remember { mutableStateOf(emptyList<SessionSummary>()) }
    var showDeleteConfirm by remember { mutableStateOf<Int?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Однократная загрузка списка сессий при входе на экран
    LaunchedEffect(Unit) {
        sessions = loadSessions(sessionManager)
    }

    val fmt = remember {
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = "История сессий",
            subtitle = "Выберите сессию для просмотра результатов",
        )

        if (sessions.isEmpty()) {
            EmptyStateMessage(
                title = "Нет сессий",
                subtitle = "Создайте новую сессию в разделе «Настройка»",
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(sessions, key = { it.id }) { session ->
                    val isActive = activeSessionId == session.id

                    SessionCard(
                        session = session,
                        isActive = isActive,
                        fmt = fmt,
                        // Клик по сессии: делаем её активной, обновляем данные и открываем дашборд
                        onClick = {
                            sessionHolder.set(session.id)
                            sessionHolder.notifyDataChanged()
                            navigator.navigate(NavRoute.LOM_DASHBOARD)
                        },
                        onDelete = { showDeleteConfirm = session.id },
                    )
                }
            }
        }
    }

    // Диалог подтверждения мягкого удаления (перемещение в корзину)
    showDeleteConfirm?.let { sessionId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Удалить сессию?") },
            text = {
                Text("Сессия #$sessionId будет перемещена в корзину. Вы сможете восстановить её позже.")
            },
            confirmButton = {
                TextButton(onClick = {
                    // Мягкое удаление: помечаем сессию удалённой и перезагружаем список.
                    // Обе операции блокирующие, поэтому уходят в IO-диспетчер.
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) { sessionDao.softDelete(sessionId) }
                        sessions = loadSessions(sessionManager)
                        // Если удалили активную сессию — сбрасываем активную
                        if (activeSessionId == sessionId) {
                            sessionHolder.clear()
                        }
                    }
                    showDeleteConfirm = null
                }) {
                    Text("Удалить", color = AppColors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Отмена")
                }
            },
            shape = RoundedCornerShape(12.dp),
        )
    }
}

/**
 * Загружает сессии через SessionManager и маппит строки БД в модели SessionSummary.
 *
 * Выборка выполняется в Dispatchers.IO: listSessions() — блокирующая
 * JDBC-транзакция, а вызывающий код работает в UI-потоке композиции.
 */
private suspend fun loadSessions(sessionManager: SessionManager): List<SessionSummary> =
    withContext(Dispatchers.IO) {
        sessionManager.listSessions().map { row ->
            SessionSummary(
                id = row[AnalysisSessions.id].value,
                name = row[AnalysisSessions.name],
                topicQuery = row[AnalysisSessions.topicQuery],
                // Статус из строки БД; при неизвестном значении откатываемся на CREATED
                status = try {
                    SessionStatus.valueOf(row[AnalysisSessions.status])
                } catch (_: Exception) {
                    SessionStatus.CREATED
                },
                qualityScore = row[AnalysisSessions.sessionQualityScore],
                createdAt = row[AnalysisSessions.createdAt],
                updatedAt = row[AnalysisSessions.updatedAt],
            )
        }
    }

/**
 * Карточка одной сессии в списке истории: значок статуса, имя, тема, дата,
 * оценка качества и кнопки открытия/удаления.
 * @param session сводка по сессии.
 * @param isActive является ли сессия текущей активной.
 * @param fmt форматтер даты создания.
 * @param onClick колбэк открытия сессии.
 * @param onDelete колбэк удаления сессии.
 */
@Composable
@Suppress("FunctionNaming")
private fun SessionCard(
    session: SessionSummary,
    isActive: Boolean,
    fmt: DateTimeFormatter,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    // Открыть можно только сессии в «терминальном» состоянии (завершена/прервана/неполная)
    val isClickable = session.status == SessionStatus.COMPLETED
            || session.status == SessionStatus.FAILED
            || session.status == SessionStatus.CANCELLED
            || session.status == SessionStatus.INCOMPLETE

    val borderColor = when {
        isActive -> AppColors.primary
        else -> AppColors.border
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (isClickable) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(10.dp),
        elevation = if (isActive) 3.dp else 1.dp,
        border = if (isActive) {
            androidx.compose.foundation.BorderStroke(2.dp, AppColors.primary)
        } else null,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status icon
            StatusBadge(session.status)

            Spacer(Modifier.width(14.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        session.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AppColors.primaryLight,
                        ) {
                            Text(
                                "активная",
                                fontSize = 10.sp,
                                color = AppColors.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        session.topicQuery,
                        fontSize = 12.sp,
                        color = AppColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp),
                    )
                    Text(
                        fmt.format(Instant.ofEpochMilli(session.createdAt)),
                        fontSize = 12.sp,
                        color = AppColors.textTertiary,
                    )
                }

                // Оценка качества (в процентах) — показываем только если она рассчитана
                session.qualityScore?.let { score ->
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Speed, null,
                            tint = qualityColor(score),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            "Качество: ${"%.1f".format(score * 100)}%",
                            fontSize = 11.sp,
                            color = qualityColor(score),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isClickable) {
                    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Visibility, "Открыть",
                            tint = AppColors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Delete, "Удалить",
                        tint = AppColors.textTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Значок статуса сессии: иконка и цвет соответствуют состоянию выполнения.
 * @param status статус сессии.
 */
@Composable
@Suppress("FunctionNaming")
private fun StatusBadge(status: SessionStatus) {
    // Сопоставление статуса с парой «иконка -> цвет»
    val (icon, color) = when (status) {
        SessionStatus.COMPLETED -> Icons.Filled.CheckCircle to AppColors.success
        SessionStatus.ANALYZING, SessionStatus.COLLECTING -> Icons.Filled.Sync to AppColors.primary
        SessionStatus.FAILED -> Icons.Filled.Error to AppColors.error
        SessionStatus.CANCELLED -> Icons.Filled.Cancel to AppColors.warning
        SessionStatus.INCOMPLETE -> Icons.Filled.Warning to AppColors.warning
        SessionStatus.PAUSED_PENDING_RECOVERY -> Icons.Filled.Pause to AppColors.warning
        SessionStatus.CREATED -> Icons.Outlined.HourglassEmpty to AppColors.textTertiary
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, status.name, tint = color, modifier = Modifier.size(22.dp))
        }
    }
}

/** Цвет индикатора качества: зелёный (>=0.7), жёлтый (>=0.4), красный (ниже). */
private fun qualityColor(score: Float): Color = when {
    score >= 0.7f -> AppColors.success
    score >= 0.4f -> AppColors.warning
    else -> AppColors.error
}
