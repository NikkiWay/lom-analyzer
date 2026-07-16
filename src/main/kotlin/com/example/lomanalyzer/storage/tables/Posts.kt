/*
 * НАЗНАЧЕНИЕ
 * Декларативное описание таблицы "post" — центральная таблица собранных постов
 * ВКонтакте. Хранит сырой и очищенный текст, метрики вовлечённости, результаты
 * тематической фильтрации (этапы 5-6), языковую атрибуцию, признаки медиа и
 * оригинальности. Это основной вход для большинства аналитических этапов.
 *
 * ЧТО ВНУТРИ
 * Один object-таблица Posts (Exposed ORM). На post ссылаются processed_text,
 * sentiment_result, comment, dedup_group. Сам post ссылается на analysis_session.
 *
 * МЕТОД
 * Поля topic_score_l1/l2/combined хранят результат двухпроходной тематической
 * фильтрации (L1 — ключевые слова ≥0.50, L2 — RuBERT cosine ≥0.55 для спорных;
 * см. docs/algorithm.md). own_text_length и originality_type участвуют в оценке
 * оригинальности контента.
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — IntIdTable (суррогатный primary key "id"). foreign key на
 * analysis_session. Физически создаётся миграциями Flyway.
 *
 * СВЯЗИ
 * session_id -> analysis_session.id. Дочерние: processed_text, sentiment_result,
 * comment (по post_id), dedup_group (canonical/duplicate post_id).
 */
package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

/**
 * Таблица постов ВКонтакте ("post").
 *
 * IntIdTable (суррогатный primary key "id", отдельный от vk_id). Объединяет
 * сырые данные сбора, метрики и производные признаки анализа одного поста.
 */
object Posts : IntIdTable("post") {
    /** foreign key на analysis_session.id — сессия, в рамках которой собран пост. */
    val sessionId = reference("session_id", AnalysisSessions)
    /** Идентификатор поста во ВКонтакте (в пределах владельца стены). */
    val vkId = integer("vk_id")
    /** Идентификатор владельца стены (положительный — пользователь, отрицательный — сообщество). */
    val ownerId = integer("owner_id")
    /** Идентификатор автора поста во ВКонтакте (from_id); может отличаться от ownerId при репостах. */
    val fromId = integer("from_id")
    /** Время публикации поста (Unix-время, мс). */
    val publishedAt = long("published_at")
    /** Сырой текст поста; может отсутствовать. */
    val text = text("text").nullable()
    /** Очищенный текст поста (без URL, разметки); может отсутствовать. */
    val textClean = text("text_clean").nullable()
    /** Длина собственного текста автора (без репостной части) в символах — для оценки оригинальности; по умолчанию 0. */
    val ownTextLength = integer("own_text_length").default(0)
    /** Число лайков; по умолчанию 0. */
    val likes = integer("likes").default(0)
    /** Число репостов; по умолчанию 0. */
    val reposts = integer("reposts").default(0)
    /** Число комментариев; по умолчанию 0. */
    val comments = integer("comments").default(0)
    /** Число просмотров; может отсутствовать (доступно не для всех постов). */
    val views = integer("views").nullable()
    /** Временное окно сбора, к которому отнесён пост (например, baseline/current). */
    val window = text("window")
    /** Признак тематической релевантности поста (итог фильтрации); может быть не определён (null). */
    val isTopicRelevant = bool("is_topic_relevant").nullable()
    /** Оценка релевантности прохода L1 (ключевые слова) [0..1]; может отсутствовать. */
    val topicScoreL1 = float("topic_score_l1").nullable()
    /** Оценка релевантности прохода L2 (RuBERT cosine) [0..1]; считается только для спорных постов; может отсутствовать. */
    val topicScoreL2 = float("topic_score_l2").nullable()
    /** Итоговая комбинированная оценка релевантности [0..1]; может отсутствовать. */
    val topicScoreCombined = float("topic_score_combined").nullable()
    /** Признак попадания даты поста на праздник (влияет на сезонную нормализацию); по умолчанию false. */
    val isHoliday = bool("is_holiday").default(false)
    /** Название праздника, если isHoliday; может отсутствовать. */
    val holidayName = text("holiday_name").nullable()
    /** Определённый язык поста (например, "ru"); может отсутствовать. */
    val detectedLanguage = text("detected_language").nullable()
    /** Уверенность определения языка [0..1]; может отсутствовать. */
    val languageConfidence = float("language_confidence").nullable()
    /** Языковая метка/флаг для фильтрации (например, не-русский контент); может отсутствовать. */
    val languageFlag = text("language_flag").nullable()
    /** Признак наличия медиа (фото/видео/аудио) в посте; по умолчанию false. */
    val containsMedia = bool("contains_media").default(false)
    /** Типы вложенных медиа (JSON/строка); может отсутствовать. */
    val mediaTypes = text("media_types").nullable()
    /** Признак, что текст был усечён при сборе/хранении; по умолчанию false. */
    val truncated = bool("truncated").default(false)
    /** Причина усечения текста; может отсутствовать. */
    val truncationReason = text("truncation_reason").nullable()
    /** Признак наличия copy_history (пост является репостом); по умолчанию false. */
    val hasCopyHistory = bool("has_copy_history").default(false)
    /** Число хэштегов в тексте; по умолчанию 0. */
    val hashtagsCount = integer("hashtags_count").default(0)
    /** Число упоминаний (@/[id]) в тексте; по умолчанию 0. */
    val mentionsCount = integer("mentions_count").default(0)
    /** Число ссылок (URL) в тексте; по умолчанию 0. */
    val urlsCount = integer("urls_count").default(0)
    /** Тип оригинальности контента (например, оригинал/репост/частичный); может отсутствовать. */
    val originalityType = text("originality_type").nullable()
    /** Ручная отметка аналитика по посту (override); может отсутствовать. */
    val analystVote = bool("analyst_vote").nullable()
    /** Момент создания записи в БД (Unix-время, мс). */
    val createdAt = long("created_at")
    /** Момент мягкого удаления (soft delete); null — запись активна. */
    val deletedAt = long("deleted_at").nullable()
}
