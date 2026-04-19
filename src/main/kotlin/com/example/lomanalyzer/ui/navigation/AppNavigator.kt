package com.example.lomanalyzer.ui.navigation

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppNavigator(private val logger: Logger) {
    private val _currentRoute = MutableStateFlow(NavRoute.SETUP)
    val currentRoute: StateFlow<NavRoute> = _currentRoute.asStateFlow()

    private val _selectedAuthorId = MutableStateFlow<Int?>(null)
    val selectedAuthorId: StateFlow<Int?> = _selectedAuthorId.asStateFlow()

    fun navigate(route: NavRoute) {
        _currentRoute.value = route
        logger.event(AppEvent.UI_SCREEN_NAVIGATED, mapOf("screen" to route.name))
    }

    fun navigateToDetail(authorId: Int) {
        _selectedAuthorId.value = authorId
        navigate(NavRoute.LOM_DETAIL)
    }

    fun back() {
        _currentRoute.value = NavRoute.LOM_DASHBOARD
    }
}
