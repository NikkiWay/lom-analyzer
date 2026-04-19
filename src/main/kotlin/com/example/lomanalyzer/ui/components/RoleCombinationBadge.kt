package com.example.lomanalyzer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
@Suppress("FunctionNaming")
fun RoleCombinationBadge(role: String, modifier: Modifier = Modifier) {
    val (bg, fg) = roleColors(role)
    Text(
        text = role.replace("_", " "),
        fontSize = 11.sp,
        color = fg,
        modifier = modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun roleColors(role: String): Pair<Color, Color> = when (role) {
    "AUTHORITATIVE_LOM_CONFIRMED" -> Color(0xFF1B5E20) to Color.White
    "AUTHORITATIVE_LOM_LOCAL" -> Color(0xFF4CAF50) to Color.White
    "SLEEPING_GIANT_CONFIRMED" -> Color(0xFFE65100) to Color.White
    "SLEEPING_GIANT_LOCAL" -> Color(0xFFFF9800) to Color.Black
    "TOPIC_DRIVER_WITH_BASE" -> Color(0xFF1565C0) to Color.White
    "TOPIC_DRIVER" -> Color(0xFF42A5F5) to Color.Black
    "BACKGROUND_LARGE" -> Color(0xFF616161) to Color.White
    "BACKGROUND" -> Color(0xFFBDBDBD) to Color.Black
    "BASELINE_UNKNOWN" -> Color(0xFF9E9E9E) to Color.Black
    else -> Color(0xFFE0E0E0) to Color.Black
}
