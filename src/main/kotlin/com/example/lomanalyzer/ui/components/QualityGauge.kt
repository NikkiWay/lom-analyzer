package com.example.lomanalyzer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
@Suppress("FunctionNaming")
fun QualityGauge(label: String, value: Float, isGate: Boolean = false, modifier: Modifier = Modifier) {
    val color = when {
        value >= 0.7f -> Color(0xFF4CAF50)
        value >= 0.4f -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp)
        Box(Modifier.fillMaxWidth().height(8.dp).background(Color.LightGray)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0f, 1f)).background(color))
        }
        Text("%.2f%s".format(value, if (isGate) " [GATE]" else ""), fontSize = 10.sp)
    }
}
