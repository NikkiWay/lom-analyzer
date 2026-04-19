package com.example.lomanalyzer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
@Suppress("FunctionNaming")
fun ConfidenceIndicator(confidence: Float, modifier: Modifier = Modifier) {
    val color = when {
        confidence >= 0.60f -> Color(0xFF4CAF50)
        confidence >= 0.25f -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
    val label = when {
        confidence >= 0.60f -> "High"
        confidence >= 0.25f -> "Medium"
        else -> "Low"
    }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(12.dp).background(color))
        Text("%.2f (%s)".format(confidence, label), fontSize = 11.sp)
    }
}
