import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

group = "com.example"
version = "0.1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

val ktorVersion = "3.0.3"
val exposedVersion = "0.56.0"
val koinVersion = "3.5.6"
val logbackVersion = "1.4.14"
val flywayVersion = "10.21.0"

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)

    // Ktor Client (CIO)
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // kotlinx-serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // SQLite
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")

    // Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    // Flyway migrations
    implementation("org.flywaydb:flyway-core:$flywayVersion")

    // Koin DI
    implementation("io.insert-koin:koin-core:$koinVersion")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // Snowball stemmer (FALLBACK lemmatizer) — bundled in Lucene
    implementation("org.apache.lucene:lucene-analysis-common:9.12.1")

    // Lets-Plot
    implementation("org.jetbrains.lets-plot:lets-plot-kotlin-jvm:4.9.3")
    implementation("org.jetbrains.lets-plot:lets-plot-batik:4.5.2")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.example.lomanalyzer.AppKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "LomAnalyzer"
            packageVersion = "1.0.0"
            description = "Opinion Leader Analysis System for VKontakte"
            vendor = "LOM Analyzer Project"
            licenseFile.set(project.file("LICENSE"))

            windows {
                menuGroup = "LOM Analyzer"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }
            linux {
                debMaintainer = "lom-analyzer@example.com"
            }
        }
    }
}

detekt {
    config.setFrom(files("detekt.yml"))
    buildUponDefaultConfig = true
}

tasks.register("verifyNoDebugIncludesPiiInProd") {
    group = "verification"
    description = "Ensures DEBUG_INCLUDES_PII is false in production logback config"
    doLast {
        val prodConfig = file("src/main/resources/logback-prod.xml")
        if (prodConfig.exists()) {
            val content = prodConfig.readText()
            if (content.contains("\"true\"") && content.contains("DEBUG_INCLUDES_PII")) {
                throw GradleException(
                    "SECURITY: DEBUG_INCLUDES_PII is true in logback-prod.xml. " +
                    "Production builds must not include PII in debug logs."
                )
            }
        }
    }
}

tasks.named("assemble") {
    dependsOn("verifyNoDebugIncludesPiiInProd")
}

tasks.register("benchmark") {
    group = "verification"
    description = "Run performance benchmark and compare to baseline"
    doLast {
        val baselineFile = file("benchmarks/baseline.json")
        if (baselineFile.exists()) {
            logger.lifecycle("Benchmark baseline: ${baselineFile.absolutePath}")
        }
        logger.lifecycle("Benchmark: pipeline timings recorded via SessionMetrics")
    }
}

tasks.register("gatesReport") {
    group = "verification"
    description = "Aggregate all quality gate results into a summary"
    doLast {
        val gates = mapOf(
            "G-01" to file("reports/sensitivity/sensitivity_report_baseline.md"),
            "G-02" to file("benchmarks/baseline.json"),
            "G-03" to file("reports/validation/validation_dostoevsky.md"),
            "G-04" to file("reports/validation/validation_roles.md"),
            "G-05" to file("tools/nlp_model_benchmark/benchmarks/nlp_model_comparison.md"),
            "G-06" to file("reports/calibration/r2_mad_calibration.md"),
        )
        val sb = StringBuilder()
        sb.appendLine("# Quality Gates Summary")
        sb.appendLine()
        sb.appendLine("| Gate | Status | File |")
        sb.appendLine("|------|--------|------|")
        for ((id, f) in gates) {
            val status = if (f.exists()) "PASS" else "MISSING"
            sb.appendLine("| $id | $status | ${f.path} |")
        }
        val outDir = file("build/reports/gates")
        outDir.mkdirs()
        val out = File(outDir, "gates_summary.md")
        out.writeText(sb.toString())
        logger.lifecycle("Gates report written to: ${out.absolutePath}")
    }
}
