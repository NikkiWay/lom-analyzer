package com.example.lomanalyzer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
@Suppress("FunctionNaming")
fun RiskCard(
    riskScore: Float,
    ciLo: Float,
    ciHi: Float,
    category: String,
    isBorderline: Boolean,
    recommendation: String,
    modifier: Modifier = Modifier,
) {
    val bgColor = when (category) {
        "HIGH" -> Color(0xFFFFCDD2)
        "MEDIUM" -> Color(0xFFFFF9C4)
        "LOW" -> Color(0xFFC8E6C9)
        else -> Color(0xFFF5F5F5)
    }

    Card(modifier = modifier.fillMaxWidth().padding(4.dp), backgroundColor = bgColor) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(category, style = MaterialTheme.typography.h6)
                if (isBorderline) Text("BORDERLINE", color = Color(0xFFFF6F00))
            }
            CiBar(riskScore, ciLo, ciHi)
            Spacer(Modifier.height(8.dp))
            Text(recommendation, style = MaterialTheme.typography.body2)
        }
    }
}
