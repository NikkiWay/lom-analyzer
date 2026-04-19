import json
import hashlib

def det(seed):
    return int(hashlib.md5(str(seed).encode()).hexdigest(), 16)

eco = [
    "Загрязнение воздуха в городе растёт.",
    "Экология реки вызывает тревогу.",
    "Выбросы заводов превышают нормы.",
    "Вода в озере стала грязной.",
    "Экологи бьют тревогу из-за смога.",
    "Загрязнение почвы угрожает урожаю.",
    "Экология парка под угрозой.",
    "Воздух в центре стал хуже.",
    "Загрязнение реки продолжается.",
    "Экология района ухудшается.",
    "Выбросы CO2 растут каждый год.",
    "Вода из крана стала мутной.",
    "Экологическая катастрофа неизбежна.",
    "Загрязнение озера убивает рыбу.",
    "Экология побережья вызывает опасения.",
    "Воздух загрязнён промышленностью.",
    "Экология леса нуждается в защите.",
    "Загрязнение атмосферы бьёт рекорды.",
    "Вода в колодце стала опасной.",
    "Экология города требует внимания."
]
pol = [
    "Депутаты обсуждают новый закон.",
    "Выборы пройдут в следующем месяце.",
    "Бюджет города утверждён на год.",
    "Мэр выступил с отчётом.",
    "Новый закон вступает в силу.",
    "Партия набирает популярность.",
    "Реформа образования идёт медленно.",
    "Власти обещают перемены.",
    "Голосование прошло спокойно.",
    "Чиновники отчитались о расходах.",
    "Протест собрал сотни людей.",
    "Правительство приняло решение.",
    "Оппозиция критикует власть.",
    "Закон о налогах изменился.",
    "Губернатор посетил район."
]
day = [
    "Сегодня хорошая погода для прогулки.",
    "Готовлю ужин для всей семьи.",
    "Дети пошли в школу рано.",
    "Купил новую книгу в магазине.",
    "Кот опять спит на диване.",
    "Поехали на дачу на выходные.",
    "Утром пробежка в парке.",
    "Встретил друга в кафе.",
    "Погода испортилась к вечеру.",
    "Убрал квартиру за два часа.",
    "Посадил цветы на балконе.",
    "Ходили в кино всей семьёй.",
    "Завтра рано вставать на работу.",
    "Починил кран в ванной сам.",
    "Испёк пирог с яблоками."
]

cnames = ["Экологи города","Новости региона","Зелёный мир","Городская среда",
          "Чистый воздух","Природа и мы","Жизнь города","Экоактивисты",
          "Политика сегодня","Наш район"]
communities = []
for i in range(10):
    h = det(f"c{i}")
    communities.append({"id":i+1,"name":cnames[i],"members_count":500+(h%9500)})

dsrc = ["REPOSTER","COMMENTER","MENTIONED","CO_AUTHOR"]
authors = []
for i in range(50):
    a = i+1
    h = det(f"a{a}")
    authors.append({
        "id":a,
        "vk_id_hashed":hashlib.md5(f"vk{a}".encode()).hexdigest(),
        "followers_count":100+(h%49901),
        "is_closed":(h%10==0),
        "discovery_source":"SEED" if a<=30 else dsrc[(a-31)%4]
    })

base = 1717196400
sents = ["POSITIVE","NEGATIVE","NEUTRAL"]
opool = ["ORIGINAL"]*70+["REPOST_WITH_COMMENT"]*15+["PURE_REPOST"]*10+["DETECTED_COPY"]*5

posts = []
for i in range(500):
    pid = 1001+i
    fid = (i%50)+1
    h = det(f"p{pid}")
    d = i%60
    ts = base + d*86400 + (h%43200)
    rel = (h%5<2)
    if rel:
        t = eco[h%len(eco)]
    elif h%3==0:
        t = pol[h%len(pol)]
    else:
        t = day[h%len(day)]
    posts.append({
        "id":pid,"from_id":fid,"published_at":ts,
        "text_clean":t,
        "likes":5+(h%496),"reposts":h%101,"comments":1+(h%50),
        "own_text_length":40+(h%81),
        "has_copy_history":(h%10==0),
        "contains_media":(h%10<3),
        "ground_truth":{"is_topic_relevant":rel,"sentiment":sents[h%3],"originality_type":opool[h%100]}
    })

rpol = ["TOPIC_DRIVER","AUTHORITATIVE_LOM","SLEEPING_GIANT","BACKGROUND"]
gtr = [{"author_id":i+1,"expected_role":rpol[i%4],"confidence_note":"synthetic"} for i in range(20)]

gta = []
for i in range(10):
    do = 3+i*6
    gta.append({"date":f"2025-06-{1+do:02d}","community_id":(i%10)+1,
                "expected_type":["VOLUME_SPIKE","TONE_SHIFT_NEGATIVE"][i%2],
                "description":"synthetic anomaly"})

corpus = {
    "version":"test-corpus-2026-v2-extended",
    "description":"Extended synthetic corpus (500 posts, 50 authors, 10 communities)",
    "sha256":"to-be-computed",
    "metadata":{
        "posts_count":500,"authors_count":50,"communities_count":10,
        "period_from":"2025-06-01","period_to":"2025-07-31","region":"synthetic",
        "topic_config":{
            "primary_ngrams":["экология","загрязнение"],
            "secondary_ngrams":["воздух","вода"],
            "excluded_ngrams":["экологичный продукт"],
            "reference_texts":["Экологическая обстановка ухудшается.","Загрязнение воздуха угрожает здоровью."]
        }
    },
    "communities":communities,
    "authors":authors,
    "posts":posts,
    "ground_truth_roles":gtr,
    "ground_truth_anomalies":gta
}

out = json.dumps(corpus, ensure_ascii=False, separators=(',',':'))
print(f"Size: {len(out.encode('utf-8'))} bytes")

with open("C:/FinalCourseProject/src/test/resources/test_corpus_extended.json","w",encoding="utf-8") as f:
    f.write(out)
print("Done")
