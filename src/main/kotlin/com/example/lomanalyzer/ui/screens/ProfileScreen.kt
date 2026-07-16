/*
 * НАЗНАЧЕНИЕ
 * Экран профиля и управления данными. Объединяет: информацию об аккаунте VK,
 * управление мастер-паролем (шифрование токена), список активных и удалённых
 * сессий (с восстановлением/безвозвратным удалением), сведения о хранилище
 * (БД, токен, логи) и опасные операции (очистка логов, удаление всех данных).
 *
 * ЧТО ВНУТРИ
 * ProfileScreen — корневой Composable экрана со всеми секциями и диалогами.
 * SectionTitle/InfoRow — заголовок секции и строка «метка -> значение».
 * SessionRow/DeletedSessionRow — строки активной и удалённой сессии.
 * StatusChip — цветной чип статуса сессии.
 * ActionButton/DangerButton — обычная и «опасная» кнопки действий.
 * ChangePasswordDialog/ConfirmDialog — диалоги смены пароля и подтверждения.
 * fileSize/dirSize/formatBytes/clearDirectory — файловые утилиты.
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop (Material, AlertDialog) — UI и диалоги; Koin — получение
 * AuthManager, TokenVault, SessionDao, AppConfig; java.nio.file.Files — работа с
 * файловой системой (размеры, очистка); PasswordVisualTransformation — скрытие пароля.
 *
 * СВЯЗИ
 * TokenVault — шифрованное хранилище VK-токена (AES-256-GCM) и смена мастер-пароля;
 * SessionDao — мягкое/жёсткое удаление и восстановление сессий; AppConfig — пути к
 * данным/логам/хранилищу токена.
 */
package com.example.lomanalyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lomanalyzer.config.AppConfig
import com.example.lomanalyzer.security.AuthManager
import com.example.lomanalyzer.security.TokenVault
import com.example.lomanalyzer.storage.dao.SessionDao
import com.example.lomanalyzer.storage.tables.AnalysisSessions
import com.example.lomanalyzer.ui.theme.AppColors
import com.example.lomanalyzer.ui.theme.ScreenHeader
import com.example.lomanalyzer.ui.theme.SectionCard
import org.koin.java.KoinJavaComponent.get
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Экран профиля и управления данными: аккаунт VK, мастер-пароль, сессии, хранилище.
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
fun ProfileScreen() {
    // Зависимости из Koin: аутентификация, хранилище токена, DAO сессий и конфиг путей
    val authManager = remember { get<AuthManager>(AuthManager::class.java) }
    val tokenVault = remember { get<TokenVault>(TokenVault::class.java) }
    val sessionDao = remember { get<SessionDao>(SessionDao::class.java) }
    val config = remember { get<AppConfig>(AppConfig::class.java) }
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())

    // Списки активных и удалённых сессий и данные сессии VK
    var sessions by remember { mutableStateOf(sessionDao.findAll()) }
    var deletedSessions by remember { mutableStateOf(sessionDao.findSoftDeleted()) }
    val sessionInfo = remember { authManager.getSessionInfo() }

    // Состояния диалогов и статусного сообщения
    var showChangePassword by remember { mutableStateOf(false) }              // диалог смены мастер-пароля
    var showConfirmClearAll by remember { mutableStateOf(false) }             // подтверждение удаления всех данных
    var showConfirmDeleteSession by remember { mutableStateOf<Int?>(null) }   // id сессии для мягкого удаления
    var showConfirmHardDelete by remember { mutableStateOf<Int?>(null) }      // id сессии для безвозвратного удаления
    var showConfirmDeleteToken by remember { mutableStateOf(false) }          // подтверждение удаления токена
    var statusMessage by remember { mutableStateOf<String?>(null) }           // строка-уведомление об успехе операции

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = "Профиль",
            subtitle = "Управление аккаунтом, сессиями и данными",
        )

        // ── Информация об аккаунте VK ──
        SectionCard {
            SectionTitle(Icons.Outlined.Person, "Аккаунт VK")
            if (sessionInfo != null) {
                InfoRow("Имя", sessionInfo.displayName)
                InfoRow("VK ID", sessionInfo.vkUserId.toString())
                InfoRow("App ID", sessionInfo.vkAppId)
                InfoRow("Токен", if (tokenVault.hasToken()) "Сохранён (зашифрован)" else "Отсутствует")
            } else {
                Text("Нет данных сессии", color = AppColors.textTertiary, fontSize = 13.sp)
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DangerButton("Удалить токен", Icons.Outlined.VpnKey) {
                    showConfirmDeleteToken = true
                }
            }
        }

        // ── Мастер-пароль (шифрование токена) ──
        SectionCard {
            SectionTitle(Icons.Outlined.Lock, "Мастер-пароль")
            Text(
                "Мастер-пароль используется для шифрования токена доступа VK (AES-256-GCM).",
                fontSize = 13.sp,
                color = AppColors.textSecondary,
            )
            InfoRow("Хранилище", if (tokenVault.hasStoredVault()) "Создано" else "Не создано")

            Spacer(Modifier.height(4.dp))
            ActionButton("Сменить пароль", Icons.Outlined.LockReset) {
                showChangePassword = true
            }
        }

        // ── Активные сессии анализа ──
        SectionCard {
            SectionTitle(Icons.Outlined.Science, "Сессии анализа (${sessions.size})")

            if (sessions.isEmpty()) {
                Text("Нет активных сессий", color = AppColors.textTertiary, fontSize = 13.sp)
            } else {
                sessions.forEach { session ->
                    SessionRow(
                        id = session[AnalysisSessions.id].value,
                        name = session[AnalysisSessions.name],
                        status = session[AnalysisSessions.status],
                        createdAt = fmt.format(Instant.ofEpochMilli(session[AnalysisSessions.createdAt])),
                        onDelete = { showConfirmDeleteSession = session[AnalysisSessions.id].value },
                    )
                }
            }
        }

        // ── Удалённые сессии (корзина) — секция видна только если есть удалённые ──
        if (deletedSessions.isNotEmpty()) {
            SectionCard {
                SectionTitle(Icons.Outlined.DeleteSweep, "Удалённые сессии (${deletedSessions.size})")

                deletedSessions.forEach { session ->
                    DeletedSessionRow(
                        id = session[AnalysisSessions.id].value,
                        name = session[AnalysisSessions.name],
                        deletedAt = session[AnalysisSessions.deletedAt]?.let {
                            fmt.format(Instant.ofEpochMilli(it))
                        } ?: "",
                        // Восстановление сессии из корзины с обновлением обоих списков
                        onRestore = {
                            sessionDao.restore(session[AnalysisSessions.id].value)
                            sessions = sessionDao.findAll()
                            deletedSessions = sessionDao.findSoftDeleted()
                        },
                        onHardDelete = {
                            showConfirmHardDelete = session[AnalysisSessions.id].value
                        },
                    )
                }
            }
        }

        // ── Хранилище: пути и размеры файлов данных, кнопки очистки ──
        SectionCard {
            SectionTitle(Icons.Outlined.Storage, "Хранилище")
            InfoRow("Директория", config.appDataDir.toString())
            InfoRow("База данных", fileSize(config.appDataDir.resolve("lom_analyzer.db")))
            InfoRow("Хранилище токенов", fileSize(config.tokenVaultFile))
            InfoRow("Логи", dirSize(config.logsDir))

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Очистить логи", Icons.Outlined.CleaningServices) {
                    clearDirectory(config.logsDir)
                    statusMessage = "Логи очищены"
                }
                DangerButton("Удалить все данные", Icons.Outlined.DeleteForever) {
                    showConfirmClearAll = true
                }
            }
        }

        // ── Status message ──
        statusMessage?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                backgroundColor = AppColors.successLight,
                elevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = AppColors.success, modifier = Modifier.size(18.dp))
                    Text(msg, fontSize = 13.sp, color = AppColors.success, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    // ── Диалоги (показываются по соответствующим флагам состояния) ──

    if (showChangePassword) {
        ChangePasswordDialog(
            tokenVault = tokenVault,
            onDismiss = { showChangePassword = false },
            onSuccess = {
                showChangePassword = false
                statusMessage = "Мастер-пароль успешно изменён"
            },
        )
    }

    if (showConfirmDeleteToken) {
        ConfirmDialog(
            title = "Удалить токен?",
            text = "Токен доступа VK будет удалён. Потребуется повторная авторизация.",
            onConfirm = {
                tokenVault.removeToken()
                showConfirmDeleteToken = false
                statusMessage = "Токен удалён"
            },
            onDismiss = { showConfirmDeleteToken = false },
        )
    }

    showConfirmDeleteSession?.let { sessionId ->
        ConfirmDialog(
            title = "Удалить сессию #$sessionId?",
            text = "Сессия будет перемещена в корзину. Вы сможете восстановить её позже.",
            onConfirm = {
                sessionDao.softDelete(sessionId)
                sessions = sessionDao.findAll()
                deletedSessions = sessionDao.findSoftDeleted()
                showConfirmDeleteSession = null
                statusMessage = "Сессия #$sessionId удалена"
            },
            onDismiss = { showConfirmDeleteSession = null },
        )
    }

    // Подтверждение безвозвратного (жёсткого) удаления сессии из корзины
    showConfirmHardDelete?.let { sessionId ->
        ConfirmDialog(
            title = "Удалить навсегда #$sessionId?",
            text = "Сессия будет удалена безвозвратно. Это действие нельзя отменить.",
            onConfirm = {
                sessionDao.hardDelete(sessionId)
                deletedSessions = sessionDao.findSoftDeleted()
                showConfirmHardDelete = null
                statusMessage = "Сессия #$sessionId удалена навсегда"
            },
            onDismiss = { showConfirmHardDelete = null },
        )
    }

    // Подтверждение полной очистки: токен, инфо сессии, логи и БД (необратимо)
    if (showConfirmClearAll) {
        ConfirmDialog(
            title = "Удалить ВСЕ данные?",
            text = "Будут удалены: токен, информация о сессии, логи и база данных. " +
                "Приложение потребует перезапуска. Это действие необратимо!",
            onConfirm = {
                tokenVault.deleteVault()
                Files.deleteIfExists(config.sessionInfoFile)
                clearDirectory(config.logsDir)
                showConfirmClearAll = false
                statusMessage = "Все данные удалены. Перезапустите приложение."
            },
            onDismiss = { showConfirmClearAll = false },
        )
    }
}

// ── Вспомогательные composable ──

/**
 * Заголовок секции профиля: иконка в плашке + текст.
 * @param icon иконка секции.
 * @param title текст заголовка.
 */
@Composable
@Suppress("FunctionNaming")
private fun SectionTitle(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = AppColors.primaryLight,
        ) {
            Icon(icon, null, tint = AppColors.primary, modifier = Modifier.padding(6.dp).size(18.dp))
        }
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

/**
 * Строка «метка слева — значение справа» для отображения сведений.
 * @param label подпись поля.
 * @param value значение поля.
 */
@Composable
@Suppress("FunctionNaming")
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = AppColors.textSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Строка активной сессии в профиле: id, имя, дата, статус и кнопка удаления.
 * @param id id сессии.
 * @param name имя сессии.
 * @param status статус выполнения (строкой).
 * @param createdAt отформатированная дата создания.
 * @param onDelete колбэк удаления сессии.
 */
@Composable
@Suppress("FunctionNaming")
private fun SessionRow(
    id: Int,
    name: String,
    status: String,
    createdAt: String,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = 0.dp,
        backgroundColor = AppColors.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("#$id: $name", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(createdAt, fontSize = 11.sp, color = AppColors.textTertiary)
                    StatusChip(status)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Удалить",
                    tint = AppColors.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Строка удалённой сессии (в корзине): id, имя, дата удаления, кнопки восстановления
 * и безвозвратного удаления.
 * @param id id сессии.
 * @param name имя сессии.
 * @param deletedAt отформатированная дата удаления.
 * @param onRestore колбэк восстановления из корзины.
 * @param onHardDelete колбэк безвозвратного удаления.
 */
@Composable
@Suppress("FunctionNaming")
private fun DeletedSessionRow(
    id: Int,
    name: String,
    deletedAt: String,
    onRestore: () -> Unit,
    onHardDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = 0.dp,
        backgroundColor = AppColors.errorLight.copy(alpha = 0.3f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("#$id: $name", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text("Удалено: $deletedAt", fontSize = 11.sp, color = AppColors.textTertiary)
            }
            IconButton(onClick = onRestore, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.RestoreFromTrash,
                    contentDescription = "Восстановить",
                    tint = AppColors.secondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onHardDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.DeleteForever,
                    contentDescription = "Удалить навсегда",
                    tint = AppColors.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Цветной чип статуса сессии (по строковому коду статуса).
 * @param status статус выполнения сессии.
 */
@Composable
@Suppress("FunctionNaming")
private fun StatusChip(status: String) {
    // Пара «цвет текста -> цвет фона» в зависимости от статуса
    val (color, bg) = when (status) {
        "COMPLETED" -> AppColors.success to AppColors.successLight
        "ANALYZING" -> AppColors.info to AppColors.infoLight
        "FAILED", "CANCELLED" -> AppColors.error to AppColors.errorLight
        else -> AppColors.warning to AppColors.warningLight
    }
    Surface(shape = RoundedCornerShape(4.dp), color = bg) {
        Text(
            status,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Обычная кнопка действия (нейтральный стиль): иконка + текст.
 * @param text подпись кнопки.
 * @param icon иконка.
 * @param onClick колбэк нажатия.
 */
@Composable
@Suppress("FunctionNaming")
private fun ActionButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = AppColors.primaryLight,
            contentColor = AppColors.primary,
        ),
        elevation = ButtonDefaults.elevation(0.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp)
    }
}

/**
 * Кнопка опасного действия (красный стиль): для удаления токена/данных.
 * @param text подпись кнопки.
 * @param icon иконка.
 * @param onClick колбэк нажатия.
 */
@Composable
@Suppress("FunctionNaming")
private fun DangerButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = AppColors.errorLight,
            contentColor = AppColors.error,
        ),
        elevation = ButtonDefaults.elevation(0.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp)
    }
}

// ── Диалоги ──

/**
 * Диалог смены мастер-пароля: ввод и подтверждение нового пароля, валидация и
 * перешифрование токена через TokenVault.
 * @param tokenVault хранилище токена (выполняет смену пароля).
 * @param onDismiss колбэк закрытия без сохранения.
 * @param onSuccess колбэк успешной смены пароля.
 */
@Composable
@Suppress("FunctionNaming")
private fun ChangePasswordDialog(
    tokenVault: TokenVault,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    // Поля нового пароля, подтверждения и текст ошибки валидации
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сменить мастер-пароль", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Введите новый мастер-пароль. Токен будет перешифрован.",
                    fontSize = 13.sp,
                    color = AppColors.textSecondary,
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error = null },
                    label = { Text("Новый пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = { Text("Подтвердите пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, color = AppColors.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Валидация: минимум 4 символа и совпадение паролей; затем перешифрование токена
                    when {
                        newPassword.length < 4 -> error = "Пароль должен содержать минимум 4 символа"
                        newPassword != confirmPassword -> error = "Пароли не совпадают"
                        else -> {
                            try {
                                tokenVault.changePassword(newPassword.toCharArray())
                                onSuccess()
                            } catch (e: Exception) {
                                error = "Ошибка: ${e.message}"
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
        shape = RoundedCornerShape(16.dp),
    )
}

/**
 * Универсальный диалог подтверждения опасного действия (красная кнопка «Удалить»).
 * @param title заголовок диалога.
 * @param text пояснительный текст.
 * @param onConfirm колбэк подтверждения действия.
 * @param onDismiss колбэк отмены.
 */
@Composable
@Suppress("FunctionNaming")
private fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text, fontSize = 13.sp, color = AppColors.textSecondary) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AppColors.error,
                    contentColor = Color.White,
                ),
            ) {
                Text("Удалить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
        shape = RoundedCornerShape(16.dp),
    )
}

// ── Файловые утилиты ──

/** Размер одного файла в человекочитаемом виде; «—», если файла нет. */
private fun fileSize(path: java.nio.file.Path): String {
    if (!Files.exists(path)) return "—"
    val bytes = Files.size(path)
    return formatBytes(bytes)
}

/** Суммарный размер всех файлов в директории (рекурсивно); «—», если её нет. */
private fun dirSize(path: java.nio.file.Path): String {
    if (!Files.exists(path) || !Files.isDirectory(path)) return "—"
    // Обходим дерево, суммируем размеры обычных файлов
    val bytes = Files.walk(path).filter { Files.isRegularFile(it) }.mapToLong { Files.size(it) }.sum()
    return formatBytes(bytes)
}

/** Форматирует число байт в B/KB/MB. */
private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

/** Рекурсивно удаляет содержимое директории, не трогая саму директорию (обход в обратном порядке — сначала вложенное). */
private fun clearDirectory(path: java.nio.file.Path) {
    if (!Files.exists(path) || !Files.isDirectory(path)) return
    Files.walk(path)
        .sorted(Comparator.reverseOrder())
        .filter { it != path }
        .forEach { Files.deleteIfExists(it) }
}
