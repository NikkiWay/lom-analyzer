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

## First Launch

On first launch the application will prompt you to **create a master password**. This password is used to derive an AES-256-GCM encryption key (via PBKDF2, 100 000 iterations) that protects your VK API token at rest.

- **New vault** — you will be asked to enter and confirm a password. The encrypted vault is stored at `<appDataDir>/token_vault.bin`.
- **Existing vault** — you will be asked to enter your password to unlock the vault. If the password is wrong, decryption will fail and you can retry.
- **On exit** — the token is securely wiped from memory and the vault key is cleared.

You must enter the master password each time you start the application.

## License

MIT — see [LICENSE](LICENSE).
