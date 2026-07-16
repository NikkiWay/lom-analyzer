/*
 * НАЗНАЧЕНИЕ
 * Модальный диалог Compose для ввода мастер-пароля: используется как при
 * создании нового хранилища (vault), так и при разблокировке существующего.
 * Этот пароль через PBKDF2 превращается в ключ AES-256-GCM для TokenVault.
 * Часть модуля безопасности (UI-слой).
 *
 * ЧТО ВНУТРИ
 * @Composable-функция MasterPasswordDialog — отдельное диалоговое окно с полем
 * пароля (и подтверждением для нового vault), переключателем видимости пароля,
 * блоком ошибки и кнопкой подтверждения.
 *
 * ПОВЕДЕНИЕ
 * При нажатии кнопки проверяются: непустой пароль, совпадение с подтверждением
 * (для нового vault) и результат onPasswordSubmit (false трактуется как неверный
 * пароль и показывает ошибку). Состояние полей хранится локально через remember.
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop (Material, DialogWindow), иконки Material Icons; цвета берутся
 * из AppColors темы приложения.
 *
 * СВЯЗИ
 * Колбэк onPasswordSubmit обычно вызывает TokenVault.initializeKey; вызывается
 * из слоя авторизации (AuthManager/UI) при старте приложения.
 */
package com.example.lomanalyzer.security

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow
import com.example.lomanalyzer.ui.theme.AppColors

/**
 * Диалоговое окно ввода мастер-пароля для шифрованного хранилища токена.
 *
 * @param isNewVault true — режим создания пароля (с подтверждением); false — режим разблокировки.
 * @param onPasswordSubmit колбэк проверки пароля; возвращает true при успехе, false — неверный пароль.
 * @param onDismiss колбэк закрытия диалога без ввода.
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
fun MasterPasswordDialog(
    isNewVault: Boolean,
    onPasswordSubmit: (CharArray) -> Boolean,
    onDismiss: () -> Unit,
) {
    // Локальное UI-состояние диалога (сохраняется между рекомпозициями)
    var password by remember { mutableStateOf("") }            // введённый пароль
    var confirmPassword by remember { mutableStateOf("") }     // подтверждение (только для нового vault)
    var errorMessage by remember { mutableStateOf<String?>(null) } // текст ошибки под полями
    var showPassword by remember { mutableStateOf(false) }     // показывать пароль открытым текстом

    // Отдельное диалоговое окно ОС; заголовок и высота зависят от режима (создание/вход)
    DialogWindow(
        onCloseRequest = onDismiss,
        title = if (isNewVault) "LOM Analyzer — Создание пароля" else "LOM Analyzer — Вход",
        state = DialogState(width = 520.dp, height = if (isNewVault) 500.dp else 400.dp),
        resizable = false,
    ) {
        MaterialTheme(
            colors = lightColors(
                primary = AppColors.primary,
                error = AppColors.error,
                surface = AppColors.surface,
                background = AppColors.contentBg,
                onSurface = AppColors.textPrimary,
            ),
            shapes = Shapes(
                small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(12.dp),
                large = RoundedCornerShape(16.dp),
            ),
        ) {
            Surface(color = AppColors.contentBg, modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // ── Icon ──
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isNewVault) AppColors.primaryLight else AppColors.secondaryLight,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isNewVault) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = if (isNewVault) AppColors.primary else AppColors.secondary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }

                    Text(
                        if (isNewVault) "Создайте мастер-пароль"
                        else "Введите мастер-пароль",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = AppColors.textPrimary,
                    )
                    Text(
                        if (isNewVault) "Пароль защитит ваш VK-токен шифрованием AES-256"
                        else "Для разблокировки зашифрованного хранилища",
                        fontSize = 13.sp,
                        color = AppColors.textSecondary,
                    )

                    Spacer(Modifier.height(4.dp))

                    // ── Password field ──
                    // Поле пароля: при изменении сбрасываем ошибку; видимость зависит от showPassword
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        label = { Text("Пароль") },
                        leadingIcon = { Icon(Icons.Outlined.Key, null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    )

                    // ── Confirm field ──
                    // Поле подтверждения показывается только при создании нового хранилища
                    if (isNewVault) {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; errorMessage = null },
                            label = { Text("Подтвердите пароль") },
                            leadingIcon = { Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(18.dp)) },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        )
                    }

                    // ── Error ──
                    // Блок ошибки с анимацией появления — виден только при наличии errorMessage
                    AnimatedVisibility(errorMessage != null) {
                        errorMessage?.let {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AppColors.errorLight,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Filled.ErrorOutline, null, tint = AppColors.error, modifier = Modifier.size(16.dp))
                                    Text(it, color = AppColors.error, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // ── Submit ──
                    // Кнопка подтверждения: последовательно валидирует ввод
                    Button(
                        onClick = {
                            when {
                                // 1) пароль не должен быть пустым
                                password.isBlank() -> errorMessage = "Пароль не может быть пустым"
                                // 2) при создании vault пароль и подтверждение должны совпадать
                                isNewVault && password != confirmPassword -> errorMessage = "Пароли не совпадают"
                                // 3) проверяем пароль колбэком; false — неверный пароль (расшифровка не прошла)
                                !onPasswordSubmit(password.toCharArray()) -> errorMessage = "Неверный пароль"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.primary),
                        elevation = ButtonDefaults.elevation(defaultElevation = 2.dp),
                    ) {
                        Icon(
                            if (isNewVault) Icons.Filled.Add else Icons.Filled.LockOpen,
                            null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isNewVault) "Создать" else "Разблокировать",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
