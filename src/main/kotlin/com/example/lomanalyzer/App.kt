package com.example.lomanalyzer

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.lomanalyzer.di.appModule
import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.security.MasterPasswordDialog
import com.example.lomanalyzer.security.TokenVault
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.java.KoinJavaComponent.get

fun main() {
    startKoin {
        modules(appModule)
    }

    val logger = get<Logger>(Logger::class.java)
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
                    exitApplication()
                },
            )
        }

        if (vaultUnlocked) {
            Window(
                onCloseRequest = {
                    logger.event(AppEvent.APP_STOPPING)
                    tokenVault.clear()
                    stopKoin()
                    exitApplication()
                },
                title = "LOM Analyzer",
            ) {
                MaterialTheme {
                    Text("LOM Analyzer — ready")
                }
            }
        }
    }
}
