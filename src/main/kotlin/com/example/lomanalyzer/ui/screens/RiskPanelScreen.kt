package com.example.lomanalyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lomanalyzer.ui.components.RiskCard

@Composable
@Suppress("FunctionNaming")
fun RiskPanelScreen() {
    // Placeholder — viewmodel will populate from DB
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Risk Panel", style = MaterialTheme.typography.h5)
        Spacer(Modifier.height(16.dp))
        Text("Risk signals will appear here after anomaly detection completes.")

        // Example card
        RiskCard(
            riskScore = 0f,
            ciLo = 0f,
            ciHi = 0f,
            category = "MINIMAL",
            isBorderline = false,
            recommendation = "No anomalies detected yet.",
        )
    }
}
