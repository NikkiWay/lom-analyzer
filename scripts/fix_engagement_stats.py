# -*- coding: utf-8 -*-
"""
НАЗНАЧЕНИЕ
Скрипт-«фиксер» статистики вовлечённости в синтетическом датасете
examples/dataset_ai_v2.json. Делает показатели реакций правдоподобными: заново
рассчитывает лайки, комментарии, репосты и просмотры для всех постов, исходя из
числа подписчиков автора и реалистичных уровней ER (engagement rate) по тирам.
Дополнительно синхронизирует поле post.comments с фактическим числом
комментариев в массиве comments и добавляет недостающие комментарии к
популярным CURRENT-постам.

ЧТО ВНУТРИ
- get_er_params(followers) — таблица параметров ER по тирам аудитории.
- main() — пересчёт engagement всех постов, синхронизация счётчика комментариев,
  генерация недостающих комментариев из шаблонов, печать проверочной статистики
  и сохранение обновлённого датасета на место.

МЕТОД
ER задаётся ступенчато по числу подписчиков; на каждый пост добавляется
случайный разброс (фиксированный seed=42 для воспроизводимости). Реакции
делятся на лайки/комментарии/репосты по долям тира, просмотры = лайки × множитель.

БИБЛИОТЕКИ
Только стандартная библиотека: json, random, math, pathlib, collections.

СВЯЗИ
Читает и перезаписывает examples/dataset_ai_v2.json (вход и выход совпадают).
"""

import json
import random
import math
from pathlib import Path
from collections import defaultdict

DATASET_PATH = Path(__file__).parent.parent / "examples" / "dataset_ai_v2.json"


def get_er_params(followers):
    """Возвращает параметры вовлечённости для тира по числу подписчиков.

    Кортеж: (базовый ER, доля лайков, доля комментариев, доля репостов,
    диапазон множителя просмотров). У мелких аккаунтов ER выше, у крупных — ниже
    (типичная обратная зависимость ER от размера аудитории во ВКонтакте).
    """
    if followers >= 400000:
        return 0.006, 0.76, 0.10, 0.14, (18, 30)
    elif followers >= 100000:
        return 0.009, 0.75, 0.12, 0.13, (15, 25)
    elif followers >= 50000:
        return 0.014, 0.74, 0.12, 0.14, (14, 22)
    elif followers >= 10000:
        return 0.025, 0.72, 0.13, 0.15, (12, 18)
    elif followers >= 5000:
        return 0.045, 0.70, 0.15, 0.15, (10, 16)
    elif followers >= 1000:
        return 0.07, 0.68, 0.16, 0.16, (8, 14)
    elif followers >= 300:
        return 0.10, 0.65, 0.20, 0.15, (5, 10)
    else:
        return 0.15, 0.60, 0.22, 0.18, (4, 8)


def main():
    """Пересчитывает engagement всех постов и досоздаёт комментарии, сохраняя датасет."""
    rng = random.Random(42)  # фиксированный seed — воспроизводимый результат

    with open(DATASET_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    authors = {a["vkId"]: a for a in data["authors"]}

    # Индексируем комментарии по vkId поста для подсчёта фактического числа
    comments_by_post = defaultdict(list)
    for c in data["comments"]:
        comments_by_post[c["postVkId"]].append(c)

    # ── Пересчёт вовлечённости для ВСЕХ постов ──
    for i, post in enumerate(data["posts"]):
        author = authors.get(post["fromId"])
        if not author:
            continue
        followers = author["followersCount"]
        if followers <= 0:
            followers = 50  # защита от нуля: минимальная условная аудитория

        base_er, like_pct, comment_pct, repost_pct, view_range = get_er_params(followers)

        # Случайный разброс ER в пределах ±40% на каждый пост
        er = base_er * rng.uniform(0.6, 1.4)

        # Общее число реакций = подписчики × ER (не меньше 1)
        total_reactions = max(1, int(followers * er))

        # Делим общее число реакций на лайки/комментарии/репосты по долям тира (с разбросом)
        likes = max(1, int(total_reactions * like_pct * rng.uniform(0.8, 1.2)))
        comment_count_expected = max(0, int(total_reactions * comment_pct * rng.uniform(0.5, 1.5)))
        reposts = max(0, int(total_reactions * repost_pct * rng.uniform(0.6, 1.3)))

        # Просмотры = лайки × случайный множитель из диапазона тира
        view_mult = rng.uniform(*view_range)
        views = max(likes + 1, int(likes * view_mult))

        # Фактическое число собранных комментариев для этого поста
        actual_comments = len(comments_by_post.get(post["vkId"], []))

        if post["window"] == "CURRENT":
            # Для CURRENT поле comments = реально собранное число комментариев
            data["posts"][i]["comments"] = actual_comments
        else:
            # Для BASELINE комментарии не собирались — ставим расчётное ожидаемое число
            data["posts"][i]["comments"] = comment_count_expected

        data["posts"][i]["likes"] = likes
        data["posts"][i]["reposts"] = reposts
        data["posts"][i]["views"] = views

    # ── Досоздаём комментарии постам, где их слишком мало ──
    # Цель: набрать осмысленное число комментариев под популярными CURRENT-постами,
    # чтобы хватало данных на расчёт Resp и двухуровневый bootstrap.
    comment_templates_positive = [
        "Согласен, полезная штука",
        "Интересный опыт, спасибо",
        "Мы тоже внедряли, результат хороший",
        "Наконец-то кто-то разумно пишет",
        "Подтверждаю, сам пробовал",
        "Полезно, сохранил",
        "Да, прогресс заметен",
        "Отличная мысль",
        "Взял на заметку",
        "Спасибо за пост, актуальная тема",
        "Поддерживаю, сам так думаю",
        "Хорошо сказано",
        "Вот бы ещё примеры",
        "Очень кстати, как раз изучаю тему",
        "Точно подмечено",
    ]
    comment_templates_neutral = [
        "Спорный вопрос, но есть над чем подумать",
        "Время покажет",
        "Не уверен, что всё так однозначно",
        "Интересно, но нужно больше данных",
        "Надо смотреть на конкретные кейсы",
        "Тут много нюансов",
        "Посмотрим через год",
        "Сложная тема",
        "А есть ссылка на исследование?",
        "Кто-нибудь пробовал в реальном бизнесе?",
        "Хотелось бы увидеть цифры",
        "Ну такое, двоякое впечатление",
        "Надо подумать",
        "А какой промпт использовали?",
        "Зависит от задачи",
    ]
    comment_templates_negative = [
        "Это маркетинг, а не прорыв",
        "Пользовался, не впечатлило",
        "Переоцениваете возможности",
        "Звучит красиво, но на практике не работает",
        "Пока это игрушка",
        "Пузырь рано или поздно лопнет",
        "Слишком оптимистично",
        "А кто за ошибки отвечает?",
        "Очередной хайп",
        "Людей увольняют, а вы радуетесь",
    ]

    # Смешанный пул шаблонов с перекосом в позитив/нейтрал (умножение = вес доли тональности)
    all_templates = comment_templates_positive * 3 + comment_templates_neutral * 3 + comment_templates_negative * 1

    # Список возможных авторов комментариев (только открытые аккаунты) — для разнообразия
    all_author_ids = [a["vkId"] for a in data["authors"] if not a.get("isClosed")]

    # Новым комментариям присваиваем vkId, продолжая нумерацию от текущего максимума
    max_comment_vk_id = max(c["vkId"] for c in data["comments"]) if data["comments"] else 60000
    new_comment_id = max_comment_vk_id + 1

    added_comments = 0
    for post in data["posts"]:
        if post["window"] != "CURRENT":
            continue

        actual = len(comments_by_post.get(post["vkId"], []))
        followers = authors.get(post["fromId"], {}).get("followersCount", 100)

        # Целевое число комментариев зависит от популярности поста (по числу лайков)
        if post["likes"] > 500:
            target = rng.randint(8, 15)
        elif post["likes"] > 200:
            target = rng.randint(5, 10)
        elif post["likes"] > 50:
            target = rng.randint(3, 7)
        else:
            target = rng.randint(1, 4)

        need = target - actual  # сколько комментариев не хватает до цели
        if need <= 0:
            continue

        for _ in range(need):
            text = rng.choice(all_templates)        # случайный шаблон комментария
            commenter = rng.choice(all_author_ids)  # случайный автор комментария
            new_comment = {
                "vkId": new_comment_id,
                "postVkId": post["vkId"],
                "postOwnerId": post["ownerId"],
                "fromId": commenter,
                "date": post["date"] + rng.randint(600, 86400),  # позже поста на 10 мин..1 сутки
                "text": text,
                "likes": rng.randint(0, max(1, post["likes"] // 50)),  # лайки комментария — доля от лайков поста
            }
            data["comments"].append(new_comment)
            comments_by_post[post["vkId"]].append(new_comment)  # держим индекс в актуальном состоянии
            new_comment_id += 1
            added_comments += 1

        # Обновляем счётчик comments у поста на новое фактическое число
        for i, p in enumerate(data["posts"]):
            if p["vkId"] == post["vkId"]:
                data["posts"][i]["comments"] = len(comments_by_post[post["vkId"]])
                break

    # ── Проверочная статистика ──
    print("=== ENGAGEMENT STATS ===")
    # Выборка крупных авторов для контроля правдоподобности чисел
    for check_id in [1009286, 1049159, 1329809, 1148583, 1114402]:
        author = authors.get(check_id)
        if not author:
            continue
        name = f"{author['firstName']} {author['lastName']}"
        posts = [p for p in data["posts"] if p["fromId"] == check_id and p["window"] == "CURRENT"]
        if not posts:
            continue
        avg_l = sum(p["likes"] for p in posts) / len(posts)
        avg_c = sum(p["comments"] for p in posts) / len(posts)
        avg_r = sum(p["reposts"] for p in posts) / len(posts)
        avg_v = sum(p["views"] for p in posts) / len(posts)
        # Итоговый ER в процентах = средние реакции / подписчики (для контроля правдоподобности)
        er = (avg_l + avg_c + avg_r) / author["followersCount"] * 100
        print(f"  {name:<25} {author['followersCount']:>10,} followers | "
              f"avg L={avg_l:,.0f} C={avg_c:,.0f} R={avg_r:,.0f} V={avg_v:,.0f} | ER={er:.2f}%")

    # Small author sample
    small = [a for a in data["authors"] if 200 < a["followersCount"] < 1000 and not a.get("isClosed")]
    if small:
        s = small[0]
        posts = [p for p in data["posts"] if p["fromId"] == s["vkId"] and p["window"] == "CURRENT"]
        if posts:
            avg_l = sum(p["likes"] for p in posts) / len(posts)
            avg_c = sum(p["comments"] for p in posts) / len(posts)
            avg_v = sum(p["views"] for p in posts) / len(posts)
            print(f"  {s['firstName']} {s['lastName']:<20} {s['followersCount']:>10,} followers | "
                  f"avg L={avg_l:,.0f} C={avg_c:,.0f} V={avg_v:,.0f}")

    # Comment coverage
    current_posts = [p for p in data["posts"] if p["window"] == "CURRENT"]
    with_5plus = sum(1 for p in current_posts if p["comments"] >= 5)
    print(f"\n  Комментарии: {len(data['comments'])} (добавлено {added_comments})")
    print(f"  Постов с >=5 комментариев: {with_5plus}/{len(current_posts)} ({with_5plus/len(current_posts)*100:.1f}%)")

    # Сохраняем обновлённый датасет поверх исходного файла
    with open(DATASET_PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"\nSaved to {DATASET_PATH}")


if __name__ == "__main__":
    main()
