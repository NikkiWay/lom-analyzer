/*
 * НАЗНАЧЕНИЕ
 * DAO (слой доступа к данным) для сессий анализа (таблица analysis_session) —
 * корневая сущность пайплайна. Сессия хранит постановку задачи (тематический запрос,
 * n-граммы, эталонные тексты, регион), режимы (NLP, роли), окна сбора (фоновое/текущее)
 * и итоговые показатели качества. Поддерживает мягкое удаление (deletedAt) с
 * восстановлением, а также «семьи» сессий (sessionFamilyId) для связанных запусков.
 * Обмен между модулями — только через SQLite.
 *
 * ЧТО ВНУТРИ
 * Класс SessionDao: insert (создать сессию), findById/findAll/findSoftDeleted (выборки),
 * updateStatus (статус пайплайна), softDelete/restore/hardDelete (управление жизненным
 * циклом), setSessionFamily (привязка к семье), updateQualityScore (итоги качества).
 *
 * БИБЛИОТЕКИ
 * Exposed ORM — DSL запросов (insertAndGetId, update, deleteWhere).
 * java.time.Instant — created_at/updated_at/deleted_at в epoch millis.
 *
 * СВЯЗИ
 * Таблица AnalysisSessions (storage/tables). Центральный sessionId для всех прочих DAO.
 */
package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.AnalysisSessions
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * DAO сессий анализа. Каждый метод — отдельная транзакция.
 *
 * @param db подключение к БД (Exposed Database).
 */
class SessionDao(private val db: Database) {
    /**
     * INSERT новой сессии анализа с постановкой задачи и параметрами пайплайна.
     * @param topicQuery тематический запрос пользователя.
     * @param primaryNgrams/secondaryNgrams/excludedNgrams n-граммы для тематического
     *        фильтра (основные/вспомогательные/исключающие), сериализованные строкой.
     * @param referenceTexts эталонные тексты для L2-фильтрации (RuBERT) или NULL.
     * @param nlpMode режим NLP (по умолчанию FULL).
     * @param roleMode режим классификации ролей (по умолчанию QUADRANT — квадрантный).
     * @param baselineWindowDays окно фонового периода в днях (по умолчанию 60).
     * @param currentWindowDays окно текущего периода в днях (по умолчанию 30).
     * @param importJsonPath путь к импортируемому JSON или NULL.
     * @return сгенерированный id сессии.
     */
    @Suppress("LongParameterList")
    fun insert(
        name: String,
        topicQuery: String,
        primaryNgrams: String? = null,
        secondaryNgrams: String? = null,
        excludedNgrams: String? = null,
        referenceTexts: String? = null,
        region: String? = null,
        nlpMode: String = "FULL",
        roleMode: String = "QUADRANT",
        baselineWindowDays: Int = 60,
        currentWindowDays: Int = 30,
        importJsonPath: String? = null,
    ): Int = transaction(db) {
        // Единая отметка времени для created_at и updated_at при создании
        val now = Instant.now().toEpochMilli()
        AnalysisSessions.insertAndGetId {
            it[AnalysisSessions.name] = name
            it[AnalysisSessions.topicQuery] = topicQuery
            it[AnalysisSessions.primaryNgrams] = primaryNgrams
            it[AnalysisSessions.secondaryNgrams] = secondaryNgrams
            it[AnalysisSessions.excludedNgrams] = excludedNgrams
            it[AnalysisSessions.referenceTexts] = referenceTexts
            it[AnalysisSessions.region] = region
            it[AnalysisSessions.nlpMode] = nlpMode
            it[AnalysisSessions.roleMode] = roleMode
            it[AnalysisSessions.baselineWindowDays] = baselineWindowDays
            it[AnalysisSessions.currentWindowDays] = currentWindowDays
            it[AnalysisSessions.importJsonPath] = importJsonPath
            it[createdAt] = now
            it[updatedAt] = now
        }.value
    }

    /**
     * SELECT сессии по id (без учёта мягкого удаления).
     * @return ResultRow или null.
     */
    fun findById(id: Int): ResultRow? = transaction(db) {
        AnalysisSessions.selectAll().where { AnalysisSessions.id eq id }.singleOrNull()
    }

    /**
     * UPDATE статуса сессии (этап/состояние пайплайна) с обновлением updatedAt.
     */
    fun updateStatus(id: Int, status: String) = transaction(db) {
        AnalysisSessions.update({ AnalysisSessions.id eq id }) {
            it[AnalysisSessions.status] = status
            it[updatedAt] = Instant.now().toEpochMilli()
        }
    }

    /**
     * UPDATE фактического режима NLP и версий моделей — вызывается после того, как
     * NLP инициализирован и стало известно, что удалось поднять.
     *
     * При создании сессии в nlp_mode попадает значение из параметров, то есть
     * намерение: реальный режим определяется позже и зависит от того, поднялся ли
     * sidecar. Пока факт не записывался, сессия, посчитанная словарём, ничем не
     * отличалась от посчитанной моделью, а nlp_model_versions оставался пустым —
     * и по результатам нельзя было сказать, чем они получены.
     *
     * @param mode фактический режим: FULL или FALLBACK_ONLY.
     * @param modelVersions версии реально использованных моделей (JSON).
     */
    fun updateNlpRuntime(id: Int, mode: String, modelVersions: String) = transaction(db) {
        AnalysisSessions.update({ AnalysisSessions.id eq id }) {
            it[nlpMode] = mode
            it[nlpModelVersions] = modelVersions
            it[updatedAt] = Instant.now().toEpochMilli()
        }
    }

    /**
     * SELECT всех «живых» сессий (deletedAt IS NULL), новые сверху (createdAt DESC).
     * @return список ResultRow.
     */
    fun findAll(): List<ResultRow> = transaction(db) {
        AnalysisSessions.selectAll()
            .where { AnalysisSessions.deletedAt.isNull() }
            .orderBy(AnalysisSessions.createdAt, SortOrder.DESC)
            .toList()
    }

    /**
     * Мягкое удаление: UPDATE проставляет deletedAt (и updatedAt) текущим временем,
     * физически строку не удаляет — её можно восстановить через restore.
     */
    fun softDelete(id: Int) = transaction(db) {
        AnalysisSessions.update({ AnalysisSessions.id eq id }) {
            it[deletedAt] = Instant.now().toEpochMilli()
            it[updatedAt] = Instant.now().toEpochMilli()
        }
    }

    /**
     * SELECT мягко удалённых сессий (deletedAt IS NOT NULL), позже удалённые сверху.
     * @return список ResultRow («корзина»).
     */
    fun findSoftDeleted(): List<ResultRow> = transaction(db) {
        AnalysisSessions.selectAll()
            .where { AnalysisSessions.deletedAt.isNotNull() }
            .orderBy(AnalysisSessions.deletedAt, SortOrder.DESC)
            .toList()
    }

    /**
     * Восстановление из «корзины»: UPDATE сбрасывает deletedAt в null.
     */
    fun restore(id: Int) = transaction(db) {
        AnalysisSessions.update({ AnalysisSessions.id eq id }) {
            it[deletedAt] = null
            it[updatedAt] = Instant.now().toEpochMilli()
        }
    }

    /**
     * Жёсткое удаление: DELETE физически удаляет строку сессии из таблицы.
     */
    fun hardDelete(id: Int) = transaction(db) {
        AnalysisSessions.deleteWhere { AnalysisSessions.id eq id }
    }

    /**
     * UPDATE привязки сессии к «семье» (familySourceId) — связывает родственные
     * запуски анализа.
     */
    fun setSessionFamily(id: Int, familySourceId: Int) = transaction(db) {
        AnalysisSessions.update({ AnalysisSessions.id eq id }) {
            it[sessionFamilyId] = familySourceId
            it[updatedAt] = Instant.now().toEpochMilli()
        }
    }

    /**
     * UPDATE итоговых показателей качества сессии (этап 8/качество).
     * @param score интегральная оценка качества сессии.
     * @param qualityGatesJson сериализованные «ворота качества» (записываются только
     *        если не null).
     * @param coverageRatio доля покрытия данных (записывается только если не null).
     */
    fun updateQualityScore(
        id: Int,
        score: Float,
        qualityGatesJson: String? = null,
        coverageRatio: Float? = null,
    ) = transaction(db) {
        AnalysisSessions.update({ AnalysisSessions.id eq id }) {
            it[sessionQualityScore] = score
            // Необязательные поля пишем только при наличии значения
            if (qualityGatesJson != null) it[AnalysisSessions.qualityGatesJson] = qualityGatesJson
            if (coverageRatio != null) it[AnalysisSessions.coverageRatio] = coverageRatio
            it[updatedAt] = Instant.now().toEpochMilli()
        }
    }
}
