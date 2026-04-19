package com.example.lomanalyzer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "LOM Analyzer"
    ) {
        // Empty window — UI will be implemented in subsequent prompts
    }
}
