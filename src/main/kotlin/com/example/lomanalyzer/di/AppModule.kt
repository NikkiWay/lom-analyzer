package com.example.lomanalyzer.di

import com.example.lomanalyzer.config.AppConfig
import com.example.lomanalyzer.config.ConfigManager
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.observability.MetricsCollector
import com.example.lomanalyzer.analysis.anomaly.*
import com.example.lomanalyzer.analysis.quality.*
import com.example.lomanalyzer.analysis.content.*
import com.example.lomanalyzer.analysis.dedup.*
import com.example.lomanalyzer.analysis.risk.*
import com.example.lomanalyzer.analysis.lom.*
import com.example.lomanalyzer.analysis.topic.*
import com.example.lomanalyzer.nlp.*
import com.example.lomanalyzer.orchestration.*
import com.example.lomanalyzer.persona.*
import com.example.lomanalyzer.export.*
import com.example.lomanalyzer.preprocessing.*
import com.example.lomanalyzer.ui.navigation.AppNavigator
import com.example.lomanalyzer.security.AuditDao
import com.example.lomanalyzer.security.AuditLog
import com.example.lomanalyzer.security.TokenVault
import com.example.lomanalyzer.storage.DatabaseFactory
import com.example.lomanalyzer.storage.dao.*
import com.example.lomanalyzer.vk.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val appModule = module {
    single { ConfigManager() }
    single { get<ConfigManager>().initialize() }
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
    single { get<DatabaseFactory>().database }

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

    // AuditLog
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

    // VK — HTTP client
    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    // VK — API layer
    single { VkRateLimiter(logger = get()) }
    single { VkBackoff(logger = get()) }
    single { VkApiClient(httpClient = get(), rateLimiter = get(), backoff = get()) }
    single { VkExecuteBatcher(apiClient = get()) }
    single { PaginationManager(apiClient = get()) }

    // VK — Collectors
    single {
        BaselineCollector(
            paginationManager = get(),
            postDao = get(),
            checkpointDao = get(),
            progressReporter = get(),
            logger = get(),
        )
    }
    single {
        CurrentCollector(
            paginationManager = get(),
            postDao = get(),
            checkpointDao = get(),
            progressReporter = get(),
            logger = get(),
        )
    }
    single {
        ReposterCollector(
            apiClient = get(),
            postDao = get(),
            repostRelationDao = get(),
            logger = get(),
        )
    }
    single {
        DiscoveryEngine(
            postDao = get(),
            authorDao = get(),
            repostRelationDao = get(),
            logger = get(),
        )
    }
    single { OAuthFlow(tokenVault = get(), logger = get()) }

    // NLP
    single {
        val config = get<AppConfig>()
        PythonServiceManager(
            pythonEnvPath = config.pythonEnvPath,
            httpClient = get(),
            logger = get(),
        )
    }
    single { LocalKotlinNlpService(lemmatizer = get(), languageDetector = get()) }
    single {
        NlpServiceSelector(
            pythonServiceManager = get(),
            localService = get(),
            httpClient = get(),
            logger = get(),
        )
    }

    // Topic filtering
    single { TopicRelevanceFilter() }
    single {
        TopicFilterExecutor(
            postDao = get(),
            processedTextDao = get(),
            ngramMatcher = NgramMatcher(emptyList(), emptyList(), emptyList()),
            topicFilter = get(),
            progressReporter = get(),
            logger = get(),
        )
    }

    // Resources
    single { com.example.lomanalyzer.config.ResourceLoader(logger = get()) }

    // LOM scoring
    single { GammaCalibrator() }
    single { RobustNormalizer() }
    single { BootstrapEstimator() }
    single { DisseminationReachComponent() }
    single { OrthogonalizerT(logger = get()) }
    single { EventActivityScorer(normalizer = get(), logger = get()) }
    single { ReferenceCalibrator() }
    single { RoleCombinator }
    single { ClusterRoleClassifier() }
    single {
        BaseInfluenceScorer(
            postDao = get(),
            authorDao = get(),
            lomScoreDao = get(),
            gammaCalibrator = get(),
            normalizer = get(),
            bootstrapEstimator = get(),
            logger = get(),
        )
    }

    // Anomaly detection
    single { HolidayCalendar() }
    single { SeasonalityNormalizer(calendar = get()) }
    single { RollingZScore() }
    single { VolumeSpikeDetector(calendar = get()) }
    single { ToneShiftDetectorNegative(calendar = get()) }
    single { ToneShiftDetectorPositive(calendar = get()) }
    single { BlockBootstrap() }

    // Content analysis
    single { DictionarySentiment() }
    single { TermExtractor() }
    single {
        PersonaAggregator(
            lomScoreDao = get(),
            personaAggregateDao = get(),
            logger = get(),
        )
    }

    // Deduplication
    single { BoundedJaccard() }
    single {
        DedupPipeline(
            postDao = get(),
            processedTextDao = get(),
            dedupGroupDao = get(),
            boundedJaccard = get(),
            progressReporter = get(),
            logger = get(),
        )
    }
    single {
        OriginalityExecutor(
            postDao = get(),
            dedupGroupDao = get(),
            logger = get(),
        )
    }

    // Preprocessing
    single { LanguageDetectorProxy() }
    single { LemmatizerProxy() }
    single {
        PreprocessingExecutor(
            postDao = get(),
            processedTextDao = get(),
            languageDetector = get(),
            lemmatizer = get(),
            progressReporter = get(),
            logger = get(),
        )
    }

    // Quality
    single { SessionQualityEvaluator() }

    // Export
    single { CsvExporter(logger = get()) }
    single { SafeExporter(csvExporter = get()) }

    // Navigation
    single { AppNavigator(logger = get()) }
}
