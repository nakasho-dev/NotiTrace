package org.ukky.notitrace.ui.screen.home

import org.ukky.notitrace.data.db.entity.ReceivedNotificationWithTag

data class HomeUiState(
    val notifications: List<ReceivedNotificationWithTag> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val selectedTag: String? = null,
    val isLoading: Boolean = true,
)
