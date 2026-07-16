"""
НАЗНАЧЕНИЕ
LOM Analyzer — Python NLP Sidecar (отдельный микросервис обработки текста на
FastAPI + uvicorn). Это «sidecar»-процесс, который запускается рядом с основным
Kotlin/Compose-приложением и берёт на себя всю русскоязычную NLP-обработку,
недоступную в JVM «из коробки»: лемматизацию, sentiment-анализ, извлечение
именованных сущностей (NER), построение векторных embedding и семантическое
сравнение текстов. Закрывает этап препроцессинга и тематической фильтрации
пайплайна (этапы 3–4): результаты этих endpoint'ов использует Kotlin-сторона
при расчёте позиции автора (Pos), отклика аудитории (Resp) и при двухпроходной
тематической фильтрации L2 (RuBERT cosine).

ЧТО ВНУТРИ
- Разбор аргументов командной строки (--port, --secret, --model-cache-dir).
- Ленивая (lazy) загрузка NLP-моделей в словарь _models: модель скачивается и
  инициализируется только при первом обращении к соответствующему endpoint'у,
  чтобы не тратить память и время старта на неиспользуемые модели.
- Проверка авторизации verify_token по заголовку X-Auth-Token.
- pydantic-схемы запросов: TextRequest, SentimentRequest, BatchTextRequest,
  SimilarityRequest.
- HTTP-endpoint'ы FastAPI: /health, /lemmatize, /language/detect,
  /sentiment/dostoevsky, /batch/lemmatize, /batch/sentiment,
  /semantic_similarity, /embed, /ner/natasha.
- Точка входа __main__: запуск uvicorn на 127.0.0.1 (только локальные обращения).

ПОЧЕМУ ОБРАБОТЧИКИ ОБЪЯВЛЕНЫ ОБЫЧНЫМ def, А НЕ async def
Обработчики моделей выполняют синхронную работу, упирающуюся в процессор:
загрузку модели при первом обращении и сам вывод (inference). FastAPI исполняет
async def прямо в цикле событий, поэтому такой обработчик блокирует весь
сервис — на время первой загрузки модели (сотни мегабайт) sidecar переставал
отвечать вообще, включая /health, а накопившиеся запросы отваливались по
таймауту. Объявленные обычным def обработчики FastAPI уводит в пул потоков, и
цикл событий остаётся свободным. /health намеренно оставлен async def: работы он
не делает и должен отвечать немедленно.

МОДЕЛИ NLP И ИХ НАЗНАЧЕНИЕ
- transformers pipeline (seara/rubert-tiny2-russian-sentiment) — sentiment
  русскоязычных текстов (positive/neutral/negative); работает на CPU.
- pymorphy3 — морфологический анализатор, приводит слова к нормальной форме
  (лемматизация) для последующего сопоставления с тематическими n-граммами.
- natasha (Segmenter + NewsNERTagger на NewsEmbedding) — сегментация текста и
  извлечение именованных сущностей (персоны, организации, локации).
- sentence_transformers (cointegrated/rubert-tiny2) — построение векторных
  embedding и расчёт косинусной близости для семантического сравнения текстов.
- langdetect — определение языка текста с оценкой уверенности.

ФРЕЙМВОРКИ И БИБЛИОТЕКИ
- FastAPI — декларативное описание HTTP-API (декораторы @app.post / @app.get),
  автоматическая валидация тел запросов по pydantic-схемам.
- pydantic (BaseModel) — описание и валидация структур входных данных.
- uvicorn — ASGI-сервер, на котором поднимается FastAPI-приложение.

БЕЗОПАСНОСТЬ / СВЯЗИ
- Каждый endpoint требует заголовок X-Auth-Token, равный значению --secret;
  тот же общий секрет передаёт Kotlin-клиент (Ktor) при каждом вызове.
- Сервис слушает только loopback (127.0.0.1) — снаружи недоступен.

Usage:
    python main.py --port 8300 --secret <hex> [--model-cache-dir ./models]
"""
import argparse
import functools
import os
import sys
from contextlib import asynccontextmanager

from fastapi import FastAPI, Header, HTTPException, Request
from pydantic import BaseModel

# ---------------------------------------------------------------------------
# Аргументы командной строки (CLI args)
# ---------------------------------------------------------------------------
# Сервис конфигурируется только через CLI: порт, общий секрет и каталог кэша
# моделей. parse_known_args игнорирует посторонние аргументы (например, те,
# что может добавить окружение запуска), не падая с ошибкой.
parser = argparse.ArgumentParser()
parser.add_argument("--port", type=int, default=8300)          # порт HTTP-сервера
parser.add_argument("--secret", type=str, required=True)        # общий секрет для X-Auth-Token (обязателен)
parser.add_argument("--model-cache-dir", type=str, default="./models")  # куда складывать скачанные модели
args, _unknown = parser.parse_known_args()

# Секрет, с которым будут сравниваться входящие заголовки X-Auth-Token.
SHARED_SECRET = args.secret
# Указываем sentence-transformers, куда кэшировать веса моделей (через env-переменную).
os.environ["SENTENCE_TRANSFORMERS_HOME"] = args.model_cache_dir

# ---------------------------------------------------------------------------
# Ленивая загрузка моделей (lazy model loading)
# ---------------------------------------------------------------------------
# Общий кэш уже инициализированных моделей: ключ — имя модели, значение — объект
# модели/pipeline. Каждая get_*-функция создаёт модель при первом вызове и потом
# возвращает её из кэша. Это экономит память и ускоряет старт, ведь конкретный
# запуск использует не все модели сразу.
_models = {}


def get_sentiment_pipeline():
    """Возвращает (создавая при первом вызове) transformers-pipeline для sentiment.

    Модель seara/rubert-tiny2-russian-sentiment классифицирует русский текст на
    positive/neutral/negative. top_k=None — вернуть оценки по всем классам;
    device=-1 — принудительно CPU (без GPU).
    """
    if "sentiment" not in _models:
        from transformers import pipeline
        _models["sentiment"] = pipeline(
            "text-classification",
            model="seara/rubert-tiny2-russian-sentiment",
            top_k=None,
            device=-1,  # CPU
        )
    return _models["sentiment"]


def get_morph():
    """Возвращает морфологический анализатор pymorphy3 для лемматизации.

    MorphAnalyzer умеет приводить слово к нормальной форме (лемме) — используется
    в endpoint'ах /lemmatize и /batch/lemmatize.
    """
    if "morph" not in _models:
        import pymorphy3
        _models["morph"] = pymorphy3.MorphAnalyzer()
    return _models["morph"]


def get_natasha():
    """Возвращает набор компонентов natasha для сегментации и NER.

    Собирает Segmenter (разбиение на токены/предложения), MorphVocab и
    NewsNERTagger (распознавание именованных сущностей) поверх эмбеддингов
    NewsEmbedding. Возвращает словарь с готовыми segmenter/morph_vocab/ner_tagger.
    """
    if "natasha" not in _models:
        from natasha import (Segmenter, MorphVocab, NewsEmbedding,
                             NewsNERTagger, NamesExtractor, Doc)
        segmenter = Segmenter()              # сегментация текста на токены/предложения
        morph_vocab = MorphVocab()           # морфологический словарь natasha
        emb = NewsEmbedding()                # предобученные новостные эмбеддинги
        ner_tagger = NewsNERTagger(emb)      # NER-теггер поверх этих эмбеддингов
        _models["natasha"] = {
            "segmenter": segmenter,
            "morph_vocab": morph_vocab,
            "ner_tagger": ner_tagger,
        }
    return _models["natasha"]


def get_embedder():
    """Возвращает SentenceTransformer (rubert-tiny2) для построения embedding.

    Модель cointegrated/rubert-tiny2 переводит текст в плотный вектор; используется
    в endpoint'ах /embed (вектор) и /semantic_similarity (косинусная близость).
    """
    if "embedder" not in _models:
        from sentence_transformers import SentenceTransformer
        _models["embedder"] = SentenceTransformer("cointegrated/rubert-tiny2")
    return _models["embedder"]


def get_langdetect():
    """Возвращает модуль langdetect для определения языка текста.

    Здесь кэшируется сам импортированный модуль (а не объект модели), так как
    langdetect использует функции уровня модуля.
    """
    if "langdetect" not in _models:
        import langdetect
        _models["langdetect"] = langdetect
    return _models["langdetect"]


# ---------------------------------------------------------------------------
# Авторизация (Auth)
# ---------------------------------------------------------------------------
def verify_token(x_auth_token: str = Header(...)):
    """Проверяет совпадение заголовка X-Auth-Token с общим секретом.

    Если токен не совпадает с SHARED_SECRET — выбрасывает HTTP 403. Вызывается
    первой строкой в каждом endpoint'е, обеспечивая, что обращаться к sidecar
    может только доверенный Kotlin-клиент с тем же секретом.
    """
    if x_auth_token != SHARED_SECRET:
        raise HTTPException(status_code=403, detail="Invalid token")


# ---------------------------------------------------------------------------
# Приложение (App)
# ---------------------------------------------------------------------------
# Экземпляр FastAPI-приложения, к которому декораторами привязываются endpoint'ы.
app = FastAPI(title="LOM NLP Sidecar")


# ---------------------------------------------------------------------------
# pydantic-схемы запросов (request models)
# ---------------------------------------------------------------------------
class TextRequest(BaseModel):
    """Запрос с одним текстом (для лемматизации, embedding, NER, определения языка)."""
    text: str


class SentimentRequest(BaseModel):
    """Запрос на sentiment-анализ: текст и режим (mode по умолчанию 'dostoevsky')."""
    text: str
    mode: str = "dostoevsky"


class BatchTextRequest(BaseModel):
    """Пакетный запрос: список текстов для массовой обработки за один вызов."""
    texts: list[str]

class SimilarityRequest(BaseModel):
    """Запрос на семантическое сравнение двух текстов a и b (косинусная близость)."""
    a: str
    b: str


# ---------------------------------------------------------------------------
# Endpoint'ы
# ---------------------------------------------------------------------------
@app.get("/health")
async def health(x_auth_token: str = Header(...)):
    """Проверка живости сервиса. Возвращает {'status': 'ok'} при валидном токене.

    Используется Kotlin-клиентом, чтобы убедиться, что sidecar запущен и отвечает,
    прежде чем слать рабочие запросы.
    """
    verify_token(x_auth_token)
    return {"status": "ok"}


@app.post("/lemmatize")
def lemmatize(req: TextRequest, x_auth_token: str = Header(...)):
    """Лемматизация одного текста через pymorphy3.

    Разбивает текст по пробелам и для каждого слова берёт нормальную форму
    (первый, наиболее вероятный разбор). Возвращает {'lemmas': [...]}.
    """
    verify_token(x_auth_token)
    morph = get_morph()
    words = req.text.split()  # простое разбиение по пробелам на токены
    # Для каждого слова берём нормальную форму первого (самого вероятного) разбора
    lemmas = [morph.parse(w)[0].normal_form for w in words]
    return {"lemmas": lemmas}


@app.post("/language/detect")
def detect_language(req: TextRequest, x_auth_token: str = Header(...)):
    """Определение языка текста через langdetect.

    Возвращает наиболее вероятный язык и его уверенность [0..1]. При любой ошибке
    (например, пустой/нечитаемый текст) безопасно возвращает 'unknown' с 0.0.
    """
    verify_token(x_auth_token)
    ld = get_langdetect()
    try:
        results = ld.detect_langs(req.text)  # список языков с вероятностями, по убыванию
        top = results[0]                     # самый вероятный язык
        return {"language": top.lang, "confidence": round(top.prob, 4)}
    except Exception:
        # langdetect бросает исключение на пустых/слишком коротких строках — гасим его
        return {"language": "unknown", "confidence": 0.0}


def _distribution(scores) -> dict:
    """Собирает ответ по одному тексту: метка-победитель и полное распределение.

    Распределение возвращается целиком, потому что метка-победитель теряет силу
    склонности: у сдержанного текста может быть neutral 0.80 при positive 0.15,
    и по одной метке «neutral» этот перевес уже не восстановить. Оценки по осям
    позиции автора и отклика аудитории усредняют именно вероятности.
    """
    top = max(scores, key=lambda r: r["score"])
    by_label = {r["label"].lower(): r["score"] for r in scores}
    return {
        "label": top["label"].lower(),
        "score": round(top["score"], 4),
        "positive": round(by_label.get("positive", 0.0), 4),
        "neutral": round(by_label.get("neutral", 0.0), 4),
        "negative": round(by_label.get("negative", 0.0), 4),
    }


@app.post("/sentiment/dostoevsky")
def sentiment(req: SentimentRequest, x_auth_token: str = Header(...)):
    """Sentiment-анализ одного текста (rubert-tiny2-russian-sentiment).

    Несмотря на имя пути (dostoevsky — историческое название режима), под капотом
    используется transformers-pipeline. Возвращает метку и её уверенность.
    """
    verify_token(x_auth_token)
    pipe = get_sentiment_pipeline()
    # Обрезаем до 2000 символов: модель ограничена ~512 токенами
    text = req.text[:2000]
    # results — список словарей {label, score} по всем классам (top_k=None)
    return _distribution(pipe(text)[0])


@app.post("/batch/lemmatize")
def batch_lemmatize(req: BatchTextRequest, x_auth_token: str = Header(...)):
    """Пакетная лемматизация списка текстов через pymorphy3.

    Та же логика, что и /lemmatize, но за один вызов обрабатывается список текстов.
    Возвращает {'results': [[леммы текста 1], [леммы текста 2], ...]}.
    """
    verify_token(x_auth_token)
    morph = get_morph()
    results = []
    for text in req.texts:
        words = text.split()
        lemmas = [morph.parse(w)[0].normal_form for w in words]
        results.append(lemmas)
    return {"results": results}


@app.post("/batch/sentiment")
def batch_sentiment(req: BatchTextRequest, x_auth_token: str = Header(...)):
    """Пакетный sentiment-анализ списка текстов.

    Каждый текст обрезается до 2000 символов; pipeline обрабатывает весь список
    нативно (батчем). Для каждого текста возвращается метка-победитель и её оценка.
    """
    verify_token(x_auth_token)
    pipe = get_sentiment_pipeline()
    truncated = [t[:2000] for t in req.texts]  # ограничиваем длину каждого текста
    # Pipeline принимает список на вход и считает sentiment пакетом — это быстрее
    all_results = pipe(truncated)
    return {"results": [_distribution(res) for res in all_results]}


@app.post("/semantic_similarity")
def semantic_similarity(req: SimilarityRequest, x_auth_token: str = Header(...)):
    """Семантическая близость двух текстов через косинус их embedding.

    Используется в двухпроходной тематической фильтрации (L2): RuBERT-эмбеддинги
    a и b, затем cos_sim. Возвращает {'similarity': значение в [-1..1]}.
    """
    verify_token(x_auth_token)
    embedder = get_embedder()
    from sentence_transformers.util import cos_sim
    emb_a = embedder.encode(req.a)            # вектор первого текста
    emb_b = embedder.encode(req.b)            # вектор второго текста
    sim = float(cos_sim(emb_a, emb_b)[0][0])  # косинусная близость векторов
    return {"similarity": round(sim, 4)}


@app.post("/embed")
def embed(req: TextRequest, x_auth_token: str = Header(...)):
    """Построение embedding-вектора текста (rubert-tiny2).

    Возвращает {'vector': [...]} — плотный вектор, который Kotlin-сторона может
    кэшировать и сравнивать самостоятельно (например, с центроидом темы).
    """
    verify_token(x_auth_token)
    embedder = get_embedder()
    vector = embedder.encode(req.text).tolist()  # numpy-вектор -> обычный список для JSON
    return {"vector": vector}


@app.post("/ner/natasha")
def ner(req: TextRequest, x_auth_token: str = Header(...)):
    """Извлечение именованных сущностей (NER) через natasha.

    Сегментирует текст и помечает сущности (персоны/организации/локации).
    Возвращает список сущностей с текстом, типом и позициями (start/end) в строке.
    """
    verify_token(x_auth_token)
    from natasha import Doc, Segmenter
    n = get_natasha()
    doc = Doc(req.text)
    doc.segment(n["segmenter"])      # разбиваем текст на токены/предложения
    doc.tag_ner(n["ner_tagger"])     # размечаем именованные сущности
    entities = []
    for span in doc.ner.spans:
        # Каждую найденную сущность описываем текстом, типом и границами в строке
        entities.append({
            "text": span.text,
            "type": span.type,
            "start": span.start,
            "end": span.stop,
        })
    return {"entities": entities}


# ---------------------------------------------------------------------------
# Точка входа (Main)
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    import uvicorn
    # Поднимаем ASGI-сервер только на loopback (127.0.0.1) — снаружи недоступно;
    # workers=1, т.к. модели держатся в памяти одного процесса (общий кэш _models).
    #
    # access_log=False: журнал обращений не нужен — все запросы идут от одного
    # локального клиента, который ведёт собственный лог. При этом строка на каждый
    # запрос за сессию даёт тысячи строк вывода, а вывод sidecar пишется в файл на
    # стороне вызывающего (PythonServiceManager) и только замусоривался бы.
    uvicorn.run(app, host="127.0.0.1", port=args.port, workers=1, access_log=False)
