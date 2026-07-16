-- V10: Replace v6 lom_score (I_base/I_event) with 11 quantitative scores across 4 axes
-- Per diploma Appendix E.4

DROP TABLE IF EXISTS lom_score;

CREATE TABLE lom_score (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id        INTEGER NOT NULL REFERENCES analysis_session(id),
    author_id         INTEGER NOT NULL REFERENCES author(id),

    -- Structural influence (axis 1)
    aud               REAL,     -- Aud_a = log(1 + F_a)                    [point estimate]
    age               REAL,     -- Age_a = normalized account age          [point estimate]
    er_bg             REAL,     -- ER_a^bg = background engagement rate    [sample estimate]

    -- Topic activity (axis 2)
    top_vol           INTEGER,  -- TopVol_a = |T_a|                        [point count]
    top_focus         REAL,     -- TopFocus_a = |T_a|/(|T_a|+|B_a^period|) [point count]
    reach             REAL,     -- Reach_a = sum(V_i or A_i)              [sample estimate]

    -- Author position (axis 3)
    pos_positive      REAL,     -- Pos_a.positive                          [sample estimate]
    pos_neutral       REAL,     -- Pos_a.neutral
    pos_negative      REAL,     -- Pos_a.negative

    -- Audience response (axis 4)
    er_top            REAL,     -- ER_a^top = topic engagement rate        [sample estimate]
    resp_positive     REAL,     -- Resp_a.positive                         [sample, clustered]
    resp_neutral      REAL,     -- Resp_a.neutral
    resp_negative     REAL,     -- Resp_a.negative

    -- Service fields (needed for bootstrap stage 6 and sufficiency stage 8)
    bg_post_count     INTEGER NOT NULL DEFAULT 0,   -- |B_a|
    topic_post_count  INTEGER NOT NULL DEFAULT 0,   -- |T_a|
    comment_count     INTEGER NOT NULL DEFAULT 0,   -- sum_i |C_i|
    followers_count   INTEGER,                       -- F_a (for ER formulas)

    created_at        INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    UNIQUE(session_id, author_id)
);

CREATE INDEX idx_lom_score_session ON lom_score(session_id);
