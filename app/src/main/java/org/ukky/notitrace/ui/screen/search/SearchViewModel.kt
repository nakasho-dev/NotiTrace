package org.ukky.notitrace.ui.screen.search

import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import org.ukky.notitrace.data.db.entity.NotificationListItemModel
import org.ukky.notitrace.data.repository.NotificationRepository
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: NotificationRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")

    val results: Flow<PagingData<NotificationListItemModel>> = _query
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(PagingData.empty())
            else repo.searchListItems(q)
        }
        .cachedIn(viewModelScope)

    val uiState: StateFlow<SearchUiState> = _query
        .map(::SearchUiState)
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState(),
    )

    fun onQueryChange(query: String) {
        _query.value = query
    }
}
