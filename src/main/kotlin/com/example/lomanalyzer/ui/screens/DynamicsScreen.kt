package com.example.lomanalyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lomanalyzer.ui.components.TimePoint
import com.example.lomanalyzer.ui.components.TimeSeriesChart

@Composable
@Suppress("FunctionNaming")
fun DynamicsScreen() {
    // Placeholder — viewmodel will populate
    val volumeData = remember { emptyList<TimePoint>() }
    val sentimentData = remember { emptyList<TimePoint>() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Dynamics", style = MaterialTheme.typography.h5)
        Spacer(Modifier.height(16.dp))

        Text("Daily Post Volume")
        TimeSeriesChart(volumeData, title = "Volume")

        Spacer(Modifier.height(16.dp))
        Text("Daily Mean Sentiment")
        TimeSeriesChart(sentimentData, title = "Sentiment")
    }
}
