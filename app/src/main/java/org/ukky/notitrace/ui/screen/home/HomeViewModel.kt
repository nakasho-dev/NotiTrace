package org.ukky.notitrace.ui.screen.home

import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.ukky.notitrace.data.db.entity.NotificationListItemModel
import org.ukky.notitrace.data.repository.AppTagRepository
import org.ukky.notitrace.data.repository.NotificationRepository
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val notificationRepo: NotificationRepository,
    private val tagRepo: AppTagRepository,
) : ViewModel() {

    private val _selectedTag = MutableStateFlow<String?>(null)

    val notifications: Flow<PagingData<NotificationListItemModel>> = _selectedTag
        .flatMapLatest { tag ->
            if (tag == null) {
                notificationRepo.getAllListItems()
            } else {
                notificationRepo.getListItemsByTag(tag)
            }
        }
        .cachedIn(viewModelScope)

    private val availableTags = tagRepo.getAllTags()

    val uiState: StateFlow<HomeUiState> = combine(
        availableTags,
        _selectedTag,
    ) { tags, selected ->
        HomeUiState(
            availableTags = tags,
            selectedTag = selected,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun selectTag(tag: String?) {
        _selectedTag.value = tag
    }
}
