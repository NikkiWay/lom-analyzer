import json, random

with open('examples/ai_dataset.json', encoding='utf-8') as f:
    data = json.load(f)

existing_ids = {p['vkId'] for p in data['posts']}
next_id = max(existing_ids) + 1

baseline_start = 1774569600
baseline_end   = 1777161600
current_start  = 1777161600
current_end    = 1779753600

random.seed(42)

expert_baseline_texts = [
    "Провели внутренний хакатон по ИИ. 200 команд, 48 часов. Лучшее решение — автоматизация проверки кредитных заявок.",
    "ИИ должен быть прозрачным. Если алгоритм принимает решение о кредите, вы имеете право знать почему.",
    "Каждый месяц новая модель. Но качество не растёт линейно. Мы входим в фазу diminishing returns.",
    "Подготовили отчёт по внедрению ИИ в госсектор. Автоматизация документооборота экономит 40% времени.",
    "Тестирую GigaChat для юридических документов. Ошибки есть, но с каждым обновлением их меньше.",
    "На конференции AAAI представили chain-of-thought с верификацией. Точность на математике выросла до 95%.",
    "Запустили пилот: ИИ-ассистент для скорой помощи. За месяц ноль критических ошибок. Масштабируем.",
    "Проблема bias в ИИ — это социальная проблема. Модель учится на данных, данные отражают предубеждения.",
    "Энергопотребление дата-центров для ИИ удвоилось за год. Нужны энергоэффективные архитектуры.",
    "Статья о трансформерах для медицинских изображений. Точность обнаружения опухолей 97.3%.",
    "Вебинар: как малому бизнесу использовать ИИ без бюджета. 3000 регистраций за день.",
    "Разрабатываем стандарт качества ИИ-систем в России. Обсуждение открыто для экспертов.",
    "Нейросети в сельском хозяйстве: определение состояния посевов по спутниковым снимкам.",
    "Обучили модель для автоматической модерации на 500K текстах. Точность 93%. Open source.",
    "ИИ в логистике: оптимизация маршрутов сократила транспортные расходы на 25%.",
    "Опрос: 60% россиян считают ИИ полезной технологией. Год назад было 40%.",
    "Внедрили ИИ-контроль качества на производстве. Брак сократился в 4 раза.",
    "Этика ИИ — не про запреты, а про ответственное использование. Нужны правила, как ПДД.",
    "Участвовал в разработке российского фреймворка для нейросетей. На 30% быстрее PyTorch.",
    "Аудит ИИ-систем в банках. 40% моделей содержат скрытый bias по возрасту и полу.",
]

expert_current_texts = [
    "Обновление проекта ИИ: результаты Q1 превзошли ожидания. Точность модели +15%.",
    "Провёл AMA про нейросети. Частый вопрос: заменит ли ИИ мою профессию?",
    "GPT-5 показывает признаки абстрактного мышления. Интересно, но скептицизм уместен.",
    "ИИ в HR: автоматический скрининг резюме сократил время найма с 30 до 5 дней.",
    "Доклад о рисках ИИ для Совета Федерации. Регулирование необходимо, но без перегибов.",
    "ИИ-тьютор адаптируется под уровень ученика. Персонализация обучения — будущее.",
    "Тренд 2026: ИИ-агенты для многошаговых задач. Claude Code, Devin меняют правила.",
    "Должен ли ИИ иметь право голоса? Нет. ИИ советует, человек решает.",
]

regular_texts = [
    "Попробовал нейросеть для аватарки. Получилось смешно — похож на эльфа.",
    "Сосед говорит ИИ всех заменит. А сам принтер настроить не может.",
    "Голосовой ИИ-ассистент для покупок. Удобно, не записываю руками.",
    "На работе показали презентацию про ИИ. Половина испугалась, другая заснула.",
    "Генерировал рецепты через ChatGPT. Предложил суп из ананаса и селёдки.",
    "Подруга учит английский с ИИ. Говорит, лучше репетитора.",
    "Задал ChatGPT физику — правильно. Математику — ошибся. 50 на 50.",
    "На собеседовании спросили про ИИ. Сказал да. Имел в виду гугление промптов.",
    "ИИ написал поздравление маме. Мама прослезилась. Чувствую себя мошенником.",
    "Заказал логотип — получил от нейросети. Заплатил 5000 за работу ИИ.",
    "ИИ для бюджета. Пока понял только, что слишком много трачу на кофе.",
    "Коллега пишет отчёты с ИИ. По качеству — верю.",
    "ИИ-переводчик перевёл I am cold как Я холодильник.",
    "Спросил ChatGPT менять ли работу. 10 за, 10 против. Спасибо.",
    "ИИ для определения растений. Показал кактус — говорит роза. Удалил.",
]

bot_texts = [
    "Топ нейросетей! Бесплатный доступ! Переходи!",
    "Заработай на ИИ 200000 в месяц! Бесплатный вебинар!",
    "Подборка новостей ИИ: обновления, функции, инвестиции.",
    "Нейросеть для заработка! Курс бесплатно!",
    "ИИ-новости дня: релизы, обновления, тренды.",
]

troll_texts = [
    "Техноблогеры впаривают курсы по ИИ. Классическая пирамида.",
    "ИИ — способ выкачивать деньги из инвесторов. Через 3 года забудут.",
    "Все ИИ-стартапы — обёртка ChatGPT API с наценкой x100.",
]

reposter_texts = [
    "Интересная статья про нейросети, рекомендую!",
    "Обзор ИИ-инструментов — сохраняйте!",
    "Новые правила ИИ в образовании.",
    "Хороший анализ рынка ИИ в России.",
]

creator_texts = [
    "Новый арт в стиле ренессанса, генерация + 5 часов Photoshop.",
    "Туториал: Stable Diffusion локально без ограничений.",
    "Написал бота на Python с GPT API. Код на GitHub.",
    "Промпт-батл: одна задача, 5 моделей. Бесплатная победила платную.",
    "Workflow из 10 ИИ-инструментов, экономит 3 часа в день.",
    "Fine-tuning на своих данных: пошаговый гайд.",
    "Open-source альтернатива Midjourney: Flux на RTX 3060.",
]

communities = [-100001, -100002, -100003, -100005, -100006, -100007]

def make_posts(author_id, texts, window, likes_r, reposts_r, comments_r, views_r, media_p=0.3, copy_p=0.0):
    global next_id
    posts = []
    for text in texts:
        ts_s = baseline_start if window == "BASELINE" else current_start
        ts_e = baseline_end if window == "BASELINE" else current_end
        posts.append({
            "vkId": next_id,
            "ownerId": random.choice(communities),
            "fromId": author_id,
            "date": random.randint(ts_s, ts_e),
            "text": text,
            "likes": random.randint(*likes_r),
            "reposts": random.randint(*reposts_r),
            "comments": random.randint(*comments_r),
            "views": random.randint(*views_r),
            "containsMedia": random.random() < media_p,
            "hasCopyHistory": random.random() < copy_p,
            "window": window
        })
        next_id += 1
    return posts

new_posts = []

# Experts: 6-10 baseline + 3-5 current each
experts = [1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010,
           1011, 1012, 1013, 1014, 1015, 1016, 1017, 1018, 1019, 1020]
for eid in experts:
    n = random.randint(6, 10)
    sample = random.sample(expert_baseline_texts, min(n, len(expert_baseline_texts)))
    if eid in [1001, 1019]:
        new_posts += make_posts(eid, sample, "BASELINE", (800, 4000), (200, 1200), (100, 700), (30000, 180000), 0.4)
    elif eid in [1002, 1017]:
        new_posts += make_posts(eid, sample, "BASELINE", (500, 2500), (150, 900), (200, 800), (20000, 100000), 0.2)
    else:
        new_posts += make_posts(eid, sample, "BASELINE", (200, 1500), (50, 600), (50, 400), (10000, 60000), 0.3)

for eid in experts[:10]:
    n = random.randint(3, 5)
    sample = random.sample(expert_current_texts, min(n, len(expert_current_texts)))
    if eid == 1001:
        new_posts += make_posts(eid, sample, "CURRENT", (1500, 5000), (400, 1500), (200, 900), (80000, 250000), 0.4)
    else:
        new_posts += make_posts(eid, sample, "CURRENT", (300, 2000), (80, 700), (50, 500), (15000, 80000), 0.3)

# Regular users: 3-5 baseline + 2-3 current
regulars = [2001, 2002, 2003, 2004, 2005, 2006, 2007, 2008, 2009, 2010, 2011, 2012, 2013, 2014, 2015]
for uid in regulars:
    n = random.randint(3, 5)
    sample = random.sample(regular_texts, min(n, len(regular_texts)))
    new_posts += make_posts(uid, sample, "BASELINE", (5, 80), (0, 15), (2, 40), (100, 3000), 0.2)
    n2 = random.randint(2, 3)
    sample2 = random.sample(regular_texts, min(n2, len(regular_texts)))
    new_posts += make_posts(uid, sample2, "CURRENT", (8, 100), (0, 20), (3, 50), (150, 4000), 0.2)

# Bots: 6 baseline + 4 current (repetitive)
bots = [3001, 3002, 3003, 3004, 3005]
for bid in bots:
    texts = (bot_texts * 2)[:6]
    new_posts += make_posts(bid, texts, "BASELINE", (0, 5), (0, 2), (0, 3), (20, 200), 0.5)
    new_posts += make_posts(bid, texts[:4], "CURRENT", (0, 4), (0, 1), (0, 2), (15, 150), 0.5)

# Trolls: 3 baseline + 2 current
trolls = [4001, 4002, 4003]
for tid in trolls:
    new_posts += make_posts(tid, troll_texts, "BASELINE", (30, 250), (10, 100), (50, 400), (1500, 10000), 0.1)
    new_posts += make_posts(tid, troll_texts[:2], "CURRENT", (40, 300), (15, 120), (60, 500), (2000, 12000), 0.1)

# Reposters: 4 baseline + 3 current (all reposts)
reposters = [5001, 5002, 5003, 5004, 5005]
for rid in reposters:
    new_posts += make_posts(rid, reposter_texts, "BASELINE", (1, 10), (0, 3), (0, 2), (30, 200), 0.0, 1.0)
    new_posts += make_posts(rid, reposter_texts[:3], "CURRENT", (1, 12), (0, 4), (0, 3), (40, 250), 0.0, 1.0)

# Content creators: 4-6 baseline + 2-4 current
creators = [6001, 6002, 6003, 6004, 6005]
for cid in creators:
    n = random.randint(4, 6)
    sample = random.sample(creator_texts, min(n, len(creator_texts)))
    new_posts += make_posts(cid, sample, "BASELINE", (200, 1800), (80, 800), (30, 250), (8000, 50000), 0.5)
    n2 = random.randint(2, 4)
    sample2 = random.sample(creator_texts, min(n2, len(creator_texts)))
    new_posts += make_posts(cid, sample2, "CURRENT", (250, 2000), (100, 900), (40, 300), (10000, 60000), 0.5)

data['posts'] += new_posts

total = len(data['posts'])
bl = sum(1 for p in data['posts'] if p['window'] == 'BASELINE')
cu = sum(1 for p in data['posts'] if p['window'] == 'CURRENT')
authors_posting = len(set(p['fromId'] for p in data['posts']))

print(f"Total posts: {total} (BASELINE: {bl}, CURRENT: {cu})")
print(f"Authors posting: {authors_posting}")
print(f"New posts added: {len(new_posts)}")

from collections import Counter
bl_counts = Counter(p['fromId'] for p in data['posts'] if p['window'] == 'BASELINE')
print(f"\nBaseline posts per author (top 25):")
for aid, cnt in bl_counts.most_common(25):
    print(f"  {aid}: {cnt}")

with open('examples/ai_dataset.json', 'w', encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print("\nFile saved.")
