package com.example.lomanalyzer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
@Suppress("FunctionNaming")
fun TimeSeriesChart(
    data: List<TimePoint>,
    title: String = "",
    lineColor: Color = Color(0xFF1565C0),
    anomalyColor: Color = Color(0xFFD32F2F),
    modifier: Modifier = Modifier.fillMaxWidth().height(200.dp),
) {
    Column {
        if (title.isNotEmpty()) Text(title, fontSize = 12.sp)
        Canvas(modifier = modifier) {
            if (data.size < 2) return@Canvas
            val maxY = data.maxOf { it.y }.coerceAtLeast(1f)
            val minY = data.minOf { it.y }
            val range = (maxY - minY).coerceAtLeast(0.01f)
            val stepX = size.width / (data.size - 1)

            for (i in 0 until data.size - 1) {
                val x1 = i * stepX
                val y1 = size.height * (1 - (data[i].y - minY) / range)
                val x2 = (i + 1) * stepX
                val y2 = size.height * (1 - (data[i + 1].y - minY) / range)
                drawLine(lineColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = 2f)
            }
            for ((i, point) in data.withIndex()) {
                if (point.isAnomaly) {
                    val x = i * stepX
                    val y = size.height * (1 - (point.y - minY) / range)
                    drawCircle(anomalyColor, 6f, Offset(x, y))
                }
            }
        }
    }
}
