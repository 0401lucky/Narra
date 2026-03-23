package com.example.myapplication.model

enum class RoleplaySuggestionAxis {
    PLOT,
    INFO,
    EMOTION,
}

data class RoleplaySuggestionUiModel(
    val id: String,
    val label: String,
    val text: String,
    val axis: RoleplaySuggestionAxis,
)
