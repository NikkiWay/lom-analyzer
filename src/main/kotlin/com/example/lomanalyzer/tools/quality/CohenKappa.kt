/*
 * НАЗНАЧЕНИЕ
 * Расчёт каппы Коэна (Cohen's kappa) — меры согласия двух экспертов (inter-rater
 * agreement) при категориальной разметке. Используется для проверки качества
 * классификации ролей (например, эксперт против алгоритма), см. v6 §25.3,
 * порог качества kappa >= 0.60.
 *
 * ЧТО ВНУТРИ
 * object CohenKappa с функцией compute(rater1, rater2) — каппа по двум спискам
 * меток равной длины.
 *
 * МЕТОД
 * kappa = (Po - Pe) / (1 - Pe), где Po — доля совпавших меток (наблюдаемое
 * согласие), Pe — ожидаемое согласие случайно (сумма по категориям произведений
 * их долей у каждого эксперта). kappa = 1 при полном согласии, 0 — на уровне
 * случайного, отрицательна — хуже случайного.
 *
 * БИБЛИОТЕКИ
 * Только stdlib Kotlin — внешних зависимостей нет.
 */
package com.example.lomanalyzer.tools.quality

/** Каппа Коэна — мера согласия двух экспертов при категориальной разметке (v6 §25.3). */
object CohenKappa {
    /**
     * Считает каппу Коэна по двум спискам меток равной длины.
     * @return значение каппы; 0.0 для пустых списков и 1.0 при вырожденном Pe>=1.
     */
    fun compute(rater1: List<String>, rater2: List<String>): Double {
        require(rater1.size == rater2.size)
        val n = rater1.size.toDouble()
        if (n == 0.0) return 0.0

        // Все встречающиеся категории меток
        val categories = (rater1 + rater2).distinct()
        // Наблюдаемое согласие Po — доля позиций, где метки совпали
        val observed = rater1.zip(rater2).count { (a, b) -> a == b } / n

        // Ожидаемое случайное согласие Pe — сумма произведений долей категории у обоих экспертов
        var expected = 0.0
        for (cat in categories) {
            val p1 = rater1.count { it == cat } / n
            val p2 = rater2.count { it == cat } / n
            expected += p1 * p2
        }

        // kappa = (Po - Pe) / (1 - Pe); при Pe>=1 во избежание деления на ноль возвращаем 1.0
        return if (expected >= 1.0) 1.0 else (observed - expected) / (1.0 - expected)
    }
}
