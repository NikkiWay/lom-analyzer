package com.example.lomanalyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lomanalyzer.orchestration.RetentionManager
import org.koin.java.KoinJavaComponent.get
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
@Suppress("FunctionNaming")
fun RetentionScreen() {
    val retention = remember { get<RetentionManager>(RetentionManager::class.java) }
    var sessions by remember { mutableStateOf(retention.getSoftDeletedSessions()) }
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Deleted Sessions", style = MaterialTheme.typography.h5)
        Spacer(Modifier.height(8.dp))

        if (sessions.isEmpty()) {
            Text("No soft-deleted sessions.")
        } else {
            LazyColumn {
                items(sessions) { session ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        elevation = 2.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("#${session.id}: ${session.name}")
                                Text(
                                    "Deleted: ${fmt.format(Instant.ofEpochMilli(session.deletedAt))}",
                                    style = MaterialTheme.typography.caption,
                                )
                            }
                            Button(onClick = {
                                retention.restoreSession(session.id)
                                sessions = retention.getSoftDeletedSessions()
                            }) {
                                Text("Restore")
                            }
                        }
                    }
                }
            }
        }
    }
}
