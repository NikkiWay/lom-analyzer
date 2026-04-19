package com.example.lomanalyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lomanalyzer.orchestration.SessionManager
import com.example.lomanalyzer.orchestration.SessionParams
import org.koin.java.KoinJavaComponent.get

@Composable
@Suppress("FunctionNaming")
fun SetupScreen() {
    val sessionManager = remember { get<SessionManager>(SessionManager::class.java) }
    var lastCreatedId by remember { mutableStateOf<Int?>(null) }
    var sessionCount by remember { mutableStateOf(sessionManager.listSessions().size) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("LOM Analyzer", style = MaterialTheme.typography.h5)
        Text("Sessions: $sessionCount")

        Button(onClick = {
            val id = sessionManager.createSession(
                SessionParams(
                    name = "Test Session",
                    topicQuery = "test topic",
                )
            )
            lastCreatedId = id
            sessionCount = sessionManager.listSessions().size
        }) {
            Text("Create empty session")
        }

        lastCreatedId?.let {
            Text("Created session #$it")
        }
    }
}
