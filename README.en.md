# LOM Analyzer

[![Build & Test](https://github.com/NikkiWay/lom-analyzer/actions/workflows/build.yml/badge.svg)](https://github.com/NikkiWay/lom-analyzer/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF.svg)](https://kotlinlang.org)
[![JVM](https://img.shields.io/badge/JVM-17-orange.svg)](https://adoptium.net)

**A desktop application that quantitatively identifies opinion leaders on VKontakte.**

It collects posts on a given topic, finds the authors discussing it, and measures their influence along four independent axes. For each author the output is: 11 quantitative scores, a role in the discussion, confidence intervals for the scores computed from a sample, and a data-sufficiency label.

[Русская версия](README.md) · [Algorithm](docs/algorithm.md) · [Formulas](docs/formulas.md) · [Architecture](docs/architecture.md)

> Documentation, code comments and the user interface are in Russian: the tool analyses Russian-language content and was built as a master's thesis project at ITMO University. This file summarises the project in English.

---

## Contents

- [What it does](#what-it-does)
- [Method](#method)
- [Interface](#interface)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Build and run](#build-and-run)
- [Tests and static analysis](#tests-and-static-analysis)
- [Privacy and data](#privacy-and-data)
- [Known limitations](#known-limitations)
- [License](#license)

---

## What it does

The analyst defines a topic (keywords plus reference texts), a time period and optionally a set of communities. The application then runs ten stages: it collects posts, resolves their authors, fetches profiles and comments, cleans and lemmatises the texts, selects topical posts, computes the scores, derives confidence intervals by bootstrap, assigns roles and exports the result.

The method rests on a **four-dimensional model of influence**. Each axis describes a different facet of an author and is measured independently of the others: position in the network (axis 1), involvement in the specific topic (axis 2), the stance expressed (axis 3), and the audience's reaction to it (axis 4). A role follows from the combination of axes, so an author with a large audience outside the topic and an author with a small audience at the centre of the discussion resolve to different roles.

Composite weights are fixed at 1/3 each (OECD Handbook practice), and role thresholds are derived from the session's own data. The application exposes no tunable parameters that affect the result.

## Method

### Four axes, 11 scores

| Axis | Score | Formula | Meaning |
|---|---|---|---|
| **1. Structural influence** | `Aud_a` | `ln(1 + F_a)` | Audience size, log-compressed tail |
| | `Age_a` | `d_a / max_b(d_b)` | Account age, normalised to the session maximum |
| | `ER_a^bg` | `avg((L+C+R) / F_a)` over baseline posts | Baseline engagement (Bonsón & Ratkai) |
| **2. Topical activity** | `TopVol_a` | `\|T_a\|` | Number of topical posts |
| | `TopFocus_a` | `\|T_a\| / (\|T_a\| + \|B_a\|)` | Share of the topic in the author's output |
| | `Reach_a` | `Σ V_i` | Total reach of topical posts |
| **3. Author position** | `Pos_a` | `(p+, p0, p-)` | Sentiment of the author's own posts |
| **4. Audience response** | `ER_a^top` | `avg((L+C+R) / F_a)` over topical posts | Engagement on the topic |
| | `Resp_a` | `(q+, q0, q-)` | Sentiment of comments under those posts |

`Pos_a` and `Resp_a` are three-component distributions, which is why there are 11 numbers per author.

### From scores to roles

Axes 1 and 2 collapse into two composites via robust z-scoring on the median and the interquartile range (IQR) — estimators that resist the outliers which make up a sizable share of audience and reach distributions:

```
Struct_a = ⅓·(z(Aud) + z(ER_bg) + z(Age))
Topic_a  = ⅓·(z(TopVol) + z(TopFocus) + z(Reach))
```

The thresholds θ_Struct and θ_Topic are the session medians. Crossing them yields four roles:

|  | **Topic_a ≥ θ** | **Topic_a < θ** |
|---|---|---|
| **Struct_a ≥ θ** | Authoritative leader | Sleeping giant |
| **Struct_a < θ** | Topic activist | Background author |

Axes 3 and 4 do not enter the composites: the author's stance and the character of the audience response are attached to the role as separate attributes.

### Uncertainty

Every score computed from a sample carries a 95% confidence interval:

- **One-level bootstrap** (B = 1000, percentile method) for `ER_bg`, `ER_top`, `Reach` and `Pos_a`.
- **Two-level bootstrap** (300 × 100) for `Resp_a`. Comments are clustered by post: resampling runs over posts first, then over the comments within each post, which accounts for the within-cluster correlation when estimating variance.
- Point estimates (`Aud`, `Age`, `TopVol`, `TopFocus`) are computed over the whole population rather than a sample, so no interval is defined for them and it stays `NULL`.

Each author also receives a data-sufficiency label — `RELIABLE`, `PRELIMINARY` or `UNRELIABLE` (fewer than 3 topical posts, fewer than 10 comments, or an interval wider than 0.50) — showing how many observations the estimate rests on.

### Two-pass topic filtering

1. **L1, keywords.** `L1 = min(primary + 0.3·secondary, 3) / 3`. A post is accepted outright at `L1 ≥ 0.50`.
2. **L2, semantics.** Borderline posts are compared by cosine similarity of RuBERT embeddings (`cointegrated/rubert-tiny2`) against the topic's reference texts, with a 0.55 threshold.

Without the Python sidecar the second pass is unavailable: borderline posts take the `DISPUTED` stratum and go to the analyst's manual review queue. Filter quality is measured against the analyst's labels: precision and recall with 95% Beta-posterior intervals.

## Interface

> **Every screenshot below was taken on a synthetic dataset** (see [`docs/synthetic_datasets_spec.md`](docs/synthetic_datasets_spec.md)). Names of public figures are used in it as author labels; **the metrics, sentiment and roles shown are artificially generated and are not measurements of real people.**

### Dashboard

Quadrant chart (Struct_a × Topic_a) with threshold lines, plus a table of every author with their 11 scores and role. The side panel documents each metric and its formula.

![Dashboard](docs/screenshots/dashboard.png)

### Author detail

Role, position and response attributes, data-sufficiency badge, per-axis scores, and the author's topical posts with sentiment.

![Author detail](docs/screenshots/author-detail.png)

### Session setup

Topic, n-grams, reference texts, observation windows, community selection or JSON import.

![Session setup](docs/screenshots/setup.png)

### Session quality and log

<table>
<tr>
<td width="50%"><img src="docs/screenshots/session-quality.png" alt="Session quality"></td>
<td width="50%"><img src="docs/screenshots/session-log.png" alt="Session log"></td>
</tr>
</table>

## Architecture

Modules never call each other directly — they exchange data only through the local database. That is what makes the pipeline restartable from any checkpoint.

```
                    ┌──────────────┐
   VK API  ────────▶│      vk/     │  collection: newsfeed.search, wall.get,
                    │              │  profiles, comments; rate limiting,
                    └──────┬───────┘  backoff, execute batching, checkpoints
                           │
                    ┌──────▼───────┐
                    │   storage/   │  SQLite (WAL) + Exposed ORM
                    │              │  Flyway V1..V12 — the only channel
                    └──────┬───────┘  between modules
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
 ┌──────▼───────┐   ┌──────▼───────┐   ┌──────▼───────┐
 │preprocessing/│   │   analysis/  │   │    export/   │
 │ cleaning,    │   │ topic, dedup │   │  CSV / JSON  │
 │ tokenising,  │   │ scoring,     │   │  safe / raw  │
 │ lemmatising  │   │ inference,   │   └──────────────┘
 └──────┬───────┘   │ composite,   │
        │           │ roles,       │
        │           │ quality      │
        │           └──────┬───────┘
 ┌──────▼───────┐          │
 │     nlp/     │   ┌──────▼───────┐   ┌──────────────┐
 │  FULL or     │   │orchestration/│──▶│     ui/      │
 │  FALLBACK    │   │  10 stages   │   │ Compose,     │
 └──────┬───────┘   └──────────────┘   │ 11 screens   │
        │                              └──────────────┘
 ┌──────▼──────────────┐
 │ Python FastAPI      │  loopback HTTP + shared secret;
 │ sidecar (nlp/python)│  pymorphy3, RuBERT, natasha
 └─────────────────────┘
```

### The ten-stage pipeline

`SESSION_INIT → DATA_COLLECTION → PREPROCESSING → TOPIC_FILTERING → SCORING → BOOTSTRAP → COMPOSITE_ROLES → QUALITY_CHECK → EXPORT → PUBLISH_TO_UI`

Stages are declared in `orchestration/PipelineStage.kt`, executors registered in `PipelineWiring.kt`, and the run is driven by `PipelineOrchestrator.kt`. Every stage writes a checkpoint, so an interrupted session can resume.

### Two NLP modes

| | FULL | FALLBACK |
|---|---|---|
| Lemmatisation | pymorphy3 (sidecar) | Snowball stemmer (Lucene) |
| Sentiment | `seara/rubert-tiny2-russian-sentiment` | dictionary + negation handling |
| L2 pass | RuBERT embeddings | unavailable → manual validation |
| Needs Python | yes | no |

`NlpServiceSelector` picks the mode automatically from sidecar health. The application runs without Python, at the cost of filtering quality.

## Tech stack

**Kotlin / JVM**
- Kotlin 2.0.21, JVM 17, Gradle 8.11.1 (Kotlin DSL)
- Compose Multiplatform Desktop 1.7.3
- Exposed 0.56.0 + SQLite (xerial 3.47.1.0) in WAL mode
- Flyway 10.21.0 — forward-only schema migrations
- Ktor Client 3.0.3 (CIO) — VK API and sidecar
- Koin 3.5.6 — DI; kotlinx.coroutines 1.9.0
- Lets-Plot 4.9.3 — quadrant chart
- Lucene 9.12.1 — Snowball stemmer for FALLBACK
- JUnit 5.11.3, MockK 1.13.13, detekt 1.23.7

**Python sidecar** (Python 3.12)
- FastAPI + uvicorn
- pymorphy3 — lemmatisation
- transformers — `seara/rubert-tiny2-russian-sentiment`
- sentence-transformers — `cointegrated/rubert-tiny2` embeddings
- natasha — named entities; langdetect — language detection

## Build and run

### Requirements

- **JDK 17+** — required
- **Python 3.12** — optional, for FULL mode
- **A VK Standalone application** — for live collection; JSON import works without it

### Commands

```bash
git clone https://github.com/NikkiWay/lom-analyzer.git
cd lom-analyzer
./gradlew run
```

| Command | Action |
|---|---|
| `./gradlew run` | run the application |
| `./gradlew build` | build the project |
| `./gradlew test` | run the tests |
| `./gradlew detekt` | static analysis |

Invoke `gradlew`, the wrapper committed to the repository: it fetches Gradle
8.11.1, the version the project is built with and the one CI uses. A `gradle`
installed system-wide may be a different version.

`cmd.exe` does not accept the `./` prefix — there the command is `gradlew run`;
in PowerShell it is `.\gradlew run`.

Native distribution (MSI / DMG / DEB):

```bash
./gradlew packageDistributionForCurrentOS
```

### Python sidecar (FULL mode)

Linux and macOS:

```bash
cd nlp/python
python3.12 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

Windows (PowerShell):

```powershell
cd nlp\python
py -3.12 -m venv venv
venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

Point the application's settings at the environment; the sidecar is started and stopped automatically (`PythonServiceManager`: a free port in 8300–8399, a shared secret, health checks, up to 3 restarts). Models (~120 MB) are downloaded from HuggingFace on first use and cached in `models/`.

### First launch

1. The application asks you to **create a master password**. It derives an AES-256-GCM key from it via PBKDF2 (100,000 iterations) to encrypt the VK token.
2. Sign in to VK: VK ID (OAuth 2.1 + PKCE) or paste a token manually.
3. Create a session on the Setup screen, then watch progress on the Collection screen (cancellable).

The database is created automatically in the OS data directory (`%LOCALAPPDATA%\LomAnalyzer`, `~/.local/share/LomAnalyzer`, or `~/Library/Application Support/LomAnalyzer`) and migrated by Flyway at startup. A single-instance lock prevents concurrent runs.

### Demo without VK API

[`examples/`](examples/) holds a session setup example and a demo dataset. Import the dataset as JSON on the Setup screen: the pipeline runs end to end with no VK access.

## Tests and static analysis

```bash
./gradlew test
./gradlew detekt
```

211 tests on JUnit 5; static analysis by detekt 1.23.7.

Coverage spans the scoring formulas, robust statistics (median, IQR, type-7 quantiles), bootstrap interval correctness, two-pass filtering, deduplication, sentiment with negation, token encryption and PII hashing, the DAO layer, orchestration and cancellation, the VK rate limiter / backoff / batcher, and an end-to-end pipeline run over a minimal corpus (`MvpSmokeTest`).

CI (GitHub Actions) runs build, tests and detekt on every push. Pre-existing detekt findings are recorded in `detekt-baseline.xml`; the build fails on any **new** finding.

## Privacy and data

The application handles personal data, so:

- **The VK token is encrypted** (AES-256-GCM, key derived from the master password via PBKDF2 100k) in `token_vault.bin`, and wiped from memory on exit.
- **Exports are de-identified by default** — author identifiers are salted-hashed (`PiiHasher`). Raw export requires explicit confirmation and is written to the event log.
- **Data never leaves the machine**: SQLite is local, and the sidecar listens on loopback behind a shared secret.
- **Private profiles are excluded** from analysis.
- The authors and communities in the `examples/dataset_demo.json` demo dataset are fictional.

## Known limitations

- Without the Python sidecar the second filter pass is unavailable and borderline posts fall to manual validation. On narrow topics this noticeably increases the disputed share.
- Role thresholds are session medians, so a role is always relative to the sample: "authoritative leader" means "above this session's median".
- `Reach_a` falls back to follower count when VK does not report view counts.
- Collection completeness is assumed to be 1.0: the application cannot know about posts hidden by privacy settings or deleted before collection.

## License

MIT — see [LICENSE](LICENSE).
