/*
 * НАЗНАЧЕНИЕ
 * Сырое значение тематического объёма TopVol_a (ось тематической активности,
 * этап 7, формула Е.4.2): сколько по теме автор пишет, с поправкой на длину
 * фактического окна наблюдения.
 *
 * ЧТО ВНУТРИ
 * Объект TopicalVolumeComponent:
 *   - computeKWindow(days) — нормировочный коэффициент окна;
 *   - computeRaw(count, k) — лог-объём с поправкой на окно.
 * Константа REFERENCE_BASELINE_DAYS = 60 — эталонная длина окна.
 *
 * МЕТОД
 * k_window_a = baseline_days_actual_a / 60 приводит счётчики к единому окну:
 * seed-авторы наблюдаются 60 дней (k=1.0), discovery-авторы — 30 дней (k=0.5).
 * Деление на k нормирует число постов «к 60 дням», чтобы короткое окно не
 * занижало активность. V_raw_a = ln(1 + N_topic_eff / k_window) — лог-сжатие
 * сильно скошенного счётчика постов.
 *
 * БИБЛИОТЕКИ
 * kotlin.math.ln. СВЯЗИ: результат идёт на z-нормализацию и в тематический композит.
 */
package com.example.lomanalyzer.analysis.lom

import kotlin.math.ln

/**
 * Нормализация k_window: k_window_a = baseline_days_actual_a / 60.
 * Seed-авторы: k_window = 1.0 (окно 60 дней). Discovery-авторы: k_window = 0.5 (30 дней).
 * V_raw_a = ln(1 + N_topic_eff_a / k_window_a).
 */
object TopicalVolumeComponent {
    /** Эталонная длина фонового окна в днях (база нормировки). */
    private const val REFERENCE_BASELINE_DAYS = 60.0

    /**
     * Нормировочный коэффициент окна = фактические дни / 60.
     * @param baselineDaysActual фактическая длина окна наблюдения автора в днях.
     * @return доля эталонного окна (1.0 для 60 дней, 0.5 для 30).
     */
    fun computeKWindow(baselineDaysActual: Int): Double =
        baselineDaysActual / REFERENCE_BASELINE_DAYS

    /**
     * Сырая оценка тематического объёма с поправкой на длину окна.
     * @param topicEffCount эффективное число тематических постов (после дедупа).
     * @param kWindow коэффициент окна из computeKWindow.
     * @return ln(1 + count/k) — нормированный лог-объём.
     */
    fun computeRaw(topicEffCount: Int, kWindow: Double): Double {
        val effectiveK = if (kWindow > 0) kWindow else 1.0   // защита от деления на ноль/некорректного окна
        // Делением на k приводим счётчик к эталонному окну, затем лог-сжимаем
        return ln(1.0 + topicEffCount / effectiveK)
    }
}
