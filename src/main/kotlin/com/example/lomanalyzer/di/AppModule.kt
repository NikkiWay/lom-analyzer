package com.example.lomanalyzer.di

import com.example.lomanalyzer.config.AppConfig
import com.example.lomanalyzer.config.ConfigManager
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.observability.MetricsCollector
import com.example.lomanalyzer.orchestration.*
import com.example.lomanalyzer.security.AuditDao
import com.example.lomanalyzer.security.AuditLog
import com.example.lomanalyzer.security.TokenVault
import com.example.lomanalyzer.storage.DatabaseFactory
import com.example.lomanalyzer.storage.dao.*
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

    // Database
    single {
        val config = get<AppConfig>()
        DatabaseFactory(config.appDataDir.resolve("lom_analyzer.db")).also { it.initialize() }
    }

    single {
        get<DatabaseFactory>().database
    }

    // DAOs
    single { SessionDao(get()) }
    single { AuthorDao(get()) }
    single { PostDao(get()) }
    single { CommunityDao(get()) }
    single { LomScoreDao(get()) }
    single { AnomalyDao(get()) }
    single { RiskSignalDao(get()) }
    single { CheckpointDao(get()) }
    single { AuditLogDao(get()) }
    single { RecoveryChoiceDao(get()) }
    single { SessionMetricsDao(get()) }
    single { PersonaAggregateDao(get()) }
    single { HolidayDayStatsDao(get()) }
    single { ProcessedTextDao(get()) }
    single { SentimentResultDao(get()) }
    single { RepostRelationDao(get()) }
    single { DedupGroupDao(get()) }
    single { LinkDao(get()) }

    // AuditLog wired with real DAO
    single<AuditDao> { get<AuditLogDao>() }
    single { AuditLog(logger = get(), dao = get()) }

    // Orchestration
    single { SessionManager(sessionDao = get(), logger = get()) }
    single {
        val config = get<AppConfig>()
        SingleInstanceLock(appDataDir = config.appDataDir, logger = get())
    }
    single { ActiveSessionRegistry() }
    single { CancellationController() }
    single { ProgressReporter() }
    single { CheckpointManager(checkpointDao = get(), logger = get()) }
    single { RetentionManager(sessionDao = get(), logger = get()) }
    single {
        PipelineOrchestrator(
            sessionManager = get(),
            registry = get(),
            checkpointManager = get(),
            progressReporter = get(),
            cancellationController = get(),
            logger = get(),
        )
    }
}
