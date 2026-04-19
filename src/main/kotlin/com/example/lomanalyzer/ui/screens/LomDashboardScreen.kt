package com.example.lomanalyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lomanalyzer.ui.components.*
import com.example.lomanalyzer.ui.navigation.AppNavigator
import org.koin.java.KoinJavaComponent.get

@Composable
@Suppress("FunctionNaming")
fun LomDashboardScreen() {
    val navigator = remember { get<AppNavigator>(AppNavigator::class.java) }
    var roleFilter by remember { mutableStateOf<String?>(null) }

    // Placeholder data — viewmodel will populate from DB
    val rows = remember { emptyList<LomTableRow>() }
    val points = remember { emptyList<ScatterPoint>() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("LOM Dashboard", style = MaterialTheme.typography.h5)
        Spacer(Modifier.height(8.dp))

        // Role filter chips
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip("All", roleFilter == null) { roleFilter = null }
            FilterChip("AUTH", roleFilter == "AUTH") { roleFilter = "AUTH" }
            FilterChip("GIANT", roleFilter == "GIANT") { roleFilter = "GIANT" }
            FilterChip("DRIVER", roleFilter == "DRIVER") { roleFilter = "DRIVER" }
            FilterChip("BG", roleFilter == "BG") { roleFilter = "BG" }
        }

        Spacer(Modifier.height(8.dp))
        ScatterPlot(points, tauBase = 0.5f, tauEvent = 0.5f, tauRefBase = 0.78f)
        Spacer(Modifier.height(8.dp))
        LomTable(rows, onRowClick = { navigator.navigateToDetail(it) })
    }
}

@Composable
@Suppress("FunctionNaming")
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (selected) MaterialTheme.colors.primary
            else MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        ),
    ) { Text(label) }
}
