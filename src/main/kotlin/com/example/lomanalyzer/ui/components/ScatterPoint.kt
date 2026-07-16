/*
 * НАЗНАЧЕНИЕ
 * Модель данных одной точки квадрантного точечного графика (scatter plot) ЛОМ.
 * Описывает одного автора в координатах двух композитных осей и задаёт визуальное
 * кодирование точки (цвет = позиция автора, размер = тематический ER). Используется
 * для квадрантной классификации 4 базовых ролей (диплом 2.1.6, docs/algorithm.md).
 *
 * ЧТО ВНУТРИ
 * data class ScatterPoint: координаты (структурный и тематический композиты),
 * роль, доли позиции автора (для цвета) и тематический ER (для размера).
 *
 * МЕТОД
 * X = structComposite, Y = topicComposite. Цвет — градиент зелёный→серый→красный
 * по тональности позиции автора (posPositive − posNegative). Размер точки —
 * пропорционально тематическому ER.
 *
 * СВЯЗИ
 * Потребляется ScatterPlot для отрисовки на Canvas.
 */
package com.example.lomanalyzer.ui.components

/**
 * Точка данных для точечного графика (scatter plot).
 * Оси: structComposite (X) и topicComposite (Y).
 * Цвет: градиент зелёный→серый→красный по тональности позиции автора.
 * Размер: пропорционален тематическому ER.
 *
 * @param authorId VK-идентификатор автора.
 * @param authorName отображаемое имя (подпись точки).
 * @param structComposite композит структурного влияния — координата X.
 * @param topicComposite композит тематической активности — координата Y.
 * @param role назначенная роль (квадрант).
 * @param posPositive доля позитивной позиции автора (для цвета).
 * @param posNeutral доля нейтральной позиции автора.
 * @param posNegative доля негативной позиции автора (для цвета).
 * @param erTop тематический ER — задаёт размер точки.
 * @param topicPostCount число тематических постов автора.
 * @param sufficiency метка достаточности данных, опционально.
 */
data class ScatterPoint(
    val authorId: Int,
    val authorName: String,
    val structComposite: Float,
    val topicComposite: Float,
    val role: String,
    val posPositive: Float,
    val posNeutral: Float,
    val posNegative: Float,
    val erTop: Float,
    val topicPostCount: Int,
    val sufficiency: String? = null,
)
