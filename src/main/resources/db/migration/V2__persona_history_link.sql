-- V2: Add persona_history_link table for longitudinal tracking (v2.0 readiness)
CREATE TABLE persona_history_link (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    source_session_id INTEGER NOT NULL,
    target_session_id INTEGER NOT NULL,
    author_id       INTEGER NOT NULL,
    created_at      INTEGER NOT NULL
);

CREATE INDEX idx_persona_link_source ON persona_history_link(source_session_id);
CREATE INDEX idx_persona_link_target ON persona_history_link(target_session_id);
