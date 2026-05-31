package org.ukky.notitrace.ui.screen.search

import app.cash.turbine.test
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ukky.notitrace.data.db.entity.NotificationListItemModel
import org.ukky.notitrace.data.repository.NotificationRepository

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private lateinit var repo: NotificationRepository
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `検索クエリが空の場合は結果が空`() = runTest {
        val vm = SearchViewModel(repo)

        vm.uiState.test {
            val state = awaitItem()
            assertEquals("", state.query)
            cancelAndIgnoreRemainingEvents()
        }
        vm.viewModelScope.cancel()
    }

    @Test
    fun `検索クエリを入力すると結果が返る`() = runTest {
        val results = listOf(
            listItem(id = 1L, title = "東京の天気", receivedAt = 1_000L),
        )
        every { repo.searchListItems("東京") } returns flowOf(PagingData.from(results))

        val vm = SearchViewModel(repo)

        vm.uiState.test {
            awaitItem()
            vm.onQueryChange("東京")
            advanceTimeBy(400)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals("東京", state.query)
            cancelAndIgnoreRemainingEvents()
        }
        vm.viewModelScope.cancel()
    }

    private fun listItem(
        id: Long,
        title: String,
        receivedAt: Long,
    ) = NotificationListItemModel(
        id = id,
        packageName = "com.test",
        title = title,
        text = null,
        bigText = null,
        notificationType = "local",
        receivedAt = receivedAt,
        tag = null,
        appLabel = null,
    )
}
