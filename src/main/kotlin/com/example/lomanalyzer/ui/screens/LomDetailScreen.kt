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
fun LomDetailScreen() {
    val navigator = remember { get<AppNavigator>(AppNavigator::class.java) }
    val authorId by navigator.selectedAuthorId.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = { navigator.back() }) { Text("< Back") }
            Text("Actor Detail #${authorId ?: "-"}", style = MaterialTheme.typography.h5)
        }

        Spacer(Modifier.height(16.dp))

        // Decomposition placeholder — populated by viewmodel
        Text("I_base decomposition", style = MaterialTheme.typography.subtitle1)
        CiBar(0.65f, 0.55f, 0.75f, modifier = Modifier.width(250.dp))

        Spacer(Modifier.height(8.dp))
        Text("I_event decomposition", style = MaterialTheme.typography.subtitle1)
        CiBar(0.45f, 0.35f, 0.55f, modifier = Modifier.width(250.dp))

        Spacer(Modifier.height(8.dp))
        Text("Role", style = MaterialTheme.typography.subtitle1)
        RoleCombinationBadge("TOPIC_DRIVER")

        Spacer(Modifier.height(8.dp))
        Text("Confidence", style = MaterialTheme.typography.subtitle1)
        ConfidenceIndicator(0.72f)

        Spacer(Modifier.height(16.dp))
        Text("Account flags: -", style = MaterialTheme.typography.body2)
        Text("TF-IDF terms: -", style = MaterialTheme.typography.body2)
        Text("VAR: -", style = MaterialTheme.typography.body2)
    }
}
