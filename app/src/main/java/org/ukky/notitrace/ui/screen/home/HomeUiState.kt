package org.ukky.notitrace.ui.screen.home

data class HomeUiState(
    val availableTags: List<String> = emptyList(),
    val selectedTag: String? = null,
)
