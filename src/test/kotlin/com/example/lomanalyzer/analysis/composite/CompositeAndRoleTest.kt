/*
 * НАЗНАЧЕНИЕ
 * Юнит-тесты этапа 9 алгоритма (классификация): проверяют робастную
 * z-нормализацию, расчёт композитных осей, адаптивные пороги и квадрантное
 * назначение ролей с двумя атрибутами (позиция автора, отклик аудитории).
 *
 * ЧТО ВНУТРИ
 * Класс CompositeAndRoleTest c тремя группами тестов:
 *   1) RobustZScore.normalize — z-нормализация по медиане и IQR (формула Е.4.6);
 *   2) CompositeScorer — композиты Struct_a/Topic_a как среднее 1/3,1/3,1/3
 *      и адаптивные пороги тета как медианы композитов (Е.4.6);
 *   3) RoleAssigner — отнесение к 4 базовым ролям по квадрантам (порог сравнивается
 *      по «больше либо равно»), атрибут позиции автора и атрибут отклика аудитории.
 *
 * МЕТОД
 * Проверяются инварианты формул Е.4.6: z(med)=0, веса композита равны 1/3,
 * пороги = медианы, граница порога относится к «высоким» значениям. Атрибуты
 * назначаются по доминирующей доле сентимента (для аудитории — строго больше 50 процентов,
 * иначе MIXED).
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (org.junit.jupiter): аннотации @Test и статические assert-методы.
 *
 * СВЯЗИ
 * Тестируемые типы: RobustZScore, CompositeScorer (пакет composite),
 * RoleAssigner (пакет roles), перечисления BaseRole/AuthorPosition/AudienceResponse
 * (пакет core).
 */
package com.example.lomanalyzer.analysis.composite

import com.example.lomanalyzer.analysis.roles.RoleAssigner
import com.example.lomanalyzer.core.AudienceResponse
import com.example.lomanalyzer.core.AuthorPosition
import com.example.lomanalyzer.core.BaseRole
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Набор тестов композитов, порогов и ролевой классификации (этап 9, формулы Е.4.6).
 */
class CompositeAndRoleTest {

    // ── RobustZScore ──

    /**
     * Проверяет инвариант робастной z-нормализации z(x)=(x−med)/IQR (Е.4.6):
     * значение, равное медиане, должно давать z=0.
     * Arrange: ряд 1..5, у которого med=3, IQR (тип 7)=2 (Q75=4, Q25=2).
     * Act: нормализуем весь ряд.
     * Assert: размер выхода = 5, z центрального элемента (значение 3) ≈ 0,
     * пропусков (null) не было — список импутированных индексов пуст.
     */
    @Test
    fun `z-normalize produces mean 0 and spread via IQR`() {
        // Arrange: ряд значений, приведённый к Double? (нормализатор допускает пропуски)
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0).map { it as Double? }
        // Act: робастная z-нормализация по медиане и IQR
        val result = RobustZScore.normalize(values)
        // Assert: на выходе столько же значений, сколько на входе
        assertEquals(5, result.zValues.size)
        // Median = 3, IQR = 2 (Q75=4, Q25=2), z(3) = 0
        assertEquals(0.0, result.zValues[2], 0.01)
        // Пропусков не было — импутации не выполнялись
        assertTrue(result.nullImputedIndices.isEmpty())
    }

    /**
     * Проверяет обработку пропусков: отсутствующие значения (null) импутируются
     * нейтральным z=0 (центр распределения), а их позиции фиксируются.
     * Arrange: ряд с двумя null на позициях 1 и 3.
     * Assert: на этих позициях z=0, а список импутированных индексов = [1, 3].
     */
    @Test
    fun `z-normalize null values become 0`() {
        // Arrange: ряд с пропусками на позициях 1 и 3
        val values = listOf(1.0, null, 3.0, null, 5.0)
        // Act
        val result = RobustZScore.normalize(values)
        // Assert: пропуски заменены на нейтральный 0
        assertEquals(0.0, result.zValues[1])
        assertEquals(0.0, result.zValues[3])
        // и их позиции учтены для индикаторов качества
        assertEquals(listOf(1, 3), result.nullImputedIndices)
    }

    /**
     * Граничный случай: если все значения отсутствуют, нормализация не падает,
     * а возвращает нейтральный ряд из нулей (отсутствие данных = центр шкалы).
     */
    @Test
    fun `z-normalize all nulls produce all zeros`() {
        // Act: нормализация полностью пустого по данным ряда
        val result = RobustZScore.normalize(listOf(null, null, null))
        // Assert: все z равны 0
        assertTrue(result.zValues.all { it == 0.0 })
    }

    // ── CompositeScorer ──

    /**
     * Проверяет формулу структурного композита Struct_a = (1/3)(z(Aud)+z(ER_bg)+z(Age))
     * (Е.4.6): три z-компоненты усредняются с равными весами 1/3 (OECD Handbook).
     * Arrange: z-компоненты 0.3, 0.6, 0.9.
     * Assert: результат = их среднее = 0.6.
     */
    @Test
    fun `structural composite is equal-weighted average`() {
        // Act: композит из трёх структурных z-компонент
        val result = CompositeScorer.structuralComposite(0.3, 0.6, 0.9)
        assertEquals(0.6, result, 0.001) // (0.3 + 0.6 + 0.9) / 3
    }

    /**
     * Проверяет формулу тематического композита Topic_a = (1/3)(z(TopVol)+z(TopFocus)+z(Reach))
     * (Е.4.6): равновзвешенное среднее трёх z-компонент.
     * Arrange: компоненты 1, 2, 3 → среднее 2.0.
     */
    @Test
    fun `topic composite is equal-weighted average`() {
        // Act: композит из трёх тематических z-компонент
        val result = CompositeScorer.topicComposite(1.0, 2.0, 3.0)
        assertEquals(2.0, result, 0.001) // (1 + 2 + 3) / 3
    }

    /**
     * Проверяет адаптивные пороги тета_Struct = med(Struct_a) и тета_Topic = med(Topic_a)
     * (Е.4.6): пороги для квадрантной классификации берутся как медианы композитов по сессии.
     * Arrange: два ряда композитов с медианами 3.0 и 30.0.
     * Assert: пороги равны этим медианам.
     */
    @Test
    fun `adaptive thresholds are medians`() {
        // Arrange: ряды структурных и тематических композитов
        val structs = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val topics = listOf(10.0, 20.0, 30.0, 40.0, 50.0)
        // Act: пороги как медианы каждого ряда
        val (thetaS, thetaT) = CompositeScorer.adaptiveThresholds(structs, topics)
        // Assert: тета_Struct = med(structs) = 3.0; тета_Topic = med(topics) = 30.0
        assertEquals(3.0, thetaS, 0.01)
        assertEquals(30.0, thetaT, 0.01)
    }

    // ── RoleAssigner — quadrant classification ──

    /**
     * Квадрант «высокая структура + высокая тема» → авторитетный лидер.
     * Аргументы assignRole: (struct, topic, тета_Struct, тета_Topic).
     * Здесь struct=0.8≥0.5 и topic=0.8≥0.5 → AUTHORITATIVE_LEADER.
     */
    @Test
    fun `high struct high topic is AUTHORITATIVE_LEADER`() {
        assertEquals(BaseRole.AUTHORITATIVE_LEADER, RoleAssigner.assignRole(0.8, 0.8, 0.5, 0.5))
    }

    /**
     * Квадрант «высокая структура + низкая тема» → спящий гигант:
     * крупная аудитория, но низкая активность по теме. struct=0.8≥0.5, topic=0.2<0.5.
     */
    @Test
    fun `high struct low topic is SLEEPING_GIANT`() {
        assertEquals(BaseRole.SLEEPING_GIANT, RoleAssigner.assignRole(0.8, 0.2, 0.5, 0.5))
    }

    /**
     * Квадрант «низкая структура + высокая тема» → тематический активист:
     * активен по теме, но без крупного структурного влияния. struct=0.2<0.5, topic=0.8≥0.5.
     */
    @Test
    fun `low struct high topic is TOPIC_ACTIVIST`() {
        assertEquals(BaseRole.TOPIC_ACTIVIST, RoleAssigner.assignRole(0.2, 0.8, 0.5, 0.5))
    }

    /**
     * Квадрант «низкая структура + низкая тема» → фоновый автор. Обе оси ниже порогов.
     */
    @Test
    fun `low struct low topic is BACKGROUND_AUTHOR`() {
        assertEquals(BaseRole.BACKGROUND_AUTHOR, RoleAssigner.assignRole(0.2, 0.2, 0.5, 0.5))
    }

    /**
     * Проверяет правило границы: сравнение с порогом идёт по «больше либо равно».
     * При значениях, равных порогу (0.5 = 0.5 по обеим осям), автор относится к
     * «высоким» по обеим осям и классифицируется как AUTHORITATIVE_LEADER.
     */
    @Test
    fun `at threshold is high (ge)`() {
        assertEquals(BaseRole.AUTHORITATIVE_LEADER, RoleAssigner.assignRole(0.5, 0.5, 0.5, 0.5))
    }

    // ── Author position attribute ──

    /**
     * Атрибут позиции автора по распределению Pos_a=(p+,p0,p−) (Е.4.3).
     * Аргументы authorPosition: (доля позитива, нейтрала, негатива).
     * Доминирует позитив (0.6) → SUPPORTIVE.
     */
    @Test
    fun `dominant positive is SUPPORTIVE`() {
        assertEquals(AuthorPosition.SUPPORTIVE, RoleAssigner.authorPosition(0.6, 0.2, 0.2))
    }

    /**
     * Доминирует негатив (0.7) → критическая позиция автора CRITICAL.
     */
    @Test
    fun `dominant negative is CRITICAL`() {
        assertEquals(AuthorPosition.CRITICAL, RoleAssigner.authorPosition(0.1, 0.2, 0.7))
    }

    /**
     * Доминирует нейтрал (0.6) → нейтральная позиция автора NEUTRAL.
     */
    @Test
    fun `dominant neutral is NEUTRAL`() {
        assertEquals(AuthorPosition.NEUTRAL, RoleAssigner.authorPosition(0.2, 0.6, 0.2))
    }

    // ── Audience response attribute ──

    /**
     * Атрибут отклика аудитории по распределению Resp_a=(q+,q0,q−) (Е.4.4).
     * Правило: категория присваивается только если её доля строго больше 50 процентов.
     * Позитив 0.6 > 0.5 → одобряющий отклик APPROVING.
     */
    @Test
    fun `positive above 50 pct is APPROVING`() {
        assertEquals(AudienceResponse.APPROVING, RoleAssigner.audienceResponse(0.6, 0.3, 0.1))
    }

    /**
     * Негатив 0.6 > 0.5 → критический отклик аудитории CRITICAL.
     */
    @Test
    fun `negative above 50 pct is CRITICAL`() {
        assertEquals(AudienceResponse.CRITICAL, RoleAssigner.audienceResponse(0.1, 0.3, 0.6))
    }

    /**
     * Ни одна категория не превышает 50 процентов (0.4/0.3/0.3) → смешанный отклик MIXED.
     * Это граничный случай правила «строго больше половины».
     */
    @Test
    fun `no category above 50 pct is MIXED`() {
        assertEquals(AudienceResponse.MIXED, RoleAssigner.audienceResponse(0.4, 0.3, 0.3))
    }
}
