/*
 * НАЗНАЧЕНИЕ
 * Переиспользуемый UI-компонент Compose Desktop — диалог выбора сообществ и
 * профилей ВКонтакте для анализа (этап 1 — постановка/выбор источников, далее
 * фаза A сбора данных). Позволяет искать по названию, по ссылке или по ID/
 * screen_name, выбирать несколько объектов и вернуть выбор наружу.
 *
 * ЧТО ВНУТРИ
 * data class SelectedCommunity — выбранный объект (группа или пользователь).
 * sealed class SearchItem (Group/User) — унифицированный элемент результата
 * поиска. @Composable CommunityPickerDialog — сам диалог. Приватный @Composable
 * SearchItemRow — строка результата. Функции-помощники: classifyInput (тип
 * ввода), parseVkCommunityInput (разбор ID и screen_names), resolveDirectInput
 * (резолв прямых ссылок/ID через VK API), toSelectedCommunity, formatMembers.
 *
 * МЕТОД
 * Ввод классифицируется: прямые ID/ссылки идут в getById/resolveScreenName;
 * текстовый запрос — в groups.search и users.search, с fallback на резолв как
 * screen_name. Если поиск по имени недоступен для токена (searchAvailable=false),
 * UI подсказывает вводить ссылку/ID. Поиск выполняется в отдельном CoroutineScope
 * с отменой предыдущего запроса при новом.
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop; Dialog/Card — модальное окно; LazyColumn — список результатов;
 * kotlinx.coroutines — асинхронный поиск и отмена; Koin (DI) — получение
 * VkApiClient/AuthManager/ErrorNotifier; Ktor (через VkApiClient) — вызовы VK API.
 *
 * СВЯЗИ
 * VkApiClient и модели VkGroup/VkUser из vk; AuthManager из security; ошибки
 * публикуются в ErrorNotifier. Результат (List<SelectedCommunity>) передаётся
 * через onSelectionChanged.
 */
package com.example.lomanalyzer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.window.Dialog
import com.example.lomanalyzer.security.AuthManager
import com.example.lomanalyzer.ui.theme.AppColors
import com.example.lomanalyzer.vk.VkApiClient
import com.example.lomanalyzer.vk.models.VkGroup
import com.example.lomanalyzer.vk.models.VkUser
import kotlinx.coroutines.*
import org.koin.java.KoinJavaComponent.get

/**
 * Выбранный источник для анализа: сообщество или профиль пользователя VK.
 *
 * @param vkId VK-идентификатор объекта.
 * @param name отображаемое имя.
 * @param screenName короткое имя (screen_name), если есть.
 * @param membersCount число подписчиков/участников, если известно.
 * @param isClosed закрытый ли объект.
 * @param type тип объекта (group/page/event/user и т. п.).
 */
data class SelectedCommunity(
    val vkId: Int,
    val name: String,
    val screenName: String?,
    val membersCount: Int?,
    val isClosed: Boolean,
    val type: String?,
)

/** Унифицированный элемент результата поиска — либо группа, либо профиль пользователя. */
private sealed class SearchItem {
    abstract val id: Int
    abstract val displayName: String
    abstract val subtitle: String?
    abstract val badge: String?

    /** Вариант результата — сообщество (обёртка над VkGroup). */
    data class Group(val group: VkGroup) : SearchItem() {
        override val id get() = group.id
        override val displayName get() = group.name
        override val subtitle get() = group.screenName?.let { "@$it" }
        override val badge get() = group.membersCount?.let { formatMembers(it) }
    }

    /** Вариант результата — профиль пользователя (обёртка над VkUser). */
    data class User(val user: VkUser) : SearchItem() {
        override val id get() = user.id
        override val displayName get() = "${user.firstName} ${user.lastName}".trim()
        override val subtitle get() = user.screenName?.let { "@$it" }
        override val badge get() = user.followersCount?.let { formatMembers(it) }
    }
}

/**
 * Диалог выбора сообществ и профилей VK для анализа.
 *
 * @param selected уже выбранные ранее объекты (для предзаполнения).
 * @param onSelectionChanged вызывается при подтверждении с итоговым списком выбора.
 * @param onDismiss закрытие диалога без изменений.
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
fun CommunityPickerDialog(
    selected: List<SelectedCommunity>,
    onSelectionChanged: (List<SelectedCommunity>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Зависимости из Koin (DI): клиент VK API, менеджер токена, шина ошибок
    val vkApi = remember { get<VkApiClient>(VkApiClient::class.java) }
    val authManager = remember { get<AuthManager>(AuthManager::class.java) }
    val errorNotifier = remember { get<ErrorNotifier>(ErrorNotifier::class.java) }

    // Отдельный scope: переживает короткие recomposition, но отменяется при закрытии диалога
    val searchScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    DisposableEffect(Unit) { onDispose { searchScope.cancel() } }

    // Локальное состояние диалога
    var inputText by remember { mutableStateOf("") }                 // текст поискового поля
    var searchResults by remember { mutableStateOf<List<SearchItem>>(emptyList()) } // результаты поиска
    var isSearching by remember { mutableStateOf(false) }            // идёт ли поиск (для индикатора)
    var errorMessage by remember { mutableStateOf<String?>(null) }   // сообщение об ошибке поиска
    var searchAvailable by remember { mutableStateOf<Boolean?>(null) } // доступен ли поиск по имени для токена
    // Идентификаторы и карта выбранных объектов (предзаполняются из selected)
    val selectedIds = remember { mutableStateListOf<Int>().also { it.addAll(selected.map { c -> c.vkId }) } }
    val selectedMap = remember {
        mutableStateMapOf<Int, SelectedCommunity>().also {
            for (c in selected) it[c.vkId] = c
        }
    }
    // Текущая поисковая корутина — чтобы отменить предыдущий поиск при новом запросе
    var activeJob by remember { mutableStateOf<Job?>(null) }

    /** Запускает поиск по введённому тексту: классифицирует ввод и обращается к VK API. */
    fun doSearch(input: String) {
        if (input.isBlank()) return
        // Отменяем предыдущий поиск, чтобы не было гонки результатов
        activeJob?.cancel()
        activeJob = searchScope.launch {
            isSearching = true
            errorMessage = null
            searchResults = emptyList()
            try {
                // Без токена VK поиск невозможен
                val token = authManager.getAccessToken()
                if (token == null) {
                    errorMessage = "Нет VK токена"
                    isSearching = false
                    return@launch
                }

                val trimmed = input.trim()
                // Определяем характер ввода: прямые ID/ссылки или свободный текстовый запрос
                val inputKind = classifyInput(trimmed)

                when (inputKind) {
                    InputKind.DIRECT_IDS -> {
                        // Введены явные ID/ссылки — сразу резолвим через getById/resolveScreenName
                        val results = resolveDirectInput(trimmed, vkApi, token)
                        if (results.isEmpty()) {
                            errorMessage = "По запросу «$trimmed» ничего не найдено"
                        } else {
                            searchResults = results
                        }
                    }

                    InputKind.TEXT_QUERY -> {
                        val allResults = mutableListOf<SearchItem>()

                        // Пробуем поиск групп (groups.search) в IO-диспетчере
                        if (searchAvailable != false) {
                            val searchResult = withContext(Dispatchers.IO) {
                                vkApi.groupsSearch(query = trimmed, count = 20, accessToken = token)
                            }
                            if (searchResult.error == null) {
                                // Поиск сработал — запоминаем, что он доступен для токена
                                searchAvailable = true
                                val groups = searchResult.response?.items ?: emptyList()
                                allResults.addAll(groups.map { SearchItem.Group(it) })
                            } else {
                                // Ошибка VK — помечаем поиск по имени недоступным (нужен ввод ссылки/ID)
                                searchAvailable = false
                            }
                        }

                        // Дополнительно пробуем поиск пользователей (users.search)
                        if (searchAvailable != false) {
                            try {
                                val usersResult = withContext(Dispatchers.IO) {
                                    vkApi.usersSearch(query = trimmed, count = 10, accessToken = token)
                                }
                                if (usersResult.error == null) {
                                    val users = usersResult.response?.items ?: emptyList()
                                    allResults.addAll(users.map { SearchItem.User(it) })
                                }
                            } catch (_: Exception) {
                                // users.search опционален — не валим из-за него весь поиск
                            }
                        }

                        // Есть результаты — показываем и выходим
                        if (allResults.isNotEmpty()) {
                            searchResults = allResults
                            isSearching = false
                            return@launch
                        }

                        // Fallback: пробуем трактовать ввод как screen_name и резолвить напрямую
                        val resolved = resolveDirectInput(trimmed, vkApi, token)
                        if (resolved.isNotEmpty()) {
                            searchResults = resolved
                        } else {
                            errorMessage = if (searchAvailable == false) {
                                "Поиск по имени недоступен для данного токена.\n" +
                                    "Введите ссылку или ID: vk.com/group_name, club12345, 12345"
                            } else {
                                "По запросу «$trimmed» ничего не найдено"
                            }
                        }
                    }
                }
            } catch (_: CancellationException) {
                // Корутина отменена (диалог закрыт или начат новый поиск) — ничего не делаем
                return@launch
            } catch (e: Exception) {
                // Прочие ошибки показываем в UI и публикуем в глобальную шину
                errorMessage = e.message ?: "Ошибка загрузки"
                errorNotifier.emit(
                    source = "VK API",
                    code = null,
                    message = e.message ?: "Неизвестная ошибка",
                    detail = e::class.simpleName,
                )
            }
            isSearching = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = 8.dp,
            modifier = Modifier.width(520.dp).heightIn(max = 600.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // ── Шапка: иконка, заголовок и кнопка закрытия ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Group, null, tint = AppColors.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Выбор сообществ", style = MaterialTheme.typography.h6, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, "Закрыть", tint = AppColors.textTertiary)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Подсказка (показывается, когда поиск по имени недоступен для токена) ──
                if (searchAvailable == false) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AppColors.warningLight,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.Info, null, tint = AppColors.warning, modifier = Modifier.size(16.dp))
                            Text(
                                "Поиск по имени недоступен для вашего токена. " +
                                    "Введите ссылку или ID сообщества / пользователя.\n" +
                                    "Примеры: vk.com/group_name, club12345, id12345, 12345",
                                fontSize = 12.sp,
                                color = AppColors.textSecondary,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ── Поле поиска/ввода с иконкой и кнопкой запуска поиска ──
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            if (searchAvailable == false) "vk.com/group_name, id12345 или ID..."
                            else "Название, ссылка или ID...",
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp)) },
                    trailingIcon = {
                        // Во время поиска показываем индикатор загрузки, иначе — кнопку «Найти»
                        if (isSearching) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { doSearch(inputText) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Search, "Найти", tint = AppColors.primary)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                // ── Чипы выбранных объектов (с кнопкой удаления у каждого) ──
                if (selectedMap.isNotEmpty()) {
                    Text("Выбрано: ${selectedMap.size}", fontSize = 12.sp, color = AppColors.textTertiary)
                    Spacer(Modifier.height(4.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for ((vkId, community) in selectedMap) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = AppColors.primaryLight,
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(community.name, fontSize = 12.sp, color = AppColors.primary, maxLines = 1)
                                    IconButton(
                                        // Удаление объекта из выбора (из списка ID и карты)
                                        onClick = {
                                            selectedIds.remove(vkId)
                                            selectedMap.remove(vkId)
                                        },
                                        modifier = Modifier.size(20.dp),
                                    ) {
                                        Icon(Icons.Filled.Close, null, Modifier.size(14.dp), tint = AppColors.primary)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ── Сообщение об ошибке поиска ──
                errorMessage?.let { msg ->
                    Text(msg, fontSize = 12.sp, color = AppColors.error)
                    Spacer(Modifier.height(4.dp))
                }

                // ── Список результатов поиска ──
                Divider(color = AppColors.border)
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    // Пустое состояние: нет результатов, не идёт поиск и нет ошибки
                    if (searchResults.isEmpty() && !isSearching && errorMessage == null) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    "Введите запрос и нажмите поиск",
                                    color = AppColors.textTertiary,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                    // Строки результатов; клик переключает выбор объекта
                    items(searchResults, key = { it.id }) { item ->
                        val isSelected = item.id in selectedIds
                        SearchItemRow(item, isSelected) {
                            if (isSelected) {
                                selectedIds.remove(item.id)
                                selectedMap.remove(item.id)
                            } else {
                                selectedIds.add(item.id)
                                selectedMap[item.id] = item.toSelectedCommunity()
                            }
                        }
                    }
                }

                // ── Нижняя панель: «Отмена» и «Применить» (с числом выбранных) ──
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        // Подтверждение: отдаём выбор наружу и закрываем диалог
                        onClick = {
                            onSelectionChanged(selectedMap.values.toList())
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        enabled = selectedMap.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Применить (${selectedMap.size})")
                    }
                }
            }
        }
    }
}

// ── Классификация ввода ──

/** Тип ввода: прямые ссылки/ID (DIRECT_IDS) либо свободный текстовый запрос (TEXT_QUERY). */
private enum class InputKind { DIRECT_IDS, TEXT_QUERY }

/**
 * Определяет, что ввёл пользователь: прямую ссылку/ID (URL, числовой ID,
 * префикс club/public/id, screen_name) или свободный текстовый поисковый запрос.
 */
private fun classifyInput(input: String): InputKind {
    val trimmed = input.trim()
    // Содержит URL ВКонтакте
    if (trimmed.contains("vk.com/")) return InputKind.DIRECT_IDS
    // Начинается с префикса club/public/id и цифр
    if (trimmed.matches(Regex("^(club|public|id)\\d+.*", RegexOption.IGNORE_CASE))) return InputKind.DIRECT_IDS
    // Чисто числовой ввод (возможно, несколько ID через запятую/пробел)
    if (trimmed.all { it.isDigit() || it == ',' || it == ' ' } && trimmed.any { it.isDigit() }) return InputKind.DIRECT_IDS
    // Несколько токенов через запятую/пробел, похожих на screen_names
    val parts = trimmed.split(",", " ").filter { it.isNotBlank() }
    if (parts.size > 1 && parts.all { it.matches(Regex("[a-zA-Z0-9_.]+")) }) return InputKind.DIRECT_IDS
    // Один латинский токен, похожий на screen_name (есть подчёркивание или цифра)
    if (parts.size == 1 && trimmed.matches(Regex("[a-zA-Z][a-zA-Z0-9_.]*")) &&
        (trimmed.contains("_") || trimmed.any { it.isDigit() })
    ) return InputKind.DIRECT_IDS
    // Иначе считаем ввод свободным текстовым запросом
    return InputKind.TEXT_QUERY
}

/**
 * Разбирает ввод пользователя на отдельные числовые ID и screen_names.
 * Принимает: числовые ID, screen_names, полные URL (vk.com/xxx), префиксы
 * club/public/id. Несколько значений разделяются запятыми, пробелами или
 * переводами строк.
 *
 * @return пара (список числовых ID, список screen_names).
 */
internal fun parseVkCommunityInput(input: String): Pair<List<Int>, List<String>> {
    // Разбиваем по разделителям и убираем пустые токены
    val parts = input.split(",", "\n", " ")
        .map { it.trim() }
        .filter { it.isNotBlank() }

    val numericIds = mutableListOf<Int>()
    val screenNames = mutableListOf<String>()

    for (part in parts) {
        // Срезаем возможные URL-префиксы, оставляя «хвост» ссылки
        val cleaned = part
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("vk.com/")
            .removePrefix("m.vk.com/")

        // Префикс сообщества club12345 / public12345 — извлекаем числовой ID
        val clubMatch = Regex("^(?:club|public)(\\d+)$").find(cleaned)
        if (clubMatch != null) {
            numericIds.add(clubMatch.groupValues[1].toInt())
            continue
        }

        // Профиль пользователя вида id12345
        val idMatch = Regex("^id(\\d+)$").find(cleaned)
        if (idMatch != null) {
            // Храним как есть: будет отдельно зарезолвлено как пользователь
            // (отрицательная конвенция owner_id здесь не применяется)
            screenNames.add(cleaned)
            continue
        }

        // Просто число — это числовой ID
        val num = cleaned.toIntOrNull()
        if (num != null) {
            numericIds.add(num)
            continue
        }

        // Иначе, если это допустимый screen_name — добавляем в список имён
        if (cleaned.matches(Regex("^[a-zA-Z0-9_.]+$"))) {
            screenNames.add(cleaned)
        }
    }

    return numericIds to screenNames
}

/**
 * Резолвит прямые ID и screen_names в результаты SearchItem.
 * Для числовых ID использует groups.getById (и users.get для не-групп), для
 * screen_names — utils.resolveScreenName с последующим getById/users.get.
 */
private suspend fun resolveDirectInput(
    input: String,
    vkApi: VkApiClient,
    token: String,
): List<SearchItem> {
    val (numericIds, screenNames) = parseVkCommunityInput(input)
    val results = mutableListOf<SearchItem>()

    // Числовые ID сначала пробуем как сообщества
    if (numericIds.isNotEmpty()) {
        val groupResult = withContext(Dispatchers.IO) {
            vkApi.groupsGetByStringIds(numericIds.joinToString(","), token)
        }
        val foundGroups = groupResult.response ?: emptyList()
        results.addAll(foundGroups.map { SearchItem.Group(it) })

        // ID, не найденные как сообщества — пробуем как пользователей
        val foundGroupIds = foundGroups.map { it.id }.toSet()
        val missingIds = numericIds.filter { it !in foundGroupIds }
        if (missingIds.isNotEmpty()) {
            val userResult = withContext(Dispatchers.IO) {
                vkApi.usersGet(missingIds, token)
            }
            val foundUsers = userResult.response ?: emptyList()
            results.addAll(foundUsers.map { SearchItem.User(it) })
        }
    }

    // Screen_names резолвим через utils.resolveScreenName
    for (screenName in screenNames) {
        val name = screenName.removePrefix("id") // обработка случая «id12345»
        if (name != screenName && name.all { it.isDigit() }) {
            // Это «id12345» — резолвим напрямую как пользователя
            val userResult = withContext(Dispatchers.IO) {
                vkApi.usersGet(listOf(name.toInt()), token)
            }
            val users = userResult.response ?: emptyList()
            results.addAll(users.map { SearchItem.User(it) })
            continue
        }

        // Иначе спрашиваем VK, что это за объект (тип и его ID)
        val resolved = withContext(Dispatchers.IO) {
            vkApi.resolveScreenName(screenName, token)
        }
        val data = resolved.response ?: continue
        val objectId = data.objectId ?: continue

        // По типу объекта дозапрашиваем полные данные группы или пользователя
        when (data.type) {
            "group", "page", "event" -> {
                val groupResult = withContext(Dispatchers.IO) {
                    vkApi.groupsGetById(listOf(objectId), token)
                }
                val groups = groupResult.response ?: emptyList()
                results.addAll(groups.map { SearchItem.Group(it) })
            }

            "user" -> {
                val userResult = withContext(Dispatchers.IO) {
                    vkApi.usersGet(listOf(objectId), token)
                }
                val users = userResult.response ?: emptyList()
                results.addAll(users.map { SearchItem.User(it) })
            }
        }
    }

    return results
}

/** Преобразует элемент результата поиска в выбранный объект SelectedCommunity. */
private fun SearchItem.toSelectedCommunity(): SelectedCommunity = when (this) {
    is SearchItem.Group -> SelectedCommunity(
        vkId = group.id,
        name = group.name,
        screenName = group.screenName,
        membersCount = group.membersCount,
        isClosed = group.isClosed != 0,
        type = group.type,
    )

    is SearchItem.User -> SelectedCommunity(
        vkId = user.id,
        name = "${user.firstName} ${user.lastName}".trim(),
        screenName = user.screenName,
        membersCount = user.followersCount,
        isClosed = user.isClosed,
        type = "user",
    )
}

/**
 * Строка одного результата поиска: аватар/инициал, имя, подзаголовок, бейджи и чекбокс.
 *
 * @param item элемент результата (группа или пользователь).
 * @param isSelected выбран ли элемент (подсветка фона/границы и чекбокс).
 * @param onClick переключение выбора по клику на строку или чекбокс.
 */
@Composable
@Suppress("FunctionNaming")
private fun SearchItemRow(item: SearchItem, isSelected: Boolean, onClick: () -> Unit) {
    // Цвета подсветки зависят от того, выбран ли элемент
    val bgColor = if (isSelected) AppColors.primaryLight.copy(alpha = 0.5f) else Color.Transparent
    val borderColor = if (isSelected) AppColors.primary.copy(alpha = 0.3f) else AppColors.border
    // Пользователь рисуется иначе, чем сообщество (иконка vs инициал)
    val isUser = item is SearchItem.User

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = when {
                isSelected -> AppColors.primary
                isUser -> AppColors.secondaryLight
                else -> AppColors.surfaceVariant
            },
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Для пользователя — иконка профиля, для сообщества — первая буква названия
                if (isUser) {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else AppColors.secondary,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(
                        item.displayName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isSelected) Color.White else AppColors.textSecondary,
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.displayName,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = AppColors.textPrimary,
            )
            // Подзаголовок (screen_name), бейдж подписчиков и метки типа/закрытости
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.subtitle?.let {
                    Text(it, fontSize = 11.sp, color = AppColors.textTertiary)
                }
                item.badge?.let {
                    Text(it, fontSize = 11.sp, color = AppColors.textTertiary)
                }
                if (isUser) {
                    Text("пользователь", fontSize = 11.sp, color = AppColors.info)
                }
                // Для закрытого сообщества показываем предупреждающую метку
                if (item is SearchItem.Group && item.group.isClosed != 0) {
                    Text("закрытое", fontSize = 11.sp, color = AppColors.warning)
                }
            }
        }

        Checkbox(
            checked = isSelected,
            onCheckedChange = { onClick() },
            colors = CheckboxDefaults.colors(checkedColor = AppColors.primary),
        )
    }
}

/** Форматирует число подписчиков компактно: 1.2M / 3.4K / точное число + «подписчиков». */
private fun formatMembers(count: Int): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> "$count"
} + " подписчиков"
