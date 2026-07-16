-- Replace UNIQUE(session_id, vk_id, owner_id) with UNIQUE(session_id, vk_id, owner_id, window)
-- so the same post can exist in both BASELINE and CURRENT windows.
-- SQLite does not support ALTER TABLE DROP CONSTRAINT, so we recreate the table.

CREATE TABLE post_new (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id          INTEGER NOT NULL REFERENCES analysis_session(id),
    vk_id               INTEGER NOT NULL,
    owner_id            INTEGER NOT NULL,
    from_id             INTEGER NOT NULL,
    published_at        INTEGER NOT NULL,
    text                TEXT,
    text_clean          TEXT,
    own_text_length     INTEGER NOT NULL DEFAULT 0,
    likes               INTEGER NOT NULL DEFAULT 0,
    reposts             INTEGER NOT NULL DEFAULT 0,
    comments            INTEGER NOT NULL DEFAULT 0,
    views               INTEGER,
    window              TEXT    NOT NULL CHECK (window IN ('BASELINE','CURRENT')),
    is_topic_relevant   INTEGER,
    topic_score_l1      REAL,
    topic_score_l2      REAL,
    topic_score_combined REAL,
    is_holiday          INTEGER NOT NULL DEFAULT 0,
    holiday_name        TEXT,
    detected_language   TEXT,
    language_confidence REAL,
    language_flag       TEXT,
    contains_media      INTEGER NOT NULL DEFAULT 0,
    media_types         TEXT,
    truncated           INTEGER NOT NULL DEFAULT 0,
    truncation_reason   TEXT,
    has_copy_history    INTEGER NOT NULL DEFAULT 0,
    hashtags_count      INTEGER NOT NULL DEFAULT 0,
    mentions_count      INTEGER NOT NULL DEFAULT 0,
    urls_count          INTEGER NOT NULL DEFAULT 0,
    created_at          INTEGER NOT NULL,
    deleted_at          INTEGER,
    analyst_vote        INTEGER,
    originality_type    TEXT DEFAULT NULL,
    UNIQUE(session_id, vk_id, owner_id, window)
);

INSERT INTO post_new SELECT * FROM post;

DROP TABLE post;

ALTER TABLE post_new RENAME TO post;

-- Recreate indexes
CREATE INDEX idx_post_session_published ON post(session_id, published_at);
CREATE INDEX idx_post_session_from      ON post(session_id, from_id);
CREATE INDEX idx_post_session_relevant  ON post(session_id, is_topic_relevant);
CREATE INDEX idx_post_session_window    ON post(session_id, window);
CREATE INDEX idx_post_session_holiday   ON post(session_id, is_holiday);
