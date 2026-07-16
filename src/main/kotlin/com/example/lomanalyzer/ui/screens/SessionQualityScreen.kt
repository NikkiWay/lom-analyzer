/*
 * НАЗНАЧЕНИЕ
 * Экран индикаторов качества сессии (этап 8, разделы диплома 2.2.6 и 2.2.8).
 * Показывает рассчитанные при оценке качества индикаторы (покрытие, precision/recall,
 * дедупликация, стабильность нормализации и др.) со статусом «пройден/пограничный/
 * не пройден» и числовым значением.
 *
 * ЧТО ВНУТРИ
 * SessionQualityScreen — корневой Composable: читает события качества и парсит их.
 * QualityIndicatorCard — карточка одного индикатора (название, значение, статус, описание).
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop (Material, FlowRow) — UI с адаптивной раскладкой карточек; Koin —
 * получение ActiveSessionHolder и SessionEventDao; StateFlow + collectAsState —
 * слежение за активной сессией и версией данных.
 *
 * СВЯЗИ
 * Данные берутся из таблицы session_event (события типа QUALITY_INDICATOR через
 * SessionEventDao); их пишет этап оценки качества пайплайна. Формат сообщения
 * события: «Название: СТАТУС (значение)».
 */
package com.example.lomanalyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

/**
 * Экран индикаторов качества сессии (разделы диплома 2.2.6, 2.2.8).
 * Показывает 4 основных и технические индикаторы из оценки качества.
 * Данные берутся из таблицы session_event (события QUALITY_INDICATOR).
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
fun SessionQualityScreen() {
    // Зависимости из Koin: держатель активной сессии и DAO событий
    val sessionHolder = remember { get<ActiveSessionHolder>(ActiveSessionHolder::class.java) }
    val sessionEventDao = remember { get<SessionEventDao>(SessionEventDao::class.java) }
    val sessionId by sessionHolder.sessionId.collectAsState()
    val dataVersion by sessionHolder.dataVersion.collectAsState()

    /** Распарсенный индикатор качества: название, значение, статус и описание. */
    data class QualityItem(val name: String, val value: Float, val status: String, val description: String)

    // Загрузка и разбор индикаторов из событий QUALITY_INDICATOR активной сессии
    val indicators = remember(sessionId, dataVersion) {
        val sid = sessionId ?: return@remember emptyList<QualityItem>()
        val events = sessionEventDao.findBySession(sid)
            .filter { it[SessionEvents.eventType] == "QUALITY_INDICATOR" }
        events.mapNotNull { event ->
            val msg = event[SessionEvents.message] ?: return@mapNotNull null
            // Разбор сообщения формата «Название: СТАТУС (0.123)»
            val colonIdx = msg.indexOf(':')
            if (colonIdx < 0) return@mapNotNull null
            val name = msg.substring(0, colonIdx).trim()                  // часть до двоеточия — название
            val rest = msg.substring(colonIdx + 1).trim()                // часть после двоеточия
            val status = rest.substringBefore(" (").trim()               // статус — до открывающей скобки
            val valueStr = rest.substringAfter("(", "0").substringBefore(")") // значение — внутри скобок
            QualityItem(name, valueStr.toFloatOrNull() ?: 0f, status, event[SessionEvents.details] ?: "")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(title = "Качество сессии", subtitle = "${indicators.size} индикаторов")

        if (indicators.isEmpty()) {
            EmptyStateMessage(
                title = "Нет данных о качестве",
                subtitle = "Индикаторы появятся после завершения анализа",
            )
        } else {
            // Карточки индикаторов в адаптивной сетке (FlowRow)
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Speed, null, tint = AppColors.primary, modifier = Modifier.size(20.dp))
                    Text("Индикаторы качества", style = MaterialTheme.typography.subtitle1)
                }

                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    for (item in indicators) {
                        QualityIndicatorCard(item.name, item.value, item.status, item.description,
                            Modifier.width(280.dp))
                    }
                }
            }
        }
    }
}

/**
 * Карточка одного индикатора качества: название, бейдж статуса, числовое значение
 * и краткое описание.
 * @param name название индикатора.
 * @param value числовое значение [0..1].
 * @param status статус (PASSED/BORDERLINE/иное — не пройден).
 * @param description пояснение.
 * @param modifier модификатор компоновки (обычно фиксированная ширина).
 */
@Composable
@Suppress("FunctionNaming")
private fun QualityIndicatorCard(
    name: String, value: Float, status: String, description: String, modifier: Modifier,
) {
    // Цвет статуса: зелёный — пройден, оранжевый — пограничный, красный — не пройден
    val statusColor = when (status) {
        "PASSED" -> Color(0xFF2E7D32)
        "BORDERLINE" -> Color(0xFFE65100)
        else -> Color(0xFFC62828)
    }
    // Русская подпись статуса
    val statusLabel = when (status) {
        "PASSED" -> "Пройден"
        "BORDERLINE" -> "Пограничный"
        else -> "Не пройден"
    }

    Surface(shape = RoundedCornerShape(10.dp), color = statusColor.copy(alpha = 0.06f), modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(4.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(statusLabel, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Text("%.3f".format(value), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = statusColor)
            Text(description, fontSize = 11.sp, color = AppColors.textTertiary)
        }
    }
}
