/*
 * НАЗНАЧЕНИЕ
 * Экран журнала событий сессии (раздел диплома 2.2.6). Показывает полную
 * хронологию событий выбранной сессии (прогресс, ошибки, индикаторы качества,
 * закрытые аккаунты и т.д.) с поиском по тексту и фильтром по типу события.
 *
 * ЧТО ВНУТРИ
 * SessionLogScreen — единственный Composable экрана: загрузка событий, фильтрация
 * и отрисовка ленивого списка с цветными бейджами типов.
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop (Material, LazyColumn, DropdownMenu) — UI, список и выпадающий
 * фильтр; Koin — получение ActiveSessionHolder и SessionEventDao; StateFlow +
 * collectAsState — слежение за активной сессией и версией данных.
 *
 * СВЯЗИ
 * SessionEventDao.findBySession — источник событий по активной сессии из
 * ActiveSessionHolder (таблица SessionEvents).
 */
package com.example.lomanalyzer.ui.screens

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lomanalyzer.orchestration.ActiveSessionHolder
import com.example.lomanalyzer.storage.dao.SessionEventDao
import com.example.lomanalyzer.storage.tables.SessionEvents
import com.example.lomanalyzer.ui.theme.AppColors
import com.example.lomanalyzer.ui.theme.EmptyStateMessage
import com.example.lomanalyzer.ui.theme.ScreenHeader
import com.example.lomanalyzer.ui.theme.SectionCard
import org.koin.java.KoinJavaComponent.get
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Экран журнала событий сессии (раздел диплома 2.2.6).
 * Полная хронология событий с поиском и фильтрацией по типу.
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
fun SessionLogScreen() {
    // Зависимости из Koin: держатель активной сессии и DAO событий
    val sessionHolder = remember { get<ActiveSessionHolder>(ActiveSessionHolder::class.java) }
    val sessionEventDao = remember { get<SessionEventDao>(SessionEventDao::class.java) }
    // Активная сессия, версия данных и локальные фильтры (поиск, тип события)
    val sessionId by sessionHolder.sessionId.collectAsState()
    val dataVersion by sessionHolder.dataVersion.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<String?>(null) }

    // Загрузка всех событий активной сессии; пересчитывается при смене сессии/версии данных
    val events = remember(sessionId, dataVersion) {
        val sid = sessionId ?: return@remember emptyList()
        sessionEventDao.findBySession(sid)
    }

    // Применение фильтров: по типу события и по подстроке (в сообщении или типе)
    val filtered = remember(events, searchQuery, typeFilter) {
        events.filter { event ->
            val matchesType = typeFilter == null || event[SessionEvents.eventType] == typeFilter
            val matchesSearch = searchQuery.isBlank() ||
                (event[SessionEvents.message] ?: "").contains(searchQuery, ignoreCase = true) ||
                event[SessionEvents.eventType].contains(searchQuery, ignoreCase = true)
            matchesType && matchesSearch
        }
    }

    // Уникальные типы событий (для выпадающего фильтра), отсортированные по алфавиту
    val eventTypes = remember(events) { events.map { it[SessionEvents.eventType] }.distinct().sorted() }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(title = "Журнал сессии", subtitle = "${filtered.size} из ${events.size} событий")

        // Поиск по сообщениям + выпадающий фильтр по типу события
        SectionCard {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск по сообщениям...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f).heightIn(max = 48.dp),
                    singleLine = true,
                )
                // Выпадающий список фильтра по типу события (пункт «Все типы» сбрасывает фильтр)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(typeFilter ?: "Все типы", fontSize = 12.sp)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(onClick = { typeFilter = null; expanded = false }) {
                            Text("Все типы")
                        }
                        eventTypes.forEach { type ->
                            DropdownMenuItem(onClick = { typeFilter = type; expanded = false }) {
                                Text(type, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Список событий (или заглушка, если событий нет под текущим фильтром)
        if (filtered.isEmpty()) {
            EmptyStateMessage(title = "Нет событий", subtitle = "Журнал заполнится при выполнении сессии")
        } else {
            // Форматтеры времени (с миллисекундами) и даты для каждой строки журнала
            val dtFmt = remember { DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault()) }
            val dateFmt = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault()) }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(filtered) { _, event ->
                    val ts = event[SessionEvents.createdAt]
                    val type = event[SessionEvents.eventType]
                    val msg = event[SessionEvents.message] ?: ""
                    val details = event[SessionEvents.details]

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White,
                        elevation = 0.5.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Время и дата события
                            Column(modifier = Modifier.width(80.dp)) {
                                Text(dtFmt.format(Instant.ofEpochMilli(ts)), fontSize = 11.sp, color = AppColors.textTertiary)
                                Text(dateFmt.format(Instant.ofEpochMilli(ts)), fontSize = 10.sp, color = AppColors.textTertiary)
                            }
                            // Цветной бейдж типа события (ошибки — красный, прогресс — основной цвет и т.д.)
                            val typeColor = when (type) {
                                "ERROR", "API_ERROR" -> Color(0xFFC62828)
                                "PROGRESS" -> AppColors.primary
                                "INFO" -> Color(0xFF2E7D32)
                                "CLOSED_ACCOUNT" -> Color(0xFFE65100)
                                "QUALITY_INDICATOR" -> Color(0xFF6A1B9A)
                                else -> AppColors.textSecondary
                            }
                            Surface(shape = RoundedCornerShape(4.dp), color = typeColor.copy(alpha = 0.1f)) {
                                Text(type, fontSize = 10.sp, color = typeColor, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            // Сообщение события и необязательные подробности
                            Column(modifier = Modifier.weight(1f)) {
                                Text(msg, fontSize = 12.sp, color = AppColors.textPrimary)
                                if (details != null) {
                                    Text(details, fontSize = 11.sp, color = AppColors.textTertiary, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
