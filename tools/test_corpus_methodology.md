# Test Corpus Methodology

## Overview

The extended test corpus contains 500 synthetic posts from 50 authors over 60 days, designed to exercise all analytical pipeline components per architecture v6 §9.6.

## Construction

### Synthetic Generation
- **Posts**: 500 posts (10 per author) with Russian-language text covering ecology, politics, daily life
- **Authors**: 50 authors with followers_count ranging 100–50,000
  - 30 SEED, 20 DISCOVERY
- **Communities**: 10 communities with 500–50,000 members
- **Period**: 60 days (June 1 – July 31, 2025)

### Ground Truth Labels
Every post carries:
- `is_topic_relevant`: 40% true (ecology-related content)
- `sentiment`: POSITIVE/NEGATIVE/NEUTRAL (roughly equal distribution)
- `originality_type`: ORIGINAL (70%), REPOST_WITH_COMMENT (15%), PURE_REPOST (10%), DETECTED_COPY (5%)

### Role Ground Truth
20 of 50 authors have labeled expected roles:
- TOPIC_DRIVER, AUTHORITATIVE_LOM, SLEEPING_GIANT, BACKGROUND

### Anomaly Ground Truth
10 day-event pairs with expected anomaly types:
- VOLUME_SPIKE, TONE_SHIFT_NEGATIVE

## Validity Considerations

1. **Synthetic limitation**: The corpus does not capture real VK discourse patterns. Use for pipeline testing only, not for method validation.
2. **Text quality**: Generated texts are grammatically correct but may lack authentic VK style.
3. **Scale**: 500 posts is below the typical production workload (5,000-50,000). Performance benchmarks should use larger synthetic corpora.
4. **PII**: All author IDs are synthetic; no real VK user data is included.

## SHA-256 Verification

The corpus file includes a `sha256` field. After final freeze, compute:
```bash
sha256sum src/main/resources/resources/test_corpus.json
```
and update the field.
