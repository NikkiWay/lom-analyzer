package com.example.lomanalyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lomanalyzer.orchestration.CancellationController
import com.example.lomanalyzer.orchestration.ProgressReporter
import org.koin.java.KoinJavaComponent.get

@Composable
@Suppress("FunctionNaming")
fun CollectionScreen() {
    val progressReporter = remember {
        get<ProgressReporter>(ProgressReporter::class.java)
    }
    val cancellationController = remember {
        get<CancellationController>(CancellationController::class.java)
    }
    val progress by progressReporter.progress.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Data Collection", style = MaterialTheme.typography.h6)

        Text("Stage: ${progress.stage.ifEmpty { "Idle" }}")

        if (progress.totalItems > 0) {
            val fraction = progress.completedItems.toFloat() / progress.totalItems
            LinearProgressIndicator(
                progress = fraction,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${progress.completedItems} / ${progress.totalItems}")
        }

        progress.etaSeconds?.let { eta ->
            Text("ETA: ${eta}s")
        }

        Button(
            onClick = { cancellationController.cancel() },
            enabled = progress.stage.isNotEmpty(),
        ) {
            Text("Cancel")
        }
    }
}
