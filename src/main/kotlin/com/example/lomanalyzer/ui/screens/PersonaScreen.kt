package com.example.lomanalyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
@Suppress("FunctionNaming")
fun PersonaScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Actor Personas", style = MaterialTheme.typography.h5)
        Spacer(Modifier.height(16.dp))
        Text("Persona profiles will be displayed here after analysis.", style = MaterialTheme.typography.body1)
        Text("History placeholder — longitudinal tracking reserved for v2.0", style = MaterialTheme.typography.caption)
    }
}
