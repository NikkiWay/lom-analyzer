"""
Generator for synthetic dataset: AI and Neural Networks in Russia.
Based on real public figures, real VK community structures, and realistic engagement metrics.
Run: python generate_ai_dataset.py
Output: dataset_ai_neural_networks.json
"""
import json
import random
import time

random.seed(42)

# ─── Time windows ───
NOW = int(time.time())
DAY = 86400
CURRENT_START = NOW - 30 * DAY
BASELINE_START = CURRENT_START - 60 * DAY

def rnd_date(start, end):
    return random.randint(start, end)

def rnd_current():
    return rnd_date(CURRENT_START, NOW)

def rnd_baseline():
    return rnd_date(BASELINE_START, CURRENT_START)

# ─── Communities (based on real VK groups about AI/neural networks) ───
communities = [
    {"vkId": 194580895, "name": "Нейросети и ИИ", "screenName": "neural_networks_ai", "membersCount": 342000, "isClosed": False, "type": "group"},
    {"vkId": 211938498, "name": "Искусственный интеллект | AI Russia", "screenName": "ai_russia_community", "membersCount": 97000, "isClosed": False, "type": "group"},
    {"vkId": 178320156, "name": "Программирование и IT", "screenName": "proglib", "membersCount": 415000, "isClosed": False, "type": "group"},
    {"vkId": 165230847, "name": "Наука и технологии", "screenName": "sci_tech_news", "membersCount": 128000, "isClosed": False, "type": "group"},
    {"vkId": 202456789, "name": "GigaChat Community", "screenName": "gigachat_official", "membersCount": 54000, "isClosed": False, "type": "page"},
]

# ─── Authors: 100 authors across categories ───
# Categories:
#   Leaders (10): real public figures, high followers, many topic posts -> AUTHORITATIVE_LEADER
#   Giants (12): real known people, high followers, few topic posts -> SLEEPING_GIANT
#   Activists (25): mid-low followers, many topic posts -> TOPIC_ACTIVIST
#   Background (38): low followers, few posts -> BACKGROUND_AUTHOR
#   Closed accounts (5): isClosed=true
#   Off-topic heavy (10): mixed, some posts irrelevant

authors = []
vk_id_counter = 1000000

def next_id():
    global vk_id_counter
    vk_id_counter += random.randint(100, 50000)
    return vk_id_counter

# ── LEADERS (10) — real public figures who discuss AI ──
leaders = [
    {"firstName": "Герман", "lastName": "Греф", "screenName": "gref_german", "followersCount": 487000,
     "role": "leader", "position": "positive", "topic_posts": 8, "bg_posts": 12},
    {"firstName": "Александр", "lastName": "Крайнов", "screenName": "a_krainov", "followersCount": 35200,
     "role": "leader", "position": "positive", "topic_posts": 12, "bg_posts": 8},
    {"firstName": "Игорь", "lastName": "Ашманов", "screenName": "ashmanov_igor", "followersCount": 128000,
     "role": "leader", "position": "negative", "topic_posts": 10, "bg_posts": 15},
    {"firstName": "Максут", "lastName": "Шадаев", "screenName": "shadaev_maksut", "followersCount": 89000,
     "role": "leader", "position": "neutral", "topic_posts": 7, "bg_posts": 10},
    {"firstName": "Наталья", "lastName": "Касперская", "screenName": "n_kasperskaya", "followersCount": 96000,
     "role": "leader", "position": "negative", "topic_posts": 9, "bg_posts": 11},
    {"firstName": "Андрей", "lastName": "Себрант", "screenName": "sebrant", "followersCount": 52000,
     "role": "leader", "position": "positive", "topic_posts": 11, "bg_posts": 9},
    {"firstName": "Константин", "lastName": "Воронцов", "screenName": "vorontsov_kv", "followersCount": 67000,
     "role": "leader", "position": "neutral", "topic_posts": 8, "bg_posts": 14},
    {"firstName": "Артур", "lastName": "Хачуян", "screenName": "khachuyan_art", "followersCount": 53000,
     "role": "leader", "position": "positive", "topic_posts": 9, "bg_posts": 7},
    {"firstName": "Иван", "lastName": "Ямщиков", "screenName": "yamshchikov_iv", "followersCount": 38000,
     "role": "leader", "position": "neutral", "topic_posts": 10, "bg_posts": 8},
    {"firstName": "Дмитрий", "lastName": "Песков", "screenName": "peskov_asi", "followersCount": 72000,
     "role": "leader", "position": "positive", "topic_posts": 7, "bg_posts": 13},
]

# ── GIANTS (12) — big audience, rarely post on AI topic ──
giants = [
    {"firstName": "Илья", "lastName": "Варламов", "screenName": "varlamov", "followersCount": 325000,
     "role": "giant", "position": "neutral", "topic_posts": 2, "bg_posts": 20},
    {"firstName": "Максим", "lastName": "Кац", "screenName": "max_katz", "followersCount": 198000,
     "role": "giant", "position": "neutral", "topic_posts": 1, "bg_posts": 18},
    {"firstName": "Алексей", "lastName": "Пивоваров", "screenName": "pivovarov_a", "followersCount": 156000,
     "role": "giant", "position": "positive", "topic_posts": 2, "bg_posts": 15},
    {"firstName": "Ирина", "lastName": "Шихман", "screenName": "shikhman_i", "followersCount": 142000,
     "role": "giant", "position": "neutral", "topic_posts": 1, "bg_posts": 16},
    {"firstName": "Юрий", "lastName": "Дудь", "screenName": "yuriy_dud", "followersCount": 410000,
     "role": "giant", "position": "neutral", "topic_posts": 1, "bg_posts": 22},
    {"firstName": "Артемий", "lastName": "Лебедев", "screenName": "temalebedev", "followersCount": 520000,
     "role": "giant", "position": "negative", "topic_posts": 2, "bg_posts": 25},
    {"firstName": "Олег", "lastName": "Тиньков", "screenName": "olegtinkov", "followersCount": 380000,
     "role": "giant", "position": "positive", "topic_posts": 1, "bg_posts": 19},
    {"firstName": "Евгений", "lastName": "Черешнев", "screenName": "chereshnev_e", "followersCount": 47000,
     "role": "giant", "position": "positive", "topic_posts": 2, "bg_posts": 14},
    {"firstName": "Руслан", "lastName": "Усачев", "screenName": "usachev_r", "followersCount": 275000,
     "role": "giant", "position": "neutral", "topic_posts": 1, "bg_posts": 17},
    {"firstName": "Светлана", "lastName": "Романова", "screenName": "romanova_sv", "followersCount": 64000,
     "role": "giant", "position": "neutral", "topic_posts": 2, "bg_posts": 12},
    {"firstName": "Алёна", "lastName": "Владимирская", "screenName": "vladimirskaya_a", "followersCount": 85000,
     "role": "giant", "position": "negative", "topic_posts": 2, "bg_posts": 13},
    {"firstName": "Борис", "lastName": "Добродеев", "screenName": "dobrodeev_b", "followersCount": 41000,
     "role": "giant", "position": "positive", "topic_posts": 1, "bg_posts": 11},
]

# ── ACTIVISTS (30) — small audience, many topic posts ──
activists = [
    {"firstName": "Тимур", "lastName": "Басыров", "screenName": "basyrov_t", "followersCount": 4100},
    {"firstName": "Светлана", "lastName": "Пархоменко", "screenName": "parkhomenko_sv", "followersCount": 2700},
    {"firstName": "Илья", "lastName": "Рябцев", "screenName": "ryabtsev_il", "followersCount": 1600},
    {"firstName": "Анастасия", "lastName": "Горшкова", "screenName": "gorshkova_an", "followersCount": 3400},
    {"firstName": "Владимир", "lastName": "Чернов", "screenName": "chernov_vl", "followersCount": 5900},
    {"firstName": "Антон", "lastName": "Мельников", "screenName": "melnikov_dev", "followersCount": 3200},
    {"firstName": "Екатерина", "lastName": "Лебедева", "screenName": "lebedeva_kate", "followersCount": 4800},
    {"firstName": "Сергей", "lastName": "Корнилов", "screenName": "kornilov_ml", "followersCount": 2100},
    {"firstName": "Мария", "lastName": "Зотова", "screenName": "zotova_m", "followersCount": 5600},
    {"firstName": "Дмитрий", "lastName": "Савченко", "screenName": "savchenko_ds", "followersCount": 1700},
    {"firstName": "Ольга", "lastName": "Петрова", "screenName": "petrova_olga", "followersCount": 7200},
    {"firstName": "Андрей", "lastName": "Кузьмин", "screenName": "kuzmin_a", "followersCount": 890},
    {"firstName": "Елена", "lastName": "Максимова", "screenName": "maksimova_el", "followersCount": 3900},
    {"firstName": "Николай", "lastName": "Тарасов", "screenName": "tarasov_nik", "followersCount": 2400},
    {"firstName": "Анна", "lastName": "Воробьёва", "screenName": "vorobyova_ann", "followersCount": 6100},
    {"firstName": "Павел", "lastName": "Громов", "screenName": "gromov_pav", "followersCount": 1300},
    {"firstName": "Ксения", "lastName": "Орлова", "screenName": "orlova_ks", "followersCount": 4400},
    {"firstName": "Игорь", "lastName": "Белов", "screenName": "belov_ig", "followersCount": 2800},
    {"firstName": "Татьяна", "lastName": "Миронова", "screenName": "mironova_tat", "followersCount": 5100},
    {"firstName": "Роман", "lastName": "Козлов", "screenName": "kozlov_rom", "followersCount": 1900},
    {"firstName": "Алина", "lastName": "Новикова", "screenName": "novikova_al", "followersCount": 3500},
    {"firstName": "Виктор", "lastName": "Степанов", "screenName": "stepanov_vik", "followersCount": 780},
    {"firstName": "Юлия", "lastName": "Соколова", "screenName": "sokolova_yu", "followersCount": 4200},
    {"firstName": "Максим", "lastName": "Фёдоров", "screenName": "fedorov_max", "followersCount": 6800},
    {"firstName": "Дарья", "lastName": "Климова", "screenName": "klimova_dar", "followersCount": 2600},
    {"firstName": "Артём", "lastName": "Волков", "screenName": "volkov_art", "followersCount": 1500},
    {"firstName": "Наталья", "lastName": "Егорова", "screenName": "egorova_nat", "followersCount": 3700},
    {"firstName": "Кирилл", "lastName": "Морозов", "screenName": "morozov_kir", "followersCount": 920},
    {"firstName": "Вероника", "lastName": "Смирнова", "screenName": "smirnova_ver", "followersCount": 5300},
    {"firstName": "Денис", "lastName": "Захаров", "screenName": "zakharov_den", "followersCount": 2000},
    {"firstName": "Глеб", "lastName": "Рыжов", "screenName": "ryzhov_gl", "followersCount": 1800},
    {"firstName": "Лилия", "lastName": "Мухина", "screenName": "mukhina_lil", "followersCount": 4600},
    {"firstName": "Станислав", "lastName": "Носов", "screenName": "nosov_st", "followersCount": 3100},
    {"firstName": "Регина", "lastName": "Уварова", "screenName": "uvarova_reg", "followersCount": 2300},
    {"firstName": "Тимофей", "lastName": "Литвинов", "screenName": "litvinov_tim", "followersCount": 5500},
]
for a in activists:
    a.update({"role": "activist", "position": random.choice(["positive", "positive", "neutral", "neutral", "negative"]),
              "topic_posts": random.randint(5, 12), "bg_posts": random.randint(3, 8)})

# ── BACKGROUND (38) — low followers, few posts ──
bg_first = ["Олег","Григорий","Валерия","Степан","Людмила","Михаил","Полина","Тимофей",
            "Евгения","Фёдор","Лариса","Владислав","Карина","Руслан","Зинаида","Александра",
            "Георгий","Маргарита","Вадим","Софья","Иван","Оксана","Даниил","Ирина",
            "Пётр","Галина","Леонид","Арина","Виталий","Лидия","Яков","Диана",
            "Эдуард","Валентина","Богдан","Алиса","Захар","Нина"]
bg_last = ["Комаров","Панов","Ефимова","Соловьёв","Власова","Куликов","Попова","Семёнов",
           "Крылова","Макаров","Ильина","Жуков","Быкова","Лазарев","Антонова","Фролов",
           "Гусева","Романов","Титова","Беляев","Рогов","Данилова","Барсуков","Котова",
           "Медведев","Сергеева","Шестаков","Фомина","Голубев","Борисова","Никулин","Суханова",
           "Третьяков","Зайцева","Калашников","Вишнякова","Щербаков",
           "Тихонова","Цветков","Бурова","Князев","Архипова"]
background = []
bg_count = min(len(bg_first), len(bg_last))
for i in range(bg_count):
    fn, ln = bg_first[i], bg_last[i]
    sn = f"{ln.lower().replace('ё','e')}_{fn[0].lower()}"
    background.append({
        "firstName": fn, "lastName": ln, "screenName": sn,
        "followersCount": random.randint(30, 450),
        "role": "background",
        "position": random.choice(["neutral", "neutral", "neutral", "positive", "negative"]),
        "topic_posts": random.randint(1, 3),
        "bg_posts": random.randint(2, 6),
    })

# ── CLOSED (5) ──
closed = [
    {"firstName": "Алексей", "lastName": "Тёмный", "screenName": "dark_alex", "followersCount": 150, "isClosed": True},
    {"firstName": "Марина", "lastName": "Скрытная", "screenName": "hidden_mar", "followersCount": 80, "isClosed": True},
    {"firstName": "Владимир", "lastName": "Закрытый", "screenName": "closed_vlad", "followersCount": 210, "isClosed": True},
    {"firstName": "Елена", "lastName": "Приватная", "screenName": "private_el", "followersCount": 45, "isClosed": True},
    {"firstName": "Сергей", "lastName": "Невидимый", "screenName": "invisible_s", "followersCount": 120, "isClosed": True},
]
for c in closed:
    c.update({"role": "closed", "position": "neutral", "topic_posts": 0, "bg_posts": 0})

# Merge all
all_authors_meta = leaders + giants + activists + background + closed
all_authors = []
for meta in all_authors_meta:
    vid = next_id()
    meta["vkId"] = vid
    all_authors.append({
        "vkId": vid,
        "firstName": meta["firstName"],
        "lastName": meta["lastName"],
        "screenName": meta.get("screenName", ""),
        "followersCount": meta["followersCount"],
        "isClosed": meta.get("isClosed", False),
    })

print(f"Total authors: {len(all_authors)}")

# ─── Topic post texts (CURRENT) ───
# Realistic VK-style texts about AI in Russia
topic_texts_positive = [
    "GigaChat 3 реально впечатлил. Пишет код на Python лучше, чем я после бессонной ночи",
    "Сбер вложит 350 млрд в ИИ в 2026. Это больше, чем весь бюджет некоторых стран на науку",
    "YandexGPT 5 наконец научился нормально понимать русский контекст. Попробуйте в Алисе",
    "Нейросети экономят мне 3 часа в день на рутине. Кто ещё не использует — вы теряете время",
    "Наша компания внедрила ИИ-ассистента для клиентской поддержки. Обрабатывает 86% обращений без оператора",
    "Midjourney + русский промпт через GigaChat = идеальный рабочий процесс для дизайнера",
    "На конференции AI Journey показали невероятные кейсы применения ИИ в медицине",
    "Считаю что Россия правильно делает, что развивает свои модели ИИ. Суверенитет важен",
    "Студенты моего курса по ML устроились в Яндекс и Сбер. Спрос на специалистов огромный",
    "Генеративный ИИ меняет маркетинг. За месяц сгенерировали 200 уникальных креативов",
    "Крайнов на DUMP рассказал про YandexGPT 5. Качество генерации выросло в 3 раза за год",
    "ИИ в образовании — это не замена учителя, а усиление. Персональный тьютор для каждого",
    "Впервые использовал GigaChat для анализа юридических документов. Экономия 5 часов",
    "Российский рынок ИИ вырос до 58 млрд рублей. Наконец-то мы в мировом тренде",
    "Попробовал YandexART 3 — генерирует картинки не хуже DALL-E, но понимает русские реалии",
    "ИИ-агенты Сбера обрабатывают 500 млрд событий в день по кибербезу. Масштаб поражает",
    "Подключил нейросеть к CRM — конверсия выросла на 23%. Цифры не врут",
    "Минцифры запускает национальный сервис данных для обучения ИИ. Правильный шаг",
    "Написал бота на YandexGPT API за вечер. Документация наконец стала нормальной",
    "ИИ помог нашей клинике сократить время диагностики на 40%. Это спасает жизни",
]

topic_texts_negative = [
    "Ашманов прав: пузырь ИИ скоро лопнет. Все эти миллиарды — на хайп, не на реальные задачи",
    "87% студентов пишут курсовые через ChatGPT. Это катастрофа для образования",
    "ИИ как инвалидная коляска для мозга. Перестаёте думать — перестаёте развиваться",
    "Сбер сократит 20% сотрудников по рекомендации ИИ. Алгоритм решает, кого уволить. Нормально?",
    "Касперская права: зарубежный ИИ — угроза нацбезопасности. Данные утекают на чужие серверы",
    "Deepfake видео политиков — реальная угроза. А у нас даже закона нормального нет",
    "Генеративный ИИ убивает творческие профессии. Дизайнеры, копирайтеры, переводчики — все под ударом",
    "Нейросеть написала мне бред, завёрнутый в красивые слова. А заказчик принял за экспертизу",
    "В штабе по развитию ИИ нет ни одного специалиста по ИИ. Одни чиновники. Браво",
    "ИИ нельзя пускать в госуправление. Одна ошибка алгоритма — и тысячи людей пострадают",
    "Регулирование ИИ в России защищает бизнес, а не граждан. Минцифры — лоббист корпораций",
    "Антиплагиат определяет только 25% сгенерированных текстов. Система образования в тупике",
    "Уволили коллегу потому что ИИ может делать его работу. Ему 52 года. Куда ему теперь?",
    "Все эти российские нейросети — жалкая копия ChatGPT с цензурой. Зачем тратить бюджет?",
    "ИИ усиливает неравенство. У корпораций — суперкомпьютеры, у обычных людей — ничего",
]

topic_texts_neutral = [
    "Сравнил GigaChat и YandexGPT на задаче суммаризации текстов. Результаты примерно одинаковые",
    "Прошёл курс по промпт-инжинирингу. Интересно, но пока не понял, как применить в своей работе",
    "Минцифры готовит 50 законопроектов по регулированию ИИ. Посмотрим, что из этого выйдет",
    "Рынок ИИ входит в фазу пост-хайпа. Бизнес начинает относиться к нему как к инструменту",
    "На работе внедряют ИИ-систему. Пока непонятно, упрощает она жизнь или усложняет",
    "Конференция по ИИ в Сколково: много докладов, мало конкретных кейсов внедрения",
    "Нейросети хороши для черновиков, но финальную работу всё равно делает человек",
    "Прочитал отчёт о состоянии ИИ в России. Цифры растут, но отставание от Китая и США сохраняется",
    "YandexGPT неплохо справляется с переводами, но для технических текстов пока слабоват",
    "ИИ меняет рынок труда. Не уничтожает профессии, а трансформирует. Адаптироваться придётся всем",
    "Тестирую разные нейросети для бизнеса уже полгода. Пока ROI неоднозначный",
    "Законопроект о маркировке ИИ-контента — разумная мера. Люди должны знать, кто написал текст",
    "Воронцов прочитал отличную лекцию по ML на МФТИ. Записи есть на ютубе, рекомендую",
    "Сбер показал 900 ИИ-агентов в продакшене. Вопрос — сколько из них реально полезны",
    "ИИ в медицине перспективен, но нужна сертификация. Нельзя доверять диагноз алгоритму без проверки",
]

# BASELINE (non-topic) post texts — everyday, work, hobbies
baseline_texts = [
    "Выходные на даче. Погода шикарная, шашлыки удались",
    "Дочитал книгу Пелевина. Не его лучшая, но всё равно цепляет",
    "Пробежал 10 км за 48 минут. Новый личный рекорд!",
    "Москва утром. Пробки как обычно, метро переполнено",
    "Готовлю презентацию для клиента. Дедлайн завтра, а слайды не готовы",
    "Сходили с семьёй в парк Зарядье. Красиво, но людей слишком много",
    "Наконец починил кран на кухне. Мужик должен уметь всё",
    "Посмотрел новый сезон Слова пацана. Ну такое, первый был лучше",
    "Купил абонемент в зал. С понедельника начну. Ну ладно, со следующего понедельника",
    "Кот опять спит на клавиатуре. Работать невозможно",
    "Отпуск в Сочи. Море тёплое, еда вкусная, цены конские",
    "Разбираю завалы на рабочем столе. Нашёл документы 2019 года",
    "День рождения дочки. 5 лет уже! Как время летит",
    "Перешёл на удалёнку. Плюсы: нет пробок. Минусы: холодильник рядом",
    "Прочитал статью про инвестиции. Ничего не понял, но звучит умно",
    "Заказал доставку из Вкусвилла. Привезли за 15 минут, молодцы",
    "Сегодня первый день весны. А за окном снег. Типичная Россия",
    "Смотрю матч Спартак-ЦСКА. Нервы как обычно",
    "Ремонт в квартире. Третий месяц. Кажется, это навсегда",
    "Нашёл старые фотографии из универа. Какие мы были молодые",
    "Рабочий созвон в зуме на 2 часа. Всё можно было решить в чате за 5 минут",
    "Варю борщ по маминому рецепту. Запах на всю квартиру",
    "Начал учить английский. Опять. В третий раз за год",
    "Собеседование прошло нормально. Ждём ответ",
    "Дождь третий день. Настроение соответствующее",
    "Обновил айфон. Теперь всё тормозит ещё больше. Спасибо, Apple",
    "Играю на гитаре вечерами. Соседи пока терпят",
    "Ходил на выставку в Третьяковку. Впечатлён Врубелем",
    "Записался на курсы вождения. Давно пора было",
    "Пятница вечер. Наконец-то можно ни о чём не думать",
]

# Comment texts — short, lively, VK-style
comment_texts_positive = [
    "Огонь! Сам пользуюсь, подтверждаю", "Класс, спасибо за инфу",
    "Наконец-то кто-то адекватный написал", "Подписываюсь под каждым словом",
    "+1, сам так делаю", "Круто! А можно ссылку?", "Топ!",
    "Лучший пост по теме что видел", "Согласен на все 100",
    "вау, не знал про это. спасибо!", "Красавчик, чётко разложил",
    "А где можно попробовать?", "Наконец прогресс!", "жиза",
]
comment_texts_negative = [
    "Бред какой-то", "Очередной хайп, через год забудут",
    "А людей кто будет кормить когда всех уволят?",
    "Не верю в эти цифры", "Ага, щас. Работает через раз",
    "Херня это всё", "кому это надо вообще", "ну-ну",
    "Опять пиарят свои поделки", "Страшно жить становится",
    "А кто за последствия отвечать будет?", "Фигня полная",
    "Лол, вы серьёзно?", "Не смешите мои тапки",
]
comment_texts_neutral = [
    "Интересно, надо подумать", "А есть пруфы?",
    "Хм, неоднозначно", "Сохранил, потом почитаю",
    "А как это работает на практике?", "ну ок",
    "Надо попробовать", "Спорный тезис, но имеет право на жизнь",
    "А что думаете про альтернативы?", "Первый раз слышу",
    "Норм", "Кто-нибудь пробовал?", "Ссылку бы",
]

# ─── Generate posts ───
posts = []
post_id = 10000
post_meta = []  # track for comments

for meta in all_authors_meta:
    if meta.get("isClosed"):
        continue
    vid = meta["vkId"]
    f_count = meta["followersCount"]

    # CURRENT (topic) posts
    for _ in range(meta["topic_posts"]):
        post_id += 1
        pos = meta["position"]
        if pos == "positive":
            text = random.choice(topic_texts_positive)
        elif pos == "negative":
            text = random.choice(topic_texts_negative)
        else:
            text = random.choice(topic_texts_neutral)

        # engagement proportional to followers
        base_views = int(f_count * random.uniform(0.3, 1.5))
        likes = max(1, int(base_views / random.uniform(15, 50)))
        reposts = max(0, int(likes / random.uniform(3, 10)))
        comments_cnt = max(0, int(likes / random.uniform(3, 20)))

        # some posts in communities, some on personal wall
        if random.random() < 0.3:
            owner_id = -random.choice(communities)["vkId"]
        else:
            owner_id = vid

        p = {
            "vkId": post_id,
            "ownerId": owner_id,
            "fromId": vid,
            "date": rnd_current(),
            "text": text,
            "likes": likes,
            "reposts": reposts,
            "comments": comments_cnt,
            "views": base_views,
            "containsMedia": random.random() < 0.3,
            "hasCopyHistory": False,
            "window": "CURRENT",
        }
        posts.append(p)
        if comments_cnt > 0:
            post_meta.append({"postVkId": post_id, "postOwnerId": owner_id, "comments_cnt": comments_cnt})

    # BASELINE (non-topic) posts
    for _ in range(meta["bg_posts"]):
        post_id += 1
        text = random.choice(baseline_texts)
        base_views = int(f_count * random.uniform(0.2, 1.0))
        likes = max(1, int(base_views / random.uniform(15, 50)))
        reposts = max(0, int(likes / random.uniform(5, 15)))
        comments_cnt = max(0, int(likes / random.uniform(5, 25)))

        owner_id = vid  # baseline posts are on personal wall

        posts.append({
            "vkId": post_id,
            "ownerId": owner_id,
            "fromId": vid,
            "date": rnd_baseline(),
            "text": text,
            "likes": likes,
            "reposts": reposts,
            "comments": comments_cnt,
            "views": base_views,
            "containsMedia": random.random() < 0.2,
            "hasCopyHistory": random.random() < 0.05,
            "window": "BASELINE",
        })

# Add a few off-topic CURRENT posts (for filter testing)
off_topic = [
    "Кто идёт на концерт Басты в пятницу? Есть лишний билет",
    "Рецепт шарлотки от бабушки. Три яйца, стакан сахара, стакан муки...",
    "Прошёл Baldur's Gate 3 на тактике. 200 часов жизни потрачены не зря",
    "Продаю велосипед Stels, почти новый, 15000р. Самовывоз м. Тушинская",
]
for text in off_topic:
    post_id += 1
    vid = random.choice([a["vkId"] for a in all_authors_meta if not a.get("isClosed")])
    posts.append({
        "vkId": post_id,
        "ownerId": vid,
        "fromId": vid,
        "date": rnd_current(),
        "text": text,
        "likes": random.randint(2, 30),
        "reposts": random.randint(0, 5),
        "comments": random.randint(0, 10),
        "views": random.randint(50, 500),
        "containsMedia": random.random() < 0.5,
        "hasCopyHistory": False,
        "window": "CURRENT",
    })

current_count = sum(1 for p in posts if p["window"] == "CURRENT")
baseline_count = sum(1 for p in posts if p["window"] == "BASELINE")
print(f"Posts: {len(posts)} (CURRENT: {current_count}, BASELINE: {baseline_count})")

# ─── Generate comments ───
comments = []
comment_id = 50000

# Generate comments under topic posts that have comments > 0
# Use a pool of commenter IDs (mix of known authors + unknown users)
known_ids = [a["vkId"] for a in all_authors_meta if not a.get("isClosed")]
unknown_commenter_ids = [random.randint(10000000, 800000000) for _ in range(30)]
all_commenter_ids = known_ids + unknown_commenter_ids

for pm in post_meta:
    n_comments = min(pm["comments_cnt"], random.randint(1, 8))
    for _ in range(n_comments):
        comment_id += 1
        sentiment = random.choices(["positive", "neutral", "negative"], weights=[30, 45, 25])[0]
        if sentiment == "positive":
            text = random.choice(comment_texts_positive)
        elif sentiment == "negative":
            text = random.choice(comment_texts_negative)
        else:
            text = random.choice(comment_texts_neutral)

        comments.append({
            "vkId": comment_id,
            "postVkId": pm["postVkId"],
            "postOwnerId": pm["postOwnerId"],
            "fromId": random.choice(all_commenter_ids),
            "date": rnd_current(),
            "text": text,
            "likes": random.randint(0, 15),
        })

print(f"Comments: {len(comments)}")

# ─── Assemble dataset ───
dataset = {
    "communities": communities,
    "authors": all_authors,
    "posts": posts,
    "comments": comments,
}

output_path = "dataset_ai_neural_networks.json"
with open(output_path, "w", encoding="utf-8") as f:
    json.dump(dataset, f, ensure_ascii=False, indent=2)

print(f"\nDataset written to {output_path}")
print(f"  Communities: {len(communities)}")
print(f"  Authors: {len(all_authors)} ({len([a for a in all_authors if a['isClosed']])} closed)")
print(f"  Posts: {len(posts)} (CURRENT: {current_count}, BASELINE: {baseline_count})")
print(f"  Comments: {len(comments)}")
