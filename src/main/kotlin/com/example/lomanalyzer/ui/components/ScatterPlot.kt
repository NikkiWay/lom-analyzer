package com.example.lomanalyzer.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Scatter plot placeholder.
 * Full Lets-Plot Batik rendering will be integrated in SwingPanel
 * during the final UI polish pass.
 */
@Composable
@Suppress("FunctionNaming")
fun ScatterPlot(
    points: List<ScatterPoint>,
    tauBase: Float,
    tauEvent: Float,
    @Suppress("unused") tauRefBase: Float?,
    modifier: Modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 400.dp),
) {
    val desc = "Scatter: ${points.size} actors, " +
        "base=${"%.2f".format(tauBase)} " +
        "event=${"%.2f".format(tauEvent)}"
    Text(desc, modifier = modifier)
}
