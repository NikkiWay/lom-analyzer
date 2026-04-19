package com.example.lomanalyzer.orchestration

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow

/**
 * Python recovery dialog per v6 §6.5.
 * Shown after 3 failed auto-restart attempts.
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
fun PythonRecoveryDialog(
    failureCount: Int,
    pipelineProgressPct: Int,
    onChoice: (RecoveryChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    var showFallbackConfirm by remember { mutableStateOf(false) }

    DialogWindow(
        onCloseRequest = onDismiss,
        title = "Python NLP Service Failure",
        state = DialogState(width = 450.dp, height = 300.dp),
        resizable = false,
    ) {
        MaterialTheme {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Python NLP service failed after $failureCount attempts.",
                    style = MaterialTheme.typography.h6,
                )
                Text("Pipeline progress: $pipelineProgressPct%")

                if (showFallbackConfirm && pipelineProgressPct > 50) {
                    FallbackConfirmation(pipelineProgressPct, onChoice) {
                        showFallbackConfirm = false
                    }
                } else {
                    RecoveryButtons(pipelineProgressPct, onChoice) {
                        showFallbackConfirm = true
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun RecoveryButtons(
    progressPct: Int,
    onChoice: (RecoveryChoice) -> Unit,
    onFallbackRequested: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onChoice(RecoveryChoice.WAIT) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Wait and retry") }

        Button(
            onClick = {
                if (progressPct > 50) onFallbackRequested()
                else onChoice(RecoveryChoice.FALLBACK)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Switch to FALLBACK_ONLY") }

        OutlinedButton(
            onClick = { onChoice(RecoveryChoice.CANCEL) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Cancel session") }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun FallbackConfirmation(
    progressPct: Int,
    onChoice: (RecoveryChoice) -> Unit,
    onBack: () -> Unit,
) {
    val recomputePct = 100 - progressPct
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Warning: switching to FALLBACK at $progressPct% progress " +
                "requires recomputing ~$recomputePct% of the pipeline.",
            color = MaterialTheme.colors.error,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onChoice(RecoveryChoice.FALLBACK) }) {
                Text("Confirm switch")
            }
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
    }
}
