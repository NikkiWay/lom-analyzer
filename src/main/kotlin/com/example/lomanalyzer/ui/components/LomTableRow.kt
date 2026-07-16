/*
 * НАЗНАЧЕНИЕ
 * Модель данных одной строки таблицы ЛОМ (дашборд лидеров мнений). Агрегирует
 * для одного автора все 11 количественных оценок по 4 осям влияния (Приложение
 * Е.4, см. docs/formulas.md), а также назначенную роль и достаточность данных.
 * Это UI-DTO: заполняется на финальных этапах пайплайна и отображается в LomTable.
 *
 * ЧТО ВНУТРИ
 * data class LomTableRow с полями по 4 осям: структурное влияние, тематическая
 * активность, позиция автора, отклик аудитории; плюс служебные поля (счётчики,
 * роль из этапа 7, достаточность из этапа 8). Nullable-поля — оценка может
 * отсутствовать при нехватке данных.
 *
 * СВЯЗИ
 * Потребляется LomTable; роль отображается через RoleCombinationBadge.
 */
package com.example.lomanalyzer.ui.components

/**
 * Данные одной строки таблицы дашборда ЛОМ.
 * Соответствуют 11 количественным оценкам из диплома (Приложение Е.4).
 *
 * @param authorId VK-идентификатор автора.
 * @param authorName отображаемое имя автора.
 * @param aud Ось 1 — размер аудитории (Aud).
 * @param age Ось 1 — стаж аккаунта (Age).
 * @param erBg Ось 1 — фоновая вовлечённость (ER_bg).
 * @param topVol Ось 2 — объём тематических постов (TopVol), штук.
 * @param topFocus Ось 2 — тематический фокус (TopFocus), доля.
 * @param reach Ось 2 — охват тематического контента (Reach).
 * @param posPositive Ось 3 — доля позитивной позиции автора.
 * @param posNeutral Ось 3 — доля нейтральной позиции автора.
 * @param posNegative Ось 3 — доля негативной позиции автора.
 * @param erTop Ось 4 — вовлечённость на тематических постах (ER_top).
 * @param respPositive Ось 4 — доля позитивного отклика аудитории.
 * @param respNeutral Ось 4 — доля нейтрального отклика аудитории.
 * @param respNegative Ось 4 — доля негативного отклика аудитории.
 * @param topicPostCount служебный счётчик тематических постов автора.
 * @param commentCount служебный счётчик комментариев под постами автора.
 * @param role назначенная роль (квадрант), вычисляется на этапе 7.
 * @param sufficiency метка достаточности данных, вычисляется на этапе 8.
 */
data class LomTableRow(
    val authorId: Int,
    val authorName: String,
    // Ось 1: структурное влияние
    val aud: Float?,
    val age: Float?,
    val erBg: Float?,
    // Ось 2: тематическая активность
    val topVol: Int?,
    val topFocus: Float?,
    val reach: Float?,
    // Ось 3: позиция автора
    val posPositive: Float?,
    val posNeutral: Float?,
    val posNegative: Float?,
    // Ось 4: отклик аудитории
    val erTop: Float?,
    val respPositive: Float?,
    val respNeutral: Float?,
    val respNegative: Float?,
    // Служебные поля
    val topicPostCount: Int?,
    val commentCount: Int?,
    val role: String? = null,          // назначается на этапе 7
    val sufficiency: String? = null,   // назначается на этапе 8
)
