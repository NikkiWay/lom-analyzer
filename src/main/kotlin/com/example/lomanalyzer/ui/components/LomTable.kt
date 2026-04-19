package com.example.lomanalyzer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
@Suppress("FunctionNaming", "LongMethod")
fun LomTable(
    rows: List<LomTableRow>,
    onRowClick: (Int) -> Unit = {},
) {
    var sortColumn by remember { mutableStateOf("iBaseHist") }
    var sortAsc by remember { mutableStateOf(false) }

    val sorted = remember(rows, sortColumn, sortAsc) {
        val comparator = when (sortColumn) {
            "iBaseHist" -> compareBy<LomTableRow> { it.iBaseHist }
            "iBaseAbs" -> compareBy { it.iBaseAbs }
            "iEventHist" -> compareBy { it.iEventHist }
            "confidence" -> compareBy { it.confidence }
            else -> compareBy { it.iBaseHist }
        }
        if (sortAsc) rows.sortedWith(comparator) else rows.sortedWith(comparator.reversed())
    }

    val headers = listOf(
        "Author" to "authorName", "I_base" to "iBaseHist", "I_base_abs" to "iBaseAbs",
        "I_event" to "iEventHist", "Role" to "role", "Conf" to "confidence",
        "N_topic" to "nTopic", "Sentiment" to "sentiment", "VAR" to "var",
    )

    Column(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
        // Header
        Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            for ((label, key) in headers) {
                Text(
                    label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.width(90.dp).clickable {
                        if (sortColumn == key) sortAsc = !sortAsc else { sortColumn = key; sortAsc = false }
                    },
                )
            }
        }
        Divider()

        LazyColumn {
            items(sorted) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp)
                        .clickable { onRowClick(row.authorId) },
                ) {
                    Cell(row.authorName)
                    CellF(row.iBaseHist)
                    CellF(row.iBaseAbs)
                    CellF(row.iEventHist)
                    Cell(row.roleCombined ?: "-")
                    CellF(row.confidence)
                    Cell(row.nTopicEff?.toString() ?: "-")
                    CellF(row.sentiment)
                    CellF(row.visualActivity)
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun Cell(text: String) {
    Text(text, fontSize = 11.sp, modifier = Modifier.width(90.dp))
}

@Composable
@Suppress("FunctionNaming")
private fun CellF(value: Float?) {
    Text(value?.let { "%.3f".format(it) } ?: "-", fontSize = 11.sp, modifier = Modifier.width(90.dp))
}
