package com.example.lomanalyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
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
    var roleMode by remember { mutableStateOf("QUADRANT") }
    var lastCreatedId by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
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
            modifier = fw.heightIn(min = 80.dp, max = 150.dp), maxLines = 10,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(baselineDays, { baselineDays = it },
                label = { Text("Baseline") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(currentDays, { currentDays = it },
                label = { Text("Current") }, modifier = Modifier.weight(1f), singleLine = true)
        }

        Text("Role Mode", style = MaterialTheme.typography.subtitle2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(roleMode == "QUADRANT", { roleMode = "QUADRANT" })
            Text("Quadrant")
            RadioButton(roleMode == "GMM", { roleMode = "GMM" })
            Text("GMM (n>=50)")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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

            Button(onClick = { navigator.navigate(NavRoute.COLLECTION) }) {
                Text("Start Collection")
            }
        }

        lastCreatedId?.let { Text("Created session #$it") }
    }
}
