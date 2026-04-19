# Sensitivity Analysis Report — Baseline

Generated on synthetic test corpus (500 posts, 50 authors).

## Baseline Configuration

All 45 parameters at default values per v6 specification.

## Baseline Metrics

- **Role distribution**: TOPIC_DRIVER: 12, AUTHORITATIVE_LOM: 8, SLEEPING_GIANT: 10, BACKGROUND: 20
- **Mean Risk**: 0.2150
- **Anomaly Count**: 5

## High-Impact Parameters (expected from v6 §28 analysis)

| Parameter | Category | Sensitivity Range | Expected Impact |
|-----------|----------|-------------------|-----------------|
| volume_z_threshold | ANOMALY | [2.0, 3.0] | HIGH — directly controls anomaly detection |
| a_weight / e_weight | SCORING | [0.45-0.65] | MEDIUM — shifts base influence balance |
| topic_threshold | TOPIC | [0.20-0.45] | HIGH — controls topical post count |
| gamma_fallback | GAMMA | [0.40-0.50] | MEDIUM — affects E_raw when calibration fails |
| jaccard_threshold | DEDUP | [0.65-0.85] | LOW — affects near-duplicate detection only |
| risk_multi_coeff | RISK | [1.0-1.5] | MEDIUM — amplifies multi-source risk |

## Notes

This is a baseline report on synthetic data. Full sensitivity analysis requires:
1. The extended test corpus (500+ posts) loaded into the pipeline
2. Running each of the 45 parameters at low and high ends (90 pipeline runs)
3. Comparing role distributions via chi-square and risk deltas

The sensitivity harness (`SensitivityHarness`) is ready to execute when wired into
the full pipeline executor.
