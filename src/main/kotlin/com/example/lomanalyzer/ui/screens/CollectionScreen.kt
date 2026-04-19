package com.example.lomanalyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
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
    val progressReporter = remember { get<ProgressReporter>(ProgressReporter::class.java) }
    val controller = remember { get<CancellationController>(CancellationController::class.java) }
    val progress by progressReporter.progress.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Data Collection & Processing", style = MaterialTheme.typography.h6)
        Text("Stage: ${progress.stage.ifEmpty { "Idle" }}")

        if (progress.totalItems > 0) {
            val fraction = progress.completedItems.toFloat() / progress.totalItems
            LinearProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth())
            Text("${progress.completedItems} / ${progress.totalItems}")

            progress.etaSeconds?.let { Text("ETA: ${it}s") }

            val rate = if (progress.etaSeconds != null && progress.etaSeconds!! > 0) {
                "%.1f items/s".format(progress.completedItems.toFloat() / progress.etaSeconds!!)
            } else ""
            if (rate.isNotEmpty()) Text(rate)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { controller.cancel() }, enabled = progress.stage.isNotEmpty()) {
                Text("Cancel")
            }
        }
    }
}
