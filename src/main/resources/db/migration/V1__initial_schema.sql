-- V1: Initial schema for LOM Analyzer per architecture v6 §31

-- ============================================================
-- analysis_session
-- ============================================================
CREATE TABLE analysis_session (
    id                              INTEGER PRIMARY KEY AUTOINCREMENT,
    name                            TEXT    NOT NULL,
    description                     TEXT,
    topic_query                     TEXT    NOT NULL,
    region                          TEXT,
    display_timezone                TEXT    NOT NULL DEFAULT 'Europe/Moscow',
    baseline_window_days            INTEGER NOT NULL DEFAULT 60,
    current_window_days             INTEGER NOT NULL DEFAULT 30,
    nlp_mode                        TEXT    NOT NULL DEFAULT 'FULL' CHECK (nlp_mode IN ('FULL','FALLBACK_ONLY')),
    nlp_model_versions              TEXT,                       -- JSON dict of model→version
    reference_base_version          TEXT,
    reference_base_sha256           TEXT,
    holidays_version                TEXT,
    sentilex_version                TEXT,
    test_corpus_version             TEXT,
    gamma_calibrated                REAL,
    gamma_clipped                   INTEGER NOT NULL DEFAULT 0, -- bool
    gamma_fallback                  INTEGER NOT NULL DEFAULT 0, -- bool
    gamma_r2_mad                    REAL,
    reference_gamma_divergence_flag TEXT    DEFAULT 'OK' CHECK (reference_gamma_divergence_flag IN ('OK','MILD_RECOMPUTED','AUDIENCE_ONLY_REFERENCE','REFERENCE_UNAVAILABLE')),
    role_threshold_base             REAL,
    role_threshold_event            REAL,
    cv_iqr_json                     TEXT,                       -- JSON with per-component CV_IQR
    coverage_ratio                  REAL,
    session_quality_score           REAL,
    quality_gates_json              TEXT,                       -- JSON array of gate results
    norm_stats_json                 TEXT,                       -- median, IQR, spread, n_eff, CV_IQR, flags
    status                          TEXT    NOT NULL DEFAULT 'CREATED' CHECK (status IN ('CREATED','COLLECTING','ANALYZING','PAUSED_PENDING_RECOVERY','COMPLETED','INCOMPLETE','CANCELLED','FAILED')),
    session_family_id               INTEGER REFERENCES analysis_session(id),
    t_orthogonalized                INTEGER NOT NULL DEFAULT 0, -- bool
    event_weights_variant           TEXT,
    possibly_non_stationary_flag    INTEGER NOT NULL DEFAULT 0, -- bool
    holidays_partial_coverage_flag  INTEGER NOT NULL DEFAULT 0, -- bool
    seasonality_disabled_flag       INTEGER NOT NULL DEFAULT 0, -- bool
    created_at                      INTEGER NOT NULL,           -- unix epoch ms
    updated_at                      INTEGER NOT NULL,
    deleted_at                      INTEGER                     -- soft delete
);

CREATE INDEX idx_session_deleted_at ON analysis_session(deleted_at);

-- ============================================================
-- community
-- ============================================================
CREATE TABLE community (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    vk_id       INTEGER NOT NULL UNIQUE,
    name        TEXT    NOT NULL,
    screen_name TEXT,
    members_count INTEGER,
    is_closed   INTEGER NOT NULL DEFAULT 0,
    community_type TEXT,
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL,
    deleted_at  INTEGER
);

-- ============================================================
-- author
-- ============================================================
CREATE TABLE author (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    vk_id                   INTEGER NOT NULL UNIQUE,
    first_name              TEXT,
    last_name               TEXT,
    screen_name             TEXT,
    followers_count         INTEGER,
    is_closed               INTEGER NOT NULL DEFAULT 0,
    audience_flag           TEXT,                                -- e.g. NORMAL, INFLATED, UNKNOWN
    first_seen_at           INTEGER,
    discovery_source        TEXT    NOT NULL DEFAULT 'SEED',     -- SEED / DISCOVERY
    baseline_window_days    INTEGER NOT NULL DEFAULT 60,
    account_flags           TEXT,                                -- JSON array
    possibly_non_stationary INTEGER NOT NULL DEFAULT 0,          -- bool
    created_at              INTEGER NOT NULL,
    updated_at              INTEGER NOT NULL,
    deleted_at              INTEGER
);

-- ============================================================
-- post
-- ============================================================
CREATE TABLE post (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id          INTEGER NOT NULL REFERENCES analysis_session(id),
    vk_id               INTEGER NOT NULL,
    owner_id            INTEGER NOT NULL,
    from_id             INTEGER NOT NULL,
    published_at        INTEGER NOT NULL,           -- unix epoch
    text                TEXT,
    text_clean           TEXT,
    own_text_length     INTEGER NOT NULL DEFAULT 0,
    likes               INTEGER NOT NULL DEFAULT 0,
    reposts             INTEGER NOT NULL DEFAULT 0,
    comments            INTEGER NOT NULL DEFAULT 0,
    views               INTEGER,
    window              TEXT    NOT NULL CHECK (window IN ('BASELINE','CURRENT')),
    is_topic_relevant   INTEGER,                    -- bool, null until classified
    topic_score_l1      REAL,
    topic_score_l2      REAL,
    topic_score_combined REAL,
    is_holiday          INTEGER NOT NULL DEFAULT 0,
    holiday_name        TEXT,
    detected_language   TEXT,
    language_confidence REAL,
    language_flag       TEXT,                        -- OK, LANGUAGE_UNCERTAIN, FILTERED_OUT_LANGUAGE
    contains_media      INTEGER NOT NULL DEFAULT 0,
    media_types         TEXT,                        -- JSON array
    truncated           INTEGER NOT NULL DEFAULT 0,
    truncation_reason   TEXT,
    has_copy_history    INTEGER NOT NULL DEFAULT 0,
    hashtags_count      INTEGER NOT NULL DEFAULT 0,
    mentions_count      INTEGER NOT NULL DEFAULT 0,
    urls_count          INTEGER NOT NULL DEFAULT 0,
    created_at          INTEGER NOT NULL,
    deleted_at          INTEGER,
    UNIQUE(session_id, vk_id, owner_id)
);

CREATE INDEX idx_post_session_published   ON post(session_id, published_at);
CREATE INDEX idx_post_session_from        ON post(session_id, from_id);
CREATE INDEX idx_post_session_relevant    ON post(session_id, is_topic_relevant);
CREATE INDEX idx_post_session_window      ON post(session_id, window);
CREATE INDEX idx_post_session_holiday     ON post(session_id, is_holiday);

-- ============================================================
-- processed_text
-- ============================================================
CREATE TABLE processed_text (
    post_id     INTEGER PRIMARY KEY REFERENCES post(id),
    lemmas_json TEXT,           -- JSON array of lemmas
    entities_json TEXT,         -- JSON array of NER entities
    language    TEXT,
    clean_text  TEXT
);

-- ============================================================
-- sentiment_result
-- ============================================================
CREATE TABLE sentiment_result (
    post_id              INTEGER PRIMARY KEY REFERENCES post(id),
    sentiment            TEXT    NOT NULL,   -- POSITIVE, NEGATIVE, NEUTRAL, SKIP, SPEECH
    score                REAL,
    method               TEXT    NOT NULL,   -- DICTIONARY, MODEL, NO_LEXICON_MATCH, LOW_CONFIDENCE
    negation_applied     INTEGER NOT NULL DEFAULT 0,
    bootstrap_agreement  REAL,
    bootstrap_variants   TEXT                -- JSON
);

-- ============================================================
-- lom_score
-- ============================================================
CREATE TABLE lom_score (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id                  INTEGER NOT NULL REFERENCES analysis_session(id),
    author_id                   INTEGER NOT NULL REFERENCES author(id),
    audience_norm               REAL,
    audience_ci_lo              REAL,
    audience_ci_hi              REAL,
    engagement_density_norm     REAL,
    engagement_density_ci_lo    REAL,
    engagement_density_ci_hi    REAL,
    base_influence_hist         REAL,
    base_influence_hist_ci_lo   REAL,
    base_influence_hist_ci_hi   REAL,
    base_influence_curr         REAL,
    base_influence_curr_ci_lo   REAL,
    base_influence_curr_ci_hi   REAL,
    base_influence_abs          REAL,
    base_influence_abs_ci_lo    REAL,
    base_influence_abs_ci_hi    REAL,
    event_activity_hist         REAL,
    event_activity_hist_ci_lo   REAL,
    event_activity_hist_ci_hi   REAL,
    event_activity_curr         REAL,
    event_activity_curr_ci_lo   REAL,
    event_activity_curr_ci_hi   REAL,
    topic_focus_raw             REAL,
    topic_volume_norm           REAL,
    dissemination_reach_norm    REAL,
    content_originality_norm    REAL,
    k_window                    INTEGER,
    gamma_used                  REAL,
    t_orthogonalized            INTEGER NOT NULL DEFAULT 0,
    r_orthogonalization_mad_r2  REAL,
    role_session                TEXT,
    role_reference              TEXT,
    role_combined               TEXT,
    role_confidence             REAL,
    role_combination_flag       TEXT,
    created_at                  INTEGER NOT NULL,
    UNIQUE(session_id, author_id)
);

CREATE INDEX idx_lom_session_base   ON lom_score(session_id, base_influence_hist);
CREATE INDEX idx_lom_session_role   ON lom_score(session_id, role_combination_flag);

-- ============================================================
-- repost_relation
-- ============================================================
CREATE TABLE repost_relation (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id      INTEGER NOT NULL REFERENCES analysis_session(id),
    original_post_id INTEGER NOT NULL REFERENCES post(id),
    repost_post_id  INTEGER NOT NULL REFERENCES post(id),
    reposter_vk_id  INTEGER NOT NULL,
    reposted_at     INTEGER NOT NULL,
    UNIQUE(session_id, original_post_id, repost_post_id)
);

-- ============================================================
-- dedup_group
-- ============================================================
CREATE TABLE dedup_group (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  INTEGER NOT NULL REFERENCES analysis_session(id),
    canonical_post_id INTEGER NOT NULL REFERENCES post(id),
    duplicate_post_id INTEGER NOT NULL REFERENCES post(id),
    similarity  REAL    NOT NULL,
    method      TEXT    NOT NULL,  -- EXACT, NEAR_DUPLICATE
    UNIQUE(session_id, duplicate_post_id)
);

-- ============================================================
-- anomaly_event
-- ============================================================
CREATE TABLE anomaly_event (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id                  INTEGER NOT NULL REFERENCES analysis_session(id),
    type                        TEXT    NOT NULL,  -- VOLUME_SPIKE, TONE_SHIFT_NEGATIVE, TONE_SHIFT_POSITIVE, GIANT_ACTIVATION
    day_date                    INTEGER NOT NULL,  -- unix epoch (midnight UTC)
    severity                    REAL    NOT NULL,
    severity_ci90_lo            REAL,
    severity_ci90_hi            REAL,
    description                 TEXT,
    is_holiday_day              INTEGER NOT NULL DEFAULT 0,
    routine_protection_applied  INTEGER NOT NULL DEFAULT 0,
    metadata_json               TEXT,
    created_at                  INTEGER NOT NULL
);

CREATE INDEX idx_anomaly_session_type_day ON anomaly_event(session_id, type, day_date);

-- ============================================================
-- risk_signal
-- ============================================================
CREATE TABLE risk_signal (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id      INTEGER NOT NULL REFERENCES analysis_session(id),
    risk_score      REAL    NOT NULL,
    risk_ci90_lo    REAL,
    risk_ci90_hi    REAL,
    is_borderline   INTEGER NOT NULL DEFAULT 0,
    category        TEXT,
    description     TEXT,
    recommendation  TEXT,
    decomposition_json TEXT,  -- JSON breakdown of contributing factors
    created_at      INTEGER NOT NULL
);

-- ============================================================
-- collection_checkpoint
-- ============================================================
CREATE TABLE collection_checkpoint (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  INTEGER NOT NULL REFERENCES analysis_session(id),
    endpoint    TEXT    NOT NULL,
    owner_id    INTEGER NOT NULL,
    offset_value TEXT,
    items_collected INTEGER NOT NULL DEFAULT 0,
    status      TEXT    NOT NULL DEFAULT 'IN_PROGRESS',
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL
);

CREATE INDEX idx_checkpoint_session_ep ON collection_checkpoint(session_id, endpoint, owner_id);

-- ============================================================
-- audit_log
-- ============================================================
CREATE TABLE audit_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  INTEGER REFERENCES analysis_session(id),
    action      TEXT    NOT NULL,
    details     TEXT,           -- JSON
    created_at  INTEGER NOT NULL
);

CREATE INDEX idx_audit_session_time ON audit_log(session_id, created_at);

-- ============================================================
-- recovery_choice
-- ============================================================
CREATE TABLE recovery_choice (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id              INTEGER NOT NULL REFERENCES analysis_session(id),
    python_failure_count    INTEGER NOT NULL,
    choice                  TEXT    NOT NULL CHECK (choice IN ('WAIT','FALLBACK','CANCEL')),
    pipeline_stage          TEXT,
    payload_json            TEXT,
    timestamp               INTEGER NOT NULL
);

CREATE INDEX idx_recovery_session_ts ON recovery_choice(session_id, timestamp);

-- ============================================================
-- session_metrics
-- ============================================================
CREATE TABLE session_metrics (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  INTEGER NOT NULL REFERENCES analysis_session(id),
    stage       TEXT    NOT NULL,
    duration_ms INTEGER NOT NULL,
    items_processed INTEGER,
    metadata_json TEXT,
    created_at  INTEGER NOT NULL
);

-- ============================================================
-- persona_aggregate
-- ============================================================
CREATE TABLE persona_aggregate (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    author_id                   INTEGER NOT NULL REFERENCES author(id),
    session_id                  INTEGER NOT NULL REFERENCES analysis_session(id),
    snapshot_timestamp          INTEGER NOT NULL,
    audience                    REAL,
    audience_ci_lo              REAL,
    audience_ci_hi              REAL,
    engagement_density          REAL,
    engagement_density_ci_lo    REAL,
    engagement_density_ci_hi    REAL,
    i_base_hist                 REAL,
    i_base_hist_ci_lo           REAL,
    i_base_hist_ci_hi           REAL,
    i_base_curr                 REAL,
    i_base_curr_ci_lo           REAL,
    i_base_curr_ci_hi           REAL,
    i_base_abs                  REAL,
    i_base_abs_ci_lo            REAL,
    i_base_abs_ci_hi            REAL,
    i_event_hist                REAL,
    i_event_hist_ci_lo          REAL,
    i_event_hist_ci_hi          REAL,
    i_event_curr                REAL,
    i_event_curr_ci_lo          REAL,
    i_event_curr_ci_hi          REAL,
    role_session                TEXT,
    role_reference              TEXT,
    role_combined               TEXT,
    role_confidence             REAL,
    role_combination_flag       TEXT,
    total_posts                 INTEGER,
    topic_posts_eff             REAL,
    topic_focus_raw             REAL,
    topic_volume_norm           REAL,
    dissemination_reach_norm    REAL,
    originality_norm            REAL,
    avg_sentiment_huber         REAL,
    avg_sentiment_huber_ci_lo   REAL,
    avg_sentiment_huber_ci_hi   REAL,
    sentiment_unstable_ratio    REAL,
    author_tone_mixed_flag      INTEGER NOT NULL DEFAULT 0,
    visual_activity_ratio       REAL,
    top_terms                   TEXT,    -- JSON array
    account_flags               TEXT,    -- JSON array
    first_seen_at               INTEGER,
    last_seen_at                INTEGER,
    anomaly_participation       TEXT,    -- JSON
    discovery_source            TEXT,
    baseline_window_days        INTEGER,
    k_window                    INTEGER,
    gamma_used                  REAL,
    gamma_divergence_flag       TEXT,
    t_orthogonalized            INTEGER NOT NULL DEFAULT 0,
    possibly_non_stationary_flag INTEGER NOT NULL DEFAULT 0,
    UNIQUE(session_id, author_id)
);

-- ============================================================
-- holiday_day_stats (materialized table)
-- ============================================================
CREATE TABLE holiday_day_stats (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id          INTEGER NOT NULL REFERENCES analysis_session(id),
    date                INTEGER NOT NULL,   -- UTC midnight epoch
    is_holiday          INTEGER NOT NULL DEFAULT 0,
    holiday_name        TEXT,
    volume_observed     INTEGER NOT NULL DEFAULT 0,
    tone_mean_observed  REAL,
    post_count          INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_holiday_stats_session_date ON holiday_day_stats(session_id, date);

-- ============================================================
-- post_metrics_snapshot (reserved for SessionFamily v2.0)
-- ============================================================
CREATE TABLE post_metrics_snapshot (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  INTEGER NOT NULL REFERENCES analysis_session(id),
    post_id     INTEGER NOT NULL REFERENCES post(id),
    snapshot_json TEXT,
    created_at  INTEGER NOT NULL
);

-- ============================================================
-- Link tables
-- ============================================================
CREATE TABLE session_community (
    session_id   INTEGER NOT NULL REFERENCES analysis_session(id),
    community_id INTEGER NOT NULL REFERENCES community(id),
    PRIMARY KEY (session_id, community_id)
);

CREATE TABLE session_author (
    session_id INTEGER NOT NULL REFERENCES analysis_session(id),
    author_id  INTEGER NOT NULL REFERENCES author(id),
    role       TEXT,   -- SEED, DISCOVERY
    PRIMARY KEY (session_id, author_id)
);

CREATE TABLE anomaly_author_link (
    anomaly_id INTEGER NOT NULL REFERENCES anomaly_event(id),
    author_id  INTEGER NOT NULL REFERENCES author(id),
    PRIMARY KEY (anomaly_id, author_id)
);

CREATE TABLE anomaly_post_link (
    anomaly_id INTEGER NOT NULL REFERENCES anomaly_event(id),
    post_id    INTEGER NOT NULL REFERENCES post(id),
    PRIMARY KEY (anomaly_id, post_id)
);

CREATE TABLE risk_anomaly_link (
    risk_id    INTEGER NOT NULL REFERENCES risk_signal(id),
    anomaly_id INTEGER NOT NULL REFERENCES anomaly_event(id),
    PRIMARY KEY (risk_id, anomaly_id)
);
