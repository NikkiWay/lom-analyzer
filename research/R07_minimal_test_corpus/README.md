# R07 – Minimal Test Corpus

`test_corpus_minimal.json` is a **minimal** synthetic test corpus for early
development and unit testing of the LOM v6 analysis pipeline.

## Contents

- **50 posts** across **5 authors**
- Topic: "экология" (ecology)
- Ground truth labels:
  - `is_topic_relevant`: 25 true / 25 false
  - `sentiment`: POSITIVE / NEUTRAL / NEGATIVE distribution
- Schema follows LOM v6 Architecture §9.6

## Purpose

This corpus is intentionally small — just enough to exercise the topic filter,
sentiment classifier, and sensitivity harness during Sprints 1–3.

## Expansion plan

A full 500-post corpus with 50 authors and 10 communities will be assembled
during Sprint 15 (validation sprint), per the implementation roadmap in
`lom_v6_full_implementation_plan.md`. The full corpus will include:

- Manually annotated or expert-verified ground truth
- `ground_truth_roles` for role classification validation
- `ground_truth_anomalies` for anomaly detection validation
- PII-hashed real VK data or high-fidelity synthetic data

## Schema reference

See `lom_architecture_v6.md` §9.6 for the full `test_corpus.json` schema.
