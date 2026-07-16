/*
 * НАЗНАЧЕНИЕ
 * Главный экран результатов анализа (этап 10, отображение в UI). Показывает всех
 * проанализированных авторов: scatter-диаграмму квадрантов (структурное влияние
 * против тематической активности) и таблицу с 11 оценками (Приложение Е.4) и
 * назначенной ролью. Сбоку — справочник метрик по 4 осям влияния.
 *
 * ЧТО ВНУТРИ
 * LomDashboardScreen — корневой Composable экрана.
 * FilterChip — чип фильтра по роли.
 * MetricInfo — модель описания одной метрики для справочника.
 * AXES — статический справочник 4 осей и их метрик (формулы Е.4).
 * MetricsReferencePanel — боковая панель справочника метрик.
 *
 * МЕТОД
 * 4 оси влияния (docs/algorithm.md): структурная (Aud, Age, ER_bg), тематическая
 * активность (TopVol, TopFocus, Reach), позиция автора (Pos), отклик аудитории
 * (ER_top, Resp). Квадрантная классификация ролей по порогам θ_struct/θ_topic.
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop (Material) — UI; Koin — получение AppNavigator, ActiveSessionHolder
 * и DAO (LomScoreDao/AuthorDao/CompositeDao); StateFlow + collectAsState — слежение
 * за активной сессией и версией данных; собственные компоненты ScatterPlot/LomTable.
 *
 * СВЯЗИ
 * LomScoreDao — 11 оценок; CompositeDao — композиты, роли и пороги θ; AuthorDao —
 * имена авторов. Клик по строке таблицы ведёт через AppNavigator.navigateToDetail
 * на экран LOM_DETAIL.
 */
package com.example.lomanalyzer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
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
import com.example.lomanalyzer.storage.dao.AuthorDao
import com.example.lomanalyzer.storage.dao.CompositeDao
import com.example.lomanalyzer.storage.dao.LomScoreDao
import com.example.lomanalyzer.storage.tables.*
import com.example.lomanalyzer.ui.components.*
import com.example.lomanalyzer.ui.navigation.AppNavigator
import com.example.lomanalyzer.ui.theme.AppColors
import com.example.lomanalyzer.ui.theme.EmptyStateMessage
import com.example.lomanalyzer.ui.theme.ScreenHeader
import com.example.lomanalyzer.ui.theme.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.get

/**
 * Согласованный снимок данных дашборда, прочитанный за один заход в БД.
 *
 * Держать эти три набора вместе важно по двум причинам: они читаются одним
 * фоновым запросом (а не тремя независимыми) и относятся к одной и той же
 * версии данных, поэтому таблица и диаграмма не могут разъехаться между собой.
 *
 * @param rows строки таблицы: 11 оценок автора, его роль и достаточность данных.
 * @param thetaStruct порог θ_struct — вертикальная линия квадрантов.
 * @param thetaTopic порог θ_topic — горизонтальная линия квадрантов.
 * @param compositeCoordinates координаты авторов на диаграмме: authorId -> (Struct_a, Topic_a).
 */
private data class DashboardSnapshot(
    val rows: List<LomTableRow> = emptyList(),
    val thetaStruct: Float = 0f,
    val thetaTopic: Float = 0f,
    val compositeCoordinates: Map<Int, Pair<Float, Float>> = emptyMap(),
)

/**
 * Главный дашборд результатов: scatter-диаграмма квадрантов, таблица авторов с
 * оценками и ролями, боковой справочник метрик.
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
fun LomDashboardScreen() {
    // Зависимости из Koin: навигатор, держатель активной сессии и DAO результатов
    val navigator = remember { get<AppNavigator>(AppNavigator::class.java) }
    val sessionHolder = remember { get<ActiveSessionHolder>(ActiveSessionHolder::class.java) }
    val lomScoreDao = remember { get<LomScoreDao>(LomScoreDao::class.java) }
    val authorDao = remember { get<AuthorDao>(AuthorDao::class.java) }
    val compositeDao = remember { get<CompositeDao>(CompositeDao::class.java) }

    // Реактивное состояние: активная сессия, версия данных, выбранный фильтр по роли
    val sessionId by sessionHolder.sessionId.collectAsState()
    val dataVersion by sessionHolder.dataVersion.collectAsState()
    var roleFilter by remember { mutableStateOf<String?>(null) }

    // Чтение результатов сессии. Все обращения к БД идут через Dispatchers.IO:
    // DAO выполняют блокирующие JDBC-транзакции, и вызов их прямо в composition
    // подвешивал бы отрисовку на время запроса (на больших сессиях — заметно).
    // produceState перезапускает загрузку при смене сессии или версии данных и
    // сам отменяет предыдущую загрузку, если она ещё идёт.
    val snapshot by produceState(DashboardSnapshot(), sessionId, dataVersion) {
        val sid = sessionId
        if (sid == null) {
            value = DashboardSnapshot()
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            val scores = lomScoreDao.findBySession(sid)
            // Композиты и роли индексируем по authorId для быстрого сопоставления с оценками
            val compositeRows = compositeDao.findCompositesBySession(sid)
            val roles = compositeDao.findRolesBySession(sid)
                .associateBy { it[AuthorRoles.authorId].value }
            // Авторов читаем одной выборкой: точечный findById на каждую оценку давал
            // отдельный запрос к БД на автора (N+1) прямо во время отрисовки.
            val authorsById = authorDao.findAll().associateBy { it[Authors.id].value }
            val thresholds = compositeDao.findThresholds(sid)

            val rows = scores.mapNotNull { lom ->
                val authorId = lom[LomScores.authorId].value
                val author = authorsById[authorId] ?: return@mapNotNull null
                val name = listOfNotNull(author[Authors.firstName], author[Authors.lastName])
                    .joinToString(" ").ifBlank { "Author #$authorId" }
                val roleRow = roles[authorId]

                LomTableRow(
                    authorId = authorId,
                    authorName = name,
                    aud = lom[LomScores.aud],
                    age = lom[LomScores.age],
                    erBg = lom[LomScores.erBg],
                    topVol = lom[LomScores.topVol],
                    topFocus = lom[LomScores.topFocus],
                    reach = lom[LomScores.reach],
                    posPositive = lom[LomScores.posPositive],
                    posNeutral = lom[LomScores.posNeutral],
                    posNegative = lom[LomScores.posNegative],
                    erTop = lom[LomScores.erTop],
                    respPositive = lom[LomScores.respPositive],
                    respNeutral = lom[LomScores.respNeutral],
                    respNegative = lom[LomScores.respNegative],
                    topicPostCount = lom[LomScores.topicPostCount],
                    commentCount = lom[LomScores.commentCount],
                    role = roleRow?.get(AuthorRoles.baseRole),
                    sufficiency = roleRow?.get(AuthorRoles.sufficiency),
                )
            }

            DashboardSnapshot(
                rows = rows,
                // Пороги осей (0, если ещё не рассчитаны) — положение разделительных линий
                thetaStruct = thresholds?.get(SessionThresholds.thetaStruct) ?: 0f,
                thetaTopic = thresholds?.get(SessionThresholds.thetaTopic) ?: 0f,
                compositeCoordinates = compositeRows.associate {
                    it[CompositeScores.authorId].value to
                        (it[CompositeScores.structComposite] to it[CompositeScores.topicComposite])
                },
            )
        }
    }

    val allRows = snapshot.rows

    // Применяем фильтр по роли (если выбран): оставляем авторов, чья роль содержит подстроку фильтра
    val rows = remember(allRows, roleFilter) {
        if (roleFilter == null) allRows
        else allRows.filter { it.role?.contains(roleFilter!!, ignoreCase = true) == true }
    }

    val composites = snapshot.compositeCoordinates
    val tauStruct = snapshot.thetaStruct
    val tauTopic = snapshot.thetaTopic

    // Точки для scatter-диаграммы: координаты (композиты) + данные для цвета (позиция/отклик)
    val points = remember(rows, composites) {
        rows.mapNotNull { r ->
            val comp = composites[r.authorId] ?: return@mapNotNull null
            ScatterPoint(
                authorId = r.authorId,
                authorName = r.authorName,
                structComposite = comp.first,
                topicComposite = comp.second,
                role = r.role ?: "BACKGROUND_AUTHOR",
                posPositive = r.posPositive ?: 0f,
                posNeutral = r.posNeutral ?: 1f,
                posNegative = r.posNegative ?: 0f,
                erTop = r.erTop ?: 0f,
                topicPostCount = r.topicPostCount ?: 0,
                sufficiency = r.sufficiency,
            )
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // ── Слева: основной контент (фильтр, диаграмма, таблица) ──
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader(title = "Дашборд", subtitle = "Обзор лидеров мнений по результатам анализа")

            // Чипы фильтра по базовой роли автора
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.FilterList, null, tint = AppColors.textSecondary, modifier = Modifier.size(18.dp))
                    Text("Фильтр по роли:", style = MaterialTheme.typography.subtitle2)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    // Каждый чип задаёт подстроку фильтра роли; «Все» сбрасывает фильтр (null)
                    FilterChip("Все", roleFilter == null, AppColors.primary) { roleFilter = null }
                    FilterChip("Авторитетный лидер", roleFilter == "AUTHORITATIVE", Color(0xFF1B5E20)) { roleFilter = "AUTHORITATIVE" }
                    FilterChip("Спящий гигант", roleFilter == "SLEEPING", Color(0xFFE65100)) { roleFilter = "SLEEPING" }
                    FilterChip("Тематический активист", roleFilter == "ACTIVIST", Color(0xFF0D47A1)) { roleFilter = "ACTIVIST" }
                    FilterChip("Фоновый участник", roleFilter == "BACKGROUND", Color(0xFF6A1B9A)) { roleFilter = "BACKGROUND" }
                }
            }

            // Scatter-диаграмма распределения авторов по квадрантам (оси: структурный и тематический композиты)
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.BubbleChart, null, tint = AppColors.primary, modifier = Modifier.size(20.dp))
                    Text("Распределение авторов", style = MaterialTheme.typography.subtitle1)
                }
                ScatterPlot(points, tauStruct = tauStruct, tauTopic = tauTopic)
            }

            // Таблица авторов с оценками и ролями (клик по строке открывает детальную карточку)
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.TableChart, null, tint = AppColors.primary, modifier = Modifier.size(20.dp))
                    Text("Таблица авторов (${rows.size})", style = MaterialTheme.typography.subtitle1)
                }
                if (rows.isEmpty()) {
                    EmptyStateMessage(title = "Нет данных", subtitle = "Запустите сбор и анализ для отображения результатов")
                } else {
                    Box(modifier = Modifier.heightIn(max = 500.dp)) {
                        // Клик по строке -> переход на детальную карточку выбранного автора
                        LomTable(rows, onRowClick = { navigator.navigateToDetail(it) })
                    }
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // ── Справа: боковая панель-справочник метрик ──
        MetricsReferencePanel(modifier = Modifier.width(280.dp).fillMaxHeight())
    }
}

/**
 * Чип фильтра по роли с анимированной подсветкой выбранного.
 * @param label подпись роли.
 * @param selected выбран ли чип.
 * @param color акцентный цвет роли.
 * @param onClick колбэк выбора фильтра.
 */
@Composable
@Suppress("FunctionNaming")
private fun FilterChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) color.copy(alpha = 0.12f) else AppColors.surfaceVariant, tween(200))
    val fg by animateColorAsState(if (selected) color else AppColors.textSecondary, tween(200))
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
            Text(label, color = fg, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

// ── Боковая панель-справочник метрик ──

/**
 * Описание одной метрики для справочника.
 * @property name русское название метрики.
 * @property code краткий код (например, Aud, ER_bg).
 * @property formula формула расчёта (символьная запись).
 * @property description пояснение смысла метрики.
 */
private data class MetricInfo(val name: String, val code: String, val formula: String, val description: String)

/** Статический справочник 4 осей влияния и их метрик (формулы Приложения Е.4). */
private val AXES = listOf(
    "Ось 1: Структурное влияние" to listOf(
        MetricInfo("Аудитория", "Aud", "ln(1 + F)", "Логарифм числа подписчиков автора"),
        MetricInfo("Стаж", "Age", "d / max(d)", "Нормированный возраст аккаунта (0..1)"),
        MetricInfo("Вовлеч. фон", "ER_bg", "avg((L+C+R)/F)", "Средняя вовлечённость на фоновых постах"),
    ),
    "Ось 2: Тематическая активность" to listOf(
        MetricInfo("Тем. посты", "TopVol", "|T|", "Число тематических публикаций"),
        MetricInfo("Тем. фокус", "TopFocus", "|T|/(|T|+|B|)", "Доля темы в потоке автора (0..1)"),
        MetricInfo("Охват", "Reach", "sum(V)", "Сумма просмотров тематических постов"),
    ),
    "Ось 3: Позиция автора" to listOf(
        MetricInfo("Позиция", "Pos", "(p+, p0, p-)", "Тональность постов: позитив / нейтрал / негатив (%)"),
    ),
    "Ось 4: Отклик аудитории" to listOf(
        MetricInfo("Вовлеч. тема", "ER_top", "avg((L+C+R)/F)", "Средняя вовлечённость на тематических постах"),
        MetricInfo("Отклик", "Resp", "(q+, q0, q-)", "Тональность комментариев: позитив / нейтрал / негатив (%)"),
    ),
)

/**
 * Боковая панель-справочник: для каждой из 4 осей влияния выводит метрики с их
 * кодом, формулой и пояснением (по данным AXES).
 * @param modifier модификатор компоновки панели.
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
private fun MetricsReferencePanel(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = AppColors.surface,
        elevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.Info, null, tint = AppColors.primary, modifier = Modifier.size(18.dp))
                Text("Справочник метрик", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            for ((axisTitle, metrics) in AXES) {
                Text(
                    axisTitle,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = AppColors.primary,
                )
                for (m in metrics) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "${m.name} (${m.code})",
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = AppColors.textPrimary,
                        )
                        Text(
                            m.formula,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.textSecondary,
                        )
                        Text(
                            m.description,
                            fontSize = 11.sp,
                            color = AppColors.textTertiary,
                            lineHeight = 14.sp,
                        )
                    }
                }

                if (axisTitle != AXES.last().first) {
                    Divider(color = AppColors.divider, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}
