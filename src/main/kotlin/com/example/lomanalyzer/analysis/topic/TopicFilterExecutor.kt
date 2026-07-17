/*
 * НАЗНАЧЕНИЕ
 * Исполнитель этапа 6 пайплайна (двухпроходная тематическая фильтрация,
 * docs/algorithm.md). Прогоняет все посты сессии через L1 (n-граммы) и при
 * необходимости L2 (RuBERT cosine), проставляет в БД признак тематической
 * релевантности и баллы, формирует сводку качества фильтрации.
 *
 * ЧТО ВНУТРИ
 * class TopicFilterExecutor (реализует StageExecutor):
 * - execute: основной прогон фильтрации по всем постам сессии.
 * - parseAndLemmatizeNgrams: разбор и лемматизация пользовательских n-грамм.
 * - preprocessOnTheFly: лемматизация текста «на лету», если нет готовых лемм.
 * Поле lastFilterSummary хранит последнюю сводку для показа в UI.
 *
 * АЛГОРИТМ
 * Двухпроходная фильтрация L1/L2: NgramMatcher (L1) + SemanticScorer (L2, RuBERT
 * cosine, порог 0.55), решение принимает TopicRelevanceFilter. Сводку строит
 * FilterQualityComputer.
 *
 * ФРЕЙМВОРКИ / БИБЛИОТЕКИ
 * - Exposed ORM — доступ к таблицам Posts/ProcessedTexts/AnalysisSessions через DAO.
 * - kotlinx.serialization (Json) — декодирование сохранённых лемм (lemmasJson).
 * - NlpServiceSelector — выбор режима NLP (FULL с sidecar / FALLBACK_ONLY) и
 *   получение клиента к Python FastAPI sidecar (pymorphy3, rubert-tiny2).
 * - StageExecutor/ProgressReporter — интеграция в оркестрацию и прогресс-бар.
 * - java.time — форматирование времени (используется при сборке сводки).
 *
 * СВЯЗИ
 * Читает посты/леммы и параметры сессии (ключевые слова, reference-тексты) из БД,
 * пишет обратно признак релевантности и баллы; результат потребляют этап 7
 * (оценки) и UI ручной валидации.
 */
package com.example.lomanalyzer.analysis.topic

import com.example.lomanalyzer.nlp.NlpServiceSelector
import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.PipelineStage
import com.example.lomanalyzer.orchestration.ProgressEvent
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.orchestration.StageExecutor
import com.example.lomanalyzer.preprocessing.LemmatizerProxy
import com.example.lomanalyzer.preprocessing.StopWords
import com.example.lomanalyzer.preprocessing.TextCleaner
import com.example.lomanalyzer.preprocessing.Tokenizer
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.dao.ProcessedTextDao
import com.example.lomanalyzer.storage.dao.SessionDao
import com.example.lomanalyzer.storage.tables.AnalysisSessions
import com.example.lomanalyzer.storage.tables.Posts
import com.example.lomanalyzer.storage.tables.ProcessedTexts
import com.example.lomanalyzer.vk.SessionEventService
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Two-pass topic filtering (diploma 2.1.1, stages 5-6).
 *
 * Pass 1: keyword match on lemmatized n-grams.
 * Pass 2 (borderline only): cosine similarity on RuBERT embeddings (threshold 0.55).
 *
 * Produces [FilterQualitySummary] with three proportions and first 30 disputed posts.
 */
class TopicFilterExecutor(
    private val postDao: PostDao,
    private val processedTextDao: ProcessedTextDao,
    private val sessionDao: SessionDao,
    private val nlpServiceSelector: NlpServiceSelector,
    private val lemmatizer: LemmatizerProxy,
    private val progressReporter: ProgressReporter,
    private val sessionEventService: SessionEventService,
    private val logger: Logger,
) : StageExecutor {

    /** Last computed filter summary, accessible from UI.
     *  Последняя вычисленная сводка качества фильтрации (для отображения в UI). */
    var lastFilterSummary: FilterQualitySummary? = null
        private set

    /**
     * Запускает тематическую фильтрацию для всех постов сессии.
     * @param sessionId идентификатор сессии анализа.
     * @param stage текущая стадия пайплайна (передаётся оркестратором).
     */
    override suspend fun execute(sessionId: Int, stage: PipelineStage) {
        // Загружаем параметры сессии из БД
        val session = sessionDao.findById(sessionId)
            ?: error("Session $sessionId not found")

        // Разбираем и лемматизируем пользовательские наборы n-грамм
        val primaryNgrams = parseAndLemmatizeNgrams(session[AnalysisSessions.primaryNgrams])
        val secondaryNgrams = parseAndLemmatizeNgrams(session[AnalysisSessions.secondaryNgrams])
        val excludedNgrams = parseAndLemmatizeNgrams(session[AnalysisSessions.excludedNgrams])

        // Если основные n-граммы не заданы — fallback на поисковый запрос темы
        val topicQuery = session[AnalysisSessions.topicQuery]
        val effectivePrimary = if (primaryNgrams.isEmpty() && topicQuery.isNotBlank()) {
            parseAndLemmatizeNgrams(topicQuery)
        } else {
            primaryNgrams
        }

        // Сопоставитель n-грамм для прохода 1 (L1)
        val ngramMatcher = NgramMatcher(effectivePrimary, secondaryNgrams, excludedNgrams)

        // Set up semantic scorer (pass 2) if FULL NLP + reference texts
        // Готовим семантический оценщик (проход 2) только при режиме FULL и наличии
        // reference-текстов: эталонные тексты темы вводятся построчно
        val nlpMode = nlpServiceSelector.mode
        val referenceTexts = session[AnalysisSessions.referenceTexts]
            ?.split("\n")?.map { it.trim() }?.filter { it.isNotBlank() }
            ?: emptyList()

        var semanticScorer: SemanticScorer? = null
        // Причина отсутствия прохода 2; null — проход работает
        var semanticPassOffReason: String? = when {
            nlpMode != "FULL" -> "nlp_mode=$nlpMode"
            referenceTexts.isEmpty() -> "нет эталонных текстов темы"
            else -> null
        }
        if (semanticPassOffReason == null) {
            // Строим эталонный эмбеддинг темы; используем scorer только если он успешно инициализирован
            val scorer = SemanticScorer(nlpServiceSelector.getService())
            scorer.initializeReference(referenceTexts)
            if (scorer.isInitialized()) {
                semanticScorer = scorer
            } else {
                semanticPassOffReason = "не удалось построить эталонный эмбеддинг (sidecar недоступен или медленный)"
            }
        }

        // Фильтр-решатель, объединяющий проходы 1 и 2
        val topicFilter = TopicRelevanceFilter(semanticScorer = semanticScorer, nlpMode = nlpMode)

        logger.event(AppEvent.TOPIC_FILTER_APPLIED, mapOf(
            "session_id" to sessionId,
            "primary_ngrams" to effectivePrimary.map { it.joinToString(" ") },
            "nlp_mode" to nlpMode,
            "semantic_scorer" to (semanticScorer != null),
        ))

        // Без прохода 2 решение по пограничным постам принимается по одним ключевым
        // словам, и вся пограничная полоса уходит в отсев: состав тематической
        // выборки, а с ним и все оценки, получены иначе. Сессия при этом
        // завершается успешно, поэтому отключение фиксируется явно — по статусу
        // COMPLETED отличить такой прогон от полноценного нельзя.
        if (semanticPassOffReason != null) {
            logger.event(AppEvent.TOPIC_SEMANTIC_PASS_DISABLED, mapOf(
                "session_id" to sessionId,
                "reason" to semanticPassOffReason,
            ))
            sessionEventService.logSemanticPassDisabled(sessionId, semanticPassOffReason)
        }

        // Apply filter to all posts and collect results for summary
        // Прогоняем фильтр по всем постам сессии, собирая результаты для сводки
        val posts = postDao.findBySession(sessionId)
        val total = posts.size
        val scoredPosts = mutableListOf<Pair<Int, TopicScoreResult>>()

        for ((index, post) in posts.withIndex()) {
            val postId = post[Posts.id].value
            // Предпочитаем очищенный текст; при его отсутствии — исходный
            val cleanText = post[Posts.textClean] ?: post[Posts.text] ?: ""

            // Берём готовые леммы из БД (этап 5); если их нет — лемматизируем на лету
            val processed = processedTextDao.findByPostId(postId)
            val lemmas: List<String> = if (processed != null) {
                val json = processed[ProcessedTexts.lemmasJson]
                if (json != null) Json.decodeFromString(json) else emptyList()
            } else {
                preprocessOnTheFly(cleanText)
            }

            // Проход 1: сопоставление лемм с n-граммами; затем полная оценка (L1+L2)
            val matchResult = ngramMatcher.match(lemmas)
            val scoreResult = topicFilter.score(matchResult, cleanText)

            // Сохраняем в БД признак релевантности и все баллы
            postDao.updateTopicRelevance(
                id = postId,
                relevant = scoreResult.relevant,
                l1 = scoreResult.l1,
                l2 = scoreResult.l2,
                combined = scoreResult.combined,
            )

            scoredPosts.add(postId to scoreResult)

            // Обновляем прогресс каждые 100 постов и на последнем посте
            if ((index + 1) % 100 == 0 || index == total - 1) {
                progressReporter.update(ProgressEvent("TOPIC_FILTERING", index + 1, total))
            }
        }

        // Compute and store filter quality summary
        // Формируем и сохраняем сводку качества фильтрации
        // Колбэк postLookup собирает ValidationPost по id из БД для спорных постов
        lastFilterSummary = FilterQualityComputer.compute(scoredPosts, { postId ->
            val post = postDao.findById(postId) ?: return@compute null
            val fromId = post[Posts.fromId]
            val ownerId = post[Posts.ownerId]
            ValidationPost(
                id = postId,
                text = post[Posts.text] ?: "",
                score = post[Posts.topicScoreCombined] ?: 0f,
                systemRelevant = post[Posts.isTopicRelevant] ?: false,
                stratum = scoredPosts.find { it.first == postId }?.second?.stratum?.name ?: "UNSCORED",
                fromVkId = fromId,
                ownerVkId = ownerId,
                analystVote = post[Posts.analystVote],
                publishedAt = post[Posts.publishedAt],
                window = post[Posts.window],
            )
        })

        logger.info("Topic filter summary: " +
            "confident=${lastFilterSummary?.confidentCount}, " +
            "pass2=${lastFilterSummary?.pass2ConfirmedCount}, " +
            "disputed=${lastFilterSummary?.disputedCount}, " +
            "excluded=${lastFilterSummary?.excludedCount}")
    }

    /**
     * Parse n-grams from comma/newline-separated string and lemmatize them.
     * Uses the SAME lemmatization method as posts (pymorphy3 via sidecar when available,
     * Snowball fallback otherwise) to ensure n-gram stems match post lemmas.
     *
     * Разбирает n-граммы из строки (разделители — перевод строки или запятая) и
     * лемматизирует их ТЕМ ЖЕ методом, что и посты (pymorphy3 через sidecar, иначе
     * Snowball-стеммер), чтобы основы n-грамм совпадали с леммами постов.
     * @return список n-грамм, каждая — список лемм.
     */
    private suspend fun parseAndLemmatizeNgrams(raw: String?): List<List<String>> {
        if (raw.isNullOrBlank()) return emptyList()
        // Клиент NLP-сервиса (sidecar) или null, если недоступен
        val nlpService = nlpServiceSelector.getServiceOrNull()
        return raw.split("\n", ",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { ngram ->
                // Разбиваем n-грамму на токены по пробелам в нижнем регистре
                val tokens = ngram.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
                if (nlpService != null) {
                    // Use same lemmatizer as posts (pymorphy3)
                    // Тот же лемматизатор, что и для постов (pymorphy3 через sidecar)
                    val result = nlpService.lemmatize(tokens.joinToString(" "))
                    result.lemmas.map { it.lowercase() }
                } else {
                    // Fallback: Snowball stemmer
                    // Fallback: Snowball-стеммер (Lucene) при отсутствии sidecar
                    lemmatizer.stemFallback(tokens).map { it.lemma }
                }
            }
            .filter { it.isNotEmpty() }
    }

    /**
     * Лемматизация текста «на лету», когда готовых лемм из этапа 5 нет в БД:
     * очистка -> токенизация -> удаление стоп-слов -> Snowball-стемминг.
     * @return список лемм текста.
     */
    private fun preprocessOnTheFly(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        // Очистка текста (удаление URL, упоминаний и т.п.)
        val cleaned = TextCleaner.clean(text)
        // Токенизация очищенного текста
        val tokens = Tokenizer.tokenize(cleaned.cleanText)
        // Удаление стоп-слов
        val filtered = StopWords.filter(tokens)
        // Стемминг (Snowball) -> леммы
        return lemmatizer.stemFallback(filtered).map { it.lemma }
    }
}
