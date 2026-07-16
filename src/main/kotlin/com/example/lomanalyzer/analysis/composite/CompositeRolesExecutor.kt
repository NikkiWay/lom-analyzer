/*
 * НАЗНАЧЕНИЕ
 * Исполнитель ЭТАПА 9 пайплайна (docs/algorithm.md; диплом 2.1.6): превращение
 * 11 сырых оценок в композиты двух осей, расчёт адаптивных порогов и квадрантная
 * классификация авторов по ролям с двумя атрибутами.
 *
 * ЧТО ВНУТРИ
 * class CompositeRolesExecutor (реализует StageExecutor) с методом execute,
 * выполняющим всю цепочку z-нормализация → композиты → пороги → роли.
 *
 * АЛГОРИТМ
 *  1. z-нормализовать каждую из 6 компонентных оценок по всем авторам (RobustZScore);
 *  2. собрать композиты Struct_a и Topic_a (равные веса 1/3, CompositeScorer);
 *  3. адаптивные пороги θ_Struct, θ_Topic = медианы композитов;
 *  4. квадрантная классификация → 4 базовые роли (RoleAssigner.assignRole);
 *  5. два атрибута: позиция автора (из Pos_a) и отклик аудитории (из Resp_a).
 *
 * БИБЛИОТЕКИ / ФРЕЙМВОРКИ
 *  - Exposed (LomScores) — чтение оценок; Koin — внедрение DAO/сервисов;
 *  - корутины (suspend execute); ProgressReporter/Logger/SessionEventService —
 *    прогресс и журналирование (в т. ч. логирование null-импутаций и порогов).
 *
 * СВЯЗИ
 *  Читает LomScores (этап 7), пишет композиты/пороги/роли через CompositeDao.
 *  Использует RobustZScore, CompositeScorer, RoleAssigner. Результат — вход
 *  для этапов качества/экспорта/UI.
 */
package com.example.lomanalyzer.analysis.composite

import com.example.lomanalyzer.analysis.roles.RoleAssigner
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.PipelineStage
import com.example.lomanalyzer.orchestration.ProgressEvent
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.orchestration.StageExecutor
import com.example.lomanalyzer.storage.dao.CompositeDao
import com.example.lomanalyzer.storage.dao.LomScoreDao
import com.example.lomanalyzer.storage.tables.LomScores
import com.example.lomanalyzer.vk.SessionEventService

/**
 * Этап 9 алгоритма (диплом 2.1.6):
 * композитные оценки → адаптивные пороги → классификация ролей.
 *
 * 1. z-нормализовать каждую из 6 компонентных оценок по всем авторам сессии;
 * 2. вычислить композиты Struct_a и Topic_a (равные веса 1/3);
 * 3. адаптивные пороги = медианы композитов;
 * 4. квадрантная классификация → 4 базовые роли;
 * 5. два атрибута: позиция (из Pos_a) + отклик (из Resp_a).
 *
 * @param lomScoreDao чтение 11 оценок этапа 7
 * @param compositeDao запись композитов, порогов и ролей
 * @param sessionEventService журнал событий сессии (для UI)
 * @param progressReporter отчёт о прогрессе
 * @param logger журналирование
 */
class CompositeRolesExecutor(
    private val lomScoreDao: LomScoreDao,
    private val compositeDao: CompositeDao,
    private val sessionEventService: SessionEventService,
    private val progressReporter: ProgressReporter,
    private val logger: Logger,
) : StageExecutor {

    /**
     * Выполняет полный конвейер этапа 9 для одной сессии.
     * @param sessionId идентификатор сессии анализа
     * @param stage текущий этап пайплайна (для совместимости с интерфейсом)
     */
    override suspend fun execute(sessionId: Int, stage: PipelineStage) {
        val scores = lomScoreDao.findBySession(sessionId)
        // Для z-нормализации и медианных порогов нужно ≥2 авторов, иначе пропускаем этап
        if (scores.size < 2) {
            logger.info("Not enough authors (${scores.size}) for composite scoring, skipping")
            return
        }

        // Собираем сырые значения по каждой из 6 компонент (nullable — могут отсутствовать)
        // Порядок authorIds фиксирован и используется для сопоставления с z-значениями по индексу
        val authorIds = scores.map { it[LomScores.authorId].value }
        val audValues = scores.map { it[LomScores.aud]?.toDouble() }
        val ageValues = scores.map { it[LomScores.age]?.toDouble() }
        val erBgValues = scores.map { it[LomScores.erBg]?.toDouble() }
        val topVolValues = scores.map { it[LomScores.topVol]?.toDouble() }
        val topFocusValues = scores.map { it[LomScores.topFocus]?.toDouble() }
        val reachValues = scores.map { it[LomScores.reach]?.toDouble() }

        // Шаг 1: робастная z-нормализация каждой компоненты (z = (x − med)/IQR), null → 0
        val zAud = RobustZScore.normalize(audValues)
        val zAge = RobustZScore.normalize(ageValues)
        val zErBg = RobustZScore.normalize(erBgValues)
        val zTopVol = RobustZScore.normalize(topVolValues)
        val zTopFocus = RobustZScore.normalize(topFocusValues)
        val zReach = RobustZScore.normalize(reachValues)

        // Логируем «значимую импутацию пропусков»: сколько авторов получили z=0 из-за null
        val allNullIndices = zAud.nullImputedIndices + zAge.nullImputedIndices +
            zErBg.nullImputedIndices + zTopVol.nullImputedIndices +
            zTopFocus.nullImputedIndices + zReach.nullImputedIndices
        if (allNullIndices.isNotEmpty()) {
            sessionEventService.logInfo(
                sessionId,
                "z-нормализация: ${allNullIndices.toSet().size} авторов с null-импутацией (z=0)",
            )
        }

        // Шаг 2: для каждого автора собираем композиты Struct_a и Topic_a из z-компонент
        val structComposites = mutableListOf<Double>()
        val topicComposites = mutableListOf<Double>()
        for (i in scores.indices) {
            // Struct_a = (1/3)(z(Aud) + z(ER_bg) + z(Age))
            structComposites.add(CompositeScorer.structuralComposite(
                zAud.zValues[i], zErBg.zValues[i], zAge.zValues[i]))
            // Topic_a = (1/3)(z(TopVol) + z(TopFocus) + z(Reach))
            topicComposites.add(CompositeScorer.topicComposite(
                zTopVol.zValues[i], zTopFocus.zValues[i], zReach.zValues[i]))
        }

        // Шаг 3: адаптивные пороги = медианы композитов; сохраняем их в БД
        val (thetaStruct, thetaTopic) = CompositeScorer.adaptiveThresholds(structComposites, topicComposites)
        compositeDao.upsertThresholds(sessionId, thetaStruct.toFloat(), thetaTopic.toFloat())

        sessionEventService.logInfo(
            sessionId,
            "Адаптивные пороги: θ_Struct=%.4f, θ_Topic=%.4f".format(thetaStruct, thetaTopic),
        )

        // Шаги 4–5: для каждого автора — базовая роль и два атрибута (позиция, отклик)
        for (i in scores.indices) {
            val authorId = authorIds[i]
            val struct = structComposites[i]
            val topic = topicComposites[i]

            // Сохраняем композиты автора
            compositeDao.upsertComposite(sessionId, authorId, struct.toFloat(), topic.toFloat())

            // Базовая роль по квадранту (сравнение композитов с порогами θ)
            val role = RoleAssigner.assignRole(struct, topic, thetaStruct, thetaTopic)

            // Атрибут «позиция автора» из распределения Pos_a (default при null: нейтрально)
            val posP = scores[i][LomScores.posPositive]?.toDouble() ?: 0.0
            val posN = scores[i][LomScores.posNeutral]?.toDouble() ?: 1.0
            val posNeg = scores[i][LomScores.posNegative]?.toDouble() ?: 0.0
            val position = RoleAssigner.authorPosition(posP, posN, posNeg)

            // Атрибут «отклик аудитории» из распределения Resp_a (default при null: нейтрально)
            val respP = scores[i][LomScores.respPositive]?.toDouble() ?: 0.0
            val respN = scores[i][LomScores.respNeutral]?.toDouble() ?: 1.0
            val respNeg = scores[i][LomScores.respNegative]?.toDouble() ?: 0.0
            val response = RoleAssigner.audienceResponse(respP, respN, respNeg)

            // Сохраняем роль и оба атрибута (имена enum)
            compositeDao.upsertRole(sessionId, authorId, role.name, position.name, response.name)

            // Прогресс батчами по 50 авторов (и обязательно на последнем)
            if ((i + 1) % 50 == 0 || i == scores.size - 1) {
                progressReporter.update(ProgressEvent(
                    stage = "Классификация: ${i + 1}/${scores.size}",
                    completedItems = i + 1,
                    totalItems = scores.size,
                ))
            }
        }

        logger.info("Composite+roles completed: ${scores.size} authors, " +
            "θ_Struct=%.4f, θ_Topic=%.4f".format(thetaStruct, thetaTopic))
    }
}
