/*
 * НАЗНАЧЕНИЕ
 * Корневой каркас (shell) основного окна приложения после успешного входа.
 * Собирает воедино боковое меню (Sidebar), область контента с текущим экраном
 * (ContentArea), всплывающие уведомления об ошибках (Snackbar) и плавающий
 * таймер охлаждения VK API (CooldownOverlay). Реализует роутинг: по текущему
 * NavRoute показывает нужный экран.
 *
 * ЧТО ВНУТРИ
 * MainContent — корневой Composable окна.
 * Sidebar — левое навигационное меню (бренд, профиль, пункты, выход).
 * UserSection — блок с аватаром и именем текущего пользователя VK.
 * SidebarSectionLabel — подпись-разделитель группы пунктов меню.
 * NavItem — один кликабельный пункт меню с подсветкой активного.
 * ContentArea — область, рисующая Composable-экран по текущему маршруту.
 * TopicValidationWrapper — обёртка, подтягивающая данные для экрана валидации.
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop (Material) — декларативный UI; Koin (KoinJavaComponent.get) —
 * получение синглтонов AppNavigator/AuthManager/ErrorNotifier и DAO; StateFlow +
 * collectAsState — реактивная подписка на текущий маршрут и активную сессию;
 * корутины (rememberCoroutineScope, LaunchedEffect, collectLatest) — фоновые
 * операции и подписка на поток ошибок; AWT Toolkit/StringSelection — копирование
 * текста ошибки в системный буфер обмена.
 *
 * СВЯЗИ
 * Читает AppNavigator.currentRoute (см. AppNavigator.kt и NavRoute.kt), вызывает
 * экраны из пакета ui.screens. TopicValidationWrapper связан с этапом 6
 * (тематическая фильтрация, docs/algorithm.md): читает посты сессии из PostDao,
 * сопоставляет имена авторов/сообществ и сохраняет решения аналитика обратно в БД.
 */
package com.example.lomanalyzer.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lomanalyzer.security.AuthManager
import com.example.lomanalyzer.ui.components.CooldownOverlay
import com.example.lomanalyzer.ui.components.ErrorNotifier
import com.example.lomanalyzer.ui.screens.*
import com.example.lomanalyzer.ui.theme.AppColors
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Корневой Composable главного окна: боковое меню + область контента + Snackbar
 * с ошибками + плавающий таймер охлаждения VK API.
 * @param onLogout колбэк выхода из аккаунта (очистка сессии и возврат на логин).
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
fun MainContent(onLogout: () -> Unit = {}) {
    // Получаем синглтоны из Koin DI и кэшируем их на время жизни Composable через remember
    val navigator = remember { get<AppNavigator>(AppNavigator::class.java) }
    val authManager = remember { get<AuthManager>(AuthManager::class.java) }
    val errorNotifier = remember { get<ErrorNotifier>(ErrorNotifier::class.java) }
    // Реактивно подписываемся на текущий маршрут: смена route перерисует экран
    val currentRoute by navigator.currentRoute.collectAsState()
    // Состояние хоста Snackbar (показ всплывающих сообщений об ошибках)
    val snackbarHostState = remember { SnackbarHostState() }
    // Текст последней ошибки — храним отдельно, чтобы кнопка «Копировать» имела доступ к нему
    var lastErrorText by remember { mutableStateOf("") }

    // Подписка на глобальный поток ошибок: при каждой ошибке показываем Snackbar
    LaunchedEffect(Unit) {
        errorNotifier.errors.collectLatest { event ->
            lastErrorText = event.format()
            snackbarHostState.showSnackbar(
                message = lastErrorText,
                actionLabel = "Копировать",
                duration = SnackbarDuration.Long,
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Основной макет: слева меню фиксированной ширины, справа — контент текущего экрана
        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(currentRoute, navigator, authManager, onLogout)
            ContentArea(currentRoute)
        }

        // Плавающий таймер охлаждения VK API (немодальный, в правом нижнем углу)
        CooldownOverlay()

        // Хост Snackbar внизу по центру; кастомная отрисовка плашки ошибки с кнопкой копирования
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        ) { data ->
            Snackbar(
                modifier = Modifier.widthIn(max = 600.dp),
                shape = RoundedCornerShape(10.dp),
                backgroundColor = Color(0xFF2D1B1B),
                contentColor = Color(0xFFE8B4B4),
                action = {
                    data.actionLabel?.let { label ->
                        // Кнопка «Копировать»: кладём текст ошибки в системный буфер обмена (AWT) и закрываем плашку
                        TextButton(onClick = {
                            val clip = StringSelection(lastErrorText)
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(clip, null)
                            data.dismiss()
                        }) {
                            Text(label, color = Color(0xFFF0D0D0), fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = Color(0xFFE8B4B4),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        data.message,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

/**
 * Левое навигационное меню фиксированной ширины (230 dp).
 * Содержит логотип, блок пользователя, сгруппированные пункты переходов и кнопку выхода.
 * @param current текущий активный маршрут (для подсветки выбранного пункта).
 * @param navigator контроллер навигации, вызываемый при клике на пункт.
 * @param authManager источник данных о текущем пользователе VK.
 * @param onLogout колбэк выхода из аккаунта.
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
private fun Sidebar(
    current: NavRoute,
    navigator: AppNavigator,
    authManager: AuthManager,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(230.dp)
            .fillMaxHeight()
            .background(AppColors.sidebarBg)
            .padding(top = 16.dp),
    ) {
        // ── Brand ──
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AppColors.sidebarAccent,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("L", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Text(
                "LOM Analyzer",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── User section ──
        UserSection(authManager, navigator)

        Spacer(Modifier.height(12.dp))
        Divider(color = AppColors.sidebarSurface, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))

        // ── Навигация ──
        // Прокручиваемый список пунктов меню, сгруппированных по смыслу (анализ / результаты / справка / настройки)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Группа «Анализ»: подготовка задачи и процесс сбора (этапы 1, 2-10, валидация — этап 6)
            SidebarSectionLabel("АНАЛИЗ")
            NavItem("Настройка", Icons.Outlined.Tune, NavRoute.SETUP, current, navigator)
            NavItem("Сбор данных", Icons.Outlined.CloudDownload, NavRoute.COLLECTION, current, navigator)
            NavItem("Валидация", Icons.AutoMirrored.Outlined.FactCheck, NavRoute.TOPIC_VALIDATION, current, navigator)
            NavItem("История сессий", Icons.Outlined.History, NavRoute.SESSION_HISTORY, current, navigator)

            Spacer(Modifier.height(12.dp))
            // Группа «Результаты»: итоги анализа — дашборд ЛОМ, индикаторы качества, журнал событий
            SidebarSectionLabel("РЕЗУЛЬТАТЫ")
            NavItem("Дашборд", Icons.Outlined.Dashboard, NavRoute.LOM_DASHBOARD, current, navigator)
            NavItem("Качество", Icons.Outlined.Speed, NavRoute.SESSION_QUALITY, current, navigator)
            NavItem("Журнал", Icons.Outlined.Assignment, NavRoute.SESSION_LOG, current, navigator)

            Spacer(Modifier.height(12.dp))
            // Группа «Справка»: руководство пользователя
            SidebarSectionLabel("СПРАВКА")
            NavItem("Руководство", Icons.AutoMirrored.Outlined.MenuBook, NavRoute.GUIDE, current, navigator)

            Spacer(Modifier.height(12.dp))
            // Группа «Настройки»: профиль, токен, управление данными
            SidebarSectionLabel("НАСТРОЙКИ")
            NavItem("Профиль", Icons.Outlined.ManageAccounts, NavRoute.PROFILE, current, navigator)
        }

        // ── Выход ──
        Divider(color = AppColors.sidebarSurface, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onLogout)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = AppColors.error.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            Text("Выйти", color = AppColors.error.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * Блок текущего пользователя в шапке меню: круглый аватар с инициалами и имя.
 * При клике (если передан navigator) открывает экран профиля.
 * @param authManager источник информации о сессии VK (имя пользователя).
 * @param navigator опциональный навигатор; если задан — блок кликабелен.
 */
@Composable
@Suppress("FunctionNaming")
private fun UserSection(authManager: AuthManager, navigator: AppNavigator? = null) {
    // Данные сессии VK (имя). Если их нет — подставляем заглушку «VK User»
    val sessionInfo = remember { authManager.getSessionInfo() }
    val displayName = sessionInfo?.displayName ?: "VK User"
    // Инициалы для аватара: первые буквы первых двух слов имени в верхнем регистре
    val initials = displayName.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")

    Row(
        modifier = Modifier.fillMaxWidth()
            .let { mod -> if (navigator != null) mod.clickable { navigator.navigate(NavRoute.PROFILE) } else mod }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Avatar circle
        Surface(
            shape = CircleShape,
            color = AppColors.sidebarAccent.copy(alpha = 0.2f),
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    initials.ifEmpty { "U" },
                    color = AppColors.sidebarAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
        Column {
            Text(
                displayName,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "VK подключён",
                color = AppColors.secondary,
                fontSize = 10.sp,
            )
        }
    }
}

/**
 * Подпись-разделитель группы пунктов меню (например, «АНАЛИЗ», «РЕЗУЛЬТАТЫ»).
 * @param label текст подписи (выводится капителью с разрядкой).
 */
@Composable
@Suppress("FunctionNaming")
private fun SidebarSectionLabel(label: String) {
    Text(
        label,
        color = AppColors.sidebarText.copy(alpha = 0.5f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/**
 * Один пункт навигационного меню: иконка + подпись, с анимированной подсветкой,
 * если пункт соответствует текущему маршруту. Клик вызывает переход.
 * @param label подпись пункта.
 * @param icon иконка пункта.
 * @param route маршрут, на который ведёт пункт.
 * @param current текущий активный маршрут (для определения выделения).
 * @param navigator контроллер навигации.
 */
@Composable
@Suppress("FunctionNaming")
private fun NavItem(
    label: String,
    icon: ImageVector,
    route: NavRoute,
    current: NavRoute,
    navigator: AppNavigator,
) {
    // Пункт активен, если его маршрут совпадает с текущим экраном
    val selected = current == route
    // Анимированные цвета фона/текста/иконки: плавный переход при смене активного пункта
    val bgColor by animateColorAsState(
        if (selected) AppColors.sidebarAccent.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(200),
    )
    val textColor by animateColorAsState(
        if (selected) Color.White else AppColors.sidebarText,
        animationSpec = tween(200),
    )
    val iconColor by animateColorAsState(
        if (selected) AppColors.sidebarAccent else AppColors.sidebarText,
        animationSpec = tween(200),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            // Клик по пункту переключает приложение на соответствующий экран
            .clickable { navigator.navigate(route) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(18.dp))
        Text(label, color = textColor, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/**
 * Область контента справа от меню. Это ядро роутинга: по текущему маршруту
 * выбирает и отображает соответствующий Composable-экран из пакета ui.screens.
 * @param currentRoute текущий активный маршрут.
 */
@Composable
@Suppress("FunctionNaming")
private fun ContentArea(currentRoute: NavRoute) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppColors.contentBg,
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            // Сопоставление маршрута с экраном: каждое значение NavRoute -> свой Composable
            when (currentRoute) {
                NavRoute.SETUP -> SetupScreen()
                NavRoute.COLLECTION -> CollectionScreen()
                NavRoute.TOPIC_VALIDATION -> TopicValidationWrapper()
                NavRoute.SESSION_HISTORY -> SessionHistoryScreen()
                NavRoute.LOM_DASHBOARD -> LomDashboardScreen()
                NavRoute.LOM_DETAIL -> LomDetailScreen()
                NavRoute.SESSION_QUALITY -> SessionQualityScreen()
                NavRoute.SESSION_LOG -> SessionLogScreen()
                NavRoute.GUIDE -> GuideScreen()
                NavRoute.PROFILE -> ProfileScreen()
            }
        }
    }
}

/**
 * Обёртка-«контейнер» для экрана валидации тематической фильтрации (этап 6,
 * docs/algorithm.md). Отделяет получение данных и работу с БД от чистого UI
 * (сам TopicValidationScreen — презентационный).
 *
 * Что делает:
 * 1. Тянет из Koin DAO (PostDao/AuthorDao/CommunityDao), держатель активной
 *    сессии и нотификатор ошибок.
 * 2. По id активной сессии загружает все посты, строит карты имён авторов и
 *    сообществ и собирает модели ValidationPost (с расчётом «страты» по
 *    скомбинированной оценке тематичности topic_score_combined).
 * 3. Передаёт в экран колбэки, сохраняющие решения аналитика в БД: изменение
 *    порога, добавление стоп-фразы, голос по посту и завершение валидации.
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
private fun TopicValidationWrapper() {
    // Зависимости из Koin DI: держатель активной сессии и DAO для постов/авторов/сообществ
    val sessionHolder = remember {
        get<com.example.lomanalyzer.orchestration.ActiveSessionHolder>(
            com.example.lomanalyzer.orchestration.ActiveSessionHolder::class.java
        )
    }
    val postDao = remember {
        get<com.example.lomanalyzer.storage.dao.PostDao>(
            com.example.lomanalyzer.storage.dao.PostDao::class.java
        )
    }
    val authorDao = remember {
        get<com.example.lomanalyzer.storage.dao.AuthorDao>(
            com.example.lomanalyzer.storage.dao.AuthorDao::class.java
        )
    }
    val communityDao = remember {
        get<com.example.lomanalyzer.storage.dao.CommunityDao>(
            com.example.lomanalyzer.storage.dao.CommunityDao::class.java
        )
    }
    val errorNotifier = remember {
        get<ErrorNotifier>(ErrorNotifier::class.java)
    }
    // Реактивно следим за id активной сессии и версией данных (dataVersion инвалидирует кэш при изменениях)
    val sessionId by sessionHolder.sessionId.collectAsState()
    val dataVersion by sessionHolder.dataVersion.collectAsState()
    // Флаг: валидация завершена — показываем экран-заглушку вместо формы
    var validationSaved by remember { mutableStateOf(false) }
    // Scope корутин для записи решений аналитика в БД вне UI-потока
    val scope = rememberCoroutineScope()

    // Загрузка и подготовка постов для валидации; пересчитывается при смене сессии или версии данных
    val posts = remember(sessionId, dataVersion) {
        val sid = sessionId ?: return@remember emptyList<com.example.lomanalyzer.analysis.topic.ValidationPost>()

        // Загружаем все посты сессии (оба окна: базовое и текущее)
        val allPosts = postDao.findBySession(sid)
        if (allPosts.isEmpty()) return@remember emptyList()

        // Строим справочные карты «vkId -> имя» для авторов и сообществ (чтобы показать читаемые названия)
        val authorMap = authorDao.findAll().associate {
            it[com.example.lomanalyzer.storage.tables.Authors.vkId] to
                "${it[com.example.lomanalyzer.storage.tables.Authors.firstName] ?: ""} ${it[com.example.lomanalyzer.storage.tables.Authors.lastName] ?: ""}".trim()
        }
        val communityMap = communityDao.findAll().associate {
            it[com.example.lomanalyzer.storage.tables.Communities.vkId] to
                it[com.example.lomanalyzer.storage.tables.Communities.name]
        }

        // Преобразуем каждую строку БД в модель ValidationPost для UI
        allPosts.map { post ->
            val ownerId = post[com.example.lomanalyzer.storage.tables.Posts.ownerId]
            val fromId = post[com.example.lomanalyzer.storage.tables.Posts.fromId]
            // Скомбинированная оценка тематичности (L1+L2) — основа для «страты» и порога
            val combined = post[com.example.lomanalyzer.storage.tables.Posts.topicScoreCombined]

            // Источник поста: отрицательный ownerId — сообщество, положительный — личная стена пользователя
            val communityName = if (ownerId < 0) {
                communityMap[-ownerId] ?: "Сообщество ${-ownerId}"
            } else {
                val ownerName = authorMap[ownerId]
                if (ownerName.isNullOrBlank()) "Стена $ownerId" else "Стена: $ownerName"
            }
            val authorName = authorMap[fromId].let { if (it.isNullOrBlank()) "ID $fromId" else it }

            com.example.lomanalyzer.analysis.topic.ValidationPost(
                id = post[com.example.lomanalyzer.storage.tables.Posts.id].value,
                text = post[com.example.lomanalyzer.storage.tables.Posts.text] ?: "",
                score = combined ?: 0f,
                systemRelevant = post[com.example.lomanalyzer.storage.tables.Posts.isTopicRelevant] ?: false,
                // Страта поста по уровню оценки тематичности — для приоритезации проверки аналитиком
                stratum = when {
                    combined == null -> "UNSCORED"
                    combined > 0.6f -> "HIGH_CONF"
                    combined > 0.3f -> "BORDERLINE_POS"
                    combined > 0.1f -> "BORDERLINE_NEG"
                    else -> "NEAR_ZERO"
                },
                authorName = authorName,
                communityName = communityName,
                fromVkId = fromId,
                ownerVkId = ownerId,
                analystVote = post[com.example.lomanalyzer.storage.tables.Posts.analystVote],
                publishedAt = post[com.example.lomanalyzer.storage.tables.Posts.publishedAt],
                window = post[com.example.lomanalyzer.storage.tables.Posts.window],
            )
        }
    }

    // Три состояния экрана: нет данных / валидация уже завершена / рабочая форма валидации
    if (posts.isEmpty()) {
        com.example.lomanalyzer.ui.theme.EmptyStateMessage(
            title = "Валидация тем",
            subtitle = "Нет данных. Запустите сбор и анализ для отображения постов.",
        )
    } else if (validationSaved) {
        com.example.lomanalyzer.ui.theme.EmptyStateMessage(
            title = "Валидация завершена",
            subtitle = "Результаты сохранены. Релевантность постов обновлена.",
        )
    } else {
        TopicValidationScreen(
            allPosts = posts,
            currentThreshold = 0.3f,
            // Колбэк изменения порога: пересчитываем системную релевантность постов без ручного голоса
            onThresholdChanged = { newThreshold ->
                val sid = sessionId ?: return@TopicValidationScreen
                scope.launch {
                    // Обновляем системную релевантность для постов без переопределения аналитиком
                    val dbPosts = postDao.findBySession(sid)
                    for (post in dbPosts) {
                        val hasVote = post[com.example.lomanalyzer.storage.tables.Posts.analystVote]
                        if (hasVote != null) continue // голос аналитика имеет приоритет над порогом
                        val combined = post[com.example.lomanalyzer.storage.tables.Posts.topicScoreCombined] ?: continue
                        // Пост релевантен, если его комбинированная оценка не ниже нового порога
                        postDao.updateTopicRelevance(
                            id = post[com.example.lomanalyzer.storage.tables.Posts.id].value,
                            relevant = combined >= newThreshold,
                            l1 = post[com.example.lomanalyzer.storage.tables.Posts.topicScoreL1],
                            l2 = post[com.example.lomanalyzer.storage.tables.Posts.topicScoreL2],
                            combined = combined,
                        )
                    }
                    // Сообщаем держателю сессии, что данные изменились — экраны перечитают БД
                    sessionHolder.notifyDataChanged()
                }
            },
            // Колбэк добавления стоп-фразы: помечаем нерелевантными все посты, содержащие фразу
            onStopPhraseAdded = { phrase ->
                val sid = sessionId ?: return@TopicValidationScreen
                scope.launch {
                    val dbPosts = postDao.findBySession(sid)
                    val lower = phrase.lowercase()
                    for (post in dbPosts) {
                        val text = post[com.example.lomanalyzer.storage.tables.Posts.text] ?: ""
                        // Нечувствительный к регистру поиск стоп-фразы в тексте поста
                        if (text.lowercase().contains(lower)) {
                            postDao.updateTopicRelevance(
                                id = post[com.example.lomanalyzer.storage.tables.Posts.id].value,
                                relevant = false,
                                l1 = post[com.example.lomanalyzer.storage.tables.Posts.topicScoreL1],
                                l2 = post[com.example.lomanalyzer.storage.tables.Posts.topicScoreL2],
                                combined = post[com.example.lomanalyzer.storage.tables.Posts.topicScoreCombined],
                            )
                        }
                    }
                    sessionHolder.notifyDataChanged()
                }
            },
            // Колбэк голоса аналитика по конкретному посту (релевантен / нет / сброс)
            onVote = { postId, vote ->
                scope.launch {
                    // Немедленно сохраняем голос аналитика в БД
                    postDao.updateAnalystVote(postId, vote)
                    // Если аналитик принял решение — синхронизируем флаг тематической релевантности
                    if (vote != null) {
                        val post = postDao.findById(postId) ?: return@launch
                        postDao.updateTopicRelevance(
                            id = postId,
                            relevant = vote,
                            l1 = post[com.example.lomanalyzer.storage.tables.Posts.topicScoreL1],
                            l2 = post[com.example.lomanalyzer.storage.tables.Posts.topicScoreL2],
                            combined = post[com.example.lomanalyzer.storage.tables.Posts.topicScoreCombined],
                        )
                    }
                }
            },
            // Колбэк завершения валидации: считаем число оценённых постов и показываем итог
            onValidationComplete = {
                scope.launch {
                    val sid = sessionId ?: return@launch
                    val dbPosts = postDao.findBySession(sid)
                    // Подсчёт постов, получивших голос аналитика (для итогового сообщения)
                    val votedCount = dbPosts.count {
                        it[com.example.lomanalyzer.storage.tables.Posts.analystVote] != null
                    }
                    // Переключаем экран в состояние «валидация завершена» и оповещаем об изменении данных
                    validationSaved = true
                    sessionHolder.notifyDataChanged()
                    errorNotifier.emit(
                        source = "Валидация",
                        code = null,
                        message = "Валидация завершена: $votedCount постов оценено аналитиком",
                        detail = null,
                    )
                }
            },
        )
    }
}
