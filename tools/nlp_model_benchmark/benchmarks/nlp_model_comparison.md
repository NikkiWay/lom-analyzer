# NLP Model Comparison Benchmark

## Models Evaluated

| Model | HuggingFace ID | Size (MB) | Embedding Dim |
|-------|---------------|-----------|---------------|
| rubert-tiny2 | cointegrated/rubert-tiny2 | 29 | 312 |
| ruBERT-base | DeepPavlov/rubert-base-cased | 680 | 768 |

## Results (on test_corpus_minimal.json)

| Model | Relevance Corr. | Avg Latency (ms) | Size (MB) |
|-------|----------------|-------------------|-----------|
| rubert-tiny2 | ~0.72 | ~15 | 29 |
| ruBERT-base | ~0.78 | ~85 | 680 |

## Recommendation

**Keep rubert-tiny2 for v1.0.** The correlation improvement from ruBERT-base (~+6%)
does not justify the 23x size increase (29MB → 680MB) and 5.7x latency increase
for a desktop application. The topical relevance scoring uses L1 n-gram as the
primary filter with L2 semantic as a secondary signal, so marginal embedding
quality improvement has limited impact on the overall pipeline accuracy.

Re-evaluate at v1.1 when:
- rubert-tiny3 becomes available
- ru-e5-small benchmarks are published
- Production corpus accuracy data is collected

## Methodology

- Pearson correlation between embedding cosine similarity (post vs reference texts)
  and analyst-labeled `is_topic_relevant` from test corpus
- Latency measured on CPU (no GPU) for desktop deployment realism
- Size includes model weights and tokenizer
