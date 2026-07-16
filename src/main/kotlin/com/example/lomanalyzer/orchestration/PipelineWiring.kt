/*
 * НАЗНАЧЕНИЕ
 * Связывает 10 стадий пайплайна (PipelineStage) с их реальными исполнителями.
 * Это «монтажная плата» оркестрации: метод wire регистрирует в
 * PipelineOrchestrator по одной лямбде-StageExecutor на каждую стадию. Именно
 * здесь видно, как этапы 1–10 из docs/algorithm.md превращаются в конкретные
 * вызовы коллекторов VK, препроцессинга, фильтрации, оценок, бутстрапа,
 * классификации, качества и экспорта, и где ставятся контрольные точки PHASE_1..5.
 *
 * ЧТО ВНУТРИ
 * Класс PipelineWiring с большим числом зависимостей (коллекторы VK, DAO,
 * исполнители анализа, NLP, экспорт/импорт, прогресс, cooldown) и единственным
 * методом wire, регистрирующим исполнителей всех стадий.
 *
 * АЛГОРИТМ / ПОТОК ВЫПОЛНЕНИЯ ПО СТАДИЯМ
 *   1 SessionInit:    статус COLLECTING, инициализация NLP, checkpoint PHASE_1.
 *   2 DataCollection: либо импорт из JSON, либо трёхфазный сбор VK A→B→C
 *      (Phase A — тематические посты; Phase B — реестр авторов; пауза-cooldown
 *      и проба wall.get; Phase C — стены авторов; затем комментарии). Между
 *      шагами — checkpoint PHASE_2. Если VK включает flood control — Phase C
 *      пропускается с предупреждением, анализ продолжается на данных A и B.
 *   3 Preprocessing:  очистка/лемматизация, checkpoint PHASE_3.
 *   4 TopicFiltering: двухпроходная фильтрация + дедуп + оригинальность, PHASE_3.
 *   5 Scoring:        11 оценок по 4 осям (формулы E.4), checkpoint PHASE_4.
 *   6 Bootstrap:      одно- и двухуровневый бутстрап (E.3), checkpoint PHASE_4.
 *   7 CompositeRoles: композиты, пороги, роли, checkpoint PHASE_5.
 *   8 QualityCheck:   достаточность + индикаторы качества, checkpoint PHASE_5.
 *   9 Export:         результаты доступны для экспорта из UI.
 *  10 PublishToUi:    статус COMPLETED, уведомление о готовности.
 *
 * ФРЕЙМВОРКИ / БИБЛИОТЕКИ
 * Koin (KoinJavaComponent.get) — точечное получение ErrorNotifier; kotlinx.coroutines.delay
 * — обратный отсчёт cooldown; Exposed DAO — доступ к БД; собственные коллекторы
 * VK и исполнители анализа.
 *
 * СВЯЗИ
 * Вызывается один раз из App.initializeBackend. Регистрирует исполнителей в
 * PipelineOrchestrator, который затем выполняет их по очереди (см. PipelineOrchestrator).
 */
package com.example.lomanalyzer.orchestration

import com.example.lomanalyzer.analysis.composite.CompositeRolesExecutor
import com.example.lomanalyzer.analysis.quality.QualityCheckExecutor
import com.example.lomanalyzer.analysis.dedup.DedupPipeline
import com.example.lomanalyzer.analysis.dedup.OriginalityExecutor
import com.example.lomanalyzer.analysis.inference.InferenceExecutor
import com.example.lomanalyzer.analysis.scoring.ScoringExecutor
import com.example.lomanalyzer.analysis.topic.TopicFilterExecutor
import com.example.lomanalyzer.import.JsonDataImporter
import com.example.lomanalyzer.nlp.NlpServiceSelector
import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.preprocessing.PreprocessingExecutor
import com.example.lomanalyzer.security.AuthManager
import com.example.lomanalyzer.storage.dao.*
import com.example.lomanalyzer.storage.tables.*
import com.example.lomanalyzer.ui.components.ErrorNotifier
import com.example.lomanalyzer.vk.*
import kotlinx.coroutines.delay

/**
 * Registers [StageExecutor] implementations for every [PipelineStage].
 *
 * Stages map to the 10-step algorithm from diploma section 2.1.1:
 *  1. Session init (auth, NLP, resources)
 *  2. Data collection (topic posts, author registry, author data + comments)
 *  3. Preprocessing (text cleaning, lemmatization)
 *  4. Topic filtering (two-pass + dedup)
 *  5. Scoring (11 quantitative scores) — TODO: stage 5
 *  6. Bootstrap (one-level + two-level) — TODO: stage 6
 *  7. Composite scores + role assignment — TODO: stage 7
 *  8. Quality indicators — TODO: stage 8
 *  9. Export
 * 10. Publish to UI
 */
@Suppress("LongParameterList", "TooManyFunctions")
class PipelineWiring(
    private val orchestrator: PipelineOrchestrator,
    private val sessionManager: SessionManager,
    private val authManager: AuthManager,
    private val postDao: PostDao,
    private val sessionMetricsDao: SessionMetricsDao,
    private val linkDao: LinkDao,
    private val vkApiClient: VkApiClient,
    private val baselineCollector: BaselineCollector,
    private val currentCollector: CurrentCollector,
    private val newsfeedSearchCollector: NewsfeedSearchCollector,
    private val authorWallCollector: AuthorWallCollector,
    private val commentCollector: CommentCollector,
    private val authorProfileCollector: AuthorProfileCollector,
    private val sessionEventService: SessionEventService,
    private val checkpointService: CheckpointService,
    private val preprocessingExecutor: PreprocessingExecutor,
    private val topicFilterExecutor: TopicFilterExecutor,
    private val dedupPipeline: DedupPipeline,
    private val originalityExecutor: OriginalityExecutor,
    private val nlpServiceSelector: NlpServiceSelector,
    private val scoringExecutor: ScoringExecutor,
    private val inferenceExecutor: InferenceExecutor,
    private val compositeRolesExecutor: CompositeRolesExecutor,
    private val qualityCheckExecutor: QualityCheckExecutor,
    private val jsonDataImporter: JsonDataImporter,
    private val progressReporter: ProgressReporter,
    private val cooldownState: CooldownState,
    private val logger: Logger,
) {
    /**
     * Регистрирует исполнителей для всех 10 стадий пайплайна в оркестраторе.
     * Вызывается один раз при старте приложения (App.initializeBackend).
     */
    fun wire() {
        // ── Stage 1: Session init — VK auth check, NLP initialization ──
        // Этап 1: инициализация сессии — статус COLLECTING и подготовка NLP
        orchestrator.registerExecutor(PipelineStage.SessionInit.name) { sessionId, stage ->
            // Засекаем время стадии для метрик длительности
            val startMs = System.currentTimeMillis()
            sessionManager.updateStatus(sessionId, SessionStatus.COLLECTING)

            // Initialize NLP service (Python sidecar or Kotlin fallback)
            // Инициализируем NLP: Python sidecar или fallback на Kotlin (Lucene)
            nlpServiceSelector.initialize()
            logger.event(AppEvent.NLP_MODE_SELECTED, mapOf(
                "mode" to nlpServiceSelector.mode,
                "session_id" to sessionId,
            ))
            sessionEventService.logInfo(sessionId, "NLP инициализирован: режим ${nlpServiceSelector.mode}")

            // Контрольная точка фазы 1 (после инициализации)
            checkpointService.saveCheckpoint(sessionId, "PHASE_1", stage.name)
            // Записываем длительность стадии в метрики сессии
            val durationMs = System.currentTimeMillis() - startMs
            sessionMetricsDao.insert(sessionId, stage.name, durationMs.toInt())
        }

        // ── Stage 2: Data collection ──
        // Diploma 2.1.1 stages 2-4:
        //   2. Topic posts by keywords
        //   3. Author registry = all unique authors
        //   4. Per author: profile, background posts, topic posts, comments
        orchestrator.registerExecutor(PipelineStage.DataCollection.name) { sessionId, stage ->
            val startMs = System.currentTimeMillis()
            // Читаем параметры сессии; отсутствие сессии — фатальная ошибка
            val session = sessionManager.getSession(sessionId)
                ?: throw IllegalStateException("Session #$sessionId not found")

            // Развилка источника данных: импорт из файла или живой сбор из VK
            val importPath = session[AnalysisSessions.importJsonPath]
            if (importPath != null) {
                // ── Path A: JSON import ──
                // Вариант A: импорт ранее сохранённых данных из JSON (без обращения к VK)
                sessionEventService.logInfo(sessionId, "Импорт данных из файла: $importPath")
                val result = jsonDataImporter.import(sessionId, importPath)
                sessionEventService.logInfo(sessionId,
                    "Импорт завершён: ${result.communities} сообществ, ${result.authors} авторов, " +
                        "${result.posts} постов, ${result.comments} комментариев")
            } else {
                // ── Path B: VK API three-phase collection ──
                // Вариант B: трёхфазный сбор из VK API (A → B → C)
                // Токен доступа VK обязателен для сбора
                val accessToken = authManager.getAccessToken()
                    ?: throw IllegalStateException("No VK access token available")
                // Сообщества сессии и окна периодов/фона из параметров
                val communityVkIds = linkDao.getCommunityVkIdsForSession(sessionId)
                val baselineDays = session[AnalysisSessions.baselineWindowDays]
                val currentDays = session[AnalysisSessions.currentWindowDays]
                val topicQuery = session[AnalysisSessions.topicQuery]

                // ── Phase A: Discover topic posts ──
                // Фаза A: поиск тематических публикаций (этап 2 алгоритма)
                if (communityVkIds.isNotEmpty()) {
                    // A1: Communities specified → wall.get per community
                    // A1: заданы сообщества → собираем тематические посты периода через wall.get
                    sessionEventService.logInfo(sessionId,
                        "Сбор из ${communityVkIds.size} сообществ за $currentDays дней")
                    currentCollector.collect(sessionId, communityVkIds, currentDays, accessToken)

                    // Also collect baseline from same communities
                    // Дополнительно собираем фон из тех же сообществ за более длинное окно
                    sessionEventService.logInfo(sessionId,
                        "Сбор фона из сообществ за $baselineDays дней")
                    baselineCollector.collect(sessionId, communityVkIds, baselineDays, accessToken)
                } else {
                    // A2: No communities → newsfeed.search with date segmentation
                    // A2: сообществ нет → ищем по теме через newsfeed.search (сегментация по датам)
                    sessionEventService.logInfo(sessionId,
                        "Сообщества не указаны → поиск через newsfeed.search: '$topicQuery'")
                    newsfeedSearchCollector.collect(sessionId, topicQuery, currentDays, accessToken)
                }
                // Контрольная точка: тематические посты собраны
                checkpointService.saveCheckpoint(sessionId, "PHASE_2", "TOPIC_POSTS_COMPLETE")

                // ── Phase B: Build author registry ──
                // Фаза B: реестр авторов = все уникальные авторы тематических постов (этап 3)
                sessionEventService.logInfo(sessionId, "Формирование реестра авторов")
                authorProfileCollector.collectAndRegister(sessionId, accessToken)
                // Контрольная точка: реестр авторов готов
                checkpointService.saveCheckpoint(sessionId, "PHASE_2", "AUTHOR_REGISTRY_COMPLETE")

                // ── Cooldown + probe before Phase C ──
                // VK rate-limits tokens after intensive Phase A+B usage.
                // Wait 5 minutes, then do a single probe wall.get.
                // If probe fails → skip Phase C entirely (no point retrying 300 authors).
                // Пауза + проба перед фазой C: после интенсивных фаз A и B VK включает
                // flood control. Ждём 10 минут, затем делаем одну пробную wall.get;
                // если она не прошла — фазу C пропускаем целиком (нет смысла дёргать всех авторов).
                val cooldownSeconds = 600
                sessionEventService.logInfo(sessionId,
                    "Пауза ${cooldownSeconds / 60} мин перед этапом C (VK API cooldown)")
                // Запускаем видимый в UI обратный отсчёт
                cooldownState.startCountdown(cooldownSeconds,
                    "Пауза перед этапом C",
                    "VK API: ожидание снятия ограничений")
                // Тикаем каждую секунду: обновляем UI и ждём delay(1с) (suspend, не блокирует поток)
                for (remaining in cooldownSeconds downTo 1) {
                    cooldownState.tick(remaining)
                    delay(1_000L)
                }
                cooldownState.clear()

                // Probe: try a single wall.get to check if flood control lifted
                // Проба: берём любой реальный id автора из тематических постов периода
                val probeAuthorVkId = postDao.findBySessionAndWindow(sessionId, "CURRENT")
                    .map { it[Posts.fromId] }.firstOrNull { it > 0 }
                // Делаем один запрос wall.get и смотрим, снято ли ограничение
                val probeOk = if (probeAuthorVkId != null) {
                    val probeResult = vkApiClient.wallGet(
                        ownerId = probeAuthorVkId, offset = 0, count = 1,
                        accessToken = accessToken,
                    )
                    // Ошибка в ответе означает, что flood control ещё действует
                    val ok = probeResult.error == null
                    if (!ok) {
                        logger.warn("Probe wall.get failed: error ${probeResult.error?.errorCode}")
                    } else {
                        logger.info("Probe wall.get OK — Phase C can proceed")
                    }
                    ok
                } else {
                    // Нет ни одного автора для пробы — продолжать фазу C бессмысленно
                    false
                }

                // Признак того, что фаза C фактически пропущена (нет собранных стен)
                val phaseCSkipped: Boolean
                if (probeOk) {
                    // ── Phase C: Per-author extended data ──
                    // Фаза C: по каждому автору собираем стены (фон + тематические посты), этап 4
                    sessionEventService.logInfo(sessionId,
                        "Сбор стен авторов: фон=${baselineDays}д, период=${currentDays}д")
                    val wallResult = authorWallCollector.collect(
                        sessionId, baselineDays, currentDays, accessToken,
                    )
                    // Считаем фазу пропущенной, если сбор прерван flood control и постов ноль
                    phaseCSkipped = wallResult.floodAborted && wallResult.totalPosts == 0
                } else {
                    // Проба не прошла — фазу C не запускаем вовсе
                    phaseCSkipped = true
                }
                // Контрольная точка: стены авторов обработаны (или фаза пропущена)
                checkpointService.saveCheckpoint(sessionId, "PHASE_2", "AUTHOR_WALLS_COMPLETE")

                // Если фаза C пропущена — предупреждаем пользователя и продолжаем на данных A+B
                if (phaseCSkipped) {
                    // Точечно достаём ErrorNotifier из Koin (не инжектится в конструктор)
                    val errorNotifier = org.koin.java.KoinJavaComponent
                        .get<ErrorNotifier>(ErrorNotifier::class.java)
                    // Уточняем причину пропуска для сообщения
                    val reason = if (!probeOk) "после 10-минутной паузы" else "во время сбора стен"
                    val msg = "Этап C (сбор стен авторов) прерван: VK API " +
                        "заблокировал запросы wall.get $reason. " +
                        "Анализ продолжится с данными из этапов A и B. " +
                        "Метрики ER_bg и TopFocus будут недоступны."
                    // Логируем, показываем баннер в UI и пускаем уведомление об ошибке
                    sessionEventService.logInfo(sessionId, msg)
                    cooldownState.notifyPhaseSkipped(msg)
                    errorNotifier.emit(
                        source = "Сбор данных",
                        code = 9,
                        message = "Этап C прерван: VK flood control",
                        detail = "Метрики ER_bg и TopFocus будут недоступны. " +
                            "Остальные метрики будут рассчитаны по данным этапов A и B.",
                    )
                    // Отражаем пропуск фазы в индикаторе прогресса
                    progressReporter.update(ProgressEvent(
                        stage = "Этап C пропущен (flood control)",
                        completedItems = 2,
                        totalItems = 4,
                    ))
                }

                // C2: Collect comments under topic posts (critical for Resp_a axis)
                // C2: сбор комментариев под тематическими постами — критично для оси отклика (Resp_a)
                sessionEventService.logInfo(sessionId,
                    "Сбор комментариев под тематическими публикациями")
                val commentsCount = commentCollector.collect(sessionId, accessToken)
                sessionEventService.logInfo(sessionId, "Собрано комментариев: $commentsCount")
                // Контрольная точка: комментарии собраны
                checkpointService.saveCheckpoint(sessionId, "PHASE_2", "COMMENTS_COMPLETE")
            }

            // Данные собраны — переводим сессию в фазу анализа
            sessionManager.updateStatus(sessionId, SessionStatus.ANALYZING)
            // Контрольная точка фазы 2 (сбор завершён) + метрика длительности стадии
            checkpointService.saveCheckpoint(sessionId, "PHASE_2", stage.name)
            val durationMs = System.currentTimeMillis() - startMs
            sessionMetricsDao.insert(sessionId, stage.name, durationMs.toInt())
        }

        // ── Stage 3: Preprocessing — text cleaning, lemmatization ──
        // Этап 5 алгоритма: очистка текстов, лемматизация, определение языка
        orchestrator.registerExecutor(PipelineStage.Preprocessing.name) { sessionId, stage ->
            preprocessingExecutor.execute(sessionId, stage)
            // Контрольная точка фазы 3 (обработка)
            checkpointService.saveCheckpoint(sessionId, "PHASE_3", stage.name)
        }

        // ── Stage 4: Topic filtering — two-pass + deduplication ──
        // Этап 6 алгоритма: двухпроходная тематическая фильтрация (L1 ключевые слова,
        // L2 RuBERT) + дедупликация near-дубликатов + оценка оригинальности
        orchestrator.registerExecutor(PipelineStage.TopicFiltering.name) { sessionId, stage ->
            topicFilterExecutor.execute(sessionId, stage)
            dedupPipeline.execute(sessionId, stage)
            originalityExecutor.execute(sessionId, stage)
            // Контрольная точка фазы 3 (обработка завершена)
            checkpointService.saveCheckpoint(sessionId, "PHASE_3", stage.name)
        }

        // ── Stage 5: Scoring — 11 quantitative scores across 4 axes (diploma E.4) ──
        // Этап 7 алгоритма: 11 количественных оценок по 4 осям влияния (формулы E.4)
        orchestrator.registerExecutor(PipelineStage.Scoring.name) { sessionId, stage ->
            scoringExecutor.execute(sessionId, stage)
            // Контрольная точка фазы 4 (оценки и неопределённость)
            checkpointService.saveCheckpoint(sessionId, "PHASE_4", stage.name)
        }

        // ── Stage 6: Bootstrap — one-level (B=1000) + two-level (300×100) ──
        // Этап 8 алгоритма: оценка неопределённости бутстрапом — одноуровневый (B=1000)
        // и двухуровневый (300×100 только для Resp_a), см. E.3
        orchestrator.registerExecutor(PipelineStage.Bootstrap.name) { sessionId, stage ->
            inferenceExecutor.execute(sessionId, stage)
            // Контрольная точка фазы 4
            checkpointService.saveCheckpoint(sessionId, "PHASE_4", stage.name)
        }

        // ── Stage 7: Composite scores + adaptive thresholds + role assignment ──
        // Этап 9 алгоритма: композитные оценки (веса 1/3,1/3,1/3) → адаптивные пороги
        // → классификация: 4 базовые роли + 2 атрибута (квадранты)
        orchestrator.registerExecutor(PipelineStage.CompositeRoles.name) { sessionId, stage ->
            compositeRolesExecutor.execute(sessionId, stage)
            // Контрольная точка фазы 5 (классификация)
            checkpointService.saveCheckpoint(sessionId, "PHASE_5", stage.name)
        }

        // ── Stage 8: Quality check — sufficiency + session quality indicators ──
        // TODO: Implement on instruction stage 8 (analysis/sufficiency/ + analysis/quality/)
        // Этап 10 алгоритма: индикатор достаточности данных + 8 индикаторов качества сессии
        orchestrator.registerExecutor(PipelineStage.QualityCheck.name) { sessionId, stage ->
            qualityCheckExecutor.execute(sessionId, stage)
            // Контрольная точка фазы 5
            checkpointService.saveCheckpoint(sessionId, "PHASE_5", stage.name)
        }

        // ── Stage 9: Export ──
        // Финализация: результаты готовы; экспорт в CSV/JSON инициируется из UI
        orchestrator.registerExecutor(PipelineStage.Export.name) { sessionId, _ ->
            logger.info("Stage EXPORT: results available for export via UI")
        }

        // ── Stage 10: Publish to UI ──
        // Финализация: помечаем сессию завершённой и уведомляем о готовности результатов
        orchestrator.registerExecutor(PipelineStage.PublishToUi.name) { sessionId, _ ->
            sessionManager.updateStatus(sessionId, SessionStatus.COMPLETED)
            logger.event(AppEvent.ANALYSIS_COMPLETED, mapOf("session_id" to sessionId))
            sessionEventService.logInfo(sessionId, "Анализ завершён")
        }
    }
}
