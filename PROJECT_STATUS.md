# LOM Analyzer — Project Status

**Milestone: Full Analytical Pipeline** (post-MVP Phase 4 complete)

## What Is Implemented

The system implements a complete end-to-end pipeline for opinion leader identification and risk analysis on VKontakte data, including all methodological extensions:

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
- **Gamma calibration**: Theil-Sen primary (≥20 authors), OLS fallback (<20), MAD-based R², bootstrap 1000
- **Normalization**: RobustNormalizer (Robust Sigmoid, cascade fallback, CV_IQR bootstrap)
- **Bootstrap**: Full two-level 300×100 for I_base CI (parallelized via Dispatchers.Default)
- **Event scoring**: TopicFocus (leave-one-out prior), TopicalVolume (k_window), DisseminationReach (M_reach), ContentOriginality
- **T orthogonalization**: Theil-Sen + MAD R² + permutation p-value → Set A/B weight selection
- **Reference calibration**: Full 3-branch (OK / MILD_RECOMPUTED / AUDIENCE_ONLY_REFERENCE)
- **MILD_RECOMPUTED**: E-quantile recomputation under session γ with correlation safeguard
- **GMM role classification**: 2-3 component EM on (I_base, I_event), BIC selection, n_eff >= 50
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

## What Is Deliberately Deferred

| Feature | Status | Notes |
|---------|--------|-------|
| Theil-Sen regression (gamma) | DONE | Primary for ≥20 authors, OLS fallback |
| MAD-based R² diagnostic | DONE | Replaces OLS R² |
| T orthogonalization | DONE | Theil-Sen + permutation p-value |
| MILD_RECOMPUTED gamma divergence | DONE | Full 3-branch with correlation safeguard |
| Bootstrap upgrade (300x100) | DONE | Parallelized across CPU cores |
| Huber M-estimator (sentiment) | DONE | With bootstrap 200 CI |
| Full 20-variant sentiment bootstrap | DONE | 3 negator sets × 3 windows × 3 thresholds |
| Discovery rules 2-3 + full DPS | DONE | All 3 rules + DPS ranking |
| GMM role classification | DONE | 2-3 component EM, BIC selection |
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

## MILD_RECOMPUTED Approximation Limits

The MILD_RECOMPUTED branch (0.1 < |Δγ| ≤ 0.2) recomputes E-quantiles under session γ using the independence approximation:

```
q_p(E|γ_sess) ≈ q_p(ln_r_bar) - γ_sess * q_p(ln_F)
```

**Limitations:**
1. This approximation assumes quantile additivity, which holds exactly only for independent variables. When ln_F and ln_r_bar are correlated (common in social media), the approximation error increases.
2. When the IQR ratio proxy exceeds 0.6 (suggesting high correlation), the flag `MILD_RECOMPUTED_HIGH_CORRELATION` is set, warning that approximation accuracy may exceed 15%.
3. The reference threshold τ^ref_base = 0.78 is used as-is (not recomputed), with flag `REF_THRESHOLD_APPROXIMATED`.
4. For production use with high-stakes decisions, prefer collecting a fresh reference base at the session γ rather than relying on the MILD_RECOMPUTED approximation.

## Concurrency Policy

- Only one session may be in ANALYZING state at a time (enforced by ActiveSessionRegistry).
- Two sessions in COLLECTING phase may run in parallel but share the Python sidecar semaphore (4 permits).
- RECOVERY_AWAITING blocks new ANALYZING attempts until resolved (30-minute timeout → auto-CANCELLED).
- PAUSED_PENDING_RECOVERY sessions persist across app restarts and prompt the user on next launch.

## Reference Base Builder

### Running
```bash
export VK_TOKEN=your_vk_api_token
cd tools/reference_base_builder
# Run via Kotlin script or compile as standalone JAR
```

### Expected Runtime
- 4-8 hours at 3 req/s with execute batching (15,000 accounts)
- Requires VK API token with `wall`, `groups`, `users` permissions
- Checkpointing every 500 accounts for resumability
- Output: `reference_base.json` with SHA-256

### Methodology
See `tools/reference_base_builder/ref_base_methodology.md`

## NLP Model Benchmark

### Running
```bash
cd tools/nlp_model_benchmark
# Requires Python environment with sentence-transformers
```

### Current Recommendation
**Keep rubert-tiny2 for v1.0.** The ~6% correlation improvement from ruBERT-base
does not justify the 23x size increase (29MB → 680MB) for a desktop application.
See `tools/nlp_model_benchmark/benchmarks/nlp_model_comparison.md`.

## Sensitivity Analysis Harness

### Running
The sensitivity harness tests 45 pipeline parameters at low/high sensitivity ranges:
```bash
./gradlew sensitivityReport
```

### Parameters
45 parameters across 10 categories: TOPIC, GAMMA, NORM, BOOTSTRAP, SCORING, ANOMALY, RISK, DEDUP, REFERENCE, ROLE, ORIGINALITY, SENTIMENT, DISCOVERY.

### Output
Reports are generated at `reports/sensitivity/sensitivity_report_<timestamp>.md` with:
- Chi-square role distribution comparison
- Mean risk delta
- Anomaly count delta
- Impact classification (HIGH/MEDIUM/LOW)

### Extended Test Corpus
- 500 posts, 50 authors, 10 communities, 60 days
- Ground truth: topic relevance, sentiment, originality, roles (20), anomalies (10)
- Located at `src/main/resources/resources/test_corpus.json`
- Methodology: `tools/test_corpus_methodology.md`

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
