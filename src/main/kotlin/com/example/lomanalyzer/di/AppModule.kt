/*
 * НАЗНАЧЕНИЕ
 * Единый Koin-модуль (DI — dependency injection) приложения. Здесь описывается
 * граф всех зависимостей: как создаётся каждый компонент и из чего он собирается.
 * Это «точка сборки» всех четырёх архитектурных модулей (UI, сбор данных, NLP,
 * аналитическое ядро) и их инфраструктуры (конфиг, БД, логирование, безопасность).
 *
 * ЧТО ВНУТРИ
 * Свойство appModule = module { ... } — набор определений Koin. Почти все
 * зависимости объявлены как single (синглтоны на время жизни приложения).
 * Определения сгруппированы комментариями-разделителями по подсистемам:
 * Config, Security, Database, DAOs, Audit, Orchestration, VK HTTP client,
 * VK API layer, VK Collectors, NLP, Topic filtering, Resources, Content analysis,
 * Deduplication, Preprocessing, Scoring, Composite, Inference, Quality, Export,
 * JSON import, Navigation, Error notifications.
 *
 * КЛЮЧЕВОЙ ПРИНЦИП АРХИТЕКТУРЫ (почему модули общаются только через БД)
 * Все четыре модуля изолированы и НЕ вызывают друг друга напрямую — они
 * обмениваются данными только через локальную SQLite (режим WAL)
 * (см. docs/architecture.md, «Изоляция модулей»). На уровне DI это выражается
 * так: общим «связующим звеном» выступают DAO (storage/dao) и единственный
 * объект Database. Исполнители этапов (collectors, executors) получают через
 * конструктор не другие модули, а ИМЕННО DAO — то есть читают входные данные из
 * таблиц и пишут результаты обратно в таблицы. Так, например, аналитическое ядро
 * не вызывает модуль сбора: оно просто читает уже собранные посты/комментарии из
 * БД. Это снижает связанность, позволяет перезапускать пайплайн с контрольных
 * точек (pipeline_checkpoint) и тестировать модули независимо.
 *
 * КАК KOIN СОБИРАЕТ ЗАВИСИМОСТИ
 * single { ... } регистрирует фабрику синглтона: внутри лямбды вызовы get()
 * запрашивают у контейнера уже зарегистрированные зависимости нужного типа
 * (разрешение по типу). Порядок объявлений не важен — Koin разрешает граф лениво
 * при первом обращении. single<Интерфейс> { get<Реализация>() } публикует
 *
 * СВЯЗИ
 * Модуль подключается при старте приложения (App.kt) через startKoin.
 * Конфигурация — config/, логирование/метрики — observability/, БД — storage/,
 * оркестрация пайплайна — orchestration/.
 *
 * БИБЛИОТЕКИ
 * Koin (org.koin.dsl.module) — DI-контейнер; Ktor CIO (HttpClient) — HTTP-клиент
 * для VK API и Python sidecar; kotlinx.serialization (Json) — разбор JSON-ответов.
 */
package com.example.lomanalyzer.di

import com.example.lomanalyzer.config.AppConfig
import com.example.lomanalyzer.config.ConfigManager
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.analysis.content.*
import com.example.lomanalyzer.analysis.dedup.*
import com.example.lomanalyzer.analysis.quality.*
import com.example.lomanalyzer.analysis.topic.*
import com.example.lomanalyzer.nlp.*
import com.example.lomanalyzer.orchestration.*
import com.example.lomanalyzer.export.*
import com.example.lomanalyzer.import.JsonDataImporter
import com.example.lomanalyzer.preprocessing.*
import com.example.lomanalyzer.ui.components.ErrorNotifier
import com.example.lomanalyzer.ui.navigation.AppNavigator
import com.example.lomanalyzer.security.AuthManager
import com.example.lomanalyzer.security.TokenVault
import com.example.lomanalyzer.storage.DatabaseFactory
import com.example.lomanalyzer.storage.dao.*
import com.example.lomanalyzer.vk.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * Корневой Koin-модуль приложения: граф всех зависимостей (синглтонов).
 * Внутри лямбд single { ... } вызовы get() разрешают уже зарегистрированные
 * компоненты по типу.
 */
val appModule = module {
    // ── Config ──
    // Конфигурация. ConfigManager создаётся первым; его initialize() возвращает
    // AppConfig, который публикуется как отдельный single и используется везде,
    // где нужны пути (БД, логи, токены, Python). Также общий Logger.
    single { ConfigManager() }
    single { get<ConfigManager>().initialize() }
    single { Logger() }

    // ── Security ──
    // Зашифрованное хранилище токенов VK. Путь к файлу и число итераций PBKDF2
    // берутся из AppConfig.
    single {
        val config = get<AppConfig>()
        TokenVault(
            vaultFile = config.tokenVaultFile,
            iterations = config.pbkdf2Iterations,
        )
    }

    // ── Database ──
    // Единая точка доступа к SQLite (WAL). DatabaseFactory создаёт файл БД в
    // каталоге данных и инициализирует подключение/миграции; объект Database
    // публикуется отдельно и инъектируется во все DAO — это и есть общий канал
    // обмена данными между модулями.
    single {
        val config = get<AppConfig>()
        DatabaseFactory(config.appDataDir.resolve("lom_analyzer.db")).also { it.initialize() }
    }
    single { get<DatabaseFactory>().database }

    // ── DAOs ──
    // Data Access Objects — типобезопасный доступ к таблицам (Exposed). Каждый DAO
    // получает Database через get(). Именно DAO обеспечивают изоляцию модулей:
    // модули читают/пишут данные только через них, а не вызывая друг друга.
    single { SessionDao(get()) }
    single { AuthorDao(get()) }
    single { PostDao(get()) }
    single { CommunityDao(get()) }
    single { LomScoreDao(get()) }
    single { CheckpointDao(get()) }
    single { SessionMetricsDao(get()) }
    single { ProcessedTextDao(get()) }
    single { SentimentResultDao(get()) }
    single { DedupGroupDao(get()) }
    single { LinkDao(get()) }
    single { CommentDao(get()) }
    single { SessionEventDao(get()) }
    single { NlpResultDao(get()) }
    single { BootstrapIntervalDao(get()) }
    single { CompositeDao(get()) }

    // ── Audit ──

    // ── Orchestration ──
    // Оркестрация пайплайна: управление сессиями, единственный экземпляр приложения,
    // реестр активных сессий, отмена, прогресс, кулдаун, контрольные точки.
    single { SessionManager(sessionDao = get(), logger = get()) }
    single {
        val config = get<AppConfig>()
        // Блокировка единственного экземпляра приложения (по каталогу данных)
        SingleInstanceLock(appDataDir = config.appDataDir, logger = get())
    }
    single { ActiveSessionRegistry() }
    single { ActiveSessionHolder() }
    single { CancellationController() }
    single { ProgressReporter() }
    single { CooldownState() }
    single { CheckpointManager(checkpointDao = get(), logger = get()) }
    // Центральный оркестратор: связывает управление сессиями, контрольные точки,
    // прогресс и отмену в единый прогон пайплайна.
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
    // Запуск пайплайна (управление жизненным циклом прогона и активной сессией).
    single {
        PipelineLauncher(
            orchestrator = get(),
            cancellationController = get(),
            sessionHolder = get(),
            logger = get(),
        )
    }
    // PipelineWiring — регистрация исполнителей всех 10 этапов в оркестраторе.
    // Это самая «толстая» зависимость: ей передаются ВСЕ DAO, коллекторы VK,
    // исполнители обработки/оценок/качества и экспортёры. Обратите внимание:
    // исполнители получают DAO, а не другие модули — поэтому общение между этапами
    // идёт исключительно через таблицы БД (изоляция модулей).
    single {
        PipelineWiring(
            orchestrator = get(),
            sessionManager = get(),
            authManager = get(),
            postDao = get(),
            sessionDao = get(),
            sessionMetricsDao = get(),
            linkDao = get(),
            vkApiClient = get(),
            communityPostCollector = get(),
            newsfeedSearchCollector = get(),
            authorWallCollector = get(),
            commentCollector = get(),
            authorProfileCollector = get(),
            sessionEventService = get(),
            checkpointService = get(),
            preprocessingExecutor = get<PreprocessingExecutor>(),
            topicFilterExecutor = get<TopicFilterExecutor>(),
            dedupPipeline = get<DedupPipeline>(),
            originalityExecutor = get<OriginalityExecutor>(),
            nlpServiceSelector = get(),
            scoringExecutor = get(),
            inferenceExecutor = get(),
            compositeRolesExecutor = get(),
            qualityCheckExecutor = get(),
            jsonDataImporter = get(),
            progressReporter = get(),
            cooldownState = get(),
            logger = get(),
        )
    }

    // ── VK HTTP client ──
    // Единый Ktor HTTP-клиент (движок CIO) для запросов к VK API. ContentNegotiation
    // c kotlinx.serialization Json: ignoreUnknownKeys/ isLenient — устойчивость к
    // незнакомым/нестрогим полям ответов VK. HttpTimeout — таймауты запроса/соединения/сокета.
    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }
        }
    }

    // ── VK API layer ──
    // Слой работы с VK API: ограничитель частоты запросов (rate limiter),
    // экспоненциальный backoff при ошибках, клиент API, батчер execute (склейка
    // вызовов в один execute) и менеджер пагинации.
    single { VkRateLimiter(logger = get()) }
    single { VkBackoff(logger = get()) }
    single { VkApiClient(httpClient = get(), rateLimiter = get(), backoff = get()) }
    single { VkExecuteBatcher(apiClient = get()) }
    single { PaginationManager(apiClient = get()) }

    // ── VK Collectors ──
    // Коллекторы данных (этапы 2–4). Каждый коллектор пишет результат напрямую в
    // соответствующие DAO (postDao, authorDao, commentDao, linkDao) — модуль сбора
    // не зависит от аналитики, та позже читает эти таблицы.
    single {
        // Сбор публикаций сообществ; окно (фон либо тематический период)
        // передаётся аргументом вызова, а не отдельным классом на окно
        CommunityPostCollector(
            paginationManager = get(),
            postDao = get(),
            checkpointDao = get(),
            progressReporter = get(),
            logger = get(),
        )
    }
    single { SessionEventService(sessionEventDao = get(), logger = get()) }
    single {
        // Сбор комментариев к постам
        CommentCollector(
            apiClient = get(),
            postDao = get(),
            commentDao = get(),
            sessionEventService = get(),
            progressReporter = get(),
            logger = get(),
        )
    }
    single {
        // Сбор профилей авторов и их фоновых постов; связи через linkDao
        AuthorProfileCollector(
            apiClient = get(),
            authorDao = get(),
            postDao = get(),
            linkDao = get(),
            sessionEventService = get(),
            progressReporter = get(),
            logger = get(),
        )
    }
    single {
        // Поиск по ленте новостей (newsfeed.search) с сегментацией по датам
        NewsfeedSearchCollector(
            apiClient = get(),
            postDao = get(),
            sessionEventService = get(),
            progressReporter = get(),
            logger = get(),
        )
    }
    single {
        // Сбор стен авторов (wall.get); cooldownState — пауза при flood control VK
        AuthorWallCollector(
            apiClient = get(),
            postDao = get(),
            authorDao = get(),
            linkDao = get(),
            sessionEventService = get(),
            checkpointService = get(),
            cooldownState = get(),
            progressReporter = get(),
            logger = get(),
        )
    }
    single { CheckpointService(db = get(), logger = get()) }
    single { OAuthFlow(tokenVault = get(), logger = get(), httpClient = get()) }
    single {
        val config = get<AppConfig>()
        // Менеджер авторизации VK; путь к файлу сессии берётся из AppConfig
        AuthManager(
            oAuthFlow = get(),
            tokenVault = get(),
            logger = get(),
            sessionInfoFile = config.sessionInfoFile,
        )
    }

    // ── NLP ──
    // NLP-модуль с двумя режимами. PythonServiceManager управляет Python sidecar
    // (FastAPI), LocalKotlinNlpService — резервный режим на Kotlin. NlpServiceSelector
    // выбирает основной/резервный режим и кеширует результаты в nlpResultDao.
    single {
        val config = get<AppConfig>()
        PythonServiceManager(
            pythonEnvPath = config.pythonEnvPath,
            httpClient = get(),
            logger = get(),
            // Вывод sidecar пишется в файл: иначе он копится в конвейере и по
            // заполнении буфера ОС намертво блокирует процесс (см. start()).
            sidecarLogFile = config.logsDir.resolve("python_sidecar.log"),
        )
    }
    single { LocalKotlinNlpService(lemmatizer = get(), languageDetector = get()) }
    single {
        NlpServiceSelector(
            pythonServiceManager = get(),
            localService = get(),
            httpClient = get(),
            logger = get(),
            nlpResultDao = get(),
        )
    }

    // ── Topic filtering ──
    // Исполнитель тематической фильтрации (этап 6, двухпроходный L1/L2). Читает
    // посты/тексты из DAO, использует NLP-селектор и лемматизатор.
    single {
        TopicFilterExecutor(
            postDao = get(),
            processedTextDao = get(),
            sessionDao = get(),
            nlpServiceSelector = get(),
            lemmatizer = get(),
            progressReporter = get(),
            sessionEventService = get(),
            logger = get(),
        )
    }

    // ── Resources ──
    // Загрузчик встроенных ресурсов (словарь sentilex, тест-корпус).
    single { com.example.lomanalyzer.config.ResourceLoader(logger = get()) }

    // ── Content analysis (dictionary sentiment for fallback NLP) ──
    // Словарный сентимент (резервный режим NLP) и извлечение терминов.
    single { DictionarySentiment() }

    // ── Deduplication ──
    // Дедупликация (этап 5). NormalizedLevenshtein — метрика близости по леммам
    // (порог 0.90); DedupPipeline формирует группы near-дубликатов; OriginalityExecutor
    // классифицирует оригинальность постов по группам.
    single { NormalizedLevenshtein() }
    single {
        DedupPipeline(
            postDao = get(),
            processedTextDao = get(),
            dedupGroupDao = get(),
            levenshtein = get(),
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

    // ── Preprocessing ──
    // Препроцессинг (этап 5): прокси языка и лемматизатора + исполнитель, который
    // чистит тексты, лемматизирует, определяет язык и пишет результаты в DAO.
    single { LanguageDetectorProxy() }
    single { LemmatizerProxy() }
    single {
        PreprocessingExecutor(
            postDao = get(),
            processedTextDao = get(),
            commentDao = get(),
            sentimentResultDao = get(),
            nlpServiceSelector = get(),
            languageDetector = get(),
            lemmatizer = get(),
            progressReporter = get(),
            logger = get(),
        )
    }

    // ── Scoring (11 quantitative scores, diploma E.4) ──
    // Исполнитель этапа 7: расчёт 11 количественных оценок по 4 осям. Читает
    // посты/авторов/комментарии/сентимент/связи из DAO, пишет оценки в lomScoreDao.
    single {
        com.example.lomanalyzer.analysis.scoring.ScoringExecutor(
            postDao = get(),
            authorDao = get(),
            commentDao = get(),
            sentimentResultDao = get(),
            lomScoreDao = get(),
            linkDao = get(),
            progressReporter = get(),
            logger = get(),
        )
    }

    // ── Composite scores + role classification (diploma 2.1.6, E.4.6) ──
    // Исполнитель этапа 9: z-нормализация, композиты, адаптивные пороги и квадрантные
    // роли. Читает оценки из lomScoreDao, пишет композиты в compositeDao.
    single {
        com.example.lomanalyzer.analysis.composite.CompositeRolesExecutor(
            lomScoreDao = get(),
            compositeDao = get(),
            sessionEventService = get(),
            progressReporter = get(),
            logger = get(),
        )
    }

    // ── Inference (bootstrap, diploma E.3) ──
    // Исполнитель этапа 8: бутстрап доверительных интервалов (одноуровневый B=1000;
    // двухуровневый 300×100 только для Resp_a). Пишет интервалы в bootstrapIntervalDao.
    single {
        com.example.lomanalyzer.analysis.inference.InferenceExecutor(
            postDao = get(),
            commentDao = get(),
            sentimentResultDao = get(),
            lomScoreDao = get(),
            authorDao = get(),
            bootstrapIntervalDao = get(),
            linkDao = get(),
            progressReporter = get(),
            logger = get(),
        )
    }

    // ── Quality (diploma 2.2.8) ──
    // Исполнитель этапа 10: достаточность данных + 8 индикаторов качества сессии.
    // SessionQualityEvaluator — чистая логика оценки; исполнитель агрегирует данные
    // из множества DAO.
    single { SessionQualityEvaluator() }
    single {
        com.example.lomanalyzer.analysis.quality.QualityCheckExecutor(
            lomScoreDao = get(),
            bootstrapIntervalDao = get(),
            compositeDao = get(),
            postDao = get(),
            commentDao = get(),
            linkDao = get(),
            authorDao = get(),
            sessionEventDao = get(),
            sentimentResultDao = get(),
            sessionDao = get(),
            sessionQualityEvaluator = get(),
            sessionEventService = get(),
            progressReporter = get(),
            logger = get(),
        )
    }

    // ── Export ──
    // Экспорт результатов. CsvExporter — низкоуровневый CSV; SafeExporter — обёртка
    // privacy-first над ним; JsonExporter — JSON serialization.
    single { CsvExporter(logger = get()) }
    single { SafeExporter(csvExporter = get()) }
    single { com.example.lomanalyzer.export.JsonExporter(logger = get()) }

    // ── JSON data import ──
    // Импорт целой сессии из JSON (альтернатива сбору через VK API): пишет данные
    // напрямую в DAO сообществ/авторов/постов/комментариев/связей.
    single {
        JsonDataImporter(
            communityDao = get(),
            authorDao = get(),
            postDao = get(),
            commentDao = get(),
            linkDao = get(),
            progressReporter = get(),
            logger = get(),
        )
    }

    // ── Navigation ──
    // Навигатор экранов UI (модуль 1).
    single { AppNavigator(logger = get()) }

    // ── Error notifications ──
    // Сервис показа уведомлений об ошибках в UI.
    single { ErrorNotifier() }
}
