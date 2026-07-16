"""
Generator for synthetic dataset: VK Max Messenger promotion campaign.
Based on real public figures, real events (Telegram restrictions, Max launch),
and realistic VK engagement metrics.
Run: python generate_max_dataset.py
Output: dataset_vk_max_messenger.json
"""
import json
import random
import time

random.seed(77)

NOW = int(time.time())
DAY = 86400
CURRENT_START = NOW - 30 * DAY
BASELINE_START = CURRENT_START - 60 * DAY

def rnd_current(): return random.randint(CURRENT_START, NOW)
def rnd_baseline(): return random.randint(BASELINE_START, CURRENT_START)

# ─── Communities ───
communities = [
    {"vkId": 147845620, "name": "MAX | Мессенджер", "screenName": "max_messenger", "membersCount": 230000, "isClosed": False, "type": "page"},
    {"vkId": 178320156, "name": "Программирование и IT", "screenName": "proglib", "membersCount": 415000, "isClosed": False, "type": "group"},
    {"vkId": 192045678, "name": "Цифровая Россия", "screenName": "digital_russia", "membersCount": 87000, "isClosed": False, "type": "group"},
    {"vkId": 201567890, "name": "Мобильные технологии", "screenName": "mobile_tech_ru", "membersCount": 156000, "isClosed": False, "type": "group"},
    {"vkId": 183456012, "name": "IT и бизнес в России", "screenName": "it_business_ru", "membersCount": 64000, "isClosed": False, "type": "group"},
]

# ─── Authors ───
vk_id_counter = 2000000
def next_id():
    global vk_id_counter
    vk_id_counter += random.randint(200, 80000)
    return vk_id_counter

# LEADERS (10) — real public figures connected to Max/VK/Telegram topic
leaders = [
    {"firstName": "Владимир", "lastName": "Кириенко", "screenName": "kirienko_vk", "followersCount": 125000,
     "role": "leader", "position": "positive", "topic_posts": 10, "bg_posts": 12},
    {"firstName": "Фарит", "lastName": "Хуснияров", "screenName": "khusn_farit", "followersCount": 18000,
     "role": "leader", "position": "positive", "topic_posts": 8, "bg_posts": 10},
    {"firstName": "Антон", "lastName": "Немкин", "screenName": "nemkin_anton", "followersCount": 42000,
     "role": "leader", "position": "positive", "topic_posts": 7, "bg_posts": 11},
    {"firstName": "Максут", "lastName": "Шадаев", "screenName": "shadaev_m", "followersCount": 89000,
     "role": "leader", "position": "neutral", "topic_posts": 6, "bg_posts": 14},
    {"firstName": "Александр", "lastName": "Хинштейн", "screenName": "khinshtein_a", "followersCount": 210000,
     "role": "leader", "position": "positive", "topic_posts": 9, "bg_posts": 13},
    {"firstName": "Светлана", "lastName": "Бондарчук", "screenName": "bondarchuk_sv", "followersCount": 480000,
     "role": "leader", "position": "positive", "topic_posts": 6, "bg_posts": 18},
    {"firstName": "Валерий", "lastName": "Фадеев", "screenName": "fadeev_val", "followersCount": 56000,
     "role": "leader", "position": "neutral", "topic_posts": 5, "bg_posts": 10},
    {"firstName": "Аскар", "lastName": "Туганбаев", "screenName": "tuganbaev_a", "followersCount": 34000,
     "role": "leader", "position": "positive", "topic_posts": 8, "bg_posts": 9},
    {"firstName": "Евгений", "lastName": "Козлов", "screenName": "kozlov_ev_tech", "followersCount": 28000,
     "role": "leader", "position": "negative", "topic_posts": 9, "bg_posts": 8},
    {"firstName": "Артём", "lastName": "Козлюк", "screenName": "kozlyuk_art", "followersCount": 67000,
     "role": "leader", "position": "negative", "topic_posts": 7, "bg_posts": 12},
]

# GIANTS (12) — big audience, mentioned Max once or twice
giants = [
    {"firstName": "Илья", "lastName": "Варламов", "screenName": "varlamov", "followersCount": 325000,
     "role": "giant", "position": "negative", "topic_posts": 2, "bg_posts": 20},
    {"firstName": "Артемий", "lastName": "Лебедев", "screenName": "temalebedev", "followersCount": 520000,
     "role": "giant", "position": "negative", "topic_posts": 1, "bg_posts": 22},
    {"firstName": "Юрий", "lastName": "Дудь", "screenName": "yuriy_dud", "followersCount": 410000,
     "role": "giant", "position": "neutral", "topic_posts": 1, "bg_posts": 19},
    {"firstName": "Ксения", "lastName": "Собчак", "screenName": "xenia_sobchak", "followersCount": 390000,
     "role": "giant", "position": "neutral", "topic_posts": 1, "bg_posts": 17},
    {"firstName": "Павел", "lastName": "Воля", "screenName": "pavelvolya", "followersCount": 280000,
     "role": "giant", "position": "neutral", "topic_posts": 2, "bg_posts": 16},
    {"firstName": "Настя", "lastName": "Ивлеева", "screenName": "ivleeva_nastya", "followersCount": 350000,
     "role": "giant", "position": "positive", "topic_posts": 1, "bg_posts": 21},
    {"firstName": "Алёна", "lastName": "Владимирская", "screenName": "vladimirskaya_a", "followersCount": 85000,
     "role": "giant", "position": "negative", "topic_posts": 2, "bg_posts": 14},
    {"firstName": "Николай", "lastName": "Давыдов", "screenName": "davydov_nik", "followersCount": 95000,
     "role": "giant", "position": "neutral", "topic_posts": 1, "bg_posts": 15},
    {"firstName": "Тина", "lastName": "Канделаки", "screenName": "tina_kandelaki", "followersCount": 310000,
     "role": "giant", "position": "positive", "topic_posts": 2, "bg_posts": 18},
    {"firstName": "Маргарита", "lastName": "Симоньян", "screenName": "simonyan_rt", "followersCount": 440000,
     "role": "giant", "position": "positive", "topic_posts": 1, "bg_posts": 20},
    {"firstName": "Олег", "lastName": "Тиньков", "screenName": "olegtinkov", "followersCount": 380000,
     "role": "giant", "position": "negative", "topic_posts": 1, "bg_posts": 19},
    {"firstName": "Дмитрий", "lastName": "Потапенко", "screenName": "potapenko_d", "followersCount": 145000,
     "role": "giant", "position": "negative", "topic_posts": 2, "bg_posts": 13},
]

# ACTIVISTS (35) — mid-low followers, many topic posts
activist_data = [
    ("Антон", "Мельников", "melnikov_ant", 3400), ("Екатерина", "Лебедева", "lebedeva_ek", 5100),
    ("Сергей", "Корнилов", "kornilov_sg", 2200), ("Мария", "Зотова", "zotova_maria", 6300),
    ("Дмитрий", "Савченко", "savchenko_dm", 1800), ("Ольга", "Петрова", "petrova_ol", 7500),
    ("Андрей", "Кузьмин", "kuzmin_andr", 950), ("Елена", "Максимова", "maksimova_e", 4100),
    ("Николай", "Тарасов", "tarasov_n", 2600), ("Анна", "Воробьёва", "vorobyova_a", 5800),
    ("Павел", "Громов", "gromov_pv", 1400), ("Ксения", "Орлова", "orlova_x", 4700),
    ("Игорь", "Белов", "belov_igor", 3000), ("Татьяна", "Миронова", "mironova_t", 5400),
    ("Роман", "Козлов", "kozlov_rm", 2100), ("Алина", "Новикова", "novikova_a", 3800),
    ("Виктор", "Степанов", "stepanov_v", 820), ("Юлия", "Соколова", "sokolova_yu", 4500),
    ("Максим", "Фёдоров", "fedorov_mx", 7100), ("Дарья", "Климова", "klimova_d", 2800),
    ("Артём", "Волков", "volkov_a", 1600), ("Наталья", "Егорова", "egorova_nt", 3900),
    ("Кирилл", "Морозов", "morozov_k", 1050), ("Вероника", "Смирнова", "smirnova_v", 5600),
    ("Денис", "Захаров", "zakharov_d", 2200), ("Тимур", "Басыров", "basyrov_tm", 4300),
    ("Светлана", "Пархоменко", "parkhomenko_s", 2900), ("Илья", "Рябцев", "ryabtsev_i", 1700),
    ("Анастасия", "Горшкова", "gorshkova_a", 3600), ("Владимир", "Чернов", "chernov_v", 6200),
    ("Глеб", "Рыжов", "ryzhov_g", 1900), ("Лилия", "Мухина", "mukhina_l", 4800),
    ("Станислав", "Носов", "nosov_s", 3200), ("Регина", "Уварова", "uvarova_r", 2500),
    ("Тимофей", "Литвинов", "litvinov_t", 5700),
]
activists = []
for fn, ln, sn, fc in activist_data:
    activists.append({
        "firstName": fn, "lastName": ln, "screenName": sn, "followersCount": fc,
        "role": "activist",
        "position": random.choice(["positive", "positive", "neutral", "negative", "negative", "neutral"]),
        "topic_posts": random.randint(5, 11), "bg_posts": random.randint(3, 7),
    })

# BACKGROUND (38)
bg_names = [
    ("Олег","Комаров"), ("Григорий","Панов"), ("Валерия","Ефимова"), ("Степан","Соловьёв"),
    ("Людмила","Власова"), ("Михаил","Куликов"), ("Полина","Попова"), ("Тимофей","Семёнов"),
    ("Евгения","Крылова"), ("Фёдор","Макаров"), ("Лариса","Ильина"), ("Владислав","Жуков"),
    ("Карина","Быкова"), ("Руслан","Лазарев"), ("Зинаида","Антонова"), ("Александра","Фролов"),
    ("Георгий","Гусев"), ("Маргарита","Титова"), ("Вадим","Беляев"), ("Софья","Данилова"),
    ("Иван","Барсуков"), ("Оксана","Котова"), ("Даниил","Медведев"), ("Ирина","Сергеева"),
    ("Пётр","Шестаков"), ("Галина","Фомина"), ("Леонид","Голубев"), ("Арина","Борисова"),
    ("Виталий","Никулин"), ("Лидия","Суханова"), ("Яков","Третьяков"), ("Диана","Зайцева"),
    ("Эдуард","Калашников"), ("Валентина","Вишнякова"), ("Богдан","Щербаков"),
    ("Алиса","Тихонова"), ("Захар","Цветков"), ("Нина","Архипова"),
]
background = []
for fn, ln in bg_names:
    sn = f"{ln.lower().replace('ё','e')}_{fn[0].lower()}"
    background.append({
        "firstName": fn, "lastName": ln, "screenName": sn,
        "followersCount": random.randint(25, 500),
        "role": "background",
        "position": random.choice(["neutral", "neutral", "neutral", "positive", "negative"]),
        "topic_posts": random.randint(1, 3), "bg_posts": random.randint(2, 6),
    })

# CLOSED (5)
closed = [
    {"firstName": "Алексей", "lastName": "Тёмный", "screenName": "dark_al", "followersCount": 160, "isClosed": True},
    {"firstName": "Марина", "lastName": "Скрытная", "screenName": "hidden_m", "followersCount": 90, "isClosed": True},
    {"firstName": "Виктор", "lastName": "Закрытый", "screenName": "closed_v", "followersCount": 230, "isClosed": True},
    {"firstName": "Елена", "lastName": "Приватная", "screenName": "priv_el", "followersCount": 55, "isClosed": True},
    {"firstName": "Сергей", "lastName": "Невидимый", "screenName": "invis_s", "followersCount": 110, "isClosed": True},
]
for c in closed:
    c.update({"role": "closed", "position": "neutral", "topic_posts": 0, "bg_posts": 0})

all_authors_meta = leaders + giants + activists + background + closed
all_authors = []
for meta in all_authors_meta:
    vid = next_id()
    meta["vkId"] = vid
    all_authors.append({
        "vkId": vid, "firstName": meta["firstName"], "lastName": meta["lastName"],
        "screenName": meta.get("screenName", ""), "followersCount": meta["followersCount"],
        "isClosed": meta.get("isClosed", False),
    })

print(f"Total authors: {len(all_authors)}")

# ─── Post texts ───
topic_positive = [
    "Перешёл на Max, и знаете что? Звонки реально лучше чем в Телеге. Качество голоса отличное",
    "Max от VK — наконец-то нормальный российский мессенджер. Файлы до 2 ГБ, это жирный плюс",
    "Уже 107 млн пользователей в Max. Критическая масса набрана, теперь все контакты там",
    "Перевёл рабочие чаты в Max. Интеграция с VK и госуслугами — удобно для бизнеса",
    "Мини-приложения в Max — это как WeChat, только российский. Оплата, заказы, всё в одном",
    "Боты в Max работают отлично. Перенёс из Телеграма за вечер, API почти совместимый",
    "Max предустановлен на новых телефонах, это правильный подход. Пусть люди пробуют",
    "Групповые видеозвонки в Max — топ. До 100 участников, стабильно работает",
    "Сделал канал в Max, за неделю 5000 подписчиков. Аудитория голодная до контента",
    "Для учителей Max — находка. Родительские чаты, файлы без сжатия, всё бесплатно",
    "Кириенко обещает 15 млн звонков в день через Max. Цифры говорят сами за себя",
    "Max для бизнеса: CRM интеграция, боты поддержки, рассылки. Всё что нужно",
    "Скептики говорили Max не взлетит. 85 млн пользователей говорят обратное",
    "Обновления Max выходят каждые 2 недели. Разработчики реально слушают фидбэк",
]

topic_negative = [
    "Max — это слежка под видом мессенджера. Никакого шифрования, данные сливают",
    "На Хабре выложили реверс Max: недокументированные функции слежки. Кто вообще этим пользуется?",
    "Заставляют переходить на Max, блокируя Telegram. Это не конкуренция, а принуждение",
    "Три месяца Max работает — баги на каждом шагу. Уведомления пропадают, чаты глючат",
    "Max без шифрования — это как открытка по почте. Все могут прочитать",
    "Блогеры рекламируют Max за деньги, а сами сидят в Telegram. Лицемерие",
    "Навязывание Max через предустановку — это не выбор пользователя, это госзаказ",
    "В Max нет стикеров, нет каналов нормальных, нет ничего. Голый мессенджер",
    "Max — переименованный VK Мессенджер. Те же яйца, вид сбоку. Ничего нового",
    "Почему нельзя просто улучшить Telegram вместо создания его клона за бюджетные деньги?",
    "Реверс-инжиниринг показал: Max отправляет геолокацию и список контактов на серверы VK",
    "Бизнес заставляют переходить в Max. ФАС запретила рекламу в Telegram. Это нормально?",
    "Max crash каждый третий звонок на Андроид. Протестировал лично, это непригодно для работы",
]

topic_neutral = [
    "Telegram замедляют, Max продвигают. Посмотрим, чем закончится к концу года",
    "Установил Max, попробовал. Работает, но пока мало контактов там",
    "Max vs Telegram: сравнил основные функции. У каждого свои плюсы и минусы",
    "Перенёс один рабочий чат в Max для теста. Пока работает, но VPN на Telegram тоже никто не отменял",
    "107 млн регистраций, но DAU 77 млн. Значит 30 млн зашли и ушли. Интересная статистика",
    "Законопроект о блокировке Telegram. Если примут — выбора не останется, все перейдут в Max",
    "Max для чиновников — обязательно до Нового года. Для остальных — добровольно. Пока что",
    "Попробовал Max для созвонов. Нормально, но интерфейс непривычный после Telegram",
    "Max подходит для базового общения. Для продвинутых пользователей пока не хватает фич",
    "ФАС запретила рекламу в Telegram. Бизнесу придётся осваивать Max или VK",
    "Max и Telegram могут сосуществовать. Не обязательно выбирать одно",
]

baseline_texts = [
    "Выходные на даче. Шашлыки, солнце, тишина", "Москва в пробках с утра. Обычное дело",
    "Дочитал новый роман Пелевина. Неплохо, но не шедевр", "Записался на курсы английского",
    "Пробежал 10 км. Личный рекорд!", "Кот снова на клавиатуре",
    "Ремонт на кухне затянулся. Третий месяц", "Сходили в Третьяковку. Дети в восторге",
    "Отпуск в Турции. Всё включено, мозги выключены", "Готовлю борщ по бабушкиному рецепту",
    "Пятница. Наконец-то", "Новый сезон Слова пацана. Ну такое",
    "Собеседование прошло хорошо. Ждём оффер", "Купил подписку на Яндекс Плюс",
    "Дождь неделю. Настроение соответствующее", "Играю на гитаре. Соседи терпят",
    "Прочитал статью про крипту. Ничего не понял", "День рождения сына. 3 года!",
    "Заказал доставку. Привезли за 12 минут, молодцы", "Матч Спартак-Зенит. Нервы",
    "Начал бегать по утрам. День второй", "Обновил телефон. Теперь всё летает",
    "Рабочий созвон на 2 часа. Можно было за 10 минут в чате",
    "Нашёл старые фото из школы. Ностальгия", "Варю глинтвейн. За окном снег",
]

comment_positive = [
    "Плюсую, сам перешёл", "Норм мессенджер, привык уже", "Наконец-то российское и рабочее",
    "Звонки реально ок", "Боты удобные", "Канал завёл, подписчики идут",
    "Лучше чем ожидал", "Файлы 2гб это огонь", "Работает стабильно у меня",
    "Поддерживаю, пора уходить от западных сервисов",
]
comment_negative = [
    "Слежка и баги, отличный продукт", "Лол, кому это надо", "Телега лучше в 100 раз",
    "Через впн всё работает, зачем этот макс", "Навязывание чистой воды",
    "Без шифрования? Нет спасибо", "Баги, баги, баги", "Верните Телеграм",
    "Это не мессенджер а прослушка", "Клон телеги но хуже",
]
comment_neutral = [
    "Интересно, надо попробовать", "А как перенести чаты?", "Посмотрим",
    "Пока непонятно", "У кого Max норм работает?", "Сколько там людей реально?",
    "Ну ок", "Надо подождать обновлений", "Кто-нибудь для работы использует?",
]

# ─── Generate posts ───
posts = []
post_id = 20000
post_meta = []

for meta in all_authors_meta:
    if meta.get("isClosed"): continue
    vid = meta["vkId"]
    f_count = meta["followersCount"]

    for _ in range(meta["topic_posts"]):
        post_id += 1
        pos = meta["position"]
        if pos == "positive": text = random.choice(topic_positive)
        elif pos == "negative": text = random.choice(topic_negative)
        else: text = random.choice(topic_neutral)

        base_views = int(f_count * random.uniform(0.3, 1.5))
        likes = max(1, int(base_views / random.uniform(15, 50)))
        reposts = max(0, int(likes / random.uniform(3, 10)))
        comments_cnt = max(0, int(likes / random.uniform(3, 20)))

        owner_id = -random.choice(communities)["vkId"] if random.random() < 0.25 else vid

        p = {
            "vkId": post_id, "ownerId": owner_id, "fromId": vid,
            "date": rnd_current(), "text": text,
            "likes": likes, "reposts": reposts, "comments": comments_cnt,
            "views": base_views, "containsMedia": random.random() < 0.25,
            "hasCopyHistory": False, "window": "CURRENT",
        }
        posts.append(p)
        if comments_cnt > 0:
            post_meta.append({"postVkId": post_id, "postOwnerId": owner_id, "comments_cnt": comments_cnt})

    for _ in range(meta["bg_posts"]):
        post_id += 1
        text = random.choice(baseline_texts)
        base_views = int(f_count * random.uniform(0.2, 1.0))
        likes = max(1, int(base_views / random.uniform(15, 50)))
        reposts = max(0, int(likes / random.uniform(5, 15)))
        comments_cnt = max(0, int(likes / random.uniform(5, 25)))

        posts.append({
            "vkId": post_id, "ownerId": vid, "fromId": vid,
            "date": rnd_baseline(), "text": text,
            "likes": likes, "reposts": reposts, "comments": comments_cnt,
            "views": base_views, "containsMedia": random.random() < 0.2,
            "hasCopyHistory": random.random() < 0.05, "window": "BASELINE",
        })

# Off-topic posts
off_topic = [
    "Кто идёт на концерт Басты? Есть лишний билет",
    "Рецепт пасты карбонара. Бекон, пармезан, желтки...",
    "Прошёл Elden Ring DLC. 200 часов не зря потрачены",
    "Продаю самокат Xiaomi, почти новый. 12000р, м. Тушинская",
]
for text in off_topic:
    post_id += 1
    vid = random.choice([a["vkId"] for a in all_authors_meta if not a.get("isClosed")])
    posts.append({
        "vkId": post_id, "ownerId": vid, "fromId": vid,
        "date": rnd_current(), "text": text,
        "likes": random.randint(2, 30), "reposts": random.randint(0, 5),
        "comments": random.randint(0, 8), "views": random.randint(50, 500),
        "containsMedia": random.random() < 0.5, "hasCopyHistory": False, "window": "CURRENT",
    })

# ─── Generate comments ───
known_ids = [a["vkId"] for a in all_authors_meta if not a.get("isClosed")]
unknown_ids = [random.randint(10000000, 800000000) for _ in range(30)]
all_commenter_ids = known_ids + unknown_ids

comments = []
comment_id = 60000
for pm in post_meta:
    n = min(pm["comments_cnt"], random.randint(1, 8))
    for _ in range(n):
        comment_id += 1
        sent = random.choices(["positive","neutral","negative"], weights=[30,40,30])[0]
        if sent == "positive": text = random.choice(comment_positive)
        elif sent == "negative": text = random.choice(comment_negative)
        else: text = random.choice(comment_neutral)
        comments.append({
            "vkId": comment_id, "postVkId": pm["postVkId"], "postOwnerId": pm["postOwnerId"],
            "fromId": random.choice(all_commenter_ids), "date": rnd_current(),
            "text": text, "likes": random.randint(0, 15),
        })

cur = sum(1 for p in posts if p["window"] == "CURRENT")
bl = sum(1 for p in posts if p["window"] == "BASELINE")
print(f"Posts: {len(posts)} (CURRENT: {cur}, BASELINE: {bl})")
print(f"Comments: {len(comments)}")

dataset = {"communities": communities, "authors": all_authors, "posts": posts, "comments": comments}
out = "dataset_vk_max_messenger.json"
with open(out, "w", encoding="utf-8") as f:
    json.dump(dataset, f, ensure_ascii=False, indent=2)

print(f"\nDataset written to {out}")
print(f"  Communities: {len(communities)}")
print(f"  Authors: {len(all_authors)} ({sum(1 for a in all_authors if a['isClosed'])} closed)")
print(f"  Posts: {len(posts)} (CURRENT: {cur}, BASELINE: {bl})")
print(f"  Comments: {len(comments)}")
