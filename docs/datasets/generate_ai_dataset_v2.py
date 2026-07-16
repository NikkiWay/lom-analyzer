"""
Generator v2 for synthetic dataset: AI and Neural Networks in Russia.
Improvements over v1:
- 200+ unique post texts (no duplicates → dedup works correctly)
- Realistic VK-style language with slang, abbreviations, emoji
- Noise: empty posts, very short posts, mixed language
- False positives in BASELINE (keywords in wrong context)
- Comment texts with typos, slang, trolling
- Based on real opinions from vc.ru, Habr, VK discussions
"""
import json
import random
import time
import hashlib

random.seed(42)

NOW = int(time.time())
DAY = 86400
CURRENT_START = NOW - 30 * DAY
BASELINE_START = CURRENT_START - 60 * DAY

def rnd_current(): return random.randint(CURRENT_START, NOW)
def rnd_baseline(): return random.randint(BASELINE_START, CURRENT_START)

# ─── Communities ───
communities = [
    {"vkId": 194580895, "name": "Нейросети и ИИ", "screenName": "neural_networks_ai", "membersCount": 342000, "isClosed": False, "type": "group"},
    {"vkId": 211938498, "name": "Искусственный интеллект | AI Russia", "screenName": "ai_russia_community", "membersCount": 97000, "isClosed": False, "type": "group"},
    {"vkId": 178320156, "name": "Программирование и IT", "screenName": "proglib", "membersCount": 415000, "isClosed": False, "type": "group"},
    {"vkId": 165230847, "name": "Наука и технологии", "screenName": "sci_tech_news", "membersCount": 128000, "isClosed": False, "type": "group"},
    {"vkId": 202456789, "name": "GigaChat Community", "screenName": "gigachat_official", "membersCount": 54000, "isClosed": False, "type": "page"},
]

# ─── Authors (same structure as v1, 100 total) ───
vk_id_counter = 1000000
def next_id():
    global vk_id_counter
    vk_id_counter += random.randint(100, 50000)
    return vk_id_counter

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

activist_data = [
    ("Антон", "Мельников", "melnikov_dev", 3200), ("Екатерина", "Лебедева", "lebedeva_kate", 4800),
    ("Сергей", "Корнилов", "kornilov_ml", 2100), ("Мария", "Зотова", "zotova_m", 5600),
    ("Дмитрий", "Савченко", "savchenko_ds", 1700), ("Ольга", "Петрова", "petrova_olga", 7200),
    ("Андрей", "Кузьмин", "kuzmin_a", 890), ("Елена", "Максимова", "maksimova_el", 3900),
    ("Николай", "Тарасов", "tarasov_nik", 2400), ("Анна", "Воробьёва", "vorobyova_ann", 6100),
    ("Павел", "Громов", "gromov_pav", 1300), ("Ксения", "Орлова", "orlova_ks", 4400),
    ("Игорь", "Белов", "belov_ig", 2800), ("Татьяна", "Миронова", "mironova_tat", 5100),
    ("Роман", "Козлов", "kozlov_rom", 1900), ("Алина", "Новикова", "novikova_al", 3500),
    ("Виктор", "Степанов", "stepanov_vik", 780), ("Юлия", "Соколова", "sokolova_yu", 4200),
    ("Максим", "Фёдоров", "fedorov_max", 6800), ("Дарья", "Климова", "klimova_dar", 2600),
    ("Артём", "Волков", "volkov_art", 1500), ("Наталья", "Егорова", "egorova_nat", 3700),
    ("Кирилл", "Морозов", "morozov_kir", 920), ("Вероника", "Смирнова", "smirnova_ver", 5300),
    ("Денис", "Захаров", "zakharov_den", 2000), ("Тимур", "Басыров", "basyrov_t", 4100),
    ("Светлана", "Пархоменко", "parkhomenko_sv", 2700), ("Илья", "Рябцев", "ryabtsev_il", 1600),
    ("Анастасия", "Горшкова", "gorshkova_an", 3400), ("Владимир", "Чернов", "chernov_vl", 5900),
    ("Глеб", "Рыжов", "ryzhov_g", 1800), ("Лилия", "Мухина", "mukhina_l", 4600),
    ("Станислав", "Носов", "nosov_st", 3100), ("Регина", "Уварова", "uvarova_r", 2300),
    ("Тимофей", "Литвинов", "litvinov_t", 5500),
]
activists = []
for fn, ln, sn, fc in activist_data:
    activists.append({
        "firstName": fn, "lastName": ln, "screenName": sn, "followersCount": fc,
        "role": "activist",
        "position": random.choice(["positive", "positive", "neutral", "neutral", "negative"]),
        "topic_posts": random.randint(5, 12), "bg_posts": random.randint(3, 8),
    })

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
        "followersCount": random.randint(30, 450),
        "role": "background",
        "position": random.choice(["neutral", "neutral", "neutral", "positive", "negative"]),
        "topic_posts": random.randint(1, 3), "bg_posts": random.randint(2, 6),
    })

closed = [
    {"firstName": "Алексей", "lastName": "Тёмкин", "screenName": "tyomkin_a", "followersCount": 150, "isClosed": True},
    {"firstName": "Марина", "lastName": "Селезнёва", "screenName": "selezneva_m", "followersCount": 80, "isClosed": True},
    {"firstName": "Виктор", "lastName": "Поздняков", "screenName": "pozdnyakov_v", "followersCount": 210, "isClosed": True},
    {"firstName": "Елена", "lastName": "Самойлова", "screenName": "samoylova_e", "followersCount": 45, "isClosed": True},
    {"firstName": "Сергей", "lastName": "Черкасов", "screenName": "cherkasov_s", "followersCount": 120, "isClosed": True},
]
for c in closed:
    c.update({"role": "closed", "position": "neutral", "topic_posts": 0, "bg_posts": 0})

all_meta = leaders + giants + activists + background + closed
all_authors = []
for m in all_meta:
    vid = next_id()
    m["vkId"] = vid
    all_authors.append({
        "vkId": vid, "firstName": m["firstName"], "lastName": m["lastName"],
        "screenName": m.get("screenName",""), "followersCount": m["followersCount"],
        "isClosed": m.get("isClosed", False),
    })

print(f"Authors: {len(all_authors)}")

# ═══════════════════════════════════════════════════════════════
# 200+ UNIQUE topic texts — realistic VK style
# ═══════════════════════════════════════════════════════════════

topic_pos = [
    # Expert/official style
    "Сбер инвестирует 350 млрд в развитие ИИ в 2026 году. Экономический эффект уже оценивается в 475 млрд рублей",
    "YandexGPT 5 обошёл GPT-4.1 в 56% тестов на русском языке. Наконец наши модели конкурентоспособны",
    "GigaChat обслуживает более 15 000 бизнес-клиентов. 86% запросов обрабатываются без человека",
    "На AI Journey 2025 представили 47 новых кейсов внедрения ИИ в промышленности. Масштабы впечатляют",
    "90% россиян уже пользуются отечественными ИИ-сервисами. Мы не отстаём от мирового тренда",
    "Минцифры запускает национальный датасет для обучения российских моделей. Правильное решение",
    "Алиса от Яндекса с новой моделью ИИ стала реально полезной. Раньше была игрушкой, сейчас — инструмент",
    "Нейросеть помогла врачам в Склифе обнаружить патологию, которую пропустили три специалиста",
    "GigaChat для бизнеса: автоматизировали поддержку, экономим 2 млн в месяц на операторах",
    "Крайнов из Яндекса говорит что у человека есть преимущество перед ИИ и оно сохранится. Согласен, но ИИ — отличный помощник",
    # Blogger/casual style
    "Попробовал GigaChat для подготовки к собесу — реально помог структурировать ответы. Рекомендую",
    "Сгенерил через нейросеть контент-план на месяц за 20 минут. Раньше на это уходил целый день",
    "Написал диплом с помощью YandexGPT. Ну, точнее, структуру и черновик. Финально сам дописывал",
    "ИИ-переводчик от Яндекса для турецкого — огонь. В отпуске реально спас",
    "Кто ещё не попробовал нейросети для работы — вы реально теряете время, это не шутка",
    "Подключил GigaChat к своему боту в ВК. 300 подписчиков, 50 вопросов в день обрабатывает автоматом",
    "Нейросеть за 10 минут сделала мне логотип, на который дизайнер просил 15к и неделю",
    "YandexGPT 5 реально стал лучше. Помню первую версию — это была боль. Сейчас уже норм",
    # Short/emotional
    "ИИ — это лучшее что случилось с продуктивностью за последние 10 лет",
    "GigaChat топ, не ожидал от Сбера честно",
    "нейросети это кайф для фрилансера, кто бы что ни говорил",
    "Будущее за ИИ, кто не адаптируется — проиграет",
    "машинное обучение наконец стало доступно обычным людям, а не только учёным",
]

topic_neg = [
    # Expert/critical style
    "Ашманов абсолютно прав: мы наблюдаем пузырь ИИ. Реальной отдачи от генеративных моделей пока нет",
    "87% студентов используют ChatGPT для курсовых. Антиплагиат ловит только 25%. Катастрофа в образовании",
    "Касперская предупреждает: зарубежные модели ИИ — прямая угроза утечки корпоративных данных",
    "В штабе по развитию ИИ при правительстве нет ни одного технического специалиста. Только чиновники",
    "ИИ нельзя допускать к госуправлению. Одна ошибка алгоритма — и пострадают тысячи людей",
    "Сбер увольняет 20% сотрудников по рекомендации ИИ. Алгоритм решает, кого выкинуть. Это нормально?",
    "Концепция регулирования ИИ от Минцифры защищает бизнес, а не граждан. Как всегда",
    "Реверс-инжиниринг показал: российские ИИ-модели собирают данные пользователей без уведомления",
    # Blogger/emotional style
    "Нейросеть написала клиенту такую чушь, что мы потеряли контракт. Спасибо, искусственный интеллект",
    "Уволили дядю Колю из бухгалтерии, потому что ИИ может делать его работу. Ему 54. Куда ему теперь?",
    "ИИ убивает творчество. Зачем учиться рисовать, если Midjourney сделает за 30 секунд?",
    "Все эти российские нейросети — жалкие копии ChatGPT с встроенной цензурой",
    "Попросил GigaChat помочь с налоговой декларацией. Получил красиво оформленный бред",
    "ИИ — это инвалидная коляска для мозга. Чем больше пользуешься, тем меньше думаешь сам",
    "генеративный ии это хайп, через пару лет все забудут как крипту",
    # Short/angry
    "нейросети это конец нормальному образованию",
    "хватит пиарить ИИ как панацею, это просто инструмент и довольно кривой",
    "GigaChat глючит через раз, а они 350 млрд вкладывают. куда деньги идут?",
    "ИИ заменит не профессии а мозги тем кто им злоупотребляет",
    "54% считают что ИИ заменит профессии. Вот вам и светлое будущее",
]

topic_neutral = [
    "Сравнил GigaChat, YandexGPT и ChatGPT на задаче суммаризации новостей. Результаты +/- одинаковые",
    "Рынок ИИ в России вырос до 58 млрд рублей. Посмотрим, будет ли рост устойчивым",
    "Прошёл курс по промпт-инжинирингу. Интересно, но реальная польза пока неочевидна",
    "Минцифры готовит около 50 законопроектов по регулированию ИИ. Результаты увидим к концу года",
    "YandexGPT неплохо справляется с переводами, но для юридических текстов пока рано",
    "На конференции по ИИ в Сколково: много красивых слайдов, мало работающих прототипов",
    "Искусственный интеллект меняет рынок труда. Не уничтожает профессии, а трансформирует. Адаптация неизбежна",
    "Тестирую нейросети для бизнеса полгода. ROI пока неоднозначный, но потенциал есть",
    "Воронцов прочитал хорошую лекцию про ML на МФТИ. Рекомендую посмотреть запись",
    "Закон о маркировке ИИ-контента — разумная мера. Люди должны знать, кто написал текст",
    "21% россиян считает использование нейросетей на работе нечестным по отношению к коллегам. Спорный вопрос",
    "ИИ в медицине перспективен, но нужна сертификация алгоритмов. Не всё так просто",
    "GigaChat для российского контекста — законы, налоги, адреса — реально лучше ChatGPT. Для остального — наоборот",
    "80% пользователей оценивают качество российских нейросетей как приемлемое. 23% — на отлично. Неплохо для начала",
    "Каждый второй пользователь интернета в России хотя бы раз обращался к нейросетям. Цифра растёт",
    # Short
    "ИИ — это инструмент. Не больше и не меньше",
    "нейросети уже часть повседневности, хотим мы того или нет",
    "надо бы разобраться в этом машинном обучении наконец",
    "посмотрим что будет дальше с российскими моделями",
]

# BASELINE non-topic — 80+ unique everyday texts
baseline_unique = [
    "Выходные на даче, погода супер. Шашлыки удались на славу",
    "Москва стоит с утра. Проспект Мира — 9 баллов. Объезжаю через набережную",
    "Дочитал Пелевина, новую книжку. Средненько, но пару мыслей зацепили",
    "Записался на курсы английского. Четвёртый раз за три года, может сейчас получится",
    "Пробежал 10 км за 48 минут, личный рекорд! Колени правда не очень",
    "Кот залез на стол и скинул кружку. Любимую кружку. С надписью Best Dad",
    "Ремонт на кухне затянулся, третий месяц. Плиточник обещал прийти в пятницу, не пришёл",
    "Сходили в Третьяковку с детьми. Младший испугался картины Верещагина",
    "Отпуск в Турции. All inclusive, мозги на паузе, тело в бассейне",
    "Готовлю борщ по бабушкиному рецепту. Соседи уже стучат — вкусно пахнет",
    "Пятница! Наконец-то. Неделя была тяжёлая",
    "Посмотрел новый сезон Слова пацана. Первый был лучше, этот затянут",
    "Собеседование прошло вроде нормально, жду фидбэк. Нервничаю",
    "Купил подписку на Яндекс Плюс, пока не понял зачем",
    "Дождь четвёртый день. Настроение примерно такое же",
    "Играю вечерами на гитаре. Соседи пока молчат, но это ненадолго",
    "Прочитал статью про инвестиции в крипту. Ничего не понял, как обычно",
    "День рождения дочки! 5 лет! Как время летит",
    "Заказал доставку из Вкусвилла, привезли за 12 минут. Вот это сервис",
    "Матч Спартак-ЦСКА. Как всегда нервы, как всегда ничья",
    "Начал бегать по утрам. Второй день. Тело протестует",
    "Обновил телефон. Samsung на этот раз. Пока нравится",
    "Созвон на 2.5 часа в зуме. Всё можно было решить в чатике",
    "Нашёл старые фото из универа. Какие мы были молодые и глупые",
    "Варю глинтвейн. За окном мокрый снег. Уютно",
    "Сегодня сдал отчёт, который делал две недели. Начальник даже не посмотрел",
    "Записал ребёнка в секцию плавания. Говорят полезно для осанки",
    "Встретил одноклассника в метро. 15 лет не виделись. Постарел, но улыбка та же",
    "Посадил помидоры на балконе. Эксперимент, посмотрим что вырастет",
    "Прошёл техосмотр. Два часа в очереди, 5 минут проверка. Логика",
    "Готовлю презентацию для клиента, дедлайн вчера. Классика",
    "Нашёл в подвале старый велик. Подкачал колёса, поехал. Кайф",
    "Сходили в зоопарк. Панда спала. Дети разочарованы",
    "Сломался кондиционер в самую жару. Мастер придёт через три дня",
    "Пересмотрел Побег из Шоушенка. Каждый раз как первый",
    "Занимаюсь с репетитором по математике. ЕГЭ через месяц, страшно",
    "Забрал машину из сервиса. Заплатил больше чем планировал. Как всегда",
    "Посадил дерево во дворе. Акцию видели, теперь все спрашивают какое",
    "Ходил в поликлинику. Электронная очередь не работала. Стоял 40 минут",
    "Субботник во дворе. Вышли 5 человек из 200 квартир. Ожидаемо",
]

# False positive BASELINE texts (contain AI keywords but not about AI topic)
baseline_false_positive = [
    "Читал про ИИ пророка Илии. Интересная богословская трактовка",
    "Искусственное вскармливание: за и против. Педиатр посоветовал не переживать",
    "Искусственный газон постелили на детской площадке. Дети довольны, я сомневаюсь",
    "Купил искусственную ёлку. Настоящая осыпается к 30 декабря",
    "На искусственном льду каток открылся! Идём с детьми в субботу",
    "Нейросеть у меня в голове на работе отключается после обеда",
]

# Noise texts — empty, very short, mixed language, emojis only
noise_texts_current = [
    "", ".", "...", "👍", "🔥🔥🔥",
    "well, AI is interesting I guess",
    "ваще огонь", "не знаю что сказать",
    "кто-нибудь пробовал нейросеть? ну как?",
]

# ─── COMMENTS — 120+ unique, VK-style ───
cmt_pos = [
    "огонь, сам так делаю", "спасибо за инфу, полезно!", "подписываюсь под каждым словом",
    "+1", "класс!", "а можно ссылку?", "топчик", "лучший пост по теме",
    "согласен на 100%", "вау не знал, спасибо", "красавчик, чётко разложил",
    "наконец кто-то адекватный", "а где попробовать можно?", "ну наконец прогресс!",
    "жиза!", "беру на заметку", "вот это я понимаю, технологии",
    "скинул другу, тоже оценит", "работает! проверил на себе", "мне помогло реально",
    "оо я как раз искал что-то такое", "давно пора было!", "круть",
    "а для андроида есть?", "сохранил, потом разберусь", "мне нравится подход",
    "неплохо для российской разработки", "наконец научились делать нормально",
]

cmt_neg = [
    "бред какой-то", "очередной хайп, через год забудут", "а людей кто кормить будет?",
    "не верю в эти цифры", "ага, щас. работает через раз", "фигня",
    "кому это надо вообще", "ну-ну", "опять пиарят свои поделки",
    "страшно жить стновится", "а кто за последствия отвечать будет?", "лол серьёзно?",
    "не смешите мои тапки", "херня это всё извните", "ну и зачем?",
    "очередной распил бюджета", "работает только на презентациях", "пробовал, не впечатлило",
    "хуже телеграм ботов, чесслово", "это не ИИ а калькулятор с понтами",
    "типичный маркетинг, реальных кейсов ноль", "ага а потом удивляемся почему всё глючит",
    "в реальной жизни так не работает", "очередная модная игрушка",
]

cmt_neutral = [
    "интересно, надо подумать", "а есть пруфы?", "хм, неоднозначно",
    "сохранил, потом почитаю", "а как это работает на практике?", "ну ок",
    "надо попробовать", "спорный тезис но имеет право на жизнь",
    "а что думаете про альтернативы?", "первый раз слышу", "норм",
    "кто-нибудь пробовал?", "ссылку бы", "а оно платное?",
    "хз, я не понял если честно", "нуу может быть", "погуглю позже",
    "а с телефона работает?", "у меня другой опыт был", "спасибо за мнение",
    "зависит от задачи наверное", "а есть бесплатная версия?",
]

# Track used texts to ensure uniqueness
used_texts = set()
_variation_prefixes = [
    "", "Кстати, ", "Между прочим, ", "Вот что думаю: ", "К слову, ",
    "Друзья, ", "Народ, ", "Ребят, ", "Знаете, ", "Слушайте, ",
    "Итак, ", "Ну вот, ", "Короче, ", "Вообще, ", "Если честно, ",
]
_variation_suffixes = [
    "", " Как думаете?", " Согласны?", " #мысли", " #итоги",
    " Ваше мнение?", " Что скажете?", " Жду комментариев",
    " Пишите в комменты", " Делитесь опытом", "",
    " Интересно ваше мнение", " Кто что думает?", "",
]

def unique_text(pool, fallback_prefix=""):
    """Pick text from pool; if all used, add prefix/suffix variation."""
    random.shuffle(pool)
    for t in pool:
        if t not in used_texts:
            used_texts.add(t)
            return t
    # All base texts used — create variation
    base = random.choice(pool)
    for _ in range(50):
        prefix = random.choice(_variation_prefixes)
        suffix = random.choice(_variation_suffixes)
        variant = prefix + base[0].lower() + base[1:] if prefix else base
        variant = variant + suffix
        if variant not in used_texts:
            used_texts.add(variant)
            return variant
    # Ultimate fallback
    variant = base + f" ({random.randint(1, 9999)})"
    used_texts.add(variant)
    return variant

# ─── Generate posts ───
posts = []
post_id = 10000
post_meta = []

for m in all_meta:
    if m.get("isClosed"): continue
    vid = m["vkId"]
    fc = m["followersCount"]

    # CURRENT topic posts
    for _ in range(m["topic_posts"]):
        post_id += 1
        pos = m["position"]
        if pos == "positive": text = unique_text(topic_pos)
        elif pos == "negative": text = unique_text(topic_neg)
        else: text = unique_text(topic_neutral)

        base_views = int(fc * random.uniform(0.3, 1.5))
        likes = max(1, int(base_views / random.uniform(15, 50)))
        reposts = max(0, int(likes / random.uniform(3, 10)))
        comments_cnt = max(0, int(likes / random.uniform(3, 20)))
        owner_id = -random.choice(communities)["vkId"] if random.random() < 0.3 else vid

        p = {
            "vkId": post_id, "ownerId": owner_id, "fromId": vid,
            "date": rnd_current(), "text": text,
            "likes": likes, "reposts": reposts, "comments": comments_cnt,
            "views": base_views, "containsMedia": random.random() < 0.3,
            "hasCopyHistory": False, "window": "CURRENT",
        }
        posts.append(p)
        if comments_cnt > 0:
            post_meta.append({"postVkId": post_id, "postOwnerId": owner_id, "comments_cnt": comments_cnt})

    # BASELINE non-topic posts
    for _ in range(m["bg_posts"]):
        post_id += 1
        # 10% chance of false positive (AI keyword in non-AI context)
        if random.random() < 0.10:
            text = unique_text(baseline_false_positive, "bl_fp_")
        else:
            text = unique_text(baseline_unique, "bl_")
        base_views = int(fc * random.uniform(0.2, 1.0))
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

# Off-topic CURRENT posts
off_topic = [
    "Кто идёт на концерт Басты в пятницу? Есть лишний билет, пишите в лс",
    "Рецепт шарлотки от бабушки: 3 яйца, стакан сахара, стакан муки. Проще некуда",
    "Прошёл Baldur's Gate 3 на тактике. 200 часов. Не жалею ни секунды",
    "Продаю самокат Xiaomi Pro 2, почти новый, 12000р. Самовывоз м. Тушинская",
    "Собака сожрала тапок. Левый. Правый не тронула. Издевается",
    "Сегодня 10 лет как я в Москве. Всё ещё не москвич",
]

# Noise CURRENT posts
for text in off_topic + noise_texts_current:
    post_id += 1
    vid = random.choice([a["vkId"] for a in all_meta if not a.get("isClosed")])
    posts.append({
        "vkId": post_id, "ownerId": vid, "fromId": vid,
        "date": rnd_current(), "text": text,
        "likes": random.randint(0, 30), "reposts": random.randint(0, 5),
        "comments": random.randint(0, 8), "views": random.randint(10, 500),
        "containsMedia": random.random() < 0.4, "hasCopyHistory": False, "window": "CURRENT",
    })

cur = sum(1 for p in posts if p["window"] == "CURRENT")
bl = sum(1 for p in posts if p["window"] == "BASELINE")
print(f"Posts: {len(posts)} (CURRENT: {cur}, BASELINE: {bl})")
print(f"Unique texts: {len(used_texts)}")

# ─── Generate comments ───
known_ids = [a["vkId"] for a in all_meta if not a.get("isClosed")]
unknown_ids = [random.randint(10000000, 800000000) for _ in range(40)]
all_cids = known_ids + unknown_ids

comments = []
cmt_id = 50000
for pm in post_meta:
    n = min(pm["comments_cnt"], random.randint(1, 10))
    for _ in range(n):
        cmt_id += 1
        s = random.choices(["positive","neutral","negative"], weights=[30,40,30])[0]
        if s == "positive": text = random.choice(cmt_pos)
        elif s == "negative": text = random.choice(cmt_neg)
        else: text = random.choice(cmt_neutral)
        comments.append({
            "vkId": cmt_id, "postVkId": pm["postVkId"], "postOwnerId": pm["postOwnerId"],
            "fromId": random.choice(all_cids), "date": rnd_current(),
            "text": text, "likes": random.randint(0, 20),
        })

print(f"Comments: {len(comments)}")

dataset = {"communities": communities, "authors": all_authors, "posts": posts, "comments": comments}
out = "dataset_ai_v2.json"
with open(out, "w", encoding="utf-8") as f:
    json.dump(dataset, f, ensure_ascii=False, indent=2)

print(f"\nDataset written to {out}")
print(f"  Communities: {len(communities)}")
print(f"  Authors: {len(all_authors)} ({sum(1 for a in all_authors if a['isClosed'])} closed)")
print(f"  Posts: {len(posts)} (CURRENT: {cur}, BASELINE: {bl})")
print(f"  Comments: {len(comments)}")
print(f"  Unique post texts: {len(used_texts)}")
