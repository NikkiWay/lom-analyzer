"""
НАЗНАЧЕНИЕ
LOM Analyzer — автономный (offline) калькулятор метрик для синтетического
датасета examples/dataset_ai_v2.json. Это Python-«дублёр» основного
Kotlin-пайплайна: он целиком воспроизводит расчётную часть алгоритма (этапы
5–7) вне приложения, чтобы быстро пересчитать все метрики по готовому датасету
и получить JSON-результат для последующей загрузки в БД и отображения на
дашборде. Реализует все формулы Приложения E (E.1–E.4.6) и классификацию ролей.

ЧТО ВНУТРИ (карта файла)
- E.1 — робастная статистика: median, quantile, iqr.
- E.2 — MAD и M-оценка Хьюбера (huber_m_estimate) с константами MAD_C, HUBER_K.
- E.3 — bootstrap: одноуровневый (bootstrap_one_level, bootstrap_distribution)
  и двухуровневый (bootstrap_two_level, только для Resp_a).
- Sentiment: загрузка словаря sentilex (_load_sentilex) и словарный анализ
  тональности (_sentilex_sentiment) как fallback, плюс ML-вариант на dostoevsky.
- E.4 — 11 оценок по 4 осям: структурная (aud/age/er_bg), тематическая
  активность (top_vol/top_focus/reach), позиция автора (pos), отклик
  аудитории (er_top/resp).
- E.4.6 — робастная z-нормализация (z_normalize) и композиты (1/3,1/3,1/3).
- Классификация: assign_role (4 базовые роли по квадрантам), author_position,
  audience_response.
- main() — весь конвейер: загрузка датасета → sentiment → 11 оценок → bootstrap
  → z-нормализация и композиты → роли → оценка достаточности данных → выгрузка
  результата в examples/dataset_ai_v2_results.json и печать топ-авторов.

МЕТОДЫ / ФОРМУЛЫ
Робастная статистика (медиана, IQR, MAD c=1.4826), M-оценка Хьюбера (k=1.345,
tol=1e-6), bootstrap (одноуровневый B=1000; двухуровневый 300×100 только для
Resp_a), z-нормализация по медиане и IQR, равновесные композиты (OECD).
Подробности — docs/formulas.md (Приложение E), docs/algorithm.md.

БИБЛИОТЕКИ
Только стандартная библиотека (json, math, random, statistics, collections,
pathlib, re) плюс ОПЦИОНАЛЬНО dostoevsky (ML-sentiment); при его отсутствии
автоматически используется словарный fallback на sentilex_base.json.

СВЯЗИ
Вход: examples/dataset_ai_v2.json и src/main/resources/.../sentilex_base.json.
Выход: examples/dataset_ai_v2_results.json, который затем читают
load_results_to_db.py и generate_session_report.py.
"""

import json
import math
import random
import statistics
from collections import defaultdict
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor

# ── Dostoevsky для sentiment (если доступен) ──
# Пытаемся подключить ML-модель тональности dostoevsky (FastText). Если её нет в
# окружении — переходим на словарный fallback (sentilex), о чём сигнализирует флаг
# HAS_DOSTOEVSKY. Так скрипт работает и без тяжёлых ML-зависимостей.
try:
    from dostoevsky.tokenization import RegexTokenizer
    from dostoevsky.models import FastTextSocialNetworkModel
    _tokenizer = RegexTokenizer()                                    # токенизатор dostoevsky
    _sentiment_model = FastTextSocialNetworkModel(tokenizer=_tokenizer)  # ML-модель тональности
    HAS_DOSTOEVSKY = True
    print("[OK] dostoevsky loaded — using ML sentiment")
except Exception:
    HAS_DOSTOEVSKY = False
    print("[WARN] dostoevsky not available — using sentilex dictionary sentiment")

# ═══════════════════════════════════════════════════════
# E.1 — Robust Statistics
# ═══════════════════════════════════════════════════════

def median(values):
    """Медиана списка значений (E.1). Для пустого списка возвращает 0.0.

    При нечётной длине — центральный элемент, при чётной — среднее двух центральных.
    """
    if not values:
        return 0.0
    s = sorted(values)
    n = len(s)
    if n % 2 == 1:
        return s[n // 2]                      # нечётная длина — центральный элемент
    return (s[n // 2 - 1] + s[n // 2]) / 2.0  # чётная — среднее двух центральных

def quantile(values, p):
    """Квантиль уровня p (0..1) с линейной интерполяцией между соседними рангами."""
    s = sorted(values)
    n = len(s)
    if n == 1:
        return s[0]
    h = (n - 1) * p          # «дробная» позиция искомого квантиля
    lo = int(h)              # нижний целый индекс
    hi = min(lo + 1, n - 1)  # верхний индекс (с защитой от выхода за границы)
    frac = h - lo            # дробная часть для интерполяции
    return s[lo] + frac * (s[hi] - s[lo])

def iqr(values):
    """Межквартильный размах IQR = Q3 − Q1 (E.1). Знаменатель z-нормализации."""
    if len(values) < 2:
        return 0.0
    return quantile(values, 0.75) - quantile(values, 0.25)

# E.2 — MAD и M-оценка Хьюбера

MAD_C = 1.4826        # масштабная константа MAD для согласованности с σ нормального распределения
HUBER_K = 1.345       # порог Хьюбера k: при |r|>k наблюдение получает меньший вес
HUBER_TOL = 1e-6      # допуск сходимости IRLS
HUBER_MAX_ITER = 100  # максимум итераций IRLS

def mad(values):
    """Медианное абсолютное отклонение (MAD) с масштабом MAD_C=1.4826 (E.2).

    MAD = 1.4826 · median(|x − median(x)|) — робастная оценка разброса,
    устойчивая к выбросам. Возвращает 0.0 при менее чем двух значениях.
    """
    if len(values) < 2:
        return 0.0
    med = median(values)
    abs_devs = [abs(x - med) for x in values]  # абсолютные отклонения от медианы
    return MAD_C * median(abs_devs)

def huber_m_estimate(values, k=HUBER_K):
    """Робастная M-оценка центра по Хьюберу методом IRLS (E.2).

    Итеративно перевзвешивает наблюдения: близкие к центру получают вес 1,
    выбросы (|r|>k) — вес k/|r|, что ограничивает их влияние. Стартует от медианы,
    масштаб берётся из MAD; сходится по HUBER_TOL или после HUBER_MAX_ITER итераций.
    """
    if not values:
        return 0.0
    if len(values) == 1:
        return values[0]
    mu = median(values)            # начальное приближение центра — медиана
    s = max(mad(values), 1e-10)    # масштаб (MAD), защищённый от нуля
    for _ in range(HUBER_MAX_ITER):
        sum_w = 0.0
        sum_wx = 0.0
        for x in values:
            r = (x - mu) / s                          # стандартизованный остаток
            w = 1.0 if abs(r) <= k else k / abs(r)    # вес Хьюбера: режем влияние выбросов
            sum_w += w
            sum_wx += w * x
        if sum_w == 0:
            return mu
        mu_new = sum_wx / sum_w                        # новое взвешенное среднее
        # Проверка сходимости относительно масштаба самого mu
        if abs(mu_new - mu) < HUBER_TOL * (1.0 + abs(mu)):
            return mu_new
        mu = mu_new
    return mu

# ═══════════════════════════════════════════════════════
# E.3 — Bootstrap
# ═══════════════════════════════════════════════════════

B_ONE_LEVEL = 1000  # число итераций одноуровневого bootstrap
B_OUTER = 300       # число внешних итераций двухуровневого bootstrap
B_INNER = 100       # число внутренних итераций двухуровневого bootstrap

def bootstrap_one_level(values, statistic, B=B_ONE_LEVEL, seed=42):
    """Одноуровневый bootstrap-доверительный интервал 95% для произвольной статистики (E.3).

    B раз делает выборку с возвращением того же размера n, считает на ней statistic,
    затем берёт 2.5%- и 97.5%-квантили распределения как границы CI. seed фиксирует
    воспроизводимость. Возвращает None, если данных меньше двух точек.
    """
    if len(values) < 2:
        return None
    rng = random.Random(seed)
    n = len(values)
    results = []
    for _ in range(B):
        # Выборка с возвращением размера n (bootstrap-ресэмплинг)
        sample = [values[rng.randint(0, n - 1)] for _ in range(n)]
        results.append(statistic(sample))
    results.sort()
    lo = quantile(results, 0.025)  # нижняя граница 95% CI (перцентильный метод)
    hi = quantile(results, 0.975)  # верхняя граница 95% CI
    return {"ci_lo": round(lo, 6), "ci_hi": round(hi, 6), "procedure": "one_level", "B": B}

def bootstrap_distribution(labels, B=B_ONE_LEVEL, seed=42):
    """Одноуровневый bootstrap CI для долей тональности p+ и p− (E.3).

    Ресэмплит список меток sentiment и на каждой выборке считает долю POSITIVE и
    долю NEGATIVE; возвращает 95% CI отдельно для положительной и отрицательной
    доли. Используется для оценки Pos (позиция автора).
    """
    if len(labels) < 2:
        return None
    rng = random.Random(seed)
    n = len(labels)
    pos_results = []
    neg_results = []
    for _ in range(B):
        sample = [labels[rng.randint(0, n - 1)] for _ in range(n)]  # ресэмплинг меток
        pos_count = sum(1 for l in sample if l == "POSITIVE")        # сколько положительных
        neg_count = sum(1 for l in sample if l == "NEGATIVE")        # сколько отрицательных
        pos_results.append(pos_count / n)                            # доля p+
        neg_results.append(neg_count / n)                            # доля p−
    return {
        "positive_ci": {"ci_lo": round(quantile(pos_results, 0.025), 6),
                        "ci_hi": round(quantile(pos_results, 0.975), 6)},
        "negative_ci": {"ci_lo": round(quantile(neg_results, 0.025), 6),
                        "ci_hi": round(quantile(neg_results, 0.975), 6)},
        "procedure": "one_level_distribution", "B": B,
    }

def bootstrap_two_level(clusters, B_out=B_OUTER, B_in=B_INNER, seed=42):
    """Двухуровневый bootstrap для Resp_a — отклика аудитории (E.3).

    Учитывает кластерную структуру: комментарии сгруппированы по постам. Внешний
    уровень ресэмплит сами посты (кластеры), внутренний — комментарии внутри
    каждого выбранного поста. Так CI корректно отражает зависимость комментариев
    внутри одного поста. Применяется ТОЛЬКО к Resp_a (по диплому). Размер 300×100.
    """
    non_empty = [c for c in clusters if c]  # отбрасываем посты без комментариев
    if len(non_empty) < 2:
        return None
    rng = random.Random(seed)
    n = len(non_empty)
    pos_results = []
    neg_results = []
    for _ in range(B_out):
        # Внешний уровень: ресэмплинг постов (кластеров) с возвращением
        sampled_posts = [non_empty[rng.randint(0, n - 1)] for _ in range(n)]
        for _ in range(B_in):
            total_pos = 0
            total_neg = 0
            total_count = 0
            for post_comments in sampled_posts:
                m = len(post_comments)
                # Внутренний уровень: ресэмплинг комментариев внутри поста
                inner_sample = [post_comments[rng.randint(0, m - 1)] for _ in range(m)]
                total_pos += sum(1 for l in inner_sample if l == "POSITIVE")
                total_neg += sum(1 for l in inner_sample if l == "NEGATIVE")
                total_count += m
            if total_count > 0:
                # Доли по всем комментариям объединённой выборки этой итерации
                pos_results.append(total_pos / total_count)
                neg_results.append(total_neg / total_count)
    if not pos_results:
        return None
    return {
        "positive_ci": {"ci_lo": round(quantile(pos_results, 0.025), 6),
                        "ci_hi": round(quantile(pos_results, 0.975), 6)},
        "negative_ci": {"ci_lo": round(quantile(neg_results, 0.025), 6),
                        "ci_hi": round(quantile(neg_results, 0.975), 6)},
        "procedure": "two_level", "B_outer": B_out, "B_inner": B_in,
    }

# ═══════════════════════════════════════════════════════
# Sentiment Analysis (sentilex dictionary-based)
# ═══════════════════════════════════════════════════════

def _load_sentilex():
    """Загружает словарь тональности sentilex_base.json и строит наборы для поиска.

    Помимо полных слов (pos_full/neg_full) строит наборы «грубых основ» — первые
    4 и 5 символов слова. Это даёт примитивный учёт словоформ без полноценной
    лемматизации (большее покрытие). Пересекающиеся основы (встречаются и в
    положительных, и в отрицательных) удаляются, чтобы не вносить шум.
    Возвращает (pos_full, neg_full, pos_stems, neg_stems).
    """
    sentilex_path = Path(__file__).parent.parent / "src" / "main" / "resources" / "resources" / "sentilex_base.json"
    with open(sentilex_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    # Строим наборы основ: берём первые 4–6 символов как грубую основу для покрытия
    pos_stems = set()
    neg_stems = set()
    pos_full = set(w.lower() for w in data.get("positive", []))  # полные положительные слова
    neg_full = set(w.lower() for w in data.get("negative", []))  # полные отрицательные слова
    for w in pos_full:
        pos_stems.add(w[:4])
        if len(w) > 5:
            pos_stems.add(w[:5])
    for w in neg_full:
        neg_stems.add(w[:4])
        if len(w) > 5:
            neg_stems.add(w[:5])
    # Убираем неоднозначные основы, попавшие в оба набора (иначе ложные срабатывания)
    overlap = pos_stems & neg_stems
    pos_stems -= overlap
    neg_stems -= overlap
    return pos_full, neg_full, pos_stems, neg_stems

# Загружаем словарь один раз на уровне модуля (используется как fallback к dostoevsky)
_POS_FULL, _NEG_FULL, _POS_STEMS, _NEG_STEMS = _load_sentilex()

import re
_WORD_RE = re.compile(r'[а-яёa-z]+', re.IGNORECASE)  # извлечение слов (рус/лат буквы)

def _sentilex_sentiment(text):
    """Словарная тональность по sentilex_base.json со сравнением по основам.

    Считает баллы за положительные и отрицательные слова: точное совпадение даёт
    +2 (сильный сигнал), совпадение по 4-символьной основе — +1. Возвращает
    POSITIVE/NEGATIVE/NEUTRAL по тому, какой балл больше.
    """
    if not text:
        return "NEUTRAL"
    words = _WORD_RE.findall(text.lower())
    pos_score = 0
    neg_score = 0
    for w in words:
        if w in _POS_FULL:
            pos_score += 2  # точное совпадение = более сильный сигнал
        elif len(w) >= 4 and w[:4] in _POS_STEMS:
            pos_score += 1  # совпадение по основе = слабый сигнал
        if w in _NEG_FULL:
            neg_score += 2
        elif len(w) >= 4 and w[:4] in _NEG_STEMS:
            neg_score += 1
    # Итоговая метка — по перевесу баллов; при равенстве считаем нейтральным
    if pos_score > neg_score:
        return "POSITIVE"
    elif neg_score > pos_score:
        return "NEGATIVE"
    return "NEUTRAL"

def analyze_sentiment(text):
    """Тональность одного текста: ML (dostoevsky) при наличии, иначе словарь.

    Возвращает POSITIVE/NEGATIVE/NEUTRAL. Метки dostoevsky (skip/speech) сводятся
    к NEUTRAL. Пустой текст — NEUTRAL.
    """
    if not text or not text.strip():
        return "NEUTRAL"
    if HAS_DOSTOEVSKY:
        results = _sentiment_model.predict([text])
        if results:
            r = results[0]
            best = max(r, key=r.get)  # класс с максимальной вероятностью
            mapping = {"positive": "POSITIVE", "negative": "NEGATIVE",
                       "neutral": "NEUTRAL", "skip": "NEUTRAL", "speech": "NEUTRAL"}
            return mapping.get(best, "NEUTRAL")
    return _sentilex_sentiment(text)  # fallback на словарь

def batch_sentiment(texts):
    """Пакетная тональность списка текстов: dostoevsky одним вызовом, иначе словарь.

    Возвращает список меток POSITIVE/NEGATIVE/NEUTRAL по одной на каждый текст.
    """
    if HAS_DOSTOEVSKY and texts:
        results = _sentiment_model.predict(texts)  # ML-предсказание сразу для всех текстов
        mapping = {"positive": "POSITIVE", "negative": "NEGATIVE",
                   "neutral": "NEUTRAL", "skip": "NEUTRAL", "speech": "NEUTRAL"}
        return [mapping.get(max(r, key=r.get), "NEUTRAL") for r in results]
    return [_sentilex_sentiment(t) for t in texts]  # fallback на словарь

# ═══════════════════════════════════════════════════════
# E.4 — Scoring Formulas
# ═══════════════════════════════════════════════════════

# E.4.1 — Структурное влияние
def aud(followers):
    """Aud — размер аудитории по логарифмической шкале: ln(1 + число подписчиков)."""
    return math.log(1 + followers)

def age(account_age_days, max_age_days):
    """Age — нормированный «стаж»: возраст активности / максимум по сессии [0..1]."""
    if max_age_days <= 0:
        return 0.0
    return account_age_days / max_age_days

def er_bg(reactions_list, followers):
    """ER_bg — фоновая вовлечённость: средние реакции на фоновый пост, делённые на подписчиков.

    reactions_list: список (лайки+комментарии+репосты) по каждому BASELINE-посту.
    Возвращает 0.0 при отсутствии данных или нулевых подписчиках.
    """
    if not reactions_list or followers <= 0:
        return 0.0
    total = sum(reactions_list)
    return total / (len(reactions_list) * followers)

# E.4.2 — Тематическая активность
def top_vol(topic_post_count):
    """TopVol — объём тематической активности: число тематических постов автора."""
    return topic_post_count

def top_focus(topic_count, non_topic_count):
    """TopFocus — тематический фокус: доля тематических постов среди всех [0..1]."""
    total = topic_count + non_topic_count
    if total <= 0:
        return 0.0
    return topic_count / total

def reach(views_list):
    """Reach — суммарный охват: сумма просмотров по тематическим постам."""
    return sum(views_list)

# E.4.3 — Позиция автора
def pos(sentiments):
    """Pos — распределение тональности постов автора: доли positive/neutral/negative.

    Пустой список интерпретируется как полностью нейтральный (neutral=1.0).
    """
    if not sentiments:
        return {"positive": 0.0, "neutral": 1.0, "negative": 0.0}
    n = len(sentiments)
    p = sum(1 for s in sentiments if s == "POSITIVE") / n
    neg = sum(1 for s in sentiments if s == "NEGATIVE") / n
    neu = 1.0 - p - neg  # нейтральная доля — остаток до 1
    return {"positive": p, "neutral": neu, "negative": neg}

# E.4.4 — Отклик аудитории
def er_top(reactions_list, followers):
    """ER_top — тематическая вовлечённость: средние реакции на тематический пост / подписчики."""
    if not reactions_list or followers <= 0:
        return 0.0
    total = sum(reactions_list)
    return total / (len(reactions_list) * followers)

def resp(comment_sentiments):
    """Resp — распределение тональности комментариев (отклик аудитории).

    Аналогично pos, но по тональностям комментариев к тематическим постам.
    """
    if not comment_sentiments:
        return {"positive": 0.0, "neutral": 1.0, "negative": 0.0}
    n = len(comment_sentiments)
    p = sum(1 for s in comment_sentiments if s == "POSITIVE") / n
    neg = sum(1 for s in comment_sentiments if s == "NEGATIVE") / n
    neu = 1.0 - p - neg
    return {"positive": p, "neutral": neu, "negative": neg}

# E.4.6 — Робастная z-нормализация
def z_normalize(values):
    """Робастная z-нормализация: z = (v − медиана) / IQR (E.4.6).

    Допускает None во входном списке (заменяются на 0.0). Возвращает кортеж
    (список z-оценок, медиана, IQR). Если IQR=0 или данных мало — z=0.0.
    """
    non_null = [v for v in values if v is not None]  # значения без пропусков
    if len(non_null) < 2:
        return [0.0] * len(values), median(non_null) if non_null else 0.0, 0.0
    med = median(non_null)  # центр нормализации — медиана
    iq = iqr(non_null)      # масштаб нормализации — IQR
    z_vals = []
    for v in values:
        if v is None:
            z_vals.append(0.0)             # пропуск -> нейтральная z=0
        elif iq > 0:
            z_vals.append((v - med) / iq)  # робастная z-оценка
        else:
            z_vals.append(0.0)             # нет разброса -> все z=0
    return z_vals, med, iq

# Композиты (равные веса 1/3 — OECD Handbook)
def structural_composite(z_aud, z_er_bg, z_age):
    """Структурный композит — среднее z-оценок Aud, ER_bg, Age (веса 1/3,1/3,1/3)."""
    return (z_aud + z_er_bg + z_age) / 3.0

def topic_composite(z_top_vol, z_top_focus, z_reach):
    """Тематический композит — среднее z-оценок TopVol, TopFocus, Reach (1/3 каждый)."""
    return (z_top_vol + z_top_focus + z_reach) / 3.0

# Классификация ролей
def assign_role(struct, topic, theta_struct, theta_topic):
    """Базовая роль автора по квадрантам (структурный × тематический композит).

    Сравнивает композиты с адаптивными порогами theta. Четыре квадранта:
    высокий/высокий — авторитетный лидер; высокий/низкий — спящий гигант;
    низкий/высокий — тематический активист; иначе — фоновый автор.
    """
    if struct >= theta_struct and topic >= theta_topic:
        return "AUTHORITATIVE_LEADER"
    elif struct >= theta_struct and topic < theta_topic:
        return "SLEEPING_GIANT"
    elif struct < theta_struct and topic >= theta_topic:
        return "TOPIC_ACTIVIST"
    else:
        return "BACKGROUND_AUTHOR"

def author_position(pos_dist):
    """Атрибут позиции автора по распределению Pos: SUPPORTIVE/CRITICAL/NEUTRAL.

    Метка соответствует преобладающей доле тональности постов автора.
    """
    p, neu, neg = pos_dist["positive"], pos_dist["neutral"], pos_dist["negative"]
    if p >= neu and p >= neg:
        return "SUPPORTIVE"
    if neg >= p and neg >= neu:
        return "CRITICAL"
    return "NEUTRAL"

def audience_response(resp_dist):
    """Атрибут отклика аудитории по распределению Resp: APPROVING/CRITICAL/MIXED.

    Положительный или нейтральный перевес >0.5 трактуется как одобрительный отклик,
    отрицательный >0.5 — как критический; иначе отклик смешанный.
    """
    p, neu, neg = resp_dist["positive"], resp_dist["neutral"], resp_dist["negative"]
    if p > 0.5:
        return "APPROVING"
    if neg > 0.5:
        return "CRITICAL"
    if neu > 0.5:
        return "APPROVING"
    return "MIXED"

# ═══════════════════════════════════════════════════════
# MAIN PIPELINE
# ═══════════════════════════════════════════════════════

def main():
    """Полный офлайн-конвейер расчёта метрик по dataset_ai_v2.json.

    Шаги: загрузка датасета и индексация → sentiment текущих постов и
    комментариев → расчёт 11 оценок по 4 осям для каждого автора → bootstrap-CI →
    z-нормализация и композиты → адаптивные пороги и классификация ролей → оценка
    достаточности данных → сохранение dataset_ai_v2_results.json и печать топа.
    """
    dataset_path = Path(__file__).parent.parent / "examples" / "dataset_ai_v2.json"
    with open(dataset_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    # Индексируем сущности по vkId для быстрого доступа
    communities = {c["vkId"]: c for c in data["communities"]}
    authors_raw = {a["vkId"]: a for a in data["authors"]}
    posts = data["posts"]
    comments = data["comments"]

    # Пропускаем закрытые аккаунты — по ним нет надёжных данных
    open_authors = {vk_id: a for vk_id, a in authors_raw.items() if not a.get("isClosed", False)}
    print(f"Authors: {len(authors_raw)} total, {len(open_authors)} open")

    # Группируем посты по автору (fromId)
    posts_by_author = defaultdict(list)
    for p in posts:
        posts_by_author[p["fromId"]].append(p)

    # Группируем комментарии по посту — ключ (владелец поста, vkId поста)
    comments_by_post = defaultdict(list)
    for c in comments:
        key = (c["postOwnerId"], c["postVkId"])
        comments_by_post[key].append(c)

    # ── Step 1: Sentiment for CURRENT post texts ──
    print("\n[Step 1] Analyzing sentiment for CURRENT posts...")
    current_posts = [p for p in posts if p["window"] == "CURRENT"]
    current_texts = [p.get("text", "") or "" for p in current_posts]
    current_sentiments = batch_sentiment(current_texts)
    post_sentiment_map = {}
    for p, s in zip(current_posts, current_sentiments):
        post_sentiment_map[p["vkId"]] = s
    sent_counts = defaultdict(int)
    for s in current_sentiments:
        sent_counts[s] += 1
    print(f"  CURRENT posts: {len(current_posts)}, sentiments: {dict(sent_counts)}")

    # ── Step 2: Sentiment for comments ──
    print("[Step 2] Analyzing sentiment for comments...")
    comment_texts = [c.get("text", "") or "" for c in comments]
    comment_sentiments = batch_sentiment(comment_texts)
    comment_sentiment_map = {}
    for c, s in zip(comments, comment_sentiments):
        comment_sentiment_map[c["vkId"]] = s
    c_counts = defaultdict(int)
    for s in comment_sentiments:
        c_counts[s] += 1
    print(f"  Comments: {len(comments)}, sentiments: {dict(c_counts)}")

    # ── Step 3: Compute 11 scores per author ──
    print("\n[Step 3] Computing 11 scores per author...")

    # В датасете нет даты создания аккаунта, поэтому Age оцениваем по «размаху»
    # активности: (последний пост − первый пост) в днях, нормированный на максимум.
    author_age_days = {}
    for vk_id in open_authors:
        author_posts = posts_by_author.get(vk_id, [])
        if author_posts:
            dates = [p["date"] for p in author_posts]
            # Размах дат постов в днях как прокси длительности активности автора
            spread = (max(dates) - min(dates)) / 86400.0  # 86400 = секунд в сутках
            author_age_days[vk_id] = max(spread, 1.0)     # не меньше 1 дня
        else:
            author_age_days[vk_id] = 0.0
    max_age = max(author_age_days.values()) if author_age_days else 1.0  # знаменатель нормировки Age

    # Определяем «шумовые» (нетематические) CURRENT-посты по характерным маркерам в тексте
    noise_markers = [
        "стоматолога", "iPhone", "посоветуйте сериал", "пробежка по набережной",
        "шарлотки", "Кот опять залез", "фильм Нолана", "пробке на МКАДе",
        "наступающими", "матч Спартак", "репетитора по математике", "Oxxxymiron",
        "Забронировал отель", "выставке современного", "Готовлю плов", "кроссовки для бега",
        "Дочке сегодня 5", "шаверма", "Переехал в новую", "Мастер и Маргариту",
        "менять масло", "концерт Земфиры", "спортзале после", "Грядки посажены",
        "электросамокатами", "День рождения мамы", "сковородка", "командировки в Казань",
        "Ищу квартиру", "Собака научилась", "Грузии этим летом", "отпуска в Турции",
        "Зимние шины", "подкаст «Что бы посмотреть", "Капучино из кофемашины",
        "курсы английского", "хомяка", "великах по Воробьёвым", "ипотека в Сбере",
        "кассеты с музыкой", "Заказал торт и цветы",
    ]
    noise_vkids = set()
    for p in posts:
        if p["window"] == "CURRENT":
            text = p.get("text", "")
            # Если в тексте встретился любой маркер — считаем пост нетематическим (шум)
            if any(m in text for m in noise_markers):
                noise_vkids.add(p["vkId"])
    print(f"  Noise posts filtered: {len(noise_vkids)}")

    author_scores = {}
    for vk_id, author in open_authors.items():
        a_posts = posts_by_author.get(vk_id, [])
        # Тематические = CURRENT-посты, не попавшие в шум; фоновые = BASELINE
        topic_posts = [p for p in a_posts if p["window"] == "CURRENT" and p["vkId"] not in noise_vkids]
        baseline_posts = [p for p in a_posts if p["window"] == "BASELINE"]
        followers = author["followersCount"]

        if not topic_posts:
            continue  # авторов без тематических постов не оцениваем

        # E.4.1 Структурное влияние
        s_aud = aud(followers)
        s_age = age(author_age_days.get(vk_id, 0), max_age)
        # Реакции на фоновые посты = лайки+комментарии+репосты
        bg_reactions = [p["likes"] + p["comments"] + p["reposts"] for p in baseline_posts]
        s_er_bg = er_bg(bg_reactions, followers)

        # E.4.2 Тематическая активность
        s_top_vol = top_vol(len(topic_posts))
        # Нетематические в том же периоде приближаем фоновыми постами
        s_top_focus = top_focus(len(topic_posts), len(baseline_posts))
        views_list = [p.get("views", 0) or 0 for p in topic_posts]
        # Запасной вариант: если просмотров нет (0), берём число подписчиков
        views_list = [v if v > 0 else followers for v in views_list]
        s_reach = reach(views_list)

        # E.4.3 Позиция автора — по тональности его тематических постов
        topic_sents = [post_sentiment_map.get(p["vkId"], "NEUTRAL") for p in topic_posts]
        s_pos = pos(topic_sents)

        # E.4.4 Отклик аудитории
        topic_reactions = [p["likes"] + p["comments"] + p["reposts"] for p in topic_posts]
        s_er_top = er_top(topic_reactions, followers)

        # Собираем тональности комментариев к тематическим постам автора
        all_comment_sents = []
        comment_clusters = []  # кластеры по постам — для двухуровневого bootstrap
        for tp in topic_posts:
            key = (tp["ownerId"], tp["vkId"])
            post_comments = comments_by_post.get(key, [])
            cluster_sents = [comment_sentiment_map.get(c["vkId"], "NEUTRAL") for c in post_comments]
            all_comment_sents.extend(cluster_sents)
            if cluster_sents:
                comment_clusters.append(cluster_sents)  # непустой кластер = один пост
        s_resp = resp(all_comment_sents)

        author_scores[vk_id] = {
            "author": f"{author['firstName']} {author['lastName']}",
            "screenName": author.get("screenName", ""),
            "followers": followers,
            "topicPostCount": len(topic_posts),
            "baselinePostCount": len(baseline_posts),
            "commentCount": len(all_comment_sents),
            # Raw scores
            "aud": round(s_aud, 4),
            "age": round(s_age, 4),
            "er_bg": round(s_er_bg, 6),
            "top_vol": s_top_vol,
            "top_focus": round(s_top_focus, 4),
            "reach": round(s_reach, 2),
            "pos": {k: round(v, 4) for k, v in s_pos.items()},
            "er_top": round(s_er_top, 6),
            "resp": {k: round(v, 4) for k, v in s_resp.items()},
            # Store raw lists for bootstrap
            "_bg_reactions": bg_reactions,
            "_topic_reactions": topic_reactions,
            "_views": views_list,
            "_topic_sents": topic_sents,
            "_comment_clusters": comment_clusters,
        }

    print(f"  Scored {len(author_scores)} authors (skipped {len(open_authors) - len(author_scores)} with 0 topic posts)")

    # ── Step 4: Bootstrap confidence intervals ──
    print("\n[Step 4] Bootstrap confidence intervals...")
    for vk_id, sc in author_scores.items():
        followers = sc["followers"]
        # ER_bg: one-level, statistic = mean
        bg_r = sc["_bg_reactions"]
        if len(bg_r) >= 2 and followers > 0:
            per_post = [r / followers for r in bg_r]
            sc["er_bg_ci"] = bootstrap_one_level(per_post, lambda s: sum(s) / len(s))
        else:
            sc["er_bg_ci"] = None

        # ER_top: one-level
        top_r = sc["_topic_reactions"]
        if len(top_r) >= 2 and followers > 0:
            per_post = [r / followers for r in top_r]
            sc["er_top_ci"] = bootstrap_one_level(per_post, lambda s: sum(s) / len(s))
        else:
            sc["er_top_ci"] = None

        # Reach: one-level, statistic = sum
        views = sc["_views"]
        if len(views) >= 2:
            sc["reach_ci"] = bootstrap_one_level(views, lambda s: sum(s))
        else:
            sc["reach_ci"] = None

        # Pos: one-level distribution
        sents = sc["_topic_sents"]
        if len(sents) >= 2:
            sc["pos_ci"] = bootstrap_distribution(sents)
        else:
            sc["pos_ci"] = None

        # Resp: TWO-LEVEL bootstrap
        clusters = sc["_comment_clusters"]
        if len([c for c in clusters if c]) >= 2:
            sc["resp_ci"] = bootstrap_two_level(clusters)
        else:
            sc["resp_ci"] = None

    print("  Bootstrap complete.")

    # ── Step 5: Z-normalization + composites ──
    print("\n[Step 5] Z-normalization and composites...")
    vk_ids = list(author_scores.keys())

    # Extract raw score vectors
    aud_vals = [author_scores[v]["aud"] for v in vk_ids]
    age_vals = [author_scores[v]["age"] for v in vk_ids]
    er_bg_vals = [author_scores[v]["er_bg"] for v in vk_ids]
    top_vol_vals = [float(author_scores[v]["top_vol"]) for v in vk_ids]
    top_focus_vals = [author_scores[v]["top_focus"] for v in vk_ids]
    reach_vals = [author_scores[v]["reach"] for v in vk_ids]

    z_aud, med_aud, iqr_aud = z_normalize(aud_vals)
    z_age, med_age, iqr_age = z_normalize(age_vals)
    z_er_bg, med_er_bg, iqr_er_bg = z_normalize(er_bg_vals)
    z_top_vol, med_tv, iqr_tv = z_normalize(top_vol_vals)
    z_top_focus, med_tf, iqr_tf = z_normalize(top_focus_vals)
    z_reach, med_r, iqr_r = z_normalize(reach_vals)

    struct_composites = []
    topic_composites = []
    for i, vk_id in enumerate(vk_ids):
        sc = author_scores[vk_id]
        sc["z_aud"] = round(z_aud[i], 4)
        sc["z_age"] = round(z_age[i], 4)
        sc["z_er_bg"] = round(z_er_bg[i], 4)
        sc["z_top_vol"] = round(z_top_vol[i], 4)
        sc["z_top_focus"] = round(z_top_focus[i], 4)
        sc["z_reach"] = round(z_reach[i], 4)

        s_comp = structural_composite(z_aud[i], z_er_bg[i], z_age[i])
        t_comp = topic_composite(z_top_vol[i], z_top_focus[i], z_reach[i])
        sc["structural_composite"] = round(s_comp, 4)
        sc["topic_composite"] = round(t_comp, 4)
        struct_composites.append(s_comp)
        topic_composites.append(t_comp)

    # Adaptive thresholds
    theta_struct = median(struct_composites)
    theta_topic = median(topic_composites)
    print(f"  theta_Struct = {theta_struct:.4f}")
    print(f"  theta_Topic  = {theta_topic:.4f}")

    # ── Step 6: Role classification ──
    print("\n[Step 6] Role classification...")
    role_counts = defaultdict(int)
    for i, vk_id in enumerate(vk_ids):
        sc = author_scores[vk_id]
        role = assign_role(struct_composites[i], topic_composites[i], theta_struct, theta_topic)
        position = author_position(sc["pos"])
        response = audience_response(sc["resp"])
        sc["role"] = role
        sc["author_position"] = position
        sc["audience_response"] = response
        role_counts[role] += 1

        # Clean up internal fields
        for key in ["_bg_reactions", "_topic_reactions", "_views", "_topic_sents", "_comment_clusters"]:
            del sc[key]

    print(f"  Roles: {dict(role_counts)}")

    # ── Step 7: Data sufficiency ──
    print("\n[Step 7] Data sufficiency assessment...")
    for vk_id in vk_ids:
        sc = author_scores[vk_id]
        t_count = sc["topicPostCount"]
        c_count = sc["commentCount"]

        # CI width check (average of available CIs)
        ci_widths = []
        for ci_key in ["er_bg_ci", "er_top_ci", "reach_ci"]:
            ci = sc.get(ci_key)
            if ci:
                ci_widths.append(ci["ci_hi"] - ci["ci_lo"])

        # Средняя ширина доступных CI — индикатор разброса оценок (чем шире, тем хуже)
        avg_ci_width = sum(ci_widths) / len(ci_widths) if ci_widths else 999.0

        # Классификация надёжности по числу постов/комментариев и ширине CI
        if t_count >= 10 and c_count >= 50 and avg_ci_width <= 0.20:
            sc["data_sufficiency"] = "RELIABLE"       # достаточно данных, узкие CI
        elif t_count < 3 or c_count < 10 or avg_ci_width > 0.50:
            sc["data_sufficiency"] = "UNRELIABLE"     # мало данных или слишком широкие CI
        else:
            sc["data_sufficiency"] = "PRELIMINARY"    # промежуточный случай

    suf_counts = defaultdict(int)
    for vk_id in vk_ids:
        suf_counts[author_scores[vk_id]["data_sufficiency"]] += 1
    print(f"  Sufficiency: {dict(suf_counts)}")

    # ── Build output ──
    output = {
        "dataset": "dataset_ai_v2.json",
        "total_authors": len(authors_raw),
        "scored_authors": len(author_scores),
        "total_posts": len(posts),
        "current_posts": len(current_posts),
        "baseline_posts": len(posts) - len(current_posts),
        "total_comments": len(comments),
        "sentiment_method": "dostoevsky_fasttext" if HAS_DOSTOEVSKY else "rule_based_fallback",
        "thresholds": {
            "theta_struct": round(theta_struct, 4),
            "theta_topic": round(theta_topic, 4),
        },
        "z_normalization": {
            "aud": {"median": round(med_aud, 4), "iqr": round(iqr_aud, 4)},
            "age": {"median": round(med_age, 4), "iqr": round(iqr_age, 4)},
            "er_bg": {"median": round(med_er_bg, 6), "iqr": round(iqr_er_bg, 6)},
            "top_vol": {"median": round(med_tv, 4), "iqr": round(iqr_tv, 4)},
            "top_focus": {"median": round(med_tf, 4), "iqr": round(iqr_tf, 4)},
            "reach": {"median": round(med_r, 2), "iqr": round(iqr_r, 2)},
        },
        "role_distribution": dict(role_counts),
        "sufficiency_distribution": dict(suf_counts),
        "authors": sorted(
            list(author_scores.values()),
            key=lambda x: x["structural_composite"] + x["topic_composite"],
            reverse=True,
        ),
    }

    out_path = Path(__file__).parent.parent / "examples" / "dataset_ai_v2_results.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    print(f"\n[DONE] Results saved to {out_path}")
    print(f"  {len(author_scores)} authors scored across 4 axes (11 metrics)")

    # ── Print top authors summary ──
    print("\n" + "=" * 90)
    print("TOP AUTHORS BY COMPOSITE SCORE (Struct + Topic)")
    print("=" * 90)
    ROLE_NAMES = {
        "AUTHORITATIVE_LEADER": "Авторитетный лидер",
        "SLEEPING_GIANT": "Спящий гигант",
        "TOPIC_ACTIVIST": "Тематический активист",
        "BACKGROUND_AUTHOR": "Фоновый автор",
    }
    for a in output["authors"][:20]:
        total = a["structural_composite"] + a["topic_composite"]
        print(f"  {a['author']:<30s} "
              f"Struct={a['structural_composite']:+.3f}  "
              f"Topic={a['topic_composite']:+.3f}  "
              f"Sum={total:+.3f}  "
              f"Role={ROLE_NAMES.get(a['role'], a['role'])}")
        print(f"    {'':30s} "
              f"Followers={a['followers']:>8,}  "
              f"TopPosts={a['topicPostCount']:>3}  "
              f"Position={a['author_position']}  "
              f"Response={a['audience_response']}  "
              f"Suf={a['data_sufficiency']}")

if __name__ == "__main__":
    main()
