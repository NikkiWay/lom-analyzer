package com.example.lomanalyzer.di

import com.example.lomanalyzer.config.AppConfig
import com.example.lomanalyzer.config.ConfigManager
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.observability.MetricsCollector
import com.example.lomanalyzer.security.AuditLog
import com.example.lomanalyzer.security.TokenVault
import org.koin.dsl.module

val appModule = module {
    single { ConfigManager() }

    single {
        get<ConfigManager>().initialize()
    }

    single { Logger() }

    single { MetricsCollector() }

    single {
        val config = get<AppConfig>()
        TokenVault(
            vaultFile = config.tokenVaultFile,
            iterations = config.pbkdf2Iterations,
        )
    }

    single { AuditLog(logger = get()) }
}
