package org.ukky.notitrace.ui.screen.search

import org.ukky.notitrace.data.db.entity.ReceivedNotificationWithTag

data class SearchUiState(
    val query: String = "",
    val results: List<ReceivedNotificationWithTag> = emptyList(),
)
