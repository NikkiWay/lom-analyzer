# LOM Analyzer v1.0 — Release Notes

## Features

### Core Analytical Pipeline
- Two-dimensional influence model (I_base + I_event) with separate baseline/current windows
- Theil-Sen robust regression for gamma calibration with MAD-based R² diagnostics
- T orthogonalization with permutation test for significance
- Full two-level bootstrap (300x100) with parallelized CI computation
- Robust Sigmoid normalization with cascade fallback and CV_IQR stability flags
- 8-role classification via 4x2 matrix with sqrt(n_eff) confidence penalty
- GMM alternative for role classification (n_eff >= 50)
- Three-branch reference calibration (OK / MILD_RECOMPUTED / AUDIENCE_ONLY)
- Leave-one-out prior for topic focus with k_window normalization

### Content Analysis
- Dictionary sentiment (200 RuSentiLex lemmas) with negation handling
- Dostoevsky model sentiment via Python sidecar (FULL mode)
- Huber M-estimator for actor-level sentiment aggregation
- 20-variant sentiment bootstrap for reliability assessment
- TF-IDF term extraction, visual activity ratio
- Experimental frame classifier (6 frames) with validation gating

### Anomaly Detection
- Volume spike (z > 2.5), tone shift positive/negative (z > 2.0)
- Giant activation with routine protection (N_topic_baseline guard)
- Anomaly deduplication (ABSORBED_BY_GIANT)
- Holiday-aware seasonality with separate holiday mean
- Multi-year holiday calendar (2020-2030)

### Risk Scoring
- R_multi formula with coefficient 1.2 for multi-source amplification
- Block bootstrap (300 iterations, 7-day blocks) with BORDERLINE flags
- Draft recommendations tagged [ЧЕРНОВИК]

### Security & Legal
- AES-256-GCM token vault with PBKDF2 (100k iterations)
- OAuth Authorization Code + PKCE (Implicit Flow fallback)
- PII masking in logs (vk_id, screen_name, names, long text)
- Build-time PII verification
- Soft-delete → 30-day grace → hard-delete retention lifecycle
- Session forking with traceability

### User Interface
- 9 screens: Setup, Collection, Validation, Dashboard, Detail, Persona, Risk, Dynamics, Quality
- Sortable LOM table, role badges (8 colors), CI bars, risk cards
- State-based navigation with sidebar
- Python recovery dialog with fallback confirmation

## Known Limitations

1. **Dostoevsky accuracy**: 0.68 on synthetic data (< 0.70 target). Mitigated by SentimentBootstrap + Huber aggregation. Production validation planned for v1.1.
2. **MILD_RECOMPUTED approximation**: E-quantile recomputation assumes independence; accuracy may exceed 15% when ln_F and ln_r_bar are correlated (flag: MILD_RECOMPUTED_HIGH_CORRELATION).
3. **OAuth Implicit Flow**: deprecated in OAuth 2.1 but still supported by VK API 5.199+. PKCE is primary; Implicit is fallback.
4. **Bundled Python size**: ~700 MB. Bootstrap variant available at ~100 MB.
5. **ScatterPlot rendering**: data model ready; Lets-Plot Batik SwingPanel integration pending.
6. **Synthetic test corpus**: validation results based on synthetic data; production validation required.

## Tech Debt

| Item | Priority | Plan |
|------|----------|------|
| OAuth Implicit fallback removal | Medium | Remove when VK drops support |
| rubert-tiny2 → alternative | Low | Re-evaluate at v1.1 |
| Bundled Python 700 MB | Medium | ONNX runtime for sentiment in v1.2 |
| ViewModels for screens | Medium | Wire DB-backed StateFlow VMs |

## Future Roadmap (v1.1+)

- **SessionFamily**: longitudinal monitoring across sessions
- **ONNX Runtime**: replace Python sidecar for sentiment
- **MinHash LSH**: O(n log n) near-duplicate detection
- **OCR**: image text extraction for multimodal analysis
- **PostgreSQL**: for multi-user deployment
- **Dark mode**: UI theme variant
