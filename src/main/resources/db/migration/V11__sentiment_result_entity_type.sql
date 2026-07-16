-- ============================================================
-- V11: sentiment_result — разделение пространств идентификаторов постов и комментариев
-- ============================================================
--
-- ПРОБЛЕМА
-- Таблица создавалась как «одна тональность на пост»:
--     post_id INTEGER PRIMARY KEY REFERENCES post(id)
-- Однако код всегда писал в неё тональность И постов, И комментариев, подставляя
-- в post_id идентификатор комментария (PreprocessingExecutor, шаг 4), а читал её
-- как плоскую карту «id сущности -> метка» (SentimentResultDao.findAllAsMap,
-- ScoringExecutor, InferenceExecutor). post и comment — независимые
-- автоинкрементные последовательности, поэтому их идентификаторы пересекаются:
-- первый же комментарий (id=1) конфликтовал с тональностью поста id=1 и падал с
-- «UNIQUE constraint failed: sentiment_result.post_id», обрушивая стадию
-- препроцессинга для любой сессии, где есть комментарии.
--
-- РЕШЕНИЕ
-- Явный дискриминатор entity_type ('POST' | 'COMMENT') + составной первичный ключ
-- (entity_type, entity_id). Это ровно та модель, которую предполагал код.
--
-- КОМПРОМИСС
-- entity_id указывает на две разные таблицы, поэтому внешний ключ на уровне
-- SQLite здесь невыразим и намеренно снят. Целостность обеспечивается тем, что
-- запись всегда создаётся сразу после вставки соответствующего поста/комментария
-- в рамках той же стадии пайплайна. Взамен появляется корректный составной ключ,
-- которого раньше не было ни для комментариев, ни для смешанного набора.

ALTER TABLE sentiment_result RENAME TO sentiment_result_v1;

CREATE TABLE sentiment_result (
    entity_type          TEXT    NOT NULL,   -- POST | COMMENT
    entity_id            INTEGER NOT NULL,   -- post.id либо comment.id (по entity_type)
    sentiment            TEXT    NOT NULL,   -- POSITIVE, NEGATIVE, NEUTRAL, SKIP, SPEECH
    score                REAL,
    method               TEXT    NOT NULL,   -- модель sidecar либо словарный fallback
    negation_applied     INTEGER NOT NULL DEFAULT 0,
    bootstrap_agreement  REAL,
    bootstrap_variants   TEXT,               -- JSON
    PRIMARY KEY (entity_type, entity_id)
);

-- Все ранее сохранённые строки относились к постам: до этой миграции строка с
-- идентификатором комментария физически не могла быть записана (см. выше).
INSERT INTO sentiment_result (
    entity_type, entity_id, sentiment, score, method,
    negation_applied, bootstrap_agreement, bootstrap_variants
)
SELECT
    'POST', post_id, sentiment, score, method,
    negation_applied, bootstrap_agreement, bootstrap_variants
FROM sentiment_result_v1;

DROP TABLE sentiment_result_v1;
