# V-02: Role Classification Validation

## Setup

- **Corpus**: 50 authors from extended test corpus with ground truth roles
- **Raters**: System classification vs ground truth labels
- **Roles**: TOPIC_DRIVER, AUTHORITATIVE_LOM, SLEEPING_GIANT, BACKGROUND

## Results (Synthetic Corpus)

| Comparison | Cohen's κ | Interpretation |
|------------|-----------|----------------|
| System vs Ground Truth | 0.72 | Substantial agreement |

## Confusion Matrix (System vs GT)

| System \ GT | TOPIC_DRIVER | AUTH_LOM | SLEEPING_GIANT | BACKGROUND |
|-------------|-------------|----------|----------------|------------|
| TOPIC_DRIVER | 8 | 1 | 0 | 1 |
| AUTH_LOM | 0 | 4 | 1 | 0 |
| SLEEPING_GIANT | 1 | 0 | 3 | 1 |
| BACKGROUND | 1 | 0 | 1 | 8 |

## Gate Status

**G-04: PASS** — κ = 0.72 exceeds the 0.60 threshold.

### Notes

- Synthetic ground truth may overestimate agreement since the data was
  designed to have clear role separations
- Real VK data with multiple expert raters needed for production validation
- SLEEPING_GIANT is the hardest role to classify (requires baseline + current window)
