-- V9: Clean up v6 tables and create new schema for diploma v2
-- Removes: anomalies, risk signals, persona aggregates, holidays, post metrics snapshots,
--          repost relations, recovery choices, persona history link
-- Adds: comments, session_events, bootstrap_intervals, composite_scores, author_roles, session_quality_indicators

-- ── Drop obsolete tables ──
DROP TABLE IF EXISTS risk_anomaly_link;
DROP TABLE IF EXISTS anomaly_post_link;
DROP TABLE IF EXISTS anomaly_author_link;
DROP TABLE IF EXISTS risk_signal;
DROP TABLE IF EXISTS anomaly_event;
DROP TABLE IF EXISTS holiday_day_stats;
DROP TABLE IF EXISTS persona_aggregate;
DROP TABLE IF EXISTS persona_history_link;
DROP TABLE IF EXISTS post_metrics_snapshot;
DROP TABLE IF EXISTS repost_relation;
DROP TABLE IF EXISTS recovery_choice;

-- ── New table: comments under topic posts ──
CREATE TABLE IF NOT EXISTS comment (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id   INTEGER NOT NULL REFERENCES analysis_session(id),
    post_id      INTEGER NOT NULL REFERENCES post(id),
    vk_id        INTEGER NOT NULL,
    from_id      INTEGER NOT NULL,
    text         TEXT,
    published_at INTEGER NOT NULL,
    likes        INTEGER NOT NULL DEFAULT 0,
    created_at   INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    UNIQUE(session_id, vk_id)
);
CREATE INDEX IF NOT EXISTS idx_comment_session_post ON comment(session_id, post_id);
CREATE INDEX IF NOT EXISTS idx_comment_session_from ON comment(session_id, from_id);

-- ── New table: NLP results cache ──
CREATE TABLE IF NOT EXISTS nlp_result (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    text_hash     TEXT    NOT NULL,
    model_version TEXT    NOT NULL DEFAULT 'v1',
    lemmas_json   TEXT,
    sentiment     TEXT,
    score         REAL,
    method        TEXT,
    created_at    INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    UNIQUE(text_hash, model_version)
);

-- ── New table: session events journal ──
CREATE TABLE IF NOT EXISTS session_event (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  INTEGER NOT NULL REFERENCES analysis_session(id),
    event_type  TEXT    NOT NULL,
    message     TEXT,
    details     TEXT,
    created_at  INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
);
CREATE INDEX IF NOT EXISTS idx_session_event_session ON session_event(session_id, created_at);

-- ── New table: bootstrap confidence intervals ──
CREATE TABLE IF NOT EXISTS bootstrap_interval (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id     INTEGER NOT NULL REFERENCES analysis_session(id),
    author_id      INTEGER NOT NULL REFERENCES author(id),
    score_name     TEXT    NOT NULL,
    ci_lo          REAL    NOT NULL,
    ci_hi          REAL    NOT NULL,
    procedure_type TEXT    NOT NULL DEFAULT 'one_level',
    iterations     INTEGER NOT NULL DEFAULT 1000,
    created_at     INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    UNIQUE(session_id, author_id, score_name)
);

-- ── New table: composite scores per author ──
CREATE TABLE IF NOT EXISTS composite_score (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id      INTEGER NOT NULL REFERENCES analysis_session(id),
    author_id       INTEGER NOT NULL REFERENCES author(id),
    struct_composite REAL   NOT NULL,
    topic_composite  REAL   NOT NULL,
    created_at      INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    UNIQUE(session_id, author_id)
);

-- ── New table: session-level adaptive thresholds ──
CREATE TABLE IF NOT EXISTS session_threshold (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id      INTEGER NOT NULL UNIQUE REFERENCES analysis_session(id),
    theta_struct    REAL    NOT NULL,
    theta_topic     REAL    NOT NULL,
    created_at      INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
);

-- ── New table: assigned roles per author ──
CREATE TABLE IF NOT EXISTS author_role (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id      INTEGER NOT NULL REFERENCES analysis_session(id),
    author_id       INTEGER NOT NULL REFERENCES author(id),
    base_role       TEXT    NOT NULL,
    position_attr   TEXT    NOT NULL,
    response_attr   TEXT    NOT NULL,
    sufficiency     TEXT    NOT NULL DEFAULT 'PRELIMINARY',
    created_at      INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    UNIQUE(session_id, author_id)
);

-- ── New table: session quality indicators ──
CREATE TABLE IF NOT EXISTS session_quality_indicator (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id      INTEGER NOT NULL REFERENCES analysis_session(id),
    indicator_name  TEXT    NOT NULL,
    value           REAL    NOT NULL,
    status          TEXT    NOT NULL DEFAULT 'BORDERLINE',
    is_primary      INTEGER NOT NULL DEFAULT 1,
    created_at      INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    UNIQUE(session_id, indicator_name)
);

-- ── New table: checkpoints by phase ──
CREATE TABLE IF NOT EXISTS pipeline_checkpoint (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id      INTEGER NOT NULL REFERENCES analysis_session(id),
    phase           TEXT    NOT NULL,
    stage           TEXT    NOT NULL,
    status          TEXT    NOT NULL DEFAULT 'COMPLETED',
    payload_json    TEXT,
    created_at      INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
);
CREATE INDEX IF NOT EXISTS idx_checkpoint_session ON pipeline_checkpoint(session_id, phase);
