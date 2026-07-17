/*
 * НАЗНАЧЕНИЕ
 * Защита от правки уже применённых миграций. Flyway хранит контрольную сумму
 * каждой применённой миграции и при старте сверяет её с файлом: расхождение
 * останавливает приложение с ошибкой валидации. Сумма считается по всему файлу,
 * включая комментарии, поэтому неизменны и они.
 *
 * ЧТО ВНУТРИ
 * Класс MigrationImmutabilityTest: сверка контрольных сумм файлов миграций с
 * зафиксированным списком и проверка, что список полон.
 *
 * МЕТОД
 * SHA-256 по содержимому файла с нормализованными переводами строк — как и
 * Flyway, который читает файл построчно, поэтому CRLF и LF дают одну сумму.
 * Алгоритм с Flyway не совпадает намеренно: сверяется не значение суммы, а факт
 * неизменности файла.
 *
 * ПОЧЕМУ ОТДЕЛЬНЫЙ ТЕСТ
 * Прочие тесты и CI работают на чистой базе: там история миграций пуста, сверять
 * нечего, и правка применённой миграции проходит незамеченной. Ломается она
 * только на машине, где миграция уже накатана, — то есть у пользователя.
 *
 * СВЯЗИ
 * src/main/resources/db/migration, storage/Migrations.kt.
 */
package com.example.lomanalyzer.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest

class MigrationImmutabilityTest {

    private val migrationsDir = File("src/main/resources/db/migration")

    /**
     * Контрольные суммы применяемых миграций.
     *
     * Запись добавляется один раз — когда миграция создаётся. Изменять её у уже
     * выпущенной миграции нельзя: схема правится новой миграцией, а не правкой
     * существующей (forward-only).
     */
    private val expectedChecksums = mapOf(
        "V1__initial_schema.sql" to
            "964563261987c80664806b0a4d19288cebd86a106d67dcd697821d1e3802d879",
        "V2__persona_history_link.sql" to
            "04504d0a1c8c64bddaadfa777b8137ae3363ebe0f7116fae57caa55e083d05fe",
        "V3__add_analyst_vote.sql" to
            "f6621fb8551a71de22ee75e48ca0fa106d4b41bf2398d5dc7d9487cb3a5acf82",
        "V4__add_ngrams_to_session.sql" to
            "d34d837a115553d5042df907570ebf7da32b6b80528739aea1c1fc10ab92447a",
        "V5__add_role_mode.sql" to
            "ab04e5cf14bd59e9d644f61052d86274d6b14cfa35c7bf61b7781cf9b9aa2285",
        "V6__add_originality_type.sql" to
            "492286048b28e7c1ec00fcf5017fa4c0f3f6f176f163fc5e5abe45b48536e7a8",
        "V7__post_unique_with_window.sql" to
            "be63f848faf3d2701f2a3fad66e2ccef80970123946d6f9d2071d1046425ad53",
        "V8__add_import_json_path.sql" to
            "920fa9ddb7a83cb6f16b481b283861a34e1f4137bb4eddda2d1213c6a0c0d6ab",
        "V9__v2_schema_cleanup.sql" to
            "d81c90fc6477aaedad3696ce3f096d076ad4d030319d95da3793cd446cb2692e",
        "V10__new_lom_scores.sql" to
            "9e6e00e525c2bcf45d67398f1ad0f76c6407f7096db7570256e450ca2f865f64",
        "V11__sentiment_result_entity_type.sql" to
            "a836371700ae2a5b1974b63cd73be07f5132979b81c06d9e1abb21ccee9b9c32",
        "V12__drop_unused_audit_log.sql" to
            "ee64cf18da32194d1190ff67bbd728d98955f2a724b641312da511a978d68a2a",
        "V13__sentiment_probabilities.sql" to
            "b5c86e6e26315df925615794ae54274ae5382d7bc0af7af27cf38c87e801e95d",
        "V14__drop_unused_role_mode.sql" to
            "e8f0a6e2d32d92b2f0d1ac522f2fdc783d85dbf5a19036851a83e6534c24de10",
    )

    /** SHA-256 содержимого файла с нормализованными переводами строк. */
    private fun checksumOf(file: File): String {
        val normalized = file.readText().replace("\r\n", "\n")
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Ни один файл миграции не изменился.
     *
     * Падение означает правку выпущенной миграции: на базе, где она уже накатана,
     * Flyway остановит приложение с ошибкой несовпадения контрольной суммы. Схема
     * меняется добавлением новой миграции.
     */
    @Test
    fun `applied migrations stay unchanged`() {
        val files = migrationsDir.listFiles { f -> f.extension == "sql" }.orEmpty()
        assertTrue(files.isNotEmpty(), "каталог миграций не найден: ${migrationsDir.absolutePath}")

        for (file in files.sortedBy { it.name }) {
            val expected = expectedChecksums[file.name] ?: continue
            assertEquals(
                expected,
                checksumOf(file),
                "миграция ${file.name} изменена. Применённые миграции неизменны: " +
                    "Flyway сверяет их контрольные суммы и остановит приложение на базе, " +
                    "где миграция уже накатана. Правьте схему новой миграцией.",
            )
        }
    }

    /** У каждой миграции есть зафиксированная сумма: новая обязана попасть в список. */
    @Test
    fun `every migration file is pinned`() {
        val onDisk = migrationsDir.listFiles { f -> f.extension == "sql" }
            .orEmpty().map { it.name }.toSet()

        val unpinned = onDisk - expectedChecksums.keys
        assertTrue(
            unpinned.isEmpty(),
            "миграции без зафиксированной суммы: $unpinned. Добавьте запись в expectedChecksums.",
        )
    }

    /** В списке нет записей для удалённых файлов. */
    @Test
    fun `pinned list has no stale entries`() {
        val onDisk = migrationsDir.listFiles { f -> f.extension == "sql" }
            .orEmpty().map { it.name }.toSet()

        val missing = expectedChecksums.keys - onDisk
        assertTrue(missing.isEmpty(), "в списке есть отсутствующие файлы: $missing")
    }
}
