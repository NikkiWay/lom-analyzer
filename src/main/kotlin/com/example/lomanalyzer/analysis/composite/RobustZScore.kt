/*
 * НАЗНАЧЕНИЕ
 * Робастная z-нормализация оценок перед построением композитов (этап 9 алгоритма,
 * docs/algorithm.md; формула Приложения Е.4.6). Приводит разнородные оценки осей
 * к единой безразмерной шкале, устойчивой к выбросам.
 *
 * ЧТО ВНУТРИ
 * object RobustZScore:
 *  - data class ZNormResult — результат: z-значения, медиана, IQR, индексы null-импутаций;
 *  - normalize(values)      — собственно нормализация списка значений по сессии.
 *
 * МЕТОД
 * z(x_a) = (x_a − med(x)) / IQR(x) — вместо классических среднего и СКО берутся
 * РОБАСТНЫЕ медиана и IQR (квантиль типа 7 Hyndman–Fan, см. RobustStats), что
 * снижает чувствительность к выбросам. Пропуски (null) импутируются центральным
 * значением z = 0; их индексы возвращаются, чтобы вызывающий код залогировал
 * «consequential null imputation» (значимую подстановку пропусков).
 *
 * БИБЛИОТЕКИ
 * Stdlib Kotlin; медиана/IQR — из analysis.inference.RobustStats.
 *
 * СВЯЗИ
 * Вызывается из CompositeRolesExecutor по каждой из 6 компонентных оценок;
 * z-значения далее агрегируются в CompositeScorer (Struct_a, Topic_a).
 */
package com.example.lomanalyzer.analysis.composite

import com.example.lomanalyzer.analysis.inference.RobustStats

/**
 * Робастная z-нормализация (диплом Е.4.6).
 * z(x_a) = (x_a − med(x)) / IQR(x).
 *
 * Использует медиану и IQR из RobustStats (тип 7 Hyndman–Fan).
 * Значения null → z = 0 (центральное значение); вызывающий код обязан
 * залогировать это как «consequential null imputation».
 */
object RobustZScore {

    /**
     * Результат z-нормализации.
     * @property zValues z-значения в том же порядке, что и вход (null → 0.0)
     * @property median медиана med(x) по непустым значениям
     * @property iqr межквартильный размах IQR(x) (знаменатель z)
     * @property nullImputedIndices индексы авторов, чьи значения были null (импутированы z=0)
     */
    data class ZNormResult(
        val zValues: List<Double>,
        val median: Double,
        val iqr: Double,
        val nullImputedIndices: List<Int>,
    )

    /**
     * Z-нормализует список значений (по одному на автора) в пределах сессии.
     * @param values по одному значению на автора в фиксированном порядке; null = нет данных
     * @return z-значения в том же порядке; null заменены на 0.0, плюс служебные med/IQR/индексы
     */
    fun normalize(values: List<Double?>): ZNormResult {
        // Оставляем только заданные значения — по ним считаем med и IQR
        val nonNull = values.filterNotNull()
        // Вырожденный случай: < 2 значений → нормировать нечем, все z = 0
        if (nonNull.size < 2) {
            return ZNormResult(
                zValues = values.map { 0.0 },
                median = nonNull.firstOrNull() ?: 0.0,
                iqr = 0.0,
                nullImputedIndices = values.indices.filter { values[it] == null },
            )
        }

        // Робастные центр и масштаб распределения
        val med = RobustStats.median(nonNull)
        val iqr = RobustStats.iqr(nonNull)
        val nullIndices = mutableListOf<Int>()

        // Поэлементная нормализация с сохранением исходного порядка авторов
        val zValues = values.mapIndexed { i, v ->
            if (v == null) {
                // Пропуск: запоминаем индекс и подставляем центральное значение z = 0
                nullIndices.add(i)
                0.0 // Центральное значение для отсутствующих данных
            } else {
                // z = (x − med) / IQR; при вырожденном IQR (все значения равны) → 0
                if (iqr > 0) (v - med) / iqr else 0.0
            }
        }

        return ZNormResult(zValues, med, iqr, nullIndices)
    }
}
