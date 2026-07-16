/*
 * НАЗНАЧЕНИЕ
 * Центральный контроллер навигации между экранами приложения. Хранит, какой
 * экран сейчас открыт, и какой автор выбран для детального просмотра. Это
 * простейший «роутер» для desktop-Compose (без Android Navigation Component):
 * экраны переключаются через единый StateFlow currentRoute.
 *
 * ЧТО ВНУТРИ
 * Класс AppNavigator: два StateFlow (текущий маршрут и id выбранного автора) и
 * методы navigate / navigateToDetail / back для смены экранов.
 *
 * ФРЕЙМВОРКИ
 * kotlinx.coroutines StateFlow — реактивное состояние навигации, на которое
 * подписываются Composable через collectAsState; Logger (observability) —
 * запись события UI_SCREEN_NAVIGATED при каждом переходе для аудита.
 *
 * СВЯЗИ
 * Создаётся через Koin DI; MainContent читает currentRoute и рисует нужный экран;
 * LomDashboard вызывает navigateToDetail для открытия карточки автора.
 */
package com.example.lomanalyzer.ui.navigation

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Контроллер навигации: единый источник правды о текущем экране и выбранном авторе.
 *
 * @param logger логгер для записи событий переходов между экранами (аудит UI).
 */
class AppNavigator(private val logger: Logger) {
    // Внутренний изменяемый поток текущего маршрута; стартовый экран — SETUP (постановка задачи)
    private val _currentRoute = MutableStateFlow(NavRoute.SETUP)
    /** Текущий открытый экран; экраны подписываются на него через collectAsState. */
    val currentRoute: StateFlow<NavRoute> = _currentRoute.asStateFlow()

    // id автора, выбранного на дашборде для перехода в детальную карточку (null — не выбран)
    private val _selectedAuthorId = MutableStateFlow<Int?>(null)
    /** id выбранного автора для экрана LOM_DETAIL; null, если автор не выбран. */
    val selectedAuthorId: StateFlow<Int?> = _selectedAuthorId.asStateFlow()

    /**
     * Переход на указанный экран. Меняет текущий маршрут и пишет событие в лог.
     * @param route целевой экран навигации.
     */
    fun navigate(route: NavRoute) {
        // Обновляем StateFlow — все подписанные Composable перерисуются на новый экран
        _currentRoute.value = route
        // Фиксируем переход в журнале наблюдаемости (имя экрана как параметр события)
        logger.event(AppEvent.UI_SCREEN_NAVIGATED, mapOf("screen" to route.name))
    }

    /**
     * Открыть детальную карточку конкретного автора.
     * Сначала запоминает id автора, затем переключает экран на LOM_DETAIL.
     * @param authorId внутренний id автора в БД.
     */
    fun navigateToDetail(authorId: Int) {
        _selectedAuthorId.value = authorId
        navigate(NavRoute.LOM_DETAIL)
    }

    /**
     * Возврат «назад» из детальной карточки автора на дашборд.
     * Намеренно ведёт всегда на LOM_DASHBOARD (стек истории не ведётся).
     */
    fun back() {
        _currentRoute.value = NavRoute.LOM_DASHBOARD
    }
}
