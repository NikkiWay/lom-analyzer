# LOM Analyzer

Desktop application for automated identification and analysis of opinion leaders (Leaders of Public Opinion — LOM) within a specified thematic niche on VKontakte. The system builds aggregated analytical models of key public actors and provides early warning signals about changes in discourse structure, emergence of new actors, and anomalous shifts in public communication content.

## Status

**pre-MVP** — project skeleton only, no business logic implemented yet.

## Technology Stack

- **Language:** Kotlin 2.0+ / JVM 17
- **UI:** Compose Desktop 1.7+
- **Build:** Gradle (Kotlin DSL)
- **Database:** SQLite (via Exposed ORM, Flyway migrations)
- **HTTP Client:** Ktor (CIO engine)
- **DI:** Koin
- **Charting:** Lets-Plot for Kotlin

## Build & Run

### Prerequisites

- JDK 17+
- Gradle 8+ (or use the included wrapper)

### Build

```bash
./gradlew build
```

### Run

```bash
./gradlew run
```

### Tests

```bash
./gradlew test
```

### Static Analysis

```bash
./gradlew detekt
```

## Database

SQLite database at `<appDataDir>/lom_analyzer.db` with WAL mode. Schema is managed by Flyway (forward-only, no downgrades).

**V1 schema** includes 23 tables:
- `analysis_session` — full session config, gamma/threshold/quality fields, status enum (8 values)
- `community`, `author`, `post` — core VK entities with all v6 fields
- `processed_text`, `sentiment_result` — NLP pipeline outputs
- `lom_score` — influence scores with confidence intervals and role classification
- `repost_relation`, `dedup_group` — content linkage
- `anomaly_event`, `risk_signal` — detection outputs with CI and holiday/routine flags
- `collection_checkpoint`, `audit_log`, `recovery_choice`, `session_metrics` — operational tables
- `persona_aggregate` — aggregated actor profiles per session
- `holiday_day_stats` — materialized daily stats with `(session_id, date)` index
- `post_metrics_snapshot` — reserved for SessionFamily (v2.0)
- Link tables: `session_community`, `session_author`, `anomaly_author_link`, `anomaly_post_link`, `risk_anomaly_link`

## First Launch

The app enforces a **single-instance lock** — if another instance is already running, the new one exits with a message. Stale locks are auto-removed.

On first launch the application will prompt you to **create a master password**. This password is used to derive an AES-256-GCM encryption key (via PBKDF2, 100 000 iterations) that protects your VK API token at rest.

- **New vault** — you will be asked to enter and confirm a password. The encrypted vault is stored at `<appDataDir>/token_vault.bin`.
- **Existing vault** — you will be asked to enter your password to unlock the vault. If the password is wrong, decryption will fail and you can retry.
- **On exit** — the token is securely wiped from memory and the vault key is cleared.

You must enter the master password each time you start the application.

After unlocking, the main window shows a setup screen where you can create analysis sessions. Each session tracks a topic query and region, and progresses through a 35-stage pipeline from data collection through risk scoring and export.

## VK API Setup

To use VK data collection you need a VK developer application:

1. Go to [VK Apps](https://vk.com/apps?act=manage) and create a new Standalone app.
2. Note the **App ID** (client_id).
3. The redirect URI is `https://oauth.vk.com/blank.html` (VK default for Implicit Flow).
4. On first analysis run, the app opens an embedded browser for VK OAuth. After granting access, the token is encrypted and stored in `<appDataDir>/token_vault.bin`.

Required permissions: `wall`, `friends`, `groups`, `stats`.

## License

MIT — see [LICENSE](LICENSE).
