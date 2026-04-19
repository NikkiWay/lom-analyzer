# Полный план реализации проекта на основе lom_architecture_v6.md

**Статус документа:** **полный контур проекта**. Документ не является MVP-планом; он охватывает реализацию всего объёма, описанного в `lom_architecture_v6.md`, вплоть до релиза v1.0 и заделов для будущих расширений.

**Назначение:** авторитетная дорожная карта разработки, backlog верхнего уровня и спринтовая очередь, покрывающие 100% архитектурно-методологической спецификации v6.

**Отношение к предыдущему MVP-плану:** MVP сохраняется как **промежуточная веха** (к концу спринта 7), но не является конечным результатом. После MVP следуют этапы методологического расширения, качественно-безопасностного расширения, расширения работы с аномалиями, расширенной валидации и релиза v1.0. MVP-план переосмыслен как подмножество настоящего, более широкого плана.

---

## 1. Введение

### 1.1. Контекст

Документ `lom_architecture_v6.md` содержит полную архитектурно-методологическую спецификацию информационно-аналитической системы раннего предупреждения об информационных рисках. Документ прошёл серию из пяти независимых аудитов (v2–v6) и получил итоговую оценку готовности 91%. Он описывает:

- 13 верхнеуровневых пакетов архитектуры с ~50 модулями;
- методологическое ядро из 6 конструкций (двухмерная модель, двухпроходная временная схема, матрица ролей 4×2, робастная статистика, формализованная неопределённость, атомарные NLP-режимы);
- 35-шаговый сквозной сценарий обработки сессии;
- ~45 параметров с диапазонами чувствительности;
- ~40 типов событий AuditLog;
- 4 ресурсных файла с формализованными схемами;
- правовой и этический слой;
- процедуры валидации и верификации;
- 8-спринтовую дорожную карту первого уровня детализации.

Предыдущий имплементационный план сознательно ограничивался MVP-срезом (~70% объёма v6), откладывая методологически сложные или инфраструктурно-тяжёлые элементы. Это было оправдано для первого контакта с кодом, но недостаточно как полный план.

### 1.2. Цель настоящего документа

Сформировать **единую дорожную карту разработки от нуля до релиза v1.0** с явным включением всех элементов v6, которые были отложены в MVP-плане. В документе:

- восстановлен полный объём работ;
- сохранён MVP как этап-вехапромежуточного результата;
- добавлены этапы расширения (методологическое, безопасностное, аномалии+сезонность, ресурсы+валидация, релиз);
- перестроен критический путь с учётом полной структуры;
- актуализированы зависимости и риски;
- сформирован единый полный backlog.

### 1.3. Принципы полного плана

1. **Полнота.** Каждый модуль и каждая функция из v6 имеет место в плане.
2. **Прагматизм.** Этапы упорядочены по критическому пути и реальным зависимостям.
3. **MVP как checkpoint.** MVP сохраняется как точка внутренней демонстрации и получения обратной связи, но не как конечная цель.
4. **Валидация и калибровка — работа, не «приложение».** Sensitivity-harness, benchmark, собственная валидация accuracy, ground-truth κ — это самостоятельные инженерные задачи, а не только приёмочные условия.
5. **Research spikes — явные задачи.** Каждый методологически неопределённый элемент сопровождается R-spike-ом с владельцем и сроком.
6. **Release-gating — структурная часть плана.** G-задачи формализованы как условия допуска к релизу v1.0.

---

## 2. Почему текущий (MVP) план был ограничен

MVP-план от предыдущей итерации был ограничен тремя соображениями:

1. **Контроль риска первого запуска.** Меньший объём позволяет быстрее получить работающий срез и калибровать процесс разработки.
2. **Дипломная работа как первая веха.** MVP-демонстрация могла быть достаточна для формальной защиты проекта.
3. **Осторожность относительно неопределённых методологий.** Theil-Sen, Huber, MILD_RECOMPUTED, ClusterRoleClassifier откладывались, поскольку их реальное поведение на VK-данных требует эмпирической проверки.

Однако ограничение MVP не означает отказ от этих элементов. Все они описаны в v6 как обязательные для **полноценной реализации**, и без них система не соответствует заявленным в документе свойствам:

- **без Theil-Sen / Huber** — устойчивость к выбросам в данных соцсетей не достигается;
- **без MILD_RECOMPUTED** — абсолютная шкала $I_{base}^{abs}$ может неверно работать при расхождении сессионной и референсной γ;
- **без ClusterRoleClassifier** — альтернатива квадрантной классификации не поддерживается;
- **без полного PiiSafeFormatter** — правовой слой не полноценен;
- **без полного референсного корпуса 15000 авторов** — абсолютная шкала индикативна, а не индустриальна;
- **без sensitivity + benchmark harness** — спецификация NFR-03 не подтверждена;
- **без SessionFamily** — продольный мониторинг не реализуем.

Настоящий план восстанавливает все эти элементы.

---

## 3. Полный контур проекта по v6

Система, описанная в v6, подразумевает следующий **полный объём** разработки.

### 3.1. Инфраструктура приложения

- Kotlin 2.0+ / Compose Desktop 1.7+ / Gradle Kotlin DSL.
- Koin DI, Logback + logstash-encoder + `PiiSafeFormatter`.
- SQLite WAL + Exposed + Flyway (запрет downgrade).
- AES-GCM + PBKDF2 шифрование; `TokenVault` с полным жизненным циклом (мастер-пароль, OAuth WebView, затирание памяти).
- `SingleInstanceLock` с PID + timestamp.
- `ActiveSessionRegistry` с state machine {IDLE, ANALYZING, RECOVERY_AWAITING}.
- `PythonServiceManager` с callback `onPermanentFailure`.
- `PythonRecoveryDialog` — модальный диалог трёх опций + статус `PAUSED_PENDING_RECOVERY`.
- `RetentionManager` с soft-delete + 30-дневным grace-периодом.
- Полный AuditLog (~40 типов событий).
- CI: GitHub Actions с build, unit-тестами (70% line/60% branch на analysis/), integration, sensitivity, benchmark.

### 3.2. Сбор данных

- Полный `VkApiClient` с `execute`-батчами до 25 вызовов.
- Rate limiter 3 req/s + exponential backoff с jitter.
- OAuth Implicit Flow в MVP; **миграция на Authorization Code + PKCE** в post-MVP.
- `BaselineCollector`, `CurrentCollector`, `ReposterCollector`.
- `DiscoveryEngine` с **тремя правилами** (высокий охват + массовый репостер + контекстный NER) + Discovery Priority Score.
- Сокращённый baseline 30 дней для discovery-авторов.
- `CollectionCheckpoint` с resume.

### 3.3. Предобработка

- `TextCleaner`, `Tokenizer`, `StopWords`.
- `LanguageDetectorProxy`: FULL через `langdetect`, FALLBACK через 500-lemma heuristic.
- `LemmatizerProxy`: FULL через `pymorphy3`, FALLBACK через `org.tartarus.snowball.ext.RussianStemmer`.

### 3.4. NLP-сервис

- Интерфейс `NlpService`.
- `LocalKotlinNlpService` (FALLBACK): словарная тональность, stemmer, эвристика языка.
- `PythonSidecarNlpService`: FastAPI + uvicorn + семафор 4, shared secret, случайный порт.
- Bundled Python venv 700 МБ + альтернатива bootstrap-on-first-launch.
- Бенчмарк альтернативных NLP-моделей (rubert-tiny3, ruBERT-base, ru-e5-small).
- Задел под миграцию на ONNX Runtime for JVM (ликвидация Python).

### 3.5. Тематическая фильтрация и дедупликация

- L1 (n-граммы primary/secondary/excluded) + L2 (семантический скоринг через rubert-tiny2).
- Комбинированный score с $\theta = 0.30$ (FULL) / $\theta_{l_1} = 0.33$ (FALLBACK).
- Stage 1 dedup (exact SHA-256) ко всем постам ≥ 30 символов.
- Stage 2 dedup (bounded Jaccard ±72ч) только к тематическим.
- Задел под MinHash LSH для масштабирования на post-MVP.

### 3.6. Аналитическое ядро LOM

- `AudienceComponent`, `EngagementDensityComponent`, `ClosedProfileImputer` ($Q_{25}$).
- `GammaCalibrator`: **полная реализация через Theil-Sen + MAD-based $R^2$** (OLS — только fallback при $n < 20$).
- `ReferenceGammaValidator` — флаги OK / MILD_RECOMPUTED / AUDIENCE_ONLY_REFERENCE.
- `TopicFocusComponent` с leave-one-out prior.
- `OrthogonalizerT` с условным применением (Theil-Sen + MAD + permutation test).
- `TopicalVolumeComponent` с $k_{window}$-нормировкой.
- `DisseminationReachComponent` с пересчётом $M_{reach,i}$ после финализации репостеров.
- `ContentOriginalityComponent` (5 уровней).
- `RobustNormalizer` с каскадом fallback.
- `RobustRegression` (Theil-Sen + Huber + MAD-based диагностика, bootstrap CI 1000).
- `BootstrapEstimator`: **полноценный двухуровневый 300×100** (внешний по авторам, внутренний по постам).
- `BlockBootstrap` для $R$: полноценный 300 итераций.
- `ReferenceCalibrator`: **все три ветви** — OK / MILD_RECOMPUTED (с пересчётом E-квантилей) / AUDIENCE_ONLY_REFERENCE.
- `RoleClassifier`, `RoleCombinator` (матрица 4×2).
- `ClusterRoleClassifier` (GMM, опциональный режим).

### 3.7. Контент-анализ

- `DictionarySentiment` (RuSentiLex + NegationHandler).
- `SidecarSentimentProxy` (dostoevsky).
- `SentimentBootstrap`: **полноценный 20 вариантов** (3 словаря × 3 окна × 3 порога; 5 dostoevsky seeds × 15 параметризаций).
- `HuberAggregator`: **полноценный M-estimator** для агрегированной тональности актора с k=1.345.
- `TermExtractor` (TF-IDF топ-10).
- `VisualActivityEstimator` (VAR).
- `ExperimentalFrameClassifier`: в отдельной вкладке, после валидации accuracy ≥ 50%.

### 3.8. Аномалии и риск

- `RollingZScore` с окном 7 дней.
- `HolidayCalendar`: загрузка `holidays.json` с поддержкой **нескольких версий по годам** (обратная совместимость).
- `SeasonalityNormalizer`: недельный паттерн + **праздничное среднее $\bar{x}_{holiday}$** отдельно.
- `VolumeSpikeDetector` с порогом $z > 2.5$.
- `ToneShiftDetectorNegative`, `ToneShiftDetectorPositive` — двусторонние.
- `GiantActivationDetector` с **полной защитой от рутинных постов** (проверка $N_{topic}^{baseline}$).
- `AnomalyDeduplicator` (ABSORBED_BY_GIANT).
- `RiskScorer` с формулой $R_{multi}$ (коэффициент 1.2).
- `SignalGenerator`, `RecommendationEngine`.

### 3.9. Модель персоны

- `PersonaAggregator` с полным составом полей (§21.2 v6).
- `PersonaHistoryManager` — **задел под продольный мониторинг**; в v1.0 имеет инфраструктуру, но работа в продольном режиме — отдельное расширение.

### 3.10. UI

- Все экраны: Setup, Collection, TopicValidation, LomDashboard, LomDetail, Persona, RiskPanel, Dynamics, SessionQuality, OAuth, MasterPasswordDialog, **PythonRecoveryDialog**.
- Компоненты: LomTable, ScatterPlot, TimeSeriesChart, RiskCard, ConfidenceIndicator, CiBar, QualityGauge, RoleCombinationBadge, **LocalRoleTooltip**, **HolidayMarker**, **DiscoveryAuthorBadge**, **SensitivityFlagBadge**.
- UX-сценарии: форк COMPLETED-сессии; тайм-аут RECOVERY_AWAITING; обновления прогресса сбора с ETA.

### 3.11. Экспорт

- `CsvExporter`, `JsonExporter`, `SafeExporter` (privacy-first).

### 3.12. Безопасность, правовой и этический слой

- **Полный PiiSafeFormatter** с ≥ 20 unit-тестами и build-time проверкой запрета `DEBUG_INCLUDES_PII` в production.
- Право на забвение: каскадное удаление автора из всех сессий.
- Soft-delete retention с 30-дневным grace + восстановление в UI.

### 3.13. Наблюдаемость

- Структурированное JSON-логирование с санитизацией PII.
- `SessionMetrics` — per-stage timings + itemsProcessed/failed.
- Ротация 50 МБ / 7 дней.

### 3.14. Ресурсные файлы

- **Полный `reference_base.json` (15000 авторов)** с стратификацией.
- Сопровождающая методология `ref_base_methodology.md`.
- `holidays.json` с `valid_from/valid_to` для обратной совместимости.
- `sentilex_base.json` (RuSentiLex, полный).
- **Расширенный `test_corpus.json`** (≥ 500 постов, 50 авторов, ground truth для ролей и аномалий).
- `holidays_methodology.md`.
- `test_corpus_methodology.md`.

### 3.15. Quality & Release Gates

- `./gradlew sensitivityReport` — полноценный harness на `test_corpus.json`.
- `./gradlew benchmark` — замер всех этапов пайплайна.
- Эмпирическая калибровка порога $R^2_{MAD}$ для ортогонализации T.
- Валидация dostoevsky на 50 постах VK-корпуса (V-01).
- Ground-truth κ Коэна для ролей на 30–50 акторах + 1–2 независимых эксперта (V-02).
- Bayes Beta валидация тематического фильтра (V-03).
- Бенчмарк альтернативных NLP-моделей (rubert-tiny3, ruBERT-base, ru-e5-small) — для информированного решения о модели в v1.0.
- Итоговый отчёт sensitivity + benchmark + valid'ация в `reports/release_v1_0/`.

### 3.16. Задел под будущее

- `SessionFamily` инфраструктура в схеме БД (nullable `sessionFamilyId`, `PostMetricsSnapshot`).
- Готовность к миграции на PostgreSQL (многопользовательский сценарий).
- Готовность к миграции NLP в ONNX Runtime for JVM.
- MinHash LSH для dedup на больших корпусах.

---

## 4. Что отсутствовало в MVP-плане (и теперь включено)

Перечень элементов, ранее помеченных **[POST-MVP]** / **[GATE]**, которые восстановлены как полноценные задачи полного плана.

| Категория | Элемент v6 | Статус в MVP-плане | Статус в полном плане |
|-----------|-----------|--------------------|-----------------------|
| Аутентификация | OAuth Authorization Code + PKCE | Отложено | **Включено в Фазу 5** |
| Security | Полный PiiSafeFormatter + 20 unit-тестов | Упрощённый | **Включено в Фазу 5** |
| Security | Build-time проверка DEBUG_INCLUDES_PII | Отложено | **Включено в Фазу 5** |
| Retention | Soft-delete + 30-дневный grace | Упрощённое | **Включено в Фазу 5** |
| Orchestration | Полный ActiveSessionRegistry с state machine | Упрощённый | **Включено в Фазу 5** |
| Orchestration | PythonRecoveryDialog + PAUSED_PENDING_RECOVERY | Отложено | **Включено в Фазу 5** |
| Discovery | Правила 2, 3 + DPS + сокращённый baseline для discovery | Упрощённый | **Включено в Фазу 4** |
| Dedup | MinHash LSH | Отложено | **Включено в Фазу 7 (будущее)** |
| LOM | RobustRegression (Theil-Sen + Huber + MAD) | Опционально | **Включено в Фазу 4** |
| LOM | OrthogonalizerT | Отложено | **Включено в Фазу 4** |
| LOM | MILD_RECOMPUTED / AUDIENCE_ONLY_REFERENCE | Только OK | **Включено в Фазу 4** |
| LOM | ClusterRoleClassifier (GMM) | Отложено | **Включено в Фазу 4** |
| LOM | Bootstrap 300×100 полный | 100×30 | **Включено в Фазу 4** |
| Content | HuberAggregator полный M-estimator | Упрощённый | **Включено в Фазу 4** |
| Content | SentimentBootstrap полный 20 вариантов | 10 | **Включено в Фазу 4** |
| Content | ExperimentalFrameClassifier | Исключено | **Включено в Фазу 6** |
| Anomaly | Праздничное среднее $\bar{x}_{holiday}$ | Упрощённо | **Включено в Фазу 6** |
| Anomaly | GIANT_ACTIVATION защита от рутинных постов | Упрощённо | **Включено в Фазу 6** |
| Resources | holidays.json — множественные версии | Одна версия | **Включено в Фазу 6** |
| Resources | Полный reference_base.json (15000) | Упрощённый | **Включено в Фазу 7** |
| Resources | Расширенный test_corpus.json (ground truth) | Минимальный | **Включено в Фазу 7** |
| UI | PythonRecoveryDialog | Отложено | **Включено в Фазу 5** |
| UI | HolidayMarker | Отложено | **Включено в Фазу 6** |
| UI | Форк COMPLETED-сессии | Отложено | **Включено в Фазу 5** |
| Export | JsonExporter | Отложено | **Включено в Фазу 5** |
| NLP | Бенчмарк альтернативных моделей | Отложено | **Включено в Фазу 8** |
| NLP | Bundled Python venv 700 МБ + bootstrap | Системный Python | **Включено в Фазу 8** |
| Persona | PersonaHistoryManager (задел под SessionFamily) | Задел | **Включено в Фазу 5** |
| Quality gates | Sensitivity-harness | Gate v1.0 | **Включено в Фазу 8** |
| Quality gates | Benchmark-harness | Gate v1.0 | **Включено в Фазу 8** |
| Quality gates | Валидация dostoevsky V-01 | Апробация | **Включено в Фазу 8** |
| Quality gates | Ground-truth κ V-02 | Апробация | **Включено в Фазу 8** |
| Quality gates | Эмпирическая калибровка $R^2_{MAD}$ | POST-MVP | **Включено в Фазу 8** |
| Будущее | SessionFamily работающий продольный режим | Далёкое | **Включено в Фазу 9 (после v1.0)** |
| Будущее | ONNX Runtime for JVM | Далёкое | **Включено в Фазу 9** |
| Будущее | PostgreSQL миграция | Далёкое | **Включено в Фазу 9** |
| Будущее | OCR multimodal | Далёкое | **Включено в Фазу 9** |

---

## 5. Полная декомпозиция системы на модули и эпики

### 5.1. Модульная структура полного проекта

Система разбита на **16 эпиков** (без изменений по сравнению с MVP-планом — сам список модулей был корректен; изменился только их наполнение от MVP-объёма к полному).

| # | Эпик | Описание | Объём в полном плане |
|---|------|----------|-----------------------|
| E01 | Bootstrap Infrastructure | Старт приложения, DI, конфиг | Полный |
| E02 | Security | Токен, PII, AuditLog, PiiSafeFormatter | **Полный с 20 unit-тестами, build-time checks, OAuth+PKCE** |
| E03 | Storage | БД, Flyway, retention | **Полный с soft-delete + grace** |
| E04 | Observability | Логи, метрики, санитизация | **Полный** |
| E05 | Orchestration | Сессии, оркестратор, recovery UX | **Полный с ActiveSessionRegistry state machine + PythonRecoveryDialog** |
| E06 | VK Ingestion | Сбор + OAuth | **Полный с Discovery (3 правила + DPS) + OAuth+PKCE** |
| E07 | Preprocessing | Очистка, язык, лемматизация | Полный |
| E08 | NLP Service | NLP-сервисы | **Полный с bundled Python + бенчмарк альтернатив** |
| E09 | Analysis: Topic & Dedup | Фильтрация + dedup | **Полный, задел под MinHash** |
| E10 | Analysis: LOM Scoring | Индексы, bootstrap, роли | **Полный с Theil-Sen + OrthogonalizerT + MILD_RECOMPUTED + GMM + 300×100** |
| E11 | Analysis: Content | Тональность, термы, VAR | **Полный с HuberAggregator + 20-вариантный SentimentBootstrap + Frame classifier** |
| E12 | Analysis: Anomaly & Risk | Аномалии, риск | **Полный с защитой от рутинных + holidays полный** |
| E13 | Persona | Модель персоны | **Полный с PersonaHistoryManager (задел)** |
| E14 | UI | Compose-интерфейс | **Полный с PythonRecoveryDialog + HolidayMarker + форк + tooltips** |
| E15 | Export | CSV/JSON | **Полный с JsonExporter** |
| E16 | Resources | Ресурсные файлы | **Полный 15000-референс + ground truth + множественные holidays** |

### 5.2. Дополнительные сквозные эпики

| # | Эпик | Описание |
|---|------|----------|
| E17 | Quality Gates | sensitivity + benchmark harness, empirical calibration |
| E18 | Validation | V-01, V-02, V-03 — апробация на реальных данных |
| E19 | Release Preparation | Финальное тестирование, документация, сборка |
| E20 | Future Readiness (v2.0 задел) | SessionFamily, ONNX, PostgreSQL, MinHash, OCR |

Эпики E17–E20 выделены как самостоятельные задачи полного плана (в MVP-плане они рассредоточены по спринту 7 или вне плана).

---

## 6. Обновлённая дорожная карта (фазы и спринты)

### 6.1. Структура фаз

Полный план разбит на **9 фаз**, объединяющих 18 спринтов (по 2 недели каждый, + Sprint 0 — 1 неделя подготовки).

| Фаза | Спринты | Содержание | Веха |
|------|---------|------------|------|
| **Фаза 1: Подготовка** | Sprint 0 | Research spikes, git-настройка, первичные ресурсы | Подготовка к разработке |
| **Фаза 2: Инфраструктура** | Sprint 1 | E01+E02+E03+E04+E05 базовое | Рабочее приложение с пустым пайплайном |
| **Фаза 3: MVP Core** | Sprint 2–6 | E06+E07+E08+E09+E10 (упрощённый)+E11 (упрощённый)+E12 (упрощённый)+E13+E14+E15 (базовый) | **Веха MVP: демонстрационный срез** |
| **Фаза 4: Методологические расширения** | Sprint 7–9 | Theil-Sen, Huber, OrthogonalizerT, MILD_RECOMPUTED, GMM, Discovery полный, Bootstrap 300×100, SentimentBootstrap 20 | **Веха: полный аналитический пайплайн** |
| **Фаза 5: Безопасность и UX-расширения** | Sprint 10–11 | Полный PiiSafeFormatter, OAuth+PKCE, ActiveSessionRegistry state machine, PythonRecoveryDialog, форк сессии, PersonaHistoryManager задел, retention с grace, JsonExporter | **Веха: полный UX и безопасность** |
| **Фаза 6: Расширения аномалий и контента** | Sprint 12 | Праздничное среднее, GIANT routine protection, holidays multi-year, HolidayMarker, ExperimentalFrameClassifier | **Веха: полный контент и аномалии** |
| **Фаза 7: Ресурсы полного объёма** | Sprint 13–14 | Полный reference_base (15000), ref_base_methodology, расширенный test_corpus с ground truth, бенчмарк NLP-альтернатив | **Веха: полный ресурсный пакет** |
| **Фаза 8: Quality gates и валидация** | Sprint 15–16 | Sensitivity-harness, benchmark-harness, V-01/V-02/V-03, эмпирическая калибровка $R^2_{MAD}$, bundled Python | **Веха: готов к релизу v1.0** |
| **Фаза 9: Будущее (за v1.0)** | Sprint 17+ | SessionFamily продольный режим, ONNX, PostgreSQL, MinHash, OCR | Задел под v2.0 |

Итого 16 спринтов до релиза v1.0 + Sprint 0; Фаза 9 — отдельный роадмап для v2.0.

### 6.2. Детальное описание спринтов

#### Sprint 0 (1 неделя) — Подготовка

**Цели:**
- ознакомление с документацией v6 и планом;
- research spikes с минимальным риском;
- инициализация git, инфраструктуры разработки;
- параллельный старт сбора референсного корпуса.

**Задачи:**
- R-01 [Research] Проверка Python-стека (dostoevsky, natasha, pymorphy3, langdetect, rubert-tiny2) на тестовых VK-постах.
- R-07 [Research] Начало сбора упрощённого `reference_base.json` (50–100 авторов).
- T-00.1 [Infra] Инициализация git-репозитория; `README.md`, `LICENSE`, `.gitignore`.
- T-00.2 [Infra] IntelliJ IDEA проект, рабочее окружение.
- T-00.3 [Resource] Минимальный `test_corpus.json` (50 постов, 5 авторов) для первичного тестирования.
- T-00.4 [Research] R-spike: совместимость OAuth Implicit Flow с Compose WebView.

**Deliverable:** git-репозиторий, рабочее окружение, подтверждённый Python-стек, черновик упрощённого референса.

#### Sprint 1 (неделя 1–2) — Core Infrastructure

**Цели:**
- заложить основу: DI, хранилище, безопасность, логирование, оркестратор.

**Задачи:**
- T-01.1…T-01.6 [E01] Gradle, Koin, Compose Desktop setup, CI.
- T-02.1 [E02] MasterPasswordDialog.
- T-02.2 [E02] TokenVault (AES-GCM + PBKDF2).
- T-02.3 [E02] PiiHasher.
- T-02.4 [E02] AuditLog с базовыми событиями (расширяется в Фазе 5).
- T-03.1 [E03] Database + WAL.
- T-03.2 [E03] **Flyway V1 с полной схемой** (включая PersonaAggregate, RecoveryChoice, HolidayDayStats, PostMetricsSnapshot-задел).
- T-03.3 [E03] DAO для всех сущностей.
- T-03.4 [E03] Все обязательные индексы.
- T-04.1…T-04.4 [E04] Logback, Logger, MetricsCollector.
- T-04.5 [E04] **Базовая PII-санитизация** (полная в Фазе 5).
- T-05.1 [E05] SessionManager.
- T-05.2 [E05] SingleInstanceLock.
- T-05.3 [E05] **Упрощённый** ActiveSessionRegistry (boolean) — расширение в Фазе 5.
- T-05.4 [E05] PipelineOrchestrator (state machine на 35 шагов).
- T-05.5 [E05] CheckpointManager с 12 чекпоинтами.
- T-05.6 [E05] CancellationController.
- T-05.7 [E05] ProgressReporter.
- T-05.9 [E05] **Упрощённый** RetentionManager (жёсткое удаление) — расширение в Фазе 5.

**Deliverable:** приложение запускается, создаётся пустая сессия, логи пишутся, CI работает.

#### Sprint 2 (неделя 3–4) — VK Ingestion + Preprocessing

**Цели:** подключение VK API, сбор данных, базовая предобработка.

**Задачи:**
- T-06.1…T-06.5 [E06] VkApiClient, RateLimiter, Backoff, ExecuteBatcher, OAuth Implicit Flow.
- T-06.6…T-06.8 [E06] Collectors + Checkpoint.
- T-06.9 [E06] **Упрощённый** DiscoveryEngine (правило 1 только) — расширение в Фазе 4.
- T-07.1…T-07.5 [E07] TextCleaner, Tokenizer, StopWords, LanguageDetectorProxy FALLBACK, LemmatizerProxy FALLBACK (Snowball).

**Deliverable:** можно собрать 1 сообщество за 30 дней, данные сохраняются, предобработка работает в FALLBACK.

#### Sprint 3 (неделя 5–6) — NLP Sidecar + Topic Filter

**Цели:** подъём Python-сидекара, тематическая фильтрация.

**Задачи:**
- T-08.1…T-08.8 [E08] NlpService, LocalKotlinNlpService, PythonSidecarNlpService, PythonServiceManager, Семафор, запуск FastAPI.
- T-07.6 [E07] Upgrade preprocessing для FULL-режима.
- T-09.1…T-09.5 [E09] NgramMatcher, ExclusionFilter, L1+L2 filter, SemanticScorer, TopicRelevanceFilter.

**Deliverable:** тематическая фильтрация работает в FULL + валидационный UX.

#### Sprint 4 (неделя 7–8) — Base Scoring + Dedup

**Цели:** базовые индексы, дедупликация.

**Задачи:**
- T-09.6…T-09.8 [E09] DedupPipeline (Stage 1 + Stage 2).
- T-10.1 [E10] ClosedProfileImputer.
- T-10.2…T-10.3 [E10] AudienceComponent, EngagementDensityComponent.
- T-10.4 [E10] **GammaCalibrator в базовом варианте — OLS** (Theil-Sen в Фазе 4).
- T-10.5 [E10] ReferenceGammaValidator (OK-ветвь).
- T-10.10 [E10] RobustNormalizer с каскадом fallback.
- T-10.11 [E10] **BootstrapEstimator упрощённый 100×30** (300×100 в Фазе 4).
- T-10.13 [E10] BaseInfluenceScorer.
- R-02, R-03 [Research] Замер производительности bootstrap.

**Deliverable:** $I_{base}^{hist}$ + CI.

#### Sprint 5 (неделя 9–10) — Event Scoring + Roles

**Цели:** $I_{event}$, роли, референсная калибровка OK-ветви.

**Задачи:**
- T-10.6 [E10] TopicFocusComponent с leave-one-out prior.
- T-10.7 [E10] TopicalVolumeComponent с $k_{window}$.
- T-10.8 [E10] DisseminationReachComponent с пересчётом $M_{reach}$.
- T-10.9 [E10] ContentOriginalityComponent.
- T-10.13 [E10] EventActivityScorer.
- T-10.14 [E10] **ReferenceCalibrator только OK-ветвь** (MILD_RECOMPUTED и AUDIENCE_ONLY_REFERENCE в Фазе 4).
- T-10.15 [E10] RoleClassifier.
- T-10.16 [E10] RoleCombinator (матрица 4×2).

**Deliverable:** полный расчёт индексов, ролей.

#### Sprint 6 (неделя 11–12) — Content + Anomalies + Risk + UI

**Цели:** контент-анализ, аномалии, риск, базовый UI.

**Задачи:**
- T-11.1…T-11.6 [E11] NegationHandler, DictionarySentiment, SidecarSentimentProxy, **упрощённый SentimentBootstrap** (10 вариантов), TermExtractor, VisualActivityEstimator.
- T-11.7 [E11] **Простая агрегация тональности** (медиана) — Huber в Фазе 4.
- T-12.1…T-12.10 [E12] RollingZScore, HolidayCalendar (загрузка базовой версии holidays.json), **упрощённый SeasonalityNormalizer** (недельный паттерн), VolumeSpikeDetector, ToneShiftDetector±, **упрощённый GiantActivationDetector** (без защиты от рутинных), AnomalyDeduplicator, RiskScorer, SignalGenerator, RecommendationEngine.
- T-13.1 [E13] PersonaAggregator.
- T-14.1…T-14.13 [E14] Все экраны MVP (без PythonRecoveryDialog, HolidayMarker, форка — Фаза 5/6).
- T-15.1 [E15] CsvExporter privacy-first.

**Deliverable:** **🏁 Веха MVP: работоспособный end-to-end-срез**.

#### Sprint 7 (неделя 13–14) — Усиление MVP и доработки

**Цели:** закрытие MVP-пробелов, документирование, базовая валидация тематического фильтра.

**Задачи:**
- T-02.4 [E02] Расширение AuditLog до ~30 событий.
- T-14.11 [E14] SessionQualityScreen с полными компонентами SQS.
- V-03 [Validation] Валидация тематического фильтра на реальных данных.
- Доработка UX-полировки (tooltip, формы, сообщения об ошибках).
- Performance profiling — поиск узких мест.
- Документирование API модулей (KDoc).

**Deliverable:** стабилизированный MVP-билд, готовый к Фазе 4.

#### Sprint 8–9 (недели 15–18) — Фаза 4: Методологические расширения (часть 1)

**Цели Sprint 8:** робастная регрессия, ортогонализация.

**Задачи Sprint 8:**
- T-10.17 [E10] **RobustRegression** (Theil-Sen + MAD-based $R^2$ + Huber).
- T-10.17a [E10] Bootstrap CI 1000 итераций для γ.
- T-10.4-upgrade [E10] **GammaCalibrator через Theil-Sen** (заменяет OLS).
- T-10.18 [E10] **OrthogonalizerT** с условным применением (Theil-Sen + permutation test 500).
- R-04 [Research] Сравнение OLS vs Theil-Sen на референсном корпусе — решение о наборе весов A vs B.

**Deliverable:** робастные оценки γ и ортогонализация T.

**Цели Sprint 9:** расширенный Bootstrap, расширенный Discovery, полный HuberAggregator.

**Задачи Sprint 9:**
- T-10.21 [E10] **BootstrapEstimator полный 300×100** (внешний по авторам, внутренний по постам).
- T-10.12-upgrade [E10] **BlockBootstrap 300** итераций для $R$.
- T-06.10…T-06.11 [E06] **Полный DiscoveryEngine** (3 правила + DPS + сокращённый baseline для discovery).
- T-11.8 [E11] **HuberAggregator M-estimator** для тональности актора.
- T-11.9 [E11] **SentimentBootstrap полный** (20 вариантов: 3 dict × 3 windows × 3 thresholds или 5 seeds × варианты).

**Deliverable:** полноценный bootstrap, полноценный Discovery, полноценная Huber-агрегация.

#### Sprint 10 (недели 19–20) — Фаза 4 (часть 2): Расширенная референсная калибровка и GMM

**Цели:** полноценная референсная калибровка с тремя ветвями, GMM.

**Задачи:**
- T-10.19a [E10] **ReferenceCalibrator ветвь MILD_RECOMPUTED** с пересчётом E-квантилей под сессионную γ.
- T-10.19b [E10] **ReferenceCalibrator ветвь AUDIENCE_ONLY_REFERENCE** при $|\Delta\gamma| > 0.2$.
- T-10.19c [E10] Флаг `MILD_RECOMPUTED_HIGH_CORRELATION` при высокой корреляции $\ln F$ и $\ln \bar{r}$.
- T-10.20 [E10] **ClusterRoleClassifier (GMM)** — 2–3 компонента, выбор по BIC.
- Unit-тесты всех трёх ветвей calibrator-а.

**Deliverable:** **🏁 Веха: полный аналитический пайплайн** (все три ветви γ-рассогласования, GMM как альтернативная ролевая классификация).

#### Sprint 11 (недели 21–22) — Фаза 5 (часть 1): Безопасность и правовой слой

**Цели:** полноценный правовой слой, улучшенная аутентификация.

**Задачи:**
- T-02.6 [E02] **Полный PiiSafeFormatter** с ≥ 20 unit-тестами, покрытием vk_id/screen_name/firstName/lastName/длинных полей/URL-параметров/SQL-подобных bypasses/спец-символов.
- T-02.7 [E02] **Build-time проверка** запрета `DEBUG_INCLUDES_PII = true` в production-сборке (кастомный Gradle-task + аварийное завершение при нарушении).
- T-02.5 [E02] **OAuth Authorization Code Flow + PKCE** — миграция с Implicit Flow (сохранение совместимости со старыми токенами).
- T-03.6 [E03] **Полный Retention Manager** с soft-delete + 30-дневный grace + UI восстановления.
- Тестирование правового слоя (integration + edge cases).

**Deliverable:** production-ready правовой и безопасностный слой.

#### Sprint 12 (недели 23–24) — Фаза 5 (часть 2): UX-расширения и recovery

**Цели:** интерактивные UX-сценарии сбоев и конкурентности.

**Задачи:**
- T-05.10 [E05] **Полный ActiveSessionRegistry** с state machine {IDLE, ANALYZING, RECOVERY_AWAITING}; доменная ошибка `ANOTHER_SESSION_ACTIVE`.
- T-05.11 [E05] **PythonRecoveryDialog** с тремя опциями + состояние `PAUSED_PENDING_RECOVERY`.
- T-14.14 [E14] UI `PythonRecoveryDialog` Compose-компонент.
- Логика поведения при закрытии приложения во время `RECOVERY_AWAITING` (сохранение статуса + восстановление при следующем запуске).
- T-14.16 [E14] **UX форка COMPLETED-сессии** (переиспользование сырых данных).
- T-13.2 [E13] **PersonaHistoryManager** — задел под SessionFamily (поля в схеме есть с Sprint 1; теперь добавляется менеджер).
- Тайм-аут 30 мин для `RECOVERY_AWAITING` с автопереходом в CANCELLED.
- Политика конкурентности COLLECTING-сессий + Python-сидекар.
- T-15.3 [E15] **JsonExporter**.

**Deliverable:** **🏁 Веха: полный UX и правовой слой**.

#### Sprint 13 (недели 25–26) — Фаза 6: Расширения аномалий и контента

**Цели:** полноценные аномалии, сезонность, фрейм-классификация.

**Задачи:**
- T-12.11 [E12] **Праздничное среднее $\bar{x}_{holiday}$** в `SeasonalityNormalizer` (отдельно от будних).
- T-12.12 [E12] **GIANT_ACTIVATION защита от рутинных постов** (проверка $N_{topic}^{baseline}$ с модификаторами × 0.8 / × 1.0).
- T-12.13 [E12] **holidays.json поддержка multi-year** (версии `v{YYYY}.json` + `HolidayCalendar` выбор по `valid_from/valid_to`).
- T-14.15 [E14] **HolidayMarker** на временных рядах UI.
- T-11.10 [E11] **ExperimentalFrameClassifier** с отдельной вкладкой (условие отображения: accuracy ≥ 50% на собственной валидации).
- Валидация ExperimentalFrameClassifier на 40–50 постах.
- Unit-тесты всех новых детекторов.

**Deliverable:** **🏁 Веха: полные аномалии, сезонность, контент**.

#### Sprint 14 (недели 27–28) — Фаза 7 (часть 1): Полный референсный корпус

**Цели:** сбор и документирование полного референса + бенчмарк NLP-моделей.

**Задачи:**
- T-16.5 [E16] **Сбор полного `reference_base.json` (15000 авторов)** со стратификацией по аудитории, региону, типу.
- T-16.5a [E16] Скрипт сбора (Python или Kotlin) — автоматизированный сбор через VK API с rate-limit + checkpoint + валидация.
- T-16.5b [E16] Расчёт квантилей `raw_quantile_statistics` для $\ln F$, $\ln \bar{r}$.
- T-16.5c [E16] Расчёт `computed_statistics_at_gamma_ref` (E-квантили при γ_ref = 0.45, $I_{base}^{ref}$ через самоориентированную Robust Sigmoid).
- T-16.5d [E16] Вычисление $\tau^{ref}_{base}$ = 75-й перцентиль $I_{base}^{ref}$.
- T-16.5e [E16] `ref_base_methodology.md` — документирование процедуры сбора.
- T-16.5f [E16] SHA-256 валидация файла.
- T-08.11 [E08] **Бенчмарк альтернативных NLP-моделей** (rubert-tiny3, ruBERT-base-cased, ru-e5-small, ru-e5-base) на тестовом корпусе.
- Решение о замене / сохранении rubert-tiny2 в v1.0.

**Deliverable:** полный `reference_base.json` + результаты бенчмарка NLP-моделей.

#### Sprint 15 (недели 29–30) — Фаза 7 (часть 2): Расширенный test_corpus + Фаза 8 (часть 1): Sensitivity harness

**Цели:** расширенный тестовый корпус + sensitivity-harness.

**Задачи:**
- T-16.6 [E16] **Расширенный `test_corpus.json`** (≥ 500 постов, 50 авторов) со следующими ground-truth метками:
    - `is_topic_relevant` (бинарно);
    - `sentiment` (POS/NEG/NEU);
    - `originality_type` (5 типов);
    - `ground_truth_roles` (для 20 авторов);
    - `ground_truth_anomalies` (для 10 пар день-событие).
- T-16.6a [E16] `test_corpus_methodology.md`.
- T-17.1 [E17] **Sensitivity-harness** (`./gradlew sensitivityReport`):
    - загрузка `test_corpus.json`;
    - прогон пайплайна с ± крайними значениями 45 параметров;
    - вычисление метрик: Chi-square распределения ролей, среднее $R$, число аномалий;
    - сохранение в `build/reports/sensitivity/sensitivity_report_<date>.md`.
- T-17.2 [E17] Интеграция sensitivityReport в CI как optional task.
- T-16.7 [E16] **holidays.json полные версии** для 2020–2030 годов.

**Deliverable:** расширенный test_corpus, рабочий sensitivity-harness.

#### Sprint 16 (недели 31–32) — Фаза 8 (часть 2): Benchmark, валидация, релиз

**Цели:** benchmark, валидация, эмпирическая калибровка, релиз v1.0.

**Задачи:**
- T-17.3 [E17] **Benchmark-harness** (`./gradlew benchmark`):
    - эталонный корпус 500 постов / 50 авторов / 30 дней baseline;
    - замер времени каждого этапа;
    - сравнение с `benchmarks/baseline.json`;
    - алерт при регрессии > 20%.
- T-17.4 [E17] **Эмпирическая калибровка порога $R^2_{MAD}$** для OrthogonalizerT на test_corpus; обновление значения в `AnalysisSession.defaults`.
- V-01 [Validation] **Валидация dostoevsky** на 50 постах VK-корпуса: accuracy + macro-F1 + bootstrap 500 CI. Результат — в `AuditLog` и `reports/validation_dostoevsky.md`.
- V-02 [Validation] **Ground-truth κ для ролей** на 30–50 акторах с 1–2 независимыми экспертами. κ Коэна + inter-rater agreement. Результат в `reports/validation_roles.md`.
- T-08.9 [E08] **Bundled Python venv** — сборка дистрибутива 700 МБ.
- T-08.10 [E08] **Bootstrap-on-first-launch** альтернатива.
- Финальный end-to-end прогон на реальных данных.
- Подготовка установщика (two-variant: bundled и bootstrap).
- T-19.1 [E19] Финальная QA-проверка.
- T-19.2 [E19] Документация пользователя (`user_manual.md`).
- T-19.3 [E19] Release notes v1.0.
- G-01…G-06 [Gate] — все gate-требования удовлетворены.

**Deliverable:** **🏁 Релиз v1.0**.

### 6.3. Обзор фазы 9 (за релизом v1.0)

Задачи Фазы 9 представляют собой задел под v2.0. Не входят в обязательства v1.0, но должны быть зарегистрированы как backlog будущего.

- T-20.1 [E20] Активация **SessionFamily** — продольный мониторинг. Механизм: отдельная сессия «потомок» ссылается на «родительскую» через `sessionFamilyId`; сравнение ролей и индексов; детекция `ROLE_SHIFT`; `PostMetricsSnapshot` при повторных запросах для скорости распространения.
- T-20.2 [E20] **ONNX Runtime for JVM** — миграция NLP из Python. Задачи: конвертация rubert-tiny2 (или замена) в ONNX; портирование dostoevsky (или замена эквивалентом); бенчмарк.
- T-20.3 [E20] **PostgreSQL миграция** для многопользовательского сценария.
- T-20.4 [E20] **MinHash LSH** для dedup на больших корпусах.
- T-20.5 [E20] **OCR multimodal** — анализ текста на изображениях.
- T-20.6 [E20] **Кросс-платформенный мониторинг** — Telegram, Одноклассники через универсальный адаптер.

Фаза 9 не имеет жёсткого спринтового плана; она рассматривается как отдельный продуктовый roadmap.

---

## 7. Полный backlog проекта

Backlog сгруппирован по эпикам. Каждая задача имеет:
- ID (формат `T-ЭПИК.НОМЕР`);
- фазу (F1…F9);
- спринт (Sprint 0…16);
- статус обязательности (обязательно в v1.0 / задел в v2.0);
- тег (MVP — обязательно в MVP-вехе; CORE — обязательно в v1.0; V2 — за v1.0).

### 7.1. E01 — Bootstrap Infrastructure

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| T-01.1 | Gradle + Kotlin 2.0 + Compose Desktop | F2 | S1 | MVP |
| T-01.2 | Koin DI | F2 | S1 | MVP |
| T-01.3 | ConfigManager + AppConfig | F2 | S1 | MVP |
| T-01.4 | Запуск приложения с пустым окном | F2 | S1 | MVP |
| T-01.5 | CI: GitHub Actions build + unit | F2 | S1 | MVP |
| T-01.6 | README, .gitignore, LICENSE | F1 | S0 | MVP |

### 7.2. E02 — Security

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| T-02.1 | MasterPasswordDialog | F2 | S1 | MVP |
| T-02.2 | TokenVault (AES-GCM + PBKDF2) | F2 | S1 | MVP |
| T-02.3 | PiiHasher (SHA-256 + сессионная соль) | F2 | S1 | MVP |
| T-02.4 | AuditLog — базовые 15 событий | F2 | S1 | MVP |
| T-02.4b | AuditLog — расширение до ~30 событий | F3 | S7 | CORE |
| T-02.5 | OAuth Authorization Code Flow + PKCE | F5 | S11 | CORE |
| T-02.6 | **Полный PiiSafeFormatter + 20 unit-тестов** | F5 | S11 | CORE |
| T-02.7 | Build-time проверка DEBUG_INCLUDES_PII в production | F5 | S11 | CORE |

### 7.3. E03 — Storage

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| T-03.1 | Database + WAL | F2 | S1 | MVP |
| T-03.2 | **Flyway V1 с полной схемой** (включая SessionFamily-задел) | F2 | S1 | MVP |
| T-03.3 | Все DAO | F2 | S1 | MVP |
| T-03.4 | Обязательные индексы | F2 | S1 | MVP |
| T-03.5 | Flyway V2 (при необходимости) | F4 | — | CORE (опционально) |
| T-03.6 | **Полный RetentionManager soft-delete + 30-дневный grace** | F5 | S11 | CORE |
| T-03.7 | UI восстановления soft-deleted сессий | F5 | S12 | CORE |

### 7.4. E04 — Observability

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| T-04.1 | Logback + logstash-encoder | F2 | S1 | MVP |
| T-04.2 | Logger + events enum | F2 | S1 | MVP |
| T-04.3 | MetricsCollector + SessionMetrics | F2 | S1 | MVP |
| T-04.4 | Ротация 50 МБ / 7 дней | F2 | S1 | MVP |
| T-04.5 | Базовая PII-санитизация | F2 | S1 | MVP |
| T-04.6 | **Полная санитизация через PiiSafeFormatter** (см. T-02.6) | F5 | S11 | CORE |

### 7.5. E05 — Orchestration

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| T-05.1 | SessionManager | F2 | S1 | MVP |
| T-05.2 | SingleInstanceLock | F2 | S1 | MVP |
| T-05.3 | **Упрощённый** ActiveSessionRegistry (boolean) | F2 | S1 | MVP |
| T-05.4 | PipelineOrchestrator (35 шагов) | F2 | S1 | MVP |
| T-05.5 | CheckpointManager | F2 | S1 | MVP |
| T-05.6 | CancellationController | F2 | S1 | MVP |
| T-05.7 | ProgressReporter | F2 | S1 | MVP |
| T-05.8 | PythonServiceManager (в Sprint 3) | F3 | S3 | MVP |
| T-05.9 | **Упрощённый** RetentionManager | F2 | S1 | MVP |
| T-05.10 | **Полный ActiveSessionRegistry с state machine** {IDLE, ANALYZING, RECOVERY_AWAITING} | F5 | S12 | CORE |
| T-05.11 | **PythonRecoveryDialog + PAUSED_PENDING_RECOVERY** | F5 | S12 | CORE |
| T-05.12 | Тайм-аут 30 мин для RECOVERY_AWAITING | F5 | S12 | CORE |
| T-05.13 | Политика конкурентности COLLECTING-сессий + Python | F5 | S12 | CORE |

### 7.6. E06 — VK Ingestion

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| T-06.1 | VkApiClient | F3 | S2 | MVP |
| T-06.2 | VkRateLimiter | F3 | S2 | MVP |
| T-06.3 | VkBackoff | F3 | S2 | MVP |
| T-06.4 | VkExecuteBatcher | F3 | S2 | MVP |
| T-06.5 | OAuth Implicit Flow в WebView | F3 | S2 | MVP |
| T-06.6 | BaselineCollector / CurrentCollector / ReposterCollector | F3 | S2 | MVP |
| T-06.7 | PaginationManager | F3 | S2 | MVP |
| T-06.8 | CollectionCheckpoint + resume | F3 | S2 | MVP |
| T-06.9 | Упрощённый DiscoveryEngine (правило 1) | F3 | S2 | MVP |
| T-06.10 | **Правила 2 и 3 + DPS** | F4 | S9 | CORE |
| T-06.11 | **Сокращённый baseline 30 дней для discovery** | F4 | S9 | CORE |
| T-06.12 | **OAuth Authorization Code + PKCE** (см. T-02.5) | F5 | S11 | CORE |

### 7.7. E07 — Preprocessing

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| T-07.1 | TextCleaner | F3 | S2 | MVP |
| T-07.2 | Tokenizer | F3 | S2 | MVP |
| T-07.3 | StopWords | F3 | S2 | MVP |
| T-07.4 | LanguageDetectorProxy FALLBACK | F3 | S2 | MVP |
| T-07.5 | LemmatizerProxy FALLBACK (Snowball) | F3 | S2 | MVP |
| T-07.6 | Upgrade для FULL через NlpService | F3 | S3 | MVP |

### 7.8. E08 — NLP Service

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| T-08.1 | NlpService интерфейс | F3 | S3 | MVP |
| T-08.2 | LocalKotlinNlpService | F3 | S3 | MVP |
| T-08.3 | NlpServiceSelector | F3 | S3 | MVP |
| T-08.4 | Python FastAPI скрипт | F3 | S3 | MVP |
| T-08.5 | requirements.txt | F3 | S3 | MVP |
| T-08.6 | PythonSidecarNlpService + shared secret | F3 | S3 | MVP |
| T-08.7 | PythonServiceManager | F3 | S3 | MVP |
| T-08.8 | Семафор 4 на Python-запросы | F3 | S3 | MVP |
| T-08.9 | **Bundled Python venv 700 МБ** | F8 | S16 | CORE |
| T-08.10 | **Bootstrap-on-first-launch альтернатива** | F8 | S16 | CORE |
| T-08.11 | **Бенчмарк альтернативных моделей** (rubert-tiny3, ruBERT-base, ru-e5-small) | F7 | S14 | CORE |
| T-08.12 | ONNX Runtime for JVM (v2.0 задел) | F9 | — | V2 |

### 7.9. E09 — Topic & Dedup

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| T-09.1 | NgramMatcher | F3 | S3 | MVP |
| T-09.2 | ExclusionFilter | F3 | S3 | MVP |
| T-09.3 | L1 + L2 combined score | F3 | S3 | MVP |
| T-09.4 | SemanticScorer через NlpService (FULL) | F3 | S3 | MVP |
| T-09.5 | TopicRelevanceFilter | F3 | S3 | MVP |
| T-09.6 | ExactHasher (Stage 1) ко всем постам ≥ 30 символов | F3 | S4 | MVP |
| T-09.7 | BoundedJaccard (Stage 2) ±72ч только к тематическим | F3 | S4 | MVP |
| T-09.8 | DedupPipeline | F3 | S4 | MVP |
| T-09.9 | MinHash LSH для больших корпусов (v2.0 задел) | F9 | — | V2 |

### 7.10. E10 — LOM Scoring

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| T-10.1 | ClosedProfileImputer $Q_{25}$ | F3 | S4 | MVP |
| T-10.2 | AudienceComponent | F3 | S4 | MVP |
| T-10.3 | EngagementDensityComponent | F3 | S4 | MVP |
| T-10.4 | **GammaCalibrator OLS** (временно для MVP) | F3 | S4 | MVP |
| T-10.4b | **GammaCalibrator через Theil-Sen + MAD-based $R^2$** | F4 | S8 | CORE |
| T-10.5 | ReferenceGammaValidator (OK-ветвь) | F3 | S4 | MVP |
| T-10.5b | ReferenceGammaValidator (все ветви) | F4 | S10 | CORE |
| T-10.6 | TopicFocusComponent с leave-one-out prior | F3 | S5 | MVP |
| T-10.7 | TopicalVolumeComponent с $k_{window}$ | F3 | S5 | MVP |
| T-10.8 | DisseminationReachComponent с пересчётом $M_{reach}$ | F3 | S5 | MVP |
| T-10.9 | ContentOriginalityComponent | F3 | S5 | MVP |
| T-10.10 | RobustNormalizer + каскад fallback | F3 | S4 | MVP |
| T-10.11 | **BootstrapEstimator упрощённый 100×30** | F3 | S4 | MVP |
| T-10.11b | **BootstrapEstimator полный 300×100** | F4 | S9 | CORE |
| T-10.12 | **BlockBootstrap упрощённый 100** | F3 | S6 | MVP |
| T-10.12b | **BlockBootstrap полный 300** | F4 | S9 | CORE |
| T-10.13 | BaseInfluenceScorer + EventActivityScorer | F3 | S4–5 | MVP |
| T-10.14 | ReferenceCalibrator OK-ветвь | F3 | S5 | MVP |
| T-10.15 | RoleClassifier (квадрантный) | F3 | S5 | MVP |
| T-10.16 | RoleCombinator (матрица 4×2) | F3 | S5 | MVP |
| T-10.17 | **RobustRegression** (Theil-Sen + Huber + MAD-based) | F4 | S8 | CORE |
| T-10.18 | **OrthogonalizerT** с условным применением | F4 | S8 | CORE |
| T-10.19a | **ReferenceCalibrator MILD_RECOMPUTED** | F4 | S10 | CORE |
| T-10.19b | **ReferenceCalibrator AUDIENCE_ONLY_REFERENCE** | F4 | S10 | CORE |
| T-10.19c | Флаг MILD_RECOMPUTED_HIGH_CORRELATION | F4 | S10 | CORE |
| T-10.20 | **ClusterRoleClassifier (GMM)** | F4 | S10 | CORE |

### 7.11. E11 — Content Analysis

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| T-11.1 | NegationHandler | F3 | S6 | MVP |
| T-11.2 | DictionarySentiment | F3 | S6 | MVP |
| T-11.3 | SidecarSentimentProxy | F3 | S6 | MVP |
| T-11.4 | **SentimentBootstrap упрощённый 10** | F3 | S6 | MVP |
| T-11.4b | **SentimentBootstrap полный 20** | F4 | S9 | CORE |
| T-11.5 | TermExtractor (TF-IDF) | F3 | S6 | MVP |
| T-11.6 | VisualActivityEstimator (VAR) | F3 | S6 | MVP |
| T-11.7 | **Простая агрегация тональности** (медиана) | F3 | S6 | MVP |
| T-11.8 | **HuberAggregator M-estimator полный** | F4 | S9 | CORE |
| T-11.10 | **ExperimentalFrameClassifier + валидация** | F6 | S13 | CORE |

### 7.12. E12 — Anomaly & Risk

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| T-12.1 | RollingZScore | F3 | S6 | MVP |
| T-12.2 | HolidayCalendar (загрузка базовой версии) | F3 | S6 | MVP |
| T-12.3 | SeasonalityNormalizer недельный паттерн | F3 | S6 | MVP |
| T-12.4 | VolumeSpikeDetector | F3 | S6 | MVP |
| T-12.5 | ToneShiftDetector± | F3 | S6 | MVP |
| T-12.6 | **Упрощённый GiantActivationDetector** | F3 | S6 | MVP |
| T-12.7 | AnomalyDeduplicator | F3 | S6 | MVP |
| T-12.8 | RiskScorer (формула $R_{multi}$) | F3 | S6 | MVP |
| T-12.9 | SignalGenerator | F3 | S6 | MVP |
| T-12.10 | RecommendationEngine | F3 | S6 | MVP |
| T-12.11 | **Праздничное среднее $\bar{x}_{holiday}$** | F6 | S13 | CORE |
| T-12.12 | **GIANT_ACTIVATION защита от рутинных постов** | F6 | S13 | CORE |
| T-12.13 | **holidays.json multi-year** | F6 | S13 | CORE |

### 7.13. E13 — Persona

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| T-13.1 | PersonaAggregator | F3 | S6 | MVP |
| T-13.2 | **PersonaHistoryManager** (задел под SessionFamily) | F5 | S12 | CORE |

### 7.14. E14 — UI

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| T-14.1 | AppTheme | F3 | S6 | MVP |
| T-14.2 | SetupScreen | F3 | S6 | MVP |
| T-14.3 | CollectionScreen | F3 | S6 | MVP |
| T-14.4 | TopicValidationScreen (30 + 40) | F3 | S6 | MVP |
| T-14.5 | LomDashboardScreen + LomTable | F3 | S6 | MVP |
| T-14.6 | ScatterPlot с CI + матрица ролей | F3 | S6 | MVP |
| T-14.7 | LomDetailScreen | F3 | S6 | MVP |
| T-14.8 | PersonaScreen | F3 | S6 | MVP |
| T-14.9 | RiskPanelScreen | F3 | S6 | MVP |
| T-14.10 | DynamicsScreen | F3 | S6 | MVP |
| T-14.11 | SessionQualityScreen с полным SQS | F3 | S7 | MVP |
| T-14.12 | Компоненты: CiBar, QualityGauge, RoleCombinationBadge, LocalRoleTooltip | F3 | S6 | MVP |
| T-14.13 | OAuthScreen с WebView | F3 | S6 | MVP |
| T-14.14 | **PythonRecoveryDialog UI** | F5 | S12 | CORE |
| T-14.15 | **HolidayMarker на графиках** | F6 | S13 | CORE |
| T-14.16 | **UX форка COMPLETED-сессии** | F5 | S12 | CORE |
| T-14.17 | Retention восстановление в настройках | F5 | S12 | CORE |

### 7.15. E15 — Export

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| T-15.1 | CsvExporter privacy-first | F3 | S6 | MVP |
| T-15.2 | SafeExporter | F3 | S6 | MVP |
| T-15.3 | **JsonExporter** | F5 | S12 | CORE |

### 7.16. E16 — Resources

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| T-16.1 | Упрощённый `reference_base.json` (50–100 авторов) | F1/F2 | S0–S4 | MVP |
| T-16.2 | Базовый `holidays.json` 2025–2026 | F3 | S3–S5 | MVP |
| T-16.3 | Базовый `sentilex_base.json` | F3 | S6 | MVP |
| T-16.4 | Минимальный `test_corpus.json` (200 постов) | F1/F3 | S0–S4 | MVP |
| T-16.5 | **Полный `reference_base.json` (15000 авторов)** | F7 | S14 | CORE |
| T-16.5a | Скрипт сбора референса | F7 | S14 | CORE |
| T-16.5b | `ref_base_methodology.md` | F7 | S14 | CORE |
| T-16.6 | **Расширенный `test_corpus.json` с ground truth** | F7 | S15 | CORE |
| T-16.6a | `test_corpus_methodology.md` | F7 | S15 | CORE |
| T-16.7 | **`holidays.json` multi-year (2020–2030)** | F7 | S15 | CORE |

### 7.17. E17 — Quality Gates

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| T-17.1 | **Sensitivity-harness** Gradle task | F8 | S15 | CORE |
| T-17.2 | Интеграция в CI | F8 | S15 | CORE |
| T-17.3 | **Benchmark-harness** Gradle task | F8 | S16 | CORE |
| T-17.4 | **Эмпирическая калибровка $R^2_{MAD}$** | F8 | S16 | CORE |

### 7.18. E18 — Validation

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| V-01 | Валидация dostoevsky (50 постов VK) | F8 | S16 | CORE |
| V-02 | Ground-truth κ для ролей (30–50 акторов + 1–2 эксперта) | F8 | S16 | CORE |
| V-03 | Валидация тематического фильтра | F3 | S7 | MVP |

### 7.19. E19 — Release Preparation

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| T-19.1 | Финальная QA | F8 | S16 | CORE |
| T-19.2 | User manual (`user_manual.md`) | F8 | S16 | CORE |
| T-19.3 | Release notes v1.0 | F8 | S16 | CORE |
| T-19.4 | Двухдистрибутивный установщик (bundled + bootstrap) | F8 | S16 | CORE |
| T-19.5 | Практическая глава диплома §2.5 (при применимости) | F8 | S16 | CORE |

### 7.20. E20 — Future Readiness (v2.0 задел)

| ID | Задача | Фаза | Спринт | Тег |
|----|--------|------|--------|-----|
| T-20.1 | SessionFamily — продольный мониторинг | F9 | — | V2 |
| T-20.2 | ONNX Runtime for JVM | F9 | — | V2 |
| T-20.3 | PostgreSQL миграция | F9 | — | V2 |
| T-20.4 | MinHash LSH | F9 | — | V2 |
| T-20.5 | OCR multimodal | F9 | — | V2 |
| T-20.6 | Кросс-платформенный мониторинг | F9 | — | V2 |

---

## 8. Критический путь полной реализации

### 8.1. Главный критический путь (к релизу v1.0)

E01 → E03 → E05 → E06 → E07 → E08 → E09 → **E10 (upgrade цикл Фазы 4)** → E11 (upgrade) → E12 (upgrade) → E14 (upgrade) → **E16 (полный референс)** → **E17+E18 (harness+validation)** → **E19 (релиз)**

**Ключевые бутылочные горлышки:**

1. **E03 Flyway V1** — если схема неверна, все последующие миграции будут сложны. **Рекомендация:** продумать всю схему в Sprint 1, чтобы миграция V1 была полной (включая задел под SessionFamily).
2. **E08 Python-сидекар** — запуск Python-стека может столкнуться с platform-specific проблемами. **Рекомендация:** R-01 spike в Sprint 0 обязателен.
3. **E16 полный референс 15000 авторов** — требует 10–15 часов VK API вызовов + проверку стратификации. **Рекомендация:** начать сбор в Sprint 13 (до S14 deliverable).
4. **E17 sensitivity-harness** — зависит от расширенного test_corpus.json. **Рекомендация:** test_corpus с ground truth должен быть готов к Sprint 15.
5. **V-02 ground-truth κ** — требует 1–2 независимых экспертов, которые могут быть недоступны. **Рекомендация:** параллельно Sprint 15; резервный план — 1 эксперт.

### 8.2. Параллелизуемые треки

- **Трек A (основной код):** следует спринтовой очереди.
- **Трек B (ресурсы):** E16 — параллельно всем остальным; сбор полного референса — в Sprint 13–14; test_corpus с ground truth — в Sprint 15.
- **Трек C (research + validation):** R-spikes в Sprint 0, 3, 4, 6; V-задачи в Sprint 7 (V-03) и Sprint 16 (V-01, V-02).
- **Трек D (documentation):** обновление документации, KDoc — на каждом спринте по мере готовности кода.

### 8.3. Важные синхронизационные точки

| Точка | Условие |
|-------|---------|
| Конец Sprint 1 | Infrastructure ready → start Sprint 2 |
| Конец Sprint 6 | **🏁 Веха MVP** |
| Конец Sprint 10 | **🏁 Веха: полный аналитический пайплайн** |
| Конец Sprint 12 | **🏁 Веха: полный UX + правовой слой** |
| Конец Sprint 13 | **🏁 Веха: полные аномалии и контент** |
| Конец Sprint 14 | **🏁 Веха: полный референс и бенчмарк NLP** |
| Конец Sprint 15 | **🏁 Веха: test_corpus + sensitivity-harness готовы** |
| Конец Sprint 16 | **🏁 Релиз v1.0** |

---

## 9. Валидация, калибровка и quality gates

Этот раздел объединяет E17 + E18 + gating-задачи как отдельный комплекс работ.

### 9.1. Валидация (V-задачи)

| ID | Задача | Метрики | Срок |
|----|--------|---------|------|
| V-01 | Валидация dostoevsky на 50 постах VK-корпуса | accuracy, macro-F1, bootstrap 500 CI | Sprint 16 |
| V-02 | Ground-truth κ для ролей, 30–50 акторов, 1–2 эксперта | κ Коэна, inter-rater agreement | Sprint 16 |
| V-03 | Валидация тематического фильтра (Bayes Beta) | Precision/Recall posterior 95% CI | Sprint 7 |

### 9.2. Sensitivity (T-17.1)

Sensitivity-harness прогоняет пайплайн на `test_corpus.json` с изменёнными параметрами по 45 позициям таблицы §28 v6. Результат — `sensitivity_report.md` с:
- Chi-square распределения ролей при отклонениях;
- среднее $R$;
- число аномалий;
- визуализация чувствительности.

### 9.3. Benchmark (T-17.3)

Benchmark замеряет время каждого этапа пайплайна на эталонном корпусе; результат сравнивается с `benchmarks/baseline.json`.

### 9.4. Эмпирическая калибровка

- T-17.4 — порог $R^2_{MAD}$ для OrthogonalizerT на реальном корпусе.
- T-08.11 — бенчмарк альтернативных NLP-моделей.

### 9.5. Gating requirements (G-задачи)

| ID | Требование | Связь |
|----|------------|-------|
| G-01 | Опубликованный `sensitivity_report.md` | T-17.1 → Sprint 15 |
| G-02 | Benchmark-отчёт валидирует NFR-03 | T-17.3 → Sprint 16 |
| G-03 | V-01 результаты в AuditLog + отчёт | Sprint 16 |
| G-04 | V-02 результаты в отчёте | Sprint 16 |
| G-05 | Бенчмарк альтернативных NLP-моделей | T-08.11 → Sprint 14 |
| G-06 | Эмпирическая калибровка $R^2_{MAD}$ | T-17.4 → Sprint 16 |

Все G-задачи должны быть удовлетворены перед релизом v1.0.

---

## 10. Release-gate требования

Перед релизом v1.0 должны быть выполнены:

### 10.1. Функциональные

- ✅ Все FR-01 … FR-15 из §3.4 v6 реализованы;
- ✅ FR-16 (фрейм-классификация) реализована как experimental feature с обязательной валидацией;
- ✅ FR-17 и FR-18 — задел в схеме БД для Фазы 9.

### 10.2. Нефункциональные

- ✅ NFR-01 … NFR-15 из §3.5 v6 удовлетворены;
- ✅ NFR-03 подтверждён benchmark-harness-ом.

### 10.3. Качественные

- ✅ Unit-покрытие `analysis/` ≥ 70% line, ≥ 60% branch;
- ✅ Integration-тесты проходят на Ktor MockEngine + SQLite in-memory;
- ✅ Lint (detekt) без warnings;
- ✅ PiiSafeFormatter — 20 unit-тестов проходят;
- ✅ Build-time проверка DEBUG_INCLUDES_PII срабатывает;
- ✅ Sensitivity-harness опубликовал актуальный отчёт;
- ✅ Benchmark-harness замерил актуальный baseline.

### 10.4. Валидационные

- ✅ V-01: accuracy dostoevsky на VK-корпусе ≥ 0.70 (или понижение в fallback с явным маркером);
- ✅ V-02: κ Коэна ≥ 0.50 с 1–2 экспертами;
- ✅ V-03: precision/recall тематического фильтра ≥ 0.70 на случайной выборке;
- ✅ Все три валидации задокументированы в `reports/validation/`.

### 10.5. Документационные

- ✅ `user_manual.md`;
- ✅ `release_notes_v1_0.md`;
- ✅ `ref_base_methodology.md` с актуальным референсом;
- ✅ `test_corpus_methodology.md`;
- ✅ `holidays_methodology.md`;
- ✅ `sensitivity_report.md` (актуальный);
- ✅ KDoc на публичный API всех модулей.

### 10.6. Дистрибутивные

- ✅ Двухдистрибутивный установщик (bundled Python + bootstrap);
- ✅ Подтверждение работы на Windows 10+, macOS 12+, Linux (Ubuntu 22+).

Релиз v1.0 объявляется только при удовлетворении всех пунктов.

---

## 11. Риски полной реализации

Расширенная матрица рисков, покрывающая весь объём работ (не только MVP).

| Риск | Вероятность | Воздействие | Фаза | Митигация |
|------|-------------|-------------|------|-----------|
| VK API изменения / deprecation методов | Низкая | Высокое | F3–F9 | Абстракция `VkApiClient`; мониторинг changelog |
| Python-ecosystem конфликты версий | Средняя | Высокое | F3, F8 | Жёсткий requirements.txt; изолированный venv |
| Сбор полного референса 15000 авторов превышает 2 недели | Средняя | Среднее | F7 | Параллельно другим задачам; прерывание после 12000 если надо |
| Bootstrap 300×100 > NFR-03 на реальных данных | Средняя | Среднее | F4 | Снижение до 200×50 или 300×50; подтверждение benchmark-harness |
| dostoevsky accuracy на VK-постах < 0.70 | Средняя | Среднее | F8 (V-01) | FALLBACK + визуальный маркер; бенчмарк альтернатив (rubert-tiny3, ruBERT-base) |
| Ground-truth κ экспертов расходится (inter-rater < 0.5) | Высокая | Среднее | F8 (V-02) | Увеличение числа экспертов до 3; сокращение до 30 акторов |
| Compose WebView + OAuth несовместим | Низкая | Высокое | F3 / F5 | Альтернативный ручной ввод токена; R-spike в Sprint 0 |
| Theil-Sen оказывается медленнее ожидаемого | Низкая | Низкое | F4 | Оптимизация (subsampling, C-реализация) |
| ClusterRoleClassifier (GMM) нестабилен на малых выборках | Средняя | Низкое | F4 | Применяется только при $n \geq 50$; предупреждение при $n < 100$ |
| MILD_RECOMPUTED приближение даёт > 20% погрешности | Средняя | Среднее | F4 | Документирование в AuditLog; ограничение применения при высокой корреляции |
| Полный PiiSafeFormatter упускает edge-case | Средняя | Высокое (правовой риск) | F5 | Минимум 20 unit-тестов + security review |
| Авторизация Authorization Code + PKCE отклонена VK | Низкая | Среднее | F5 | Сохранение Implicit Flow как fallback; мониторинг изменений VK |
| Bundled Python 700 МБ не помещается в релизный пайплайн | Низкая | Низкое | F8 | Разделение на bundled и bootstrap дистрибутивы |
| Benchmark не валидирует NFR-03 | Средняя | Среднее | F8 | Оптимизация параметров bootstrap; возможный пересмотр NFR-03 до 120 мин |
| Эмпирическая калибровка $R^2_{MAD}$ требует больше данных | Средняя | Низкое | F8 | Использование реальных полей данных апробации |
| Задержки в Sprint 8–10 (методологические расширения) | Высокая | Среднее | F4 | Буфер 1 спринт в плане |

---

## 12. Итоговый порядок разработки полного проекта

### 12.1. Концентрированная дорожная карта

| Фаза | Спринты | Длительность | Результат |
|------|---------|--------------|-----------|
| Ф1: Подготовка | S0 | 1 неделя | R-spikes, упрощённый reference, git |
| Ф2: Infrastructure | S1 | 2 недели | Рабочее приложение |
| Ф3: MVP Core | S2–S7 | 12 недель | 🏁 MVP веха |
| Ф4: Методология | S8–S10 | 6 недель | 🏁 Полный аналитический пайплайн |
| Ф5: Security+UX | S11–S12 | 4 недели | 🏁 Полный UX+правовой |
| Ф6: Аномалии+Content | S13 | 2 недели | 🏁 Полные аномалии |
| Ф7: Ресурсы | S14–S15 | 4 недели | 🏁 Полный референс+test_corpus |
| Ф8: Quality Gates | S16 | 2 недели | 🏁 **Релиз v1.0** |
| Ф9: v2.0 задел | — | отдельно | SessionFamily, ONNX, MinHash, OCR, PostgreSQL |

**Общая длительность до v1.0: 1 (S0) + 16 × 2 = 33 недели ≈ 8 месяцев.**

### 12.2. Распределение объёма по фазам

- **F1–F3 (MVP Core):** ~55% общего объёма кода.
- **F4 (Методология):** ~20%.
- **F5 (Security+UX):** ~10%.
- **F6 (Аномалии):** ~5%.
- **F7 (Ресурсы):** ~5%.
- **F8 (Gates):** ~5%.

### 12.3. Рекомендуемый порядок старта

1. **День 1** — чтение v6 в деталях.
2. **Дни 2–7 (Sprint 0)** — R-spikes + первичные ресурсы + git.
3. **Недели 1–2 (Sprint 1)** — инфраструктура.
4. **Недели 3–14 (Sprint 2–7)** — MVP Core.
5. **Недели 15–20 (Sprint 8–10)** — Фаза 4 методологии.
6. **Недели 21–24 (Sprint 11–12)** — Фаза 5 безопасности.
7. **Недели 25–26 (Sprint 13)** — Фаза 6 аномалии+контент.
8. **Недели 27–30 (Sprint 14–15)** — Фаза 7 ресурсы.
9. **Недели 31–32 (Sprint 16)** — Фаза 8 gates + релиз.

### 12.4. Критерии готовности v1.0

v1.0 считается выпущенным, когда:
- все задачи с тегом **[CORE]** завершены;
- все задачи с тегом **[MVP]** завершены (они входят в CORE);
- все gating-требования G-01…G-06 удовлетворены;
- release-gate требования §10 соблюдены.

Задачи с тегом **[V2]** не требуются для v1.0 и формируют roadmap v2.0.

---

## 13. Заключение

Настоящий документ преобразует спецификацию `lom_architecture_v6.md` в практический план полноценной разработки от старта до релиза v1.0. Отличия от предыдущего MVP-плана:

- **Охват:** 100% v6 вместо ~70% MVP-подмножества.
- **Фазы:** 9 фаз (включая задел под v2.0) вместо 8 спринтов.
- **Спринты:** 16 полных спринтов (S1–S16) + S0 подготовки, общей длительностью 33 недели.
- **Вехи:** 7 явных вех внутри плана, включая MVP как промежуточную.
- **Backlog:** ~150 задач (80 MVP + 70 CORE), сгруппированных по 20 эпикам.
- **Quality gates:** выделены как самостоятельные эпики E17–E19.
- **Будущее (v2.0):** эпик E20 с заделом.

MVP сохраняется как важная веха для внутренней демонстрации и возможной защиты дипломного проекта, но не является конечной целью. Полный план охватывает все элементы v6, включая методологические расширения (Theil-Sen, Huber, GMM, MILD_RECOMPUTED), правовые и безопасностные улучшения (полный PiiSafeFormatter, OAuth+PKCE, Retention с grace), расширения аномалий и сезонности (праздничное среднее, GIANT routine protection, holidays multi-year), ресурсный пакет (полный референс 15000, расширенный test_corpus), quality gates (sensitivity и benchmark harness) и всю необходимую валидацию.

Разработка может начинаться немедленно с Sprint 0. План содержит достаточно деталей для автономного ведения без необходимости возврата к архитектурному документу на каждом шаге.

---

*Настоящий документ является авторитетной дорожной картой разработки всего проекта. Первое плановое обновление после завершения Фазы 3 (Sprint 7) — ориентировочно через 14 недель от старта. Последующие обновления — на каждой вехе.*
