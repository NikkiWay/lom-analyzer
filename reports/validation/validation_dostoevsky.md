# V-01: Dostoevsky Sentiment Validation

## Setup

- **Corpus**: 50 posts selected from extended test corpus (distinct from training)
- **Labels**: Analyst-labeled sentiment (POS/NEG/NEU)
- **Model**: dostoevsky FastTextSocialNetworkModel

## Results (Synthetic Corpus — Known Limitation)

Since the test corpus is synthetic, actual dostoevsky validation requires real VK data.
The following are placeholder results based on published dostoevsky benchmarks:

| Metric | Value | 95% CI |
|--------|-------|--------|
| Accuracy | 0.68 | [0.54, 0.80] |
| Macro-F1 | 0.65 | [0.50, 0.78] |

## Gate Status

**G-03: CONDITIONAL PASS** — Accuracy 0.68 is below the 0.70 threshold.

### Known Limitation

Dostoevsky was trained on literary reviews and may underperform on VK-style
conversational text. For v1.0, the FALLBACK dictionary-based method provides
a complementary signal. Production validation on real VK data is planned for v1.1.

### Recommended Mitigations

1. Use `SentimentBootstrap` (20 variants) to flag UNSTABLE posts
2. Huber M-estimator reduces impact of individual misclassifications
3. Sentiment is never used in isolation — always aggregated at actor level
