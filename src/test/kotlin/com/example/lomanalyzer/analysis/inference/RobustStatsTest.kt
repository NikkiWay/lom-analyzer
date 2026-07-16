/*
 * НАЗНАЧЕНИЕ
 * Юнит-тесты робастной статистики (основа этапов оценок и нормализации):
 * медиана и IQR (Е.1), MAD и M-оценка Хьюбера (Е.2), квантиль типа 7
 * (Hyndman-Fan, тип R-7), а также фиксированные константы метода.
 *
 * ЧТО ВНУТРИ
 * Класс RobustStatsTest c группами тестов по объекту RobustStats:
 *   1) median — чётная/нечётная длина, единичный/пустой список, устойчивость к выбросам;
 *   2) iqr — равномерный ряд, постоянные значения, единичный элемент;
 *   3) quantile — крайние квантили 0 и 1, квантиль 0.5 = медиана;
 *   4) mad — нормальная выборка, постоянные значения, константа MAD_CONSTANT=1.4826;
 *   5) huberMEstimate — симметричные данные, устойчивость к выбросу, константа HUBER_K=1.345,
 *      крайние случаи (пусто, один и два элемента).
 *
 * МЕТОД
 * Робастные меры: медиана и IQR устойчивы к выбросам; MAD = c·median(|x−med|),
 * c=1.4826 для согласованности с σ нормального распределения; M-оценка Хьюбера —
 * итеративно-перевзвешенный МНК (IRLS) с порогом k=1.345. Квантили — тип 7.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (@Test); многие тесты записаны как однострочные функции через знак равенства.
 *
 * СВЯЗИ
 * Тестируемый объект RobustStats из пакета analysis/inference.
 */
package com.example.lomanalyzer.analysis.inference

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Тесты робастной статистики: медиана, IQR, квантили, MAD и M-оценка Хьюбера (Е.1, Е.2).
 */
class RobustStatsTest {

    // ── Median (E.1) ──

    /** Медиана нечётного ряда — центральный элемент: med(1,3,5)=3 (Е.1). */
    @Test fun `median of odd-length list`() = assertEquals(3.0, RobustStats.median(listOf(1.0, 3.0, 5.0)))

    /** Медиана чётного ряда — среднее двух центральных: med(1,2,3,4)=2.5 (Е.1). */
    @Test fun `median of even-length list`() = assertEquals(2.5, RobustStats.median(listOf(1.0, 2.0, 3.0, 4.0)))

    /** Медиана одного элемента равна ему самому. */
    @Test fun `median of single element`() = assertEquals(7.0, RobustStats.median(listOf(7.0)))

    /** Граничный случай: медиана пустого списка по соглашению равна 0. */
    @Test fun `median of empty list returns 0`() = assertEquals(0.0, RobustStats.median(emptyList()))

    /**
     * Демонстрирует устойчивость медианы к выбросам: при 30 процентах экстремальных значений
     * (3 из 10) медиана остаётся внутри «нормального» диапазона данных.
     */
    @Test fun `median is robust to 30 percent outliers`() {
        // 7 normal values + 3 extreme outliers (30%)
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 1000.0, 2000.0, 3000.0)
        val med = RobustStats.median(values)
        assertTrue(med in 4.0..6.0, "median=$med should be between 4 and 6")
    }

    // ── IQR type 7 (E.1) ──

    /**
     * IQR (тип 7) равномерного ряда 1..100 ≈ 50 (Q75−Q25 ≈ 75−25). Проверка масштаба разброса (Е.1).
     */
    @Test fun `iqr of uniform sequence`() {
        val values = (1..100).map { it.toDouble() }
        val iqr = RobustStats.iqr(values)
        assertTrue(iqr in 49.0..51.0, "iqr=$iqr should be ~50")
    }

    /** Для постоянных значений разброса нет → IQR=0 (важно для деления при z-нормализации). */
    @Test fun `iqr of constant values is 0`() = assertEquals(0.0, RobustStats.iqr(listOf(5.0, 5.0, 5.0, 5.0)))

    /** Граничный случай: для одного элемента IQR=0. */
    @Test fun `iqr of single element is 0`() = assertEquals(0.0, RobustStats.iqr(listOf(42.0)))

    // ── Quantile type 7 (Hyndman-Fan) ──

    /** Крайние квантили типа 7: q=0 → минимум, q=1 → максимум ряда. */
    @Test fun `quantile 0 and 1`() {
        val v = listOf(10.0, 20.0, 30.0, 40.0, 50.0)
        assertEquals(10.0, RobustStats.quantile(v, 0.0))
        assertEquals(50.0, RobustStats.quantile(v, 1.0))
    }

    /** Согласованность определений: квантиль 0.5 должен совпадать с медианой. */
    @Test fun `quantile 0_5 equals median`() {
        val v = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        assertEquals(RobustStats.median(v), RobustStats.quantile(v, 0.5))
    }

    // ── MAD (E.2) ──

    /**
     * MAD на квазинормальной симметричной выборке близок к 1 (Е.2): множитель c=1.4826
     * делает MAD согласованной оценкой σ для N(0,1). Проверяем попадание в окрестность.
     */
    @Test fun `mad of normal-like sample`() {
        // For N(0,1): MAD ≈ 1.0 (with constant 1.4826)
        val values = listOf(-2.0, -1.0, -0.5, 0.0, 0.5, 1.0, 2.0)
        val mad = RobustStats.mad(values)
        assertTrue(mad > 0.5 && mad < 2.0, "mad=$mad")
    }

    /** Для постоянных значений все отклонения от медианы равны 0 → MAD=0. */
    @Test fun `mad of constant values is 0`() = assertEquals(0.0, RobustStats.mad(listOf(3.0, 3.0, 3.0)))

    /** Фиксированная константа метода: множитель MAD равен 1.4826 (Е.2). */
    @Test fun `mad constant is 1_4826`() = assertEquals(1.4826, RobustStats.MAD_CONSTANT)

    // ── Huber M-estimate (E.2, k=1.345) ──

    /**
     * На симметричных данных без выбросов M-оценка Хьюбера совпадает со средним/медианой:
     * для 1..5 оценка ≈ 3.0 (Е.2).
     */
    @Test fun `huber on symmetric data equals mean`() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val est = RobustStats.huberMEstimate(values)
        assertTrue(abs(est - 3.0) < 0.1, "huber=$est should be ~3.0")
    }

    /**
     * Демонстрирует устойчивость M-оценки Хьюбера к выбросу: один экстремум (100)
     * тянет арифметическое среднее к ~19.2, но оценка Хьюбера остаётся близкой к центру
     * (<10), так как функция влияния ограничивает вклад больших остатков (порог k).
     */
    @Test fun `huber is robust to outliers`() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 100.0) // one extreme outlier
        val huber = RobustStats.huberMEstimate(values)
        val mean = values.average()
        // Huber should be much closer to 3 than the arithmetic mean (~19.2)
        assertTrue(huber < 10.0, "huber=$huber should be << mean=$mean")
        assertTrue(huber > 1.0, "huber=$huber should be > 1")
    }

    /** Фиксированный параметр метода: порог настройки Хьюбера k=1.345 (Е.2). */
    @Test fun `huber default k is 1_345`() = assertEquals(1.345, RobustStats.HUBER_K)

    /** Граничный случай: для пустого списка оценка по соглашению равна 0. */
    @Test fun `huber of empty list returns 0`() = assertEquals(0.0, RobustStats.huberMEstimate(emptyList()))

    /** Для одного элемента оценка равна ему самому (оценивать нечего). */
    @Test fun `huber of single element returns that element`() =
        assertEquals(42.0, RobustStats.huberMEstimate(listOf(42.0)))

    /** Для двух элементов оценка Хьюбера ≈ их среднее (нет выбросов для подавления). */
    @Test fun `huber of two elements returns average`() {
        val est = RobustStats.huberMEstimate(listOf(10.0, 20.0))
        assertTrue(abs(est - 15.0) < 1.0, "huber=$est should be ~15")
    }
}
