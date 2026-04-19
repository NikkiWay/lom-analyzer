package com.example.lomanalyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lomanalyzer.ui.components.QualityGauge

@Composable
@Suppress("FunctionNaming")
fun SessionQualityScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Session Quality Score", style = MaterialTheme.typography.h5)
        Spacer(Modifier.height(16.dp))

        // 9 SQS components — values populated by viewmodel
        val components = listOf(
            Triple("Coverage Ratio", 0f, false),
            Triple("Topic Validation Precision", 0f, false),
            Triple("Topic Validation Recall", 0f, false),
            Triple("Dedup Ratio", 0f, false),
            Triple("Gamma R2", 0f, false),
            Triple("Norm Stability (CV_IQR)", 0f, true),
            Triple("Bootstrap Width", 0f, false),
            Triple("Confidence Distribution", 0f, true),
            Triple("Reference Freshness", 0f, false),
        )

        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((label, value, isGate) in components) {
                QualityGauge(label, value, isGate, Modifier.width(120.dp))
            }
        }
    }
}
