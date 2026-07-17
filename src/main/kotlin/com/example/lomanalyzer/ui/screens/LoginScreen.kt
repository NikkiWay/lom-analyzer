/*
 * НАЗНАЧЕНИЕ
 * Экран входа (логина) в приложение. Показывается до основного окна. Позволяет
 * авторизоваться во ВКонтакте двумя способами: через VK ID (OAuth-редирект с
 * ручной вставкой URL) или вводом готового access_token. Предшествует этапу 1
 * (нужен валидный токен для обращений к VK API в пайплайне).
 *
 * ЧТО ВНУТРИ
 * LoginScreen — корневой Composable экрана с пошаговым процессом (AnimatedContent).
 * LoginStep — enum шагов входа (выбор метода / ожидание VK ID редиректа).
 * ChooseMethodContent — форма выбора способа входа (VK ID или токен).
 * WaitingRedirectContent — шаг вставки URL после авторизации в браузере.
 * openInBrowser — открытие ссылки во внешнем браузере (AWT Desktop).
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop (Material, анимации) — UI и переходы между шагами; AuthManager
 * (security) — старт и завершение VK ID входа, вход по токену; корутины
 * (rememberCoroutineScope) — асинхронное завершение OAuth; PasswordVisualTransformation
 * — скрытие токена при вводе; AWT Desktop — открытие браузера.
 *
 * СВЯЗИ
 * AuthManager сохраняет сессию/токен (TokenVault); при успехе вызывается
 * onLoginSuccess, которое переключает приложение на главное окно (MainContent).
 */
package com.example.lomanalyzer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lomanalyzer.security.AuthManager
import com.example.lomanalyzer.ui.theme.AppColors
import com.example.lomanalyzer.ui.theme.appTextFieldColors
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

/**
 * Экран входа во ВКонтакте (VK ID или access_token).
 * @param authManager менеджер аутентификации (старт/завершение входа, хранение токена).
 * @param onLoginSuccess колбэк успешного входа — переход в главное окно приложения.
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
fun LoginScreen(
    authManager: AuthManager,
    onLoginSuccess: () -> Unit,
) {
    // Локальное состояние формы входа
    var vkAppId by remember { mutableStateOf(authManager.getSessionInfo()?.vkAppId ?: "") } // ID приложения VK (из прошлой сессии, если есть)
    var loginStep by remember { mutableStateOf(LoginStep.CHOOSE_METHOD) } // текущий шаг входа
    var redirectUrl by remember { mutableStateOf("") }       // URL, вставленный пользователем после OAuth-редиректа
    var manualToken by remember { mutableStateOf("") }       // вручную введённый access_token
    var showOtherMethods by remember { mutableStateOf(false) } // развернут ли блок «другие способы входа»
    var errorMessage by remember { mutableStateOf<String?>(null) } // текст ошибки валидации/входа
    var vkIdLoading by remember { mutableStateOf(false) }    // идёт ли асинхронное завершение VK ID входа

    // Scope корутин для асинхронного обмена кода авторизации на токен
    val scope = rememberCoroutineScope()

    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFF0F172A), Color(0xFF1E3A5F), Color(0xFF0F172A)),
    )

    Box(
        modifier = Modifier.fillMaxSize().background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.widthIn(min = 420.dp, max = 460.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = 12.dp,
            backgroundColor = AppColors.surface,
        ) {
            Column(
                modifier = Modifier.padding(36.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── Brand header ──
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.primaryLight,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("L", color = AppColors.primary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    }
                }
                Text("LOM Analyzer", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = AppColors.textPrimary)
                Text(
                    "Войдите для начала работы",
                    fontSize = 14.sp,
                    color = AppColors.textSecondary,
                )

                Divider(color = AppColors.divider)

                // ── Контент: переключается между шагами входа с анимацией ──
                AnimatedContent(
                    targetState = loginStep,
                    transitionSpec = {
                        (fadeIn(tween(250)) + slideInHorizontally(tween(250)) { it / 4 })
                            .togetherWith(fadeOut(tween(150)))
                    },
                ) { step ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        when (step) {
                            LoginStep.CHOOSE_METHOD -> ChooseMethodContent(
                                vkAppId = vkAppId,
                                onVkAppIdChange = { vkAppId = it; errorMessage = null },
                                showOtherMethods = showOtherMethods,
                                onToggleOther = { showOtherMethods = !showOtherMethods },
                                manualToken = manualToken,
                                onTokenChange = { manualToken = it; errorMessage = null },
                                // Старт входа через VK ID: открывает браузер и переводит на шаг вставки URL
                                onVkIdLogin = {
                                    if (vkAppId.isBlank()) {
                                        errorMessage = "Введите ID приложения VK"
                                        return@ChooseMethodContent
                                    }
                                    errorMessage = null
                                    authManager.startVkIdLogin(vkAppId.trim())
                                    redirectUrl = ""
                                    loginStep = LoginStep.WAITING_VKID_REDIRECT
                                },
                                // Вход по готовому access_token: сразу сохраняет сессию и пускает в приложение
                                onTokenLogin = {
                                    if (manualToken.isBlank()) {
                                        errorMessage = "Введите access token"
                                        return@ChooseMethodContent
                                    }
                                    authManager.loginWithToken(
                                        manualToken.trim(),
                                        AuthManager.SessionInfo(vkAppId = vkAppId.trim()),
                                    )
                                    onLoginSuccess()
                                },
                            )

                            LoginStep.WAITING_VKID_REDIRECT -> WaitingRedirectContent(
                                redirectUrl = redirectUrl,
                                onUrlChange = { redirectUrl = it; errorMessage = null },
                                hintText = "Авторизуйтесь в открывшемся браузере, затем скопируйте URL из адресной строки и вставьте ниже.",
                                placeholder = "https://oauth.vk.com/blank.html?code=...",
                                loading = vkIdLoading,
                                // Завершение VK ID входа: асинхронно обмениваем вставленный URL на токен
                                onSubmit = {
                                    if (redirectUrl.isBlank()) {
                                        errorMessage = "Вставьте URL из адресной строки"
                                        return@WaitingRedirectContent
                                    }
                                    vkIdLoading = true
                                    errorMessage = null
                                    scope.launch {
                                        // Обмен кода авторизации (из URL) на access_token через AuthManager
                                        val result = authManager.completeVkIdLogin(
                                            vkAppId.trim(),
                                            redirectUrl.trim(),
                                        )
                                        vkIdLoading = false
                                        // Успех — вход в приложение; ошибка — показать сообщение пользователю
                                        when (result) {
                                            is AuthManager.VkIdLoginResult.Success -> onLoginSuccess()
                                            is AuthManager.VkIdLoginResult.Error ->
                                                errorMessage = result.message
                                        }
                                    }
                                },
                                onBack = { loginStep = LoginStep.CHOOSE_METHOD },
                            )

                            LoginStep.WAITING_REDIRECT -> {
                                // не используется, оставлено для полноты enum
                            }
                        }
                    }
                }

                // ── Плашка ошибки: появляется при непустом errorMessage ──
                AnimatedVisibility(errorMessage != null) {
                    errorMessage?.let {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppColors.errorLight,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Filled.ErrorOutline, null, tint = AppColors.error, modifier = Modifier.size(16.dp))
                                Text(it, color = AppColors.error, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Шаги процесса входа: выбор метода, ожидание VK ID редиректа и зарезервированный WAITING_REDIRECT. */
private enum class LoginStep { CHOOSE_METHOD, WAITING_VKID_REDIRECT, WAITING_REDIRECT }

/**
 * Шаг выбора способа входа: поле ID приложения, кнопка «Войти через VK ID» и
 * раскрываемый блок «другие способы» с вводом access_token.
 */
@Composable
@Suppress("FunctionNaming", "LongParameterList")
private fun ChooseMethodContent(
    vkAppId: String,
    onVkAppIdChange: (String) -> Unit,
    showOtherMethods: Boolean,
    onToggleOther: () -> Unit,
    manualToken: String,
    onTokenChange: (String) -> Unit,
    onVkIdLogin: () -> Unit,
    onTokenLogin: () -> Unit,
) {
    OutlinedTextField(
        colors = appTextFieldColors(),
        value = vkAppId,
        onValueChange = onVkAppIdChange,
        label = { Text("ID приложения VK") },
        placeholder = { Text("Например: 12345678") },
        leadingIcon = { Icon(Icons.Outlined.Apps, null, modifier = Modifier.size(18.dp)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
    )

    // ── Primary: VK ID ──
    Button(
        onClick = onVkIdLogin,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = AppColors.vkBlue,
            contentColor = Color.White,
        ),
        elevation = ButtonDefaults.elevation(defaultElevation = 2.dp),
    ) {
        Icon(Icons.Filled.Verified, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Войти через VK ID", fontWeight = FontWeight.SemiBold)
    }

    // ── Other methods ──
    TextButton(onClick = onToggleOther, modifier = Modifier.fillMaxWidth()) {
        Icon(
            if (showOtherMethods) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            null,
            modifier = Modifier.size(18.dp),
            tint = AppColors.textTertiary,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            if (showOtherMethods) "Скрыть" else "Другие способы входа",
            color = AppColors.textTertiary,
            fontSize = 13.sp,
        )
    }

    AnimatedVisibility(showOtherMethods) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Manual token
            OutlinedTextField(
                colors = appTextFieldColors(),
                value = manualToken,
                onValueChange = onTokenChange,
                label = { Text("Access Token") },
                placeholder = { Text("Вставьте ваш access_token") },
                leadingIcon = { Icon(Icons.Outlined.Key, null, modifier = Modifier.size(18.dp)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            )
            OutlinedButton(
                onClick = onTokenLogin,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Filled.VpnKey, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Войти по токену")
            }
        }
    }
}

/**
 * Шаг ожидания авторизации в браузере: подсказка, поле для вставки URL после
 * редиректа и кнопки «Назад» / «Подтвердить» (с индикатором загрузки).
 */
@Composable
@Suppress("FunctionNaming", "LongParameterList")
private fun WaitingRedirectContent(
    redirectUrl: String,
    onUrlChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    hintText: String = "Авторизуйтесь в открывшемся браузере, затем скопируйте URL из адресной строки и вставьте ниже.",
    placeholder: String = "https://oauth.vk.com/blank.html#...",
    loading: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = AppColors.infoLight,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Info, null, tint = AppColors.info, modifier = Modifier.size(18.dp))
            Text(
                hintText,
                fontSize = 13.sp,
                color = AppColors.textPrimary,
                lineHeight = 18.sp,
            )
        }
    }

    OutlinedTextField(
        colors = appTextFieldColors(),
        value = redirectUrl,
        onValueChange = onUrlChange,
        label = { Text("URL после авторизации") },
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Outlined.Link, null, modifier = Modifier.size(18.dp)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        enabled = !loading,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.weight(1f).height(44.dp),
            shape = RoundedCornerShape(10.dp),
            enabled = !loading,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Назад")
        }
        Button(
            onClick = onSubmit,
            modifier = Modifier.weight(1f).height(44.dp),
            shape = RoundedCornerShape(10.dp),
            enabled = !loading,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text("Подтвердить")
        }
    }
}

/** Открывает URL во внешнем браузере через AWT Desktop; при ошибке пользователь копирует ссылку вручную. */
private fun openInBrowser(url: String) {
    try {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
    } catch (_: Exception) { /* пользователь может скопировать ссылку вручную */ }
}
