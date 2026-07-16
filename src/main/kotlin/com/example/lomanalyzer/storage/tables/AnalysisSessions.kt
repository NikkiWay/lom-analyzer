/*
 * НАЗНАЧЕНИЕ
 * Декларативное описание таблицы "analysis_session" — корневая сущность всего
 * пайплайна. Одна сессия = один прогон анализа по заданной теме: параметры
 * темы и окон, версии моделей/справочников, калибровка gamma, пороги ролей,
 * агрегированные показатели качества и статус выполнения. Почти все остальные
 * таблицы ссылаются на неё через session_id.
 *
 * ЧТО ВНУТРИ
 * Один object-таблица AnalysisSessions (Exposed ORM). Содержит параметры
 * постановки задачи (этап 1), конфигурацию тематической фильтрации, версии
 * артефактов (для воспроизводимости), результаты калибровки и контроля качества.
 * Имеет самоссылку session_family_id (объединение связанных сессий в "семью").
 *
 * МЕТОД
 * gamma_* — параметры калибровки степенной зависимости (этап оценок/композитов);
 * role_threshold_base/event — пороги квадрантной классификации ролей; norm_stats
 * и cv_iqr — статистики робастной z-нормализации (медиана/IQR/MAD).
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — IntIdTable (суррогатный primary key "id"). optReference задаёт
 * необязательную самоссылку на эту же таблицу. Создаётся миграциями Flyway.
 *
 * СВЯЗИ
 * Дочерние таблицы (по session_id): post, comment, author (через session_author),
 * lom_score, composite_score, session_threshold, author_role, bootstrap_interval,
 * dedup_group, session_event, pipeline_checkpoint, collection_checkpoint,
 * session_metrics, audit_log, session_community.
 */
package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

/**
 * Таблица сессий анализа ("analysis_session") — корень модели данных.
 *
 * IntIdTable (суррогатный primary key "id"). Хранит всю конфигурацию и сводные
 * результаты одного прогона выявления ЛОМ по заданной теме.
 */
object AnalysisSessions : IntIdTable("analysis_session") {
    /** Человекочитаемое имя сессии. */
    val name = text("name")
    /** Описание сессии; может отсутствовать. */
    val description = text("description").nullable()
    /** Поисковый запрос/формулировка темы анализа. */
    val topicQuery = text("topic_query")
    /** Первичные n-граммы темы (ключевые слова прохода L1, JSON); может отсутствовать. */
    val primaryNgrams = text("primary_ngrams").nullable()
    /** Вторичные n-граммы темы (расширяющие, JSON); может отсутствовать. */
    val secondaryNgrams = text("secondary_ngrams").nullable()
    /** Исключающие n-граммы (стоп-термины темы, JSON); может отсутствовать. */
    val excludedNgrams = text("excluded_ngrams").nullable()
    /** Эталонные тексты темы для прохода L2 (RuBERT cosine), JSON; может отсутствовать. */
    val referenceTexts = text("reference_texts").nullable()
    /** Регион анализа; может отсутствовать. */
    val region = text("region").nullable()
    /** Часовой пояс для отображения дат; по умолчанию "Europe/Moscow". */
    val displayTimezone = text("display_timezone").default("Europe/Moscow")
    /** Ширина окна базовой линии (фоновой активности) в днях; по умолчанию 60. */
    val baselineWindowDays = integer("baseline_window_days").default(60)
    /** Ширина текущего (тематического) окна в днях; по умолчанию 30. */
    val currentWindowDays = integer("current_window_days").default(30)
    /** Режим NLP: "FULL" (Python sidecar) или ограниченный fallback; по умолчанию "FULL". */
    val nlpMode = text("nlp_mode").default("FULL")
    /** Режим назначения ролей; по умолчанию "QUADRANT" (квадрантная классификация). */
    val roleMode = text("role_mode").default("QUADRANT")
    /** Версии используемых NLP-моделей (JSON) — для воспроизводимости; может отсутствовать. */
    val nlpModelVersions = text("nlp_model_versions").nullable()
    /** Версия базы эталонных текстов; может отсутствовать. */
    val referenceBaseVersion = text("reference_base_version").nullable()
    /** SHA-256 базы эталонных текстов — контроль целостности; может отсутствовать. */
    val referenceBaseSha256 = text("reference_base_sha256").nullable()
    /** Версия справочника праздников; может отсутствовать. */
    val holidaysVersion = text("holidays_version").nullable()
    /** Версия словаря тональности (sentilex); может отсутствовать. */
    val sentilexVersion = text("sentilex_version").nullable()
    /** Версия тестового корпуса; может отсутствовать. */
    val testCorpusVersion = text("test_corpus_version").nullable()
    /** Откалиброванное значение gamma (параметр степенной зависимости); может отсутствовать. */
    val gammaCalibrated = float("gamma_calibrated").nullable()
    /** Признак, что gamma была обрезана до допустимого диапазона (clip); по умолчанию false. */
    val gammaClipped = bool("gamma_clipped").default(false)
    /** Признак, что использовано запасное значение gamma (fallback); по умолчанию false. */
    val gammaFallback = bool("gamma_fallback").default(false)
    /** Робастный R^2 (на MAD) качества подгонки gamma; может отсутствовать. */
    val gammaR2Mad = float("gamma_r2_mad").nullable()
    /** Флаг расхождения эталонной gamma; по умолчанию "OK". */
    val referenceGammaDivergenceFlag = text("reference_gamma_divergence_flag").default("OK")
    /** Порог базовой роли (theta) для квадрантной классификации; может отсутствовать. */
    val roleThresholdBase = float("role_threshold_base").nullable()
    /** Порог событийной роли (theta) для квадрантной классификации; может отсутствовать. */
    val roleThresholdEvent = float("role_threshold_event").nullable()
    /** Коэффициенты вариации по IQR для осей (JSON) — диагностика разброса; может отсутствовать. */
    val cvIqrJson = text("cv_iqr_json").nullable()
    /** Доля покрытия данных [0..1] — индикатор достаточности; может отсутствовать. */
    val coverageRatio = float("coverage_ratio").nullable()
    /** Сводная оценка качества сессии; может отсутствовать. */
    val sessionQualityScore = float("session_quality_score").nullable()
    /** Состояние "ворот качества" (quality gates, JSON); может отсутствовать. */
    val qualityGatesJson = text("quality_gates_json").nullable()
    /** Статистики нормализации (медиана/IQR/MAD по осям, JSON) для робастной z-нормализации; может отсутствовать. */
    val normStatsJson = text("norm_stats_json").nullable()
    /** Статус сессии в пайплайне; по умолчанию "CREATED". */
    val status = text("status").default("CREATED")
    /** Необязательная самоссылка (foreign key) на analysis_session.id — объединение связанных сессий в "семью". */
    val sessionFamilyId = optReference("session_family_id", AnalysisSessions)
    /** Признак ортогонализации тематической оси T (устранение корреляции со структурной); по умолчанию false. */
    val tOrthogonalized = bool("t_orthogonalized").default(false)
    /** Вариант весов событийного режима; может отсутствовать. */
    val eventWeightsVariant = text("event_weights_variant").nullable()
    /** Флаг возможной нестационарности данных сессии; по умолчанию false. */
    val possiblyNonStationaryFlag = bool("possibly_non_stationary_flag").default(false)
    /** Флаг частичного покрытия справочника праздников; по умолчанию false. */
    val holidaysPartialCoverageFlag = bool("holidays_partial_coverage_flag").default(false)
    /** Флаг отключения сезонной коррекции; по умолчанию false. */
    val seasonalityDisabledFlag = bool("seasonality_disabled_flag").default(false)
    /** Путь к импортированному JSON (если сессия создана из импорта); может отсутствовать. */
    val importJsonPath = text("import_json_path").nullable()
    /** Момент создания сессии (Unix-время, мс). */
    val createdAt = long("created_at")
    /** Момент последнего обновления сессии (Unix-время, мс). */
    val updatedAt = long("updated_at")
    /** Момент мягкого удаления (soft delete); null — сессия активна. */
    val deletedAt = long("deleted_at").nullable()
}
