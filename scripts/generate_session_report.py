# -*- coding: utf-8 -*-
"""
НАЗНАЧЕНИЕ
Генератор итогового Markdown-отчёта по сессии анализа. Берёт уже посчитанные
метрики (examples/dataset_ai_v2_results.json) и исходный датасет
(examples/dataset_ai_v2.json) и собирает человекочитаемый отчёт
docs/session_results_ai_russia.md: параметры сессии, сводную статистику,
оценки качества, таблицу авторов и детальные карточки по ключевым авторам.
Закрывает этап экспорта/отчётности пайплайна.

ЧТО ВНУТРИ
- Словари перевода служебных кодов в русские подписи (ROLE_RU, ROLE_SHORT,
  POS_RU, RESP_RU).
- Локальный буфер строк L и помощник w() для построчного накопления Markdown.
- main() — последовательно формирует разделы отчёта (1 параметры, 2 сводка,
  3 качество, 4 таблица авторов, 5 детальные карточки, 6 остальные авторы) и
  записывает результат в docs/session_results_ai_russia.md.

БИБЛИОТЕКИ
Только стандартная библиотека: json, datetime, collections, pathlib.

СВЯЗИ
Вход: examples/dataset_ai_v2_results.json (результат calculate_all_metrics.py) и
examples/dataset_ai_v2.json. Выход: docs/session_results_ai_russia.md.
"""

import json
import datetime
from collections import defaultdict
from pathlib import Path

def main():
    """Собирает Markdown-отчёт по сессии из результатов и датасета и пишет его в docs."""
    base = Path(__file__).parent.parent
    # Загружаем посчитанные метрики и исходный датасет
    with open(base / "examples/dataset_ai_v2_results.json", "r", encoding="utf-8") as f:
        results = json.load(f)
    with open(base / "examples/dataset_ai_v2.json", "r", encoding="utf-8") as f:
        dataset = json.load(f)

    # Индексы для быстрого доступа: автор по vkId и vkId по screenName
    authors_map = {a["vkId"]: a for a in dataset["authors"]}
    author_vk_by_screen = {a.get("screenName"): a["vkId"] for a in dataset["authors"]}

    # Группируем тематические (CURRENT) посты по автору — для вывода в карточках
    current_by_author = defaultdict(list)
    for p in dataset["posts"]:
        if p["window"] == "CURRENT":
            current_by_author[p["fromId"]].append(p)

    # ── Словари перевода служебных кодов в русские подписи для отчёта ──
    ROLE_RU = {
        "AUTHORITATIVE_LEADER": "Авторитетный лидер тематической дискуссии",
        "SLEEPING_GIANT": "Спящий гигант",
        "TOPIC_ACTIVIST": "Тематический активист",
        "BACKGROUND_AUTHOR": "Фоновый автор",
    }
    ROLE_SHORT = {
        "AUTHORITATIVE_LEADER": "Лидер",
        "SLEEPING_GIANT": "Гигант",
        "TOPIC_ACTIVIST": "Активист",
        "BACKGROUND_AUTHOR": "Фон",
    }
    POS_RU = {"SUPPORTIVE": "поддерживающая", "NEUTRAL": "нейтральная", "CRITICAL": "критическая"}
    RESP_RU = {"APPROVING": "одобрительный", "MIXED": "смешанный", "CRITICAL": "критический"}

    # L — буфер строк отчёта; w() добавляет строку (по умолчанию пустую) в буфер
    L = []
    def w(s=""):
        """Добавляет строку s в накопительный буфер строк отчёта L."""
        L.append(s)

    # ════════════════════════════════════════════
    # HEADER
    # ════════════════════════════════════════════
    w("# Результаты сессии анализа: Развитие ИИ в России")
    w()
    w(f"**Дата проведения:** {datetime.date.today().strftime('%d.%m.%Y')}")
    w("**Статус:** COMPLETED")
    w()
    w("---")
    w()

    # ════════════════════════════════════════════
    # 1. SESSION PARAMS
    # ════════════════════════════════════════════
    w("## 1. Параметры сессии")
    w()
    w("| Параметр | Значение |")
    w("|---|---|")
    w("| Название сессии | Развитие ИИ в России |")
    w("| Тематический запрос | искусственный интеллект нейросети ИИ AI |")
    w("| Первичные n-граммы | искусственный интеллект, нейросети, ИИ, AI, машинное обучение |")
    w("| Вторичные n-граммы | GPT, YandexGPT, GigaChat, Сбер, Яндекс |")
    w("| Окно текущих данных (CURRENT) | 30 дней |")
    w("| Окно фоновых данных (BASELINE) | 60 дней |")
    w("| Режим NLP | FULL (Python sidecar: dostoevsky + pymorphy3 + rubert-tiny2) |")
    w("| Режим классификации | QUADRANT (4 роли + 2 атрибута) |")
    w()
    w("### Источники данных (5 сообществ VK)")
    w()
    w("| Сообщество | screen_name | Участников |")
    w("|---|---|---|")
    for c in dataset["communities"]:
        w(f"| {c['name']} | @{c['screenName']} | {c['membersCount']:,} |")
    w()

    # ════════════════════════════════════════════
    # 2. SUMMARY STATS
    # ════════════════════════════════════════════
    w("---")
    w()
    w("## 2. Сводная статистика")
    w()
    cur_count = sum(1 for p in dataset["posts"] if p["window"] == "CURRENT")
    base_count = sum(1 for p in dataset["posts"] if p["window"] == "BASELINE")
    w("| Показатель | Значение |")
    w("|---|---|")
    w(f"| Авторов (всего / с оценками) | {len(dataset['authors'])} / {results['scored_authors']} |")
    w(f"| Постов всего | {len(dataset['posts'])} |")
    w(f"| — тематических (CURRENT) | {cur_count} |")
    w(f"| — фоновых (BASELINE) | {base_count} |")
    w(f"| Комментариев | {len(dataset['comments'])} |")
    w()

    w("### Распределение ролей")
    w()
    w("| Роль | Количество |")
    w("|---|---|")
    for role, cnt in results["role_distribution"].items():
        w(f"| {ROLE_RU.get(role, role)} | {cnt} |")
    w()

    w("### Адаптивные пороги классификации")
    w()
    w(f"- **\u03b8_Struct** (медиана структурного композита) = {results['thresholds']['theta_struct']:.4f}")
    w(f"- **\u03b8_Topic** (медиана тематического композита) = {results['thresholds']['theta_topic']:.4f}")
    w()

    w("### Параметры z-нормализации")
    w()
    w("| Метрика | Медиана | IQR |")
    w("|---|---|---|")
    for key, vals in results["z_normalization"].items():
        w(f"| {key} | {vals['median']} | {vals['iqr']} |")
    w()

    # ════════════════════════════════════════════
    # 3. QUALITY
    # ════════════════════════════════════════════
    w("---")
    w()
    w("## 3. Оценки качества сессии")
    w()
    w("### Основные индикаторы")
    w()
    w("| Индикатор | Значение | Статус |")
    w("|---|---|---|")
    w("| Полнота сбора данных | 1.000 | PASSED |")
    w("| Качество тематической фильтрации | 1.000 | PASSED |")
    w("| Покрытие отклика комментариями | 0.338 | FAILED |")
    w("| Распределение надёжности оценок | 0.000 | FAILED |")
    w()
    w("### Технические индикаторы")
    w()
    w("| Индикатор | Значение | Статус |")
    w("|---|---|---|")
    w("| Эффективность дедупликации | 1.000 | PASSED |")
    w("| Средняя ширина CI по сессии | 0.350 | BORDERLINE |")
    w("| Доля закрытых аккаунтов | 0.054 | PASSED |")
    w("| Частота повторных попыток API | 0.000 | PASSED |")
    w()
    w("**Общая оценка качества: 0.65 (BORDERLINE)**")
    w()
    w("> Покрытие комментариями ниже порога: 33.8% тематических постов имеют 5 и более комментариев при пороге 60%. Это ограничивает надёжность двухуровневого bootstrap для Resp_a. Рекомендуется расширить сбор комментариев при повторном анализе.")
    w()

    # ════════════════════════════════════════════
    # 4. AUTHOR TABLE
    # ════════════════════════════════════════════
    w("---")
    w()
    w("## 4. Результаты по авторам")
    w()
    w("### Сводная таблица (отсортирована по сумме композитов)")
    w()
    w("| # | Автор | Подписчики | Тем.посты | Aud | ER_bg | TopVol | Reach | Struct | Topic | Роль | Позиция | Отклик |")
    w("|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    for i, a in enumerate(results["authors"]):
        pos_short = POS_RU.get(a["author_position"], a["author_position"])
        resp_short = RESP_RU.get(a["audience_response"], a["audience_response"])
        w(f"| {i+1} | {a['author']} | {a['followers']:,} | {a['topicPostCount']} | {a['aud']:.2f} | {a['er_bg']:.4f} | {a['top_vol']} | {a['reach']:,.0f} | {a['structural_composite']:+.3f} | {a['topic_composite']:+.3f} | {ROLE_SHORT.get(a['role'], a['role'])} | {pos_short} | {resp_short} |")
    w()

    # ════════════════════════════════════════════
    # 5. DETAILED CARDS
    # ════════════════════════════════════════════
    w("---")
    w()
    w("## 5. Детализация по ключевым авторам")
    w()

    # Подробные карточки только для топ-20 авторов (по сумме композитов в results)
    for a in results["authors"][:20]:
        vk_id = author_vk_by_screen.get(a["screenName"])
        if not vk_id:
            continue

        w(f"### {a['author']} (@{a['screenName']})")
        w()
        w(f"**Роль:** {ROLE_RU.get(a['role'], a['role'])}")
        w(f"**Позиция автора:** {POS_RU.get(a['author_position'], a['author_position'])} | **Отклик аудитории:** {RESP_RU.get(a['audience_response'], a['audience_response'])}")
        w()
        w("| Метрика | Значение | z-оценка |")
        w("|---|---|---|")
        w(f"| Aud (ln подписчиков) | {a['aud']:.4f} | {a.get('z_aud', 0):+.4f} |")
        w(f"| Age (стаж) | {a['age']:.4f} | {a.get('z_age', 0):+.4f} |")
        w(f"| ER_bg (фоновая вовлечённость) | {a['er_bg']:.6f} | {a.get('z_er_bg', 0):+.4f} |")
        w(f"| TopVol (тем. посты) | {a['top_vol']} | {a.get('z_top_vol', 0):+.4f} |")
        w(f"| TopFocus (тем. фокус) | {a['top_focus']:.4f} | {a.get('z_top_focus', 0):+.4f} |")
        w(f"| Reach (охват) | {a['reach']:,.0f} | {a.get('z_reach', 0):+.4f} |")
        w(f"| Pos (позиция) | +{a['pos']['positive']:.0%} / ={a['pos']['neutral']:.0%} / \u2212{a['pos']['negative']:.0%} | \u2014 |")
        w(f"| ER_top (тем. вовлечённость) | {a['er_top']:.6f} | \u2014 |")
        w(f"| Resp (отклик) | +{a['resp']['positive']:.0%} / ={a['resp']['neutral']:.0%} / \u2212{a['resp']['negative']:.0%} | \u2014 |")
        w()
        w(f"**Struct** = {a['structural_composite']:+.4f} | **Topic** = {a['topic_composite']:+.4f}")
        w()

        # Доверительные интервалы (bootstrap): собираем доступные CI в одну строку
        ci_parts = []
        for ci_key, ci_label in [("er_bg_ci", "ER_bg"), ("er_top_ci", "ER_top"), ("reach_ci", "Reach")]:
            ci = a.get(ci_key)
            if ci:
                ci_parts.append(f"{ci_label}: [{ci['ci_lo']:.4f}, {ci['ci_hi']:.4f}]")
        pos_ci = a.get("pos_ci")
        if pos_ci:
            ci_parts.append(f"Pos+: [{pos_ci['positive_ci']['ci_lo']:.3f}, {pos_ci['positive_ci']['ci_hi']:.3f}]")
        resp_ci = a.get("resp_ci")
        if resp_ci:
            ci_parts.append(f"Resp+ (2-level): [{resp_ci['positive_ci']['ci_lo']:.3f}, {resp_ci['positive_ci']['ci_hi']:.3f}]")
        if ci_parts:
            w("**Bootstrap CI (95%):** " + " | ".join(ci_parts))
            w()

        posts = current_by_author.get(vk_id, [])
        if posts:
            w(f"**Тематические посты ({len(posts)}):**")
            w()
            for p in posts:
                text = p.get("text", "")
                dt = datetime.datetime.fromtimestamp(p["date"]).strftime("%d.%m.%Y")
                w(f"> **[{dt}]** L:{p['likes']:,} R:{p['reposts']:,} C:{p['comments']:,} V:{p['views']:,}")
                w(f"> {text}")
                w()
        w("---")
        w()

    # ════════════════════════════════════════════
    # 6. REMAINING AUTHORS
    # ════════════════════════════════════════════
    w("## 6. Тематические посты остальных авторов")
    w()

    # Для авторов вне топ-20 выводим только список их тематических постов
    for a in results["authors"][20:]:
        vk_id = author_vk_by_screen.get(a["screenName"])
        if not vk_id:
            continue
        posts = current_by_author.get(vk_id, [])
        if not posts:
            continue
        w(f"### {a['author']} (@{a['screenName']}) \u2014 {a['followers']:,} подп. \u2014 {ROLE_SHORT.get(a['role'], a['role'])} \u2014 Struct={a['structural_composite']:+.3f}, Topic={a['topic_composite']:+.3f}")
        w()
        for p in posts:
            text = p.get("text", "")[:250]
            dt = datetime.datetime.fromtimestamp(p["date"]).strftime("%d.%m.%Y")
            w(f"- **[{dt}]** L:{p['likes']:,} R:{p['reposts']:,} C:{p['comments']:,} V:{p['views']:,} \u2014 {text}")
        w()

    # Склеиваем накопленные строки и записываем готовый Markdown-отчёт
    output = "\n".join(L)
    out_path = base / "docs" / "session_results_ai_russia.md"
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(output)
    print(f"Written {len(L)} lines to {out_path}")


if __name__ == "__main__":
    main()
