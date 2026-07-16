/*
 * НАЗНАЧЕНИЕ
 * Классификация оригинальности постов (этап обработки/дедупликации, см.
 * docs/algorithm.md). По признакам поста (репост, длина собственного текста,
 * наличие медиа, факт обнаруженного копирования) присваивает тип оригинальности
 * и числовой вес, используемый далее в оценке оригинальности контента.
 *
 * ЧТО ВНУТРИ
 * - enum OriginalityType: пять типов оригинальности, каждый со своим весом [0..1].
 * - object OriginalityClassifier: метод classify — приоритетное правило выбора типа.
 *
 * МЕТОД
 * Решающие правила в порядке приоритета (см. таблицу ниже): обнаруженная копия →
 * репост с комментарием → чистый репост → оригинал → медиа-только.
 *
 * СВЯЗИ
 * Признак isDetectedCopy формируется DedupPipeline (EXACT/NEAR_DUPLICATE),
 * вызывается из OriginalityExecutor, результат пишется в Posts.
 *
 * БИБЛИОТЕКИ
 * Только stdlib Kotlin.
 */
package com.example.lomanalyzer.analysis.dedup

/**
 * Originality classification per v6 §14.2.4.
 *
 * | Type                | Condition                                              | Weight |
 * |---------------------|--------------------------------------------------------|--------|
 * | ORIGINAL            | No copy_history, ownTextLength >= 20, not DETECTED_COPY | 1.0    |
 * | REPOST_WITH_COMMENT | Has copy_history, ownTextLength >= 30                   | 0.5    |
 * | PURE_REPOST         | Has copy_history, ownTextLength < 30                    | 0.0    |
 * | DETECTED_COPY       | SHA-256 matches earlier post by different author         | 0.0    |
 * | MEDIA_ONLY          | No copy_history, ownTextLength < 20, containsMedia      | 0.25   |
 */
/**
 * Тип оригинальности поста и его вклад (вес) в оценку оригинальности.
 * @property weight вес типа в [0..1]: 1.0 — полностью оригинальный, 0.0 — копия/репост.
 */
enum class OriginalityType(val weight: Float) {
    /** Оригинальный пост: нет репоста, есть собственный текст. */
    ORIGINAL(1.0f),
    /** Репост со значимым собственным комментарием. */
    REPOST_WITH_COMMENT(0.5f),
    /** Чистый репост без собственного текста. */
    PURE_REPOST(0.0f),
    /** Обнаруженная копия (точная или near-дубликат) более раннего поста. */
    DETECTED_COPY(0.0f),
    /** Короткий пост без текста, но с медиа-вложением. */
    MEDIA_ONLY(0.25f),
}

/** Классификатор оригинальности поста по приоритетным правилам. */
object OriginalityClassifier {

    /**
     * Определяет тип оригинальности поста.
     * @param hasCopyHistory есть ли у поста история репоста (copy_history).
     * @param ownTextLength длина собственного текста поста (символы).
     * @param containsMedia есть ли медиа-вложение.
     * @param isDetectedCopy обнаружен ли пост как копия (по дедупликации).
     * @return тип оригинальности (см. [OriginalityType]).
     */
    fun classify(
        hasCopyHistory: Boolean,
        ownTextLength: Int,
        containsMedia: Boolean,
        isDetectedCopy: Boolean,
    ): OriginalityType = when {
        // Приоритет 1: пост распознан как копия по дедупликации
        isDetectedCopy -> OriginalityType.DETECTED_COPY
        // Приоритет 2: репост с содержательным собственным комментарием (>=30 символов)
        hasCopyHistory && ownTextLength >= 30 -> OriginalityType.REPOST_WITH_COMMENT
        // Приоритет 3: репост без значимого комментария — чистый репост
        hasCopyHistory -> OriginalityType.PURE_REPOST
        // Приоритет 4: достаточно собственного текста (>=20 символов) — оригинал
        ownTextLength >= 20 -> OriginalityType.ORIGINAL
        // Приоритет 5: текста мало, но есть медиа — медиа-только
        containsMedia -> OriginalityType.MEDIA_ONLY
        else -> OriginalityType.ORIGINAL // fallback for short non-media posts
    }
}
