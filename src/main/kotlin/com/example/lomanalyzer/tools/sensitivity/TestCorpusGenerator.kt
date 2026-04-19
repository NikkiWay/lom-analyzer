package com.example.lomanalyzer.tools.sensitivity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Generates an extended test corpus with 500 posts, 50 authors, 10 communities.
 * Ground truth labels per v6 §9.6.
 */
object TestCorpusGenerator {

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

    private val sentiments = listOf("POSITIVE", "NEGATIVE", "NEUTRAL")

    @Suppress("LongMethod")
    fun generate(): String {
        val authors = (1..50).map { i ->
            mapOf(
                "id" to i,
                "vk_id_hashed" to "hash_${"%04d".format(i)}",
                "followers_count" to (100 + i * i * 20),
                "is_closed" to (i % 10 == 0),
                "discovery_source" to if (i <= 30) "SEED" else "DISCOVERY",
            )
        }

        val baseTs = 1717196400L // June 1 2025
        val posts = (1..500).map { i ->
            val authorId = ((i - 1) % 50) + 1
            val dayOffset = ((i - 1) / 8) % 60
            val isTopical = i % 5 <= 1 // ~40%
            val text = if (isTopical) ecologyTexts[(i - 1) % ecologyTexts.size]
                else dailyTexts[(i - 1) % dailyTexts.size]

            mapOf(
                "id" to (1000 + i),
                "from_id" to authorId,
                "published_at" to (baseTs + dayOffset * 86400),
                "text_clean" to text,
                "likes" to (5 + i % 100),
                "reposts" to (i % 30),
                "comments" to (1 + i % 20),
                "own_text_length" to text.length,
                "has_copy_history" to (i % 10 == 0),
                "contains_media" to (i % 3 == 0),
                "ground_truth" to mapOf(
                    "is_topic_relevant" to isTopical,
                    "sentiment" to sentiments[(i - 1) % 3],
                    "originality_type" to when {
                        i % 20 == 0 -> "DETECTED_COPY"
                        i % 10 == 0 -> "PURE_REPOST"
                        i % 7 == 0 -> "REPOST_WITH_COMMENT"
                        else -> "ORIGINAL"
                    },
                ),
            )
        }

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

        val anomalies = (1..10).map { i ->
            mapOf(
                "date" to "2025-06-${"%02d".format(5 + i * 5)}",
                "expected_types" to listOf(
                    if (i % 2 == 0) "VOLUME_SPIKE" else "TONE_SHIFT_NEGATIVE",
                ),
            )
        }

        val communities = (1..10).map { i ->
            mapOf("id" to i, "name" to "Community $i", "members_count" to (500 + i * 1000))
        }

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

        return Json { prettyPrint = false }.encodeToString(corpus)
    }
}
