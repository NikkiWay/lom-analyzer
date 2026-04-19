package com.example.lomanalyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lomanalyzer.analysis.topic.BayesBetaValidator
import com.example.lomanalyzer.analysis.topic.ValidationMetrics
import com.example.lomanalyzer.analysis.topic.ValidationPost

@Composable
@Suppress("FunctionNaming", "LongMethod")
fun TopicValidationScreen(
    stratifiedPosts: List<ValidationPost>,
    randomPosts: List<ValidationPost>,
    currentThreshold: Float,
    onThresholdChanged: (Float) -> Unit,
    onStopPhraseAdded: (String) -> Unit,
    onValidationComplete: (Map<Int, Boolean?>) -> Unit,
) {
    val allPosts = stratifiedPosts + randomPosts
    val votes = remember { mutableStateMapOf<Int, Boolean?>() }
    var stopPhrase by remember { mutableStateOf("") }
    var threshold by remember { mutableStateOf(currentThreshold) }

    val metrics: ValidationMetrics? = remember(votes.toMap()) {
        if (votes.isEmpty()) null
        else {
            val voteList = allPosts.map { post ->
                post.systemRelevant to votes[post.id]
            }
            BayesBetaValidator.computeMetrics(voteList)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Topic Validation", style = MaterialTheme.typography.h6)
        Spacer(Modifier.height(8.dp))

        // Metrics display
        metrics?.let { m ->
            MetricsRow(m)
            Spacer(Modifier.height(8.dp))
        }

        // Threshold adjustment
        ThresholdRow(threshold) { threshold = it; onThresholdChanged(it) }
        Spacer(Modifier.height(8.dp))

        // Stop-phrase input
        StopPhraseRow(stopPhrase, { stopPhrase = it }) {
            if (stopPhrase.isNotBlank()) {
                onStopPhraseAdded(stopPhrase)
                stopPhrase = ""
            }
        }
        Spacer(Modifier.height(8.dp))

        // Post list
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(allPosts) { post ->
                PostVoteCard(post, votes[post.id]) { vote ->
                    votes[post.id] = vote
                }
            }
        }

        Button(
            onClick = { onValidationComplete(votes.toMap()) },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Complete Validation")
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun MetricsRow(m: ValidationMetrics) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Precision: %.2f [%.2f, %.2f]".format(m.precision.mean, m.precision.ci95Lo, m.precision.ci95Hi))
        Text("Recall: %.2f [%.2f, %.2f]".format(m.recall.mean, m.recall.ci95Lo, m.recall.ci95Hi))
        Text("Votes: ${m.totalVotes}")
    }
}

@Composable
@Suppress("FunctionNaming")
private fun ThresholdRow(threshold: Float, onChanged: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Threshold: %.2f".format(threshold))
        Spacer(Modifier.width(8.dp))
        Slider(
            value = threshold,
            onValueChange = onChanged,
            valueRange = 0.1f..0.9f,
            modifier = Modifier.width(200.dp),
        )
    }
}

@Composable
@Suppress("FunctionNaming")
private fun StopPhraseRow(value: String, onValueChange: (String) -> Unit, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Add stop-phrase") },
            modifier = Modifier.width(250.dp),
            singleLine = true,
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = onAdd) { Text("Add") }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun PostVoteCard(post: ValidationPost, vote: Boolean?, onVote: (Boolean?) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = 2.dp) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("[${post.stratum}] Score: %.3f".format(post.score))
            Text(post.text.take(200), style = MaterialTheme.typography.body2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioButton(selected = vote == true, onClick = { onVote(true) })
                Text("Yes")
                RadioButton(selected = vote == false, onClick = { onVote(false) })
                Text("No")
                RadioButton(selected = vote == null, onClick = { onVote(null) })
                Text("Unsure")
            }
        }
    }
}
