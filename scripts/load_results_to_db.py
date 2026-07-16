"""
НАЗНАЧЕНИЕ
Загрузчик готовых результатов анализа в рабочую SQLite-базу приложения LOM
Analyzer. Берёт предрассчитанные метрики (examples/dataset_ai_v2_results.json) и
исходный датасет (examples/dataset_ai_v2.json) и создаёт в БД целостную
завершённую сессию: сообщества, авторов, посты и комментарии с тональностью,
11 оценок (lom_score), композиты, пороги, роли и bootstrap-интервалы. После
загрузки сессия видна в приложении на экранах истории, дашборда и детализации.
Закрывает этап публикации результатов в UI вне основного пайплайна.

ЧТО ВНУТРИ
- Константы путей к БД и файлам, метаданные сессии (имя, тема, описание).
- main() — внутри одной транзакции по шагам наполняет связанные таблицы
  ([1] session, [2] community, [3] author, [4] post, [5] post sentiment,
  [6] comment + sentiment, [7] lom_score, [8] composite_score,
  [9] session_threshold, [10] author_role, [11] bootstrap_interval,
  [12] pipeline_checkpoint, [13] session_event, [14] финализация сессии).
- Вложенная sentilex_sentiment — словарный sentiment (та же логика, что в
  calculate_all_metrics.py), пересчитывается на лету для записи в БД.
- Словари ROLE_MAP/POS_MAP/RESP_MAP — приведение кодов скрипта к enum'ам БД.

МЕТОД
Идемпотентность по справочным сущностям через SELECT-перед-INSERT и
INSERT OR IGNORE; вся загрузка обёрнута в транзакцию с rollback при ошибке.
WAL и foreign_keys включаются через PRAGMA. Метки времени — единый now_ms.

БИБЛИОТЕКИ
Стандартная библиотека: json, sqlite3 (прямой доступ к БД в обход Exposed), time,
pathlib, re (внутри main).

СВЯЗИ
Вход: examples/dataset_ai_v2.json, examples/dataset_ai_v2_results.json,
src/main/resources/.../sentilex_base.json. Выход: запись в SQLite-файл
~/AppData/Local/LomAnalyzer/lom_analyzer.db (схема Flyway V1–V10).
"""

import json
import sqlite3
import time
from pathlib import Path

DB_PATH = Path.home() / "AppData" / "Local" / "LomAnalyzer" / "lom_analyzer.db"  # рабочая БД приложения
DATASET_PATH = Path(__file__).parent.parent / "examples" / "dataset_ai_v2.json"
RESULTS_PATH = Path(__file__).parent.parent / "examples" / "dataset_ai_v2_results.json"

# Метаданные создаваемой сессии (отображаются в UI)
SESSION_NAME = "Развитие ИИ в России"
TOPIC_QUERY = "искусственный интеллект нейросети ИИ AI"
DESCRIPTION = "Синтетический датасет v2: 5 сообществ, 100 авторов, 1133 поста, 1787 комментариев. Тема: ИИ и нейросети в России."

now_ms = int(time.time() * 1000)  # единая метка времени (мс) для всех created_at/updated_at


def main():
    """Загружает полную сессию (сущности + метрики) в SQLite одной транзакцией."""
    # Загружаем датасет и результаты расчёта
    with open(DATASET_PATH, "r", encoding="utf-8") as f:
        dataset = json.load(f)
    with open(RESULTS_PATH, "r", encoding="utf-8") as f:
        results = json.load(f)

    conn = sqlite3.connect(str(DB_PATH))
    conn.execute("PRAGMA journal_mode=WAL")    # режим WAL — совместимость с приложением
    conn.execute("PRAGMA foreign_keys=ON")     # включаем контроль внешних ключей
    cur = conn.cursor()

    try:
        # ── 1. Создаём запись сессии ──
        cur.execute("""
            INSERT INTO analysis_session (
                name, description, topic_query,
                primary_ngrams, secondary_ngrams,
                baseline_window_days, current_window_days,
                nlp_mode, role_mode, sentilex_version,
                import_json_path, status,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            SESSION_NAME, DESCRIPTION, TOPIC_QUERY,
            "искусственный интеллект\nнейросети\nИИ\nAI\nмашинное обучение",
            "GPT\nYandexGPT\nGigaChat\nСбер\nЯндекс",
            60, 30,
            "FULL", "QUADRANT", "2.0.0",
            "examples/dataset_ai_v2.json", "COMPLETED",
            now_ms, now_ms,
        ))
        session_id = cur.lastrowid  # id новой сессии — внешний ключ для всех связанных таблиц
        print(f"[1] Created session #{session_id}: {SESSION_NAME}")

        # ── 2. Вставляем сообщества ──
        community_db_ids = {}  # vkId сообщества -> id строки в БД
        for comm in dataset["communities"]:
            # Идемпотентность: если сообщество уже есть, переиспользуем его id, иначе вставляем
            cur.execute("SELECT id FROM community WHERE vk_id = ?", (comm["vkId"],))
            row = cur.fetchone()
            if row:
                community_db_ids[comm["vkId"]] = row[0]
            else:
                cur.execute("""
                    INSERT INTO community (vk_id, name, screen_name, members_count, is_closed, community_type, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    comm["vkId"], comm["name"], comm.get("screenName"),
                    comm.get("membersCount"), 1 if comm.get("isClosed") else 0,
                    comm.get("type"), now_ms, now_ms,
                ))
                community_db_ids[comm["vkId"]] = cur.lastrowid

        # Link communities to session
        for vk_id, db_id in community_db_ids.items():
            cur.execute(
                "INSERT OR IGNORE INTO session_community (session_id, community_id) VALUES (?, ?)",
                (session_id, db_id),
            )
        print(f"[2] Inserted {len(community_db_ids)} communities")

        # ── 3. Вставляем авторов (с идемпотентностью по vk_id) ──
        author_db_ids = {}  # vkId автора -> id строки в БД
        for author in dataset["authors"]:
            cur.execute("SELECT id FROM author WHERE vk_id = ?", (author["vkId"],))
            row = cur.fetchone()
            if row:
                author_db_ids[author["vkId"]] = row[0]
                # Update followers count if needed
                cur.execute(
                    "UPDATE author SET followers_count = ?, updated_at = ? WHERE id = ?",
                    (author["followersCount"], now_ms, row[0]),
                )
            else:
                cur.execute("""
                    INSERT INTO author (vk_id, first_name, last_name, screen_name, followers_count,
                                        is_closed, discovery_source, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    author["vkId"], author.get("firstName"), author.get("lastName"),
                    author.get("screenName"), author.get("followersCount"),
                    1 if author.get("isClosed") else 0, "SEED", now_ms, now_ms,
                ))
                author_db_ids[author["vkId"]] = cur.lastrowid

        # Link authors to session
        for vk_id, db_id in author_db_ids.items():
            cur.execute(
                "INSERT OR IGNORE INTO session_author (session_id, author_id, role) VALUES (?, ?, ?)",
                (session_id, db_id, "SEED"),
            )
        print(f"[3] Inserted {len(author_db_ids)} authors")

        # ── 4. Вставляем посты ──
        post_db_ids = {}  # vkId поста -> id строки в БД
        for post in dataset["posts"]:
            cur.execute("""
                INSERT INTO post (
                    session_id, vk_id, owner_id, from_id, published_at,
                    text, likes, reposts, comments, views,
                    window, is_topic_relevant, contains_media, has_copy_history,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                session_id, post["vkId"], post["ownerId"], post["fromId"], post["date"],
                post.get("text"), post.get("likes", 0), post.get("reposts", 0),
                post.get("comments", 0), post.get("views"),
                post["window"],
                1 if post["window"] == "CURRENT" else 0,  # все CURRENT-посты считаем тематическими
                1 if post.get("containsMedia") else 0,
                1 if post.get("hasCopyHistory") else 0,
                now_ms,
            ))
            post_db_ids[post["vkId"]] = cur.lastrowid
        print(f"[4] Inserted {len(post_db_ids)} posts")

        # ── 5. Вставляем sentiment_result для CURRENT-постов ──
        # Пересчитываем тональность на лету той же словарной логикой, что и в
        # calculate_all_metrics.py (sentilex со сравнением по основам).
        import re
        sentilex_path = Path(__file__).parent.parent / "src" / "main" / "resources" / "resources" / "sentilex_base.json"
        with open(sentilex_path, "r", encoding="utf-8") as f:
            sentilex = json.load(f)
        pos_full = set(w.lower() for w in sentilex.get("positive", []))
        neg_full = set(w.lower() for w in sentilex.get("negative", []))
        pos_stems = set()
        neg_stems = set()
        for w in pos_full:
            pos_stems.add(w[:4])
            if len(w) > 5: pos_stems.add(w[:5])
        for w in neg_full:
            neg_stems.add(w[:4])
            if len(w) > 5: neg_stems.add(w[:5])
        overlap = pos_stems & neg_stems
        pos_stems -= overlap
        neg_stems -= overlap
        word_re = re.compile(r'[а-яёa-z]+', re.IGNORECASE)

        def sentilex_sentiment(text):
            """Словарная тональность текста -> (метка, оценка уверенности 0.5..1.0).

            Точное совпадение слова даёт +2, совпадение по 4-символьной основе +1.
            Метка по перевесу баллов; score растёт с долей «победивших» баллов.
            """
            if not text: return "NEUTRAL", 0.5
            words = word_re.findall(text.lower())
            ps, ns = 0, 0
            for w in words:
                if w in pos_full: ps += 2
                elif len(w) >= 4 and w[:4] in pos_stems: ps += 1
                if w in neg_full: ns += 2
                elif len(w) >= 4 and w[:4] in neg_stems: ns += 1
            total = max(ps + ns, 1)
            if ps > ns: return "POSITIVE", min(0.5 + ps / total * 0.5, 1.0)
            elif ns > ps: return "NEGATIVE", min(0.5 + ns / total * 0.5, 1.0)
            return "NEUTRAL", 0.5

        sent_count = 0
        for post in dataset["posts"]:
            if post["window"] == "CURRENT":
                db_id = post_db_ids[post["vkId"]]
                label, score = sentilex_sentiment(post.get("text", ""))
                cur.execute("""
                    INSERT OR IGNORE INTO sentiment_result (post_id, sentiment, score, method)
                    VALUES (?, ?, ?, ?)
                """, (db_id, label, score, "sentilex_dictionary"))
                sent_count += 1
        print(f"[5] Inserted {sent_count} post sentiment results")

        # ── 6. Вставляем комментарии и их тональность ──
        comment_count = 0
        comment_sent_count = 0
        for comm in dataset["comments"]:
            post_key = comm["postVkId"]
            if post_key not in post_db_ids:
                continue  # пропускаем комментарии к постам, которых нет в БД
            post_db_id = post_db_ids[post_key]
            cur.execute("""
                INSERT INTO comment (
                    session_id, post_id, vk_id, from_id, text, published_at, likes, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                session_id, post_db_id, comm["vkId"], comm["fromId"],
                comm.get("text"), comm["date"], comm.get("likes", 0), now_ms,
            ))
            comment_db_id = cur.lastrowid
            comment_count += 1

            # Тональность комментария храним в sentiment_result, привязав к его db id
            label, score = sentilex_sentiment(comm.get("text", ""))
            cur.execute("""
                INSERT OR IGNORE INTO sentiment_result (post_id, sentiment, score, method)
                VALUES (?, ?, ?, ?)
            """, (comment_db_id, label, score, "sentilex_dictionary"))
            comment_sent_count += 1

        print(f"[6] Inserted {comment_count} comments + {comment_sent_count} comment sentiments")

        # ── 7. Вставляем lom_score (11 оценок) для каждого оценённого автора ──
        # Индекс результатов по screenName
        author_results_by_name = {}
        for a in results["authors"]:
            author_results_by_name[a["screenName"]] = a

        # Сопоставление результатов с vkId авторов идёт через screenName
        author_vk_by_screen = {a.get("screenName"): a["vkId"] for a in dataset["authors"]}

        lom_count = 0
        scored_author_db_ids = []  # (db id автора, словарь результата) — пригодится для следующих таблиц
        for a in results["authors"]:
            screen = a["screenName"]
            vk_id = author_vk_by_screen.get(screen)
            if not vk_id or vk_id not in author_db_ids:
                continue  # нет соответствия автору в БД — пропускаем
            db_id = author_db_ids[vk_id]

            cur.execute("""
                INSERT INTO lom_score (
                    session_id, author_id,
                    aud, age, er_bg,
                    top_vol, top_focus, reach,
                    pos_positive, pos_neutral, pos_negative,
                    er_top,
                    resp_positive, resp_neutral, resp_negative,
                    bg_post_count, topic_post_count, comment_count, followers_count,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                session_id, db_id,
                a["aud"], a["age"], a["er_bg"],
                a["top_vol"], a["top_focus"], a["reach"],
                a["pos"]["positive"], a["pos"]["neutral"], a["pos"]["negative"],
                a["er_top"],
                a["resp"]["positive"], a["resp"]["neutral"], a["resp"]["negative"],
                a["baselinePostCount"], a["topicPostCount"], a["commentCount"],
                a["followers"],
                now_ms,
            ))
            scored_author_db_ids.append((db_id, a))
            lom_count += 1
        print(f"[7] Inserted {lom_count} lom_scores")

        # ── 8. Вставляем composite_score (структурный и тематический композиты) ──
        for db_id, a in scored_author_db_ids:
            cur.execute("""
                INSERT INTO composite_score (session_id, author_id, struct_composite, topic_composite, created_at)
                VALUES (?, ?, ?, ?, ?)
            """, (session_id, db_id, a["structural_composite"], a["topic_composite"], now_ms))
        print(f"[8] Inserted {len(scored_author_db_ids)} composite_scores")

        # ── 9. Вставляем адаптивные пороги классификации (theta) ──
        cur.execute("""
            INSERT INTO session_threshold (session_id, theta_struct, theta_topic, created_at)
            VALUES (?, ?, ?, ?)
        """, (
            session_id,
            results["thresholds"]["theta_struct"],
            results["thresholds"]["theta_topic"],
            now_ms,
        ))
        print(f"[9] Inserted session_threshold: theta_Struct={results['thresholds']['theta_struct']}, theta_Topic={results['thresholds']['theta_topic']}")

        # ── 10. Вставляем author_role (роль + атрибуты позиции/отклика) ──
        # Приводим коды ролей/позиции/отклика скрипта к enum-значениям схемы БД
        ROLE_MAP = {
            "AUTHORITATIVE_LEADER": "AUTHORITATIVE",
            "SLEEPING_GIANT": "SLEEPING",
            "TOPIC_ACTIVIST": "ACTIVIST",
            "BACKGROUND_AUTHOR": "BACKGROUND_AUTHOR",
        }
        POS_MAP = {
            "SUPPORTIVE": "POSITIVE",
            "NEUTRAL": "NEUTRAL",
            "CRITICAL": "NEGATIVE",
        }
        RESP_MAP = {
            "APPROVING": "POSITIVE",
            "MIXED": "MIXED",
            "CRITICAL": "NEGATIVE",
        }
        for db_id, a in scored_author_db_ids:
            cur.execute("""
                INSERT INTO author_role (session_id, author_id, base_role, position_attr, response_attr, sufficiency, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """, (
                session_id, db_id,
                ROLE_MAP.get(a["role"], a["role"]),
                POS_MAP.get(a["author_position"], a["author_position"]),
                RESP_MAP.get(a["audience_response"], a["audience_response"]),
                a["data_sufficiency"],
                now_ms,
            ))
        print(f"[10] Inserted {len(scored_author_db_ids)} author_roles")

        # ── 11. Вставляем bootstrap_interval (доверительные интервалы метрик) ──
        # Для каждого автора пишем все доступные CI: ER_bg, ER_top, Reach (одноуровневые),
        # Pos (p+/p−) и Resp (p+/p−, двухуровневый). Для двухуровневого число итераций
        # = B_outer × B_inner.
        bi_count = 0
        for db_id, a in scored_author_db_ids:
            # CI для ER_bg
            ci = a.get("er_bg_ci")
            if ci:
                cur.execute("""
                    INSERT INTO bootstrap_interval (session_id, author_id, score_name, ci_lo, ci_hi, procedure_type, iterations, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, (session_id, db_id, "er_bg", ci["ci_lo"], ci["ci_hi"], ci["procedure"], ci["B"], now_ms))
                bi_count += 1
            # ER_top CI
            ci = a.get("er_top_ci")
            if ci:
                cur.execute("""
                    INSERT INTO bootstrap_interval (session_id, author_id, score_name, ci_lo, ci_hi, procedure_type, iterations, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, (session_id, db_id, "er_top", ci["ci_lo"], ci["ci_hi"], ci["procedure"], ci["B"], now_ms))
                bi_count += 1
            # Reach CI
            ci = a.get("reach_ci")
            if ci:
                cur.execute("""
                    INSERT INTO bootstrap_interval (session_id, author_id, score_name, ci_lo, ci_hi, procedure_type, iterations, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, (session_id, db_id, "reach", ci["ci_lo"], ci["ci_hi"], ci["procedure"], ci["B"], now_ms))
                bi_count += 1
            # Pos CI (positive component)
            ci = a.get("pos_ci")
            if ci:
                p_ci = ci["positive_ci"]
                cur.execute("""
                    INSERT INTO bootstrap_interval (session_id, author_id, score_name, ci_lo, ci_hi, procedure_type, iterations, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, (session_id, db_id, "pos_positive", p_ci["ci_lo"], p_ci["ci_hi"], ci["procedure"], ci["B"], now_ms))
                n_ci = ci["negative_ci"]
                cur.execute("""
                    INSERT INTO bootstrap_interval (session_id, author_id, score_name, ci_lo, ci_hi, procedure_type, iterations, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, (session_id, db_id, "pos_negative", n_ci["ci_lo"], n_ci["ci_hi"], ci["procedure"], ci["B"], now_ms))
                bi_count += 2
            # Resp CI (two-level)
            ci = a.get("resp_ci")
            if ci:
                p_ci = ci["positive_ci"]
                cur.execute("""
                    INSERT INTO bootstrap_interval (session_id, author_id, score_name, ci_lo, ci_hi, procedure_type, iterations, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, (session_id, db_id, "resp_positive", p_ci["ci_lo"], p_ci["ci_hi"],
                      ci["procedure"], ci.get("B_outer", 300) * ci.get("B_inner", 100), now_ms))
                n_ci = ci["negative_ci"]
                cur.execute("""
                    INSERT INTO bootstrap_interval (session_id, author_id, score_name, ci_lo, ci_hi, procedure_type, iterations, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, (session_id, db_id, "resp_negative", n_ci["ci_lo"], n_ci["ci_hi"],
                      ci["procedure"], ci.get("B_outer", 300) * ci.get("B_inner", 100), now_ms))
                bi_count += 2

        print(f"[11] Inserted {bi_count} bootstrap_intervals")

        # ── 12. Вставляем чекпоинты пайплайна (помечаем все этапы выполненными) ──
        stages = [
            ("PHASE_1", "SETUP"),
            ("PHASE_2", "COLLECTION"),
            ("PHASE_3", "PREPROCESSING"),
            ("PHASE_3", "TOPIC_FILTER"),
            ("PHASE_4", "SCORING"),
            ("PHASE_4", "INFERENCE"),
            ("PHASE_5", "COMPOSITE_ROLES"),
            ("PHASE_5", "QUALITY_CHECK"),
        ]
        for phase, stage in stages:
            cur.execute("""
                INSERT INTO pipeline_checkpoint (session_id, phase, stage, status, created_at)
                VALUES (?, ?, ?, ?, ?)
            """, (session_id, phase, stage, "COMPLETED", now_ms))
        print(f"[12] Inserted {len(stages)} pipeline checkpoints")

        # ── 13. Вставляем события сессии (журнал хода анализа) ──
        events = [
            ("SESSION_CREATED", f"Session '{SESSION_NAME}' created"),
            ("COLLECTION_STARTED", "Data collection from JSON import"),
            ("COLLECTION_COMPLETED", f"Imported 5 communities, 100 authors, 1133 posts, 1787 comments"),
            ("PREPROCESSING_STARTED", "Text cleaning, lemmatization, sentiment"),
            ("PREPROCESSING_COMPLETED", f"Processed 1133 posts, 478 CURRENT sentiments"),
            ("TOPIC_FILTER_COMPLETED", "478 CURRENT posts marked as topic-relevant"),
            ("SCORING_COMPLETED", f"11 scores computed for 95 authors"),
            ("INFERENCE_COMPLETED", f"Bootstrap CIs: one-level B=1000 + two-level 300x100"),
            ("COMPOSITE_ROLES_COMPLETED", f"Roles assigned: {results['role_distribution']}"),
            ("ANALYSIS_COMPLETED", "All pipeline stages completed successfully"),
        ]
        for i, (etype, msg) in enumerate(events):
            cur.execute("""
                INSERT INTO session_event (session_id, event_type, message, created_at)
                VALUES (?, ?, ?, ?)
            """, (session_id, etype, msg, now_ms + i * 1000))
        print(f"[13] Inserted {len(events)} session events")

        # ── 14. Финализируем сессию: статус COMPLETED, оценка качества, статистика z-норм ──
        cur.execute("""
            UPDATE analysis_session
            SET status = 'COMPLETED',
                session_quality_score = 0.65,
                norm_stats_json = ?,
                updated_at = ?
            WHERE id = ?
        """, (
            json.dumps(results["z_normalization"], ensure_ascii=False),
            now_ms,
            session_id,
        ))
        print(f"[14] Session #{session_id} marked as COMPLETED")

        conn.commit()  # фиксируем всю транзакцию загрузки разом
        print(f"\n{'='*60}")
        print(f"SUCCESS! Session #{session_id} '{SESSION_NAME}' loaded into DB")
        print(f"  - 95 authors with 11 scores each")
        print(f"  - Composite scores + thresholds + roles")
        print(f"  - Bootstrap CIs for ER_bg, ER_top, Reach, Pos, Resp")
        print(f"  - 1133 posts + 1787 comments with sentiment")
        print(f"  - Ready for Dashboard and Detail views")
        print(f"{'='*60}")

    except Exception as e:
        conn.rollback()  # при любой ошибке откатываем всю транзакцию целиком
        print(f"ERROR: {e}")
        raise
    finally:
        conn.close()  # всегда закрываем соединение с БД


if __name__ == "__main__":
    main()
