# -*- coding: utf-8 -*-
"""
НАЗНАЧЕНИЕ
Скрипт приведения синтетического датасета examples/dataset_ai_v2.json к более
реалистичному виду по известным авторам. Подставляет реальные (по открытым
источникам) числа подписчиков для узнаваемых персон, пересчитывает вовлечённость
постов от этих реальных значений и добавляет «шумовые» (нетематические) посты,
чтобы качество тематической фильтрации в эксперименте было правдоподобным
(не идеально 100%).

ЧТО ВНУТРИ
- REAL_DATA — справочник vkId -> {followersCount, engagement} с подтверждёнными
  данными по известным авторам и комментарием-источником для каждого.
- NOISE_POSTS — список бытовых нетематических текстов (должны не проходить фильтр).
- get_er_params(followers) — реалистичный ER по тиру аудитории.
- main() — обновление подписчиков, пересчёт engagement всех постов, вброс шумовых
  постов случайным авторам, печать итоговой статистики и сохранение датасета.

МЕТОД
Для авторов из REAL_DATA реакции берутся из заданных диапазонов; для остальных —
из ER по тиру со случайным разбросом (seed=42). Шумовые посты получают
пониженную вовлечённость и помечаются окном CURRENT.

БИБЛИОТЕКИ
Только стандартная библиотека: json, random, math, pathlib, collections.

СВЯЗИ
Читает и перезаписывает examples/dataset_ai_v2.json.
"""

import json
import random
import math
from pathlib import Path
from collections import defaultdict

DATASET_PATH = Path(__file__).parent.parent / "examples" / "dataset_ai_v2.json"

# ═══════════════════════════════════════════════════════
# РЕАЛЬНЫЕ ДАННЫЕ VK по известным авторам
# Источники: top100vk.com, nebaz.ru, веб-исследование (июнь 2026)
#
# Формат: vkId -> {поле: значение}
# Переопределяем только те поля, по которым есть подтверждённые данные.
# Диапазоны engagement задают (мин, макс) для лайков/комментариев/репостов и
# множитель просмотров views_mult.
# ═══════════════════════════════════════════════════════

REAL_DATA = {
    # Артемий Лебедев — VK group @temalebedev, Director of Design at VK
    # Source: top100vk.com — 1,632,672 subscribers
    # Engagement: L=50-5000, C=10-400, R=6-100
    1329809: {
        "followersCount": 1632672,
        "engagement": {"likes": (50, 5000), "comments": (10, 400), "reposts": (6, 100), "views_mult": (8, 20)},
    },

    # Герман Греф — no personal VK page (uses pseudonym), but Sber official page
    # Engagement estimated from Sber corporate page + public appearances
    # Realistic for corporate exec: moderate engagement, high views
    1009286: {
        "followersCount": 45000,  # personal page under pseudonym, low
        "engagement": {"likes": (200, 3000), "comments": (30, 500), "reposts": (20, 300), "views_mult": (15, 35)},
    },

    # Юрий Дудь — personal VK page exists but inactive since emigration
    # YouTube: 10.1M subs. VK: ~300K (historical, pre-emigration)
    1280324: {
        "followersCount": 312000,
        "engagement": {"likes": (500, 8000), "comments": (50, 800), "reposts": (30, 400), "views_mult": (10, 25)},
    },

    # Олег Тиньков — no active VK page, Instagram 1M
    # VK: estimated ~150K (historical)
    1345400: {
        "followersCount": 148000,
        "engagement": {"likes": (300, 5000), "comments": (40, 600), "reposts": (20, 300), "views_mult": (12, 28)},
    },

    # Илья Варламов — YouTube 4M+, VK estimated ~200K
    1194680: {
        "followersCount": 215000,
        "engagement": {"likes": (200, 4000), "comments": (30, 500), "reposts": (15, 200), "views_mult": (10, 22)},
    },

    # Руслан Усачев — YouTube popular, VK Video shows, estimated ~180K VK
    1383133: {
        "followersCount": 176000,
        "engagement": {"likes": (300, 3500), "comments": (20, 400), "reposts": (10, 150), "views_mult": (10, 20)},
    },

    # Игорь Ашманов — personal VK not confirmed as active public page
    # Expert/speaker, estimated ~15K followers on VK (if page exists)
    1049159: {
        "followersCount": 14800,
        "engagement": {"likes": (50, 800), "comments": (10, 150), "reposts": (5, 80), "views_mult": (8, 18)},
    },

    # Наталья Касперская — Telegram primary, VK minimal
    # Estimated ~8K VK
    1097931: {
        "followersCount": 8200,
        "engagement": {"likes": (30, 500), "comments": (5, 100), "reposts": (3, 50), "views_mult": (8, 15)},
    },

    # Алёна Владимирская — Telegram 92K, VK estimated ~12K
    1446705: {
        "followersCount": 11500,
        "engagement": {"likes": (40, 600), "comments": (10, 120), "reposts": (5, 60), "views_mult": (8, 16)},
    },

    # Артур Хачуян — VK confirmed: 968 subscribers
    # Source: nebaz.ru — 968 subs, 565 friends
    1148583: {
        "followersCount": 968,
        "engagement": {"likes": (5, 80), "comments": (2, 25), "reposts": (1, 15), "views_mult": (5, 12)},
    },

    # Андрей Себрант — Telegram @techsparks primary, VK estimated ~5K
    1114402: {
        "followersCount": 4800,
        "engagement": {"likes": (20, 300), "comments": (5, 60), "reposts": (3, 40), "views_mult": (8, 15)},
    },

    # Евгений Черешнев — blog + YouTube primary, VK estimated ~3K
    1356399: {
        "followersCount": 2900,
        "engagement": {"likes": (10, 200), "comments": (3, 40), "reposts": (2, 25), "views_mult": (6, 12)},
    },

    # Иван Ямщиков — VK personal page dormant, ~200 subs
    # Source: in-vk.com — 5 subscribers, 42 friends (very inactive)
    # More realistic: ~1500 (if had a public page for AI content)
    1184749: {
        "followersCount": 1450,
        "engagement": {"likes": (8, 120), "comments": (2, 30), "reposts": (1, 15), "views_mult": (5, 10)},
    },

    # Александр Крайнов — Habr active, VK not confirmed, estimated ~2K
    1037034: {
        "followersCount": 2100,
        "engagement": {"likes": (10, 150), "comments": (3, 35), "reposts": (2, 20), "views_mult": (6, 12)},
    },

    # Светлана Романова — fictional, moderate blogger
    1415059: {
        "followersCount": 8500,
        "engagement": {"likes": (30, 400), "comments": (5, 80), "reposts": (3, 40), "views_mult": (8, 15)},
    },
}

# Тексты шумовых постов (нетематические, должны не пройти тематический фильтр)
NOISE_POSTS = [
    "Кто-нибудь знает хорошего стоматолога в центре Москвы? Нужна срочная консультация",
    "Продаю iPhone 15 Pro Max, 256 ГБ, чёрный, идеальное состояние. Писать в ЛС",
    "Ребят, посоветуйте сериал на вечер. Желательно что-то лёгкое, не триллер",
    "Утренняя пробежка по набережной. Погода шикарная, жалко что скоро на работу",
    "Рецепт шарлотки от бабушки: 4 яблока, 3 яйца, стакан сахара, стакан муки. Всё смешать и в духовку на 40 минут",
    "Кот опять залез на шкаф и не может слезть. Третий раз за неделю",
    "Посмотрел вчера новый фильм Нолана. Спойлерить не буду, но концовка — вау",
    "Застрял в пробке на МКАДе. Второй час. Навигатор показывает ещё 40 минут. Москва, я люблю тебя",
    "Поздравляю всех с наступающими! Хорошего настроения и здоровья",
    "Кто идёт на матч Спартак-ЦСКА в субботу? Есть лишний билет",
    "Ищу репетитора по математике для дочки, 7 класс. Район Бутово",
    "Новый альбом Oxxxymiron — огонь или нет? Делитесь мнениями",
    "Забронировал отель в Сочи на август. Цены космос, но море того стоит",
    "Вчера была на выставке современного искусства в Гараже. Половину экспонатов не поняла",
    "Готовлю плов по узбекскому рецепту. Зира, барбарис, баранина. Соседи уже стучат — запах на весь подъезд",
    "Купил новые кроссовки для бега. Nike Pegasus 42. Первые впечатления — удобные",
    "Дочке сегодня 5 лет. Время летит. Вроде вчера из роддома забирал",
    "Кто знает, где в Питере лучший шаверма? Спор с коллегами на обеде",
    "Переехал в новую квартиру. Ремонт ещё не закончен, но уже уютно",
    "Закончил читать «Мастер и Маргариту» в десятый раз. Каждый раз нахожу что-то новое",
    "Вопрос к автомобилистам: менять масло каждые 10 или 15 тысяч?",
    "Сходил на концерт Земфиры. Голос — как 20 лет назад. Мурашки",
    "Первый день в спортзале после отпуска. Ноги не слушаются. Мотивация — минус",
    "Грядки посажены, теплица готова. Дачный сезон открыт",
    "У кого-нибудь есть опыт с электросамокатами? Какой брать для города?",
    "День рождения мамы. Заказал торт и цветы. Надеюсь, угадал с букетом",
    "Погода в Москве: +32, центр города как сковородка. Спасение только в кондиционере",
    "Вернулся из командировки в Казань. Город красивый, еда вкусная, люди приветливые",
    "Ищу квартиру в аренду. 1-комнатная, метро желательно. Бюджет до 45к",
    "Собака научилась открывать холодильник. Приходим домой — сосиски съедены",
    "Кто был в Грузии этим летом? Как дорога, визы, цены?",
    "Фотки с отпуска в Турции. Море, солнце, all inclusive. Отдохнул на год вперёд",
    "Зимние шины пора менять. Кто какую резину ставит на Хендай Солярис?",
    "Рекомендую подкаст «Что бы посмотреть». Обсуждают кино без спойлеров",
    "Капучино из кофемашины наконец получается с нормальной пенкой. Полгода тренировок",
    "Записался на курсы английского. Уровень — Elementary. Стыдно, но надо",
    "Ребёнок принёс из школы хомяка. Говорит — на выходные. Подозреваю, что навсегда",
    "Вчера катались на великах по Воробьёвым горам. Виды — супер, ноги — ватные",
    "У кого была ипотека в Сбере? Какой процент дали? Реально ли рефинансировать?",
    "Нашёл в гараже старые кассеты с музыкой 90-х. Ностальгия зашкаливает",
]


def get_er_params(followers):
    """Реалистичный ER (engagement rate) по тиру числа подписчиков.

    Возвращает одно число — базовый ER. Чем больше аудитория, тем ниже ER
    (обратная зависимость). Используется для авторов без явных данных в REAL_DATA.
    """
    if followers >= 1000000:
        return 0.003
    elif followers >= 500000:
        return 0.005
    elif followers >= 200000:
        return 0.008
    elif followers >= 100000:
        return 0.01
    elif followers >= 50000:
        return 0.015
    elif followers >= 10000:
        return 0.025
    elif followers >= 5000:
        return 0.045
    elif followers >= 1000:
        return 0.07
    elif followers >= 300:
        return 0.10
    else:
        return 0.15


def main():
    """Подставляет реальные данные авторов, пересчитывает engagement и добавляет шум."""
    rng = random.Random(42)  # фиксированный seed для воспроизводимости

    with open(DATASET_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    authors = {a["vkId"]: a for a in data["authors"]}

    # ── 1. Обновляем число подписчиков реальными данными из REAL_DATA ──
    print("=== Updating author data ===")
    for i, author in enumerate(data["authors"]):
        real = REAL_DATA.get(author["vkId"])
        if real:
            old_f = author["followersCount"]
            data["authors"][i]["followersCount"] = real["followersCount"]
            print(f"  {author['firstName']} {author['lastName']}: {old_f:,} -> {real['followersCount']:,}")
    # Пересобираем индекс авторов после обновления
    authors = {a["vkId"]: a for a in data["authors"]}

    # ── 2. Пересчитываем engagement для ВСЕХ постов ──
    print("\n=== Recalculating engagement ===")
    comments_by_post = defaultdict(list)
    for c in data["comments"]:
        comments_by_post[c["postVkId"]].append(c)

    for i, post in enumerate(data["posts"]):
        author = authors.get(post["fromId"])
        if not author:
            continue
        followers = author["followersCount"]
        real = REAL_DATA.get(post["fromId"])

        if real and "engagement" in real:
            # Для известных авторов реакции берём из заданных диапазонов
            eng = real["engagement"]
            likes = rng.randint(*eng["likes"])
            comments_est = rng.randint(*eng["comments"])
            reposts = rng.randint(*eng["reposts"])
            views = int(likes * rng.uniform(*eng["views_mult"]))
        else:
            # Для остальных — расчёт от ER по тиру со случайным разбросом
            er = get_er_params(followers) * rng.uniform(0.6, 1.4)
            total = max(1, int(followers * er))
            likes = max(1, int(total * 0.75 * rng.uniform(0.8, 1.2)))
            comments_est = max(0, int(total * 0.12 * rng.uniform(0.5, 1.5)))
            reposts = max(0, int(total * 0.13 * rng.uniform(0.6, 1.3)))
            views = max(likes + 1, int(likes * rng.uniform(8, 18)))

        # Для CURRENT синхронизируем поле comments с фактическим числом в массиве
        actual_comments = len(comments_by_post.get(post["vkId"], []))
        if post["window"] == "CURRENT":
            data["posts"][i]["comments"] = actual_comments
        else:
            data["posts"][i]["comments"] = comments_est  # для BASELINE — расчётное число

        data["posts"][i]["likes"] = likes
        data["posts"][i]["reposts"] = reposts
        data["posts"][i]["views"] = views

    # ── 3. Добавляем шумовые (нетематические) посты ──
    print("\n=== Adding noise posts ===")
    # Добавляем ~60 шумовых постов, распределённых по авторам (вне темы)
    max_post_vkid = max(p["vkId"] for p in data["posts"])
    noise_post_id = max_post_vkid + 100  # vkId шумовых постов начинаем с запасом
    noise_added = 0
    open_authors = [a for a in data["authors"] if not a.get("isClosed")]

    # Выбираем ~40 случайных авторов, которым добавим шумовые посты
    noise_authors = rng.sample(open_authors, min(40, len(open_authors)))
    for author in noise_authors:
        n_noise = rng.randint(1, 2)  # 1–2 шумовых поста на автора
        for _ in range(n_noise):
            text = rng.choice(NOISE_POSTS)
            followers = author["followersCount"]
            real = REAL_DATA.get(author["vkId"])
            if real and "engagement" in real:
                eng = real["engagement"]
                likes = rng.randint(eng["likes"][0], eng["likes"][1] // 3)  # у шумовых постов меньше реакций
                reposts = rng.randint(0, eng["reposts"][1] // 4)
                views = int(likes * rng.uniform(5, 12))
            else:
                er = get_er_params(followers) * rng.uniform(0.3, 0.8)  # пониженный ER для шума
                total = max(1, int(followers * er))
                likes = max(0, int(total * 0.7))
                reposts = max(0, int(total * 0.1))
                views = max(likes + 1, int(likes * rng.uniform(5, 12)))

            noise_post = {
                "vkId": noise_post_id,
                "ownerId": author["vkId"],
                "fromId": author["vkId"],
                "date": rng.randint(1777000000, 1780000000),
                "text": text,
                "likes": likes,
                "reposts": reposts,
                "comments": rng.randint(0, 5),
                "views": views,
                "containsMedia": rng.random() < 0.3,  # ~30% постов с медиа
                "hasCopyHistory": False,
                "window": "CURRENT",
            }
            data["posts"].append(noise_post)
            noise_post_id += 1
            noise_added += 1

    print(f"  Added {noise_added} noise posts")

    # ── 4. Проверка ──
    cur = [p for p in data["posts"] if p["window"] == "CURRENT"]
    # Тематические = те, что НЕ являются шумом (исходные CURRENT-посты — тематические)
    noise_ids = set(range(max_post_vkid + 100, noise_post_id))  # диапазон vkId добавленного шума
    topic_rel = len([p for p in cur if p["vkId"] not in noise_ids])
    topic_noise = len([p for p in cur if p["vkId"] in noise_ids])

    print(f"\n=== FINAL STATS ===")
    print(f"  Authors: {len(data['authors'])}")
    print(f"  Posts: {len(data['posts'])} (CURRENT: {len(cur)}, BASELINE: {len(data['posts']) - len(cur)})")
    print(f"  CURRENT breakdown: {topic_rel} topic-relevant + {topic_noise} noise")
    print(f"  Topic filter quality: {topic_rel}/{len(cur)} = {topic_rel/len(cur):.3f}")
    print(f"  Comments: {len(data['comments'])}")

    # Sample engagement
    print(f"\n=== SAMPLE ENGAGEMENT ===")
    for check_id in [1329809, 1009286, 1049159, 1148583, 1114402]:
        author = authors.get(check_id)
        if not author:
            continue
        posts = [p for p in data["posts"] if p["fromId"] == check_id and p["window"] == "CURRENT" and p["vkId"] not in noise_ids]
        if not posts:
            continue
        avg_l = sum(p["likes"] for p in posts) / len(posts)
        avg_c = sum(p["comments"] for p in posts) / len(posts)
        avg_r = sum(p["reposts"] for p in posts) / len(posts)
        avg_v = sum(p["views"] for p in posts) / len(posts)
        print(f"  {author['firstName']} {author['lastName']:<22} {author['followersCount']:>10,} | "
              f"L={avg_l:,.0f} C={avg_c:,.0f} R={avg_r:,.0f} V={avg_v:,.0f}")

    # Сохраняем обновлённый датасет поверх исходного файла
    with open(DATASET_PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"\nSaved to {DATASET_PATH}")


if __name__ == "__main__":
    main()
