/*
 * НАЗНАЧЕНИЕ
 * Генератор синтетического тестового корпуса с эталонной разметкой (ground
 * truth) для прогона и проверки пайплайна без обращения к VK API. Создаёт
 * воспроизводимый набор: 500 постов, 50 авторов, 10 сообществ, а также эталонные
 * роли и аномалии (см. v6 §9.6).
 *
 * ЧТО ВНУТРИ
 * object TestCorpusGenerator с функцией generate(), возвращающей корпус как
 * JSON-строку. Внутри — заготовки текстов на тему экологии и бытовых тем, а
 * также детерминированная генерация авторов, постов, ролей и аномалий.
 *
 * МЕТОД
 * Данные генерируются детерминированно от индекса (остатки по модулю), чтобы
 * корпус был воспроизводим: тематичность ~40 процентов постов, тональность по
 * циклу из трёх классов, тип оригинальности по периодическим правилам, число
 * подписчиков растёт квадратично от индекса автора.
 *
 * БИБЛИОТЕКИ
 * kotlinx.serialization (Json.encodeToString) — сериализация структуры в JSON.
 *
 * СВЯЗИ
 * Результат потребляется TestCorpusLoader и инструментами качества.
 */
package com.example.lomanalyzer.tools.sensitivity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Генератор расширенного тестового корпуса: 500 постов, 50 авторов, 10 сообществ
 * с эталонной разметкой (ground truth) по v6 §9.6.
 */
object TestCorpusGenerator {

    /** Заготовки тематических (экология) текстов — используются для «тематических» постов. */
    private val ecologyTexts = listOf(
        "Загрязнение воздуха превышает норму",
        "Экология города ухудшается с каждым годом",
        "Качество воды в реке неудовлетворительное",
        "Выбросы парниковых газов растут",
        "Раздельный сбор мусора необходим",
        "Экологический мониторинг показал превышение",
        "Загрязнение почвы промышленными отходами",
        "Парниковый эффект усиливается",
        "Экологическая катастрофа в регионе",
        "Водоёмы загрязнены промышленными стоками",
    )

    /** Заготовки бытовых (нетематических) текстов — для «фоновых» постов. */
    private val dailyTexts = listOf(
        "Сегодня отличная погода для прогулки",
        "Смотрю новый сериал, интересный сюжет",
        "Купил новый телефон, доволен покупкой",
        "Утренняя пробежка и завтрак",
        "Готовлю ужин для семьи",
        "Выходные на даче, красота",
        "Футбол сегодня, болею за наших",
        "Дождь весь день, настроение среднее",
        "Читаю интересную книгу",
        "Концерт был великолепный вчера",
    )

    /** Допустимые классы тональности для эталонной разметки. */
    private val sentiments = listOf("POSITIVE", "NEGATIVE", "NEUTRAL")

    /**
     * Генерирует тестовый корпус и возвращает его как JSON-строку.
     * Все данные детерминированы от индекса — корпус воспроизводим между запусками.
     */
    @Suppress("LongMethod")
    fun generate(): String {
        // 50 авторов: число подписчиков растёт квадратично; каждый 10-й закрыт;
        // первые 30 — SEED (затравочные), остальные — DISCOVERY (найденные)
        val authors = (1..50).map { i ->
            mapOf(
                "id" to i,
                "vk_id_hashed" to "hash_${"%04d".format(i)}",
                "followers_count" to (100 + i * i * 20),
                "is_closed" to (i % 10 == 0),
                "discovery_source" to if (i <= 30) "SEED" else "DISCOVERY",
            )
        }

        val baseTs = 1717196400L // June 1 2025 — базовая метка времени
        // 500 постов, равномерно распределённых по авторам и дням
        val posts = (1..500).map { i ->
            val authorId = ((i - 1) % 50) + 1   // циклическое назначение автора
            val dayOffset = ((i - 1) / 8) % 60  // смещение по дням (окно ~60 дней)
            val isTopical = i % 5 <= 1 // ~40% постов помечаются тематическими
            // Текст берётся из тематических либо бытовых заготовок
            val text = if (isTopical) ecologyTexts[(i - 1) % ecologyTexts.size]
                else dailyTexts[(i - 1) % dailyTexts.size]

            mapOf(
                "id" to (1000 + i),
                "from_id" to authorId,
                "published_at" to (baseTs + dayOffset * 86400), // дата = база + смещение в днях (86400 с)
                "text_clean" to text,
                "likes" to (5 + i % 100),
                "reposts" to (i % 30),
                "comments" to (1 + i % 20),
                "own_text_length" to text.length,
                "has_copy_history" to (i % 10 == 0), // каждый 10-й — репост
                "contains_media" to (i % 3 == 0),
                // Эталонная разметка поста: тематичность, тональность и тип оригинальности
                "ground_truth" to mapOf(
                    "is_topic_relevant" to isTopical,
                    "sentiment" to sentiments[(i - 1) % 3],
                    // Тип оригинальности по периодическим правилам (приоритет сверху вниз)
                    "originality_type" to when {
                        i % 20 == 0 -> "DETECTED_COPY"
                        i % 10 == 0 -> "PURE_REPOST"
                        i % 7 == 0 -> "REPOST_WITH_COMMENT"
                        else -> "ORIGINAL"
                    },
                ),
            )
        }

        // Эталонные роли для первых 20 авторов — циклически по 4 базовым ролям
        val roles = (1..20).map { i ->
            mapOf(
                "author_id" to i,
                "expected_role" to when (i % 4) {
                    0 -> "AUTHORITATIVE_LOM"
                    1 -> "TOPIC_DRIVER"
                    2 -> "SLEEPING_GIANT"
                    else -> "BACKGROUND"
                },
            )
        }

        // 10 эталонных аномалий: чередование всплеска объёма и негативного сдвига тона
        val anomalies = (1..10).map { i ->
            mapOf(
                "date" to "2025-06-${"%02d".format(5 + i * 5)}",
                "expected_types" to listOf(
                    if (i % 2 == 0) "VOLUME_SPIKE" else "TONE_SHIFT_NEGATIVE",
                ),
            )
        }

        // 10 сообществ с возрастающим числом участников
        val communities = (1..10).map { i ->
            mapOf("id" to i, "name" to "Community $i", "members_count" to (500 + i * 1000))
        }

        // Собираем корневой объект корпуса с метаданными
        val corpus = mapOf(
            "version" to "test-corpus-2026-v2-extended",
            "description" to "Extended synthetic corpus (500 posts, 50 authors)",
            "sha256" to "to-be-computed-after-freeze",
            "metadata" to mapOf(
                "posts_count" to 500,
                "authors_count" to 50,
                "communities_count" to 10,
                "period_from" to "2025-06-01",
                "period_to" to "2025-07-31",
                "region" to "synthetic",
            ),
            "communities" to communities,
            "authors" to authors,
            "posts" to posts,
            "ground_truth_roles" to roles,
            "ground_truth_anomalies" to anomalies,
        )

        // Сериализуем в компактный JSON (без форматирования)
        return Json { prettyPrint = false }.encodeToString(corpus)
    }
}
