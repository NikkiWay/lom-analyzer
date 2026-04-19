package com.example.lomanalyzer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
@Suppress("FunctionNaming")
fun CiBar(
    value: Float,
    ciLo: Float?,
    ciHi: Float?,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colors.primary,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(20.dp).background(Color.LightGray)) {
            val lo = (ciLo ?: value).coerceIn(0f, 1f)
            val hi = (ciHi ?: value).coerceIn(0f, 1f)
            val v = value.coerceIn(0f, 1f)
            val totalWidth = maxWidth
            // CI range
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(totalWidth * (hi - lo))
                    .offset(x = totalWidth * lo)
                    .background(barColor.copy(alpha = 0.3f))
            )
            // Point estimate
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .offset(x = totalWidth * v)
                    .background(barColor)
            )
        }
        Text("%.3f [%.3f, %.3f]".format(value, ciLo ?: value, ciHi ?: value), fontSize = 10.sp)
    }
}
