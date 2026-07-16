/*
 * НАЗНАЧЕНИЕ
 * Точка входа приложения LOM Analyzer (Compose Desktop). Здесь поднимается весь
 * backend (DI, миграции БД, восстановление сессий, регистрация исполнителей
 * пайплайна) и запускается UI: разблокировка хранилища токенов, экран входа и
 * главный экран. Отсюда начинается жизненный цикл всего приложения.
 *
 * ЧТО ВНУТРИ
 * main — оркестрация запуска; initializeBackend — миграции БД, восстановление и
 * wiring пайплайна; recoverStaleSessions — починка/очистка сессий после
 * предыдущего запуска; launchUi — Compose-приложение с диалогом мастер-пароля,
 * окном, экраном логина и MainContent.
 *
 * АЛГОРИТМ / ПОТОК ВЫПОЛНЕНИЯ
 * 1) startKoin — инициализация DI. 2) SingleInstanceLock.acquire — не допустить
 * второй экземпляр. 3) initializeBackend: Migrations.migrate (Flyway V1–V10),
 * recoverStaleSessions, PipelineWiring.wire. 4) launchUi: разблокировать
 * TokenVault мастер-паролем, затем показать вход или главный экран. При закрытии
 * окна — остановка Python sidecar, очистка хранилища, снятие блокировки, stopKoin.
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop (application/Window/@Composable) — декларативный UI; Koin
 * (startKoin/stopKoin/get) — внедрение зависимостей; Flyway (через Migrations)
 * — миграции SQLite; собственные TokenVault/AuthManager — шифрованное хранилище
 * токенов VK и авторизация.
 *
 * СВЯЗИ
 * appModule (DI), PipelineWiring (этапы), PythonServiceManager (sidecar),
 * ActiveSessionRegistry/ProgressReporter (состояние пайплайна), SessionDao (БД).
 */
package com.example.lomanalyzer

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.lomanalyzer.config.AppConfig
import com.example.lomanalyzer.di.appModule
import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.ActiveSessionRegistry
import com.example.lomanalyzer.orchestration.PipelineWiring
import com.example.lomanalyzer.orchestration.PythonServiceManager
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.orchestration.SessionStatus
import com.example.lomanalyzer.orchestration.SingleInstanceLock
import com.example.lomanalyzer.storage.dao.SessionDao
import com.example.lomanalyzer.storage.tables.AnalysisSessions
import com.example.lomanalyzer.security.AuthManager
import com.example.lomanalyzer.security.MasterPasswordDialog
import com.example.lomanalyzer.security.TokenVault
import com.example.lomanalyzer.storage.Migrations
import com.example.lomanalyzer.ui.navigation.MainContent
import com.example.lomanalyzer.ui.screens.LoginScreen
import com.example.lomanalyzer.ui.theme.AppTheme
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.java.KoinJavaComponent.get

/**
 * Точка входа приложения: DI, защита от второго экземпляра, инициализация
 * backend и запуск UI. Блокировка снимается в finally при выходе.
 */
fun main() {
    // Поднимаем Koin с главным модулем зависимостей
    startKoin { modules(appModule) }

    // Получаем из DI логгер и файловую блокировку единственного экземпляра
    val logger = get<Logger>(Logger::class.java)
    val lock = get<SingleInstanceLock>(SingleInstanceLock::class.java)

    // Если другой экземпляр уже работает — выходим, не трогая его БД
    if (!lock.acquire()) {
        logger.error("Another instance is already running. Exiting.")
        stopKoin()
        return
    }

    try {
        // Готовим backend (БД, восстановление, wiring), затем запускаем UI
        initializeBackend(logger)
        launchUi(logger, lock)
    } finally {
        // Гарантированно снимаем блокировку при любом выходе из приложения
        lock.release()
    }
}

/**
 * Инициализация backend: применяет миграции БД (Flyway V1–V10), восстанавливает
 * зависшие сессии и регистрирует исполнителей всех этапов пайплайна.
 */
private fun initializeBackend(logger: Logger) {
    val config = get<AppConfig>(AppConfig::class.java)
    // Применяем миграции к локальной БД SQLite
    Migrations.migrate(config.appDataDir.resolve("lom_analyzer.db"))
    logger.event(AppEvent.DB_MIGRATED)
    // Чиним/чистим сессии, оставшиеся от прошлого запуска
    recoverStaleSessions(logger)
    // Связываем стадии пайплайна с их исполнителями
    get<PipelineWiring>(PipelineWiring::class.java).wire()
}

/**
 * On startup, reset any sessions stuck in ANALYZING/COLLECTING due to a previous crash.
 * Also force-reset the in-memory ActiveSessionRegistry and ProgressReporter.
 * При старте чинит сессии, «застрявшие» в ANALYZING/COLLECTING после прошлого сбоя,
 * и сбрасывает in-memory реестр активной сессии и репортёр прогресса.
 */
private fun recoverStaleSessions(logger: Logger) {
    val sessionDao = get<SessionDao>(SessionDao::class.java)
    val registry = get<ActiveSessionRegistry>(ActiveSessionRegistry::class.java)
    val progressReporter = get<ProgressReporter>(ProgressReporter::class.java)

    // Сбрасываем in-memory состояние: новый запуск стартует с чистого листа
    registry.forceReset()
    progressReporter.reset()

    // Статусы, означающие «анализ шёл, но процесс умер» — их переводим в INCOMPLETE
    val staleStatuses = setOf(
        SessionStatus.ANALYZING.name,
        SessionStatus.COLLECTING.name,
        SessionStatus.PAUSED_PENDING_RECOVERY.name,
    )
    for (session in sessionDao.findAll()) {
        val status = session[AnalysisSessions.status]
        if (status in staleStatuses) {
            val id = session[AnalysisSessions.id].value
            // Помечаем как незавершённую — далее она попадёт под очистку ниже
            sessionDao.updateStatus(id, SessionStatus.INCOMPLETE.name)
            logger.warn("Recovered stale session #$id from status $status → INCOMPLETE")
        }
    }

    // Clean up old incomplete/failed sessions from previous runs
    // Удаляем неудавшиеся/незавершённые сессии прошлых запусков, чтобы не копить мусор
    val cleanupStatuses = setOf(
        SessionStatus.INCOMPLETE.name,
        SessionStatus.FAILED.name,
        SessionStatus.CANCELLED.name,
    )
    for (session in sessionDao.findAll()) {
        val status = session[AnalysisSessions.status]
        if (status in cleanupStatuses) {
            val id = session[AnalysisSessions.id].value
            // Полное удаление записи сессии (hard delete)
            sessionDao.hardDelete(id)
            logger.warn("Cleaned up old session #$id with status $status")
        }
    }
}

/**
 * Запускает Compose-приложение: сначала диалог мастер-пароля для разблокировки
 * хранилища токенов, затем — окно с экраном входа VK или главным экраном.
 */
private fun launchUi(logger: Logger, lock: SingleInstanceLock) {
    // Шифрованное хранилище токенов и менеджер авторизации VK из DI
    val tokenVault = get<TokenVault>(TokenVault::class.java)
    val authManager = get<AuthManager>(AuthManager::class.java)

    application {
        // Состояния UI верхнего уровня (remember — переживают рекомпозицию):
        // разблокировано ли хранилище, показывать ли диалог пароля, вошёл ли пользователь
        var vaultUnlocked by remember { mutableStateOf(false) }
        var showPasswordDialog by remember { mutableStateOf(true) }
        var authenticated by remember { mutableStateOf(false) }

        // Пока хранилище не разблокировано — показываем диалог мастер-пароля
        if (showPasswordDialog && !vaultUnlocked) {
            MasterPasswordDialog(
                // Новое хранилище (задать пароль) или существующее (ввести пароль)
                isNewVault = !tokenVault.hasStoredVault(),
                onPasswordSubmit = { password ->
                    try {
                        // Пытаемся вывести ключ из пароля; ошибка → неверный пароль
                        tokenVault.initializeKey(password)
                        vaultUnlocked = true
                        showPasswordDialog = false
                        logger.event(AppEvent.APP_STARTED)
                        // После разблокировки проверяем, есть ли действующий вход в VK
                        authenticated = authManager.isLoggedIn()
                        true
                    } catch (_: Exception) {
                        // Неверный пароль — диалог сообщит об ошибке (возврат false)
                        false
                    }
                },
                onDismiss = {
                    // Пользователь отказался вводить пароль — корректный выход
                    showPasswordDialog = false
                    lock.release()
                    exitApplication()
                },
            )
        }

        // Главное окно показываем только после разблокировки хранилища
        if (vaultUnlocked) {
            Window(
                onCloseRequest = {
                    logger.event(AppEvent.APP_STOPPING)
                    // Kill Python sidecar before exit
                    // Перед выходом гасим Python sidecar, чтобы не оставить висящий процесс
                    try {
                        get<PythonServiceManager>(PythonServiceManager::class.java).stop()
                    } catch (_: Exception) { /* already stopped */ }
                    // Очищаем ключи из памяти, снимаем блокировку и останавливаем DI
                    tokenVault.clear()
                    lock.release()
                    stopKoin()
                    exitApplication()
                },
                title = "LOM Analyzer",
            ) {
                AppTheme {
                    // Если вошли в VK — главный экран; иначе — экран логина
                    if (authenticated) {
                        MainContent(onLogout = {
                            // Выход из VK возвращает на экран логина
                            authManager.logout()
                            authenticated = false
                        })
                    } else {
                        LoginScreen(
                            authManager = authManager,
                            onLoginSuccess = { authenticated = true },
                        )
                    }
                }
            }
        }
    }
}
