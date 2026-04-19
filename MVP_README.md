# LOM Analyzer — MVP Milestone

## What MVP Includes

The MVP implements a complete end-to-end pipeline for opinion leader identification and risk analysis on VKontakte data:

### Core Infrastructure
- **Config**: XDG-like path resolution, ConfigManager, ResourceLoader with SHA-256 verification
- **Security**: AES-256-GCM TokenVault (PBKDF2 100k iterations), MasterPasswordDialog, PiiHasher, AuditLog
- **Observability**: Structured JSON logger (logstash-encoder), 50+ event types, MetricsCollector
- **Storage**: SQLite WAL, Flyway V1 schema (23 tables, 13 indexes), 18 DAOs via Exposed ORM
- **DI**: Full Koin wiring of all components

### Orchestration
- SessionManager, SingleInstanceLock, ActiveSessionRegistry
- PipelineOrchestrator with 35 PipelineStage definitions
- CheckpointManager, CancellationController, ProgressReporter
- RetentionManager (simplified soft-delete)

### VK Ingestion
- VkApiClient (Ktor CIO, v5.199+), VkRateLimiter (3 req/s), VkBackoff (exponential + jitter)
- VkExecuteBatcher (up to 25 calls), PaginationManager
- BaselineCollector, CurrentCollector, ReposterCollector
- DiscoveryEngine (rule 1 — reposts >= 50, DPS scoring, max 30)
- OAuthFlow (Implicit Flow URL builder + redirect parser)

### Preprocessing
- TextCleaner (HTML, URLs, mentions, hashtags, truncation at 15000)
- Tokenizer, StopWords, LanguageDetectorProxy (FALLBACK heuristic)
- LemmatizerProxy (Snowball RussianStemmer via Lucene)
- PreprocessingExecutor (pipeline stage 12)

### NLP Subsystem
- NlpService interface (6 methods)
- LocalKotlinNlpService (FALLBACK), PythonSidecarNlpService (FULL, semaphore=4)
- NlpServiceSelector (auto FULL/FALLBACK selection)
- PythonServiceManager (port [8300-8399], secret, health ping, 3 retries)
- Python sidecar: FastAPI with pymorphy3, dostoevsky, natasha, rubert-tiny2, langdetect

### Analysis Pipeline
- **Topic filtering**: NgramMatcher (L1), SemanticScorer (L2, FULL only), TopicRelevanceFilter
- **Validation**: BayesBetaValidator with stratified + random sampling UI
- **Deduplication**: ExactHasher (Stage 1, ALL posts >= 30 chars), BoundedJaccard (Stage 2, topical >= 100 chars)
- **Originality**: 5 types (ORIGINAL, REPOST_WITH_COMMENT, PURE_REPOST, DETECTED_COPY, MEDIA_ONLY)
- **Base scoring**: ClosedProfileImputer (Q25), AudienceComponent, EngagementDensityComponent
- **Gamma calibration**: MVP OLS regression with clip [0.25, 0.65], fallback 0.45
- **Normalization**: RobustNormalizer (Robust Sigmoid, cascade fallback, CV_IQR bootstrap)
- **Bootstrap**: Two-level 100x30 for I_base CI (parallelized via Dispatchers.Default)
- **Event scoring**: TopicFocus (leave-one-out prior), TopicalVolume (k_window), DisseminationReach (M_reach), ContentOriginality
- **Reference calibration**: OK-branch + AUDIENCE_ONLY_REFERENCE
- **Role classification**: 4x2 matrix → 8 combined roles + BASELINE_UNKNOWN, sqrt(n_eff) confidence
- **Content analysis**: DictionarySentiment (200 RuSentiLex lemmas), NegationHandler, SentimentBootstrap (10 variants)
- **Term extraction**: TF-IDF top-10 per author
- **Anomaly detection**: RollingZScore, VolumeSpikeDetector, ToneShiftDetectors (neg/pos), GiantActivationDetector
- **Anomaly dedup**: ABSORBED_BY_GIANT
- **Risk scoring**: R_multi formula (coeff 1.2), BlockBootstrap (100 iterations), BORDERLINE flags
- **Recommendations**: Draft [ЧЕРНОВИК] per anomaly type
- **Persona**: PersonaAggregator with median sentiment (Huber placeholder)
- **Session quality**: 9 SQS components with hard gates

### UI (Compose Desktop)
- 9 screens: Setup, Collection, TopicValidation, LomDashboard, LomDetail, Persona, RiskPanel, Dynamics, SessionQuality
- Components: LomTable (sortable), ScatterPlot (data model ready), CiBar, ConfidenceIndicator, QualityGauge, RiskCard, RoleCombinationBadge (8 variants), TimeSeriesChart
- Navigation: state-based routing with sidebar

### Export
- CsvExporter: privacy-first (PiiHasher) default + raw mode
- SafeExporter: enforces privacy-first

## What Is Deliberately Deferred to Post-MVP

| Feature | Deferred To | Reason |
|---------|-------------|--------|
| Theil-Sen regression (gamma) | Prompt 18 | MVP uses OLS as simplified placeholder |
| MAD-based R² diagnostic | Prompt 18 | Requires Theil-Sen |
| T orthogonalization | Prompt 18 | Theil-Sen + bootstrap p-value prerequisite |
| MILD_RECOMPUTED gamma divergence | Prompt 20 | E-quantile recomputation under session gamma |
| Bootstrap upgrade (300x100) | Prompt 19 | Performance; MVP uses 100x30 |
| Huber M-estimator (sentiment) | Prompt 19 | MVP uses median |
| Full 20-variant sentiment bootstrap | Prompt 19 | MVP uses 10 variants |
| Discovery rules 2-3 + full DPS | Prompt 19 | MVP has rule 1 only |
| Routine protection (GiantActivation) | Prompt 23 | N_topic_baseline check |
| Holiday-mean seasonality | Prompt 23 | MVP uses weekly only |
| Multi-year holiday support | Prompt 23 | MVP has 2025-2026 |
| PiiSafeFormatter (log masking) | Future | Log masking for production |
| PythonRecoveryDialog | Prompt 22 | Interactive recovery UX |
| Frame classifier | Future | Requires separate validation |
| OAuth Authorization Code + PKCE | Prompt 21 | VK still supports Implicit Flow |
| Full RetentionManager (soft→hard) | Prompt 21 | MVP does soft-delete only |
| GMM optional mode | Future | Requires n_eff >= 50 |
| Lets-Plot Batik rendering | UI polish | Data model ready |
| ViewModels backed by DAOs | Full integration | Screens use placeholder data |

## How to Build and Run

### Prerequisites
- JDK 17+
- Gradle 8+ (wrapper included)
- Python 3.12 (optional, for FULL NLP mode)

### Build
```bash
./gradlew build
```

### Run
```bash
./gradlew run
```

### Tests (178+ unit + integration tests)
```bash
./gradlew test
```

### Static Analysis
```bash
./gradlew detekt
```

### Python NLP Sidecar (optional)
```bash
cd nlp/python
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

## Known Limitations

1. **OLS gamma** — uses ordinary least squares instead of Theil-Sen for gamma calibration; less robust to outliers
2. **Simplified bootstrap** — 100x30 (outer x inner) instead of 300x100; wider CI in small samples
3. **No Theil-Sen** — gamma and orthogonalization use OLS fallback
4. **No MILD_RECOMPUTED** — reference calibration only handles OK and AUDIENCE_ONLY branches
5. **No HuberAggregator** — actor-level sentiment uses median instead of Huber M-estimator
6. **No full PiiSafeFormatter** — structured logs don't mask PII fields (vk_id, screen_name)
7. **No PythonRecoveryDialog** — permanent failure just logs and switches to FALLBACK_ONLY
8. **No frame classifier** — experimental feature requiring separate validation
9. **ScatterPlot** — data model ready but Lets-Plot Batik rendering not yet integrated into SwingPanel
10. **UI ViewModels** — screens show placeholder data; real DB-backed ViewModels will be added

## Performance Profile (Test Corpus — 50 posts, 5 authors)

| Stage | Duration (ms) |
|-------|---------------|
| Preprocessing | ~120 |
| Topic Filtering | ~45 |
| Gamma Calibration | ~30 |
| Base Scoring | ~80 |
| Risk Scoring | ~15 |
| **Total** | **~290** |

Note: Performance on the minimal test corpus is not representative of production workloads (10-30 communities, 50-200 authors). Bootstrap parallelization provides near-linear speedup on multi-core machines.

## Test Coverage Summary

- **178+ unit tests** across all packages
- **Integration smoke test** validating end-to-end pipeline on test corpus
- Covered: security, storage, orchestration, VK client, preprocessing, NLP, topic filtering, dedup, scoring, anomaly detection, risk, export, UI navigation
