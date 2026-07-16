/*
 * НАЗНАЧЕНИЕ
 * Экран постановки задачи (этап 1 пайплайна, docs/algorithm.md). Аналитик здесь
 * создаёт новую сессию анализа: задаёт тему, n-граммы, референсные тексты,
 * временные окна и режим классификации ролей, опционально выбирает сообщества
 * VK или загружает готовый JSON-файл. После создания сессии можно сразу запустить
 * сбор данных, перейдя на экран COLLECTION.
 *
 * ЧТО ВНУТРИ
 * SetupScreen — основной Composable формы с секциями (основное, сообщества,
 * импорт JSON, n-граммы, референсы, окна и роль, кнопки действий).
 * CommunityChip — чип выбранного сообщества с кнопкой удаления.
 * formatCount — форматирование числа подписчиков (K/M).
 * SectionTitle — заголовок секции с иконкой.
 * RoleModeChip — переключатель режима классификации ролей (Quadrant/GMM).
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop (Material) — UI и поля ввода; Koin — получение SessionManager,
 * PipelineLauncher, AppNavigator и DAO; remember/mutableStateOf — локальное
 * состояние формы; javax.swing.JFileChooser — системный диалог выбора JSON-файла.
 *
 * СВЯЗИ
 * SessionManager.createSession сохраняет параметры сессии (SessionParams) в БД;
 * CommunityDao/LinkDao привязывают выбранные сообщества к сессии; PipelineLauncher
 * запускает пайплайн; AppNavigator переключает на экран COLLECTION.
 */
package com.example.lomanalyzer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lomanalyzer.orchestration.PipelineLauncher
import com.example.lomanalyzer.orchestration.SessionManager
import com.example.lomanalyzer.orchestration.SessionParams
import com.example.lomanalyzer.storage.dao.CommunityDao
import com.example.lomanalyzer.storage.dao.LinkDao
import com.example.lomanalyzer.storage.tables.Communities
import com.example.lomanalyzer.ui.components.CommunityPickerDialog
import com.example.lomanalyzer.ui.components.SelectedCommunity
import com.example.lomanalyzer.ui.navigation.AppNavigator
import com.example.lomanalyzer.ui.navigation.NavRoute
import com.example.lomanalyzer.ui.theme.AppColors
import com.example.lomanalyzer.ui.theme.ScreenHeader
import com.example.lomanalyzer.ui.theme.SectionCard
import org.koin.java.KoinJavaComponent.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * Экран постановки задачи и создания сессии анализа (этап 1 пайплайна).
 * Форма параметров сессии с возможностью сразу запустить сбор данных.
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
fun SetupScreen() {
    // Зависимости из Koin DI: менеджер сессий, навигатор, запуск пайплайна и DAO для сообществ/связей
    val sessionManager = remember { get<SessionManager>(SessionManager::class.java) }
    val navigator = remember { get<AppNavigator>(AppNavigator::class.java) }
    val launcher = remember { get<PipelineLauncher>(PipelineLauncher::class.java) }
    val communityDao = remember { get<CommunityDao>(CommunityDao::class.java) }
    val linkDao = remember { get<LinkDao>(LinkDao::class.java) }

    // Локальное состояние полей формы (State/remember/mutableStateOf)
    var name by remember { mutableStateOf("") }                  // название сессии
    var topicQuery by remember { mutableStateOf("") }            // тема исследования (ключевая фраза)
    var region by remember { mutableStateOf("") }                // регион (необязательно)
    var primaryNgrams by remember { mutableStateOf("") }         // первичные n-граммы (через запятую)
    var secondaryNgrams by remember { mutableStateOf("") }       // вторичные n-граммы
    var excludedNgrams by remember { mutableStateOf("") }        // исключённые n-граммы (стоп-слова)
    var referenceTexts by remember { mutableStateOf("") }        // референсные тексты (по строкам), для L2-семантики
    var baselineDays by remember { mutableStateOf("60") }        // базовое окно (фон), дни
    var currentDays by remember { mutableStateOf("30") }         // текущее окно (событийная активность), дни
    var roleMode by remember { mutableStateOf("QUADRANT") }      // режим классификации ролей: QUADRANT или GMM
    var lastCreatedId by remember { mutableStateOf<Int?>(null) } // id только что созданной сессии (активирует кнопку «Начать сбор»)
    val coroutineScope = rememberCoroutineScope()
    var importJsonPath by remember { mutableStateOf<String?>(null) } // путь к JSON-файлу импорта (минуя VK API)

    // Состояние выбора сообществ: флаг показа диалога и список выбранных сообществ
    var showCommunityPicker by remember { mutableStateOf(false) }
    val selectedCommunities = remember { mutableStateListOf<SelectedCommunity>() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = "Настройка сессии",
            subtitle = "Создайте и настройте новую сессию анализа",
        )

        // ── Basic info ──
        SectionCard {
            SectionTitle(Icons.Outlined.Info, "Основная информация")

            val shape = RoundedCornerShape(10.dp)
            OutlinedTextField(
                name, { name = it },
                label = { Text("Название сессии") },
                placeholder = { Text("Например: Политическая повестка Q1") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null, Modifier.size(18.dp)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = shape,
            )
            OutlinedTextField(
                topicQuery, { topicQuery = it },
                label = { Text("Тема исследования") },
                placeholder = { Text("Ключевое слово или фраза") },
                leadingIcon = { Icon(Icons.Outlined.Topic, null, Modifier.size(18.dp)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = shape,
            )
            OutlinedTextField(
                region, { region = it },
                label = { Text("Регион (необязательно)") },
                placeholder = { Text("Например: Москва") },
                leadingIcon = { Icon(Icons.Outlined.Place, null, Modifier.size(18.dp)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = shape,
            )
        }

        // ── Communities ──
        SectionCard {
            SectionTitle(Icons.Outlined.Group, "Сообщества VK")

            if (selectedCommunities.isEmpty()) {
                Text(
                    "Сообщества не выбраны. Нажмите «Найти сообщества» или оставьте пустым — система найдёт их автоматически по теме.",
                    fontSize = 13.sp,
                    color = AppColors.textTertiary,
                )
            } else {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (community in selectedCommunities) {
                        CommunityChip(community) {
                            selectedCommunities.remove(community)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = { showCommunityPicker = true },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(40.dp),
            ) {
                Icon(Icons.Outlined.Search, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Найти сообщества", fontSize = 13.sp)
            }
        }

        // ── Импорт JSON ──
        // Альтернатива сбору через VK API: загрузка готового набора данных из файла
        SectionCard {
            SectionTitle(Icons.Outlined.FolderOpen, "Загрузить данные (JSON)")

            if (importJsonPath != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = AppColors.secondary, modifier = Modifier.size(16.dp))
                    Text(
                        importJsonPath!!.substringAfterLast('/').substringAfterLast('\\'),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.secondary,
                    )
                    IconButton(
                        onClick = { importJsonPath = null },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(Icons.Filled.Close, "Убрать", Modifier.size(14.dp))
                    }
                }
                Text(
                    "При запуске сессии данные будут загружены из файла. " +
                        "VK API не будет использоваться для сбора.",
                    fontSize = 12.sp,
                    color = AppColors.textTertiary,
                )
            } else {
                Text(
                    "Выберите JSON-файл с искусственными или экспортированными данными " +
                        "(сообщества, авторы, посты). Сбор через VK API будет пропущен.",
                    fontSize = 13.sp,
                    color = AppColors.textTertiary,
                )
            }

            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = {
                    // Системный диалог Swing для выбора JSON-файла; фильтр по расширению .json
                    val dialog = javax.swing.JFileChooser().apply {
                        fileFilter = javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json")
                        dialogTitle = "Выберите JSON-файл с данными"
                    }
                    // При подтверждении сохраняем абсолютный путь к файлу для последующего импорта
                    if (dialog.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                        importJsonPath = dialog.selectedFile.absolutePath
                    }
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(40.dp),
            ) {
                Icon(Icons.Outlined.FolderOpen, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (importJsonPath != null) "Выбрать другой файл" else "Выбрать файл", fontSize = 13.sp)
            }
        }

        // ── N-grams ──
        SectionCard {
            SectionTitle(Icons.Outlined.TextFields, "N-граммы (через запятую)")

            val shape = RoundedCornerShape(10.dp)
            OutlinedTextField(
                primaryNgrams, { primaryNgrams = it },
                label = { Text("Первичные") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = shape,
            )
            OutlinedTextField(
                secondaryNgrams, { secondaryNgrams = it },
                label = { Text("Вторичные") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = shape,
            )
            OutlinedTextField(
                excludedNgrams, { excludedNgrams = it },
                label = { Text("Исключённые") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = shape,
            )
        }

        // ── Reference texts ──
        SectionCard {
            SectionTitle(Icons.Outlined.Description, "Референсные тексты (3-10)")
            OutlinedTextField(
                referenceTexts, { referenceTexts = it },
                label = { Text("Тексты") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 150.dp),
                maxLines = 10,
                shape = RoundedCornerShape(10.dp),
            )
        }

        // ── Windows & Role ──
        SectionCard {
            SectionTitle(Icons.Outlined.DateRange, "Параметры окон и роли")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    baselineDays, { baselineDays = it },
                    label = { Text("Базовое окно (дни)") },
                    leadingIcon = { Icon(Icons.Outlined.History, null, Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                )
                OutlinedTextField(
                    currentDays, { currentDays = it },
                    label = { Text("Текущее окно (дни)") },
                    leadingIcon = { Icon(Icons.Outlined.Today, null, Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                )
            }

            // Выбор режима классификации ролей: квадрантный (всегда) или GMM (нужно >= 50 авторов)
            Text("Режим классификации ролей", style = MaterialTheme.typography.subtitle2)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RoleModeChip("Quadrant", roleMode == "QUADRANT") { roleMode = "QUADRANT" }
                RoleModeChip("GMM (n>=50)", roleMode == "GMM") { roleMode = "GMM" }
            }
        }

        // ── Кнопки действий: создать сессию и запустить сбор ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    // Создание сессии и привязка сообществ — блокирующие обращения к БД,
                    // поэтому выполняются в IO-диспетчере, а не в UI-потоке композиции.
                    coroutineScope.launch {
                    val id = withContext(Dispatchers.IO) {
                    // Создаём сессию: парсим n-граммы (через запятую) и референсы (по строкам),
                    // подставляем дефолты окон (60/30 дней) при некорректном вводе
                    sessionManager.createSession(
                        SessionParams(
                            name = name.ifBlank { "Unnamed" },
                            topicQuery = topicQuery.ifBlank { "default" },
                            primaryNgrams = primaryNgrams.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            secondaryNgrams = secondaryNgrams.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            excludedNgrams = excludedNgrams.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            referenceTexts = referenceTexts.split("\n").map { it.trim() }.filter { it.isNotBlank() },
                            region = region.ifBlank { null },
                            roleMode = roleMode,
                            baselineWindowDays = baselineDays.toIntOrNull() ?: 60,
                            currentWindowDays = currentDays.toIntOrNull() ?: 30,
                            importJsonPath = importJsonPath,
                        ),
                    )
                    }
                    withContext(Dispatchers.IO) {
                        // Привязываем выбранные сообщества к сессии (создавая записи в БД при отсутствии)
                        for (community in selectedCommunities) {
                            val existing = communityDao.findByVkId(community.vkId)
                            val communityId = existing?.let {
                                it[Communities.id].value
                            } ?: communityDao.insert(
                                vkId = community.vkId,
                                name = community.name,
                                screenName = community.screenName,
                                membersCount = community.membersCount,
                                isClosed = community.isClosed,
                                communityType = community.type,
                            )
                            linkDao.linkSessionCommunity(id, communityId)
                        }
                    }
                    // Запоминаем id созданной сессии — это разблокирует кнопку «Начать сбор»
                    lastCreatedId = id
                    }
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(44.dp),
                elevation = ButtonDefaults.elevation(defaultElevation = 2.dp),
            ) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Создать сессию")
            }

            OutlinedButton(
                onClick = {
                    // Запуск пайплайна по созданной сессии и переход на экран хода сбора
                    val sessionId = lastCreatedId
                    if (sessionId != null) {
                        launcher.launch(sessionId)
                        navigator.navigate(NavRoute.COLLECTION)
                    }
                },
                enabled = lastCreatedId != null,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(44.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Начать сбор")
            }

            AnimatedVisibility(lastCreatedId != null) {
                lastCreatedId?.let {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AppColors.secondaryLight,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Filled.CheckCircle, null, tint = AppColors.secondary, modifier = Modifier.size(16.dp))
                            Text("Сессия #$it создана", color = AppColors.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    // ── Диалог выбора сообществ VK ──
    // Открывается по кнопке «Найти сообщества»; результат заменяет текущий список выбранных
    if (showCommunityPicker) {
        CommunityPickerDialog(
            selected = selectedCommunities.toList(),
            onSelectionChanged = { newSelection ->
                selectedCommunities.clear()
                selectedCommunities.addAll(newSelection)
            },
            onDismiss = { showCommunityPicker = false },
        )
    }
}

/**
 * Чип одного выбранного сообщества VK: иконка, название, число подписчиков и крестик удаления.
 * @param community выбранное сообщество.
 * @param onRemove колбэк удаления чипа из списка выбранных.
 */
@Composable
@Suppress("FunctionNaming")
private fun CommunityChip(community: SelectedCommunity, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = AppColors.primaryLight,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.Group, null, Modifier.size(14.dp), tint = AppColors.primary)
            Text(community.name, fontSize = 12.sp, color = AppColors.primary, fontWeight = FontWeight.Medium)
            community.membersCount?.let {
                Text("(${formatCount(it)})", fontSize = 11.sp, color = AppColors.primary.copy(alpha = 0.6f))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Filled.Close, null, Modifier.size(14.dp), tint = AppColors.primary)
            }
        }
    }
}

/** Компактное форматирование числа подписчиков: тысячи как K, миллионы как M. */
private fun formatCount(count: Int): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> "$count"
}

/**
 * Заголовок секции формы: иконка слева и текст заголовка.
 * @param icon иконка секции.
 * @param title текст заголовка.
 */
@Composable
@Suppress("FunctionNaming")
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = AppColors.primary, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.subtitle1)
    }
}

/**
 * Радио-чип выбора режима классификации ролей (Quadrant или GMM).
 * @param label подпись режима.
 * @param selected выбран ли этот режим.
 * @param onClick колбэк выбора режима.
 */
@Composable
@Suppress("FunctionNaming")
private fun RoleModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) AppColors.primaryLight else AppColors.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                modifier = Modifier.size(18.dp),
                colors = RadioButtonDefaults.colors(selectedColor = AppColors.primary),
            )
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) AppColors.primary else AppColors.textSecondary,
            )
        }
    }
}
