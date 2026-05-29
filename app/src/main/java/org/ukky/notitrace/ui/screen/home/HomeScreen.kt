package org.ukky.notitrace.ui.screen.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.ukky.notitrace.ui.component.EmptyState
import org.ukky.notitrace.ui.component.NotificationListItem
import org.ukky.notitrace.ui.component.TagChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNotificationClick: (Long) -> Unit,
    onSearchClick: () -> Unit,
    onTagManageClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("NotiTrace") },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "検索")
                    }
                    IconButton(onClick = onTagManageClick) {
                        Icon(Icons.Default.Style, contentDescription = "タグ管理")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // ── タグフィルタ ChipGroup ────
            if (state.availableTags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TagChip(
                        tag = "すべて",
                        selected = state.selectedTag == null,
                        onClick = { viewModel.selectTag(null) },
                    )
                    state.availableTags.forEach { tag ->
                        TagChip(
                            tag = tag,
                            selected = state.selectedTag == tag,
                            onClick = { viewModel.selectTag(tag) },
                        )
                    }
                }
            }

            // ── 通知リスト ────
            if (state.notifications.isEmpty() && !state.isLoading) {
                EmptyState(message = "通知ログはまだありません")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = state.notifications,
                        key = { it.rawLogId },
                    ) { item ->
                        NotificationListItem(
                            item = item,
                            onClick = { onNotificationClick(item.notification.id) },
                        )
                    }
                }
            }
        }
    }
}
