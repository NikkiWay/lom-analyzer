package com.example.lomanalyzer

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.lomanalyzer.config.AppConfig
import com.example.lomanalyzer.di.appModule
import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.RetentionManager
import com.example.lomanalyzer.orchestration.SingleInstanceLock
import com.example.lomanalyzer.security.MasterPasswordDialog
import com.example.lomanalyzer.security.TokenVault
import com.example.lomanalyzer.storage.Migrations
import com.example.lomanalyzer.ui.screens.SetupScreen
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.java.KoinJavaComponent.get

fun main() {
    startKoin { modules(appModule) }

    val logger = get<Logger>(Logger::class.java)
    val lock = get<SingleInstanceLock>(SingleInstanceLock::class.java)

    if (!lock.acquire()) {
        logger.error("Another instance is already running. Exiting.")
        stopKoin()
        return
    }

    try {
        initializeBackend(logger)
        launchUi(logger, lock)
    } finally {
        lock.release()
    }
}

private fun initializeBackend(logger: Logger) {
    val config = get<AppConfig>(AppConfig::class.java)
    Migrations.migrate(config.appDataDir.resolve("lom_analyzer.db"))
    logger.event(AppEvent.DB_MIGRATED)
    get<RetentionManager>(RetentionManager::class.java).runRetention()
}

private fun launchUi(logger: Logger, lock: SingleInstanceLock) {
    val tokenVault = get<TokenVault>(TokenVault::class.java)

    application {
        var vaultUnlocked by remember { mutableStateOf(false) }
        var showPasswordDialog by remember { mutableStateOf(true) }

        if (showPasswordDialog && !vaultUnlocked) {
            MasterPasswordDialog(
                isNewVault = !tokenVault.hasStoredVault(),
                onPasswordSubmit = { password ->
                    try {
                        tokenVault.initializeKey(password)
                        vaultUnlocked = true
                        showPasswordDialog = false
                        logger.event(AppEvent.APP_STARTED)
                        true
                    } catch (_: Exception) {
                        false
                    }
                },
                onDismiss = {
                    showPasswordDialog = false
                    lock.release()
                    exitApplication()
                },
            )
        }

        if (vaultUnlocked) {
            Window(
                onCloseRequest = {
                    logger.event(AppEvent.APP_STOPPING)
                    tokenVault.clear()
                    lock.release()
                    stopKoin()
                    exitApplication()
                },
                title = "LOM Analyzer",
            ) {
                MaterialTheme {
                    SetupScreen()
                }
            }
        }
    }
}
