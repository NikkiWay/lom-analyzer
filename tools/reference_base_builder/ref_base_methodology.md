# Reference Base Methodology

Per architecture v6 §17.3.

## Sampling Strategy

- **Total sample**: 15,000 VK public accounts
- **Stratification**:
  - Audience: <1k (35%), 1k-10k (40%), 10k-100k (20%), >100k (5%)
  - Region: Moscow (30%), St. Petersburg (8%), regions (62%)
  - Type: personal (60%), community (40%)

## Data Collection

For each account:
1. Fetch `followers_count` (current)
2. Fetch last 6 months of posts via `wall.get`
3. Compute `N_all` (total posts) and `total_weighted_reactions = sum(likes + 2*reposts + 1.5*comments)`
4. Compute `r_bar = total_weighted_reactions / N_all`

## Computation

1. `ln_F = ln(1 + followers_count)`
2. `ln_r_bar = ln(1 + r_bar)`
3. `E_raw = ln_r_bar - 0.45 * ln_F` (at reference gamma = 0.45)
4. Quantiles (q10, q25, q50, q75, q90, IQR) for ln_F, ln_r_bar, E_raw
5. Self-normalized I_base^ref via Robust Sigmoid using reference-internal stats
6. `tau_base_p75 = Q75(I_base^ref)`
7. `F_tilde_reference = median(followers_count)` across all accounts

## Output Schema

```json
{
  "version": "1.0.0",
  "collected_at": "YYYY-MM-DD",
  "sample_size": 15000,
  "gamma_used_in_collection": 0.45,
  "raw_quantile_statistics": {
    "ln_F": {"q10": ..., "q25": ..., "q50": ..., "q75": ..., "q90": ..., "iqr": ...},
    "ln_r_bar": {"q10": ..., "q25": ..., "q50": ..., "q75": ..., "q90": ..., "iqr": ...}
  },
  "computed_statistics_at_gamma_ref": {
    "E_raw_at_gamma_0_45": {"q10": ..., "q25": ..., "q50": ..., "q75": ..., "q90": ..., "iqr": ...},
    "I_base_at_gamma_0_45": {"p50": ..., "p75": ..., "p90": ...}
  },
  "I_base_thresholds": {
    "tau_base_p75_at_gamma_ref": ...,
    "F_p75": ...
  }
}
```

## Checkpointing

The build uses file-based checkpoints every 500 accounts. On resume, skip already-processed accounts.

## Expected Runtime

- ~4-8 hours at 3 req/s with execute batching
- Requires VK API token with `wall`, `groups`, `users` permissions
