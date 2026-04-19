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

## License

MIT — see [LICENSE](LICENSE).
