package com.example.lomanalyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lomanalyzer.orchestration.SessionManager
import com.example.lomanalyzer.orchestration.SessionParams
import com.example.lomanalyzer.ui.navigation.AppNavigator
import com.example.lomanalyzer.ui.navigation.NavRoute
import org.koin.java.KoinJavaComponent.get

@Composable
@Suppress("FunctionNaming", "LongMethod")
fun SetupScreen() {
    val sessionManager = remember { get<SessionManager>(SessionManager::class.java) }
    val navigator = remember { get<AppNavigator>(AppNavigator::class.java) }

    var name by remember { mutableStateOf("") }
    var topicQuery by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var primaryNgrams by remember { mutableStateOf("") }
    var secondaryNgrams by remember { mutableStateOf("") }
    var excludedNgrams by remember { mutableStateOf("") }
    var referenceTexts by remember { mutableStateOf("") }
    var baselineDays by remember { mutableStateOf("60") }
    var currentDays by remember { mutableStateOf("30") }
    var lastCreatedId by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Session Setup", style = MaterialTheme.typography.h5)

        val fw = Modifier.fillMaxWidth()
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = fw)
        OutlinedTextField(topicQuery, { topicQuery = it }, label = { Text("Topic") }, modifier = fw)
        OutlinedTextField(region, { region = it }, label = { Text("Region") }, modifier = fw)

        Text("N-grams (comma-separated)", style = MaterialTheme.typography.subtitle2)
        OutlinedTextField(primaryNgrams, { primaryNgrams = it }, label = { Text("Primary") }, modifier = fw)
        OutlinedTextField(secondaryNgrams, { secondaryNgrams = it }, label = { Text("Secondary") }, modifier = fw)
        OutlinedTextField(excludedNgrams, { excludedNgrams = it }, label = { Text("Excluded") }, modifier = fw)

        OutlinedTextField(
            referenceTexts, { referenceTexts = it },
            label = { Text("Reference texts (3-10)") },
            modifier = fw.height(100.dp), maxLines = 10,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(baselineDays, { baselineDays = it },
                label = { Text("Baseline days") }, modifier = Modifier.width(120.dp))
            OutlinedTextField(currentDays, { currentDays = it },
                label = { Text("Current days") }, modifier = Modifier.width(120.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = {
                val id = sessionManager.createSession(SessionParams(
                    name = name.ifBlank { "Unnamed" },
                    topicQuery = topicQuery.ifBlank { "default" },
                    region = region.ifBlank { null },
                    baselineWindowDays = baselineDays.toIntOrNull() ?: 60,
                    currentWindowDays = currentDays.toIntOrNull() ?: 30,
                ))
                lastCreatedId = id
            }) { Text("Create Session") }

            Button(onClick = { navigator.navigate(NavRoute.COLLECTION) }) { Text("Start Collection") }
        }

        lastCreatedId?.let { Text("Created session #$it") }
    }
}
