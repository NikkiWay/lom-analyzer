package com.example.lomanalyzer.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lomanalyzer.ui.screens.*
import org.koin.java.KoinJavaComponent.get

@Composable
@Suppress("FunctionNaming", "LongMethod")
fun MainContent() {
    val navigator = remember { get<AppNavigator>(AppNavigator::class.java) }
    val currentRoute by navigator.currentRoute.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        // Sidebar navigation
        NavSidebar(currentRoute, navigator)

        // Content area
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (currentRoute) {
                NavRoute.SETUP -> SetupScreen()
                NavRoute.COLLECTION -> CollectionScreen()
                NavRoute.TOPIC_VALIDATION -> TopicValidationPlaceholder()
                NavRoute.LOM_DASHBOARD -> LomDashboardScreen()
                NavRoute.LOM_DETAIL -> LomDetailScreen()
                NavRoute.PERSONA -> PersonaScreen()
                NavRoute.RISK_PANEL -> RiskPanelScreen()
                NavRoute.DYNAMICS -> DynamicsScreen()
                NavRoute.SESSION_QUALITY -> SessionQualityScreen()
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun NavSidebar(current: NavRoute, navigator: AppNavigator) {
    Column(
        modifier = Modifier
            .widthIn(min = 120.dp, max = 200.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
    ) {
        NavButton("Setup", NavRoute.SETUP, current, navigator)
        NavButton("Collection", NavRoute.COLLECTION, current, navigator)
        NavButton("Validation", NavRoute.TOPIC_VALIDATION, current, navigator)
        NavButton("Dashboard", NavRoute.LOM_DASHBOARD, current, navigator)
        NavButton("Personas", NavRoute.PERSONA, current, navigator)
        NavButton("Risk Panel", NavRoute.RISK_PANEL, current, navigator)
        NavButton("Dynamics", NavRoute.DYNAMICS, current, navigator)
        NavButton("Quality", NavRoute.SESSION_QUALITY, current, navigator)
    }
}

@Composable
@Suppress("FunctionNaming")
private fun NavButton(label: String, route: NavRoute, current: NavRoute, nav: AppNavigator) {
    val selected = current == route
    TextButton(
        onClick = { nav.navigate(route) },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (selected) MaterialTheme.colors.primary
            else MaterialTheme.colors.onSurface,
        ),
    ) {
        Text(label)
    }
}

@Composable
@Suppress("FunctionNaming")
private fun TopicValidationPlaceholder() {
    Text("Topic Validation — use the full TopicValidationScreen after collection")
}
